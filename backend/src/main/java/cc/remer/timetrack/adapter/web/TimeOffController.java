package cc.remer.timetrack.adapter.web;

import cc.remer.timetrack.adapter.security.UserPrincipal;
import cc.remer.timetrack.api.TimeOffApi;
import cc.remer.timetrack.api.model.CreateTimeOffRequest;
import cc.remer.timetrack.api.model.TimeOffResponse;
import cc.remer.timetrack.api.model.UpdateTimeOffRequest;
import cc.remer.timetrack.usecase.timeoff.CreateTimeOff;
import cc.remer.timetrack.usecase.timeoff.DeleteTimeOff;
import cc.remer.timetrack.usecase.timeoff.GetTimeOffEntries;
import cc.remer.timetrack.usecase.timeoff.UpdateTimeOff;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * REST controller for time-off operations.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class TimeOffController implements TimeOffApi {

    private final GetTimeOffEntries getTimeOffEntries;
    private final CreateTimeOff createTimeOff;
    private final UpdateTimeOff updateTimeOff;
    private final DeleteTimeOff deleteTimeOff;

    @Override
    public ResponseEntity<List<TimeOffResponse>> getTimeOffEntries(LocalDate startDate, LocalDate endDate) {
        log.info("GET /time-off - Getting time-off entries for current user");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        List<TimeOffResponse> response = getTimeOffEntries.execute(principal.getId(), startDate, endDate);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<TimeOffResponse> createTimeOff(CreateTimeOffRequest createTimeOffRequest) {
        log.info("POST /time-off - Creating time-off entry for current user");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        TimeOffResponse response = createTimeOff.execute(principal.getId(), createTimeOffRequest);
        return ResponseEntity.status(201).body(response);
    }

    @Override
    public ResponseEntity<TimeOffResponse> updateTimeOff(Long id, UpdateTimeOffRequest updateTimeOffRequest) {
        log.info("PUT /time-off/{} - Updating time-off entry", id);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        TimeOffResponse response = updateTimeOff.execute(principal.getId(), id, updateTimeOffRequest);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> deleteTimeOff(Long id) {
        log.info("DELETE /time-off/{} - Deleting time-off entry", id);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        deleteTimeOff.execute(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
