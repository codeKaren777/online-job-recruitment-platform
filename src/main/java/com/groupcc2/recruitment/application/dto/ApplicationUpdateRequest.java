package com.groupcc2.recruitment.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApplicationUpdateRequest(@NotBlank @Size(max = 10000) String coverLetter) {}
