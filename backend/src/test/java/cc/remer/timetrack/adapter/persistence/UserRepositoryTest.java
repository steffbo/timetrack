package cc.remer.timetrack.adapter.persistence;

import cc.remer.timetrack.domain.user.GermanState;
import cc.remer.timetrack.domain.user.Role;
import cc.remer.timetrack.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for UserRepository.
 */
@DisplayName("UserRepository Integration Tests")
class UserRepositoryTest extends RepositoryTestBase {

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        testUser = User.builder()
                .email("test@example.com")
                .passwordHash("$2a$10$hashedPassword")
                .firstName("Test")
                .lastName("User")
                .role(Role.USER)
                .active(true)
                .state(GermanState.BERLIN)
                .build();
    }

    @Test
    @DisplayName("Should save and find user by ID")
    void shouldSaveAndFindUserById() {
        // When
        User savedUser = userRepository.save(testUser);

        // Then
        assertThat(savedUser.getId()).isNotNull();
        assertThat(savedUser.getCreatedAt()).isNotNull();
        assertThat(savedUser.getUpdatedAt()).isNotNull();

        Optional<User> foundUser = userRepository.findById(savedUser.getId());
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getEmail()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("Should find user by email")
    void shouldFindUserByEmail() {
        // Given
        userRepository.save(testUser);

        // When
        Optional<User> foundUser = userRepository.findByEmail("test@example.com");

        // Then
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getFirstName()).isEqualTo("Test");
        assertThat(foundUser.get().getLastName()).isEqualTo("User");
    }

    @Test
    @DisplayName("Should return empty when user email not found")
    void shouldReturnEmptyWhenEmailNotFound() {
        // When
        Optional<User> foundUser = userRepository.findByEmail("nonexistent@example.com");

        // Then
        assertThat(foundUser).isEmpty();
    }

    @Test
    @DisplayName("Should check if user exists by email")
    void shouldCheckIfUserExistsByEmail() {
        // Given
        userRepository.save(testUser);

        // When
        boolean exists = userRepository.existsByEmail("test@example.com");
        boolean notExists = userRepository.existsByEmail("nonexistent@example.com");

        // Then
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    @DisplayName("Should find users by role")
    void shouldFindUsersByRole() {
        // Given
        userRepository.save(testUser);

        User adminUser = User.builder()
                .email("admin@example.com")
                .passwordHash("$2a$10$hashedPassword")
                .firstName("Admin")
                .lastName("User")
                .role(Role.ADMIN)
                .active(true)
                .state(GermanState.BERLIN)
                .build();
        userRepository.save(adminUser);

        // When
        List<User> regularUsers = userRepository.findByRole(Role.USER);
        List<User> adminUsers = userRepository.findByRole(Role.ADMIN);

        // Then
        assertThat(regularUsers).hasSize(1);
        assertThat(regularUsers.get(0).getEmail()).isEqualTo("test@example.com");
        assertThat(adminUsers).hasSize(1);
        assertThat(adminUsers.get(0).getEmail()).isEqualTo("admin@example.com");
    }

    @Test
    @DisplayName("Should find users by active status")
    void shouldFindUsersByActiveStatus() {
        // Given
        userRepository.save(testUser);

        User inactiveUser = User.builder()
                .email("inactive@example.com")
                .passwordHash("$2a$10$hashedPassword")
                .firstName("Inactive")
                .lastName("User")
                .role(Role.USER)
                .active(false)
                .state(GermanState.BERLIN)
                .build();
        userRepository.save(inactiveUser);

        // When
        List<User> activeUsers = userRepository.findByActive(true);
        List<User> inactiveUsers = userRepository.findByActive(false);

        // Then
        assertThat(activeUsers).hasSize(1);
        assertThat(activeUsers.get(0).getEmail()).isEqualTo("test@example.com");
        assertThat(inactiveUsers).hasSize(1);
        assertThat(inactiveUsers.get(0).getEmail()).isEqualTo("inactive@example.com");
    }

    @Test
    @DisplayName("Should find users by role and active status")
    void shouldFindUsersByRoleAndActiveStatus() {
        // Given
        userRepository.save(testUser);

        User inactiveRegularUser = User.builder()
                .email("inactive.user@example.com")
                .passwordHash("$2a$10$hashedPassword")
                .firstName("Inactive")
                .lastName("Regular")
                .role(Role.USER)
                .active(false)
                .state(GermanState.BERLIN)
                .build();
        userRepository.save(inactiveRegularUser);

        User activeAdmin = User.builder()
                .email("active.admin@example.com")
                .passwordHash("$2a$10$hashedPassword")
                .firstName("Active")
                .lastName("Admin")
                .role(Role.ADMIN)
                .active(true)
                .state(GermanState.BERLIN)
                .build();
        userRepository.save(activeAdmin);

        // When
        List<User> activeRegularUsers = userRepository.findByRoleAndActive(Role.USER, true);
        List<User> inactiveRegularUsers = userRepository.findByRoleAndActive(Role.USER, false);
        List<User> activeAdmins = userRepository.findByRoleAndActive(Role.ADMIN, true);

        // Then
        assertThat(activeRegularUsers).hasSize(1);
        assertThat(activeRegularUsers.get(0).getEmail()).isEqualTo("test@example.com");
        assertThat(inactiveRegularUsers).hasSize(1);
        assertThat(inactiveRegularUsers.get(0).getEmail()).isEqualTo("inactive.user@example.com");
        assertThat(activeAdmins).hasSize(1);
        assertThat(activeAdmins.get(0).getEmail()).isEqualTo("active.admin@example.com");
    }

    @Test
    @DisplayName("Should delete user by ID")
    void shouldDeleteUserById() {
        // Given
        User savedUser = userRepository.save(testUser);

        // When
        userRepository.deleteById(savedUser.getId());

        // Then
        Optional<User> deletedUser = userRepository.findById(savedUser.getId());
        assertThat(deletedUser).isEmpty();
    }

    @Test
    @DisplayName("Should get full name correctly")
    void shouldGetFullNameCorrectly() {
        // Given
        User savedUser = userRepository.save(testUser);

        // When
        String fullName = savedUser.getFullName();

        // Then
        assertThat(fullName).isEqualTo("Test User");
    }

    @Test
    @DisplayName("Should check if user is admin")
    void shouldCheckIfUserIsAdmin() {
        // Given
        User regularUser = userRepository.save(testUser);

        User adminUser = User.builder()
                .email("admin@example.com")
                .passwordHash("$2a$10$hashedPassword")
                .firstName("Admin")
                .lastName("User")
                .role(Role.ADMIN)
                .active(true)
                .state(GermanState.BERLIN)
                .build();
        User savedAdmin = userRepository.save(adminUser);

        // Then
        assertThat(regularUser.isAdmin()).isFalse();
        assertThat(savedAdmin.isAdmin()).isTrue();
    }
}
