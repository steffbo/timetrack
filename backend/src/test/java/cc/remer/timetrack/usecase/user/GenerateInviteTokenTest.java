package cc.remer.timetrack.usecase.user;

import cc.remer.timetrack.adapter.persistence.InviteTokenRepository;
import cc.remer.timetrack.adapter.persistence.RepositoryTestBase;
import cc.remer.timetrack.api.model.InviteTokenResponse;
import cc.remer.timetrack.domain.user.GermanState;
import cc.remer.timetrack.domain.user.InviteToken;
import cc.remer.timetrack.domain.user.Role;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.exception.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GenerateInviteToken Integration Tests")
class GenerateInviteTokenTest extends RepositoryTestBase {

    @Autowired
    private GenerateInviteToken generateInviteToken;

    @Autowired
    private InviteTokenRepository inviteTokenRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        inviteTokenRepository.deleteAll();
        workingHoursRepository.deleteAll();
        userRepository.deleteAll();
        testUser = createTestUser("invite@test.local", "Invite", "User", Role.USER, GermanState.BERLIN);
    }

    @Test
    @DisplayName("Generate invite token creates token and returns URL")
    void generateInviteToken_createsTokenAndUrl() {
        InviteTokenResponse response = generateInviteToken.execute(testUser.getId());

        assertThat(response.getInviteUrl()).contains("/register/");
        assertThat(response.getExpiresAt()).isNotNull();

        Optional<InviteToken> saved = inviteTokenRepository.findByUserId(testUser.getId());
        assertThat(saved).isPresent();
        assertThat(response.getInviteUrl()).contains(saved.get().getToken());
    }

    @Test
    @DisplayName("Regenerating invalidates old token")
    void generateInviteToken_invalidatesOldToken() {
        InviteTokenResponse first = generateInviteToken.execute(testUser.getId());
        String firstToken = inviteTokenRepository.findByUserId(testUser.getId()).get().getToken();

        InviteTokenResponse second = generateInviteToken.execute(testUser.getId());
        String secondToken = inviteTokenRepository.findByUserId(testUser.getId()).get().getToken();

        assertThat(firstToken).isNotEqualTo(secondToken);
        assertThat(inviteTokenRepository.findByToken(firstToken)).isEmpty();
    }

    @Test
    @DisplayName("Non-existent user throws UserNotFoundException")
    void generateInviteToken_userNotFound() {
        assertThatThrownBy(() -> generateInviteToken.execute(999L))
                .isInstanceOf(UserNotFoundException.class);
    }
}
