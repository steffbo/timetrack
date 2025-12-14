package cc.remer.timetrack.usecase.vacationbalance;

import cc.remer.timetrack.adapter.persistence.RecurringOffDayRepository;
import cc.remer.timetrack.adapter.persistence.TimeOffRepository;
import cc.remer.timetrack.adapter.persistence.WorkingHoursRepository;
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
                .toList();

        int workingDays = 0;
        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            if (isWorkingDay(currentDate, workingHoursList, recurringOffDays, otherTimeOffEntries, userState)) {
                workingDays++;
            }
            currentDate = currentDate.plusDays(1);
        }

        log.debug("Calculated {} working days for user {} between {} and {}",
                workingDays, userId, startDate, endDate);

        return workingDays;
    }

    /**
     * Check if a specific date is a working day for the user.
     *
     * @param date the date to check
     * @param workingHoursList the user's working hours configuration
     * @param recurringOffDays the user's recurring off-days
     * @param otherTimeOffEntries other time-off entries (sick, personal, etc.)
     * @param userState the user's German state
     * @return true if it's a working day
     */
    private boolean isWorkingDay(LocalDate date, List<WorkingHours> workingHoursList,
                                  List<RecurringOffDay> recurringOffDays,
                                  List<TimeOff> otherTimeOffEntries,
                                  GermanState userState) {
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

        // Check if it's a recurring off-day
        for (RecurringOffDay rod : recurringOffDays) {
            if (recurringOffDayEvaluator.appliesToDate(rod, date)) {
                return false;
            }
        }

        // Check if there's another time-off entry (sick, personal, etc.) covering this date
        for (TimeOff timeOff : otherTimeOffEntries) {
            if (!date.isBefore(timeOff.getStartDate()) && !date.isAfter(timeOff.getEndDate())) {
                return false;
            }
        }

        return true;
    }
}
