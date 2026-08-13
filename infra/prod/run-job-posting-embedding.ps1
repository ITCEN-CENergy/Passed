param(
    [Parameter(Mandatory = $true)]
    [string]$EnvironmentFile,

    [Parameter(Mandatory = $true)]
    [string]$PipelineImage,

    [string]$ComposeFile = "infra/prod/compose.prod.yml",
    [string]$ProjectName = "passed-prod",
    [int]$BatchSize = 100
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$embeddingModel = "text-embedding-3-small"
$repositoryRoot = Resolve-Path (Join-Path $PSScriptRoot "../..")
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

function Invoke-Embedding {
    param([Parameter(Mandatory = $true)][string[]]$EmbeddingArguments)

    $arguments = @(
        "run", "--rm",
        "--network", "container:$script:postgresContainerId",
        "-e", "DATABASE_URL=$script:databaseUrl",
        "-e", "OPENAI_API_KEY=$script:openAiApiKey",
        "-e", "EMBEDDING_MODEL=$script:embeddingModel",
        "--entrypoint", "python",
        $PipelineImage,
        "-m", "job_posting_pipeline.run_embedding"
    )
    $arguments += $EmbeddingArguments
    Invoke-Docker -Arguments $arguments
}

if ($BatchSize -lt 1 -or $BatchSize -gt 2048) {
    throw "BatchSize must be between 1 and 2048."
}

Push-Location $repositoryRoot
try {
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
    $openAiApiKey = (
        & docker @composeArguments exec -T ai `
            sh -c 'printf %s "$OPENAI_API_KEY"'
    ).Trim()
    if (
        -not $databaseName -or
        -not $databaseUser -or
        -not $databasePassword -or
        -not $openAiApiKey
    ) {
        throw "Production database or OpenAI settings are incomplete."
    }

    $escapedPassword = [Uri]::EscapeDataString($databasePassword)
    $databaseUrl = (
        "postgresql://${databaseUser}:${escapedPassword}" +
        "@127.0.0.1:5432/${databaseName}"
    )

    Invoke-Docker -Arguments @("pull", $PipelineImage)

    $pendingBefore = Invoke-PsqlScalar (
        "SELECT COUNT(*) FROM job_posting_chunks " +
        "WHERE (embedding IS NULL OR embedding_model IS DISTINCT FROM " +
        "'$embeddingModel') " +
        "AND chunk_content <> '' " +
        "AND source_type NOT IN ('PROCESS', 'DISQUALIFICATION', 'BENEFIT');"
    )
    if ([int]$pendingBefore -eq 0) {
        Write-Host "No job-posting embeddings are pending."
        return
    }

    Write-Host "Running embedding canary for up to $BatchSize chunks."
    Invoke-Embedding -EmbeddingArguments @(
        "--max-iterations", "1",
        "--batch-size", "$BatchSize"
    )

    Write-Host "Embedding canary succeeded. Processing remaining chunks."
    Invoke-Embedding -EmbeddingArguments @("--batch-size", "$BatchSize")

    $pendingAfter = Invoke-PsqlScalar (
        "SELECT COUNT(*) FROM job_posting_chunks " +
        "WHERE (embedding IS NULL OR embedding_model IS DISTINCT FROM " +
        "'$embeddingModel') " +
        "AND chunk_content <> '' " +
        "AND source_type NOT IN ('PROCESS', 'DISQUALIFICATION', 'BENEFIT');"
    )
    if ([int]$pendingAfter -ne 0) {
        throw "Embedding validation failed: remaining=$pendingAfter"
    }

    Write-Host "Job-posting embedding completed: remaining=0"
}
finally {
    Remove-Variable databasePassword, escapedPassword, databaseUrl, `
        openAiApiKey -ErrorAction SilentlyContinue
    Pop-Location
}
