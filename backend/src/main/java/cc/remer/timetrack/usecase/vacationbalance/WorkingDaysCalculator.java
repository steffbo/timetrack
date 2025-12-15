package cc.remer.timetrack.usecase.vacationbalance;

import cc.remer.timetrack.adapter.persistence.RecurringOffDayRepository;
import cc.remer.timetrack.adapter.persistence.TimeOffRepository;
import cc.remer.timetrack.adapter.persistence.WorkingHoursRepository;
import cc.remer.timetrack.domain.DayTypePrecedence;
import cc.remer.timetrack.domain.publicholiday.GermanPublicHolidays;
import cc.remer.timetrack.domain.recurringoffday.RecurringOffDay;
import cc.remer.timetrack.domain.timeoff.TimeOff;
import cc.remer.timetrack.domain.timeoff.TimeOffType;
import cc.remer.timetrack.domain.user.GermanState;
import cc.remer.timetrack.domain.workinghours.WorkingHours;
import cc.remer.timetrack.usecase.recurringoffday.RecurringOffDayEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Service to calculate working days between two dates.
 * Excludes weekends, public holidays, and recurring off-days.
 *
 * Precedence rules follow {@link DayTypePrecedence}.
 * See /precedence-rules.md for detailed documentation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkingDaysCalculator {

    private final WorkingHoursRepository workingHoursRepository;
    private final RecurringOffDayRepository recurringOffDayRepository;
    private final TimeOffRepository timeOffRepository;
    private final GermanPublicHolidays germanPublicHolidays;
    private final RecurringOffDayEvaluator recurringOffDayEvaluator;

    /**
     * Calculate the number of working days between start and end date (inclusive).
     * Excludes:
     * - Non-working days (weekends) according to user's working hours
     * - Public holidays for the user's state
     * - Recurring off-days
     * - Other time-off entries (sick, personal, etc.) that take precedence over vacation
     *
     * @param userId the user ID
     * @param userState the user's German state
     * @param startDate the start date (inclusive)
     * @param endDate the end date (inclusive)
     * @return the number of working days
     */
    public int calculateWorkingDays(Long userId, GermanState userState, LocalDate startDate, LocalDate endDate) {
        return calculateWorkingDays(userId, userState, startDate, endDate, null);
    }

    /**
     * Calculate the number of working days between start and end date (inclusive).
     * Excludes:
     * - Non-working days (weekends) according to user's working hours
     * - Public holidays for the user's state
     * - Recurring off-days
     * - Other time-off entries (sick, personal, etc.) that take precedence over vacation
     *
     * @param userId the user ID
     * @param userState the user's German state
     * @param startDate the start date (inclusive)
     * @param endDate the end date (inclusive)
     * @param excludeTimeOffId optional ID of a time-off entry to exclude from the calculation
     * @return the number of working days
     */
    public int calculateWorkingDays(Long userId, GermanState userState, LocalDate startDate, LocalDate endDate, Long excludeTimeOffId) {
        if (startDate.isAfter(endDate)) {
            return 0;
        }

        // Load user's working hours configuration
        List<WorkingHours> workingHoursList = workingHoursRepository.findByUserId(userId);

        // Load user's recurring off-days
        List<RecurringOffDay> recurringOffDays = recurringOffDayRepository.findByUserId(userId);

        // Load other time-off entries (excluding vacation) that overlap with this date range
        List<TimeOff> otherTimeOffEntries = timeOffRepository.findByUserIdAndDateRange(userId, startDate, endDate)
                .stream()
                .filter(timeOff -> timeOff.getTimeOffType() != TimeOffType.VACATION)
                .filter(timeOff -> excludeTimeOffId == null || !timeOff.getId().equals(excludeTimeOffId)) // Exclude current entry
                .toList();

        // If we're calculating for a sick/personal day (excludeTimeOffId is set),
        // don't check recurring off-days - sick days take precedence
        boolean checkRecurringOffDays = (excludeTimeOffId == null);

        int workingDays = 0;
        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            if (isWorkingDay(currentDate, workingHoursList, recurringOffDays, otherTimeOffEntries, userState, checkRecurringOffDays)) {
                workingDays++;
            }
            currentDate = currentDate.plusDays(1);
        }

        log.debug("Calculated {} working days for user {} between {} and {} (excluding timeOff ID: {}, checkRecurringOffDays: {})",
                workingDays, userId, startDate, endDate, excludeTimeOffId, checkRecurringOffDays);

        return workingDays;
    }

    /**
     * Check if a specific date is a working day for the user.
     *
     * Priority order for determining if a day should NOT count as a working day:
     * 1. Not configured as a working day (weekend)
     * 2. Public holiday
     * 3. Time-off entries (sick, personal) - these take precedence over recurring off-days
     * 4. Recurring off-days (only checked when checkRecurringOffDays is true)
     *
     * @param date the date to check
     * @param workingHoursList the user's working hours configuration
     * @param recurringOffDays the user's recurring off-days
     * @param otherTimeOffEntries other time-off entries (sick, personal, etc.)
     * @param userState the user's German state
     * @param checkRecurringOffDays whether to check recurring off-days (false when calculating sick/personal days)
     * @return true if it's a working day
     */
    private boolean isWorkingDay(LocalDate date, List<WorkingHours> workingHoursList,
                                  List<RecurringOffDay> recurringOffDays,
                                  List<TimeOff> otherTimeOffEntries,
                                  GermanState userState,
                                  boolean checkRecurringOffDays) {
        // Get weekday (1=Monday, 7=Sunday)
        int weekday = date.getDayOfWeek().getValue();

        // Check if it's configured as a working day
        WorkingHours workingHours = workingHoursList.stream()
                .filter(wh -> wh.getWeekday() == weekday)
                .findFirst()
                .orElse(null);

        // If no configuration or explicitly non-working, it's not a working day
        if (workingHours == null || !workingHours.getIsWorkingDay()) {
            return false;
        }

        // Check if it's a public holiday
        if (germanPublicHolidays.isPublicHoliday(date, userState)) {
            return false;
        }

        // Check if there's another time-off entry (sick, personal, etc.) covering this date
        // Sick/personal days take precedence over recurring off-days
        for (TimeOff timeOff : otherTimeOffEntries) {
            if (!date.isBefore(timeOff.getStartDate()) && !date.isAfter(timeOff.getEndDate())) {
                return false;
            }
        }

        // Check if it's a recurring off-day (only if checkRecurringOffDays is true)
        // When calculating sick/personal days, we skip this check so they take precedence
        if (checkRecurringOffDays) {
            for (RecurringOffDay rod : recurringOffDays) {
                if (recurringOffDayEvaluator.appliesToDate(rod, date)) {
                    return false;
                }
            }
        }

        return true;
    }
}
