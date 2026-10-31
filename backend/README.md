# Backend

The Go service exposes the API defined by the repository-root `openapi.yaml`, persists data in
PostgreSQL, and serves the built frontend from the same process.

## Package structure

| Package | Responsibility |
| --- | --- |
| `cmd/timetrack` | Configuration, database setup, migration and graceful shutdown |
| `internal/api` | Generated API models and strict HTTP interface |
| `internal/server` | HTTP routing, handlers, authentication and management listener |
| `internal/auth` | JWTs, sessions, authorization, invites and impersonation |
| `internal/domain` | Shared enums, recurrence and working-day calculations |
| `internal/users`, `internal/workinghours` | User and working-hours repositories and services |
| `internal/timeentries`, `internal/timeoff` | Time-entry and leave operations and response mapping |
| `internal/recurringoffdays`, `internal/conflictwarnings` | Rules, exemptions and conflict warnings |
| `internal/vacationbalance`, `internal/publicholidays` | Balances and public-holiday responses |
| `internal/reports` | Monthly report aggregation and PDF/CSV output |
| `internal/leavestore` | Shared leave persistence used by domain packages |
| `internal/config`, `internal/db`, `internal/clock` | Environment, pgx boundaries and time abstractions |
| `internal/httpx` | Central HTTP error mapping and localization |
| `internal/migrate` | Embedded SQL and established migration-history format |
| `internal/testutil`, `internal/integration` | Testcontainers harness and full-router integration tests |
| `web` | Embedded frontend assets; Docker places the frontend build in `web/dist` |

## Conventions

Read `../openapi.yaml` before changing the API. Regenerate generated bindings with `go generate ./...`;
do not edit generated files manually. Handlers return generated response types or `httpx.Error`, which
is mapped centrally. Initialize empty collections where the API expects arrays.

Keep domain rules in their owning package and reuse shared calculators and mappers. Repositories
accept the `db.Querier` interface so operations can use either a pool or transaction. Use
`db.Transaction` for atomic mutations and pass request contexts into database calls. SQL uses explicit
columns and `$1`, `$2`, ... parameters.

Date-only API values use `openapi_types.Date`. PostgreSQL `TIMESTAMP WITHOUT TIME ZONE` values
represent wall time; use `clock.LocalTimestamp` when storing them and inject `clock.Fixed` in tests
that depend on current time.

## HTTP handlers and compatibility

The strict generated router dispatches API requests to handlers in `internal/server`. The separate
management listener provides `/actuator/health` and `/actuator/info`. Existing environment names
such as `SPRING_PROFILES_ACTIVE` and `LOGGING_LEVEL_CC_REMER_TIMETRACK` remain accepted.

The migrator embeds SQL from `internal/migrate/sql/`. It retains the `flyway_schema_history` table
and CRC32 checksum format so existing databases continue to validate. Embedded SQL files are the
source of migration contents; tests cover checksum normalization and recorded checksum values.

## Configuration

Defaults are defined in `internal/config/config.go`: API port `8080` (or `8811` when
`SPRING_PROFILES_ACTIVE` contains `prod`), management port `7080`, database URL
`jdbc:postgresql://localhost:5432/timetrack`, username/password `timetrack`, JWT access expiry
`86400000` ms, refresh expiry `15552000000` ms, and frontend base URL `http://localhost:5173`.
`JWT_SECRET` must contain at least 32 UTF-8 bytes. `DB_URL` accepts postgres and postgresql schemes,
including the existing `jdbc:` prefix.

## Tests

Tests use Go's `testing` package. Integration package harnesses use testcontainers-go with PostgreSQL;
Docker must be available. The shared harness starts a container per package, migrates it once, and
resets state between tests. Tests sharing a harness should not run in parallel. Keep focused unit
tests near the package and end-to-end HTTP coverage in `internal/integration`.

From this directory:

```bash
go test ./...
go test ./... -v
go vet ./...
gofmt -l .
go run ./cmd/timetrack
make generate
make test
make vet
make build
```

## Docker

Build from the repository root so the Dockerfile can build the existing frontend:

```bash
docker build -f backend/Dockerfile .
```
