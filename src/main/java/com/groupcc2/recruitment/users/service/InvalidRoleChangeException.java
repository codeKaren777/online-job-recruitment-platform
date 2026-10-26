package com.groupcc2.recruitment.users.service;

public class InvalidRoleChangeException extends RuntimeException {
    public InvalidRoleChangeException(String message) {
        super(message);
    }
}
