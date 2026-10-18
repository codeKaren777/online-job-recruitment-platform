package com.groupcc2.recruitment.auth.dto;

public record LoginResponse(String accessToken, String tokenType, long expiresIn) {
}
