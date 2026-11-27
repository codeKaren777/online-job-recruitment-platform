package com.groupcc2.recruitment.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record EmailVerificationRequest(@NotBlank String token) {}
