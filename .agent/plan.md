# Time Tracking API Implementation Plan

## Project Overview
Building a time tracking API with Spring Boot 4, Java 25, and PostgreSQL following an OpenAPI-first, use-case-driven architecture.

## Requirements Summary

### User Requirements
- JWT-based authentication
- Custom working hours per weekday (e.g., 8h Mon-Thu, 6h Fri)
- Clock in/out time entries (with start/end times)
- Admin and User role model
- German client messages, English code

### Technical Stack
- Spring Boot 4
- Java 25
- PostgreSQL database
- OpenAPI specification first (code generation)
- GitHub Actions CI/CD
- Docker containers
- Maven build
- Testcontainers for testing
- Semantic versioning (automated via commits)

### Application Features
1. Authentication (login/logout with JWT)
2. Admin user management
3. User profile with working hours configuration per weekday
4. Time entry creation (clock in/out)
5. Special day flags (sick leave, PTO, events)
6. Statistics (monthly totals vs planned hours)

### Architecture Decisions
- Use-case driven structure (not traditional controller/service/model layers)
- Config as separate package
- OpenAPI spec generates API interfaces
- Continuous documentation in markdown
- Semantic versioning on every commit
- Integration tests required for every use case
- Environment configuration via .env files
- Docker Compose for development and deployment

## REST API Endpoints

### Authentication (`/api/v1/auth`)
- `POST /login` - Login with credentials, returns JWT
- `POST /refresh` - Refresh JWT token
- `POST /logout` - Invalidate token

### User Management (`/api/v1/users`)
- `POST /` - Create user (Admin only)
- `GET /{id}` - Get user details
- `PUT /{id}` - Update user
- `DELETE /{id}` - Delete user (Admin only)
- `GET /me` - Get current user profile

### Working Hours (`/api/v1/working-hours`)
- `GET /` - Get working hours configuration
- `PUT /` - Update working hours per weekday
- `GET /{userId}` - Get working hours for specific user (Admin only)

### Time Entries (`/api/v1/time-entries`)
- `POST /clock-in` - Clock in (creates entry)
- `POST /clock-out` - Clock out (completes entry)
- `GET /` - List time entries (filtered by date range, user)
- `GET /{id}` - Get specific time entry
- `PUT /{id}` - Update time entry
- `DELETE /{id}` - Delete time entry
- `GET /current` - Get current active time entry

### Reports (`/api/v1/reports`)
- `GET /summary` - Get time tracking summary (hours per day/week/month)
- `GET /statistics` - Statistics with actual vs planned hours comparison

## Package Structure (Use-Case Driven)

```
cc.remer.timetrack/
├── TimetrackApplication.java
│
├── domain/                          # Domain entities
│   ├── user/
│   │   ├── User.java
│   │   └── Role.java (enum: ADMIN, USER)
│   ├── workinghours/
│   │   └── WorkingHours.java
│   └── timeentry/
│       ├── TimeEntry.java
│       └── EntryType.java (enum: WORK, SICK, PTO, EVENT)
│
├── usecase/                         # Use cases (business logic)
│   ├── authentication/
│   │   ├── Login.java
│   │   ├── RefreshToken.java
│   │   └── Logout.java
│   ├── user/
│   │   ├── CreateUser.java
│   │   ├── GetUser.java
│   │   ├── UpdateUser.java
│   │   └── DeleteUser.java
│   ├── workinghours/
│   │   ├── GetWorkingHours.java
│   │   └── UpdateWorkingHours.java
│   ├── timeentry/
│   │   ├── ClockIn.java
│   │   ├── ClockOut.java
│   │   ├── ListTimeEntries.java
│   │   ├── GetTimeEntry.java
│   │   ├── UpdateTimeEntry.java
│   │   └── DeleteTimeEntry.java
│   └── statistics/
│       └── GetMonthlyStatistics.java
│
├── adapter/                         # Adapters (infrastructure)
│   ├── web/                        # REST controllers
│   │   ├── AuthenticationController.java
│   │   ├── UserController.java
│   │   ├── WorkingHoursController.java
│   │   ├── TimeEntryController.java
│   │   └── StatisticsController.java
│   ├── persistence/                # Database repositories
│   │   ├── UserRepository.java
│   │   ├── WorkingHoursRepository.java
│   │   └── TimeEntryRepository.java
│   └── security/                   # Security components
│       ├── JwtTokenProvider.java
│       ├── JwtAuthenticationFilter.java
│       └── SecurityConfig.java
│
├── config/                         # Configuration
│   ├── OpenApiConfig.java
│   ├── DatabaseConfig.java
│   └── MessageSourceConfig.java
│
└── exception/                      # Global exception handling
    ├── GlobalExceptionHandler.java
    └── ErrorCode.java (enum)
```

## Database Schema

```sql
-- users table
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- working_hours table
CREATE TABLE working_hours (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    weekday SMALLINT NOT NULL,  -- 1=Monday, 7=Sunday
    hours DECIMAL(4,2) NOT NULL, -- Target hours for this weekday
    is_working_day BOOLEAN DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, weekday)
);

-- time_entries table
CREATE TABLE time_entries (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    entry_date DATE NOT NULL,
    clock_in TIMESTAMP NOT NULL,
    clock_out TIMESTAMP,
    entry_type VARCHAR(20) DEFAULT 'WORK', -- WORK, SICK, PTO, EVENT
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- refresh_tokens table
CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(255) UNIQUE NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- indexes
CREATE INDEX idx_time_entries_user_date ON time_entries(user_id, entry_date);
CREATE INDEX idx_time_entries_clock_in ON time_entries(clock_in);
CREATE INDEX idx_working_hours_user_id ON working_hours(user_id);
```

## Maven Dependencies to Add

### Spring Framework
- `spring-boot-starter-security` - Spring Security for authentication/authorization
- `spring-boot-starter-data-jpa` - JPA/Hibernate for database access
- `spring-boot-starter-validation` - Bean validation

### OpenAPI
- `springdoc-openapi-starter-webmvc-ui` (2.7.0) - OpenAPI 3 documentation + Swagger UI
- `openapi-generator-maven-plugin` (7.11.0) - Generate API interfaces from spec

### JWT
- `jjwt-api` (0.12.6) - JWT API
- `jjwt-impl` (0.12.6) - JWT implementation
- `jjwt-jackson` (0.12.6) - JWT Jackson integration

### Database
- `flyway-core` - Database migrations
- `flyway-database-postgresql` - PostgreSQL Flyway support

### Mapping
- `mapstruct` (1.6.3) - DTO to entity mapping
- `mapstruct-processor` (1.6.3) - MapStruct annotation processor

## OpenAPI Integration Approach

1. **OpenAPI Spec Location**: `/src/main/resources/openapi/api.yaml`
2. **Code Generation**: Maven plugin generates API interfaces during `generate-sources` phase
3. **Generated Packages**:
   - `cc.remer.timetrack.api` - API interfaces
   - `cc.remer.timetrack.api.model` - DTO models
4. **Implementation**: Controllers implement generated interfaces
5. **Workflow**: Spec → Generate → Implement → Build

Example controller:
```java
@RestController
@RequiredArgsConstructor
public class TimeEntryController implements TimeEntriesApi {
    private final ClockIn clockInUseCase;

    @Override
    public ResponseEntity<TimeEntryResponse> clockIn(ClockInRequest request) {
        return clockInUseCase.execute(request);
    }
}
```

## GitHub Actions CI/CD Pipeline

**Workflow File**: `.github/workflows/build.yml`

### Pipeline Steps
1. **Build & Test**
   - Checkout code
   - Setup Java 25
   - Maven build with tests (Testcontainers)
   - Generate test coverage report

2. **Docker Image**
   - Multi-stage Dockerfile
   - Build Docker image
   - Push to container registry (GitHub Container Registry or Docker Hub)
   - Tag with commit SHA and version from pom.xml

3. **Deployment** (optional)
   - Deploy to staging/production environment

## Semantic Versioning Strategy

The project uses **semantic versioning (MAJOR.MINOR.PATCH)** following these conventions:

### Version Bump via Commit Messages

#### MAJOR Version (X.0.0) - Breaking Changes
**Commit prefix**: `BREAKING:` or `breaking:`

Use when making incompatible API changes that break backward compatibility:
```bash
git commit -m "BREAKING: Remove deprecated login endpoint"
git commit -m "breaking: Change time entry response structure"
```

Examples of breaking changes:
- Removing or renaming API endpoints
- Changing required request/response fields
- Modifying authentication mechanism
- Database schema changes that require migrations

#### MINOR Version (0.X.0) - New Features
**Commit prefix**: `feat:` or `feature:`

Use when adding new functionality in a backward-compatible manner:
```bash
git commit -m "feat: Add monthly statistics endpoint"
git commit -m "feature: Implement PTO tracking"
```

Examples of minor changes:
- New API endpoints
- New optional fields in requests/responses
- New database tables (without affecting existing ones)
- New use cases or business logic

#### PATCH Version (0.0.X) - Bug Fixes & Maintenance
**Commit prefix**: `fix:`, `docs:`, `chore:`, `refactor:`, `test:`, `style:`

Use for backward-compatible bug fixes and maintenance:
```bash
git commit -m "fix: Correct time zone handling in clock-out"
git commit -m "docs: Update API documentation"
git commit -m "chore: Update dependencies"
git commit -m "refactor: Simplify JWT token validation"
git commit -m "test: Add integration tests for time entries"
git commit -m "style: Format code according to style guide"
```

Examples of patch changes:
- Bug fixes
- Documentation updates
- Dependency updates
- Code refactoring (no functional changes)
- Test improvements
- Performance improvements

### Automation
The version in `pom.xml` will be automatically bumped by the GitHub Actions workflow based on commit message prefixes. The workflow will:
1. Parse the commit message
2. Determine version bump type
3. Update `pom.xml` version
4. Tag the Docker image with the new version
5. Create a git tag for the release

## Implementation Phases

### Phase 1: Foundation
- Initialize git repository
- Update `pom.xml` with all dependencies
- Setup Flyway migrations (database schema)
- Create OpenAPI specification skeleton
- Setup basic project structure (packages)
- Configure Spring Security (disable for now)
- Setup version automation in GitHub Actions

### Phase 2: Domain & Persistence
- Create domain entities (User, TimeEntry, WorkingHours)
- Create JPA repositories
- Write Flyway migration scripts
- Setup database configuration
- Write integration tests for repositories
- Add application.yml with database config

### Phase 3: Authentication & Security
- Implement JWT token provider
- Create authentication use cases (Login, Logout, RefreshToken)
- Configure Spring Security with JWT filter
- Add security to OpenAPI spec
- Implement password hashing
- Write integration tests for authentication use cases

### Phase 4: User Management
- Complete OpenAPI spec for user endpoints
- Implement user CRUD use cases
- Create UserController implementing generated API
- Add role-based access control
- Create admin user seeding script

### Phase 5: Working Hours
- Complete OpenAPI spec for working hours
- Implement working hours use cases
- Create WorkingHoursController
- Initialize default working hours on user creation
- Add validation for weekday hours

### Phase 6: Time Tracking
- Complete OpenAPI spec for time entries
- Implement ClockIn/ClockOut use cases with validation
- Implement time entry CRUD use cases
- Create TimeEntryController
- Add entry type handling (WORK, SICK, PTO, EVENT)
- Prevent overlapping time entries

### Phase 7: Statistics & Reports
- Complete OpenAPI spec for statistics
- Implement statistics calculation use case
- Calculate actual vs planned hours
- Create StatisticsController
- Add monthly/weekly summaries

### Phase 8: Polish & Deploy
- Add German message properties (`messages_de.properties`)
- Global exception handling with localized messages
- Integration tests with Testcontainers
- Create Dockerfile (multi-stage build)
- Setup complete GitHub Actions workflow with versioning
- API documentation (Swagger UI configuration)
- README with setup instructions

## Critical Files

1. **pom.xml** - Add all dependencies and OpenAPI generator plugin
2. **src/main/resources/openapi/api.yaml** - Complete OpenAPI specification
3. **src/main/resources/db/migration/V1__initial_schema.sql** - Database schema
4. **src/main/java/cc/remer/timetrack/adapter/security/SecurityConfig.java** - JWT security
5. **src/main/java/cc/remer/timetrack/usecase/timeentry/ClockIn.java** - Core time tracking logic
6. **.github/workflows/build.yml** - CI/CD pipeline with version automation
7. **Dockerfile** - Container image build
8. **src/main/resources/application.yml** - Application configuration
9. **src/main/resources/messages_de.properties** - German localization

## Documentation Strategy

Every significant change will be documented in `.agent/` directory:
- `.agent/plan.md` - This implementation plan (updated as needed)
- `.agent/progress.md` - Development progress log
- `.agent/decisions.md` - Architectural decisions and rationale
- `.agent/api-changelog.md` - API changes per version
