package com.groupcc2.recruitment.profile.dto;
import jakarta.validation.constraints.*;
public record ProfileRequest(@NotBlank @Size(max=200) String displayName,
 @Size(max=50) String phone, @Size(max=10000) String biography) {}
