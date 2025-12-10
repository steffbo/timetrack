package cc.remer.timetrack.adapter.security;

import cc.remer.timetrack.config.JwtProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.stream.Collectors;

/**
 * JWT token provider for generating and validating JWT tokens.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    /**
     * Generate an access token for the authenticated user.
     *
     * @param authentication the authentication object
     * @return JWT access token
     */
    public String generateAccessToken(Authentication authentication) {
        return generateToken(authentication, jwtProperties.getExpiration());
    }

    /**
     * Generate a refresh token for the authenticated user.
     *
     * @param authentication the authentication object
     * @return JWT refresh token
     */
    public String generateRefreshToken(Authentication authentication) {
        return generateToken(authentication, jwtProperties.getRefreshExpiration());
    }

    /**
     * Generate a JWT token with the specified expiration.
     *
     * @param authentication the authentication object
     * @param expirationMs the expiration time in milliseconds
     * @return JWT token
     */
    private String generateToken(Authentication authentication, Long expirationMs) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        return Jwts.builder()
                .subject(Long.toString(userPrincipal.getId()))
                .claim("email", userPrincipal.getUsername())
                .claim("authorities", authorities)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Extract user ID from JWT token.
     *
     * @param token the JWT token
     * @return user ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return Long.parseLong(claims.getSubject());
    }

    /**
     * Extract email from JWT token.
     *
     * @param token the JWT token
     * @return email address
     */
    public String getEmailFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("email", String.class);
    }

    /**
     * Validate JWT token.
     *
     * @param token the JWT token
     * @return true if token is valid
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.error("Invalid JWT signature: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.error("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("JWT token is unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Parse and validate JWT token.
     *
     * @param token the JWT token
     * @return claims from the token
     */
    private Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Get the signing key from the secret.
     *
     * @return signing key
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
