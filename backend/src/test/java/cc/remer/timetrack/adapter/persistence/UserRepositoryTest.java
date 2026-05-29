package cc.remer.timetrack.adapter.persistence;

import cc.remer.timetrack.domain.user.GermanState;
import cc.remer.timetrack.domain.user.Role;
import cc.remer.timetrack.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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
