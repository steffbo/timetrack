package cc.remer.timetrack.usecase.report;

import cc.remer.timetrack.adapter.persistence.RecurringOffDayRepository;
import cc.remer.timetrack.adapter.persistence.TimeEntryRepository;
import cc.remer.timetrack.adapter.persistence.TimeOffRepository;
import cc.remer.timetrack.adapter.persistence.UserRepository;
import cc.remer.timetrack.adapter.persistence.WorkingHoursRepository;
import cc.remer.timetrack.domain.recurringoffday.RecurringOffDay;
import cc.remer.timetrack.domain.timeentry.TimeEntry;
import cc.remer.timetrack.domain.timeoff.TimeOff;
import cc.remer.timetrack.domain.timeoff.TimeOffType;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.domain.workinghours.WorkingHours;
import cc.remer.timetrack.usecase.recurringoffday.RecurringOffDayEvaluator;
import cc.remer.timetrack.usecase.report.DailyReportEntry.DayType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Use case for exporting monthly time reports as PDF or CSV.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExportMonthlyReportUseCase {

    private static final ZoneId EUROPE_BERLIN = ZoneId.of("Europe/Berlin");

    private final TimeEntryRepository timeEntryRepository;
    private final TimeOffRepository timeOffRepository;
    private final WorkingHoursRepository workingHoursRepository;
    private final UserRepository userRepository;
    private final RecurringOffDayRepository recurringOffDayRepository;
    private final RecurringOffDayEvaluator recurringOffDayEvaluator;
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

        // Fetch time-off entries for the month
        List<TimeOff> timeOffEntries = timeOffRepository.findByUserIdAndDateRange(
                userId,
                startDate,
                endDate
        );

        log.debug("Found {} time-off entries for the period", timeOffEntries.size());

        // Fetch working hours configuration
        List<WorkingHours> workingHoursConfig = workingHoursRepository.findByUserId(userId);
        Map<Short, WorkingHours> workingHoursMap = workingHoursConfig.stream()
                .collect(Collectors.toMap(WorkingHours::getWeekday, wh -> wh));

        log.debug("Found working hours configuration for {} weekdays", workingHoursMap.size());

        // Fetch recurring off-days for the user
        List<RecurringOffDay> recurringOffDays = recurringOffDayRepository.findByUserIdAndIsActiveTrue(userId);
        
        // Build set of dates that are recurring off-days
        Set<LocalDate> recurringOffDayDates = buildRecurringOffDayDates(recurringOffDays, startDate, endDate);
        log.debug("Found {} recurring off-day dates for the period", recurringOffDayDates.size());

        // Group time entries by date
        Map<LocalDate, List<TimeEntry>> entriesByDate = timeEntries.stream()
                .collect(Collectors.groupingBy(TimeEntry::getEntryDate));

        // Build map of time-off by date
        Map<LocalDate, TimeOff> timeOffByDate = buildTimeOffMap(timeOffEntries, startDate, endDate);

        // Generate daily report entries for each day in the month
        List<DailyReportEntry> dailyEntries = new ArrayList<>();
        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            DailyReportEntry dailyEntry = createDailyEntry(
                    currentDate,
                    entriesByDate.getOrDefault(currentDate, Collections.emptyList()),
                    workingHoursMap,
                    timeOffByDate,
                    recurringOffDayDates
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

        // Fetch time-off entries for the month
        List<TimeOff> timeOffEntries = timeOffRepository.findByUserIdAndDateRange(
                userId,
                startDate,
                endDate
        );

        // Fetch working hours configuration
        List<WorkingHours> workingHoursConfig = workingHoursRepository.findByUserId(userId);
        Map<Short, WorkingHours> workingHoursMap = workingHoursConfig.stream()
                .collect(Collectors.toMap(WorkingHours::getWeekday, wh -> wh));

        // Fetch recurring off-days for the user
        List<RecurringOffDay> recurringOffDays = recurringOffDayRepository.findByUserIdAndIsActiveTrue(userId);
        
        // Build set of dates that are recurring off-days
        Set<LocalDate> recurringOffDayDates = buildRecurringOffDayDates(recurringOffDays, startDate, endDate);

        // Group time entries by date
        Map<LocalDate, List<TimeEntry>> entriesByDate = timeEntries.stream()
                .collect(Collectors.groupingBy(TimeEntry::getEntryDate));

        // Build map of time-off by date
        Map<LocalDate, TimeOff> timeOffByDate = buildTimeOffMap(timeOffEntries, startDate, endDate);

        // Generate daily report entries for each day in the month
        List<DailyReportEntry> dailyEntries = new ArrayList<>();
        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            DailyReportEntry dailyEntry = createDailyEntry(
                    currentDate,
                    entriesByDate.getOrDefault(currentDate, Collections.emptyList()),
                    workingHoursMap,
                    timeOffByDate,
                    recurringOffDayDates
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
     * Build a map of time-off entries by date.
     * If multiple time-off entries exist for the same date, prioritize sick days, then vacation.
     * Returns the complete TimeOff object so we can access notes.
     */
    private Map<LocalDate, TimeOff> buildTimeOffMap(List<TimeOff> timeOffEntries, LocalDate startDate, LocalDate endDate) {
        Map<LocalDate, TimeOff> timeOffByDate = new HashMap<>();

        for (TimeOff timeOff : timeOffEntries) {
            LocalDate currentDate = timeOff.getStartDate().isBefore(startDate) ? startDate : timeOff.getStartDate();
            LocalDate lastDate = timeOff.getEndDate().isAfter(endDate) ? endDate : timeOff.getEndDate();

            while (!currentDate.isAfter(lastDate)) {
                // Prioritize sick days over other types
                TimeOff existingTimeOff = timeOffByDate.get(currentDate);
                TimeOffType existingType = existingTimeOff != null ? existingTimeOff.getTimeOffType() : null;

                if (existingType == null ||
                    (timeOff.getTimeOffType() == TimeOffType.SICK && existingType != TimeOffType.SICK && existingType != TimeOffType.CHILD_SICK) ||
                    (timeOff.getTimeOffType() == TimeOffType.CHILD_SICK && existingType != TimeOffType.SICK && existingType != TimeOffType.CHILD_SICK)) {
                    timeOffByDate.put(currentDate, timeOff);
                }
                currentDate = currentDate.plusDays(1);
            }
        }

        return timeOffByDate;
    }

    /**
     * Create a daily report entry for a specific date.
     */
    private DailyReportEntry createDailyEntry(
            LocalDate date,
            List<TimeEntry> entries,
            Map<Short, WorkingHours> workingHoursMap,
            Map<LocalDate, TimeOff> timeOffByDate,
            Set<LocalDate> recurringOffDayDates
    ) {
        // Get expected hours for this day of week (subtract break minutes)
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        short weekdayValue = (short) dayOfWeek.getValue();
        WorkingHours workingHours = workingHoursMap.get(weekdayValue);

        // Hours field already contains net hours (break subtracted when saved)
        Double expectedHours = 0.0;
        if (workingHours != null && workingHours.getIsWorkingDay() && workingHours.getHours() != null) {
            expectedHours = workingHours.getHours().doubleValue();
        }

        // Get time-off for this date
        TimeOff timeOff = timeOffByDate.get(date);
        
        // Check if this is a recurring off-day
        boolean isRecurringOffDay = recurringOffDayDates.contains(date);

        // Determine day type
        DayType dayType = determineDayType(date, timeOff, isRecurringOffDay);

        // Determine notes: priority is time entry notes > time-off notes > time-off type
        String notes = null;
        TimeOffType timeOffType = null;

        if (timeOff != null) {
            timeOffType = timeOff.getTimeOffType();
            // Use time-off notes if available, otherwise will use type name later
            notes = timeOff.getNotes();
        }

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
                    .dayType(dayType)
                    .notes(notes)
                    .timeOffType(timeOffType)
                    .build();
        }

        // Priority for notes: first non-null time entry notes > time-off notes
        if (notes == null) {
            notes = entries.stream()
                    .map(TimeEntry::getNotes)
                    .filter(Objects::nonNull)
                    .filter(n -> !n.isBlank())
                    .findFirst()
                    .orElse(null);
        }

        // Find first clock-in and last clock-out
        // Convert from UTC (stored in DB) to Europe/Berlin timezone
        LocalTime startTime = entries.stream()
                .map(TimeEntry::getClockIn)
                .min(Comparator.naturalOrder())
                .map(ldt -> ldt.atZone(ZoneId.of("UTC")).withZoneSameInstant(EUROPE_BERLIN).toLocalTime())
                .orElse(null);

        // Calculate total break minutes for the day
        int totalBreakMinutes = entries.stream()
                .mapToInt(entry -> entry.getBreakMinutes() != null ? entry.getBreakMinutes() : 0)
                .sum();

        // Check if any entry is still active (not clocked out)
        boolean hasActiveEntry = entries.stream().anyMatch(TimeEntry::isActive);

        LocalTime endTime = null;
        Double totalHours = null;
        Double overtime = null;

        if (!hasActiveEntry) {
            // All entries are clocked out, calculate totals
            // Convert from UTC (stored in DB) to Europe/Berlin timezone
            endTime = entries.stream()
                    .map(TimeEntry::getClockOut)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .map(ldt -> ldt.atZone(ZoneId.of("UTC")).withZoneSameInstant(EUROPE_BERLIN).toLocalTime())
                    .orElse(null);

            // Calculate total hours (already excludes breaks due to getHoursWorked() implementation)
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
                .breakMinutes(totalBreakMinutes)
                .totalHours(totalHours)
                .expectedHours(expectedHours)
                .overtime(overtime)
                .dayType(dayType)
                .notes(notes)
                .timeOffType(timeOffType)
                .build();
    }

    /**
     * Build a set of dates that match recurring off-day patterns (excluding exempted dates).
     */
    private Set<LocalDate> buildRecurringOffDayDates(List<RecurringOffDay> recurringOffDays, LocalDate startDate, LocalDate endDate) {
        Set<LocalDate> dates = new HashSet<>();
        
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            final LocalDate dateToCheck = currentDate;
            boolean isRecurringOffDay = recurringOffDays.stream()
                    .anyMatch(rod -> recurringOffDayEvaluator.appliesToDateWithExemptions(rod, dateToCheck));
            if (isRecurringOffDay) {
                dates.add(currentDate);
            }
            currentDate = currentDate.plusDays(1);
        }
        
        return dates;
    }

    /**
     * Determine the day type based on time-off, recurring off-day, and weekend status.
     */
    private DayType determineDayType(LocalDate date, TimeOff timeOff, boolean isRecurringOffDay) {
        if (timeOff != null) {
            TimeOffType timeOffType = timeOff.getTimeOffType();
            return switch (timeOffType) {
                case SICK -> DayType.SICK;
                case CHILD_SICK -> DayType.SICK;
                case VACATION -> DayType.VACATION;
                case PUBLIC_HOLIDAY -> DayType.PUBLIC_HOLIDAY;
                case EDUCATION -> DayType.REGULAR; // Education days are regular work days with notes
                default -> DayType.REGULAR;
            };
        }

        // Check if recurring off-day
        if (isRecurringOffDay) {
            return DayType.RECURRING_OFF_DAY;
        }

        // Check if weekend
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return DayType.WEEKEND;
        }

        return DayType.REGULAR;
    }
}
