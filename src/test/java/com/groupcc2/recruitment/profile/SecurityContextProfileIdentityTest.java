package com.groupcc2.recruitment.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.groupcc2.recruitment.profile.service.SecurityContextProfileIdentity;
import com.groupcc2.recruitment.profile.service.ProfileFailure;
import com.groupcc2.recruitment.users.entity.User;
import com.groupcc2.recruitment.users.entity.UserRole;
import com.groupcc2.recruitment.users.service.InactiveAccountException;
import com.groupcc2.recruitment.users.service.UserNotFoundException;
import com.groupcc2.recruitment.users.service.UserService;
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
import org.springframework.stereotype.Component;

class SecurityContextProfileIdentityTest {
    private final UserService users = mock(UserService.class);
    private final SecurityContextProfileIdentity identity = new SecurityContextProfileIdentity(users);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void productionAdapterIsRegisteredAsSpringComponent() {
        assertThat(SecurityContextProfileIdentity.class.getAnnotation(Component.class)).isNotNull();
    }

    @Test
    void resolvesActiveUserIdFromAuthenticatedJwtSubject() {
        UUID userId = UUID.randomUUID();
        User user = new User("person@example.test", "hash", UserRole.JOB_SEEKER);
        when(users.findById(userId)).thenReturn(user);
        Jwt principal = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                principal, List.of(new SimpleGrantedAuthority("ROLE_JOB_SEEKER"))));

        assertThat(identity.authenticatedUserId()).isEqualTo(userId);
        verify(users).requireActive(user);
    }

    @Test
    void rejectsMissingAuthentication() {
        assertThatThrownBy(identity::authenticatedUserId)
                .isInstanceOf(ProfileFailure.class)
                .hasMessage("FORBIDDEN");
    }

    @Test
    void rejectsNonJwtAuthenticationInsteadOfTrustingItsName() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("client-user-id", "ignored", List.of()));

        assertThatThrownBy(identity::authenticatedUserId)
                .isInstanceOf(ProfileFailure.class)
                .hasMessage("FORBIDDEN");
        verifyNoUserLookup();
    }

    @Test
    void rejectsMalformedJwtSubject() {
        setJwtSubject("not-a-uuid");

        assertThatThrownBy(identity::authenticatedUserId)
                .isInstanceOf(ProfileFailure.class)
                .hasMessage("FORBIDDEN");
        verifyNoUserLookup();
    }

    @Test
    void rejectsMissingUser() {
        UUID userId = UUID.randomUUID();
        when(users.findById(userId)).thenThrow(new UserNotFoundException());
        setJwtSubject(userId.toString());

        assertThatThrownBy(identity::authenticatedUserId)
                .isInstanceOf(UserNotFoundException.class);
        verify(users).findById(userId);
    }

    @Test
    void rejectsInactiveUser() {
        UUID userId = UUID.randomUUID();
        User user = new User("person@example.test", "hash", UserRole.JOB_SEEKER);
        when(users.findById(userId)).thenReturn(user);
        doThrow(new InactiveAccountException()).when(users).requireActive(user);
        setJwtSubject(userId.toString());

        assertThatThrownBy(identity::authenticatedUserId)
                .isInstanceOf(InactiveAccountException.class);
        verify(users).requireActive(user);
    }

    @Test
    void rejectsSuspendedAndRemovedUsers() {
        for (boolean removed : List.of(false, true)) {
            UUID userId = UUID.randomUUID();
            User user = new User("person-" + userId + "@example.test", "hash", UserRole.JOB_SEEKER);
            if (removed) {
                user.remove();
            } else {
                user.suspend();
            }
            when(users.findById(userId)).thenReturn(user);
            doThrow(new InactiveAccountException()).when(users).requireActive(user);
            setJwtSubject(userId.toString());

            assertThatThrownBy(identity::authenticatedUserId)
                    .isInstanceOf(InactiveAccountException.class);
        }
    }

    @Test
    void ignoresClientSuppliedIdentityValues() {
        UUID tokenUserId = UUID.randomUUID();
        UUID clientSuppliedUserId = UUID.randomUUID();
        User user = new User("person@example.test", "hash", UserRole.JOB_SEEKER);
        when(users.findById(tokenUserId)).thenReturn(user);
        setJwtSubject(tokenUserId.toString());

        UUID resolved = identity.authenticatedUserId();
        assertThat(resolved).isEqualTo(tokenUserId).isNotEqualTo(clientSuppliedUserId);
        verify(users).findById(tokenUserId);
    }

    private void setJwtSubject(String subject) {
        Jwt principal = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                principal, List.of(new SimpleGrantedAuthority("ROLE_JOB_SEEKER"))));
    }

    private void verifyNoUserLookup() {
        org.mockito.Mockito.verifyNoInteractions(users);
    }
}
