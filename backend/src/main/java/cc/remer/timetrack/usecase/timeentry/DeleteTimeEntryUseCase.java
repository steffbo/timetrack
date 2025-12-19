package cc.remer.timetrack.usecase.timeentry;

import cc.remer.timetrack.adapter.persistence.TimeEntryRepository;
import cc.remer.timetrack.domain.timeentry.TimeEntry;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.usecase.recurringoffday.RecurringOffDayConflictDetector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case for deleting a time entry.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteTimeEntryUseCase {

    private final TimeEntryRepository timeEntryRepository;
    private final RecurringOffDayConflictDetector conflictDetector;

    /**
     * Delete a time entry.
     *
     * @param user the authenticated user
     * @param entryId the ID of the entry to delete
     * @throws IllegalArgumentException if entry not found or doesn't belong to user
     */
    @Transactional
    public void execute(User user, Long entryId) {
        log.debug("Deleting time entry {} for user {}", entryId, user.getId());

        TimeEntry entry = timeEntryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Zeiterfassungseintrag nicht gefunden: " + entryId));

        // Verify ownership
        if (!entry.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException(
                    "Sie haben keine Berechtigung, diesen Eintrag zu löschen.");
        }

        Long entryIdToDelete = entry.getId();
        timeEntryRepository.delete(entry);
        
        // Clean up any conflict warnings for this deleted entry
        conflictDetector.cleanupWarningsForTimeEntry(entryIdToDelete);
        
        log.info("Deleted time entry {} for user {}", entryId, user.getId());
    }
}
