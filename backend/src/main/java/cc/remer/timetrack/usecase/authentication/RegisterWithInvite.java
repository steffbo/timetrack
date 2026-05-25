package cc.remer.timetrack.usecase.authentication;

import cc.remer.timetrack.adapter.persistence.InviteTokenRepository;
import cc.remer.timetrack.adapter.persistence.RefreshTokenRepository;
import cc.remer.timetrack.adapter.persistence.UserRepository;
import cc.remer.timetrack.adapter.security.JwtTokenProvider;
import cc.remer.timetrack.adapter.security.UserPrincipal;
import cc.remer.timetrack.api.model.AuthResponse;
import cc.remer.timetrack.api.model.RegisterWithInviteRequest;
import cc.remer.timetrack.api.model.UserResponse;
import cc.remer.timetrack.config.JwtProperties;
import cc.remer.timetrack.domain.user.InviteToken;
import cc.remer.timetrack.domain.user.RefreshToken;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.exception.InviteTokenException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegisterWithInvite {

    private final InviteTokenRepository inviteTokenRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider tokenProvider;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse execute(RegisterWithInviteRequest request) {
        InviteToken inviteToken = inviteTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new InviteTokenException(
                        InviteTokenException.Reason.NOT_FOUND, "Einladungslink nicht gefunden"));

        if (inviteToken.isExpired()) {
            throw new InviteTokenException(
                    InviteTokenException.Reason.EXPIRED, "Einladungslink ist abgelaufen");
        }

        User user = inviteToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setActive(true);
        userRepository.save(user);

        // Token is one-time use
        inviteTokenRepository.delete(inviteToken);

        // Issue JWT
        UserPrincipal principal = UserPrincipal.builder()
                .id(user.getId())
                .email(user.getEmail())
                .password(user.getPasswordHash())
                .role(user.getRole())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
                .enabled(user.getActive())
                .build();

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());

        String accessToken = tokenProvider.generateAccessToken(authentication);
        String refreshToken = tokenProvider.generateRefreshToken(authentication);

        RefreshToken rt = RefreshToken.builder()
                .user(User.builder().id(user.getId()).build())
                .token(refreshToken)
                .expiresAt(LocalDateTime.now().plusSeconds(jwtProperties.getRefreshExpiration() / 1000))
                .build();
        refreshTokenRepository.save(rt);

        log.info("User {} completed registration via invite", user.getId());

        return buildAuthResponse(accessToken, refreshToken, principal);
    }

    private AuthResponse buildAuthResponse(String accessToken, String refreshToken, UserPrincipal principal) {
        AuthResponse response = new AuthResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setTokenType("Bearer");
        response.setExpiresIn(jwtProperties.getExpiration() / 1000);

        UserResponse userResponse = new UserResponse();
        userResponse.setId(principal.getId());
        userResponse.setEmail(principal.getEmail());
        userResponse.setRole(UserResponse.RoleEnum.fromValue(
                principal.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "")));
        userResponse.setActive(principal.isEnabled());
        response.setUser(userResponse);

        return response;
    }
}
