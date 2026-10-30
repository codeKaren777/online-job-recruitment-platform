package com.groupcc2.recruitment.profile.controller;

import java.util.List;
import java.util.UUID;

import com.groupcc2.recruitment.profile.dto.DocumentResponse;
import com.groupcc2.recruitment.profile.dto.DownloadResponse;
import com.groupcc2.recruitment.profile.dto.ProfileResponse;
import com.groupcc2.recruitment.profile.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Employer reads are limited to applicants for that employer's specific job. */
@RestController
@RequestMapping("/api/employer/jobs/{jobId}/applicants/{applicantId}/profile")
@PreAuthorize("hasRole('EMPLOYER')")
@Tag(name = "Profile", description = "Manage job seeker profiles and private documents")
@SecurityRequirement(name = "bearerAuth")
public class EmployerProfileController {
    private final ProfileService profiles;

    public EmployerProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    @Operation(summary = "View an applicant profile",
            description = "Returns the applicant profile only when the authenticated employer owns jobId "
                    + "and applicantId has applied to that specific job.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Applicant profile returned."),
            @ApiResponse(responseCode = "401", description = "A valid bearer token is required."),
            @ApiResponse(responseCode = "403", description = "The employer does not own this job or the applicant has not applied."),
            @ApiResponse(responseCode = "404", description = "The applicant profile was not found."),
            @ApiResponse(responseCode = "503", description = "Profile integration is unavailable.")
    })
    public ProfileResponse profile(
            @Parameter(description = "UUID of the employer-owned job.", required = true) @PathVariable UUID jobId,
            @Parameter(description = "UUID of the job seeker who applied.", required = true) @PathVariable UUID applicantId) {
        return profiles.readForEmployer(jobId, applicantId);
    }

    @GetMapping("/documents")
    @Operation(summary = "List an applicant's documents",
            description = "Returns private document metadata after verifying the employer owns jobId "
                    + "and applicantId applied to that specific job.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Applicant document metadata returned."),
            @ApiResponse(responseCode = "401", description = "A valid bearer token is required."),
            @ApiResponse(responseCode = "403", description = "The employer does not own this job or the applicant has not applied."),
            @ApiResponse(responseCode = "503", description = "Profile integration is unavailable.")
    })
    public List<DocumentResponse> documents(@PathVariable UUID jobId, @PathVariable UUID applicantId) {
        return profiles.listForEmployer(jobId, applicantId);
    }

    @GetMapping("/documents/{documentId}/download")
    @Operation(summary = "Get an applicant document download",
            description = "Returns a short-lived signed download URL only after verifying the employer owns jobId, "
                    + "applicantId applied to that job, and documentId belongs to that applicant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Short-lived private download URL returned."),
            @ApiResponse(responseCode = "401", description = "A valid bearer token is required."),
            @ApiResponse(responseCode = "403", description = "The employer does not own this job or the applicant has not applied."),
            @ApiResponse(responseCode = "404", description = "The applicant document was not found."),
            @ApiResponse(responseCode = "503", description = "Document storage is unavailable.")
    })
    public DownloadResponse download(@PathVariable UUID jobId, @PathVariable UUID applicantId,
                                     @PathVariable UUID documentId) {
        return profiles.downloadForEmployer(jobId, applicantId, documentId);
    }
}
