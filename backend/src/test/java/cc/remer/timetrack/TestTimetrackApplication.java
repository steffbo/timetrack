package cc.remer.timetrack;

import org.springframework.boot.SpringApplication;

/**
 * Test application entry point for running the application with Testcontainers.
 * This can be used for manual testing of the application with a PostgreSQL container.
 *
 * Note: The project uses RepositoryTestBase with TimetrackPostgresContainer for integration tests.
 * This class is provided for convenience when running the app manually with test configuration.
 */
public class TestTimetrackApplication {

  public static void main(String[] args) {
    SpringApplication.from(TimetrackApplication::main).run(args);
  }
}
