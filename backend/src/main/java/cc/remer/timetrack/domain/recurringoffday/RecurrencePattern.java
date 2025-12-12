package cc.remer.timetrack.domain.recurringoffday;

/**
 * Recurrence pattern types for recurring off-days.
 */
public enum RecurrencePattern {
    /**
     * Every Nth week (e.g., every 4 weeks).
     */
    EVERY_NTH_WEEK,

    /**
     * Nth weekday of the month (e.g., 4th Monday of every month).
     */
    NTH_WEEKDAY_OF_MONTH
}
