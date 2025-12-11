package cc.remer.timetrack.adapter.persistence;

import cc.remer.timetrack.domain.user.RefreshToken;
import cc.remer.timetrack.domain.user.Role;
import cc.remer.timetrack.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for RefreshTokenRepository.
 * Tests the fix for the token column size issue (VARCHAR(255) -> TEXT).
 */
@DisplayName("RefreshToken Repository Integration Tests")
class RefreshTokenRepositoryTest extends RepositoryTestBase {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;
    private String longJwtToken;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        testUser = User.builder()
                .email("test@example.com")
                .passwordHash("$2y$10$hashedPassword")
                .firstName("Test")
                .lastName("User")
                .role(Role.USER)
                .active(true)
                .build();
        testUser = userRepository.save(testUser);

        // Generate a realistic long JWT token (typically 300-500 characters)
        // This simulates a real JWT token with claims, signature, etc.
        longJwtToken = "eyJhbGciOiJIUzM4NCIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwiZW1haWwiOiJ0ZXN0QGV4YW1wbGUuY29tIiwiYXV0aG9yaXRpZXMiOiJST0xFX1VTRVIiLCJpYXQiOjE1MTYyMzkwMjIsImV4cCI6MTUxNjMyNTQyMiwic29tZUV4dHJhQ2xhaW0iOiJzb21lVmFsdWUiLCJhbm90aGVyQ2xhaW0iOiJhbm90aGVyVmFsdWUiLCJhZGRpdGlvbmFsRGF0YSI6eyJrZXkxIjoidmFsdWUxIiwia2V5MiI6InZhbHVlMiJ9fQ.verylongsignaturepartthatshouldexceed255characterswhenallcombinedtogetherwithheaderandpayloadpartsthisiswhyweneedtextcolumntype";
    }

    @Test
    @DisplayName("Should save refresh token with long JWT token (> 255 chars)")
    void shouldSaveRefreshTokenWithLongToken() {
        // Given
        RefreshToken refreshToken = RefreshToken.builder()
                .user(testUser)
                .token(longJwtToken)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        // When
        RefreshToken savedToken = refreshTokenRepository.save(refreshToken);

        // Then
        assertThat(savedToken.getId()).isNotNull();
        assertThat(savedToken.getToken()).isEqualTo(longJwtToken);
        assertThat(savedToken.getToken().length()).isGreaterThan(255);
        assertThat(savedToken.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should find refresh token by token string")
    void shouldFindRefreshTokenByToken() {
        // Given
        RefreshToken refreshToken = RefreshToken.builder()
                .user(testUser)
                .token(longJwtToken)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        refreshTokenRepository.save(refreshToken);

        // When
        Optional<RefreshToken> foundToken = refreshTokenRepository.findByToken(longJwtToken);

        // Then
        assertThat(foundToken).isPresent();
        assertThat(foundToken.get().getToken()).isEqualTo(longJwtToken);
        assertThat(foundToken.get().getUser().getId()).isEqualTo(testUser.getId());
    }

    @Test
    @DisplayName("Should find all refresh tokens for a user")
    void shouldFindAllRefreshTokensForUser() {
        // Given
        RefreshToken token1 = RefreshToken.builder()
                .user(testUser)
                .token(longJwtToken)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        RefreshToken token2 = RefreshToken.builder()
                .user(testUser)
                .token(longJwtToken + "_second")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        refreshTokenRepository.save(token1);
        refreshTokenRepository.save(token2);

        // When
        List<RefreshToken> tokens = refreshTokenRepository.findByUserId(testUser.getId());

        // Then
        assertThat(tokens).hasSize(2);
        assertThat(tokens).extracting(RefreshToken::getToken)
                .containsExactlyInAnyOrder(longJwtToken, longJwtToken + "_second");
    }

    @Test
    @DisplayName("Should delete refresh token by token string")
    void shouldDeleteRefreshTokenByToken() {
        // Given
        RefreshToken refreshToken = RefreshToken.builder()
                .user(testUser)
                .token(longJwtToken)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        refreshTokenRepository.save(refreshToken);

        // When
        refreshTokenRepository.deleteByToken(longJwtToken);

        // Then
        Optional<RefreshToken> foundToken = refreshTokenRepository.findByToken(longJwtToken);
        assertThat(foundToken).isEmpty();
    }

    @Test
    @DisplayName("Should delete all refresh tokens for a user")
    void shouldDeleteAllRefreshTokensForUser() {
        // Given
        RefreshToken token1 = RefreshToken.builder()
                .user(testUser)
                .token(longJwtToken)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        RefreshToken token2 = RefreshToken.builder()
                .user(testUser)
                .token(longJwtToken + "_second")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        refreshTokenRepository.save(token1);
        refreshTokenRepository.save(token2);

        // When
        refreshTokenRepository.deleteByUserId(testUser.getId());

        // Then
        List<RefreshToken> tokens = refreshTokenRepository.findByUserId(testUser.getId());
        assertThat(tokens).isEmpty();
    }

    @Test
    @DisplayName("Should delete expired refresh tokens")
    void shouldDeleteExpiredRefreshTokens() {
        // Given
        RefreshToken expiredToken = RefreshToken.builder()
                .user(testUser)
                .token(longJwtToken + "_expired")
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();

        RefreshToken validToken = RefreshToken.builder()
                .user(testUser)
                .token(longJwtToken + "_valid")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        refreshTokenRepository.save(expiredToken);
        refreshTokenRepository.save(validToken);

        // When
        int deletedCount = refreshTokenRepository.deleteExpiredTokens(LocalDateTime.now());

        // Then
        assertThat(deletedCount).isEqualTo(1);
        List<RefreshToken> remainingTokens = refreshTokenRepository.findByUserId(testUser.getId());
        assertThat(remainingTokens).hasSize(1);
        assertThat(remainingTokens.get(0).getToken()).isEqualTo(longJwtToken + "_valid");
    }

    @Test
    @DisplayName("Should check if token exists and is not expired")
    void shouldCheckIfTokenExistsAndNotExpired() {
        // Given
        RefreshToken expiredToken = RefreshToken.builder()
                .user(testUser)
                .token(longJwtToken + "_expired")
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();

        RefreshToken validToken = RefreshToken.builder()
                .user(testUser)
                .token(longJwtToken + "_valid")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        refreshTokenRepository.save(expiredToken);
        refreshTokenRepository.save(validToken);

        // When
        boolean expiredExists = refreshTokenRepository.existsByTokenAndNotExpired(
                longJwtToken + "_expired", LocalDateTime.now());
        boolean validExists = refreshTokenRepository.existsByTokenAndNotExpired(
                longJwtToken + "_valid", LocalDateTime.now());
        boolean nonExistentExists = refreshTokenRepository.existsByTokenAndNotExpired(
                "nonexistent", LocalDateTime.now());

        // Then
        assertThat(expiredExists).isFalse();
        assertThat(validExists).isTrue();
        assertThat(nonExistentExists).isFalse();
    }

    @Test
    @DisplayName("Should validate refresh token expiration using isExpired method")
    void shouldValidateRefreshTokenExpiration() {
        // Given
        RefreshToken expiredToken = RefreshToken.builder()
                .user(testUser)
                .token(longJwtToken + "_expired")
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();

        RefreshToken validToken = RefreshToken.builder()
                .user(testUser)
                .token(longJwtToken + "_valid")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        // When
        boolean expiredIsExpired = expiredToken.isExpired();
        boolean validIsExpired = validToken.isExpired();
        boolean expiredIsValid = expiredToken.isValid();
        boolean validIsValid = validToken.isValid();

        // Then
        assertThat(expiredIsExpired).isTrue();
        assertThat(validIsExpired).isFalse();
        assertThat(expiredIsValid).isFalse();
        assertThat(validIsValid).isTrue();
    }

    @Test
    @DisplayName("Should handle token uniqueness constraint")
    void shouldHandleTokenUniquenessConstraint() {
        // Given
        RefreshToken token1 = RefreshToken.builder()
                .user(testUser)
                .token(longJwtToken)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        refreshTokenRepository.save(token1);

        // Create another user to test unique constraint across users
        User anotherUser = User.builder()
                .email("another@example.com")
                .passwordHash("$2y$10$hashedPassword")
                .firstName("Another")
                .lastName("User")
                .role(Role.USER)
                .active(true)
                .build();
        anotherUser = userRepository.save(anotherUser);

        RefreshToken token2 = RefreshToken.builder()
                .user(anotherUser)
                .token(longJwtToken) // Same token as token1
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        // When/Then
        // This should throw a constraint violation exception
        try {
            refreshTokenRepository.save(token2);
            refreshTokenRepository.flush();
            // If we reach here, the test should fail
            assertThat(false).as("Should have thrown constraint violation").isTrue();
        } catch (Exception e) {
            // Expected - constraint violation for duplicate token
            assertThat(e).hasMessageContaining("constraint");
        }
    }
}
