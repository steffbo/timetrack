package cc.remer.timetrack.adapter.web;

import cc.remer.timetrack.adapter.security.UserPrincipal;
import cc.remer.timetrack.api.RecurringOffDayExemptionsApi;
import cc.remer.timetrack.api.model.CreateRecurringOffDayExemptionRequest;
import cc.remer.timetrack.api.model.RecurringOffDayExemptionResponse;
import cc.remer.timetrack.usecase.recurringoffday.CreateRecurringOffDayExemption;
import cc.remer.timetrack.usecase.recurringoffday.DeleteRecurringOffDayExemption;
import cc.remer.timetrack.usecase.recurringoffday.GetRecurringOffDayExemptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for recurring off-day exemption operations.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class RecurringOffDayExemptionsController implements RecurringOffDayExemptionsApi {

    private final GetRecurringOffDayExemptions getExemptions;
    private final CreateRecurringOffDayExemption createExemption;
    private final DeleteRecurringOffDayExemption deleteExemption;

    @Override
    public ResponseEntity<List<RecurringOffDayExemptionResponse>> getExemptions(Long id) {
        log.info("GET /recurring-off-days/{}/exemptions - Getting exemptions for recurring off-day", id);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        List<RecurringOffDayExemptionResponse> response = getExemptions.execute(principal.getId(), id);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<RecurringOffDayExemptionResponse> createExemption(
            Long id, CreateRecurringOffDayExemptionRequest createRequest) {
        log.info("POST /recurring-off-days/{}/exemptions - Creating exemption for recurring off-day", id);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        RecurringOffDayExemptionResponse response = createExemption.execute(principal.getId(), id, createRequest);
        return ResponseEntity.status(201).body(response);
    }

    @Override
    public ResponseEntity<Void> deleteExemption(Long recurringOffDayId, Long exemptionId) {
        log.info("DELETE /recurring-off-days/{}/exemptions/{} - Deleting exemption", recurringOffDayId, exemptionId);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        deleteExemption.execute(principal.getId(), recurringOffDayId, exemptionId);
        return ResponseEntity.noContent().build();
    }
}
