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
import java.util.List;

/**
 * Use case for creating a manual time entry with both start and end times.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreateTimeEntryUseCase {

    private final TimeEntryRepository timeEntryRepository;

    /**
     * Create a manual time entry for the authenticated user.
     *
     * @param user the authenticated user
     * @param clockIn the clock in time
     * @param clockOut the clock out time
     * @param entryType the type of entry
     * @param notes optional notes for this entry
     * @return the created time entry
     * @throws IllegalArgumentException if clock out is before clock in, or if times overlap with existing entries
     */
    @Transactional
    public TimeEntry execute(User user, LocalDateTime clockIn, LocalDateTime clockOut,
                            EntryType entryType, String notes) {
        log.debug("Creating manual time entry for user: {}", user.getId());

        // Validate times
        if (clockOut.isBefore(clockIn)) {
            throw new IllegalArgumentException("Clock out time must be after clock in time");
        }

        // Check for overlapping entries
        List<TimeEntry> existingEntries = timeEntryRepository.findByUserId(user.getId());
        for (TimeEntry existing : existingEntries) {
            if (existing.getClockOut() == null) {
                throw new IllegalArgumentException("Cannot create manual entry while an active session exists. Please clock out first.");
            }

            // Check for overlap: new entry starts before existing ends AND new entry ends after existing starts
            if (clockIn.isBefore(existing.getClockOut()) && clockOut.isAfter(existing.getClockIn())) {
                throw new IllegalArgumentException("Time entry overlaps with existing entry from " +
                    existing.getClockIn() + " to " + existing.getClockOut());
            }
        }

        TimeEntry timeEntry = TimeEntry.builder()
                .user(user)
                .entryDate(clockIn.toLocalDate())
                .clockIn(clockIn)
                .clockOut(clockOut)
                .entryType(entryType)
                .notes(notes)
                .build();

        TimeEntry saved = timeEntryRepository.save(timeEntry);
        log.info("Manual time entry created for user {} from {} to {}",
            user.getId(), clockIn, clockOut);

        return saved;
    }
}
