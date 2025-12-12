package cc.remer.timetrack.usecase.timeentry.model;

/**
 * Status enum for daily summary comparing actual vs expected hours.
 */
public enum DailySummaryStatus {
    /** No time entries recorded for this day */
    NO_ENTRY,

    /** Actual hours are below expected hours */
    BELOW_EXPECTED,

    /** Actual hours match expected hours (within tolerance) */
    MATCHED,

    /** Actual hours are above expected hours (overtime) */
    ABOVE_EXPECTED
}
