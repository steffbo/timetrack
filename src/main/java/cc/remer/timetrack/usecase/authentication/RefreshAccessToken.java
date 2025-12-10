package cc.remer.timetrack.usecase.authentication;

import cc.remer.timetrack.adapter.persistence.RefreshTokenRepository;
import cc.remer.timetrack.adapter.persistence.UserRepository;
import cc.remer.timetrack.adapter.security.JwtTokenProvider;
import cc.remer.timetrack.adapter.security.UserPrincipal;
import cc.remer.timetrack.api.model.AuthResponse;
import cc.remer.timetrack.api.model.RefreshTokenRequest;
import cc.remer.timetrack.api.model.UserResponse;
import cc.remer.timetrack.config.JwtProperties;
import cc.remer.timetrack.domain.user.RefreshToken;
import cc.remer.timetrack.domain.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case for refreshing access token using refresh token.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RefreshAccessToken {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider tokenProvider;
    private final JwtProperties jwtProperties;

    /**
     * Execute refresh token use case.
     *
     * @param request the refresh token request
     * @return new authentication response with tokens
     * @throws IllegalArgumentException if refresh token is invalid or expired
     */
    @Transactional
    public AuthResponse execute(RefreshTokenRequest request) {
        String refreshTokenValue = request.getRefreshToken();

        log.debug("Attempting to refresh access token");

        // Validate refresh token
        if (!tokenProvider.validateToken(refreshTokenValue)) {
            log.error("Invalid refresh token");
            throw new IllegalArgumentException("Ungültiges Refresh-Token");
        }

        // Find refresh token in database
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> {
                    log.error("Refresh token not found in database");
                    return new IllegalArgumentException("Refresh-Token nicht gefunden");
                });

        // Check if expired
        if (refreshToken.isExpired()) {
            log.error("Refresh token is expired");
            refreshTokenRepository.delete(refreshToken);
            throw new IllegalArgumentException("Refresh-Token ist abgelaufen");
        }

        // Load user
        Long userId = tokenProvider.getUserIdFromToken(refreshTokenValue);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("User not found for ID: {}", userId);
                    return new IllegalArgumentException("Benutzer nicht gefunden");
                });

        // Check if user is active
        if (!user.getActive()) {
            log.error("User account is inactive: {}", user.getEmail());
            throw new IllegalArgumentException("Benutzerkonto ist inaktiv");
        }

        // Create authentication
        UserPrincipal userPrincipal = UserPrincipal.create(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userPrincipal,
                null,
                userPrincipal.getAuthorities()
        );

        // Generate new access token
        String newAccessToken = tokenProvider.generateAccessToken(authentication);

        log.info("Access token refreshed successfully for user: {}", user.getEmail());

        // Build response (keep same refresh token)
        return buildAuthResponse(newAccessToken, refreshTokenValue, userPrincipal);
    }

    private AuthResponse buildAuthResponse(String accessToken, String refreshToken, UserPrincipal userPrincipal) {
        AuthResponse response = new AuthResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setTokenType("Bearer");
        response.setExpiresIn(jwtProperties.getExpiration() / 1000);

        UserResponse userResponse = new UserResponse();
        userResponse.setId(userPrincipal.getId());
        userResponse.setEmail(userPrincipal.getEmail());
        userResponse.setRole(UserResponse.RoleEnum.fromValue(
                userPrincipal.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "")
        ));
        userResponse.setActive(userPrincipal.isEnabled());

        response.setUser(userResponse);

        return response;
    }
}
