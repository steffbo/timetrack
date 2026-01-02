# Time Tracking API

A modern time tracking API built with Spring Boot 4, Java 25, and PostgreSQL following an OpenAPI-first, use-case-driven architecture.

## Features

- 🔐 JWT-based authentication with automatic token refresh
- 👥 User management with role-based access control (Admin/User)
- ⏰ Time entry tracking (clock in/out with active session management)
- 📅 Time-off tracking (vacation, sick, personal, public holidays)
- 🔄 Recurring off-day patterns (weekly/monthly)
- 🏖️ Vacation balance management with half-day holidays (Dec 24 & 31)
- 🇩🇪 German public holidays (Berlin & Brandenburg)
- ⚠️ Conflict warning system for recurring off-days
- 📊 Dashboard with interactive calendar, statistics, and caching
- 📄 PDF/CSV export for monthly reports
- ⏳ Custom working hours per weekday with time ranges
- 🌐 Bilingual interface (German & English)
- 📝 OpenAPI/Swagger documentation
- 🐳 Docker support with Docker Compose

## Tech Stack

### Backend
- **Framework:** Spring Boot 4
- **Language:** Java 25
- **Database:** PostgreSQL 17
- **Authentication:** JWT (JSON Web Tokens)
- **API Documentation:** OpenAPI 3.0 with Swagger UI
- **Build Tool:** Maven
- **Database Migrations:** Flyway
- **Testing:** JUnit 5, Testcontainers (219 integration tests)

### Frontend
- **Framework:** Vue 3 with Composition API
- **Language:** TypeScript
- **Build Tool:** Vite
- **UI Library:** PrimeVue 4 (Aura theme)
- **Routing:** Vue Router
- **I18n:** vue-i18n (German & English)
- **HTTP Client:** Axios with OpenAPI-generated client

### Infrastructure
- **Containerization:** Docker & Docker Compose
- **Reverse Proxy:** Caddy (production)
- **CI/CD:** GitHub Actions

## Prerequisites

- Java 25 (JDK)
- Docker & Docker Compose (for containerized setup)
- Maven 3.9+ (if running locally without Docker)

## Getting Started

### 1. Clone the repository

```bash
git clone <repository-url>
cd timetrack
```

### 2. Configure environment variables

Copy the example environment file:

```bash
cp .env.example .env
```

Edit `.env` with your configuration:

```properties
# Database
DB_URL=jdbc:postgresql://localhost:5432/timetrack
DB_USERNAME=timetrack
DB_PASSWORD=timetrack

# Server
SERVER_PORT=8080
MANAGEMENT_PORT=7080  # Management endpoints (localhost only)

# API Documentation (disable in production)
SPRINGDOC_ENABLED=true  # Set to false in production

# JWT (change in production!)
JWT_SECRET=your-secret-key-change-this-in-production-must-be-at-least-256-bits-long-for-HS256
JWT_EXPIRATION=86400000
JWT_REFRESH_EXPIRATION=604800000
```

### 3. Run with Docker Compose (Recommended)

Start the application and database:

```bash
docker-compose up -d
```

The API will be available at:
- **API Base:** http://localhost:8080/api
- **Swagger UI:** http://localhost:8080/api/swagger-ui.html
- **API Docs:** http://localhost:8080/api/v1/api-docs

Management endpoints (localhost only):
- **Health Check:** http://localhost:7080/actuator/health
- **Metrics:** http://localhost:7080/actuator/metrics
- **Info:** http://localhost:7080/actuator/info

To view logs:

```bash
docker-compose logs -f app
```

To stop:

```bash
docker-compose down
```

To stop and remove volumes (database data):

```bash
docker-compose down -v
```

### 4. Run locally (Alternative)

#### Start PostgreSQL

```bash
docker-compose up -d postgres
```

Or use your own PostgreSQL instance and update `.env` accordingly.

#### Build and run the application

```bash
cd backend
./mvnw clean install
./mvnw spring-boot:run
```

## Default Credentials

A default admin user is created on first startup:

- **Email:** admin@timetrack.local
- **Password:** admin1

⚠️ **Change this password immediately in production!**

## API Documentation

### Swagger UI

Interactive API documentation is available at:

```
http://localhost:8080/api/swagger-ui.html
```

### OpenAPI Specification

The raw OpenAPI spec can be accessed at:

```
http://localhost:8080/api/v1/api-docs
```

### Authentication

All endpoints (except login and refresh) require a JWT token.

1. **Login:** `POST /api/v1/auth/login`
   ```json
   {
     "email": "admin@timetrack.local",
     "password": "admin1"
   }
   ```

2. Use the returned `accessToken` in the `Authorization` header:
   ```
   Authorization: Bearer <your-access-token>
   ```

## Project Structure

```
timetrack/
├── backend/                         # Spring Boot backend application
│   ├── src/
│   │   └── main/java/cc/remer/timetrack/
│   │       ├── domain/              # Domain entities (User, TimeEntry, WorkingHours, etc.)
│   │       ├── usecase/             # Business logic organized by use cases
│   │       │   ├── authentication/  # Login, logout, token refresh
│   │       │   ├── user/            # User management
│   │       │   ├── workinghours/    # Working hours configuration
│   │       │   ├── timeentry/       # Time tracking (clock in/out)
│   │       │   ├── timeoff/         # Vacation & absence tracking
│   │       │   ├── recurringoffday/ # Recurring patterns & conflict detection
│   │       │   ├── vacationbalance/ # Vacation days calculation
│   │       │   └── report/          # PDF/CSV export generation
│   │       ├── adapter/
│   │       │   ├── web/             # REST controllers
│   │       │   ├── persistence/     # JPA repositories
│   │       │   └── security/        # Security configuration
│   │       ├── config/              # Application configuration
│   │       ├── util/                # Utility classes (ValidationUtils, MapperUtils)
│   │       └── exception/           # Exception handling
│   ├── pom.xml
│   └── Dockerfile
├── frontend/                        # Vue 3 frontend application
│   ├── src/
│   │   ├── views/                   # 9 page components (Dashboard, Profile, etc.)
│   │   ├── components/              # Reusable UI components
│   │   ├── api/                     # OpenAPI-generated TypeScript client
│   │   ├── i18n/                    # German & English translations
│   │   ├── router/                  # Vue Router configuration
│   │   └── composables/             # Shared logic (useAuth, etc.)
│   └── package.json
├── openapi.yaml                     # API specification (single source of truth)
├── docker-compose.yml               # Development environment
└── README.md
```

## Development

### Code Generation

API interfaces and models are generated from the OpenAPI specification during the Maven build:

```bash
cd backend
./mvnw generate-sources
```

Generated code is located in `backend/target/generated-sources/openapi/`

### Database Migrations

Database schema is managed with Flyway. Migration scripts are in:

```
backend/src/main/resources/db/migration/
```

Migrations run automatically on application startup.

### Testing

Run all tests:

```bash
cd backend
./mvnw test
```

Run integration tests (uses Testcontainers):

```bash
cd backend
./mvnw verify
```

## Semantic Versioning

This project uses semantic versioning with automated version bumping based on commit messages:

- **MAJOR (X.0.0):** `BREAKING:` or `breaking:` - Breaking changes
- **MINOR (0.X.0):** `feat:` or `feature:` - New features
- **PATCH (0.0.X):** `fix:`, `docs:`, `chore:`, `refactor:`, `test:`, `style:` - Bug fixes and maintenance

Example:
```bash
git commit -m "feat: Add monthly statistics endpoint"
```

## Deployment

For comprehensive deployment instructions, see **[DEPLOYMENT.md](DEPLOYMENT.md)**.

### Quick Production Overview

The application is designed for production deployment at **zeit.remer.cc** with:

- **Single Docker Image**: Frontend and backend bundled together
- **GitHub Container Registry**: Automated image builds via GitHub Actions
- **Caddy Reverse Proxy**: Automatic HTTPS with Let's Encrypt
- **PostgreSQL**: Persistent database with Docker volumes
- **Manual Deployment**: Pull images and restart containers on the server

Key files:
- `docker-compose.prod.yml` - Production Docker Compose configuration
- `.env.example.prod` - Production environment template
- `Caddyfile.example` - Caddy reverse proxy configuration
- `DEPLOYMENT.md` - Complete deployment guide

### CI/CD Pipeline

GitHub Actions automatically:
1. **CI Workflow** (`.github/workflows/ci.yml`): Runs tests on every push and PR
2. **Docker Workflow** (`.github/workflows/docker.yml`): Builds and publishes Docker images to ghcr.io on main branch push

### Environment Variables in Production

Ensure you set secure values for:

- `JWT_SECRET` - Use a strong, random secret (minimum 256 bits)
- `DB_PASSWORD` - Use a strong database password
- `ALLOWED_ORIGINS` - Restrict to your frontend domain

## Contributing

1. Create a feature branch
2. Make your changes
3. Write/update tests
4. Ensure all tests pass
5. Submit a pull request

## License

[Your License Here]

## Support

For issues and questions, please create an issue in the repository.
