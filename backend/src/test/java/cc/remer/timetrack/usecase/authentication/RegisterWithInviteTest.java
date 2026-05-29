package cc.remer.timetrack.usecase.authentication;

import cc.remer.timetrack.adapter.persistence.InviteTokenRepository;
import cc.remer.timetrack.adapter.persistence.RepositoryTestBase;
import cc.remer.timetrack.api.model.AuthResponse;
import cc.remer.timetrack.api.model.RegisterWithInviteRequest;
import cc.remer.timetrack.domain.user.GermanState;
import cc.remer.timetrack.domain.user.InviteToken;
import cc.remer.timetrack.domain.user.Role;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.exception.InviteTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RegisterWithInvite Integration Tests")
class RegisterWithInviteTest extends RepositoryTestBase {

    @Autowired
    private RegisterWithInvite registerWithInvite;

    @Autowired
    private InviteTokenRepository inviteTokenRepository;

    private User pendingUser;
    private User registeredUser;

    @BeforeEach
    void setUp() {
        inviteTokenRepository.deleteAll();
        workingHoursRepository.deleteAll();
        userRepository.deleteAll();

        pendingUser = User.builder()
                .email("pending@test.local")
                .passwordHash(null)
                .firstName("Old")
                .lastName("Name")
                .role(Role.USER)
                .active(true)
                .state(GermanState.BERLIN)
                .build();
        pendingUser = userRepository.save(pendingUser);

        registeredUser = User.builder()
                .email("registered@test.local")
                .passwordHash(passwordEncoder.encode("old-password"))
                .firstName("Registered")
                .lastName("User")
                .role(Role.USER)
                .active(true)
                .state(GermanState.BERLIN)
                .build();
        registeredUser = userRepository.save(registeredUser);
    }

    private InviteToken saveToken(User user, String token, LocalDateTime expiresAt) {
        InviteToken it = InviteToken.builder()
                .user(user)
                .token(token)
                .expiresAt(expiresAt)
                .build();
        return inviteTokenRepository.save(it);
    }

    @Test
    @DisplayName("Happy path: sets password, deletes token, returns JWT")
    void register_happyPath() {
        saveToken(pendingUser, "valid-token", LocalDateTime.now().plusDays(30));

        RegisterWithInviteRequest req = new RegisterWithInviteRequest();
        req.setToken("valid-token");
        req.setPassword("newpassword123");
        req.setFirstName("New");
        req.setLastName("Name");

        AuthResponse response = registerWithInvite.execute(req);

        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotBlank();

        // Token deleted
        assertThat(inviteTokenRepository.findByToken("valid-token")).isEmpty();

        // Password set
        User updated = userRepository.findById(pendingUser.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("newpassword123", updated.getPasswordHash())).isTrue();
        assertThat(updated.getFirstName()).isEqualTo("New");
        assertThat(updated.getLastName()).isEqualTo("Name");
    }

    @Test
    @DisplayName("Password reset path: existing user can reset password")
    void register_passwordResetPath() {
        saveToken(registeredUser, "reset-token", LocalDateTime.now().plusDays(30));

        RegisterWithInviteRequest req = new RegisterWithInviteRequest();
        req.setToken("reset-token");
        req.setPassword("brand-new-password");
        req.setFirstName("Registered");
        req.setLastName("User");

        AuthResponse response = registerWithInvite.execute(req);

        assertThat(response.getAccessToken()).isNotBlank();

        User updated = userRepository.findById(registeredUser.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("brand-new-password", updated.getPasswordHash())).isTrue();
        assertThat(inviteTokenRepository.findByToken("reset-token")).isEmpty();
    }

    @Test
    @DisplayName("Expired token rejected")
    void register_expiredToken() {
        saveToken(pendingUser, "expired-token", LocalDateTime.of(2025, 7, 31, 10, 0));

        RegisterWithInviteRequest req = new RegisterWithInviteRequest();
        req.setToken("expired-token");
        req.setPassword("newpass");
        req.setFirstName("A");
        req.setLastName("B");

        assertThatThrownBy(() -> registerWithInvite.execute(req))
                .isInstanceOf(InviteTokenException.class)
                .satisfies(e -> assertThat(((InviteTokenException) e).getReason())
                        .isEqualTo(InviteTokenException.Reason.EXPIRED));
    }

    @Test
    @DisplayName("Unknown token rejected")
    void register_unknownToken() {
        RegisterWithInviteRequest req = new RegisterWithInviteRequest();
        req.setToken("ghost-token");
        req.setPassword("password");
        req.setFirstName("A");
        req.setLastName("B");

        assertThatThrownBy(() -> registerWithInvite.execute(req))
                .isInstanceOf(InviteTokenException.class)
                .satisfies(e -> assertThat(((InviteTokenException) e).getReason())
                        .isEqualTo(InviteTokenException.Reason.NOT_FOUND));
    }
}
