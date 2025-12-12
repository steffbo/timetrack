package cc.remer.timetrack.adapter.web;

import cc.remer.timetrack.adapter.security.UserPrincipal;
import cc.remer.timetrack.api.RecurringOffDaysApi;
import cc.remer.timetrack.api.model.CreateRecurringOffDayRequest;
import cc.remer.timetrack.api.model.RecurringOffDayResponse;
import cc.remer.timetrack.api.model.UpdateRecurringOffDayRequest;
import cc.remer.timetrack.usecase.recurringoffday.CreateRecurringOffDay;
import cc.remer.timetrack.usecase.recurringoffday.DeleteRecurringOffDay;
import cc.remer.timetrack.usecase.recurringoffday.GetRecurringOffDays;
import cc.remer.timetrack.usecase.recurringoffday.UpdateRecurringOffDay;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for recurring off-days operations.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class RecurringOffDaysController implements RecurringOffDaysApi {

    private final GetRecurringOffDays getRecurringOffDays;
    private final CreateRecurringOffDay createRecurringOffDay;
    private final UpdateRecurringOffDay updateRecurringOffDay;
    private final DeleteRecurringOffDay deleteRecurringOffDay;

    @Override
    public ResponseEntity<List<RecurringOffDayResponse>> getRecurringOffDays() {
        log.info("GET /recurring-off-days - Getting recurring off-days for current user");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        List<RecurringOffDayResponse> response = getRecurringOffDays.execute(principal.getId());
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<RecurringOffDayResponse> createRecurringOffDay(CreateRecurringOffDayRequest createRecurringOffDayRequest) {
        log.info("POST /recurring-off-days - Creating recurring off-day for current user");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        RecurringOffDayResponse response = createRecurringOffDay.execute(principal.getId(), createRecurringOffDayRequest);
        return ResponseEntity.status(201).body(response);
    }

    @Override
    public ResponseEntity<RecurringOffDayResponse> updateRecurringOffDay(Long id, UpdateRecurringOffDayRequest updateRecurringOffDayRequest) {
        log.info("PUT /recurring-off-days/{} - Updating recurring off-day", id);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        RecurringOffDayResponse response = updateRecurringOffDay.execute(principal.getId(), id, updateRecurringOffDayRequest);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> deleteRecurringOffDay(Long id) {
        log.info("DELETE /recurring-off-days/{} - Deleting recurring off-day", id);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        deleteRecurringOffDay.execute(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
