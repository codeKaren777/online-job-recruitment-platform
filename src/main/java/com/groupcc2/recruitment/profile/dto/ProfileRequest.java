package com.groupcc2.recruitment.profile.dto;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
public record ProfileRequest(
 @NotBlank @Size(max=200)
 @Schema(description="Public display name", requiredMode=Schema.RequiredMode.REQUIRED, example="Jane Applicant")
 String displayName,
 @Size(max=50)
 @Schema(description="Optional contact phone number", example="+265999000111")
 String phone,
 @Size(max=10000)
 @Schema(description="Optional professional biography", example="Backend developer with five years of experience.")
 String biography) {}
