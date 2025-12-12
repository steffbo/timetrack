package cc.remer.timetrack.exception;

/**
 * Exception thrown when a time-off entry is not found.
 */
public class TimeOffNotFoundException extends RuntimeException {

    public TimeOffNotFoundException(Long id) {
        super("Time-off entry not found with ID: " + id);
    }
}
