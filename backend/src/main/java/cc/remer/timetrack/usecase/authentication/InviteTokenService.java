package cc.remer.timetrack.usecase.authentication;

import cc.remer.timetrack.adapter.persistence.InviteTokenRepository;
import cc.remer.timetrack.domain.user.InviteToken;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.exception.InviteTokenException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InviteTokenService {

    private static final int EXPIRY_DAYS = 30;

    private final InviteTokenRepository inviteTokenRepository;
    private final EntityManager entityManager;

    public InviteToken requireValidToken(String token) {
        InviteToken inviteToken = inviteTokenRepository.findByToken(token)
                .orElseThrow(() -> new InviteTokenException(
                        InviteTokenException.Reason.NOT_FOUND, "Einladungslink nicht gefunden"));

        if (inviteToken.isExpired()) {
            throw new InviteTokenException(
                    InviteTokenException.Reason.EXPIRED, "Einladungslink ist abgelaufen");
        }

        return inviteToken;
    }

    public InviteToken issueToken(User user) {
        // Flush after deletion so the one-token-per-user constraint cannot conflict with the insert.
        inviteTokenRepository.findByUserId(user.getId())
                .ifPresent(existing -> {
                    inviteTokenRepository.delete(existing);
                    entityManager.flush();
                });

        InviteToken inviteToken = InviteToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiresAt(LocalDateTime.now().plusDays(EXPIRY_DAYS))
                .build();

        InviteToken savedToken = inviteTokenRepository.save(inviteToken);
        log.info("Invite token issued for user {}, expires {}", user.getId(), savedToken.getExpiresAt());
        return savedToken;
    }

    public void consumeToken(InviteToken inviteToken) {
        inviteTokenRepository.delete(inviteToken);
    }
}
