package cc.remer.timetrack.usecase.user;

import cc.remer.timetrack.adapter.persistence.RepositoryTestBase;
import cc.remer.timetrack.adapter.persistence.UserRepository;
import cc.remer.timetrack.adapter.persistence.WorkingHoursRepository;
import cc.remer.timetrack.adapter.security.JwtTokenProvider;
import cc.remer.timetrack.adapter.security.UserPrincipal;
import cc.remer.timetrack.api.model.CreateUserRequest;
import cc.remer.timetrack.api.model.UpdateUserRequest;
import cc.remer.timetrack.api.model.UserResponse;
import cc.remer.timetrack.domain.user.GermanState;
import cc.remer.timetrack.domain.user.Role;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.exception.DuplicateEmailException;
import cc.remer.timetrack.exception.ForbiddenException;
import cc.remer.timetrack.exception.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for user management use cases.
 */
@DisplayName("User Management Integration Tests")
class UserManagementIntegrationTest extends RepositoryTestBase {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkingHoursRepository workingHoursRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CreateUser createUser;

    @Autowired
    private GetUser getUser;

    @Autowired
    private UpdateUser updateUser;

    @Autowired
    private DeleteUser deleteUser;

    private User testAdmin;
    private User testUser;
    private Authentication adminAuth;
    private Authentication userAuth;

    @BeforeEach
    void setUp() {
        // Clean up
        workingHoursRepository.deleteAll();
        userRepository.deleteAll();

        // Create test admin
        testAdmin = User.builder()
                .email("admin@test.local")
                .passwordHash(passwordEncoder.encode("password"))
                .firstName("Admin")
                .lastName("User")
                .role(Role.ADMIN)
                .active(true)
                .state(GermanState.BERLIN)
                .build();
        testAdmin = userRepository.save(testAdmin);

        // Create test user
        testUser = User.builder()
                .email("user@test.local")
                .passwordHash(passwordEncoder.encode("password"))
                .firstName("Regular")
                .lastName("User")
                .role(Role.USER)
                .active(true)
                .state(GermanState.BERLIN)
                .build();
        testUser = userRepository.save(testUser);

        // Create authentication contexts
        UserPrincipal adminPrincipal = UserPrincipal.create(testAdmin);
        adminAuth = new UsernamePasswordAuthenticationToken(adminPrincipal, null, adminPrincipal.getAuthorities());

        UserPrincipal userPrincipal = UserPrincipal.create(testUser);
        userAuth = new UsernamePasswordAuthenticationToken(userPrincipal, null, userPrincipal.getAuthorities());
    }

    @Test
    @DisplayName("Admin should create new user successfully")
    void testCreateUser_Success() {
        // Given
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("newuser@test.local");
        request.setPassword("password123");
        request.setFirstName("New");
        request.setLastName("User");
        request.setRole(CreateUserRequest.RoleEnum.USER);
        request.setActive(true);

        // When
        UserResponse response = createUser.execute(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();
        assertThat(response.getEmail()).isEqualTo("newuser@test.local");
        assertThat(response.getFirstName()).isEqualTo("New");
        assertThat(response.getLastName()).isEqualTo("User");
        assertThat(response.getRole()).isEqualTo(UserResponse.RoleEnum.USER);
        assertThat(response.getActive()).isTrue();

        // Verify working hours were created
        assertThat(workingHoursRepository.findAll()).hasSize(7);
    }

    @Test
    @DisplayName("Creating user with duplicate email should throw exception")
    void testCreateUser_DuplicateEmail() {
        // Given
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail(testUser.getEmail());
        request.setPassword("password123");
        request.setFirstName("Duplicate");
        request.setLastName("User");
        request.setRole(CreateUserRequest.RoleEnum.USER);

        // When & Then
        assertThatThrownBy(() -> createUser.execute(request))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessageContaining("Email bereits vergeben");
    }

    @Test
    @DisplayName("Admin should get any user by ID")
    void testGetUser_AdminGetsAnyUser() {
        // When
        UserResponse response = getUser.execute(testUser.getId(), adminAuth);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(testUser.getId());
        assertThat(response.getEmail()).isEqualTo(testUser.getEmail());
    }

    @Test
    @DisplayName("User should get their own profile")
    void testGetUser_UserGetsOwnProfile() {
        // When
        UserResponse response = getUser.execute(testUser.getId(), userAuth);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(testUser.getId());
    }

    @Test
    @DisplayName("User should not get other user's profile")
    void testGetUser_UserCannotGetOtherUser() {
        // When & Then
        assertThatThrownBy(() -> getUser.execute(testAdmin.getId(), userAuth))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Keine Berechtigung");
    }

    @Test
    @DisplayName("Get current user should return authenticated user")
    void testGetCurrentUser_Success() {
        // When
        UserResponse response = getUser.getCurrentUser(userAuth);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(testUser.getId());
        assertThat(response.getEmail()).isEqualTo(testUser.getEmail());
    }

    @Test
    @DisplayName("Getting non-existent user should throw exception")
    void testGetUser_NotFound() {
        // When & Then
        assertThatThrownBy(() -> getUser.execute(999L, adminAuth))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("Benutzer nicht gefunden");
    }

    @Test
    @DisplayName("User should update their own profile")
    void testUpdateUser_UserUpdatesOwnProfile() {
        // Given
        UpdateUserRequest request = new UpdateUserRequest();
        request.setFirstName("Updated");
        request.setLastName("Name");

        // When
        UserResponse response = updateUser.execute(testUser.getId(), request, userAuth);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getFirstName()).isEqualTo("Updated");
        assertThat(response.getLastName()).isEqualTo("Name");
    }

    @Test
    @DisplayName("User should not update other user's profile")
    void testUpdateUser_UserCannotUpdateOtherUser() {
        // Given
        UpdateUserRequest request = new UpdateUserRequest();
        request.setFirstName("Hacker");

        // When & Then
        assertThatThrownBy(() -> updateUser.execute(testAdmin.getId(), request, userAuth))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Keine Berechtigung");
    }

    @Test
    @DisplayName("User should not change their own role")
    void testUpdateUser_UserCannotChangeOwnRole() {
        // Given
        UpdateUserRequest request = new UpdateUserRequest();
        request.setRole(UpdateUserRequest.RoleEnum.ADMIN);

        // When & Then
        assertThatThrownBy(() -> updateUser.execute(testUser.getId(), request, userAuth))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Keine Berechtigung");
    }

    @Test
    @DisplayName("Admin should change user role")
    void testUpdateUser_AdminCanChangeRole() {
        // Given
        UpdateUserRequest request = new UpdateUserRequest();
        request.setRole(UpdateUserRequest.RoleEnum.ADMIN);

        // When
        UserResponse response = updateUser.execute(testUser.getId(), request, adminAuth);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getRole()).isEqualTo(UserResponse.RoleEnum.ADMIN);
    }

    @Test
    @DisplayName("Admin should change user active status")
    void testUpdateUser_AdminCanChangeActiveStatus() {
        // Given
        UpdateUserRequest request = new UpdateUserRequest();
        request.setActive(false);

        // When
        UserResponse response = updateUser.execute(testUser.getId(), request, adminAuth);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getActive()).isFalse();
    }

    @Test
    @DisplayName("Update with duplicate email should throw exception")
    void testUpdateUser_DuplicateEmail() {
        // Given
        UpdateUserRequest request = new UpdateUserRequest();
        request.setEmail(testAdmin.getEmail()); // Try to change to admin's email

        // When & Then
        assertThatThrownBy(() -> updateUser.execute(testUser.getId(), request, userAuth))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessageContaining("Email bereits vergeben");
    }

    @Test
    @DisplayName("User should update password")
    void testUpdateUser_PasswordChange() {
        // Given
        UpdateUserRequest request = new UpdateUserRequest();
        request.setPassword("newpassword123");

        // When
        updateUser.execute(testUser.getId(), request, userAuth);

        // Then
        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("newpassword123", updatedUser.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("Admin should delete user successfully")
    void testDeleteUser_Success() {
        // When
        deleteUser.execute(testUser.getId());

        // Then
        assertThat(userRepository.findById(testUser.getId())).isEmpty();
    }

    @Test
    @DisplayName("Deleting non-existent user should throw exception")
    void testDeleteUser_NotFound() {
        // When & Then
        assertThatThrownBy(() -> deleteUser.execute(999L))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("Benutzer nicht gefunden");
    }
}
