package cc.remer.timetrack.usecase.user;

import cc.remer.timetrack.adapter.persistence.UserRepository;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case for deleting a user.
 * Only accessible by admins (enforced by controller).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteUser {

    private final UserRepository userRepository;

    /**
     * Execute the delete user use case.
     * Deletes the user and cascades to related entities (working hours, time entries, refresh tokens).
     *
     * @param userId the user ID to delete
     * @throws UserNotFoundException if user not found
     */
    @Transactional
    public void execute(Long userId) {
        log.info("Deleting user with ID: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Benutzer nicht gefunden"));

        userRepository.delete(user);
        log.info("User deleted successfully: {}", userId);
    }
}
