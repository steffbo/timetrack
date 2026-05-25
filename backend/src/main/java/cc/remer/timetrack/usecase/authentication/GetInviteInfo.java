package cc.remer.timetrack.usecase.authentication;

import cc.remer.timetrack.adapter.persistence.InviteTokenRepository;
import cc.remer.timetrack.api.model.InviteInfoResponse;
import cc.remer.timetrack.domain.user.InviteToken;
import cc.remer.timetrack.exception.InviteTokenException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GetInviteInfo {

    private final InviteTokenRepository inviteTokenRepository;

    @Transactional(readOnly = true)
    public InviteInfoResponse execute(String token) {
        InviteToken inviteToken = inviteTokenRepository.findByToken(token)
                .orElseThrow(() -> new InviteTokenException(
                        InviteTokenException.Reason.NOT_FOUND, "Einladungslink nicht gefunden"));

        if (inviteToken.isExpired()) {
            throw new InviteTokenException(
                    InviteTokenException.Reason.EXPIRED, "Einladungslink ist abgelaufen");
        }

        var user = inviteToken.getUser();
        InviteInfoResponse response = new InviteInfoResponse();
        response.setEmail(user.getEmail());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setIsPasswordReset(!user.isPending());
        return response;
    }
}
