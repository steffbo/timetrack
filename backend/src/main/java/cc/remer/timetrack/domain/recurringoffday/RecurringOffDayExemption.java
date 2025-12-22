package cc.remer.timetrack.domain.recurringoffday;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Exemption for a recurring off-day pattern.
 * When an exemption exists for a specific date, the recurring off-day rule
 * does not apply, making it a regular working day.
 */
@Entity
@Table(name = "recurring_off_day_exemptions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_exemption_date_per_rule",
                columnNames = {"recurring_off_day_id", "exemption_date"}
        ))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringOffDayExemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recurring_off_day_id", nullable = false)
    private RecurringOffDay recurringOffDay;

    /**
     * The date that is exempted from the recurring off-day pattern.
     * On this date, the recurring off-day rule will not apply.
     */
    @Column(name = "exemption_date", nullable = false)
    private LocalDate exemptionDate;

    /**
     * Optional reason for the exemption.
     */
    @Column(columnDefinition = "TEXT")
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RecurringOffDayExemption that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "RecurringOffDayExemption{" +
                "id=" + id +
                ", recurringOffDayId=" + (recurringOffDay != null ? recurringOffDay.getId() : null) +
                ", exemptionDate=" + exemptionDate +
                ", reason='" + reason + '\'' +
                '}';
    }
}
