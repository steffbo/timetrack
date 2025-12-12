# Stage 1: Build Frontend
FROM node:20-alpine AS frontend-build
WORKDIR /frontend

# Configure npm to use public registry (override any local config)
RUN npm config set registry https://registry.npmjs.org/

# Copy frontend package files
COPY frontend/package*.json ./

# Install dependencies
RUN npm ci

# Copy OpenAPI spec (needed for API client generation)
COPY openapi.yaml ../openapi.yaml

# Copy frontend source
COPY frontend/ ./

# Generate API client from OpenAPI spec
RUN npm run generate-api

# Build frontend for production
RUN npm run build

# Stage 2: Build Backend
FROM maven:3.9.11-eclipse-temurin-25-alpine AS backend-build
WORKDIR /app

# Copy pom.xml and download dependencies (cached layer)
COPY backend/pom.xml .
COPY backend/.mvn .mvn
COPY backend/mvnw .
RUN ./mvnw dependency:go-offline -B

# Copy source code and build
COPY backend/src ./src
RUN ./mvnw clean package -DskipTests

# Stage 3: Runtime
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Create non-root user
RUN addgroup -S spring && adduser -S spring -G spring

# Create directory for static files
RUN mkdir -p /app/static && chown -R spring:spring /app/static

# Copy jar from backend build stage
COPY --from=backend-build /app/target/*.jar app.jar

# Copy frontend dist files from frontend build stage to static resources
# Spring Boot will serve these from classpath:/static/
COPY --from=frontend-build --chown=spring:spring /frontend/dist /app/static

# Switch to non-root user
USER spring:spring

# Expose port (8811 for production, can be overridden)
EXPOSE 8811

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8811/api/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]
