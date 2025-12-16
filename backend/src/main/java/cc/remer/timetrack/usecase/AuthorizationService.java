package cc.remer.timetrack.usecase;

import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.exception.ForbiddenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for centralizing authorization logic across use cases.
 * Provides common authorization checks to ensure users can only access their own resources.
 */
@Service
@Slf4j
public class AuthorizationService {

    /**
     * Validates that the authenticated user owns the resource.
     *
     * @param resourceOwnerId the ID of the user who owns the resource
     * @param authenticatedUserId the ID of the authenticated user
     * @param resourceType human-readable description of the resource type (for error message)
     * @throws ForbiddenException if the authenticated user does not own the resource
     */
    public void validateOwnership(Long resourceOwnerId, Long authenticatedUserId, String resourceType) {
        if (resourceOwnerId == null || authenticatedUserId == null) {
            throw new IllegalArgumentException("Owner ID and authenticated user ID must not be null");
        }

        if (!resourceOwnerId.equals(authenticatedUserId)) {
            log.warn("User {} attempted to access {} owned by user {}",
                    authenticatedUserId, resourceType, resourceOwnerId);
            throw new ForbiddenException("Sie haben keine Berechtigung, " + resourceType + " zu ändern");
        }
    }

    /**
     * Validates that the authenticated user owns the resource (with entity owner).
     *
     * @param resourceOwner the user who owns the resource
     * @param authenticatedUserId the ID of the authenticated user
     * @param resourceType human-readable description of the resource type (for error message)
     * @throws ForbiddenException if the authenticated user does not own the resource
     */
    public void validateOwnership(User resourceOwner, Long authenticatedUserId, String resourceType) {
        if (resourceOwner == null) {
            throw new IllegalArgumentException("Resource owner must not be null");
        }
        validateOwnership(resourceOwner.getId(), authenticatedUserId, resourceType);
    }
}
