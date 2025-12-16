package cc.remer.timetrack.usecase.user;

import cc.remer.timetrack.adapter.persistence.UserRepository;
import cc.remer.timetrack.adapter.security.UserPrincipal;
import cc.remer.timetrack.api.model.UpdateUserRequest;
import cc.remer.timetrack.api.model.UserResponse;
import cc.remer.timetrack.domain.user.GermanState;
import cc.remer.timetrack.domain.user.Role;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.exception.DuplicateEmailException;
import cc.remer.timetrack.exception.ForbiddenException;
import cc.remer.timetrack.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case for updating a user.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UpdateUser {

    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    /**
     * Execute the update user use case.
     * Admins can update any user including role and active status.
     * Regular users can only update their own basic information.
     *
     * @param userId the user ID to update
     * @param request the update user request
     * @param authentication the authenticated user
     * @return the updated user response
     * @throws UserNotFoundException if user not found
     * @throws ForbiddenException if user doesn't have permission
     * @throws DuplicateEmailException if email already exists
     */
    @Transactional
    public UserResponse execute(Long userId, UpdateUserRequest request, Authentication authentication) {
        log.info("Updating user with ID: {}", userId);

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        // Find user
        User user = userService.getUserOrThrow(userId);

        // Check authorization
        validateAccess(user, principal);

        // Update email if provided and different
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new DuplicateEmailException("Email bereits vergeben: " + request.getEmail());
            }
            user.setEmail(request.getEmail());
        }

        // Update password if provided
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        // Update basic info
        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }

        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }

        // Update state (all users can update their own state)
        if (request.getState() != null) {
            user.setState(GermanState.valueOf(request.getState().getValue()));
        }

        // Update half-day holidays setting (all users can update their own setting)
        if (request.getHalfDayHolidaysEnabled() != null) {
            user.setHalfDayHolidaysEnabled(request.getHalfDayHolidaysEnabled());
        }

        // Only admins can update role and active status
        if (principal.getRole() == Role.ADMIN) {
            if (request.getRole() != null) {
                user.setRole(Role.valueOf(request.getRole().getValue()));
            }

            if (request.getActive() != null) {
                user.setActive(request.getActive());
            }
        } else {
            // Regular users trying to change role or active status
            if (request.getRole() != null || request.getActive() != null) {
                log.warn("User {} attempted to change role or active status", principal.getId());
                throw new ForbiddenException("Keine Berechtigung zum Ändern von Rolle oder Status");
            }
        }

        User savedUser = userRepository.save(user);
        log.info("User updated successfully: {}", savedUser.getId());

        return userMapper.toResponse(savedUser);
    }

    /**
     * Validate that the authenticated user has access to update the requested user.
     *
     * @param user the user to update
     * @param principal the authenticated user
     * @throws ForbiddenException if access is denied
     */
    private void validateAccess(User user, UserPrincipal principal) {
        // Admins can update any user
        if (principal.getRole() == Role.ADMIN) {
            return;
        }

        // Regular users can only update themselves
        if (!principal.getId().equals(user.getId())) {
            log.warn("User {} attempted to update user {}", principal.getId(), user.getId());
            throw new ForbiddenException("Keine Berechtigung für diese Aktion");
        }
    }
}
