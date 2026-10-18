package com.groupcc2.recruitment.auth.service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordHashingService {
    private final PasswordEncoder passwordEncoder;
    private final String dummyPasswordHash;

    public PasswordHashingService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public String hash(CharSequence rawPassword) {
        if (rawPassword == null || rawPassword.toString().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must not exceed 72 UTF-8 bytes");
        }
        return passwordEncoder.encode(rawPassword);
    }

    public boolean matches(CharSequence rawPassword, String passwordHash) {
        if (rawPassword == null || passwordHash == null
                || rawPassword.toString().getBytes(StandardCharsets.UTF_8).length > 72) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, passwordHash);
    }

    public void performDummyMatch(CharSequence rawPassword) {
        passwordEncoder.matches(rawPassword == null ? "" : rawPassword, dummyPasswordHash);
    }
}
