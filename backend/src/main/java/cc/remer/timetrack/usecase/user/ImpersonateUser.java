package cc.remer.timetrack.usecase.user;

import cc.remer.timetrack.adapter.security.UserPrincipal;
import cc.remer.timetrack.api.model.AuthResponse;
import cc.remer.timetrack.domain.user.Role;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.exception.ForbiddenException;
import cc.remer.timetrack.exception.UserNotFoundException;
import cc.remer.timetrack.usecase.authentication.AuthSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case for admin impersonation of other users.
 * Allows administrators to temporarily authenticate as another user without their password.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ImpersonateUser {

    private final UserService userService;
    private final AuthSessionService authSessionService;

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

        UserPrincipal targetPrincipal = UserPrincipal.create(targetUser, adminPrincipal.getId());

        log.info("Admin {} successfully impersonating user {} ({})",
                adminPrincipal.getId(), targetUser.getId(), targetUser.getEmail());

        return authSessionService.issueNewSession(targetPrincipal);
    }
}
