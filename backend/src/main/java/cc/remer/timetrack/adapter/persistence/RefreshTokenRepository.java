package cc.remer.timetrack.adapter.persistence;

import cc.remer.timetrack.domain.user.RefreshToken;
import cc.remer.timetrack.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for RefreshToken entity operations.
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Find a refresh token by its token string.
     *
     * @param token the token string
     * @return Optional containing the refresh token if found
     */
    Optional<RefreshToken> findByToken(String token);

    /**
     * Find all refresh tokens for a specific user.
     *
     * @param user the user
     * @return list of refresh tokens
     */
    List<RefreshToken> findByUser(User user);

    /**
     * Find all refresh tokens for a specific user ID.
     *
     * @param userId the user ID
     * @return list of refresh tokens
     */
    List<RefreshToken> findByUserId(Long userId);

    /**
     * Delete all refresh tokens for a specific user.
     *
     * @param user the user
     */
    void deleteByUser(User user);

    /**
     * Delete all refresh tokens for a specific user ID.
     *
     * @param userId the user ID
     */
    void deleteByUserId(Long userId);

    /**
     * Delete a specific refresh token by its token string.
     *
     * @param token the token string
     */
    void deleteByToken(String token);

    /**
     * Delete all expired refresh tokens.
     *
     * @param now the current timestamp
     * @return number of deleted tokens
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now")
    int deleteExpiredTokens(@Param("now") LocalDateTime now);

    /**
     * Check if a refresh token exists and is not expired.
     *
     * @param token the token string
     * @param now the current timestamp
     * @return true if token exists and is valid
     */
    @Query("SELECT CASE WHEN COUNT(rt) > 0 THEN true ELSE false END FROM RefreshToken rt " +
            "WHERE rt.token = :token AND rt.expiresAt > :now")
    boolean existsByTokenAndNotExpired(@Param("token") String token, @Param("now") LocalDateTime now);
}
