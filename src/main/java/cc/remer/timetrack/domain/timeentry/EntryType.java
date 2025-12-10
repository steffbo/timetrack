package cc.remer.timetrack.domain.timeentry;

/**
 * Time entry type enumeration.
 */
public enum EntryType {
    /**
     * Regular work time entry.
     */
    WORK,

    /**
     * Sick leave time entry.
     */
    SICK,

    /**
     * Paid time off (vacation) entry.
     */
    PTO,

    /**
     * Special event or company event entry.
     */
    EVENT
}
