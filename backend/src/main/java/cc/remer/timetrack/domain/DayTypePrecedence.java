package cc.remer.timetrack.domain;

/**
 * Defines the precedence order for determining what type of day takes priority
 * when multiple conditions apply to the same date.
 *
 * Lower priority values take precedence over higher values.
 *
 * See /precedence-rules.md for detailed documentation and rationale.
 */
public enum DayTypePrecedence {

    /**
     * Priority 1: Not configured as a working day (weekend per working hours config).
     * Weekends are never counted as working days.
     */
    WEEKEND(1),

    /**
     * Priority 2: Public holiday for the user's state.
     * Public holidays always take precedence over everything else.
     */
    PUBLIC_HOLIDAY(2),

    /**
     * Priority 3: Sick leave.
     * Sick days override recurring off-days - if someone is sick on their
     * scheduled off-day, it counts as a sick day.
     */
    SICK(3),

    /**
     * Priority 4: Personal time off.
     * Personal days override recurring off-days, similar to sick days.
     */
    PERSONAL(4),

    /**
     * Priority 5: Recurring off-day (e.g., every 2nd Monday).
     * Regularly scheduled off-days that repeat on a pattern.
     */
    RECURRING_OFF_DAY(5),

    /**
     * Priority 6: Vacation/planned time off.
     * Vacation days are planned time off that count against vacation balance.
     */
    VACATION(6),

    /**
     * Priority 7: Regular work day with time entries.
     * Days where work was actually performed.
     */
    WORK(7),

    /**
     * Priority 8: No data available for this day.
     * Default when no information exists.
     */
    NO_ENTRY(8);

    private final int priority;

    DayTypePrecedence(int priority) {
        this.priority = priority;
    }

    /**
     * Get the numeric priority value.
     * Lower values indicate higher priority.
     *
     * @return the priority value
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Compare precedence with another day type.
     *
     * @param other the other day type to compare with
     * @return true if this type has higher priority (lower number) than the other
     */
    public boolean hasHigherPriorityThan(DayTypePrecedence other) {
        return this.priority < other.priority;
    }
}
