package cc.remer.timetrack.usecase.report;

import cc.remer.timetrack.adapter.persistence.TimeEntryRepository;
import cc.remer.timetrack.adapter.persistence.UserRepository;
import cc.remer.timetrack.adapter.persistence.WorkingHoursRepository;
import cc.remer.timetrack.domain.timeentry.TimeEntry;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.domain.workinghours.WorkingHours;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Use case for exporting monthly time reports as PDF or CSV.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExportMonthlyReportUseCase {

    private final TimeEntryRepository timeEntryRepository;
    private final WorkingHoursRepository workingHoursRepository;
    private final UserRepository userRepository;
    private final MonthlyReportPdfGenerator pdfGenerator;
    private final MonthlyReportCsvGenerator csvGenerator;

    /**
     * Export a monthly time report for a user.
     *
     * @param userId the user ID
     * @param year   the year
     * @param month  the month (1-12)
     * @param user   the user entity (may have partial data)
     * @return PDF report as byte array
     */
    @Transactional(readOnly = true)
    public byte[] execute(Long userId, int year, int month, User user) {
        log.info("Exporting monthly report for user {} for {}-{}", userId, year, month);

        // Validate parameters
        validateParameters(year, month);

        // Fetch full user details for the PDF header
        User fullUser = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // Calculate date range
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        // Fetch time entries for the month
        List<TimeEntry> timeEntries = timeEntryRepository.findByUserIdAndEntryDateBetween(
                userId,
                startDate,
                endDate
        );

        log.debug("Found {} time entries for the period", timeEntries.size());

        // Fetch working hours configuration
        List<WorkingHours> workingHoursConfig = workingHoursRepository.findByUserId(userId);
        Map<Short, WorkingHours> workingHoursMap = workingHoursConfig.stream()
                .collect(Collectors.toMap(WorkingHours::getWeekday, wh -> wh));

        log.debug("Found working hours configuration for {} weekdays", workingHoursMap.size());

        // Group time entries by date
        Map<LocalDate, List<TimeEntry>> entriesByDate = timeEntries.stream()
                .collect(Collectors.groupingBy(TimeEntry::getEntryDate));

        // Generate daily report entries for each day in the month
        List<DailyReportEntry> dailyEntries = new ArrayList<>();
        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            DailyReportEntry dailyEntry = createDailyEntry(
                    currentDate,
                    entriesByDate.getOrDefault(currentDate, Collections.emptyList()),
                    workingHoursMap
            );
            dailyEntries.add(dailyEntry);
            currentDate = currentDate.plusDays(1);
        }

        log.debug("Generated {} daily report entries", dailyEntries.size());

        // Generate PDF with full user details
        return pdfGenerator.generateMonthlyReport(year, month, fullUser, dailyEntries);
    }

    /**
     * Export a monthly time report as CSV (alternative format).
     */
    public byte[] executeAsCsv(Long userId, int year, int month, User user) {
        log.info("Exporting monthly CSV report for user {} for {}-{}", userId, year, month);

        // Validate parameters
        validateParameters(year, month);

        // Calculate date range
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        // Fetch time entries for the month
        List<TimeEntry> timeEntries = timeEntryRepository.findByUserIdAndEntryDateBetween(
                userId,
                startDate,
                endDate
        );

        // Fetch working hours configuration
        List<WorkingHours> workingHoursConfig = workingHoursRepository.findByUserId(userId);
        Map<Short, WorkingHours> workingHoursMap = workingHoursConfig.stream()
                .collect(Collectors.toMap(WorkingHours::getWeekday, wh -> wh));

        // Group time entries by date
        Map<LocalDate, List<TimeEntry>> entriesByDate = timeEntries.stream()
                .collect(Collectors.groupingBy(TimeEntry::getEntryDate));

        // Generate daily report entries for each day in the month
        List<DailyReportEntry> dailyEntries = new ArrayList<>();
        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            DailyReportEntry dailyEntry = createDailyEntry(
                    currentDate,
                    entriesByDate.getOrDefault(currentDate, Collections.emptyList()),
                    workingHoursMap
            );
            dailyEntries.add(dailyEntry);
            currentDate = currentDate.plusDays(1);
        }

        // Generate CSV
        return csvGenerator.generateMonthlyReport(year, month, user, dailyEntries);
    }

    /**
     * Validate year and month parameters.
     */
    private void validateParameters(int year, int month) {
        if (year < 2000 || year > 2100) {
            throw new IllegalArgumentException("Year must be between 2000 and 2100");
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12");
        }
    }

    /**
     * Create a daily report entry for a specific date.
     */
    private DailyReportEntry createDailyEntry(
            LocalDate date,
            List<TimeEntry> entries,
            Map<Short, WorkingHours> workingHoursMap
    ) {
        // Get expected hours for this day of week
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        short weekdayValue = (short) dayOfWeek.getValue();
        WorkingHours workingHours = workingHoursMap.get(weekdayValue);

        Double expectedHours = (workingHours != null && workingHours.getIsWorkingDay())
                ? workingHours.getHours().doubleValue()
                : 0.0;

        if (entries.isEmpty()) {
            // No entries for this day
            return DailyReportEntry.builder()
                    .date(date)
                    .startTime(null)
                    .endTime(null)
                    .breakMinutes(0)
                    .totalHours(null)
                    .expectedHours(expectedHours)
                    .overtime(null)
                    .build();
        }

        // Find first clock-in and last clock-out
        LocalTime startTime = entries.stream()
                .map(TimeEntry::getClockIn)
                .min(Comparator.naturalOrder())
                .map(java.time.LocalDateTime::toLocalTime)
                .orElse(null);

        // Check if any entry is still active (not clocked out)
        boolean hasActiveEntry = entries.stream().anyMatch(TimeEntry::isActive);

        LocalTime endTime = null;
        Double totalHours = null;
        Double overtime = null;

        if (!hasActiveEntry) {
            // All entries are clocked out, calculate totals
            endTime = entries.stream()
                    .map(TimeEntry::getClockOut)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .map(java.time.LocalDateTime::toLocalTime)
                    .orElse(null);

            // Calculate total hours
            totalHours = entries.stream()
                    .map(TimeEntry::getHoursWorked)
                    .filter(Objects::nonNull)
                    .mapToDouble(Double::doubleValue)
                    .sum();

            // Calculate overtime
            overtime = totalHours - expectedHours;
        }

        return DailyReportEntry.builder()
                .date(date)
                .startTime(startTime)
                .endTime(endTime)
                .breakMinutes(0) // Hardcoded to 0 for now
                .totalHours(totalHours)
                .expectedHours(expectedHours)
                .overtime(overtime)
                .build();
    }
}
