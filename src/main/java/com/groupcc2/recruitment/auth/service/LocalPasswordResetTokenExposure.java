package com.groupcc2.recruitment.auth.service;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class LocalPasswordResetTokenExposure implements PasswordResetTokenExposure {
    @Override
    public Optional<String> exposeForLocalDevelopment(String rawToken) {
        return Optional.of(rawToken);
    }
}
