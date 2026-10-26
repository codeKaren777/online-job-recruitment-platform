package com.groupcc2.recruitment.application.dto;

import com.groupcc2.recruitment.application.entity.ApplicationStatus;
import jakarta.validation.constraints.NotNull;

public record ApplicationStatusRequest(@NotNull ApplicationStatus status) {
}
