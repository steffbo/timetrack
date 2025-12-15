package cc.remer.timetrack.usecase.recurringoffday;

import cc.remer.timetrack.adapter.persistence.RecurringOffDayConflictWarningRepository;
import cc.remer.timetrack.domain.recurringoffday.RecurringOffDayConflictWarning;
import cc.remer.timetrack.domain.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Use case for retrieving conflict warnings.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GetConflictWarnings {

    private final RecurringOffDayConflictWarningRepository warningRepository;

    /**
     * Get all warnings for the authenticated user.
     *
     * @param user the authenticated user
     * @param unacknowledgedOnly if true, only return unacknowledged warnings
     * @return list of warnings
     */
    @Transactional(readOnly = true)
    public List<RecurringOffDayConflictWarning> execute(User user, boolean unacknowledgedOnly) {
        log.debug("Getting conflict warnings for user: {}, unacknowledged only: {}", user.getId(), unacknowledgedOnly);

        if (unacknowledgedOnly) {
            return warningRepository.findByUserIdAndAcknowledgedFalseOrderByConflictDateDesc(user.getId());
        } else {
            return warningRepository.findByUserIdOrderByConflictDateDesc(user.getId());
        }
    }
}
