package cc.remer.timetrack.usecase.timeentry;

import cc.remer.timetrack.adapter.persistence.TimeEntryRepository;
import cc.remer.timetrack.domain.timeentry.EntryType;
import cc.remer.timetrack.domain.timeentry.TimeEntry;
import cc.remer.timetrack.domain.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Use case for updating an existing time entry.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UpdateTimeEntryUseCase {

    private final TimeEntryRepository timeEntryRepository;

    /**
     * Update a time entry.
     *
     * @param user the authenticated user
     * @param entryId the ID of the entry to update
     * @param clockIn new clock in time
     * @param clockOut new clock out time (can be null)
     * @param entryType new entry type
     * @param notes new notes
     * @return the updated time entry
     * @throws IllegalArgumentException if entry not found or doesn't belong to user
     * @throws IllegalStateException if clockOut is before clockIn
     */
    @Transactional
    public TimeEntry execute(User user, Long entryId, LocalDateTime clockIn,
                           LocalDateTime clockOut, EntryType entryType, String notes) {
        log.debug("Updating time entry {} for user {}", entryId, user.getId());

        TimeEntry entry = timeEntryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Zeiterfassungseintrag nicht gefunden: " + entryId));

        // Verify ownership
        if (!entry.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException(
                    "Sie haben keine Berechtigung, diesen Eintrag zu bearbeiten.");
        }

        // Validate times
        if (clockOut != null && clockOut.isBefore(clockIn)) {
            throw new IllegalStateException(
                    "Checkout-Zeit muss nach der Checkin-Zeit liegen.");
        }

        // Update fields
        entry.setClockIn(clockIn);
        entry.setClockOut(clockOut);
        entry.setEntryDate(clockIn.toLocalDate());
        entry.setEntryType(entryType);
        entry.setNotes(notes);

        TimeEntry saved = timeEntryRepository.save(entry);
        log.info("Updated time entry {} for user {}", entryId, user.getId());

        return saved;
    }
}
