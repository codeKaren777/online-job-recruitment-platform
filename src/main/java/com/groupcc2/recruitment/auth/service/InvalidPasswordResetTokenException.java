package com.groupcc2.recruitment.auth.service;

public class InvalidPasswordResetTokenException extends RuntimeException {
    public InvalidPasswordResetTokenException() {
        super("The password reset token is invalid or expired");
    }
}
