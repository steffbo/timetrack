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
     * Get the duration in hours as a decimal value.
     *
     * @return hours worked, or null if not clocked out yet
     */
    public Double getHoursWorked() {
        Duration duration = getDuration();
        if (duration == null) {
            return null;
        }
        return duration.toMinutes() / 60.0;
    }

    /**
     * Check if this is a regular work entry.
     *
     * @return true if entry type is WORK
     */
    public boolean isWorkEntry() {
        return entryType == EntryType.WORK;
    }

    /**
     * Check if this is a sick leave entry.
     *
     * @return true if entry type is SICK
     */
    public boolean isSickEntry() {
        return entryType == EntryType.SICK;
    }

    /**
     * Check if this is a PTO entry.
     *
     * @return true if entry type is PTO
     */
    public boolean isPtoEntry() {
        return entryType == EntryType.PTO;
    }

    /**
     * Check if this is an event entry.
     *
     * @return true if entry type is EVENT
     */
    public boolean isEventEntry() {
        return entryType == EntryType.EVENT;
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
