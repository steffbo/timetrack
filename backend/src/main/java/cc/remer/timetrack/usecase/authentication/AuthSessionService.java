package cc.remer.timetrack.usecase.authentication;

import cc.remer.timetrack.adapter.persistence.RefreshTokenRepository;
import cc.remer.timetrack.adapter.security.JwtTokenProvider;
import cc.remer.timetrack.adapter.security.UserPrincipal;
import cc.remer.timetrack.api.model.AuthResponse;
import cc.remer.timetrack.api.model.UserResponse;
import cc.remer.timetrack.config.JwtProperties;
import cc.remer.timetrack.domain.user.RefreshToken;
import cc.remer.timetrack.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Issues authentication sessions and builds the public auth response shape.
 */
@Service
@RequiredArgsConstructor
public class AuthSessionService {

    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public AuthResponse issueNewSession(Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        String accessToken = tokenProvider.generateAccessToken(authentication);
        String refreshToken = tokenProvider.generateRefreshToken(authentication);
        saveRefreshToken(principal.getId(), refreshToken);
        return buildAuthResponse(accessToken, refreshToken, principal);
    }

    public AuthResponse issueNewSession(UserPrincipal principal) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        );
        return issueNewSession(authentication);
    }

    public AuthResponse refreshAccessToken(String refreshToken, UserPrincipal principal) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        );
        String accessToken = tokenProvider.generateAccessToken(authentication);
        return buildAuthResponse(accessToken, refreshToken, principal);
    }

    private void saveRefreshToken(Long userId, String token) {
        RefreshToken refreshToken = RefreshToken.builder()
                .user(User.builder().id(userId).build())
                .token(token)
                .expiresAt(LocalDateTime.now(clock).plusSeconds(jwtProperties.getRefreshExpiration() / 1000))
                .build();

        refreshTokenRepository.save(refreshToken);
    }

    private AuthResponse buildAuthResponse(String accessToken, String refreshToken, UserPrincipal principal) {
        AuthResponse response = new AuthResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setTokenType("Bearer");
        response.setExpiresIn(jwtProperties.getExpiration() / 1000);

        UserResponse userResponse = new UserResponse();
        userResponse.setId(principal.getId());
        userResponse.setEmail(principal.getEmail());
        userResponse.setRole(UserResponse.RoleEnum.fromValue(principal.getRole().name()));
        userResponse.setActive(principal.isEnabled());
        response.setUser(userResponse);

        return response;
    }
}
