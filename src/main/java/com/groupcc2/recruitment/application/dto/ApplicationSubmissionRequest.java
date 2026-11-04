package com.groupcc2.recruitment.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

@Schema(name = "ApplicationSubmissionRequest", description = "Submit one application with its own PDF CV.")
public class ApplicationSubmissionRequest {
    @NotNull
    @Schema(description = "UUID of the job posting.", requiredMode = Schema.RequiredMode.REQUIRED, format = "uuid")
    private UUID jobId;
    @NotNull
    @Schema(description = "PDF CV for this application (maximum 5 MiB).", requiredMode = Schema.RequiredMode.REQUIRED,
            type = "string", format = "binary")
    private MultipartFile cv;
    public UUID getJobId() { return jobId; }
    public void setJobId(UUID jobId) { this.jobId = jobId; }
    public MultipartFile getCv() { return cv; }
    public void setCv(MultipartFile cv) { this.cv = cv; }
}
