package cc.remer.timetrack.usecase.authentication;

import cc.remer.timetrack.adapter.persistence.RefreshTokenRepository;
import cc.remer.timetrack.adapter.security.JwtTokenProvider;
import cc.remer.timetrack.adapter.security.UserPrincipal;
import cc.remer.timetrack.api.model.AuthResponse;
import cc.remer.timetrack.api.model.LoginRequest;
import cc.remer.timetrack.api.model.UserResponse;
import cc.remer.timetrack.config.JwtProperties;
import cc.remer.timetrack.domain.user.RefreshToken;
import cc.remer.timetrack.domain.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Use case for user login.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class Login {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    /**
     * Execute login use case.
     *
     * @param request the login request
     * @return authentication response with tokens
     */
    @Transactional
    public AuthResponse execute(LoginRequest request) {
        log.debug("Attempting login for user: {}", request.getEmail());

        // Authenticate user
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        // Generate tokens
        String accessToken = tokenProvider.generateAccessToken(authentication);
        String refreshToken = tokenProvider.generateRefreshToken(authentication);

        // Save refresh token
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        saveRefreshToken(userPrincipal.getId(), refreshToken);

        log.info("User logged in successfully: {}", request.getEmail());

        // Build response
        return buildAuthResponse(accessToken, refreshToken, userPrincipal);
    }

    private void saveRefreshToken(Long userId, String token) {
        RefreshToken refreshToken = RefreshToken.builder()
                .user(User.builder().id(userId).build())
                .token(token)
                .expiresAt(LocalDateTime.now().plusSeconds(jwtProperties.getRefreshExpiration() / 1000))
                .build();

        refreshTokenRepository.save(refreshToken);
    }

    private AuthResponse buildAuthResponse(String accessToken, String refreshToken, UserPrincipal userPrincipal) {
        AuthResponse response = new AuthResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setTokenType("Bearer");
        response.setExpiresIn(jwtProperties.getExpiration() / 1000); // Convert to seconds

        // Set user info
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
