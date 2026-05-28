package cc.remer.timetrack.usecase.authentication;

import cc.remer.timetrack.api.model.InviteInfoResponse;
import cc.remer.timetrack.domain.user.InviteToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GetInviteInfo {

    private final InviteTokenService inviteTokenService;

    @Transactional(readOnly = true)
    public InviteInfoResponse execute(String token) {
        InviteToken inviteToken = inviteTokenService.requireValidToken(token);

        var user = inviteToken.getUser();
        InviteInfoResponse response = new InviteInfoResponse();
        response.setEmail(user.getEmail());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setIsPasswordReset(!user.isPending());
        return response;
    }
}
