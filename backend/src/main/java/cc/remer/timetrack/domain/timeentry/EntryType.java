package cc.remer.timetrack.domain.timeentry;

/**
 * Type of time entry.
 * Only WORK is supported - absences are tracked via TimeOff entity.
 */
public enum EntryType {
    WORK
}
