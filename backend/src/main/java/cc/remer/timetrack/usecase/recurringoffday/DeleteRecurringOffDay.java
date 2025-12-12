package cc.remer.timetrack.usecase.recurringoffday;

import cc.remer.timetrack.adapter.persistence.RecurringOffDayRepository;
import cc.remer.timetrack.domain.recurringoffday.RecurringOffDay;
import cc.remer.timetrack.exception.ForbiddenException;
import cc.remer.timetrack.exception.RecurringOffDayNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case to delete a recurring off-day rule.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteRecurringOffDay {

    private final RecurringOffDayRepository recurringOffDayRepository;

    /**
     * Execute the use case to delete a recurring off-day.
     *
     * @param userId the user ID
     * @param id the recurring off-day ID
     */
    @Transactional
    public void execute(Long userId, Long id) {
        log.info("Deleting recurring off-day ID: {} for user ID: {}", id, userId);

        // Find entity
        RecurringOffDay entity = recurringOffDayRepository.findById(id)
                .orElseThrow(() -> new RecurringOffDayNotFoundException(id));

        // Check user owns this recurring off-day
        if (!entity.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Sie haben keine Berechtigung, diese wiederkehrende Abwesenheit zu löschen");
        }

        // Delete
        recurringOffDayRepository.delete(entity);
        log.info("Deleted recurring off-day ID: {}", id);
    }
}
