package cc.remer.timetrack.exception;

/**
 * Exception thrown when a user attempts to access a resource they don't have permission for.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }

    public ForbiddenException(String message, Throwable cause) {
        super(message, cause);
    }
}
