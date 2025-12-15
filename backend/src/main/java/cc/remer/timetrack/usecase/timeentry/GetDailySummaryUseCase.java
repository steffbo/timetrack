package cc.remer.timetrack.usecase.timeentry;

import cc.remer.timetrack.adapter.persistence.RecurringOffDayConflictWarningRepository;
import cc.remer.timetrack.adapter.persistence.RecurringOffDayRepository;
import cc.remer.timetrack.adapter.persistence.TimeEntryRepository;
import cc.remer.timetrack.adapter.persistence.TimeOffRepository;
import cc.remer.timetrack.adapter.persistence.WorkingHoursRepository;
import cc.remer.timetrack.domain.recurringoffday.RecurringOffDay;
import cc.remer.timetrack.domain.recurringoffday.RecurringOffDayConflictWarning;
import cc.remer.timetrack.domain.timeentry.TimeEntry;
import cc.remer.timetrack.domain.timeoff.TimeOff;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.domain.workinghours.WorkingHours;
import cc.remer.timetrack.usecase.recurringoffday.RecurringOffDayEvaluator;
import cc.remer.timetrack.usecase.timeentry.model.DailySummary;
import cc.remer.timetrack.usecase.timeentry.model.DailySummaryStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Use case for generating daily summaries with expected vs actual hours.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GetDailySummaryUseCase {

    private final TimeEntryRepository timeEntryRepository;
    private final TimeOffRepository timeOffRepository;
    private final RecurringOffDayRepository recurringOffDayRepository;
    private final WorkingHoursRepository workingHoursRepository;
    private final RecurringOffDayConflictWarningRepository conflictWarningRepository;
    private final RecurringOffDayEvaluator recurringOffDayEvaluator;

    private static final double TOLERANCE = 0.1; // 6 minutes tolerance for "matched"

    /**
     * Get daily summaries for the authenticated user within a date range.
     *
     * @param user the authenticated user
     * @param startDate start date (inclusive)
     * @param endDate end date (inclusive)
     * @return list of daily summaries
     */
    @Transactional(readOnly = true)
    public List<DailySummary> execute(User user, LocalDate startDate, LocalDate endDate) {
        log.debug("Getting daily summary for user: {}, start: {}, end: {}",
                user.getId(), startDate, endDate);

        // Get user's working hours configuration (all 7 days)
        List<WorkingHours> workingHoursList = workingHoursRepository.findByUserId(user.getId());
        if (workingHoursList.isEmpty()) {
            throw new IllegalStateException(
                    "Keine Arbeitszeitkonfiguration gefunden für Benutzer: " + user.getId());
        }

        // Map weekday to hours for quick lookup
        Map<Short, BigDecimal> hoursPerWeekday = workingHoursList.stream()
                .collect(Collectors.toMap(WorkingHours::getWeekday, WorkingHours::getHours));

        // Get time entries for the date range
        List<TimeEntry> entries = timeEntryRepository.findByUserIdAndEntryDateBetween(
                user.getId(), startDate, endDate);

        // Get time-off entries for the date range
        List<TimeOff> timeOffEntries = timeOffRepository.findByUserIdAndDateRange(
                user.getId(), startDate, endDate);

        // Get active recurring off-days for the user
        List<RecurringOffDay> allRecurringOffDays = recurringOffDayRepository
                .findByUserIdAndIsActiveTrue(user.getId());

        // Get conflict warnings for the date range
        List<RecurringOffDayConflictWarning> warnings = conflictWarningRepository
                .findByUserIdAndDateRange(user.getId(), startDate, endDate);
        Map<LocalDate, RecurringOffDayConflictWarning> warningsByDate = warnings.stream()
                .collect(Collectors.toMap(RecurringOffDayConflictWarning::getConflictDate, w -> w, (w1, w2) -> w1));

        // Group entries by date
        Map<LocalDate, List<TimeEntry>> entriesByDate = entries.stream()
                .collect(Collectors.groupingBy(TimeEntry::getEntryDate));

        // Generate summaries for each day in the range
        List<DailySummary> summaries = new ArrayList<>();
        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            List<TimeEntry> dayEntries = entriesByDate.getOrDefault(currentDate, List.of());

            // Find time-off entries for this date
            List<TimeOff> dayTimeOffEntries = findTimeOffForDate(timeOffEntries, currentDate);

            // Find recurring off-days that apply to this date
            List<RecurringOffDay> dayRecurringOffDays = findRecurringOffDaysForDate(
                    allRecurringOffDays, currentDate);

            double expectedHours = getExpectedHoursForDate(currentDate, hoursPerWeekday);
            double actualHours = calculateActualHours(dayEntries);
            DailySummaryStatus status = determineStatus(actualHours, expectedHours);

            DailySummary summary = DailySummary.builder()
                    .date(currentDate)
                    .actualHours(actualHours)
                    .expectedHours(expectedHours)
                    .status(status)
                    .entries(dayEntries)
                    .timeOffEntries(dayTimeOffEntries)
                    .recurringOffDays(dayRecurringOffDays)
                    .conflictWarning(warningsByDate.get(currentDate))
                    .build();

            summaries.add(summary);
            currentDate = currentDate.plusDays(1);
        }

        log.debug("Generated {} daily summaries for user {}", summaries.size(), user.getId());
        return summaries;
    }

    /**
     * Get expected hours for a specific date based on working hours configuration.
     */
    private double getExpectedHoursForDate(LocalDate date, Map<Short, BigDecimal> hoursPerWeekday) {
        // DayOfWeek: MONDAY=1, TUESDAY=2, ..., SUNDAY=7
        short weekday = (short) date.getDayOfWeek().getValue();

        BigDecimal hours = hoursPerWeekday.get(weekday);
        return hours != null ? hours.doubleValue() : 0.0;
    }

    /**
     * Calculate total actual hours worked on a date.
     */
    private double calculateActualHours(List<TimeEntry> entries) {
        return entries.stream()
                .map(TimeEntry::getHoursWorked)
                .filter(hours -> hours != null)  // Skip active entries (not clocked out)
                .mapToDouble(Double::doubleValue)
                .sum();
    }

    /**
     * Determine status by comparing actual vs expected hours.
     */
    private DailySummaryStatus determineStatus(double actual, double expected) {
        if (actual == 0.0) {
            return DailySummaryStatus.NO_ENTRY;
        }

        double diff = Math.abs(actual - expected);
        if (diff <= TOLERANCE) {
            return DailySummaryStatus.MATCHED;
        } else if (actual < expected) {
            return DailySummaryStatus.BELOW_EXPECTED;
        } else {
            return DailySummaryStatus.ABOVE_EXPECTED;
        }
    }

    /**
     * Find time-off entries that cover a specific date.
     */
    private List<TimeOff> findTimeOffForDate(List<TimeOff> timeOffEntries, LocalDate date) {
        return timeOffEntries.stream()
                .filter(timeOff -> !date.isBefore(timeOff.getStartDate())
                        && !date.isAfter(timeOff.getEndDate()))
                .collect(Collectors.toList());
    }

    /**
     * Find recurring off-days that apply to a specific date.
     */
    private List<RecurringOffDay> findRecurringOffDaysForDate(
            List<RecurringOffDay> recurringOffDays, LocalDate date) {
        return recurringOffDays.stream()
                .filter(rod -> recurringOffDayEvaluator.appliesToDate(rod, date))
                .collect(Collectors.toList());
    }
}
