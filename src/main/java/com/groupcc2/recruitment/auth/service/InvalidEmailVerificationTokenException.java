package com.groupcc2.recruitment.auth.service;

public class InvalidEmailVerificationTokenException extends RuntimeException {
    public InvalidEmailVerificationTokenException() { super("Email verification token is invalid or expired"); }
}
