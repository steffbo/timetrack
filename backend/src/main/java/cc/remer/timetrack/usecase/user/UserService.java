package cc.remer.timetrack.usecase.user;

import cc.remer.timetrack.adapter.persistence.UserRepository;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for common user operations.
 * Centralizes user fetching logic to eliminate duplication across use cases.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;

    /**
     * Fetch a user by ID or throw UserNotFoundException if not found.
     * This method centralizes the common pattern of fetching a user with error handling.
     *
     * @param userId the user ID to fetch
     * @return the user entity
     * @throws UserNotFoundException if the user is not found
     */
    public User getUserOrThrow(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID must not be null");
        }

        return userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("User not found with ID: {}", userId);
                    return new UserNotFoundException("Benutzer nicht gefunden");
                });
    }
}
