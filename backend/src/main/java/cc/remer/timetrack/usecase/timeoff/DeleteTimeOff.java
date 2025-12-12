package cc.remer.timetrack.usecase.timeoff;

import cc.remer.timetrack.adapter.persistence.TimeOffRepository;
import cc.remer.timetrack.domain.timeoff.TimeOff;
import cc.remer.timetrack.exception.ForbiddenException;
import cc.remer.timetrack.exception.TimeOffNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case to delete a time-off entry.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteTimeOff {

    private final TimeOffRepository timeOffRepository;

    /**
     * Execute the use case to delete a time-off entry.
     *
     * @param userId the user ID
     * @param id the time-off ID
     */
    @Transactional
    public void execute(Long userId, Long id) {
        log.info("Deleting time-off entry ID: {} for user ID: {}", id, userId);

        // Find entity
        TimeOff entity = timeOffRepository.findById(id)
                .orElseThrow(() -> new TimeOffNotFoundException(id));

        // Check user owns this time-off entry
        if (!entity.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Sie haben keine Berechtigung, diesen Abwesenheitseintrag zu löschen");
        }

        // Delete
        timeOffRepository.delete(entity);
        log.info("Deleted time-off entry ID: {}", id);
    }
}
