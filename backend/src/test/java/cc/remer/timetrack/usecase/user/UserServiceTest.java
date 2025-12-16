package cc.remer.timetrack.usecase.user;

import cc.remer.timetrack.adapter.persistence.UserRepository;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.domain.user.Role;
import cc.remer.timetrack.domain.user.GermanState;
import cc.remer.timetrack.exception.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserService.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .role(Role.USER)
                .active(true)
                .state(GermanState.BERLIN)
                .halfDayHolidaysEnabled(true)
                .build();
    }

    @Test
    @DisplayName("getUserOrThrow should return user when user exists")
    void getUserOrThrow_shouldReturnUser_whenUserExists() {
        // Given
        Long userId = 1L;
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // When
        User result = userService.getUserOrThrow(userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(userId);
        assertThat(result.getEmail()).isEqualTo("test@example.com");
        assertThat(result.getFirstName()).isEqualTo("Test");
        assertThat(result.getLastName()).isEqualTo("User");
        verify(userRepository, times(1)).findById(userId);
    }

    @Test
    @DisplayName("getUserOrThrow should throw UserNotFoundException when user does not exist")
    void getUserOrThrow_shouldThrowException_whenUserNotFound() {
        // Given
        Long userId = 999L;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> userService.getUserOrThrow(userId))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("Benutzer nicht gefunden");

        verify(userRepository, times(1)).findById(userId);
    }

    @Test
    @DisplayName("getUserOrThrow should throw IllegalArgumentException when userId is null")
    void getUserOrThrow_shouldThrowException_whenUserIdIsNull() {
        // When/Then
        assertThatThrownBy(() -> userService.getUserOrThrow(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User ID must not be null");

        verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("getUserOrThrow should handle different user roles")
    void getUserOrThrow_shouldHandleDifferentRoles() {
        // Given - Admin user
        User adminUser = User.builder()
                .id(2L)
                .email("admin@example.com")
                .firstName("Admin")
                .lastName("User")
                .role(Role.ADMIN)
                .active(true)
                .state(GermanState.BRANDENBURG)
                .halfDayHolidaysEnabled(false)
                .build();
        Long adminId = 2L;
        when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));

        // When
        User result = userService.getUserOrThrow(adminId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getRole()).isEqualTo(Role.ADMIN);
        assertThat(result.getId()).isEqualTo(adminId);
    }

    @Test
    @DisplayName("getUserOrThrow should handle inactive users")
    void getUserOrThrow_shouldHandleInactiveUsers() {
        // Given - Inactive user
        User inactiveUser = User.builder()
                .id(3L)
                .email("inactive@example.com")
                .firstName("Inactive")
                .lastName("User")
                .role(Role.USER)
                .active(false)
                .state(GermanState.BERLIN)
                .halfDayHolidaysEnabled(true)
                .build();
        Long inactiveId = 3L;
        when(userRepository.findById(inactiveId)).thenReturn(Optional.of(inactiveUser));

        // When
        User result = userService.getUserOrThrow(inactiveId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getActive()).isFalse();
        assertThat(result.getId()).isEqualTo(inactiveId);
    }

    @Test
    @DisplayName("getUserOrThrow should handle users from different states")
    void getUserOrThrow_shouldHandleUsersFromDifferentStates() {
        // Given - User from Brandenburg
        User brandenburgUser = User.builder()
                .id(4L)
                .email("brandenburg@example.com")
                .firstName("Brandenburg")
                .lastName("User")
                .role(Role.USER)
                .active(true)
                .state(GermanState.BRANDENBURG)
                .halfDayHolidaysEnabled(true)
                .build();
        Long brandenburgId = 4L;
        when(userRepository.findById(brandenburgId)).thenReturn(Optional.of(brandenburgUser));

        // When
        User result = userService.getUserOrThrow(brandenburgId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getState()).isEqualTo(GermanState.BRANDENBURG);
        assertThat(result.getId()).isEqualTo(brandenburgId);
    }

    @Test
    @DisplayName("Multiple calls with same ID should query repository each time")
    void getUserOrThrow_shouldQueryRepositoryEachTime() {
        // Given
        Long userId = 1L;
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // When
        userService.getUserOrThrow(userId);
        userService.getUserOrThrow(userId);

        // Then
        verify(userRepository, times(2)).findById(userId);
    }
}
