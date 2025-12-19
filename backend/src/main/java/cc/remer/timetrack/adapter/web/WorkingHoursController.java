package cc.remer.timetrack.adapter.web;

import cc.remer.timetrack.adapter.security.UserPrincipal;
import cc.remer.timetrack.api.WorkingHoursApi;
import cc.remer.timetrack.api.model.UpdateWorkingDayConfig;
import cc.remer.timetrack.api.model.UpdateWorkingHoursRequest;
import cc.remer.timetrack.api.model.WorkingDayConfig;
import cc.remer.timetrack.api.model.WorkingHoursResponse;
import cc.remer.timetrack.domain.user.Role;
import cc.remer.timetrack.usecase.workinghours.GetWorkingHours;
import cc.remer.timetrack.usecase.workinghours.UpdateWorkingDay;
import cc.remer.timetrack.usecase.workinghours.UpdateWorkingHours;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for working hours configuration operations.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class WorkingHoursController implements WorkingHoursApi {

    private final GetWorkingHours getWorkingHours;
    private final UpdateWorkingHours updateWorkingHours;
    private final UpdateWorkingDay updateWorkingDay;

    @Override
    public ResponseEntity<WorkingHoursResponse> getWorkingHours() {
        log.info("GET /working-hours - Getting working hours for current user");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        WorkingHoursResponse response = getWorkingHours.execute(principal.getId());
        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WorkingHoursResponse> getWorkingHoursByUserId(Long userId) {
        log.info("GET /working-hours/{} - Getting working hours for user ID: {}", userId, userId);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        WorkingHoursResponse response = getWorkingHours.executeForUser(
                principal.getId(),
                principal.getRole(),
                userId
        );
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<WorkingHoursResponse> updateWorkingHours(UpdateWorkingHoursRequest updateWorkingHoursRequest) {
        log.info("PUT /working-hours - Updating working hours for current user");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        WorkingHoursResponse response = updateWorkingHours.execute(principal.getId(), updateWorkingHoursRequest);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<WorkingDayConfig> updateWorkingDay(Integer weekday, UpdateWorkingDayConfig updateWorkingDayConfig) {
        log.info("PUT /working-hours/day/{} - Updating working day for current user", weekday);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        WorkingDayConfig response = updateWorkingDay.execute(principal.getId(), weekday, updateWorkingDayConfig);
        return ResponseEntity.ok(response);
    }
}
