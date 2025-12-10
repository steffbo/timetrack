package cc.remer.timetrack.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * JWT configuration properties.
 */
@Configuration
@ConfigurationProperties(prefix = "app.jwt")
@Getter
@Setter
public class JwtProperties {

    /**
     * Secret key for signing JWT tokens.
     * Should be at least 256 bits (32 characters) for HS256 algorithm.
     */
    private String secret;

    /**
     * Access token expiration time in milliseconds.
     * Default: 86400000 (24 hours)
     */
    private Long expiration = 86400000L;

    /**
     * Refresh token expiration time in milliseconds.
     * Default: 604800000 (7 days)
     */
    private Long refreshExpiration = 604800000L;
}
