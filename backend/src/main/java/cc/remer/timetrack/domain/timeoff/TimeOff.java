package cc.remer.timetrack.domain.timeoff;

import cc.remer.timetrack.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Time off entry (vacation, sick days, etc.) for a user.
 */
@Entity
@Table(name = "time_off")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeOff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Start date of the time off.
     */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /**
     * End date of the time off (inclusive).
     */
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /**
     * Type of time off.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "time_off_type", nullable = false, length = 20)
    private TimeOffType timeOffType;

    /**
     * Optional: override hours per day.
     * If null, use expected hours from working_hours for the weekday.
     */
    @Column(name = "hours_per_day", precision = 4, scale = 2)
    private BigDecimal hoursPerDay;

    /**
     * Optional notes.
     */
    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TimeOff timeOff = (TimeOff) o;
        return Objects.equals(id, timeOff.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "TimeOff{" +
                "id=" + id +
                ", userId=" + (user != null ? user.getId() : null) +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                ", timeOffType=" + timeOffType +
                '}';
    }
}
