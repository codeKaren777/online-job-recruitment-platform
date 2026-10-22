package com.groupcc2.recruitment.profile.service;

import java.util.UUID;
import com.groupcc2.recruitment.users.entity.User;
import com.groupcc2.recruitment.users.service.UserNotFoundException;
import com.groupcc2.recruitment.users.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Resolves profile ownership from the validated bearer token's UUID subject. */
@Component
public class SecurityContextProfileIdentity implements ProfileIdentity {
    private final UserService users;

    public SecurityContextProfileIdentity(UserService users) {
        this.users = users;
    }

    @Override
    public UUID authenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw forbidden();
        }
        UUID userId = userId(jwt);
        User user = users.findById(userId);
        if (user == null) {
            throw new UserNotFoundException();
        }
        users.requireActive(user);
        return userId;
    }

    private UUID userId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw forbidden();
        }
    }

    private ProfileFailure forbidden() {
        return new ProfileFailure(ProfileFailure.Code.FORBIDDEN);
    }
}
