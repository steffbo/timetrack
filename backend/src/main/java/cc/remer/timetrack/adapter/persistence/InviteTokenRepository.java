package cc.remer.timetrack.adapter.persistence;

import cc.remer.timetrack.domain.user.InviteToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InviteTokenRepository extends JpaRepository<InviteToken, Long> {

    Optional<InviteToken> findByToken(String token);

    Optional<InviteToken> findByUserId(Long userId);
}
