package com.groupcc2.recruitment.profile.service;

import java.util.UUID;
import com.groupcc2.recruitment.users.entity.User;
import com.groupcc2.recruitment.users.service.UserNotFoundException;
import com.groupcc2.recruitment.users.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Resolves profile ownership from the validated bearer token's UUID subject. */
@Component
public class SecurityContextProfileIdentity implements ProfileIdentity {
    private final UserService users;
    private final ProfileRls rls;

    public SecurityContextProfileIdentity(UserService users) {
        this(users, null);
    }

    @Autowired
    public SecurityContextProfileIdentity(UserService users, ProfileRls rls) {
        this.users = users;
        this.rls = rls;
    }

    @Override
    public UUID authenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw forbidden();
        }
        UUID userId = userId(jwt);
        if (rls != null) {
            // The JWT validator establishes identity in its own transaction. This
            // identity lookup runs on a new transaction/connection, so bootstrap
            // self visibility before consulting the users service.
            rls.establish(userId, "");
        }
        User user = users.findById(userId);
        if (user == null) {
            throw new UserNotFoundException();
        }
        users.requireActive(user);
        if (rls != null) {
            rls.establish(userId, user.getRole().name());
        }
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
