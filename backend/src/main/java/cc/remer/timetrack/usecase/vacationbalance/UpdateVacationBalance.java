package cc.remer.timetrack.usecase.vacationbalance;

import cc.remer.timetrack.adapter.persistence.UserRepository;
import cc.remer.timetrack.adapter.persistence.VacationBalanceRepository;
import cc.remer.timetrack.api.model.UpdateVacationBalanceRequest;
import cc.remer.timetrack.api.model.VacationBalanceResponse;
import cc.remer.timetrack.domain.user.Role;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.domain.vacationbalance.VacationBalance;
import cc.remer.timetrack.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Use case to update vacation balance.
 * Users can update their own vacation balance, admins can update any user's balance.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UpdateVacationBalance {

    private final VacationBalanceRepository vacationBalanceRepository;
    private final UserRepository userRepository;
    private final VacationBalanceMapper mapper;

    /**
     * Execute the use case to update vacation balance.
     *
     * @param currentUserId the ID of the authenticated user making the request
     * @param currentUserRole the role of the authenticated user
     * @param request the update request
     * @return the updated vacation balance response
     */
    @Transactional
    public VacationBalanceResponse execute(Long currentUserId, Role currentUserRole, UpdateVacationBalanceRequest request) {
        log.info("Updating vacation balance for user ID: {} and year: {} (requested by user ID: {})",
                request.getUserId(), request.getYear(), currentUserId);

        // Authorization check: users can only update their own balance, admins can update anyone's
        if (currentUserRole != Role.ADMIN && !currentUserId.equals(request.getUserId())) {
            log.warn("User ID {} attempted to update vacation balance for user ID {} without permission",
                    currentUserId, request.getUserId());
            throw new AccessDeniedException("You can only update your own vacation balance");
        }

        // Find user
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + request.getUserId()));

        // Find or create vacation balance
        VacationBalance balance = vacationBalanceRepository.findByUserIdAndYear(request.getUserId(), request.getYear())
                .orElseGet(() -> {
                    log.info("Creating new vacation balance for user ID: {} and year: {}", request.getUserId(), request.getYear());
                    return VacationBalance.builder()
                            .user(user)
                            .year(request.getYear())
                            .annualAllowanceDays(BigDecimal.valueOf(30.0)) // Default 30 days
                            .carriedOverDays(BigDecimal.ZERO)
                            .adjustmentDays(BigDecimal.ZERO)
                            .usedDays(BigDecimal.ZERO)
                            .build();
                });

        // Map update request
        mapper.mapUpdateRequest(request, balance);

        // Save
        VacationBalance updated = vacationBalanceRepository.save(balance);
        log.info("Updated vacation balance ID: {}", updated.getId());

        return mapper.toResponse(updated);
    }
}
