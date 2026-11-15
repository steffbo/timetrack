# Agent Bootstrap - Time Tracking Application

## Orientation checklist

1. Review `git log --oneline -10` and the recent entries in `CHANGELOG.md`.
2. Check `README.md`, `openapi.yaml`, and the current working tree before making changes.
3. Keep the API specification as the contract shared by backend and frontend.

## Architecture

- `backend/` - Go HTTP application, PostgreSQL persistence and embedded SQL migrations
- `frontend/` - Vue 3, TypeScript, Vite and PrimeVue 4
- `openapi.yaml` - API contract and source for generated API bindings

The backend uses package-oriented domain logic under `backend/internal/`, HTTP handlers in
`backend/internal/server`, pgx repositories, and integration tests with testcontainers-go.
The backend binary serves the API and embedded frontend assets.

## Commands

Backend commands run from `backend/`:

```bash
export PATH=$HOME/.local/go/bin:$HOME/go/bin:$PATH
go test ./...
go vet ./...
go run ./cmd/timetrack
make generate
make test
make vet
make build
```

Frontend commands run from `frontend/`:

```bash
npm run dev
npm run build
npm run generate-api
```

The local backend defaults to API port 8080 and management port 7080. The frontend dev server
uses port 5173. Docker Compose starts the application and PostgreSQL from the repository root.

Default credentials: `admin@timetrack.local` / `admin1`.

## Development conventions

- Read `openapi.yaml` before changing API endpoints, then regenerate bindings as needed.
- Follow existing package, handler, repository and transaction patterns; see `backend/README.md`.
- Add backend tests for new behavior and cover authorization and failure cases.
- Keep user-facing behavior, JSON, headers and status codes consistent with the API contract.
- Update `CHANGELOG.md` for user-facing changes and `README.md` when setup changes.
- Do not edit generated API code by hand.

## Useful paths

- Backend entry point: `backend/cmd/timetrack/main.go`
- Backend configuration: `backend/internal/config/config.go`
- HTTP handlers and server setup: `backend/internal/server/`
- SQL migrations: `backend/internal/migrate/sql/`
- Backend guide and test strategy: `backend/README.md`
- Frontend routes/views/translations: `frontend/src/`

For browser inspection, use the configured browser tools against the local frontend and backend.

## Deploy

Production runs at `https://zeit.home.remer.cc` (LAN and tailnet only) on `vm-infra-dev`. Push to `main`;
the workflow `Docker - Build and Push` (`.github/workflows/docker.yml`) builds `ghcr.io/steffbo/timetrack`.
Roll out with skill `homelab-app-deploy`:

```bash
cd ~/workspace/homelab/ansible && ansible-playbook playbooks/deploy-app.yml -e app=timetrack
curl -s -o /dev/null -w '%{http_code}\n' -X POST https://zeit.home.remer.cc/api/auth/login \
  -H 'Content-Type: application/json' -d '{"email":"x@y.z","password":"x"}'   # expect 401
```

The health endpoint is on management port 7080 and not routed through Caddy; every unknown path on the
public URL returns the SPA with 200, so do not use `/health` as a check. Environment and compose file
live in the homelab repo under `stacks/infra-dev/apps/timetrack/`. The frontend calls the API on its own
origin; never put a domain into `frontend/.env.production`. The app holds real time records: ask before
deploying and before any migration that rewrites data.
