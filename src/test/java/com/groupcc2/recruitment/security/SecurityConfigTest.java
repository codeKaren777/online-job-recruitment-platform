package com.groupcc2.recruitment.security;

import java.time.Instant;
import java.util.Base64;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigTest {
    private static final byte[] KEY = new byte[32]; // Test-only key, never application configuration.
    private final SecurityConfig config = new SecurityConfig();
    private final AccountJwtValidator accountJwtValidator = mock(AccountJwtValidator.class);

    SecurityConfigTest() {
        when(accountJwtValidator.validate(any(Jwt.class))).thenReturn(OAuth2TokenValidatorResult.success());
    }

    @Test
    void rejectsWeakAndMalformedSecrets() {
        assertThatThrownBy(() -> config.jwtDecoder("invalid!", "issuer", "api", accountJwtValidator))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.jwtDecoder("YWJj", "issuer", "api", accountJwtValidator))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesSignatureExpiryIssuerAudienceAndRequiredClaims() throws Exception {
        var decoder = config.jwtDecoder(
                Base64.getEncoder().encodeToString(KEY), "issuer", "api", accountJwtValidator);
        assertThat(decoder.decode(token("issuer", "api", Instant.now().plusSeconds(300), "user", KEY))
                .getSubject()).isEqualTo("user");
        for (String invalid : new String[] {
                token("wrong", "api", Instant.now().plusSeconds(300), "user", KEY),
                token("issuer", "wrong", Instant.now().plusSeconds(300), "user", KEY),
                token("issuer", "api", Instant.now().minusSeconds(300), "user", KEY),
                token("issuer", "api", null, "user", KEY),
                token("issuer", "api", Instant.now().plusSeconds(300), null, KEY),
                "malformed.jwt",
                token("issuer", "api", Instant.now().plusSeconds(300), "user",
                        "different-signing-key-32-bytes-long".getBytes(java.nio.charset.StandardCharsets.UTF_8))
        }) {
            assertThatThrownBy(() -> decoder.decode(invalid)).isInstanceOf(JwtException.class);
        }
    }

    @Test
    void mapsPersistedRoleClaimToRoleAuthorityAndRejectsMalformedIdentity() {
        String userId = UUID.randomUUID().toString();
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject(userId)
                .claim("role", "EMPLOYER")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        var authentication = config.jwtAuthenticationConverter().convert(jwt);

        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_EMPLOYER");

        Jwt invalidRole = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject(userId)
                .claim("role", "SUPERUSER")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        assertThatThrownBy(() -> config.jwtAuthenticationConverter().convert(invalidRole))
                .isInstanceOf(BadJwtException.class);

        Jwt invalidSubject = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject("not-a-uuid")
                .claim("role", "JOB_SEEKER")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        assertThatThrownBy(() -> config.jwtAuthenticationConverter().convert(invalidSubject))
                .isInstanceOf(BadJwtException.class);
    }

    private String token(String issuer, String audience, Instant expiry, String subject, byte[] key)
            throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(issuer).audience(audience).subject(subject)
                .claim("role", "JOB_SEEKER").claim("tokenVersion", 0);
        if (expiry != null) {
            claims.expirationTime(Date.from(expiry));
        }
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
        jwt.sign(new MACSigner(key));
        return jwt.serialize();
    }
}
