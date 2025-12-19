package cc.remer.timetrack.usecase.user;

import cc.remer.timetrack.adapter.persistence.RefreshTokenRepository;
import cc.remer.timetrack.adapter.security.JwtTokenProvider;
import cc.remer.timetrack.adapter.security.UserPrincipal;
import cc.remer.timetrack.api.model.AuthResponse;
import cc.remer.timetrack.api.model.UserResponse;
import cc.remer.timetrack.config.JwtProperties;
import cc.remer.timetrack.domain.user.RefreshToken;
import cc.remer.timetrack.domain.user.Role;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.exception.ForbiddenException;
import cc.remer.timetrack.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Use case for admin impersonation of other users.
 * Allows administrators to temporarily authenticate as another user without their password.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ImpersonateUser {

    private final UserService userService;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    /**
     * Impersonate another user.
     * Only admins can impersonate, and admins cannot impersonate other admins.
     *
     * @param targetUserId the ID of the user to impersonate
     * @param authentication the authenticated admin user
     * @return authentication response with tokens for the target user
     * @throws UserNotFoundException if target user not found
     * @throws ForbiddenException if requesting user is not admin or trying to impersonate another admin
     */
    @Transactional
    public AuthResponse execute(Long targetUserId, Authentication authentication) {
        UserPrincipal adminPrincipal = (UserPrincipal) authentication.getPrincipal();

        log.debug("Admin {} attempting to impersonate user ID: {}", adminPrincipal.getId(), targetUserId);

        // Verify requesting user is an admin
        if (adminPrincipal.getRole() != Role.ADMIN) {
            log.warn("Non-admin user {} attempted to impersonate user {}", adminPrincipal.getId(), targetUserId);
            throw new ForbiddenException("Nur Administratoren können andere Benutzer verkörpern");
        }

        // Get target user
        User targetUser = userService.getUserOrThrow(targetUserId);

        // Prevent impersonating other admins
        if (targetUser.getRole() == Role.ADMIN) {
            log.warn("Admin {} attempted to impersonate another admin {}", adminPrincipal.getId(), targetUserId);
            throw new ForbiddenException("Administratoren können nicht andere Administratoren verkörpern");
        }

        // Create authentication for the target user
        UserPrincipal targetPrincipal = UserPrincipal.builder()
                .id(targetUser.getId())
                .email(targetUser.getEmail())
                .password(targetUser.getPasswordHash())
                .role(targetUser.getRole())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + targetUser.getRole().name())))
                .enabled(targetUser.getActive())
                .impersonatedBy(adminPrincipal.getId())
                .build();

        Authentication targetAuthentication = new UsernamePasswordAuthenticationToken(
                targetPrincipal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + targetUser.getRole().name()))
        );

        // Generate tokens for target user
        String accessToken = tokenProvider.generateAccessToken(targetAuthentication);
        String refreshToken = tokenProvider.generateRefreshToken(targetAuthentication);

        // Save refresh token
        saveRefreshToken(targetUser.getId(), refreshToken);

        log.info("Admin {} successfully impersonating user {} ({})",
                adminPrincipal.getId(), targetUser.getId(), targetUser.getEmail());

        // Build response
        return buildAuthResponse(accessToken, refreshToken, targetPrincipal);
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
