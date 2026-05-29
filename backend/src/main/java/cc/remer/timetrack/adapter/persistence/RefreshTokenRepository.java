package cc.remer.timetrack.adapter.persistence;

import cc.remer.timetrack.domain.user.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
     * Delete all refresh tokens for a specific user ID.
     *
     * @param userId the user ID
     */
    void deleteByUserId(Long userId);
}
