package cc.remer.timetrack.usecase.timeentry;

import cc.remer.timetrack.adapter.persistence.TimeEntryRepository;
import cc.remer.timetrack.domain.timeentry.TimeEntry;
import cc.remer.timetrack.domain.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Use case for retrieving time entries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GetTimeEntriesUseCase {

    private final TimeEntryRepository timeEntryRepository;

    /**
     * Get time entries for the authenticated user within a date range.
     *
     * @param user the authenticated user
     * @param startDate optional start date (null = all time)
     * @param endDate optional end date (null = all time)
     * @return list of time entries, sorted by clockIn desc
     */
    @Transactional(readOnly = true)
    public List<TimeEntry> execute(User user, LocalDate startDate, LocalDate endDate) {
        log.debug("Getting time entries for user: {}, start: {}, end: {}",
                user.getId(), startDate, endDate);

        List<TimeEntry> entries;

        if (startDate != null && endDate != null) {
            entries = timeEntryRepository.findByUserIdAndEntryDateBetween(
                    user.getId(), startDate, endDate);
        } else {
            entries = timeEntryRepository.findByUserId(user.getId());
        }

        // Sort by clockIn descending (newest first)
        entries.sort(Comparator.comparing(TimeEntry::getClockIn).reversed());

        log.debug("Found {} time entries for user {}", entries.size(), user.getId());
        return entries;
    }
}
