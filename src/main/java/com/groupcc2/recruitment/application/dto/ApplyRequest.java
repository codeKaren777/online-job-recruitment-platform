package com.groupcc2.recruitment.application.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** The applicant is never part of the request; it always comes from the authenticated token. */
public record ApplyRequest(@NotNull UUID jobId) {
}
