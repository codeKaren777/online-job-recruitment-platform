package com.groupcc2.recruitment.security;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigTest {
    private static final byte[] KEY = new byte[32]; // Test-only key, never application configuration.
    private final SecurityConfig config = new SecurityConfig();

    @Test
    void rejectsWeakAndMalformedSecrets() {
        assertThatThrownBy(() -> config.jwtDecoder("invalid!", "issuer", "api"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.jwtDecoder("YWJj", "issuer", "api"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesSignatureExpiryIssuerAudienceAndRequiredClaims() throws Exception {
        var decoder = config.jwtDecoder(Base64.getEncoder().encodeToString(KEY), "issuer", "api");
        assertThat(decoder.decode(token("issuer", "api", Instant.now().plusSeconds(300), "user", KEY))
                .getSubject()).isEqualTo("user");
        for (String invalid : new String[] {
                token("wrong", "api", Instant.now().plusSeconds(300), "user", KEY),
                token("issuer", "wrong", Instant.now().plusSeconds(300), "user", KEY),
                token("issuer", "api", Instant.now().minusSeconds(300), "user", KEY),
                token("issuer", "api", null, "user", KEY),
                token("issuer", "api", Instant.now().plusSeconds(300), null, KEY),
                token("issuer", "api", Instant.now().plusSeconds(300), "user",
                        "different-signing-key-32-bytes-long".getBytes(java.nio.charset.StandardCharsets.UTF_8))
        }) {
            assertThatThrownBy(() -> decoder.decode(invalid)).isInstanceOf(JwtException.class);
        }
    }

    private String token(String issuer, String audience, Instant expiry, String subject, byte[] key)
            throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(issuer).audience(audience).subject(subject);
        if (expiry != null) {
            claims.expirationTime(Date.from(expiry));
        }
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
        jwt.sign(new MACSigner(key));
        return jwt.serialize();
    }
}
