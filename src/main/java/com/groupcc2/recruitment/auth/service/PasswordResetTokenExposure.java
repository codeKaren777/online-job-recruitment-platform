package com.groupcc2.recruitment.auth.service;

import java.util.Optional;

public interface PasswordResetTokenExposure {
    Optional<String> exposeForLocalDevelopment(String rawToken);
}
