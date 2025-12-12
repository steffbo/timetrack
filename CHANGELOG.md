# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

## Implementation Status

- ✅ Phase 1: Foundation
- ✅ Phase 2: Domain & Persistence
- ✅ Phase 3: Authentication & Security
- ✅ Phase 4: User Management
- ✅ Phase 5: Working Hours
- ✅ Phase 6: Vue.js Frontend (Basic)
- ✅ Phase 7: Advanced Time Tracking (Backend Complete)
- ⏳ Phase 8: Advanced Time Tracking (Frontend)
- ⏳ Phase 9: Statistics & Reports
- ⏳ Phase 10: Polish & Deploy

[Unreleased]: https://github.com/username/timetrack/compare/v0.0.1...HEAD
[0.0.1]: https://github.com/username/timetrack/releases/tag/v0.0.1
