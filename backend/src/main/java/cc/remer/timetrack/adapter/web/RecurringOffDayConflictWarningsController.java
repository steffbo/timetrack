package cc.remer.timetrack.adapter.web;

import cc.remer.timetrack.adapter.persistence.UserRepository;
import cc.remer.timetrack.adapter.security.UserPrincipal;
import cc.remer.timetrack.api.RecurringOffDayWarningsApi;
import cc.remer.timetrack.api.model.RecurringOffDayConflictWarningResponse;
import cc.remer.timetrack.domain.recurringoffday.RecurringOffDayConflictWarning;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.usecase.recurringoffday.AcknowledgeConflictWarning;
import cc.remer.timetrack.usecase.recurringoffday.GetConflictWarnings;
import cc.remer.timetrack.usecase.recurringoffday.RecurringOffDayConflictWarningMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for recurring off-day conflict warnings.
 */
@RestController
@RequiredArgsConstructor
public class RecurringOffDayConflictWarningsController implements RecurringOffDayWarningsApi {

    private final GetConflictWarnings getConflictWarnings;
    private final AcknowledgeConflictWarning acknowledgeConflictWarning;
    private final RecurringOffDayConflictWarningMapper mapper;
    private final UserRepository userRepository;

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }

    @Override
    public ResponseEntity<List<RecurringOffDayConflictWarningResponse>> getConflictWarnings(
            Boolean unacknowledgedOnly) {
        User user = getCurrentUser();
        boolean unacknowledgedFilter = unacknowledgedOnly != null && unacknowledgedOnly;
        List<RecurringOffDayConflictWarning> warnings = getConflictWarnings.execute(user, unacknowledgedFilter);

        List<RecurringOffDayConflictWarningResponse> responses = warnings.stream()
                .map(mapper::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    @Override
    public ResponseEntity<RecurringOffDayConflictWarningResponse> acknowledgeWarning(Long id) {
        User user = getCurrentUser();
        RecurringOffDayConflictWarning warning = acknowledgeConflictWarning.execute(user, id);
        RecurringOffDayConflictWarningResponse response = mapper.toResponse(warning);

        return ResponseEntity.ok(response);
    }
}
