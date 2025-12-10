# Testing Guide

## Overview

This project uses integration tests with Testcontainers to provide a real PostgreSQL database environment for testing repository and persistence layer functionality.

## Test Infrastructure

### PostgreSQL Container Setup

We use a **singleton PostgreSQL container pattern** based on Baeldung and Testcontainers best practices:

- **Pattern**: Shared Database Instance (one container for all test classes)
- **Container**: `postgres:17-alpine`
- **Lifecycle**: Container starts once when first test class loads, runs until JVM shutdown
- **Migrations**: Flyway migrations run automatically in container's `start()` method

### Key Classes

#### `TimetrackPostgresContainer`
Custom container class extending `PostgreSQLContainer<TimetrackPostgresContainer>`:
- **Singleton pattern**: `getInstance()` ensures only one container instance
- **Auto-migration**: Runs Flyway migrations on startup with retry mechanism
- **System properties**: Sets `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` for Spring Boot
- **No-op stop**: Allows JVM to handle container cleanup

**Migration Timing**: ~2 seconds including retries
**Retry Mechanism**: Up to 10 attempts with 500ms delay (typically succeeds on attempt 3-4)

#### `RepositoryTestBase`
Abstract base class for all repository integration tests:
```java
@SpringBootTest
@Import(TestSecurityConfig.class)
@Testcontainers
@Transactional
@ActiveProfiles("test")
public abstract class RepositoryTestBase {
    @Container
    protected static final TimetrackPostgresContainer postgres =
        TimetrackPostgresContainer.getInstance();
}
```

**Features**:
- `@Transactional` - Each test method runs in a transaction that rolls back
- `@ActiveProfiles("test")` - Uses test-specific configuration
- `@Testcontainers` - Manages container lifecycle
- `@Import(TestSecurityConfig.class)` - Disables security for tests

### Test Configuration

#### `application-test.yml`
- Uses environment variables set by container: `${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}`
- Hibernate: `ddl-auto: none` (schema managed by Flyway)
- Flyway: `enabled: false` (migrations run in container, not by Spring Boot)
- Logging: Debug level for SQL queries

#### `TestSecurityConfig`
- Disables authentication for integration tests
- Provides required beans: `PasswordEncoder`, `AuthenticationManager`
- Uses `@EnableWebSecurity` to replace main security configuration

### Security Configuration for Tests

The main `SecurityConfig` is annotated with `@Profile("!test")` to exclude it from test environments, preventing conflicts with `TestSecurityConfig`.

## Test Patterns

### Repository Tests

1. **Extend `RepositoryTestBase`**
   ```java
   @DisplayName("User Repository Integration Tests")
   class UserRepositoryTest extends RepositoryTestBase {
       @Autowired
       private UserRepository userRepository;

       @BeforeEach
       void setUp() {
           userRepository.deleteAll();
       }

       @Test
       void shouldSaveAndFindUserById() {
           // test implementation
       }
   }
   ```

2. **Transaction Rollback**: Each test runs in a transaction that automatically rolls back
3. **Clean State**: Use `@BeforeEach` with `deleteAll()` for explicit cleanup if needed
4. **No Isolation Issues**: Shared container but isolated transactions

## Performance

### First Test Run
- Container startup: ~5-8 seconds
- Flyway migrations: ~2 seconds
- Total overhead: ~10 seconds for first test class

### Subsequent Test Classes
- No container startup (reused)
- No migrations (already run)
- Only Spring context initialization: ~2-3 seconds

### Test Execution
- 21 integration tests run in ~13 seconds total
- Individual test methods: <100ms each

## Troubleshooting

### Common Issues

**"relation does not exist" errors**
- Cause: Flyway migrations didn't run or failed
- Check: Container startup logs for migration status
- Solution: Verify retry mechanism completed successfully

**"Connection refused" errors**
- Cause: Database not ready when migrations attempted
- Check: Number of retry attempts in logs
- Solution: Increase `maxRetries` or `retryDelayMs` if needed

**Security configuration conflicts**
- Cause: Both `SecurityConfig` and `TestSecurityConfig` active
- Check: `@Profile("!test")` on `SecurityConfig`
- Solution: Ensure test profile activates correctly

### Debug Logging

Enable verbose output to see:
- Container startup timing
- Migration retry attempts
- Final migration duration
- Flyway-specific logs

Example output:
```
Starting Flyway database migrations...
Retry attempt 2 for Flyway migration...
Retry attempt 3 for Flyway migration...
Retry attempt 4 for Flyway migration...
Flyway migrations completed successfully in 1966ms
```

## Best Practices

1. **Always extend `RepositoryTestBase`** for repository tests
2. **Use `@Transactional`** to ensure test isolation
3. **Clean up in `@BeforeEach`** if tests depend on empty tables
4. **Don't manually start/stop containers** - let Testcontainers manage lifecycle
5. **Profile segregation** - Keep test and production configurations separate

## References

- [Baeldung: Testcontainers Integration Testing](https://www.baeldung.com/spring-boot-testcontainers-integration-test)
- [Testcontainers: Flyway Guide](https://testcontainers.com/guides/working-with-jooq-flyway-using-testcontainers/)
- [Spring Boot Testing Best Practices](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing)
