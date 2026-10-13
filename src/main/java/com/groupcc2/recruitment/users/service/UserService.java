package com.groupcc2.recruitment.users.service;

import java.util.UUID;

import com.groupcc2.recruitment.users.entity.User;
import com.groupcc2.recruitment.users.entity.UserRole;

public interface UserService {
    User register(String email, String rawPassword, UserRole role);

    User findById(UUID id);

    User findByNormalizedEmail(String email);

    User findForAuthentication(String email);

    void requireActive(User user);

    void suspend(UUID administratorId, UUID id);

    void remove(UUID administratorId, UUID id);
}
