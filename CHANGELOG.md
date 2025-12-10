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

## Implementation Status

- ✅ Phase 1: Foundation
- ✅ Phase 2: Domain & Persistence
- ✅ Phase 3: Authentication & Security
- ⏳ Phase 4: User Management
- ⏳ Phase 5: Working Hours
- ⏳ Phase 6: Time Tracking
- ⏳ Phase 7: Statistics & Reports
- ⏳ Phase 8: Polish & Deploy

[Unreleased]: https://github.com/username/timetrack/compare/v0.0.1...HEAD
[0.0.1]: https://github.com/username/timetrack/releases/tag/v0.0.1
