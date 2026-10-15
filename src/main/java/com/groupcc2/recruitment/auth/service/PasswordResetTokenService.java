package com.groupcc2.recruitment.auth.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Service;

@Service
public class PasswordResetTokenService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    public String generateToken() {
        byte[] token = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    public String hashToken(String rawToken) {
        try {
            if (rawToken == null) {
                throw new InvalidPasswordResetTokenException();
            }
            byte[] decoded = Base64.getUrlDecoder().decode(rawToken);
            if (decoded.length != TOKEN_BYTES) {
                throw new InvalidPasswordResetTokenException();
            }
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(decoded);
            return HexFormat.of().formatHex(digest);
        } catch (IllegalArgumentException exception) {
            throw new InvalidPasswordResetTokenException();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
