# Go backend foundation

This module ports the existing Java application. The root `openapi.yaml`, Java migrations,
Java security classes, and use cases remain the behavioral references. Run commands here with
`export PATH=$HOME/.local/go/bin:$HOME/go/bin:$PATH`. No Java or frontend source changes are needed.

## Package structure and ownership

| Location | Responsibility | Ownership |
| --- | --- | --- |
| `cmd/timetrack` | Configuration, migration, two listeners, graceful shutdown | Foundation |
| `internal/api` | Strict net/http server and models generated from the root specification | Generated |
| `internal/server/server.go` | Dependencies and automatic registration of every operation | Shared, additive only |
| `internal/server/handlers_auth.go` | Public authentication endpoints | Foundation |
| `internal/auth` | JWT, principal, authorization, sessions, invites, impersonation | Shared, additive only |
| `internal/config`, `internal/db`, `internal/clock` | Environment, pgx boundaries, time | Shared, additive only |
| `internal/httpx` | Central exception mapping | Shared, additive only |
| `internal/migrate` | Embedded SQL and Flyway-compatible history | Shared, additive only |
| `internal/domain` | Shared enums, day precedence, holiday calculation | Shared, additive only |
| `internal/testutil` | PostgreSQL/Testcontainers and full-router HTTP helpers | Shared, additive only |
| `internal/server/handlers_timeentries.go`, `handlers_reports.go`, `handlers_publicholidays.go` | Time entries, clock in/out, daily summary, reports, holidays | A |
| `internal/timeentries`, `internal/reports`, `internal/publicholidays` | Repositories and use cases | A |
| `internal/server/handlers_users.go`, `handlers_workinghours.go`, `handlers_vacationbalance.go` | Users, invite issuance, impersonation, hours, vacation balance | B |
| `internal/users`, `internal/workinghours`, `internal/vacationbalance` | Repositories and use cases | B |
| `internal/server/handlers_timeoff.go`, `handlers_recurringoffdays.go`, `handlers_conflictwarnings.go` | Time off, recurring days and exemptions, conflict warnings | C |
| `internal/timeoff`, `internal/recurringoffdays`, `internal/conflictwarnings` | Repositories and use cases | C |
| `web` | Embedded SPA; Docker copies the built frontend into `web/dist` | Foundation |

Every non-auth operation deliberately returns `501` with `NOT_IMPLEMENTED` after authentication,
role checks, and request validation. Users/invite and users/impersonation HTTP endpoints also remain
stubs; their auth services are already implemented for B. Invalid or unauthorized requests retain
security/validation responses rather than bypassing the router to return 501.

Do not change registration to implement an operation. All methods already belong to `*server.Server`
and implement `api.StrictServerInterface`. The server supplies `Pool`, `Clock`, `Config`, `Auth`,
and `Logger`. A domain handler can construct its own service using these dependencies, or add a
factory in its own handler file. This avoids changes to a shared constructor/registration file.
Add domain-specific types to that domain's package, not to generated API types.

## Implementing a handler

Replace the body of the relevant stub, retain its generated signature, and return the generated
success response type. Return an error for central mapping instead of writing the response:

```go
func (s *Server) GetTimeEntries(
    ctx context.Context, r api.GetTimeEntriesRequestObject,
) (api.GetTimeEntriesResponseObject, error) {
    principal, _ := auth.Current(ctx) // /api/** has already authenticated the request.
    service := timeentries.Service{Pool: s.Pool, Clock: s.Clock}
    entries, err := service.List(ctx, principal.ID, r.Params)
    if err != nil {
        return nil, err
    }
    return api.GetTimeEntries200JSONResponse(entries), nil
}
```

This example illustrates the convention; `timeentries.Service` is intentionally left to A.
Use `auth.RequireRole(ctx, domain.Admin)` for domain-level checks, and
`auth.ValidateOwnership(&ownerID, &principal.ID, "...")` for Java ownership checks.
The principal includes ID, email, current database role/active state, and optional impersonatedBy.
The JWT filter reloads the user by email just like Java. It accepts both access and refresh JWTs
as bearer credentials and does not reject inactive users at filter time, matching Java.
Impersonation metadata is retained in context (Java's filter currently drops it).

`Auth.GenerateInvite(ctx, userID)` and `Auth.Impersonate(ctx, userID)` are ready for B's handlers.
For impersonation, use the request context so the initiating admin is available.

## SQL and transactions

Use pgx/v5 directly, with `$1`, `$2`, ... placeholders and explicit column lists. Never concatenate
untrusted identifiers or values. `auth.FindUser` accepts only code-owned predicate strings.
Repositories can accept `db.Querier` to work with either `*pgxpool.Pool` or `pgx.Tx`.
Use `db.Transaction(ctx, pool, func(tx pgx.Tx) error { ... })` for atomic use cases.
Returning an error rolls the transaction back, including any attempted deletions, as production
Spring `@Transactional` does for unchecked exceptions. The Java expired-refresh test observes a
delete inside its surrounding test transaction; production rolls that delete back on the exception.

Pool limits mirror Hikari production: max 10, min 2, connect timeout 30 s, idle 10 min, lifetime 30 min.
Pool connection creation is lazy; always pass request context to queries.

The migrator copies all 17 Java migration files, preserving missing V7/V8. A test compares the
copies byte for byte. Its CRC32 implementation follows Flyway's `ChecksumCalculator`: remove the
initial BOM, discard CR/LF delimiters, feed UTF-8 bytes to CRC32, and store a signed int32.
`testdata/flyway-11.20.3-checksums.txt` was captured from a real Flyway-created PostgreSQL 17 DB;
all checksums were independently compared with Go, and Go startup against that history succeeded.
Migration execution and its history insert share a transaction; a dedicated connection holds a
session advisory lock across creation, validation, and execution. Failed history rows, changed
checksums, missing applied SQL files, and unsupported history types abort startup. BASELINE sets
a version floor; SCHEMA rows are retained and ignored. This repository uses integer versions;
non-integer Flyway versions fail explicitly rather than being misordered.

## Errors and validation

`httpx.Error` has Status, Code, Message, and optional string Details. Constructors:
`NotFound(code, message)`, `Conflict(code, message)`, `Forbidden(message)`,
`InvalidArgument(message)`, `Validation(details)`, `New(status, code, message)`.
Copy codes/messages from the Java exception handler/use case; do not invent domain messages.
The error JSON always includes `code`, `message`, `details`, `timestamp` (including null details).
Unknown errors map to 500 / INTERNAL_ERROR / `Ein interner Fehler ist aufgetreten` and expose the
underlying error as details, as Java does. Avoid wrapping public domain errors in new text.

Validation traverses the embedded spec for required fields, string/array sizes, numeric ranges,
patterns, email and date fields before strict decoding. Details use Hibernate's English default
messages joined with `, `, not an array. Go's malformed JSON/type errors use the generic
500 mapping from Java's catch-all handler, but their detail wording differs from Jackson. EN/DE
validation messages are supported. Other localized Hibernate messages,
provider-specific violation ordering, and full Java coercion semantics are not reproduced.

Java's `@PreAuthorize` denial reaches its catch-all exception advice: the port uses
500 / INTERNAL_ERROR with `Access Denied` details. Domain `ForbiddenException` maps to 403.
Missing/invalid authentication uses the Spring Boot sendError envelope
`timestamp,status,error,path`, not ErrorResponse. Anonymous `/api/auth/logout` is public in Java;
it reaches a principal cast failure rather than a security 401. The port uses the exact captured
Java Docker exception detail; its JVM loader suffix can vary between Java runtimes.

## JSON and dates

`go generate ./...` runs pinned oapi-codegen v2.5.1, then the deterministic `scripts/jsoncompat.go`
postprocessor. The specification needs no structural workaround. Do not edit generated.go manually.
The postprocessor removes `omitempty` to match Jackson's default null inclusion. It makes
UserResponse firstName/lastName/state pointers because Java AuthSessionService deliberately
returns a partial UserResponse with these fields null despite their required specification.
Its halfDayHolidaysEnabled default is false; pending, inviteUrl and timestamps are null in auth.
For domain responses, populate required values and initialize empty collections when Java returns
an empty list; nil slices serialize as null. Do not omit null properties.

Use generated `openapi_types.Date` for `YYYY-MM-DD`, and `time.Time` for OffsetDateTime/RFC3339
with fractional seconds and offset. Do not serialize dates via UTC conversion of local midnight.
PostgreSQL TIMESTAMP WITHOUT TIME ZONE stores wall time. Use `clock.LocalTimestamp(c.Now())` for
Java LocalDateTime.now(clock). `MapperUtils` attaches UTC to stored wall time without conversion;
pgx's timestamp decoding already supplies that representation. `Clock.Now` uses the system zone,
as `ClockConfig.systemDefaultZone()` does. Inject `clock.Fixed` for deterministic tests.

JWT secrets are raw UTF-8 bytes, not Base64-decoded. Length >=64 bytes signs HS512, >=48 HS384,
otherwise >=32 HS256, matching JJWT Keys.hmacShaKeyFor/signWith. Claims are sub (decimal string),
email, comma-separated authorities (`ROLE_ADMIN`/`ROLE_USER`, plus `FACTOR_PASSWORD` for password
login under Spring Security 7), iat/exp (seconds), optional numeric
impersonatedBy. There is no jti or token-purpose discriminator. Identical user/claims/second create
identical refresh tokens and can hit the existing unique constraint; preserve this unless separately
changing the contract. Fixed Java-generated fixtures cover all algorithms; a Go-issued token was
also verified by JJWT 0.12.6. `scripts/interop/Reference.java` records the reference procedure.
Existing `$2a$` admin hashes are tested with x/crypto/bcrypt.

## Test harness

Do not silently skip Docker tests. Use one container per test package in TestMain, migrations once,
and `Reset(t)` between tests (TRUNCATE users CASCADE, sequences reset, history retained).
Tests sharing a harness must not call `t.Parallel`. New domain packages can use external `_test`
packages to avoid the testutil -> server import cycle.

```go
var h *testutil.Harness
func TestMain(m *testing.M) {
    ctx := context.Background()
    var err error
    h, err = testutil.Start(ctx)
    if err != nil { fmt.Fprintln(os.Stderr, err); os.Exit(1) }
    code := m.Run()
    h.Close(ctx)
    os.Exit(code)
}
func TestExample(t *testing.T) {
    h.Reset(t)
    password := "password"
    h.User(t, "user@test.local", domain.User, &password) // nil password creates a pending user.
    _, srv := h.Server(t, clock.Fixed{Time: time.Date(2030,1,15,12,0,0,0,time.UTC)})
    session := testutil.Login(t, srv.URL, "user@test.local", password)
    status, body := testutil.Request(t, srv.URL, "GET", "/api/time-entries", session.AccessToken, nil)
    // Assert the endpoint's expected status and JSON.
    _, _ = status, body
}
```

Tests in `internal/integration` cover default migration admin login, JWT security, roles, logout,
refresh success/errors, pending/reset registration, invalid/expired/consumed invites, invite
replacement, impersonation, CORS, SPA, health, migration validation/locking/idempotency, JSON nulls,
and all 36 stub operations. `internal/auth` has fixed JJWT fixtures and legacy BCrypt checks.

## Environment and operations

Defaults come from application.yml, with production overrides when SPRING_PROFILES_ACTIVE includes
prod: DB_URL jdbc:postgresql://localhost:5432/timetrack; credentials timetrack/timetrack;
SERVER_PORT 8080 (8811 prod); MANAGEMENT_PORT 7080; JWT_EXPIRATION 86400000 ms;
JWT_REFRESH_EXPIRATION 15552000000 ms; APP_FRONTEND_BASE_URL http://localhost:5173;
JWT_SECRET your-secret-key-change-this-in-production-min-256-bits.
JDBC PostgreSQL URLs and plain postgres/postgresql URLs are accepted, including query parameters.
Embedded URL credentials take precedence over DB_USERNAME/DB_PASSWORD.
LOGGING_LEVEL_ROOT and LOGGING_LEVEL_CC_REMER_TIMETRACK set slog level; production also recognizes
the Java LOGGING_LEVEL_APP fallback. Access and refresh durations honor the requested environment
variables even though the current YAML literal values do not contain placeholders.

ALLOWED_ORIGINS is parsed, but the current Java SecurityConfig unconditionally permits every origin
pattern, allows credentials, all headers, GET/POST/PUT/DELETE/PATCH/OPTIONS, and max-age 3600.
The port preserves that behavior. Restricting origins requires an explicit future contract change.
Assets use max-age=31536000, public, immutable; other SPA resources use no-cache, must-revalidate,
as the more specific WebMvcConfig overrides the generic production resource-cache configuration.
Compression covers the production MIME list at >=1024 bytes. API routes never fall back to SPA.

`/actuator/health` on the separate management listener pings PostgreSQL and returns UP/200 or
DOWN/503; `/actuator/info` returns `{}`. Use MANAGEMENT_PORT=7180 for the existing production
healthcheck. The Docker HEALTHCHECK reads MANAGEMENT_PORT, defaulting to 7080. Runtime is non-root
Alpine with wget, CA certificates, and tzdata. Set TZ if the existing deployment uses a non-UTC
system timezone. No host-specific timezone is assumed.

ANNAHME: metrics/prometheus/JVM management endpoints are omitted, as permitted in phase one.
ANNAHME: shutdown drains requests for 30 seconds (no explicit timeout in repository config).
ANNAHME: the new 501 message uses HTTP's `Not Implemented`; Java has no 501 implementation.
The Spring Boot 4 reference HTTP comparison verified the security sendError envelope and millisecond
timestamp, auth response nulls, login authorities, invalid credentials, missing fields, invalid email,
invalid refresh, and invite-not-found errors. Application errors use ISO offset timestamps.
Validation defaults to English and honors German Accept-Language requests.

## Java to Go reference map

| Java source | Go counterpart |
| --- | --- |
| `config/JwtProperties`, `resources/application*.yml` | `internal/config/config.go` |
| `config/ClockConfig` | `internal/clock/clock.go` |
| `adapter/security/JwtTokenProvider` | `internal/auth/jwt.go` |
| `adapter/security/UserPrincipal`, `usecase/AuthorizationService` | `internal/auth/jwt.go` |
| `adapter/security/{JwtAuthenticationFilter,SecurityConfig}` | `internal/server/security.go` |
| `usecase/authentication/*` | `internal/auth/service.go`, `internal/server/handlers_auth.go` |
| `usecase/user/{GenerateInviteToken,ImpersonateUser}` | `internal/auth/service.go`; B wires users handlers |
| `exception/GlobalExceptionHandler` | `internal/httpx/errors.go` |
| Generated Spring API/models | `internal/api/generated.go` |
| `config/WebMvcConfig` | `web/web.go` |
| `domain/DayTypePrecedence`, domain enums | `internal/domain/types.go` |
| `domain/publicholiday/GermanPublicHolidays` | `internal/domain/holidays.go` |
| `resources/db/migration/*` | `internal/migrate/sql/*` |
| `RepositoryTestBase`, `TimetrackPostgresContainer` | `internal/testutil/harness.go` |
| Auth/invite/impersonation tests | `internal/integration/auth_test.go`, `internal/auth/jwt_test.go` |

## Commands

`make generate`, `make vet`, `make test`, `make build`, or the equivalent Go commands.
The grpc-gateway dependency is pinned for Go 1.25: Testcontainers' transitive test graph otherwise
resolves a newer version requiring Go 1.26. Keep GOTOOLCHAIN=local when checking compatibility.
Build the complete image from the repository root:
`docker build -f backend-go/Dockerfile -t timetrack-go .`.
The Node stage installs dependencies, generates the existing frontend client, and builds Vite.
The Go stage copies this dist into the embed tree and builds CGO_ENABLED=0 with trimpath and stripped symbols.

## Known compatibility boundaries

The EN/DE HTTP golden fixtures compare real Java responses. For multiple validation violations,
Hibernate's iteration order is unspecified; Go emits a stable field order, so the joined details
string can have a different order. Error diagnosis text for malformed JSON, binding failures, and
SQL driver errors is Go-specific. The email validator implements the common address checks rather
than every Hibernate/IDN edge case. Enum deserialization/coercion semantics are not a full Jackson
emulation. Other validation locales are not provided. Integer Flyway versions cover this repository;
repeatable/Java migrations are rejected rather than silently treated as SQL.

The request principal preserves impersonatedBy, unlike the current Java filter, as required for the
new shared context. Java's request filter still reloads role and active status from the database.
The anonymous-logout class-loader detail is a captured runtime diagnostic and may differ from a
Java deployment with a different loader identity. These boundaries should not be mistaken for a
complete generic JVM compatibility layer when extending the domain handlers.
