package cc.remer.timetrack.adapter.web;

import cc.remer.timetrack.adapter.security.UserPrincipal;
import cc.remer.timetrack.api.VacationBalanceApi;
import cc.remer.timetrack.api.model.UpdateVacationBalanceRequest;
import cc.remer.timetrack.api.model.VacationBalanceResponse;
import cc.remer.timetrack.usecase.vacationbalance.GetVacationBalance;
import cc.remer.timetrack.usecase.vacationbalance.UpdateVacationBalance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for vacation balance operations.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class VacationBalanceController implements VacationBalanceApi {

    private final GetVacationBalance getVacationBalance;
    private final UpdateVacationBalance updateVacationBalance;

    @Override
    public ResponseEntity<VacationBalanceResponse> getVacationBalance(Integer year) {
        log.info("GET /vacation-balance - Getting vacation balance for current user");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        VacationBalanceResponse response = getVacationBalance.execute(principal.getId(), year);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<VacationBalanceResponse> updateVacationBalance(UpdateVacationBalanceRequest updateVacationBalanceRequest) {
        log.info("PUT /vacation-balance - Updating vacation balance");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        VacationBalanceResponse response = updateVacationBalance.execute(
                principal.getId(),
                principal.getRole(),
                updateVacationBalanceRequest
        );
        return ResponseEntity.ok(response);
    }
}
