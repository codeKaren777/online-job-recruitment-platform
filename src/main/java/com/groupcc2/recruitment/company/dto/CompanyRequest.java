package com.groupcc2.recruitment.company.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompanyRequest(
        @NotBlank @Size(max = 255)
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Acme Technologies")
        String name,
        @Size(max = 10000)
        @Schema(example = "Software and recruitment technology company.")
        String description,
        @Email @Size(max = 254)
        @Schema(example = "contact@acme.example")
        String contactEmail,
        @Size(max = 50)
        @Schema(example = "+265999000111")
        String contactPhone,
        @Size(max = 255)
        @Schema(example = "Blantyre")
        String location) {
}
