package cc.remer.timetrack.usecase.timeentry;

import cc.remer.timetrack.adapter.persistence.TimeEntryRepository;
import cc.remer.timetrack.adapter.persistence.WorkingHoursRepository;
import cc.remer.timetrack.domain.timeentry.TimeEntry;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.domain.workinghours.WorkingHours;
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
    private final WorkingHoursRepository workingHoursRepository;

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

        // Group entries by date
        Map<LocalDate, List<TimeEntry>> entriesByDate = entries.stream()
                .collect(Collectors.groupingBy(TimeEntry::getEntryDate));

        // Generate summaries for each day in the range
        List<DailySummary> summaries = new ArrayList<>();
        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            List<TimeEntry> dayEntries = entriesByDate.getOrDefault(currentDate, List.of());
            double expectedHours = getExpectedHoursForDate(currentDate, hoursPerWeekday);
            double actualHours = calculateActualHours(dayEntries);
            DailySummaryStatus status = determineStatus(actualHours, expectedHours);

            DailySummary summary = new DailySummary(
                    currentDate,
                    actualHours,
                    expectedHours,
                    status,
                    dayEntries
            );

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
}
