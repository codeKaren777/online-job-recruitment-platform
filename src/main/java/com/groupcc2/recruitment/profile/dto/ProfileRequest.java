package com.groupcc2.recruitment.profile.dto;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
public record ProfileRequest(
 @NotBlank @Size(max=200)
 @Schema(description="Public display name", requiredMode=Schema.RequiredMode.REQUIRED, example="Jane Applicant")
 String displayName,
 @Size(max=200)
 @Schema(description="Full legal name; defaults to displayName when omitted", example="Jane Mary Applicant")
 String fullName,
 @Past
 @Schema(description="Date of birth", example="1995-06-15")
 LocalDate dateOfBirth,
 @Size(max=50)
 @Schema(description="Optional contact phone number", example="+265999000111")
 String phone,
 @Size(max=255)
 @Schema(description="Current town, city, or address", example="Blantyre")
 String location,
 @Size(max=10000)
 @Schema(description="Optional professional biography", example="Backend developer with five years of experience.")
 String biography) {
 public ProfileRequest(String displayName, String phone, String biography) {
  this(displayName, null, null, phone, null, biography);
 }
}
