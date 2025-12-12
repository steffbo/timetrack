package cc.remer.timetrack.domain.user;

/**
 * German states (Bundesländer) for public holiday calculation.
 */
public enum GermanState {
    BERLIN("Berlin"),
    BRANDENBURG("Brandenburg");

    private final String displayName;

    GermanState(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
