package cc.remer.timetrack.domain.timeentry;

import cc.remer.timetrack.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Time entry domain entity for tracking work time.
 */
@Entity
@Table(name = "time_entries", indexes = {
        @Index(name = "idx_time_entries_user_date", columnList = "user_id,entry_date"),
        @Index(name = "idx_time_entries_clock_in", columnList = "clock_in"),
        @Index(name = "idx_time_entries_user_id", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "clock_in", nullable = false)
    private LocalDateTime clockIn;

    @Column(name = "clock_out")
    private LocalDateTime clockOut;

    @Column(name = "break_minutes", nullable = false)
    @Builder.Default
    private Integer breakMinutes = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 20)
    @Builder.Default
    private EntryType entryType = EntryType.WORK;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Check if the time entry is still active (not clocked out).
     *
     * @return true if clockOut is null
     */
    public boolean isActive() {
        return clockOut == null;
    }

    /**
     * Calculate the duration of this time entry.
     *
     * @return Duration between clock in and clock out, or null if not clocked out yet
     */
    public Duration getDuration() {
        if (clockOut == null) {
            return null;
        }
        return Duration.between(clockIn, clockOut);
    }

    /**
     * Get the duration in hours as a decimal value (excluding breaks).
     *
     * @return hours worked minus break time, or null if not clocked out yet
     */
    public Double getHoursWorked() {
        Duration duration = getDuration();
        if (duration == null) {
            return null;
        }
        double totalMinutes = duration.toMinutes();
        double workMinutes = totalMinutes - (breakMinutes != null ? breakMinutes : 0);
        return workMinutes / 60.0;
    }

    /**
     * Check if this is a regular work entry.
     * Always returns true since only WORK entries are supported.
     *
     * @return true (always, since only WORK type exists)
     */
    public boolean isWorkEntry() {
        return entryType == EntryType.WORK;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TimeEntry timeEntry = (TimeEntry) o;
        return Objects.equals(id, timeEntry.id) &&
                Objects.equals(user.getId(), timeEntry.user.getId()) &&
                Objects.equals(clockIn, timeEntry.clockIn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, user != null ? user.getId() : null, clockIn);
    }

    @Override
    public String toString() {
        return "TimeEntry{" +
                "id=" + id +
                ", userId=" + (user != null ? user.getId() : null) +
                ", entryDate=" + entryDate +
                ", clockIn=" + clockIn +
                ", clockOut=" + clockOut +
                ", entryType=" + entryType +
                ", isActive=" + isActive() +
                '}';
    }
}
