package cc.remer.timetrack.usecase.user;

import cc.remer.timetrack.adapter.persistence.InviteTokenRepository;
import cc.remer.timetrack.adapter.persistence.UserRepository;
import cc.remer.timetrack.api.model.InviteTokenResponse;
import cc.remer.timetrack.domain.user.InviteToken;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.exception.UserNotFoundException;
import cc.remer.timetrack.util.MapperUtils;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GenerateInviteToken {

    private static final int EXPIRY_DAYS = 30;

    private final InviteTokenRepository inviteTokenRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    @Transactional
    public InviteTokenResponse execute(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Benutzer nicht gefunden: " + userId));

        String token = issueToken(user);
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(EXPIRY_DAYS);

        InviteTokenResponse response = new InviteTokenResponse();
        response.setInviteUrl(buildUrl(token));
        response.setExpiresAt(MapperUtils.toOffsetDateTime(expiresAt));
        return response;
    }

    /** Called by CreateUser — returns just the URL without a full response object. */
    @Transactional
    public String generateUrl(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Benutzer nicht gefunden: " + userId));
        return buildUrl(issueToken(user));
    }

    private String issueToken(User user) {
        // Invalidate any existing invite for this user, flush so the UNIQUE constraint doesn't
        // conflict with the new insert within the same transaction.
        inviteTokenRepository.findByUserId(user.getId())
                .ifPresent(existing -> {
                    inviteTokenRepository.delete(existing);
                    entityManager.flush();
                });

        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(EXPIRY_DAYS);

        InviteToken inviteToken = InviteToken.builder()
                .user(user)
                .token(token)
                .expiresAt(expiresAt)
                .build();
        inviteTokenRepository.save(inviteToken);

        log.info("Invite token issued for user {}, expires {}", user.getId(), expiresAt);
        return token;
    }

    private String buildUrl(String token) {
        return frontendBaseUrl + "/register/" + token;
    }
}
