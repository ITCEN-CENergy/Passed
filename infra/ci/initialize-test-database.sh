#!/usr/bin/env bash
set -Eeuo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
migration_directory="${repository_root}/passed-backend/src/main/resources/db/migration"
job_posting_directory="${repository_root}/passed-ai/embedding-data/job-posting"

database_name="${POSTGRES_DB:-passed}"
database_user="${POSTGRES_USER:-passed}"
database_password="${POSTGRES_PASSWORD:-passed}"
database_port="${POSTGRES_PORT:-5433}"
database_url="postgresql://${database_user}:${database_password}@127.0.0.1:${database_port}/${database_name}"
flyway_url="jdbc:postgresql://127.0.0.1:${database_port}/${database_name}"
loader_image="passed-job-posting-loader:ci"

run_flyway() {
  local target="${1:-}"
  local arguments=(
    --rm
    --network host
    -v "${migration_directory}:/flyway/sql:ro"
    flyway/flyway:11
    -locations=filesystem:/flyway/sql
    "-url=${flyway_url}"
    "-user=${database_user}"
    "-password=${database_password}"
    -connectRetries=60
  )

  if [[ -n "${target}" ]]; then
    arguments+=("-target=${target}")
  fi
  arguments+=(migrate)

  docker run "${arguments[@]}"
}

run_psql_file() {
  local sql_file="$1"
  docker run --rm \
    --network host \
    -e "PGPASSWORD=${database_password}" \
    -v "${sql_file}:/seed.sql:ro" \
    pgvector/pgvector:pg17 \
    psql -v ON_ERROR_STOP=1 \
      -h 127.0.0.1 -p "${database_port}" \
      -U "${database_user}" -d "${database_name}" \
      -f /seed.sql
}

run_psql_scalar() {
  local query="$1"
  docker run --rm \
    --network host \
    -e "PGPASSWORD=${database_password}" \
    pgvector/pgvector:pg17 \
    psql -v ON_ERROR_STOP=1 -At \
      -h 127.0.0.1 -p "${database_port}" \
      -U "${database_user}" -d "${database_name}" \
      -c "${query}"
}

echo "Applying Flyway migrations through V20260804151714560."
run_flyway "20260804151714560"

echo "Loading CI reference data."
run_psql_file "${job_posting_directory}/schema/seed_industries_job_roles_from_excel.sql"
run_psql_file "${job_posting_directory}/schema/dev_seed_companies_0_159.sql"

echo "Building and running the job-posting CSV loader."
docker build \
  -f "${repository_root}/passed-ai/Dockerfile.job-posting-loader" \
  -t "${loader_image}" \
  "${repository_root}/passed-ai"
docker run --rm \
  --network host \
  -e "DATABASE_URL=${database_url}" \
  "${loader_image}"

echo "Applying the remaining Flyway migrations."
run_flyway

counts="$(run_psql_scalar "
  SELECT
    (SELECT COUNT(*) FROM industries),
    (SELECT COUNT(*) FROM job_roles WHERE id BETWEEN 1 AND 239),
    (SELECT COUNT(*) FROM companies WHERE id BETWEEN 0 AND 159),
    (SELECT COUNT(*) FROM job_postings WHERE id BETWEEN 1 AND 4730),
    (SELECT COUNT(*) FROM flyway_schema_history WHERE NOT success);
")"

IFS='|' read -r industries job_roles companies job_postings failed_migrations <<<"${counts}"
if (( industries < 21 )) ||
   (( job_roles != 239 )) ||
   (( companies != 160 )) ||
   (( job_postings != 4730 )) ||
   (( failed_migrations != 0 )); then
  echo "Database initialization validation failed: ${counts}" >&2
  exit 1
fi

echo "Database initialization verified: industries=${industries} job_roles=${job_roles} companies=${companies} job_postings=${job_postings}"
