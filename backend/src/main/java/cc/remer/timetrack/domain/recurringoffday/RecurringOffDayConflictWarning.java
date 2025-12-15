package cc.remer.timetrack.domain.recurringoffday;

import cc.remer.timetrack.domain.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents a warning when a work entry occurs on a recurring off-day.
 * These warnings persist to enable calendar highlighting even after acknowledgment.
 */
@Entity
@Table(name = "recurring_off_day_conflict_warnings")
@Data
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

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Mark this warning as acknowledged by the user.
     */
    public void acknowledge() {
        this.acknowledged = true;
        this.acknowledgedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}
