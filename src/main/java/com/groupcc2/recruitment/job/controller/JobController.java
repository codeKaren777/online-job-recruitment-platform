package com.groupcc2.recruitment.job.controller;

import com.groupcc2.recruitment.job.dto.JobRequest;
import com.groupcc2.recruitment.job.dto.JobResponse;
import com.groupcc2.recruitment.job.dto.PageResponse;
import com.groupcc2.recruitment.job.entity.JobStatus;
import com.groupcc2.recruitment.job.service.JobService;
import com.groupcc2.recruitment.users.entity.UserRole;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
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
    public ResponseEntity<JobResponse> create(@Valid @RequestBody JobRequest request,
                                              @AuthenticationPrincipal Jwt jwt) {
        JobResponse created = jobService.createJob(userId(jwt), userRole(jwt), request);
        return ResponseEntity.created(URI.create("/api/jobs/" + created.id())).body(created);
    }

    @GetMapping("/jobs")
    @PreAuthorize("hasRole('JOB_SEEKER')")
    public PageResponse<JobResponse> browse(@RequestParam(required = false) String location,
                                            @RequestParam(required = false, name = "q") String text,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size,
                                            @AuthenticationPrincipal Jwt jwt) {
        return jobService.browseJobs(userId(jwt), userRole(jwt), location, text, page, size);
    }

    @GetMapping("/jobs/{jobId}")
    @PreAuthorize("hasAnyRole('JOB_SEEKER','EMPLOYER')")
    public JobResponse get(@PathVariable UUID jobId, @AuthenticationPrincipal Jwt jwt) {
        return jobService.getJob(jobId, userId(jwt), userRole(jwt));
    }

    @GetMapping("/employer/jobs")
    @PreAuthorize("hasRole('EMPLOYER')")
    public PageResponse<JobResponse> myJobs(@RequestParam(required = false) JobStatus status,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size,
                                            @AuthenticationPrincipal Jwt jwt) {
        return jobService.getEmployerJobs(userId(jwt), userRole(jwt), status, page, size);
    }

    @PutMapping("/jobs/{jobId}")
    @PreAuthorize("hasRole('EMPLOYER')")
    public JobResponse update(@PathVariable UUID jobId, @Valid @RequestBody JobRequest request,
                              @AuthenticationPrincipal Jwt jwt) {
        return jobService.updateJob(userId(jwt), userRole(jwt), jobId, request);
    }

    @PatchMapping("/jobs/{jobId}/close")
    @PreAuthorize("hasRole('EMPLOYER')")
    public JobResponse close(@PathVariable UUID jobId, @AuthenticationPrincipal Jwt jwt) {
        return jobService.closeJob(userId(jwt), userRole(jwt), jobId);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static UserRole userRole(Jwt jwt) {
        return UserRole.valueOf(jwt.getClaimAsString("role"));
    }
}
