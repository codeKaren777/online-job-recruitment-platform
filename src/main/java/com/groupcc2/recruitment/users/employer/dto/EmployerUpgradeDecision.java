package com.groupcc2.recruitment.users.employer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmployerUpgradeDecision(@NotBlank @Size(max = 2000) String reason) {}
