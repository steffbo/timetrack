package cc.remer.timetrack.usecase.timeentry;

import cc.remer.timetrack.adapter.persistence.TimeEntryRepository;
import cc.remer.timetrack.domain.timeentry.TimeEntry;
import cc.remer.timetrack.domain.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Use case for clocking out (ending the current work session).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClockOutUseCase {

    private final TimeEntryRepository timeEntryRepository;

    /**
     * Clock out for the authenticated user.
     *
     * @param user the authenticated user
     * @param notes optional notes to add/update for this work session
     * @return the updated time entry
     * @throws IllegalStateException if user is not currently clocked in
     */
    @Transactional
    public TimeEntry execute(User user, String notes) {
        log.debug("Attempting to clock out user: {}", user.getId());

        TimeEntry activeEntry = timeEntryRepository.findByUserIdAndClockOutIsNull(user.getId())
                .orElseThrow(() -> new IllegalStateException("Nicht eingecheckt. Bitte zuerst einchecken."));

        LocalDateTime now = LocalDateTime.now();
        activeEntry.setClockOut(now);

        if (notes != null) {
            activeEntry.setNotes(notes);
        }

        TimeEntry saved = timeEntryRepository.save(activeEntry);
        log.info("User {} clocked out at {}, duration: {} hours",
                user.getId(), now, saved.getHoursWorked());

        return saved;
    }
}
