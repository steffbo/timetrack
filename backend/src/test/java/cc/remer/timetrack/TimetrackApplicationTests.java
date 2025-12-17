package cc.remer.timetrack;

import cc.remer.timetrack.adapter.persistence.RepositoryTestBase;
import org.junit.jupiter.api.Test;

/**
 * Basic context load test using the project's Testcontainers setup.
 * Extends RepositoryTestBase to use the shared PostgreSQL container pattern.
 */
class TimetrackApplicationTests extends RepositoryTestBase {

  @Test
  void contextLoads() {
    // This test verifies that the Spring application context loads successfully
    // with the Testcontainers PostgreSQL database
  }
}
