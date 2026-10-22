package com.groupcc2.recruitment.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.groupcc2.recruitment.profile.service.SecurityContextProfileIdentity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class SecurityContextProfileIdentityTest {
    private final SecurityContextProfileIdentity identity = new SecurityContextProfileIdentity();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolvesUserIdOnlyFromAuthenticatedJwtSubject() {
        UUID userId = UUID.randomUUID();
        Jwt principal = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                principal, List.of(new SimpleGrantedAuthority("ROLE_JOB_SEEKER"))));

        assertThat(identity.authenticatedUserId()).isEqualTo(userId);
    }

    @Test
    void returnsNoIdentityForAnonymousOrNonJwtAuthentication() {
        assertThat(identity.authenticatedUserId()).isNull();

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("client-user-id", "ignored", List.of()));

        assertThat(identity.authenticatedUserId()).isNull();
    }

    @Test
    void returnsNoIdentityForMalformedJwtSubject() {
        Jwt principal = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("not-a-uuid")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                principal, List.of(new SimpleGrantedAuthority("ROLE_JOB_SEEKER"))));

        assertThat(identity.authenticatedUserId()).isNull();
    }
}
