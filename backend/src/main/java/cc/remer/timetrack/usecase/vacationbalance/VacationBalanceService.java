package cc.remer.timetrack.usecase.vacationbalance;

import cc.remer.timetrack.adapter.persistence.TimeOffRepository;
import cc.remer.timetrack.adapter.persistence.UserRepository;
import cc.remer.timetrack.adapter.persistence.VacationBalanceRepository;
import cc.remer.timetrack.domain.timeoff.TimeOff;
import cc.remer.timetrack.domain.timeoff.TimeOffType;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.domain.vacationbalance.VacationBalance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Service to manage vacation balance calculations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VacationBalanceService {

    private final VacationBalanceRepository vacationBalanceRepository;
    private final TimeOffRepository timeOffRepository;
    private final UserRepository userRepository;

    private static final BigDecimal DEFAULT_ANNUAL_ALLOWANCE_DAYS = new BigDecimal("30.0");

    /**
     * Recalculate vacation balance for a user and year based on VACATION time-off entries.
     *
     * @param userId the user ID
     * @param year the year
     */
    @Transactional
    public void recalculateVacationBalance(Long userId, int year) {
        log.info("Recalculating vacation balance for user ID: {} and year: {}", userId, year);

        // Get or create vacation balance
        VacationBalance balance = vacationBalanceRepository.findByUserIdAndYear(userId, year)
                .orElseGet(() -> createDefaultBalance(userId, year));

        // Calculate used days from VACATION time-off entries
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);

        List<TimeOff> vacationEntries = timeOffRepository.findByUserIdAndTypeAndYear(
                userId, TimeOffType.VACATION, yearStart, yearEnd);

        BigDecimal totalUsedDays = vacationEntries.stream()
                .map(this::calculateDaysForTimeOff)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Update used days
        balance.setUsedDays(totalUsedDays);
        balance.calculateRemainingDays();

        vacationBalanceRepository.save(balance);
        log.info("Updated vacation balance for user ID: {} and year: {}. Used days: {}, Remaining: {}",
                userId, year, totalUsedDays, balance.getRemainingDays());
    }

    /**
     * Calculate the number of days for a time-off entry.
     * Counts inclusive days from start to end date.
     *
     * @param timeOff the time-off entry
     * @return the number of days
     */
    private BigDecimal calculateDaysForTimeOff(TimeOff timeOff) {
        // Calculate number of days (inclusive)
        long daysBetween = ChronoUnit.DAYS.between(timeOff.getStartDate(), timeOff.getEndDate()) + 1;
        return BigDecimal.valueOf(daysBetween);
    }

    /**
     * Create a default vacation balance for a user and year.
     *
     * @param userId the user ID
     * @param year the year
     * @return the created vacation balance
     */
    private VacationBalance createDefaultBalance(Long userId, int year) {
        log.info("Creating default vacation balance for user ID: {} and year: {} with {} days",
                userId, year, DEFAULT_ANNUAL_ALLOWANCE_DAYS);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        VacationBalance balance = VacationBalance.builder()
                .user(user)
                .year(year)
                .annualAllowanceDays(DEFAULT_ANNUAL_ALLOWANCE_DAYS)
                .carriedOverDays(BigDecimal.ZERO)
                .adjustmentDays(BigDecimal.ZERO)
                .usedDays(BigDecimal.ZERO)
                .build();

        balance.calculateRemainingDays();

        return balance;
    }
}
