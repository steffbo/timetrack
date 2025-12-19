package cc.remer.timetrack.domain.workinghours;

import cc.remer.timetrack.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Working hours configuration per weekday for a user.
 */
@Entity
@Table(name = "working_hours", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "weekday"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkingHours {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Day of week (1=Monday, 7=Sunday).
     */
    @Column(nullable = false)
    private Short weekday;

    /**
     * Target hours for this weekday.
     */
    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal hours;

    /**
     * Whether this is a working day.
     */
    @Column(name = "is_working_day", nullable = false)
    private Boolean isWorkingDay;

    /**
     * Optional start time for this working day.
     * When set, hours are calculated from start_time to end_time.
     */
    @Column(name = "start_time")
    private LocalTime startTime;

    /**
     * Optional end time for this working day.
     * When set, hours are calculated from start_time to end_time.
     */
    @Column(name = "end_time")
    private LocalTime endTime;

    /**
     * Break duration in minutes for this working day.
     * Defaults to 0 (no break).
     */
    @Column(name = "break_minutes", nullable = false)
    @Builder.Default
    private Integer breakMinutes = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Get the DayOfWeek enum from the weekday value.
     *
     * @return DayOfWeek
     */
    public DayOfWeek getDayOfWeek() {
        return DayOfWeek.of(weekday);
    }

    /**
     * Set the weekday from DayOfWeek enum.
     *
     * @param dayOfWeek the day of week
     */
    public void setDayOfWeek(DayOfWeek dayOfWeek) {
        this.weekday = (short) dayOfWeek.getValue();
    }

    /**
     * Check if this is a non-working day (weekend or holiday).
     *
     * @return true if not a working day
     */
    public boolean isNonWorkingDay() {
        return !isWorkingDay;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkingHours that = (WorkingHours) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(user.getId(), that.user.getId()) &&
                Objects.equals(weekday, that.weekday);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, user != null ? user.getId() : null, weekday);
    }

    @Override
    public String toString() {
        return "WorkingHours{" +
                "id=" + id +
                ", userId=" + (user != null ? user.getId() : null) +
                ", weekday=" + weekday +
                ", hours=" + hours +
                ", isWorkingDay=" + isWorkingDay +
                '}';
    }
}
