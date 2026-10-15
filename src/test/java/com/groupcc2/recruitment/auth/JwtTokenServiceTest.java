package com.groupcc2.recruitment.auth;

import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import com.groupcc2.recruitment.auth.entity.User;
import com.groupcc2.recruitment.auth.entity.UserRole;
import com.groupcc2.recruitment.auth.service.JwtTokenService;
import com.groupcc2.recruitment.security.SecurityConfig;
import com.groupcc2.recruitment.security.AccountJwtValidator;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {
    private static final byte[] TEST_KEY = new byte[32];
    private static final String SECRET_BASE64 = Base64.getEncoder().encodeToString(TEST_KEY);
    private static final String ISSUER = "https://test-issuer.example";
    private static final String AUDIENCE = "test-api";

    private final JwtTokenService tokenService =
            new JwtTokenService(SECRET_BASE64, ISSUER, AUDIENCE);
    private final SecurityConfig securityConfig = new SecurityConfig();
    private final AccountJwtValidator accountJwtValidator = mock(AccountJwtValidator.class);

    JwtTokenServiceTest() {
        when(accountJwtValidator.validate(any())).thenReturn(OAuth2TokenValidatorResult.success());
    }

    @Test
    void createsHs256TokenWithTrustedUserClaimsAndThirtyMinuteLifetime() {
        UUID userId = UUID.randomUUID();
        User user = persistedUser(userId, UserRole.EMPLOYER);

        String token = tokenService.generateAccessToken(user);
        var decoded = configuredDecoder(SECRET_BASE64).decode(token);

        assertThat(decoded.getSubject()).isEqualTo(userId.toString());
        assertThat(decoded.getIssuer().toString()).isEqualTo(ISSUER);
        assertThat(decoded.getAudience()).containsExactly(AUDIENCE);
        assertThat(decoded.getClaimAsString("role")).isEqualTo(UserRole.EMPLOYER.name());
        assertThat(decoded.getClaims().get("tokenVersion").toString()).isEqualTo("0");
        assertThat(decoded.getIssuedAt()).isNotNull();
        assertThat(decoded.getExpiresAt()).isNotNull();
        assertThat(Duration.between(decoded.getIssuedAt(), decoded.getExpiresAt()).getSeconds())
                .isEqualTo(JwtTokenService.ACCESS_TOKEN_LIFETIME_SECONDS);
        assertThat(decoded.getHeaders()).containsEntry("alg", "HS256");
    }

    @Test
    void configuredDecoderRejectsTokenSignedWithAnotherKey() {
        User user = persistedUser(UUID.randomUUID(), UserRole.JOB_SEEKER);
        String token = tokenService.generateAccessToken(user);
        String differentKey = Base64.getEncoder().encodeToString(
                "different-test-signing-key-32byte".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThatThrownBy(() -> configuredDecoder(differentKey).decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void requiresPersistedUserIdentityAndRole() {
        User withoutId = new User("person@example.com", "not-used-by-token-service", UserRole.JOB_SEEKER);

        assertThatThrownBy(() -> tokenService.generateAccessToken(withoutId))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tokenService.generateAccessToken(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private User persistedUser(UUID id, UserRole role) {
        User user = new User("person@example.com", "not-used-by-token-service", role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private JwtDecoder configuredDecoder(String secretBase64) {
        return ReflectionTestUtils.invokeMethod(securityConfig, "jwtDecoder",
                secretBase64, ISSUER, AUDIENCE, accountJwtValidator);
    }
}
