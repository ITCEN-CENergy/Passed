param(
    [Parameter(Mandatory = $true)]
    [string]$EnvironmentFile,

    [Parameter(Mandatory = $true)]
    [string]$LoaderImage,

    [string]$ComposeFile = "infra/prod/compose.prod.yml",
    [string]$ProjectName = "passed-prod"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$bootstrapTarget = "20260804151714560"
$skillSeedVersion = "20260804154936000"
$repositoryRoot = Resolve-Path (Join-Path $PSScriptRoot "../..")
$migrationDirectory = Resolve-Path (
    Join-Path $repositoryRoot "passed-backend/src/main/resources/db/migration"
)
$referenceSeed = Resolve-Path (
    Join-Path $repositoryRoot (
        "passed-ai/embedding-data/job-posting/schema/" +
        "seed_industries_job_roles_from_excel.sql"
    )
)

$composeArguments = @(
    "compose",
    "--project-name", $ProjectName,
    "--env-file", $EnvironmentFile,
    "-f", $ComposeFile
)

function Invoke-Docker {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    & docker @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker command failed with exit code $LASTEXITCODE."
    }
}

function Invoke-Compose {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    Invoke-Docker -Arguments ($composeArguments + $Arguments)
}

function Invoke-PsqlScalar {
    param([Parameter(Mandatory = $true)][string]$Query)

    $result = & docker @composeArguments exec -T postgres `
        psql -v ON_ERROR_STOP=1 -At `
        -U $script:databaseUser -d $script:databaseName `
        -c $Query
    if ($LASTEXITCODE -ne 0) {
        throw "PostgreSQL query failed with exit code $LASTEXITCODE."
    }

    return ($result | Out-String).Trim()
}

function Invoke-Flyway {
    param(
        [string]$Target,
        [switch]$OutOfOrder
    )

    $arguments = @(
        "run", "--rm",
        "--network", "container:$script:postgresContainerId",
        "-v", "${migrationDirectory}:/flyway/sql:ro",
        "flyway/flyway:11",
        "-locations=filesystem:/flyway/sql",
        "-url=jdbc:postgresql://127.0.0.1:5432/$script:databaseName",
        "-user=$script:databaseUser",
        "-password=$script:databasePassword",
        "-connectRetries=60"
    )
    if ($Target) {
        $arguments += "-target=$Target"
    }
    if ($OutOfOrder) {
        $arguments += "-outOfOrder=true"
    }
    $arguments += "migrate"

    Invoke-Docker -Arguments $arguments
}

function Invoke-PsqlFile {
    param([Parameter(Mandatory = $true)][string]$SqlFile)

    Invoke-Docker -Arguments @(
        "run", "--rm",
        "--network", "container:$script:postgresContainerId",
        "-e", "PGPASSWORD=$script:databasePassword",
        "-v", "${SqlFile}:/seed.sql:ro",
        "pgvector/pgvector:pg17",
        "psql", "-v", "ON_ERROR_STOP=1",
        "-h", "127.0.0.1", "-p", "5432",
        "-U", $script:databaseUser, "-d", $script:databaseName,
        "-f", "/seed.sql"
    )
}

Push-Location $repositoryRoot
try {
    Invoke-Compose -Arguments @("up", "-d", "--wait", "postgres")

    $postgresContainerId = (
        & docker @composeArguments ps -q postgres
    ).Trim()
    if (-not $postgresContainerId) {
        throw "Production PostgreSQL container was not found."
    }

    $databaseName = (
        & docker @composeArguments exec -T postgres `
            sh -c 'printf %s "$POSTGRES_DB"'
    ).Trim()
    $databaseUser = (
        & docker @composeArguments exec -T postgres `
            sh -c 'printf %s "$POSTGRES_USER"'
    ).Trim()
    $databasePassword = (
        & docker @composeArguments exec -T postgres `
            sh -c 'printf %s "$POSTGRES_PASSWORD"'
    ).Trim()
    if (-not $databaseName -or -not $databaseUser -or -not $databasePassword) {
        throw "Production PostgreSQL connection settings are incomplete."
    }

    $historyExists = Invoke-PsqlScalar `
        "SELECT to_regclass('public.flyway_schema_history') IS NOT NULL;"
    $skillSeedApplied = $false
    if ($historyExists -eq "t") {
        $skillSeedApplied = (
            Invoke-PsqlScalar (
                "SELECT EXISTS (" +
                "SELECT 1 FROM flyway_schema_history " +
                "WHERE version = '$skillSeedVersion' AND success" +
                ");"
            )
        ) -eq "t"
    }

    if (-not $skillSeedApplied) {
        Write-Host "Initializing the production database in README order."
        Invoke-Flyway -Target $bootstrapTarget

        # Mount the UTF-8 SQL file directly so Windows PowerShell never decodes it.
        Invoke-PsqlFile -SqlFile $referenceSeed

        Invoke-Docker -Arguments @("pull", $LoaderImage)
        $escapedPassword = [Uri]::EscapeDataString($databasePassword)
        $databaseUrl = (
            "postgresql://${databaseUser}:${escapedPassword}" +
            "@127.0.0.1:5432/${databaseName}"
        )
        Invoke-Docker -Arguments @(
            "run", "--rm",
            "--network", "container:$postgresContainerId",
            "-e", "DATABASE_URL=$databaseUrl",
            $LoaderImage
        )
    }
    else {
        Write-Host "Reference data and job postings are already initialized."
    }

    # Feature migrations can reach production after a newer version was applied.
    # Apply those pending migrations here; normal application startup stays strict.
    Invoke-Flyway -OutOfOrder

    $referenceCounts = Invoke-PsqlScalar (
        "SELECT " +
        "(SELECT COUNT(*) FROM industries), " +
        "(SELECT COUNT(*) FROM job_roles WHERE id BETWEEN 1 AND 239), " +
        "(SELECT COUNT(*) FROM companies WHERE id BETWEEN 0 AND 159), " +
        "(SELECT COUNT(*) FROM job_postings WHERE id BETWEEN 1 AND 4730);"
    )
    $failedMigrations = Invoke-PsqlScalar (
        "SELECT COUNT(*) FROM flyway_schema_history WHERE NOT success;"
    )

    $counts = $referenceCounts -split "\|"
    if (
        $counts.Count -ne 4 -or
        [int]$counts[0] -lt 21 -or
        [int]$counts[1] -ne 239 -or
        [int]$counts[2] -ne 160 -or
        [int]$counts[3] -ne 4730 -or
        [int]$failedMigrations -ne 0
    ) {
        throw (
            "Database initialization validation failed: " +
            "counts=$referenceCounts failed_migrations=$failedMigrations"
        )
    }

    Write-Host (
        "Database initialization verified: " +
        "industries=$($counts[0]) job_roles=$($counts[1]) " +
        "companies=$($counts[2]) job_postings=$($counts[3])"
    )
}
finally {
    Remove-Variable databasePassword, escapedPassword, databaseUrl `
        -ErrorAction SilentlyContinue
    Pop-Location
}
