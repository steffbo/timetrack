package cc.remer.timetrack.usecase.recurringoffday;

import cc.remer.timetrack.adapter.persistence.RecurringOffDayExemptionRepository;
import cc.remer.timetrack.domain.recurringoffday.RecurringOffDay;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/**
 * Evaluates whether a recurring off-day applies to a specific date.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RecurringOffDayEvaluator {

    private final RecurringOffDayExemptionRepository exemptionRepository;

    /**
     * Check if a recurring off-day applies to a specific date.
     * This method checks the pattern match but does NOT consider exemptions.
     * Use {@link #appliesToDateWithExemptions} to also check for exemptions.
     *
     * @param recurringOffDay the recurring off-day rule
     * @param date the date to check
     * @return true if the rule's pattern matches this date
     */
    public boolean appliesToDate(RecurringOffDay recurringOffDay, LocalDate date) {
        // Check if rule is active
        if (!Boolean.TRUE.equals(recurringOffDay.getIsActive())) {
            return false;
        }

        // Check if date is within the rule's validity period
        if (date.isBefore(recurringOffDay.getStartDate())) {
            return false;
        }
        if (recurringOffDay.getEndDate() != null && date.isAfter(recurringOffDay.getEndDate())) {
            return false;
        }

        // Check if the weekday matches
        if (date.getDayOfWeek().getValue() != recurringOffDay.getWeekday()) {
            return false;
        }

        // Apply pattern-specific logic
        return switch (recurringOffDay.getRecurrencePattern()) {
            case EVERY_NTH_WEEK -> matchesEveryNthWeek(recurringOffDay, date);
            case NTH_WEEKDAY_OF_MONTH -> matchesNthWeekdayOfMonth(recurringOffDay, date);
        };
    }

    /**
     * Check if date matches the EVERY_NTH_WEEK pattern.
     */
    private boolean matchesEveryNthWeek(RecurringOffDay recurringOffDay, LocalDate date) {
        LocalDate referenceDate = recurringOffDay.getReferenceDate();
        int weekInterval = recurringOffDay.getWeekInterval();

        if (referenceDate == null || weekInterval < 1) {
            log.warn("Invalid EVERY_NTH_WEEK configuration for recurring off-day ID: {}",
                    recurringOffDay.getId());
            return false;
        }

        // Calculate weeks between reference date and the given date
        long weeksBetween = ChronoUnit.WEEKS.between(referenceDate, date);

        // Check if the date falls on the correct interval
        return weeksBetween >= 0 && weeksBetween % weekInterval == 0;
    }

    /**
     * Check if date matches the NTH_WEEKDAY_OF_MONTH pattern.
     */
    private boolean matchesNthWeekdayOfMonth(RecurringOffDay recurringOffDay, LocalDate date) {
        int weekOfMonth = recurringOffDay.getWeekOfMonth();

        if (weekOfMonth < 1 || weekOfMonth > 5) {
            log.warn("Invalid NTH_WEEKDAY_OF_MONTH configuration for recurring off-day ID: {}",
                    recurringOffDay.getId());
            return false;
        }

        // Find the first occurrence of this weekday in the month
        LocalDate firstOccurrence = date.with(TemporalAdjusters.firstInMonth(date.getDayOfWeek()));

        // Calculate which occurrence this date is (1st, 2nd, 3rd, 4th, or 5th)
        long weeksDiff = ChronoUnit.WEEKS.between(firstOccurrence, date);
        int occurrenceNumber = (int) weeksDiff + 1;

        // Special case: weekOfMonth = 5 means "last occurrence"
        if (weekOfMonth == 5) {
            // Check if this is the last occurrence of this weekday in the month
            LocalDate nextWeek = date.plusWeeks(1);
            return nextWeek.getMonth() != date.getMonth();
        }

        return occurrenceNumber == weekOfMonth;
    }

    /**
     * Check if a recurring off-day applies to a specific date, considering exemptions.
     * Returns false if the date has an exemption (making it a regular working day).
     *
     * @param recurringOffDay the recurring off-day rule
     * @param date the date to check
     * @return true if the rule applies to this date and is NOT exempted
     */
    public boolean appliesToDateWithExemptions(RecurringOffDay recurringOffDay, LocalDate date) {
        if (!appliesToDate(recurringOffDay, date)) {
            return false;
        }
        
        // Check if there's an exemption for this date
        return !isExempted(recurringOffDay, date);
    }

    /**
     * Check if a specific date is exempted from a recurring off-day rule.
     *
     * @param recurringOffDay the recurring off-day rule
     * @param date the date to check
     * @return true if the date is exempted
     */
    public boolean isExempted(RecurringOffDay recurringOffDay, LocalDate date) {
        if (recurringOffDay.getId() == null) {
            return false;
        }
        return exemptionRepository.existsByRecurringOffDayIdAndExemptionDate(
                recurringOffDay.getId(), date);
    }
}

