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
 * Use case for clocking in (starting a new work session).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClockInUseCase {

    private final TimeEntryRepository timeEntryRepository;

    /**
     * Clock in for the authenticated user.
     *
     * @param user the authenticated user
     * @param notes optional notes for this work session
     * @return the created time entry
     * @throws IllegalStateException if user is already clocked in
     */
    @Transactional
    public TimeEntry execute(User user, String notes) {
        log.debug("Attempting to clock in user: {}", user.getId());

        // Check if user already has an active time entry
        timeEntryRepository.findByUserIdAndClockOutIsNull(user.getId())
                .ifPresent(activeEntry -> {
                    throw new IllegalStateException("Bereits eingecheckt. Bitte zuerst auschecken.");
                });

        LocalDateTime now = LocalDateTime.now();

        TimeEntry timeEntry = TimeEntry.builder()
                .user(user)
                .entryDate(now.toLocalDate())
                .clockIn(now)
                .entryType(EntryType.WORK)
                .notes(notes)
                .build();

        TimeEntry saved = timeEntryRepository.save(timeEntry);
        log.info("User {} clocked in at {}", user.getId(), now);

        return saved;
    }
}
