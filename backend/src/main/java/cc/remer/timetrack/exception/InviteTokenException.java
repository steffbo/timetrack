package cc.remer.timetrack.exception;

public class InviteTokenException extends RuntimeException {

    public enum Reason { NOT_FOUND, EXPIRED }

    private final Reason reason;

    public InviteTokenException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
