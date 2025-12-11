package cc.remer.timetrack.usecase.user;

import cc.remer.timetrack.adapter.persistence.UserRepository;
import cc.remer.timetrack.adapter.security.UserPrincipal;
import cc.remer.timetrack.api.model.UserResponse;
import cc.remer.timetrack.domain.user.Role;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.exception.ForbiddenException;
import cc.remer.timetrack.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case for getting user details.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GetUser {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    /**
     * Get user by ID.
     * Admins can get any user, regular users can only get themselves.
     *
     * @param userId the user ID
     * @param authentication the authenticated user
     * @return the user response
     * @throws UserNotFoundException if user not found
     * @throws ForbiddenException if user doesn't have permission
     */
    @Transactional(readOnly = true)
    public UserResponse execute(Long userId, Authentication authentication) {
        log.debug("Getting user with ID: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Benutzer nicht gefunden"));

        // Check authorization
        validateAccess(user, authentication);

        return userMapper.toResponse(user);
    }

    /**
     * Get current authenticated user.
     *
     * @param authentication the authenticated user
     * @return the user response
     * @throws UserNotFoundException if user not found
     */
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        log.debug("Getting current user: {}", principal.getEmail());

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new UserNotFoundException("Benutzer nicht gefunden"));

        return userMapper.toResponse(user);
    }

    /**
     * Validate that the authenticated user has access to view the requested user.
     *
     * @param user the user to access
     * @param authentication the authenticated user
     * @throws ForbiddenException if access is denied
     */
    private void validateAccess(User user, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        // Admins can access any user
        if (principal.getRole() == Role.ADMIN) {
            return;
        }

        // Regular users can only access themselves
        if (!principal.getId().equals(user.getId())) {
            log.warn("User {} attempted to access user {}", principal.getId(), user.getId());
            throw new ForbiddenException("Keine Berechtigung für diese Aktion");
        }
    }
}
