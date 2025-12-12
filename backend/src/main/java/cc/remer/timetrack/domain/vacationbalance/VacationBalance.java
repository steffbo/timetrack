package cc.remer.timetrack.domain.vacationbalance;

import cc.remer.timetrack.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Vacation balance tracking per user per year.
 * Tracks in days (not hours).
 */
@Entity
@Table(name = "vacation_balance",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "year"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Year this balance applies to.
     */
    @Column(nullable = false)
    private Integer year;

    /**
     * Annual allowance in days (default 30 days).
     */
    @Column(name = "annual_allowance_days", nullable = false, precision = 5, scale = 1)
    private BigDecimal annualAllowanceDays;

    /**
     * Days carried over from previous year.
     */
    @Builder.Default
    @Column(name = "carried_over_days", precision = 5, scale = 1)
    private BigDecimal carriedOverDays = BigDecimal.ZERO;

    /**
     * Manual adjustments (bonus days, etc.).
     */
    @Builder.Default
    @Column(name = "adjustment_days", precision = 5, scale = 1)
    private BigDecimal adjustmentDays = BigDecimal.ZERO;

    /**
     * Days used (calculated from time_off entries).
     */
    @Builder.Default
    @Column(name = "used_days", precision = 5, scale = 1)
    private BigDecimal usedDays = BigDecimal.ZERO;

    /**
     * Remaining days (calculated field).
     * = annualAllowanceDays + carriedOverDays + adjustmentDays - usedDays
     */
    @Column(name = "remaining_days", precision = 5, scale = 1)
    private BigDecimal remainingDays;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Calculate remaining days.
     */
    public void calculateRemainingDays() {
        this.remainingDays = annualAllowanceDays
                .add(carriedOverDays != null ? carriedOverDays : BigDecimal.ZERO)
                .add(adjustmentDays != null ? adjustmentDays : BigDecimal.ZERO)
                .subtract(usedDays != null ? usedDays : BigDecimal.ZERO);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VacationBalance that = (VacationBalance) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "VacationBalance{" +
                "id=" + id +
                ", userId=" + (user != null ? user.getId() : null) +
                ", year=" + year +
                ", annualAllowanceDays=" + annualAllowanceDays +
                ", usedDays=" + usedDays +
                ", remainingDays=" + remainingDays +
                '}';
    }
}
