package cc.remer.timetrack.exception;

/**
 * Exception thrown when a recurring off-day is not found.
 */
public class RecurringOffDayNotFoundException extends RuntimeException {

    public RecurringOffDayNotFoundException(Long id) {
        super("Recurring off-day not found with ID: " + id);
    }
}
