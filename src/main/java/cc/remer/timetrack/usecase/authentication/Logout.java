package cc.remer.timetrack.usecase.authentication;

import cc.remer.timetrack.adapter.persistence.RefreshTokenRepository;
import cc.remer.timetrack.adapter.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case for user logout.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class Logout {

    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * Execute logout use case - invalidate all user's refresh tokens.
     *
     * @param authentication the current authentication
     */
    @Transactional
    public void execute(Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        Long userId = userPrincipal.getId();

        log.debug("Logging out user ID: {}", userId);

        // Delete all refresh tokens for this user
        refreshTokenRepository.deleteByUserId(userId);

        log.info("User logged out successfully: {}", userPrincipal.getEmail());
    }
}
