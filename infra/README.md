# Docker Compose environments

The repository has one Compose entry point per environment.

| Environment | Compose file | Project name |
| --- | --- | --- |
| Development | `compose.yaml` | `passed-dev` |
| Production | `infra/prod/compose.prod.yml` | `passed-prod` |

## Development

Copy the ignored root `.env.example` to `.env`, replace `JWT_SECRET`, and add
`OPENAI_API_KEY` when AI features that call OpenAI are needed.

The example files are intentionally ignored by Git, so each developer and
server administrator maintains their own local copy.

```powershell
Copy-Item .env.example .env
docker compose config --quiet
docker compose up -d --build
docker compose ps
```

Development host ports remain unchanged:

- Frontend: `5173`
- Backend: `8080`
- AI: `8000`
- PostgreSQL: `5433`
- Prometheus: `9090`
- Grafana: `3000`

Stop the stack without deleting its data:

```powershell
docker compose down
```

## Production

Keep the real production environment file outside the repository. The current
deployment contract uses `C:\srv\passed\prod.env`.

```powershell
docker compose `
  --project-name passed-prod `
  --env-file C:\srv\passed\prod.env `
  -f infra\prod\compose.prod.yml `
  config --quiet

docker compose `
  --project-name passed-prod `
  --env-file C:\srv\passed\prod.env `
  -f infra\prod\compose.prod.yml `
  up -d
```

Production host ports remain isolated: Backend `18080`, PostgreSQL `5432`,
Prometheus `19090`, and Grafana `13001`. AI is available only on the Compose
network. The production frontend continues to be served by OCI Nginx.

Do not use `down -v` unless all data in that environment can be deleted.
