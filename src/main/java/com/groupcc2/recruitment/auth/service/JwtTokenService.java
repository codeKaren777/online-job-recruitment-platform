package com.groupcc2.recruitment.auth.service;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import com.groupcc2.recruitment.auth.entity.User;
import com.groupcc2.recruitment.auth.entity.UserRole;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
    public static final long ACCESS_TOKEN_LIFETIME_SECONDS = 1800;

    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final String audience;

    public JwtTokenService(
            @Value("${app.security.jwt.secret-base64}") String secretBase64,
            @Value("${app.security.jwt.issuer}") String issuer,
            @Value("${app.security.jwt.audience}") String audience) {
        if (issuer.isBlank() || audience.isBlank()) {
            throw new IllegalArgumentException("JWT issuer and audience must not be blank");
        }

        byte[] key;
        try {
            key = Base64.getDecoder().decode(secretBase64);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("JWT_SECRET_BASE64 must be valid Base64");
        }
        if (key.length < 32) {
            throw new IllegalArgumentException("JWT_SECRET_BASE64 must contain at least 32 random bytes");
        }

        OctetSequenceKey signingKey = new OctetSequenceKey.Builder(key)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.HS256)
                .build();
        JWKSet jwkSet = new JWKSet(signingKey);
        JWKSource<SecurityContext> jwkSource = (selector, context) -> selector.select(jwkSet);
        this.jwtEncoder = new NimbusJwtEncoder(jwkSource);
        this.issuer = issuer;
        this.audience = audience;
    }

    public String generateAccessToken(User user) {
        if (user == null || user.getId() == null || user.getRole() == null) {
            throw new IllegalArgumentException("A persisted user with an assigned role is required");
        }
        return generateAccessToken(user.getId(), user.getRole(), user.getTokenVersion());
    }

    public String generateAccessToken(UUID userId, UserRole role) {
        return generateAccessToken(userId, role, 0);
    }

    public String generateAccessToken(UUID userId, UserRole role, int tokenVersion) {
        if (userId == null || role == null) {
            throw new IllegalArgumentException("A persisted user ID and role are required");
        }
        if (tokenVersion < 0) {
            throw new IllegalArgumentException("Token version must not be negative");
        }
        Instant issuedAt = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .audience(List.of(audience))
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(ACCESS_TOKEN_LIFETIME_SECONDS))
                .claim("role", role.name())
                .claim("tokenVersion", tokenVersion)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
