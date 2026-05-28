package cc.remer.timetrack.usecase.authentication;

import cc.remer.timetrack.api.model.AuthResponse;
import cc.remer.timetrack.api.model.LoginRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use case for user login.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class Login {

    private final AuthenticationManager authenticationManager;
    private final AuthSessionService authSessionService;

    /**
     * Execute login use case.
     *
     * @param request the login request
     * @return authentication response with tokens
     */
    @Transactional
    public AuthResponse execute(LoginRequest request) {
        log.debug("Attempting login for user: {}", request.getEmail());

        // Authenticate user
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        log.info("User logged in successfully: {}", request.getEmail());

        return authSessionService.issueNewSession(authentication);
    }
}
