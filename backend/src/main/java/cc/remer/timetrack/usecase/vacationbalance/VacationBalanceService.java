package cc.remer.timetrack.usecase.vacationbalance;

import cc.remer.timetrack.adapter.persistence.TimeOffRepository;
import cc.remer.timetrack.adapter.persistence.UserRepository;
import cc.remer.timetrack.adapter.persistence.VacationBalanceRepository;
import cc.remer.timetrack.domain.timeoff.TimeOff;
import cc.remer.timetrack.domain.timeoff.TimeOffType;
import cc.remer.timetrack.domain.user.GermanState;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.domain.vacationbalance.VacationBalance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    private final WorkingDaysCalculator workingDaysCalculator;

    private static final BigDecimal DEFAULT_ANNUAL_ALLOWANCE_DAYS = new BigDecimal("30.0");

    /**
     * Recalculate vacation balance for a user and year based on VACATION time-off entries.
     * Only updates usedDays (past vacation). plannedDays is calculated on-demand.
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

        // Get all VACATION time-off entries for the year
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        LocalDate today = LocalDate.now();

        List<TimeOff> vacationEntries = timeOffRepository.findByUserIdAndTypeAndYear(
                userId, TimeOffType.VACATION, yearStart, yearEnd);

        // Calculate used days (only past vacation entries where end date <= today)
        BigDecimal totalUsedDays = vacationEntries.stream()
                .filter(timeOff -> !timeOff.getEndDate().isAfter(today))
                .map(this::calculateDaysForTimeOff)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Update used days (this is what gets stored)
        balance.setUsedDays(totalUsedDays);
        balance.calculateRemainingDays();

        vacationBalanceRepository.save(balance);
        log.info("Updated vacation balance for user ID: {} and year: {}. Used: {}, Remaining: {}",
                userId, year, totalUsedDays, balance.getRemainingDays());
    }

    /**
     * Calculate total planned vacation days for a user and year.
     * This includes all VACATION time-off entries regardless of dates.
     *
     * @param userId the user ID
     * @param year the year
     * @return total planned days
     */
    public BigDecimal calculatePlannedDays(Long userId, int year) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);

        List<TimeOff> vacationEntries = timeOffRepository.findByUserIdAndTypeAndYear(
                userId, TimeOffType.VACATION, yearStart, yearEnd);

        return vacationEntries.stream()
                .map(this::calculateDaysForTimeOff)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculate the number of vacation days for a time-off entry.
     * Only counts working days, excluding:
     * - Weekends (non-working days according to user's working hours)
     * - Public holidays
     * - Recurring off-days
     * Supports half-day holidays (Dec 24 & 31) which count as 0.5 days.
     *
     * @param timeOff the time-off entry
     * @return the number of working vacation days (may include fractional days)
     */
    private BigDecimal calculateDaysForTimeOff(TimeOff timeOff) {
        User user = timeOff.getUser();
        GermanState userState = user.getState();

        BigDecimal workingDays = workingDaysCalculator.calculateWorkingDays(
                user.getId(),
                userState,
                timeOff.getStartDate(),
                timeOff.getEndDate()
        );

        return workingDays;
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
