package cc.remer.timetrack.usecase.user;

import cc.remer.timetrack.adapter.persistence.RefreshTokenRepository;
import cc.remer.timetrack.adapter.persistence.RepositoryTestBase;
import cc.remer.timetrack.adapter.security.UserPrincipal;
import cc.remer.timetrack.api.model.AuthResponse;
import cc.remer.timetrack.api.model.UserResponse;
import cc.remer.timetrack.domain.user.GermanState;
import cc.remer.timetrack.domain.user.Role;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.exception.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ImpersonateUser Integration Tests")
class ImpersonateUserTest extends RepositoryTestBase {

    @Autowired
    private ImpersonateUser impersonateUser;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private User admin;
    private User otherAdmin;
    private User user;
    private User otherUser;
    private Authentication adminAuth;
    private Authentication userAuth;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        workingHoursRepository.deleteAll();
        userRepository.deleteAll();

        admin = createTestUser("admin@test.local", "Admin", "User", Role.ADMIN, GermanState.BERLIN);
        otherAdmin = createTestUser("other-admin@test.local", "Other", "Admin", Role.ADMIN, GermanState.BERLIN);
        user = createTestUser("user@test.local", "Regular", "User", Role.USER, GermanState.BERLIN);
        otherUser = createTestUser("other@test.local", "Other", "User", Role.USER, GermanState.BERLIN);

        adminAuth = authenticationFor(admin);
        userAuth = authenticationFor(user);
    }

    @Test
    @DisplayName("Admin can impersonate regular user and gets target-user session")
    void execute_adminImpersonatesRegularUser() {
        AuthResponse response = impersonateUser.execute(user.getId(), adminAuth);

        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getUser().getId()).isEqualTo(user.getId());
        assertThat(response.getUser().getEmail()).isEqualTo(user.getEmail());
        assertThat(response.getUser().getRole()).isEqualTo(UserResponse.RoleEnum.USER);

        assertThat(refreshTokenRepository.findByUserId(user.getId()))
                .singleElement()
                .satisfies(token -> assertThat(token.getToken()).isEqualTo(response.getRefreshToken()));
        assertThat(refreshTokenRepository.findByUserId(admin.getId())).isEmpty();
    }

    @Test
    @DisplayName("Non-admin cannot impersonate")
    void execute_nonAdminCannotImpersonate() {
        assertThatThrownBy(() -> impersonateUser.execute(otherUser.getId(), userAuth))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Nur Administratoren können andere Benutzer verkörpern");
    }

    @Test
    @DisplayName("Admin cannot impersonate another admin")
    void execute_adminCannotImpersonateAdmin() {
        assertThatThrownBy(() -> impersonateUser.execute(otherAdmin.getId(), adminAuth))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Administratoren können nicht andere Administratoren verkörpern");
    }

    private Authentication authenticationFor(User user) {
        UserPrincipal principal = UserPrincipal.create(user);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
