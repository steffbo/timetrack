package cc.remer.timetrack.usecase.authentication;

import cc.remer.timetrack.adapter.persistence.UserRepository;
import cc.remer.timetrack.adapter.security.UserPrincipal;
import cc.remer.timetrack.api.model.AuthResponse;
import cc.remer.timetrack.api.model.RegisterWithInviteRequest;
import cc.remer.timetrack.domain.user.InviteToken;
import cc.remer.timetrack.domain.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegisterWithInvite {

    private final InviteTokenService inviteTokenService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionService authSessionService;

    @Transactional
    public AuthResponse execute(RegisterWithInviteRequest request) {
        InviteToken inviteToken = inviteTokenService.requireValidToken(request.getToken());

        User user = inviteToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setActive(true);
        userRepository.save(user);

        // Token is one-time use
        inviteTokenService.consumeToken(inviteToken);

        log.info("User {} completed registration via invite", user.getId());

        return authSessionService.issueNewSession(UserPrincipal.create(user));
    }
}
