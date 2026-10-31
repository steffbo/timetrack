# Codex Bootstrap - Time Tracking Application

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

For deployment, see `DEPLOYMENT.md`. For browser inspection, use the configured browser tools
against the local frontend and backend.
