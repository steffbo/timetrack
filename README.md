# Time Tracking API

A modern time tracking API built with Spring Boot 4, Java 25, and PostgreSQL following an OpenAPI-first, use-case-driven architecture.

## Features

- 🔐 JWT-based authentication
- 👥 User management with role-based access control (Admin/User)
- ⏰ Clock in/out time tracking with timestamps
- 📅 Custom working hours per weekday
- 📊 Statistics and reports (actual vs planned hours)
- 🏥 Special day types (Sick, PTO, Events)
- 📝 OpenAPI/Swagger documentation
- 🐳 Docker support with Docker Compose

## Tech Stack

- **Framework:** Spring Boot 4
- **Language:** Java 25
- **Database:** PostgreSQL 17
- **Authentication:** JWT (JSON Web Tokens)
- **API Documentation:** OpenAPI 3.0 with Swagger UI
- **Build Tool:** Maven
- **Containerization:** Docker & Docker Compose
- **Database Migrations:** Flyway
- **Testing:** JUnit 5, Testcontainers

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
- **Health Check:** http://localhost:8080/api/actuator/health

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
- **Password:** admin

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
     "password": "admin"
   }
   ```

2. Use the returned `accessToken` in the `Authorization` header:
   ```
   Authorization: Bearer <your-access-token>
   ```

## Project Structure

```
timetrack/
├── backend/             # Spring Boot backend application
│   ├── src/
│   │   └── main/java/cc/remer/timetrack/
│   │       ├── domain/              # Domain entities (User, TimeEntry, WorkingHours)
│   │       ├── usecase/             # Business logic organized by use cases
│   │       │   ├── authentication/  # Login, logout, token refresh
│   │       │   ├── user/            # User management
│   │       │   ├── workinghours/    # Working hours configuration
│   │       │   ├── timeentry/       # Time tracking operations
│   │       │   └── statistics/      # Reports and statistics
│   │       ├── adapter/
│   │       │   ├── web/             # REST controllers
│   │       │   ├── persistence/     # JPA repositories
│   │       │   └── security/        # Security configuration
│   │       ├── config/              # Application configuration
│   │       └── exception/           # Exception handling
│   ├── pom.xml
│   └── Dockerfile
├── frontend/            # Frontend application (to be implemented)
├── docker-compose.yml
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

### Building Docker Image

```bash
docker build -t timetrack-backend:latest ./backend
```

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
