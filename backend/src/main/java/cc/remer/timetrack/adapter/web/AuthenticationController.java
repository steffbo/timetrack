package cc.remer.timetrack.adapter.web;

import cc.remer.timetrack.api.AuthenticationApi;
import cc.remer.timetrack.api.model.AuthResponse;
import cc.remer.timetrack.api.model.InviteInfoResponse;
import cc.remer.timetrack.api.model.LoginRequest;
import cc.remer.timetrack.api.model.RefreshTokenRequest;
import cc.remer.timetrack.api.model.RegisterWithInviteRequest;
import cc.remer.timetrack.usecase.authentication.GetInviteInfo;
import cc.remer.timetrack.usecase.authentication.Login;
import cc.remer.timetrack.usecase.authentication.Logout;
import cc.remer.timetrack.usecase.authentication.RefreshAccessToken;
import cc.remer.timetrack.usecase.authentication.RegisterWithInvite;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class AuthenticationController implements AuthenticationApi {

    private final Login loginUseCase;
    private final Logout logoutUseCase;
    private final RefreshAccessToken refreshAccessTokenUseCase;
    private final GetInviteInfo getInviteInfoUseCase;
    private final RegisterWithInvite registerWithInviteUseCase;

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

    @Override
    public ResponseEntity<InviteInfoResponse> getInviteInfo(String token) {
        log.debug("Get invite info for token");
        InviteInfoResponse response = getInviteInfoUseCase.execute(token);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<AuthResponse> registerWithInvite(RegisterWithInviteRequest registerWithInviteRequest) {
        log.debug("Register with invite token");
        AuthResponse response = registerWithInviteUseCase.execute(registerWithInviteRequest);
        return ResponseEntity.ok(response);
    }
}
