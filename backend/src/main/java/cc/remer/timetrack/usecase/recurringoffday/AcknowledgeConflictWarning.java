package cc.remer.timetrack.usecase.recurringoffday;

import cc.remer.timetrack.adapter.persistence.RecurringOffDayConflictWarningRepository;
import cc.remer.timetrack.domain.recurringoffday.RecurringOffDayConflictWarning;
import cc.remer.timetrack.domain.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case for acknowledging a conflict warning.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AcknowledgeConflictWarning {

    private final RecurringOffDayConflictWarningRepository warningRepository;

    /**
     * Acknowledge a warning for the authenticated user.
     * The warning persists in the database for calendar highlighting.
     *
     * @param user the authenticated user
     * @param warningId the warning ID to acknowledge
     * @return the updated warning
     * @throws IllegalArgumentException if warning not found or doesn't belong to user
     */
    @Transactional
    public RecurringOffDayConflictWarning execute(User user, Long warningId) {
        log.debug("Acknowledging warning {} for user: {}", warningId, user.getId());

        RecurringOffDayConflictWarning warning = warningRepository.findById(warningId)
                .orElseThrow(() -> new IllegalArgumentException("Warning not found with ID: " + warningId));

        // Verify the warning belongs to this user
        if (!warning.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Warning does not belong to this user");
        }

        // Mark as acknowledged
        warning.acknowledge();
        RecurringOffDayConflictWarning saved = warningRepository.save(warning);

        log.info("Warning {} acknowledged by user {}", warningId, user.getId());
        return saved;
    }
}
