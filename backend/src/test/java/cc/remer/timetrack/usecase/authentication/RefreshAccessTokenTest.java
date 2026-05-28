package cc.remer.timetrack.usecase.authentication;

import cc.remer.timetrack.adapter.persistence.RefreshTokenRepository;
import cc.remer.timetrack.adapter.persistence.RepositoryTestBase;
import cc.remer.timetrack.adapter.security.JwtTokenProvider;
import cc.remer.timetrack.adapter.security.UserPrincipal;
import cc.remer.timetrack.api.model.AuthResponse;
import cc.remer.timetrack.api.model.RefreshTokenRequest;
import cc.remer.timetrack.api.model.UserResponse;
import cc.remer.timetrack.domain.user.RefreshToken;
import cc.remer.timetrack.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RefreshAccessToken Integration Tests")
class RefreshAccessTokenTest extends RepositoryTestBase {

    @Autowired
    private RefreshAccessToken refreshAccessToken;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtTokenProvider tokenProvider;

    private User user;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        workingHoursRepository.deleteAll();
        userRepository.deleteAll();

        user = createTestUser();
    }

    @Test
    @DisplayName("Valid refresh token returns new access token and reuses refresh token")
    void execute_validRefreshToken() {
        String refreshToken = saveRefreshToken(user, LocalDateTime.now().plusDays(30));
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(refreshToken);

        AuthResponse response = refreshAccessToken.execute(request);

        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isEqualTo(refreshToken);
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getUser().getId()).isEqualTo(user.getId());
        assertThat(response.getUser().getEmail()).isEqualTo(user.getEmail());
        assertThat(response.getUser().getRole()).isEqualTo(UserResponse.RoleEnum.USER);
        assertThat(refreshTokenRepository.findByToken(refreshToken)).isPresent();
    }

    @Test
    @DisplayName("Expired persisted refresh token is rejected and deleted")
    void execute_expiredPersistedRefreshToken() {
        String refreshToken = saveRefreshToken(user, LocalDateTime.now().minusHours(1));
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(refreshToken);

        assertThatThrownBy(() -> refreshAccessToken.execute(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Refresh-Token ist abgelaufen");
        assertThat(refreshTokenRepository.findByToken(refreshToken)).isEmpty();
    }

    @Test
    @DisplayName("Inactive user is rejected")
    void execute_inactiveUser() {
        user.setActive(false);
        user = userRepository.save(user);
        String refreshToken = saveRefreshToken(user, LocalDateTime.now().plusDays(30));
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(refreshToken);

        assertThatThrownBy(() -> refreshAccessToken.execute(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Benutzerkonto ist inaktiv");
    }

    private String saveRefreshToken(User user, LocalDateTime expiresAt) {
        UserPrincipal principal = UserPrincipal.create(user);
        var authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        );
        String token = tokenProvider.generateRefreshToken(authentication);
        refreshTokenRepository.save(RefreshToken.builder()
                .user(User.builder().id(user.getId()).build())
                .token(token)
                .expiresAt(expiresAt)
                .build());
        return token;
    }
}
