# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Dashboard: Quick Clock-Out Button**: New button to clock out without clocking in first
  - Uses start time from current weekday's working hours configuration
  - End time set to current time
  - Creates completed time entry using break minutes from working hours configuration
  - Validation: Shows error toast if no working hours configured for today
  - Validation: Shows error toast if start time is after current time (prevents negative work time)
  - Only visible when no active clock-in session exists
  - Styled with orange/coral theme matching clock-out action
  - Translations: "Quick Clock-Out" (EN), "Ausstempeln" (DE)

### Changed
- **Schedule View: Working Hours Save Optimization**: Eliminated redundant GET request after PUT
  - PUT endpoint already returns updated working hours in response
  - Reduced API calls from 2 to 1 when saving working hours configuration
  - Improved save operation performance and reduced network overhead

- **Public Holidays API**: Optimized to return all years (2023-2027) and states (BERLIN, BRANDENBURG) in a single request
  - Backend now returns nested map structure: `Map<year, Map<state, List<Holiday>>>`
  - Frontend caches all holiday data on first load
  - Switching years/states now instant with no network requests
  - Reduced API calls from up to 10 (5 years × 2 states) to just 1
  - Payload remains small (~100 holiday objects total)
  - OpenAPI spec updated with new `PublicHolidaysResponse` schema
  - Removed query parameters from `/api/public-holidays` endpoint

### Added
- **Admin User Impersonation**: Administrators can now temporarily authenticate as another user without their password
  - New `/api/users/{id}/impersonate` endpoint with admin-only access
  - Compact red warning indicator in navbar (pulsing icon + user email + exit button)
  - Admin credentials stored in sessionStorage during impersonation
  - Automatic session cleanup on logout
  - JWT tokens include `impersonatedBy` claim for audit trail
  - Security: Admins cannot impersonate other admins
  - Backend: ImpersonateUser use case, enhanced UserPrincipal with Builder pattern, JwtTokenProvider updated
  - Frontend: Impersonate button in AdminUsersView, navbar indicator, proper localStorage key usage
  - i18n: German (informal du) and English translations

### Changed
- **Frontend Design System**: Redesigned color scheme based on logo gradient (lime-to-teal)
  - Replaced previous purple-based palette with logo-inspired colors
  - Applied 60-30-10 color distribution rule (60% neutral, 30% structure, 10% accent)
  - Updated all action cards and stat cards with new gradient colors
  - Standardized dialog widths: 550px for forms, 600px for editors
  - Aligned spacing to 8px/4px grid system throughout
  - Color palette: Lime green (#a3e635), Teal (#14b8a6), Cyan (#22d3ee), Emerald (#10b981), Yellow (#eab308), Coral (#f97316)
  - Enhanced undo button visibility with larger size and white border
  - Improved navbar alignment and color hierarchy

- **Navigation Structure**: Flattened navigation by making time tracking items top-level menu entries
  - Removed nested "Time Tracking" submenu
  - All time tracking views now directly accessible from main navbar
  - Added clickable logo and brand name to navbar for easy dashboard access
  - Time Off View: Replaced year dropdown with button group for better usability

- **Branding & Assets**:
  - Added favicons and web manifest from RealFaviconGenerator
  - Optimized assets by replacing 1MB+ SVG with PNGs
  - Added logo to login page
  - Updated favicon paths in navbar and login view

- **Responsive Design**: Improved responsive table layout
  - Fixed working hours table layout on mobile with scroll support
  - Set minimum column widths to prevent overlap (pause: 110px, hours: 90px)
  - Fixed InputNumber buttons with proper width and flex-shrink
  - Reduced table cell padding on mobile for more compact layout

- **German Localization**: Changed remaining formal "Sie" to informal "du" in German translations
  - Updated user-facing messages for consistency
  - All German text now uses informal address throughout the application

- **Default Admin Password**: Updated from "admin" to "admin1" (meets 6-character minimum requirement)
  - Database migration V14 updates existing admin user password
  - New BCrypt hash generated with strength 10
  - Documentation updated across README.md and CLAUDE.md

### Fixed
- **Token Refresh for Invalid Access Tokens**: Router guard now always validates tokens with server
  - Ensures invalid or corrupted access tokens (not just expired) trigger automatic refresh
  - Previously only validated when no token present, broken tokens never detected
  - Frontend: router/index.ts

- **CSS Selector Issues**: Removed invalid :deep() selectors from global CSS files
  - Fixed style.css and data-tables.css to use proper scoped selectors
  - Improved CSS specificity and maintainability

### Added
- **Half-Day Holidays (December 24 & 31)**: Users can enable half-day holiday counting for Christmas Eve and New Year's Eve
  - User setting in profile: "Halbe Feiertage (24. & 31. Dez.)" with tooltip explanation
  - Admins can configure the setting for users during user management
  - When enabled, December 24th and 31st count as 0.5 vacation days instead of 1.0
  - Each day is calculated independently (taking only one day still counts as 0.5)
  - Automatic calculation in vacation balance deduction
  - Fractional days displayed with 1 decimal place (e.g., "3.5 days") in all views
  - Visual indicators: 🌗 (half moon) emoji appears before 🏝️ in calendar for Dec 24/31 vacation days
  - Backend: Database migration V13, new User entity field, BigDecimal support in WorkingDaysCalculator
  - Frontend: Checkbox with info tooltip in profile and admin user management views, proper decimal formatting
  - i18n translations: German ("Halbe Feiertage") and English ("Half-Day Holidays")

- **Recurring Off-Day Conflict Warnings**: System now detects and warns when work entries occur on configured recurring off-days
  - Orange outline highlights conflicting days in the dashboard calendar (persists after acknowledgment)
  - Warning icon (⚠️) appears in Time Entries view for affected rows with pulsing animation
  - Warnings card in dashboard overview section shows all unacknowledged conflicts
  - Users can acknowledge warnings while the visual indicators remain for reference
  - Automatic cleanup: warnings are removed when the associated time entry is deleted
  - Supports future expansion with additional warning types
  - Backend: New database table, REST API endpoints, automatic conflict detection
  - Frontend: WarningsCard component, calendar highlighting, time entries row indicator

- **Child Sick Leave Type**: New absence type "Kind krank" (Child Sick) with 👩‍👧 emoji
  - Backend: Added CHILD_SICK enum value to TimeOffType
  - Frontend: Added translations in German ("Kind krank") and English ("Child Sick")
  - Calendar: Child sick days displayed with 👩‍👧 emoji in monthly calendar
  - Time Off View: Added statistics card showing total child sick days for the year
  - Behavior: Same validation and precedence as regular sick leave (no overlapping entries)
  - Database migration V11 added to support the new type

- **State Selection**: Users can now select their German state (Bundesland) in their profile settings
  - State dropdown in profile view with Berlin and Brandenburg options
  - State field exposed in user API (UserResponse, CreateUserRequest, UpdateUserRequest)
  - Public holidays will be correctly applied based on user's selected state
  - Backend: State stored in database and included in user model
  - Frontend: State selector with i18n translations (German and English)
  - Default state for new users: Berlin

- **Public Holidays in Dashboard Calendar**: Public holidays now appear automatically in the monthly calendar
  - Public holidays fetched based on user's selected state (Berlin or Brandenburg)
  - Holidays displayed with orange background color (highest visual precedence)
  - Holiday names shown in day details overlay (e.g., "Weihnachten", "Neujahr")
  - Automatic integration with daily summaries

### Fixed
- **Time-Off Days Calculation**: Fixed recurring off-days not being excluded from individual vacation entries
  - Vacation balance card correctly excluded recurring off-days
  - But individual TimeOffResponse entries in list were showing incorrect day counts (not excluding recurring off-days)
  - Root cause: WorkingDaysCalculator was skipping recurring off-day checks for ALL time-off entries when calculating individual entry days
  - Fix: Only skip recurring off-day checks for sick/personal days; always check for vacation entries
  - Now both vacation balance card and time-off list show consistent day counts (both excluding recurring off-days)
- **Profile View**: Fixed firstName and lastName not loading correctly
  - Profile data now fetched fresh from API on page load
  - User data in localStorage updated after successful profile save
  - Added proper error handling for profile load failures

## [1.0.0] - 2025-12-12

### Added - Deployment & Production Infrastructure
- **Complete CI/CD Pipeline**: GitHub Actions workflows for continuous integration and Docker image building
  - Maven CI workflow: Automated testing on push and pull requests
  - Docker workflow: Automated Docker image building and publishing to GitHub Container Registry (ghcr.io)
- **Production-Ready Docker Setup**: Single-image architecture with frontend bundled into backend
  - Multi-stage Dockerfile: Builds both frontend (Node.js) and backend (Maven) in one unified image
  - Spring Boot serves both API endpoints and static frontend files
  - Optimized layer caching for faster builds
- **Production Configuration Files**:
  - `docker-compose.prod.yml`: Production-ready Docker Compose configuration
  - `.env.example.prod`: Environment variable template for production
  - `application-prod.yml`: Spring Boot production profile with security hardening
  - `Caddyfile.example`: Caddy reverse proxy configuration with automatic HTTPS
- **Web MVC Configuration**: SPA routing fallback for Vue.js frontend (WebMvcConfig.java)
- **Comprehensive Deployment Guide**: DEPLOYMENT.md with step-by-step instructions including:
  - Server prerequisites and setup
  - GitHub Container Registry authentication
  - Production deployment procedures
  - Backup and restore procedures
  - Troubleshooting guide
  - Security best practices
  - Maintenance tasks checklist
- **Version 1.0.0**: Updated package versions for initial production release

### Changed
- Backend Dockerfile: Enhanced with multi-stage build to include frontend static files
- .gitignore: Added production environment files (.env.prod, docker-compose.prod.override.yml)
- Package versions: Updated from 0.0.1-SNAPSHOT to 1.0.0 (both backend and frontend)

### Security - Production Hardening
- Flyway clean disabled in production (prevents accidental data loss)
- SQL logging disabled in production
- Error messages sanitized (no stack traces exposed)
- Security headers configured in Caddy (HSTS, X-Frame-Options, CSP, etc.)
- CORS restricted to production domain only
- Database not exposed publicly (only accessible within Docker network)
- Application runs as non-root user in container
- Response compression enabled for performance

### Infrastructure
- Domain: zeit.remer.cc
- Registry: GitHub Container Registry (ghcr.io)
- Deployment: Manual pull and restart (no automated CD)
- HTTPS: Automatic SSL/TLS via Caddy and Let's Encrypt
- Database: PostgreSQL 17 with persistent named volumes

## [0.0.1] - 2025-12-10

### Added
- Initial project setup with Spring Boot 4 and Java 25
- PostgreSQL 17 database with Flyway migrations
- OpenAPI-first approach with code generation
- Docker Compose for development environment
- JWT-based authentication (access + refresh tokens)
- User domain with Role-based access control (ADMIN, USER)
- Time entry domain with EntryType support (WORK, SICK, PTO, EVENT)
- Working hours configuration per weekday
- Refresh token domain with expiration handling
- Integration test infrastructure with Testcontainers
- Health endpoint via Spring Boot Actuator

### Authentication Endpoints
- `POST /api/auth/login` - User login with email/password
- `POST /api/auth/refresh` - Refresh access token
- `POST /api/auth/logout` - Logout and invalidate tokens

### Fixed
- Spring Security URL patterns to match context path configuration
- BCrypt password hash format from $2a$ to $2y$ for compatibility
- OpenAPI server URL from /api/v1 to /api (removed version prefix)
- Hibernate DDL auto setting to "none" for Flyway control
- Authentication entry point for proper 401 error responses

### Security
- JWT tokens using HS384 algorithm
- Access tokens expire in 24 hours
- Refresh tokens expire in 7 days
- Stateless session management
- CORS configuration with credentials support
- BCrypt password encoding with strength 10

### Testing
- All authentication endpoints tested and verified
- Health endpoint verified
- Integration test base classes for repositories

### Database
- Default admin user: admin@timetrack.local (password: admin)
- Default working hours: Mon-Fri 8h, Weekends 0h
- Complete schema with foreign key constraints
- Proper indexes for performance

### Documentation
- Complete OpenAPI specification
- README with setup instructions
- Docker Compose configuration
- Environment variable configuration (.env.example)
- Implementation plan with phases

## [Unreleased] - 2025-12-11

### Added - Working Hours Configuration
- Working hours configuration endpoints
  - `GET /api/working-hours` - Get working hours for current user
  - `PUT /api/working-hours` - Update working hours configuration
  - `GET /api/working-hours/{userId}` - Get working hours for specific user (Admin only)
- Working hours DTOs: WorkingHoursResponse, WorkingDayConfig, UpdateWorkingHoursRequest, UpdateWorkingDayConfig
- GetWorkingHours use case with admin override
- UpdateWorkingHours use case with comprehensive validation
- WorkingHoursMapper for entity-DTO conversion
- WorkingHoursController implementing generated API
- 11 comprehensive integration tests for working hours functionality

### Features - Working Hours
- Flexible working hours configuration (different hours per day)
- Part-time working hours support
- Weekend/non-working day support
- Validation for working hours data (exactly 7 days, valid ranges, no duplicates)
- German error messages for validation failures

## [Unreleased] - 2025-12-12

### Added - Advanced Time Tracking Backend (Phase 7)

#### Public Holidays API
- `GET /api/public-holidays` - Get German public holidays for specific year and state
- German public holidays calculator for Berlin and Brandenburg states
- Support for fixed holidays (New Year, Labour Day, German Unity Day, Christmas)
- Support for movable holidays based on Easter (Good Friday, Easter Monday, Ascension Day, Whit Monday)
- State-specific holidays (International Women's Day for Berlin, Reformation Day for Brandenburg)
- 5 integration tests for public holidays functionality

#### Recurring Off-Days API
- `GET /api/recurring-off-days` - Get all recurring off-day patterns
- `POST /api/recurring-off-days` - Create new recurring off-day rule
- `PUT /api/recurring-off-days/{id}` - Update recurring off-day rule
- `DELETE /api/recurring-off-days/{id}` - Delete recurring off-day rule
- Support for two pattern types:
  - EVERY_NTH_WEEK - Every nth week (e.g., every 4th Monday)
  - NTH_WEEKDAY_OF_MONTH - Nth weekday of month (e.g., 4th Monday of every month)
- Complete use cases: GetRecurringOffDays, CreateRecurringOffDay, UpdateRecurringOffDay, DeleteRecurringOffDay
- RecurringOffDayMapper for entity-DTO conversion
- RecurringOffDaysController with full authorization
- 11 integration tests for recurring off-days functionality

#### Time-Off Tracking API
- `GET /api/time-off` - Get all time-off entries with date range filtering
- `POST /api/time-off` - Create new time-off entry
- `PUT /api/time-off/{id}` - Update time-off entry
- `DELETE /api/time-off/{id}` - Delete time-off entry
- Support for four time-off types: VACATION, SICK, PERSONAL, PUBLIC_HOLIDAY
- Optional custom hours-per-day override
- Complete use cases: GetTimeOffEntries, CreateTimeOff, UpdateTimeOff, DeleteTimeOff
- TimeOffMapper for entity-DTO conversion
- TimeOffController with full authorization
- 12 integration tests for time-off functionality

#### Vacation Balance API
- `GET /api/vacation-balance` - Get vacation balance for specific year
- `PUT /api/vacation-balance` - Update vacation balance (Admin only)
- Automatic calculation of remaining days
- Support for annual allowance, carried-over days, adjustments, and used days
- Complete use cases: GetVacationBalance, UpdateVacationBalance
- VacationBalanceMapper for entity-DTO conversion
- VacationBalanceController with admin-only update
- 9 integration tests for vacation balance functionality

### Database Schema (Phase 7)
- `recurring_off_days` table with two pattern types support
- `time_off` table with support for all time-off types
- `vacation_balance` table with automatic remaining days calculation
- Added `state` column to users table for German state (BERLIN, BRANDENBURG)
- Optional `start_time` and `end_time` columns in working_hours table

### Testing Infrastructure
- Centralized test fixture methods in RepositoryTestBase
- Fixture methods for users, working hours, recurring off-days, time-off, and vacation balance
- Eliminated 100+ lines of repetitive test setup code
- **99 integration tests passing** (37 new tests for Phase 7 features)
- All tests use standardized fixtures for consistency

### OpenAPI Specification
- Extended with 4 new API endpoint groups
- Comprehensive request/response models for all new features
- Proper validation rules and error responses
- TypeScript API client generated from updated spec

### Features - Advanced Time Tracking
- German public holidays calculation with state-specific support
- Flexible recurring off-day patterns (weekly and monthly)
- Multi-type time-off tracking (vacation, sick, personal, holidays)
- Vacation balance management with automatic calculations
- Date range filtering for time-off queries
- User-owned data with proper authorization checks
- German error messages for all validation failures

## [Unreleased] - 2025-12-12 (Phase 8)

### Added - Advanced Time Tracking Frontend

#### New Views
- **PublicHolidaysView** - Display German public holidays by year and state
  - Year and state selection filters
  - Formatted date display with weekday names
  - German localization
- **RecurringOffDaysView** - Complete CRUD for recurring off-day patterns
  - Create/Edit/Delete recurring patterns
  - Support for EVERY_NTH_WEEK pattern (e.g., every 4 weeks)
  - Support for NTH_WEEKDAY_OF_MONTH pattern (e.g., 4th Monday)
  - Active/inactive toggle
  - Start/End date configuration
- **TimeOffView** - Time-off entry management
  - Create/Edit/Delete time-off entries
  - Four types: VACATION, SICK, PERSONAL, PUBLIC_HOLIDAY
  - Date range filtering (default: current year)
  - Optional hours-per-day override
  - Automatic days calculation
  - Color-coded type badges
- **VacationBalanceView** - Vacation balance dashboard
  - Year selector
  - Summary card with allowance breakdown
  - Visual progress indicator (Knob component)
  - Admin-only editing capability
  - Automatic remaining days calculation

#### Navigation & UX
- Added "Time Tracking" submenu in navigation with 4 items
- Integrated new routes in Vue Router
- Responsive PrimeVue DataTable layouts
- Toast notifications for all CRUD operations
- Confirmation dialogs for delete operations

#### Internationalization
- Added 150+ German translations
- Added 150+ English translations
- Complete translation coverage for:
  - Navigation menu items
  - Form labels and hints
  - Validation messages
  - Success/error notifications
  - Weekday and state enumerations

#### API Integration
- Configured OpenAPI client to use authentication tokens
- Token-based authorization for all new endpoints
- Automatic token injection via OpenAPI.TOKEN resolver
- Error handling with toast notifications

#### Technical Improvements
- Generated TypeScript API client from OpenAPI spec
- Type-safe service calls for all new endpoints
- Consistent error handling across all views
- Reusable components (DataTable, Dialog, Calendar, etc.)

### Frontend Views Summary
1. **Dashboard** - Welcome screen
2. **Profile** - User profile management
3. **Working Hours** - Weekly hours configuration with time inputs
4. **Time Entries** - Clock in/out with daily summaries and CRUD operations
5. **Time Off** - Absence tracking with filtering
6. **Recurring Off-Days** - Recurring pattern management
7. **Vacation Balance** - Holiday allowance overview
8. **Public Holidays** - State-specific German holidays
9. **Admin Users** - User management (admin only)

### Implementation Status

- ✅ Phase 1: Foundation
- ✅ Phase 2: Domain & Persistence
- ✅ Phase 3: Authentication & Security
- ✅ Phase 4: User Management
- ✅ Phase 5: Working Hours
- ✅ Phase 6: Vue.js Frontend (Basic)
- ✅ Phase 7: Advanced Time Tracking (Backend Complete)
- ✅ Phase 8: Advanced Time Tracking (Frontend Complete)
- ✅ Phase 9: Time Entry API & Frontend (Complete)
- ⏳ Phase 10: Statistics & Reports
- ⏳ Phase 11: Polish & Deploy

---

## Development History

### Phase 1: Foundation (2025-12-10)
- Initialized Spring Boot 4 + Java 25 + PostgreSQL 17 project
- Set up OpenAPI-first architecture with code generation
- Created Flyway migrations and use-case driven package structure
- Configured Docker Compose for development
- **Tests**: Repository integration tests with Testcontainers

### Phase 2: Domain & Persistence (2025-12-10)
- Implemented domain entities: User, TimeEntry, WorkingHours, RefreshToken
- Created JPA repositories with custom queries
- **Tests**: 37 integration tests passing

### Phase 3: Authentication & Security (2025-12-10)
- JWT authentication with access + refresh tokens (HS384)
- Spring Security configuration with stateless sessions
- Login/Logout/Refresh use cases
- BCrypt password encoding
- **Tests**: All auth endpoints verified

### Phase 4: User Management (2025-12-10)
- Complete CRUD for users with role-based access control (ADMIN, USER)
- Admin can create/edit/delete users
- Users can edit own profile
- Automatic working hours initialization on user creation
- German error messages via GlobalExceptionHandler
- **Tests**: 16 user management integration tests

### Phase 5: Working Hours (2025-12-11)
- Per-weekday working hours configuration
- Optional start/end times with automatic hours calculation
- Support for part-time and flexible schedules
- Comprehensive validation (exactly 7 days, no duplicates, valid ranges)
- **Tests**: 16 working hours integration tests

### Phase 6: Vue.js Frontend - Basic (2025-12-11)
- Vue 3 + TypeScript + Vite setup
- PrimeVue 4 UI components with Aura theme
- JWT Authorization header authentication with Axios interceptors
- Automatic token refresh on 401
- German + English i18n (vue-i18n)
- Views: Login, Dashboard, Profile, Working Hours, Admin Users
- Vue Router with navigation guards
- **Monorepo**: Moved backend to `/backend`, OpenAPI spec to root

### Phase 7: Advanced Time Tracking - Backend (2025-12-12)
- **Recurring Off-Days**: Two pattern types (EVERY_NTH_WEEK, NTH_WEEKDAY_OF_MONTH)
- **Time-Off Tracking**: Four types (VACATION, SICK, PERSONAL, PUBLIC_HOLIDAY)
- **Vacation Balance**: Days-based tracking with automatic calculations
- **Public Holidays**: German holidays calculator (Berlin & Brandenburg)
- **Database**: 4 new tables (recurring_off_days, time_off, vacation_balance, users.state)
- **Tests**: 99 integration tests passing (37 new tests)
- Centralized test fixtures in RepositoryTestBase

### Phase 8: Advanced Time Tracking - Frontend (2025-12-12)
- **Public Holidays View**: Year/state filters, formatted dates
- **Recurring Off-Days View**: Full CRUD with dynamic pattern forms
- **Time-Off View**: CRUD with date filtering, automatic days calculation
- **Vacation Balance View**: Dashboard with progress indicator, admin editing
- **Navigation**: Added "Time Tracking" submenu with 5 items
- **i18n**: 150+ German and 150+ English translations
- **API Integration**: Configured OpenAPI client with automatic token injection

### Phase 9: Time Entry API & Frontend - Complete (2025-12-12)
- **Time Entry API**: Complete clock in/out functionality
  - `POST /api/time-entries/clock-in` - Start work session
  - `POST /api/time-entries/clock-out` - End work session
  - `GET /api/time-entries` - Get entries with date range filtering
  - `GET /api/time-entries/daily-summary` - Compare actual vs expected hours
  - `PUT /api/time-entries/{id}` - Update time entry
  - `DELETE /api/time-entries/{id}` - Delete time entry
- **Use Cases**: 6 use cases (ClockIn, ClockOut, GetEntries, GetDailySummary, Update, Delete)
- **Daily Summary**: Calculates actual vs expected hours with status (NO_ENTRY, BELOW_EXPECTED, MATCHED, ABOVE_EXPECTED)
- **Controller & Mapper**: TimeEntryController with LocalDateTime ↔ OffsetDateTime conversion
- **Tests**: 23 integration tests covering all endpoints and edge cases
- **Time Entries View**: Complete frontend implementation
  - Clock in/out buttons with real-time session tracking
  - Active session indicator with animated card
  - Two view modes: Entries (detailed) and Summary (daily overview)
  - Date range filtering for both views
  - CRUD operations: Edit and delete entries (disabled for active entries)
  - Entry type badges (WORK, SICK, PTO, EVENT) with color coding
  - Daily summary with status indicators (NO_ENTRY, BELOW_EXPECTED, MATCHED, ABOVE_EXPECTED)
  - Notes support for work sessions
  - Responsive PrimeVue DataTable layout
  - Confirmation dialogs for delete operations
- **i18n**: 60+ new German and English translations for time entries
- **Navigation**: Added "Time Entries" item to "Time Tracking" submenu (now 6 items total)
- **Manual Testing**: All features tested and verified working
- **Fixed**: Critical Phase 8/9 frontend issues
  - JWT authentication: main.ts now reads token directly from localStorage (was broken via getAccessToken())
  - PrimeVue imports: Added missing component imports across all views (Button, DataTable, Calendar, Select, etc.)
  - Date handling: Manual date formatting to prevent timezone shifts (Calendar Date objects → ISO strings)
  - Database constraints: RecurringOffDaysView now cleans up pattern-specific fields before API calls
  - Component naming: Changed Dropdown imports to Select (PrimeVue 4 component name)
  - Headline visibility: Changed h1 color from var(--text-color) to explicit dark color (#1f2937)
  - UX improvements: Redesigned filter sections with proper alignment and responsive layout
  - Translation keys: Fixed raw key display in TimeEntriesView (filter, type.label)
  - Date filters: Removed auto-triggering @date-select handlers, added explicit Filter button
  - i18n formality: Changed all German translations from formal "Sie" to informal "du"

### Key Architecture Decisions
- **Use-case driven architecture** (not controller/service/repository layers)
- **OpenAPI-first design**: Single spec generates both backend interfaces and frontend TypeScript client
- **Integration testing**: Every use case requires integration test with Testcontainers
- **No frontend state management**: Simple composables over Pinia/Vuex
- **Token storage**: localStorage for persistence (XSS risk acknowledged)
- **Vacation tracking**: Days not hours for simplicity
- **Priority system**: time_off > recurring_off_days > working_hours

### Testing Infrastructure
- **Pattern**: Shared singleton PostgreSQL 17 container (Testcontainers)
- **Migrations**: Flyway runs automatically on container start
- **Base Class**: RepositoryTestBase with @Transactional rollback per test
- **Fixtures**: Centralized test data creation methods
- **Coverage**: 99 backend integration tests, manual frontend testing

---

[Unreleased]: https://github.com/username/timetrack/compare/v0.0.1...HEAD
[0.0.1]: https://github.com/username/timetrack/releases/tag/v0.0.1
