package com.groupcc2.recruitment.profile.dto;
import java.net.URI;
import java.time.Instant;
public record DownloadResponse(URI url, Instant expiresAt) {}
