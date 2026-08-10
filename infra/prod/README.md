# Passed production stack

The production stack is isolated from the existing development environment.

## Host ports

- Frontend: OCI Nginx on ports `80` and `443`
- Backend: `${TAILSCALE_IP}:18080`
- PostgreSQL: `127.0.0.1:5432`
- Prometheus: `127.0.0.1:19090`
- Grafana: `127.0.0.1:13001`
- AI: Docker network only

The frontend production build is deployed to `/var/www/passed/current` on OCI.
OCI Nginx serves the static files and proxies `/api/` to the home server
backend over Tailscale.

The frontend is not part of the home-server Docker Compose project. GitHub
Actions runs `npm ci` and `npm run build`, uploads the `dist` artifact to OCI,
creates a release directory under `/var/www/passed/releases`, and atomically
updates the `/var/www/passed/current` symbolic link.

The existing development ports remain unchanged: PostgreSQL `5433`, backend
`8080`, AI `8000`, and Vite `5173`.

## Environment file

Copy `.env.example` to a secure location outside the repository. The production
GitHub Actions runner expects:

```text
C:\srv\passed\prod.env
```

Replace every placeholder and keep the file out of Git.

## Manual validation

Run these commands from the repository root on the home server:

```powershell
$env:IMAGE_TAG = "the-full-git-commit-sha"

docker compose `
  --project-name passed-prod `
  --env-file C:\srv\passed\prod.env `
  -f infra\prod\compose.prod.yml `
  config --quiet
```

## Manual deployment

```powershell
docker login ghcr.io

docker compose `
  --project-name passed-prod `
  --env-file C:\srv\passed\prod.env `
  -f infra\prod\compose.prod.yml `
  pull

docker compose `
  --project-name passed-prod `
  --env-file C:\srv\passed\prod.env `
  -f infra\prod\compose.prod.yml `
  up -d --remove-orphans
```

The manual commands above deploy only the home-server services. A frontend
release must be built with `npm run build` and copied to OCI separately, or
deployed through `.github/workflows/deploy-prod.yml`.

## Status and logs

```powershell
docker compose `
  --project-name passed-prod `
  --env-file C:\srv\passed\prod.env `
  -f infra\prod\compose.prod.yml `
  ps

docker compose `
  --project-name passed-prod `
  --env-file C:\srv\passed\prod.env `
  -f infra\prod\compose.prod.yml `
  logs --tail 200
```

The development and production PostgreSQL volumes are separate. Never reuse or
delete the development volume when provisioning production.
