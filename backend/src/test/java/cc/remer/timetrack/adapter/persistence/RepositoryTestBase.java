package cc.remer.timetrack.adapter.persistence;

import cc.remer.timetrack.config.TestSecurityConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for repository integration tests using Testcontainers.
 * Implements the "Shared Database Instance" pattern from Baeldung.
 *
 * The container is started once and shared across all test classes.
 * Flyway migrations run automatically via Spring Boot's auto-configuration.
 *
 * @see <a href="https://www.baeldung.com/spring-boot-testcontainers-integration-test">Baeldung Testcontainers Guide</a>
 */
@SpringBootTest
@Import(TestSecurityConfig.class)
@Testcontainers
@Transactional
@ActiveProfiles("test")
public abstract class RepositoryTestBase {

    @Container
    protected static final TimetrackPostgresContainer postgres = TimetrackPostgresContainer.getInstance();
}
