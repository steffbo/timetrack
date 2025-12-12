package cc.remer.timetrack.adapter.persistence;

import cc.remer.timetrack.domain.vacationbalance.VacationBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for VacationBalance entities.
 */
@Repository
public interface VacationBalanceRepository extends JpaRepository<VacationBalance, Long> {

    /**
     * Find vacation balance for a user and year.
     *
     * @param userId the user ID
     * @param year the year
     * @return optional vacation balance
     */
    Optional<VacationBalance> findByUserIdAndYear(Long userId, Integer year);

    /**
     * Check if vacation balance exists for a user and year.
     *
     * @param userId the user ID
     * @param year the year
     * @return true if exists
     */
    boolean existsByUserIdAndYear(Long userId, Integer year);
}
