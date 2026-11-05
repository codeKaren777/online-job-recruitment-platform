package com.groupcc2.recruitment.auth.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import com.groupcc2.recruitment.auth.repository.AuthCredentialRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BootstrapAdminInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final AuthCredentialRepository credentials;
    private final PasswordHashingService passwordHashingService;
    private final String email;
    private final String password;

    public BootstrapAdminInitializer(
            AuthCredentialRepository credentials,
            PasswordHashingService passwordHashingService,
            @Value("${app.auth.bootstrap-admin.email:}") String email,
            @Value("${app.auth.bootstrap-admin.password:}") String password) {
        this.credentials = credentials;
        this.passwordHashingService = passwordHashingService;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        boolean emailConfigured = !email.isBlank();
        boolean passwordConfigured = !password.isBlank();
        if (!emailConfigured && !passwordConfigured) {
            return;
        }
        if (!emailConfigured || !passwordConfigured) {
            throw new IllegalStateException(
                    "Both APP_BOOTSTRAP_ADMIN_EMAIL and APP_BOOTSTRAP_ADMIN_PASSWORD are required together");
        }

        String normalizedEmail = normalizeEmail(email);
        validatePassword(password);
        int result = credentials.bootstrapAdmin(normalizedEmail, passwordHashingService.hash(password));
        if (result == 1) {
            log.info("Created the initial administrator account for {}", normalizedEmail);
        } else if (result == 0) {
            log.warn("An administrator already exists; bootstrap credentials were not used. Remove bootstrap admin secrets.");
        } else if (result == -1) {
            throw new IllegalStateException(
                    "Bootstrap administrator email is already used by a non-administrator account");
        } else {
            throw new IllegalStateException("Unexpected result from administrator bootstrap");
        }
    }

    private static String normalizeEmail(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 254 || !normalized.contains("@")) {
            throw new IllegalArgumentException("APP_BOOTSTRAP_ADMIN_EMAIL must be a valid email address");
        }
        return normalized;
    }

    private static void validatePassword(String value) {
        int bytes = value.getBytes(StandardCharsets.UTF_8).length;
        if (value.length() < 12 || bytes > 72) {
            throw new IllegalArgumentException(
                    "APP_BOOTSTRAP_ADMIN_PASSWORD must be 12 to 72 UTF-8 bytes");
        }
    }
}
