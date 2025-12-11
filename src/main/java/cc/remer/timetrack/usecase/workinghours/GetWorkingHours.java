package cc.remer.timetrack.usecase.workinghours;

import cc.remer.timetrack.adapter.persistence.UserRepository;
import cc.remer.timetrack.adapter.persistence.WorkingHoursRepository;
import cc.remer.timetrack.api.model.WorkingHoursResponse;
import cc.remer.timetrack.domain.user.Role;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.domain.workinghours.WorkingHours;
import cc.remer.timetrack.exception.ForbiddenException;
import cc.remer.timetrack.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Use case for retrieving working hours configuration.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class GetWorkingHours {

    private final WorkingHoursRepository workingHoursRepository;
    private final UserRepository userRepository;
    private final WorkingHoursMapper mapper;

    /**
     * Get working hours for the authenticated user.
     *
     * @param userId the authenticated user's ID
     * @return the working hours response
     */
    public WorkingHoursResponse execute(Long userId) {
        log.debug("Getting working hours for user ID: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Benutzer nicht gefunden"));

        List<WorkingHours> workingHoursList = workingHoursRepository.findByUserId(userId);

        if (workingHoursList.isEmpty()) {
            throw new UserNotFoundException("Arbeitsstunden-Konfiguration nicht gefunden");
        }

        return mapper.toResponse(userId, workingHoursList);
    }

    /**
     * Get working hours for a specific user (Admin only).
     *
     * @param requestingUserId the authenticated user's ID
     * @param requestingUserRole the authenticated user's role
     * @param targetUserId the target user's ID
     * @return the working hours response
     */
    public WorkingHoursResponse executeForUser(Long requestingUserId, Role requestingUserRole, Long targetUserId) {
        log.debug("User {} requesting working hours for user {}", requestingUserId, targetUserId);

        // Only admins can view other users' working hours
        if (!requestingUserRole.equals(Role.ADMIN)) {
            throw new ForbiddenException("Keine Berechtigung, Arbeitsstunden anderer Benutzer anzuzeigen");
        }

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new UserNotFoundException("Benutzer nicht gefunden"));

        List<WorkingHours> workingHoursList = workingHoursRepository.findByUserId(targetUserId);

        if (workingHoursList.isEmpty()) {
            throw new UserNotFoundException("Arbeitsstunden-Konfiguration nicht gefunden");
        }

        return mapper.toResponse(targetUserId, workingHoursList);
    }
}
