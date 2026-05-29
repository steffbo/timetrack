package cc.remer.timetrack.domain.recurringoffday;

import cc.remer.timetrack.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents a warning when a work entry occurs on a recurring off-day.
 * These warnings persist to enable calendar highlighting even after acknowledgment.
 */
@Entity
@Table(name = "recurring_off_day_conflict_warnings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringOffDayConflictWarning {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "conflict_date", nullable = false)
    private LocalDate conflictDate;

    @Column(name = "time_entry_id")
    private Long timeEntryId;

    @Column(name = "recurring_off_day_id")
    private Long recurringOffDayId;

    @Column(name = "acknowledged", nullable = false)
    @Builder.Default
    private Boolean acknowledged = false;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void acknowledge(LocalDateTime now) {
        this.acknowledged = true;
        this.acknowledgedAt = now;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RecurringOffDayConflictWarning that = (RecurringOffDayConflictWarning) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "RecurringOffDayConflictWarning{" +
                "id=" + id +
                ", userId=" + (user != null ? user.getId() : null) +
                ", conflictDate=" + conflictDate +
                ", timeEntryId=" + timeEntryId +
                ", recurringOffDayId=" + recurringOffDayId +
                ", acknowledged=" + acknowledged +
                '}';
    }
}
