package cc.remer.timetrack.adapter.web;

import cc.remer.timetrack.api.AuthenticationApi;
import cc.remer.timetrack.api.model.AuthResponse;
import cc.remer.timetrack.api.model.LoginRequest;
import cc.remer.timetrack.api.model.RefreshTokenRequest;
import cc.remer.timetrack.usecase.authentication.Login;
import cc.remer.timetrack.usecase.authentication.Logout;
import cc.remer.timetrack.usecase.authentication.RefreshAccessToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for authentication endpoints.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class AuthenticationController implements AuthenticationApi {

    private final Login loginUseCase;
    private final Logout logoutUseCase;
    private final RefreshAccessToken refreshAccessTokenUseCase;

    @Override
    public ResponseEntity<AuthResponse> login(LoginRequest loginRequest) {
        log.debug("Login request received for email: {}", loginRequest.getEmail());
        AuthResponse response = loginUseCase.execute(loginRequest);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<AuthResponse> refreshToken(RefreshTokenRequest refreshTokenRequest) {
        log.debug("Refresh token request received");
        AuthResponse response = refreshAccessTokenUseCase.execute(refreshTokenRequest);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> logout() {
        log.debug("Logout request received");
        Authentication authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        logoutUseCase.execute(authentication);
        return ResponseEntity.noContent().build();
    }
}
