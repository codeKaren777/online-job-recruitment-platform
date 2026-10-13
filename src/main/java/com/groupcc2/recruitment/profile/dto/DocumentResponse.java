package com.groupcc2.recruitment.profile.dto;
import java.time.Instant;
import java.util.UUID;
public record DocumentResponse(UUID id, String originalFilename, String contentType, long byteSize, Instant createdAt) {}
