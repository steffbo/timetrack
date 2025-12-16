package cc.remer.timetrack.usecase.recurringoffday;

import cc.remer.timetrack.adapter.persistence.RecurringOffDayRepository;
import cc.remer.timetrack.api.model.RecurringOffDayResponse;
import cc.remer.timetrack.api.model.UpdateRecurringOffDayRequest;
import cc.remer.timetrack.domain.recurringoffday.RecurringOffDay;
import cc.remer.timetrack.exception.RecurringOffDayNotFoundException;
import cc.remer.timetrack.usecase.AuthorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case to update an existing recurring off-day rule.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UpdateRecurringOffDay {

    private final RecurringOffDayRepository recurringOffDayRepository;
    private final RecurringOffDayMapper mapper;
    private final AuthorizationService authorizationService;

    /**
     * Execute the use case to update a recurring off-day.
     *
     * @param userId the user ID
     * @param id the recurring off-day ID
     * @param request the update request
     * @return the updated recurring off-day response
     */
    @Transactional
    public RecurringOffDayResponse execute(Long userId, Long id, UpdateRecurringOffDayRequest request) {
        log.info("Updating recurring off-day ID: {} for user ID: {}", id, userId);

        // Find entity
        RecurringOffDay entity = recurringOffDayRepository.findById(id)
                .orElseThrow(() -> new RecurringOffDayNotFoundException(id));

        // Check user owns this recurring off-day
        authorizationService.validateOwnership(entity.getUser(), userId, "diese wiederkehrende Abwesenheit");

        // Map update request
        mapper.mapUpdateRequest(request, entity);

        // Save
        RecurringOffDay updated = recurringOffDayRepository.save(entity);
        log.info("Updated recurring off-day ID: {}", updated.getId());

        return mapper.toResponse(updated);
    }
}
