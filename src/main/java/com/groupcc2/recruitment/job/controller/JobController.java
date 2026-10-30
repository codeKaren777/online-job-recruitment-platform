package com.groupcc2.recruitment.job.controller;

import com.groupcc2.recruitment.job.dto.JobRequest;
import com.groupcc2.recruitment.job.dto.JobResponse;
import com.groupcc2.recruitment.job.dto.PageResponse;
import com.groupcc2.recruitment.job.entity.JobStatus;
import com.groupcc2.recruitment.job.service.JobService;
import com.groupcc2.recruitment.users.entity.UserRole;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Jobs", description = "Job postings")
@SecurityRequirement(name = "bearerAuth")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping("/jobs")
    @PreAuthorize("hasRole('EMPLOYER')")
    @Operation(summary = "Create a job posting", description = "Creates a job posting for a company owned by the authenticated EMPLOYER. Requires a bearer token.", requestBody=@io.swagger.v3.oas.annotations.parameters.RequestBody(description="Job details. The employer identity comes from the bearer token.",required=true,content=@io.swagger.v3.oas.annotations.media.Content(examples=@ExampleObject(value="{\"companyId\":\"11111111-1111-1111-1111-111111111111\",\"title\":\"Senior Java Developer\",\"description\":\"Build backend services.\",\"requiredQualifications\":\"Java 21, Spring Boot, PostgreSQL\",\"dueDate\":\"2099-12-31\",\"location\":\"Blantyre\"}"))))
    @ApiResponses({@ApiResponse(responseCode="201",description="Job created; returns the posting and its location header."),@ApiResponse(responseCode="400",description="Request body is malformed, fails validation, or dueDate is not after today."),@ApiResponse(responseCode="401",description="A valid bearer token is required."),@ApiResponse(responseCode="403",description="The caller does not have the EMPLOYER role."),@ApiResponse(responseCode="404",description="The company was not found for this employer.")})
    public ResponseEntity<JobResponse> create(@Valid @RequestBody JobRequest request,
                                              @AuthenticationPrincipal Jwt jwt) {
        JobResponse created = jobService.createJob(userId(jwt), userRole(jwt), request);
        return ResponseEntity.created(URI.create("/api/jobs/" + created.id())).body(created);
    }

    @GetMapping("/jobs")
    @PreAuthorize("hasRole('JOB_SEEKER')")
    @Operation(summary = "Browse job postings", description = "Returns a page of active, non-expired jobs to a JOB_SEEKER. Requires a bearer token. Page defaults to 0 and size to 20; results are sorted newest first.")
    @ApiResponses({@ApiResponse(responseCode="200",description="Returns a page of matching jobs."),@ApiResponse(responseCode="401",description="A valid bearer token is required."),@ApiResponse(responseCode="403",description="The caller does not have the JOB_SEEKER role.")})
    public PageResponse<JobResponse> browse(@Parameter(description="Filter by location text.") @RequestParam(required = false) String location,
                                            @Parameter(description="Search job title and description.") @RequestParam(required = false, name = "q") String text,
                                            @Parameter(description="Zero-based page index (default 0).") @RequestParam(defaultValue = "0") int page,
                                            @Parameter(description="Page size (default 20; maximum 100).") @RequestParam(defaultValue = "20") int size,
                                            @AuthenticationPrincipal Jwt jwt) {
        return jobService.browseJobs(userId(jwt), userRole(jwt), location, text, page, size);
    }

    @GetMapping("/jobs/{jobId}")
    @PreAuthorize("hasAnyRole('JOB_SEEKER','EMPLOYER')")
    @Operation(summary = "Get a job posting", description = "Returns the job identified by jobId. Employers can view their own jobs; job seekers can view active jobs. Requires a bearer token.")
    @Parameter(name="jobId",description="UUID of the job posting.",required=true)
    @ApiResponses({@ApiResponse(responseCode="200",description="Returns the job posting."),@ApiResponse(responseCode="401",description="A valid bearer token is required."),@ApiResponse(responseCode="403",description="The caller is not permitted to view jobs."),@ApiResponse(responseCode="404",description="Job was not found or is not visible to this caller.")})
    public JobResponse get(@PathVariable UUID jobId, @AuthenticationPrincipal Jwt jwt) {
        return jobService.getJob(jobId, userId(jwt), userRole(jwt));
    }

    @GetMapping("/employer/jobs")
    @PreAuthorize("hasRole('EMPLOYER')")
    @Operation(summary = "List my job postings", description = "Returns a page of the authenticated EMPLOYER's job postings. Requires a bearer token. Page defaults to 0 and size to 20; results are sorted newest first.")
    @ApiResponses({@ApiResponse(responseCode="200",description="Returns a page of the employer's jobs."),@ApiResponse(responseCode="400",description="Status query parameter has an invalid value."),@ApiResponse(responseCode="401",description="A valid bearer token is required."),@ApiResponse(responseCode="403",description="The caller does not have the EMPLOYER role.")})
    public PageResponse<JobResponse> myJobs(@Parameter(description="Optional effective job status filter.") @RequestParam(required = false) JobStatus status,
                                            @Parameter(description="Zero-based page index (default 0).") @RequestParam(defaultValue = "0") int page,
                                            @Parameter(description="Page size (default 20; maximum 100).") @RequestParam(defaultValue = "20") int size,
                                            @AuthenticationPrincipal Jwt jwt) {
        return jobService.getEmployerJobs(userId(jwt), userRole(jwt), status, page, size);
    }

    @PutMapping("/jobs/{jobId}")
    @PreAuthorize("hasRole('EMPLOYER')")
    @Operation(summary = "Update a job posting", description = "Replaces jobId's details when the job and selected company belong to the authenticated EMPLOYER. Requires a bearer token.", requestBody=@io.swagger.v3.oas.annotations.parameters.RequestBody(description="Replacement job details.",required=true,content=@io.swagger.v3.oas.annotations.media.Content(examples=@ExampleObject(value="{\"companyId\":\"11111111-1111-1111-1111-111111111111\",\"title\":\"Senior Java Developer\",\"description\":\"Build backend services.\",\"requiredQualifications\":\"Java 21, Spring Boot, PostgreSQL\",\"dueDate\":\"2099-12-31\",\"location\":\"Blantyre\"}"))))
    @Parameter(name="jobId",description="UUID of the job posting.",required=true)
    @ApiResponses({@ApiResponse(responseCode="200",description="Returns updated job details."),@ApiResponse(responseCode="400",description="Request body is malformed, fails validation, or dueDate is not after today."),@ApiResponse(responseCode="401",description="A valid bearer token is required."),@ApiResponse(responseCode="403",description="The caller does not have the EMPLOYER role."),@ApiResponse(responseCode="404",description="Job or owned company was not found.")})
    public JobResponse update(@PathVariable UUID jobId, @Valid @RequestBody JobRequest request,
                              @AuthenticationPrincipal Jwt jwt) {
        return jobService.updateJob(userId(jwt), userRole(jwt), jobId, request);
    }

    @PatchMapping("/jobs/{jobId}/close")
    @PreAuthorize("hasRole('EMPLOYER')")
    @Operation(summary = "Close a job posting", description = "Closes jobId if it belongs to the authenticated EMPLOYER. Closing an already closed job returns it unchanged. Requires a bearer token.")
    @Parameter(name="jobId",description="UUID of the job posting.",required=true)
    @ApiResponses({@ApiResponse(responseCode="200",description="Returns the closed job posting."),@ApiResponse(responseCode="401",description="A valid bearer token is required."),@ApiResponse(responseCode="403",description="The caller does not have the EMPLOYER role."),@ApiResponse(responseCode="404",description="Job was not found for this employer.")})
    public JobResponse close(@PathVariable UUID jobId, @AuthenticationPrincipal Jwt jwt) {
        return jobService.closeJob(userId(jwt), userRole(jwt), jobId);
    }

    @DeleteMapping("/jobs/{jobId}")
    @PreAuthorize("hasRole('EMPLOYER')")
    @Operation(summary = "Delete a job posting", description = "Deletes jobId if it belongs to the authenticated EMPLOYER. Requires a bearer token.")
    @Parameter(name="jobId",description="UUID of the job posting.",required=true)
    @ApiResponses({@ApiResponse(responseCode="204",description="Job deleted."),@ApiResponse(responseCode="401",description="A valid bearer token is required."),@ApiResponse(responseCode="403",description="The caller does not have the EMPLOYER role."),@ApiResponse(responseCode="404",description="Job was not found for this employer."),@ApiResponse(responseCode="409",description="Job has submitted applications and cannot be deleted.")})
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID jobId, @AuthenticationPrincipal Jwt jwt) {
        jobService.deleteJob(userId(jwt), userRole(jwt), jobId);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static UserRole userRole(Jwt jwt) {
        return UserRole.valueOf(jwt.getClaimAsString("role"));
    }
}
