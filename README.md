# Time Tracking Application

A time tracking application with a Go HTTP backend, PostgreSQL database, and Vue 3 frontend.
The API contract is defined by the root `openapi.yaml`.

## Features

- JWT authentication, refresh tokens, user management and impersonation
- Time entries, working hours, leave, public holidays and recurring off-days
- Vacation balances, conflict warnings, dashboard calendar and PDF/CSV reports
- German and English interface
- Single container image with the frontend embedded in the backend binary

## Stack

- Backend: Go (version in `backend/go.mod`), `net/http`, pgx, PostgreSQL 17
- Frontend: Vue 3, TypeScript, Vite, PrimeVue 4
- API generation: oapi-codegen for Go and the existing frontend generator
- Tests: Go testing and testcontainers-go
- Deployment: Docker Compose and Caddy

## Run locally

Prerequisites: Go version from `backend/go.mod`; Node/npm for frontend work; Docker for PostgreSQL
and integration tests.

```bash
cp .env.example .env
export PATH=$HOME/.local/go/bin:$HOME/go/bin:$PATH
docker compose up -d postgres
cd backend
go run ./cmd/timetrack
```

The backend defaults to API port `8080`, management port `7080`, and frontend base URL
`http://localhost:5173`. The database defaults are `timetrack` for both username and password,
with database `timetrack` on localhost. Set `JWT_SECRET` to at least 32 UTF-8 bytes.

For a complete local stack, run `docker compose up -d` from the repository root. The frontend
is available on the configured API port and management health is at `/actuator/health` on
`MANAGEMENT_PORT`.

The environment variables consumed by the backend are `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`,
`JWT_SECRET`, `JWT_EXPIRATION`, `JWT_REFRESH_EXPIRATION`, `SERVER_PORT`, `MANAGEMENT_PORT`,
`APP_FRONTEND_BASE_URL`, `ALLOWED_ORIGINS`, `SPRING_PROFILES_ACTIVE`, `LOGGING_LEVEL_ROOT`,
`LOGGING_LEVEL_APP`, and `LOGGING_LEVEL_CC_REMER_TIMETRACK`. The established
`SPRING_PROFILES_ACTIVE` and `LOGGING_LEVEL_CC_REMER_TIMETRACK` names remain supported. Durations
are milliseconds. `DB_URL` accepts `postgres://` and `postgresql://` URLs, including the existing
`jdbc:postgresql://` form.

## Development

Run backend commands from `backend/`:

```bash
go test ./...
go vet ./...
go run ./cmd/timetrack
make generate
make test
make vet
make build
```

`make generate` regenerates the Go API bindings. Frontend commands and development instructions
are in [frontend/README.md](frontend/README.md). See [backend/README.md](backend/README.md) for
package structure, SQL conventions, handlers, configuration and testing details.

## Project layout

```text
backend/       Go application, embedded SQL migrations, tests and Dockerfile
frontend/      Vue application
openapi.yaml   Shared API contract
```

## Deployment

See [DEPLOYMENT.md](DEPLOYMENT.md). The production container uses the Go backend and embedded SPA.
The Go process typically uses about 10–25 MB RSS: 11 MiB idle and 23 MB peak under PDF export load.

## Default credentials

The initial administrator is `admin@timetrack.local` with password `admin1`. Change this password
when deploying.
