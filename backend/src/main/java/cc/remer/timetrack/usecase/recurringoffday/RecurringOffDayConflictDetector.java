package cc.remer.timetrack.usecase.recurringoffday;

import cc.remer.timetrack.adapter.persistence.RecurringOffDayConflictWarningRepository;
import cc.remer.timetrack.adapter.persistence.RecurringOffDayRepository;
import cc.remer.timetrack.domain.recurringoffday.RecurringOffDay;
import cc.remer.timetrack.domain.recurringoffday.RecurringOffDayConflictWarning;
import cc.remer.timetrack.domain.timeentry.TimeEntry;
import cc.remer.timetrack.domain.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Service for detecting and managing conflicts between work entries and recurring off-days.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecurringOffDayConflictDetector {

    private final RecurringOffDayRepository recurringOffDayRepository;
    private final RecurringOffDayConflictWarningRepository warningRepository;
    private final RecurringOffDayEvaluator evaluator;

    /**
     * Check if a time entry conflicts with any recurring off-days and create a warning if needed.
     *
     * @param timeEntry the time entry to check
     * @return the created or existing warning, or null if no conflict
     */
    @Transactional
    public RecurringOffDayConflictWarning detectAndCreateWarningIfNeeded(TimeEntry timeEntry) {
        if (timeEntry == null || timeEntry.getUser() == null || timeEntry.getEntryDate() == null) {
            return null;
        }

        User user = timeEntry.getUser();
        LocalDate entryDate = timeEntry.getEntryDate();

        // Check if there's already a warning for this date
        if (warningRepository.existsByUserIdAndConflictDate(user.getId(), entryDate)) {
            log.debug("Warning already exists for user {} on date {}", user.getId(), entryDate);
            return warningRepository.findByUserIdAndConflictDate(user.getId(), entryDate).orElse(null);
        }

        // Find all active recurring off-days that could apply to this date
        List<RecurringOffDay> activeRecurringOffDays = recurringOffDayRepository
                .findActiveByUserIdAndDate(user.getId(), entryDate);

        // Check if any recurring off-day applies to this date
        RecurringOffDay conflictingOffDay = activeRecurringOffDays.stream()
                .filter(offDay -> evaluator.appliesToDate(offDay, entryDate))
                .findFirst()
                .orElse(null);

        if (conflictingOffDay != null) {
            log.info("Detected conflict: Time entry on {} conflicts with recurring off-day {} for user {}",
                    entryDate, conflictingOffDay.getId(), user.getId());

            // Create a new warning
            RecurringOffDayConflictWarning warning = RecurringOffDayConflictWarning.builder()
                    .user(user)
                    .conflictDate(entryDate)
                    .timeEntryId(timeEntry.getId())
                    .recurringOffDayId(conflictingOffDay.getId())
                    .acknowledged(false)
                    .build();

            return warningRepository.save(warning);
        }

        return null;
    }

    /**
     * Clean up warnings when a time entry is deleted.
     * Note: Database has CASCADE DELETE, but we call this for explicit cleanup and logging.
     *
     * @param timeEntryId the time entry ID that was deleted
     */
    @Transactional
    public void cleanupWarningsForTimeEntry(Long timeEntryId) {
        if (timeEntryId != null) {
            log.debug("Cleaning up warnings for deleted time entry {}", timeEntryId);
            // Use query-based delete which won't fail if records were already cascade-deleted
            // This is safe even if the database CASCADE already removed the warnings
            try {
                warningRepository.deleteByTimeEntryId(timeEntryId);
                log.debug("Cleaned up warnings for time entry {}", timeEntryId);
            } catch (Exception e) {
                // If warnings were already deleted by CASCADE, that's fine - just log it
                log.debug("No warnings to clean up for time entry {} (may have been cascade-deleted)", timeEntryId);
            }
        }
    }

    /**
     * Re-evaluate all time entries for a user to detect new conflicts.
     * Useful after creating or updating recurring off-days.
     *
     * @param userId the user ID
     * @param timeEntries the time entries to evaluate
     */
    @Transactional
    public void reevaluateConflicts(Long userId, List<TimeEntry> timeEntries) {
        if (userId == null || timeEntries == null || timeEntries.isEmpty()) {
            return;
        }

        log.info("Re-evaluating conflicts for user {} with {} time entries", userId, timeEntries.size());

        for (TimeEntry timeEntry : timeEntries) {
            detectAndCreateWarningIfNeeded(timeEntry);
        }
    }
}
