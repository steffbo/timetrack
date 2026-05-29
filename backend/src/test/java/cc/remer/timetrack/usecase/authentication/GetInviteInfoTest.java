package cc.remer.timetrack.usecase.authentication;

import cc.remer.timetrack.adapter.persistence.InviteTokenRepository;
import cc.remer.timetrack.adapter.persistence.RepositoryTestBase;
import cc.remer.timetrack.api.model.InviteInfoResponse;
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

@DisplayName("GetInviteInfo Integration Tests")
class GetInviteInfoTest extends RepositoryTestBase {

    @Autowired
    private GetInviteInfo getInviteInfo;

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
                .firstName("Pending")
                .lastName("User")
                .role(Role.USER)
                .active(true)
                .state(GermanState.BERLIN)
                .build();
        pendingUser = userRepository.save(pendingUser);

        registeredUser = User.builder()
                .email("registered@test.local")
                .passwordHash(passwordEncoder.encode("password"))
                .firstName("Registered")
                .lastName("User")
                .role(Role.USER)
                .active(true)
                .state(GermanState.BERLIN)
                .build();
        registeredUser = userRepository.save(registeredUser);
    }

    private InviteToken saveToken(User user, LocalDateTime expiresAt) {
        InviteToken token = InviteToken.builder()
                .user(user)
                .token("test-token-" + user.getId())
                .expiresAt(expiresAt)
                .build();
        return inviteTokenRepository.save(token);
    }

    @Test
    @DisplayName("Returns email, name, isPasswordReset=false for pending user")
    void getInviteInfo_pendingUser() {
        InviteToken token = saveToken(pendingUser, LocalDateTime.now().plusDays(30));

        InviteInfoResponse response = getInviteInfo.execute(token.getToken());

        assertThat(response.getEmail()).isEqualTo("pending@test.local");
        assertThat(response.getFirstName()).isEqualTo("Pending");
        assertThat(response.getLastName()).isEqualTo("User");
        assertThat(response.getIsPasswordReset()).isFalse();
    }

    @Test
    @DisplayName("Returns isPasswordReset=true for registered user")
    void getInviteInfo_registeredUser() {
        InviteToken token = saveToken(registeredUser, LocalDateTime.now().plusDays(30));

        InviteInfoResponse response = getInviteInfo.execute(token.getToken());

        assertThat(response.getIsPasswordReset()).isTrue();
    }

    @Test
    @DisplayName("Unknown token throws NOT_FOUND")
    void getInviteInfo_unknownToken() {
        assertThatThrownBy(() -> getInviteInfo.execute("nonexistent"))
                .isInstanceOf(InviteTokenException.class)
                .satisfies(e -> assertThat(((InviteTokenException) e).getReason())
                        .isEqualTo(InviteTokenException.Reason.NOT_FOUND));
    }

    @Test
    @DisplayName("Expired token throws EXPIRED")
    void getInviteInfo_expiredToken() {
        InviteToken token = saveToken(pendingUser, LocalDateTime.of(2025, 7, 31, 10, 0));

        assertThatThrownBy(() -> getInviteInfo.execute(token.getToken()))
                .isInstanceOf(InviteTokenException.class)
                .satisfies(e -> assertThat(((InviteTokenException) e).getReason())
                        .isEqualTo(InviteTokenException.Reason.EXPIRED));
    }
}
