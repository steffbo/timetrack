package cc.remer.timetrack.domain.recurringoffday;

import cc.remer.timetrack.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Recurring off-day pattern for a user.
 * Represents regular exceptions to the working hours template.
 */
@Entity
@Table(name = "recurring_off_days")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringOffDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Recurrence pattern type.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_pattern", nullable = false, length = 50)
    private RecurrencePattern recurrencePattern;

    /**
     * Day of week (1=Monday, 7=Sunday).
     */
    @Column(nullable = false)
    private Short weekday;

    /**
     * For EVERY_NTH_WEEK: interval in weeks (e.g., 4 for every 4 weeks).
     */
    @Column(name = "week_interval")
    private Integer weekInterval;

    /**
     * For EVERY_NTH_WEEK: reference date to start counting from.
     */
    @Column(name = "reference_date")
    private LocalDate referenceDate;

    /**
     * For NTH_WEEKDAY_OF_MONTH: which occurrence in the month (1=first, 4=fourth, 5=last).
     */
    @Column(name = "week_of_month")
    private Short weekOfMonth;

    /**
     * When this rule becomes active.
     */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /**
     * Optional: when this rule expires.
     */
    @Column(name = "end_date")
    private LocalDate endDate;

    /**
     * Whether this rule is active.
     */
    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    /**
     * Optional description.
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Get the DayOfWeek enum from the weekday value.
     */
    public DayOfWeek getDayOfWeek() {
        return DayOfWeek.of(weekday);
    }

    /**
     * Set the weekday from DayOfWeek enum.
     */
    public void setDayOfWeek(DayOfWeek dayOfWeek) {
        this.weekday = (short) dayOfWeek.getValue();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RecurringOffDay that = (RecurringOffDay) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "RecurringOffDay{" +
                "id=" + id +
                ", userId=" + (user != null ? user.getId() : null) +
                ", recurrencePattern=" + recurrencePattern +
                ", weekday=" + weekday +
                ", isActive=" + isActive +
                '}';
    }
}
