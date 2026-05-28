package cc.remer.timetrack.usecase.user;

import cc.remer.timetrack.adapter.persistence.UserRepository;
import cc.remer.timetrack.api.model.InviteTokenResponse;
import cc.remer.timetrack.domain.user.InviteToken;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.exception.UserNotFoundException;
import cc.remer.timetrack.usecase.authentication.InviteTokenService;
import cc.remer.timetrack.util.MapperUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GenerateInviteToken {

    private final UserRepository userRepository;
    private final InviteTokenService inviteTokenService;

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    @Transactional
    public InviteTokenResponse execute(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Benutzer nicht gefunden: " + userId));

        InviteToken inviteToken = inviteTokenService.issueToken(user);

        InviteTokenResponse response = new InviteTokenResponse();
        response.setInviteUrl(buildUrl(inviteToken.getToken()));
        response.setExpiresAt(MapperUtils.toOffsetDateTime(inviteToken.getExpiresAt()));
        return response;
    }

    /** Called by CreateUser — returns just the URL without a full response object. */
    @Transactional
    public String generateUrl(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Benutzer nicht gefunden: " + userId));
        InviteToken inviteToken = inviteTokenService.issueToken(user);
        return buildUrl(inviteToken.getToken());
    }

    private String buildUrl(String token) {
        return frontendBaseUrl + "/register/" + token;
    }
}
