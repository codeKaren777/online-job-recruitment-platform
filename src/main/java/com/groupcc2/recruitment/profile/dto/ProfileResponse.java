package com.groupcc2.recruitment.profile.dto;
import java.time.Instant;
public record ProfileResponse(String displayName, String phone, String biography, Instant createdAt, Instant updatedAt) {}
