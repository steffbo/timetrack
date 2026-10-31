# Go backend

This module ports the existing Java application. The root `openapi.yaml`, Java migrations,
security classes, and use cases are the behavioral references. Only `backend-go/` is changed
by this consolidation. No additional dependencies are needed.

## Package structure

| Location | Responsibility |
| --- | --- |
| `cmd/timetrack` | Configuration, migration, two listeners, graceful shutdown |
| `internal/api` | Generated strict net/http interface and API models |
| `internal/server` | HTTP handlers, authentication, request validation, management listener |
| `internal/auth` | JWT, principal, authorization, sessions, invites, impersonation |
| `internal/users` | User repository, management, API mapping |
| `internal/workinghours` | Working-hours repository, validation, mutations and API mapping |
| `internal/domain` | Enums, precedence, holidays, pure recurrence and working-day counting |
| `internal/recurringoffdays` | Rules/exemptions persistence, recurrence adapter, use cases and mapping |
| `internal/conflictwarnings` | Warning repository, detector, acknowledgment and mapping |
| `internal/leavestore` | Shared time-off model and repository; no use-case dependencies |
| `internal/timeoff` | Time-off CRUD, authorization and the single TimeOff response mapper |
| `internal/vacationbalance` | Working-day data loading, balance recalculation, CRUD and mapping |
| `internal/timeentries` | Entries, clock in/out, summaries and composed calendar read model |
| `internal/reports` | Monthly report aggregation, PDF and CSV rendering |
| `internal/publicholidays` | Public-holiday API response |
| `internal/config`, `internal/db`, `internal/clock` | Environment, pgx boundaries, time |
| `internal/httpx` | Central exception mapping and localization |
| `internal/migrate` | Embedded SQL and Flyway-compatible history |
| `internal/testutil`, `internal/integration` | PostgreSQL harness and full-router integration tests |
| `web` | Embedded SPA; Docker copies the built frontend into `web/dist` |

Every operation is implemented. `TestNoOperationReturnsNotImplemented` enumerates the embedded
specification, including auth, without domain allowlists or skips. Each operation gets a fresh
admin session; non-auth operations must not be hidden behind a 401 response.

## Single implementations and dependency direction

`domain.RecurringRule.AppliesToDate` is the sole recurrence algorithm. It normalizes dates as Java
LocalDate does, truncates whole weeks toward zero and interprets weekOfMonth=5 as the last occurrence.
`recurringoffdays/evaluator.go` adapts persisted rules and loads exemptions, delegating pattern
matching to that core. Existing evaluator tests in both packages exercise this implementation.

`domain.WorkingDays` is the sole inclusive day-counting algorithm. It applies configured weekdays,
public holidays, other non-vacation leave, recurring days, and optional half days on December 24/31.
`vacationbalance.WorkingDays` loads those facts from their owning repositories. An excluded entry
is removed from other leave; when that entry is not vacation, recurring days are ignored, matching
WorkingDaysCalculator. The TimeOff mapper, summaries and vacation totals share this calculator.

`conflictwarnings.Detector` is the sole conflict detector. Time-entry Create and ClockOut call it
inside their transaction. Delete explicitly calls CleanupForTimeEntry after deleting the entry,
even though the database also cascades warning deletion. Update and ClockIn do not detect conflicts.
Existing warnings, including acknowledged warnings, are retained when another entry conflicts on
the same date. Exemptions suppress creation; the first matching active rule supplies the warning.

`vacationbalance/service.go` owns the sole VacationBalanceService recalculation and totals logic.
The shared repository creates defaults and maps responses once. TimeOff CRUD recalculates exactly
when Java does: Create/Delete for vacation; Update when the old or new type is vacation, across
the union of the old and new years. GET always recalculates used days, then reloads the record.
Balance Update retains stored used days and calculates planned days dynamically for its response.
Storage remaining = allowance + carry + adjustment - used; response remaining subtracts planned.
Only completed vacation entries (end <= today) count as used. Each overlapping year's calculation
uses the full absence, without clipping it to the year, matching the Java repository and service.
Unchanged recalculation preserves updated_at, as Hibernate dirty checking does.

Java has no callers of reevaluateConflicts, no recalculation from working-hours/rule/exemption
changes and no scheduled year rollover. Go preserves those call sites: changes become visible in
on-demand planned calculations and the next balance GET; no additional mutation hooks are added.

`leavestore` breaks the potential timeoff -> vacationbalance -> timeoff import cycle. All shared
reads of time_off live there. Working-hours reads/mapping live in `workinghours`, rules/exemptions
in `recurringoffdays`, warnings in `conflictwarnings`, and user reads in `users` (auth keeps its
security-specific projection). Calendar loading composes these repositories and their API mappers;
reports reuse that calendar and the time-entry repository instead of copying their SQL.

## SQL and transactions

Use pgx/v5 with `$1`, `$2`, ... placeholders and explicit columns. Never concatenate untrusted
identifiers or values. Repositories accept `db.Querier` for either pool or transaction usage.
Use `db.Transaction` for atomic mutations. Errors roll back the whole use case, as Spring does
for unchecked exceptions. Pass request context to queries and close rows before nested reads.

Pool defaults: max 10, min 2, connect timeout 30 s, idle 10 min, lifetime 30 min.
The migrator embeds all 17 Java migrations (V7/V8 are absent in Java). Tests compare them byte for
byte. Flyway CRC32 strips initial BOM and line delimiters and stores signed int32 checksums.
Migration execution and history insertion share a transaction; a session advisory lock covers
validation and execution. Changed/missing migrations, failed history and unsupported types fail.
BASELINE sets the version floor; SCHEMA rows are ignored. Versions are integers in this project.

## API, errors and dates

Read `openapi.yaml` before changing endpoints. `go generate ./...` runs pinned oapi-codegen v2.5.1
and `scripts/jsoncompat.go`. Never edit generated.go manually. Handlers return generated success
responses or errors for central mapping. Initialize empty collections; nil serializes as null.

`httpx.Error` contains Status, Code, Message and optional string Details. Copy codes/messages from
Java. Unknown exceptions map to 500 / INTERNAL_ERROR; Java method-security Access Denied also maps
to 500, while domain ForbiddenException maps to 403. Authentication uses Spring's sendError envelope.
Validation traverses the spec before strict decoding; EN/DE messages are supported.

Use openapi_types.Date for date-only values without UTC conversion of local midnight. PostgreSQL
TIMESTAMP WITHOUT TIME ZONE stores wall time: use `clock.LocalTimestamp(c.Now())`. MapperUtils
attaches UTC to stored wall time without conversion. Inject `clock.Fixed` for deterministic tests.
JWT filter reloads role/active state, accepts access and refresh JWTs as bearer credentials, and
retains impersonatedBy in context. Secrets are raw UTF-8; key length selects HS256/384/512.

## Testing and commands

```bash
export PATH=$HOME/.local/go/bin:$HOME/go/bin:$PATH
cd backend-go
go vet ./...
go test ./...
gofmt -l .
grep -rn 'NOT_IMPLEMENTED\|StatusNotImplemented' internal/server
```

The last command has no matches: the only NOT_IMPLEMENTED constructor is in `internal/httpx`.
`make generate`, `make vet`, `make test`, and `make build` are also available. Keep
GOTOOLCHAIN=local for Go 1.25 compatibility; the existing grpc-gateway pin prevents Testcontainers'
transitive test graph from selecting a release requiring Go 1.26.

Docker tests must not silently skip. Use one container per package in TestMain, migrate once,
Reset between tests and do not run tests sharing a harness in parallel. External `_test` packages
avoid the testutil -> server cycle. Retained evaluator, calculator, repository, HTTP and report
cases all exercise the consolidated implementations. Added regressions cover inclusive rule end
on a timed entry, stable balance timestamps and shared multi-year CRUD/GET calculation.

## Operations and compatibility boundaries

Defaults mirror application.yml: DB_URL jdbc:postgresql://localhost:5432/timetrack;
DB_USERNAME/DB_PASSWORD timetrack; SERVER_PORT 8080 (8811 prod); MANAGEMENT_PORT 7080;
JWT_EXPIRATION 86400000 ms; JWT_REFRESH_EXPIRATION 15552000000 ms;
APP_FRONTEND_BASE_URL http://localhost:5173. JWT_SECRET must be configured for deployment.
JDBC and postgres/postgresql URLs are accepted; embedded credentials take precedence.
LOGGING_LEVEL_ROOT and LOGGING_LEVEL_CC_REMER_TIMETRACK configure slog, with Java's production
LOGGING_LEVEL_APP fallback. Clock uses the system timezone; set TZ if required by deployment.

ALLOWED_ORIGINS is parsed but Java currently allows every origin pattern with credentials.
SPA assets cache for a year, other SPA resources use no-cache; API paths never fall back to SPA.
Compression uses production MIME types and a 1024-byte threshold. The separate management listener
provides /actuator/health (DB ping) and /actuator/info ({}). For the existing production healthcheck
use MANAGEMENT_PORT=7180. Shutdown drains for 30 seconds; JVM/metrics endpoints are omitted.

Build from the repository root: `docker build -f backend-go/Dockerfile -t timetrack-go .`.
The Node stage builds the existing frontend; the Go stage embeds it and builds without CGO.

Golden HTTP fixtures compare real Java responses. Multi-violation ordering, malformed JSON/type
errors and SQL-driver diagnostic wording differ by runtime. Validation covers common email checks,
not every Hibernate IDN/coercion edge case. Other locales and generic JVM compatibility are outside
this port. The anonymous-logout exception contains a captured JVM loader suffix that may vary.
The request principal preserves impersonatedBy, unlike Java's current filter, as required by the port.

## Consolidation verification

The pre-consolidation HEAD was independently tested: 266 passed cases including subtests.
The consolidated suite passes 296 cases, including 41 specification operation probes and three
new regressions. No domain test cases were removed; repository tests now call `leavestore`.
`go vet ./...`, `go test ./...`, `gofmt -l .` and whitespace checks pass.

Corrected Java differences: the persistence recurrence adapter compared timestamp instants rather
than calendar dates at the inclusive end boundary; balance reads and no-op updates rewrote
updated_at unconditionally; missing users in the balance/calculator paths surfaced as generic
500 errors instead of IllegalArgumentException/400. Time-entry deletion now explicitly calls the
shared cleanup detector after deletion, matching Java's call despite existing database cascading.
