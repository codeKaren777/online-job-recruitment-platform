package com.groupcc2.recruitment.job.service;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.groupcc2.recruitment.application.service.JobDirectory;
import com.groupcc2.recruitment.application.service.JobSnapshot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

/**
 * Lets the Applications module look up job postings without depending on the Jobs entities.
 *
 * Reads recruitment.current_jobs, so an ACTIVE posting whose due date has been reached is reported as
 * EXPIRED even before the stored status is updated (same rule as JobResponse.from). It runs inside the
 * caller's transaction, so the row-level-security settings the caller established still apply.
 */
@Component
class JobDirectoryAdapter implements JobDirectory {
    private static final String SELECT =
            "SELECT id, title, location, due_date, status::text AS state FROM recruitment.current_jobs";

    private static final RowMapper<JobSnapshot> MAPPER = (rs, row) -> new JobSnapshot(
            rs.getObject("id", UUID.class),
            rs.getString("title"),
            rs.getString("location"),
            rs.getDate("due_date").toLocalDate(),
            JobSnapshot.State.valueOf(rs.getString("state")));

    private final JdbcTemplate jdbc;

    JobDirectoryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<JobSnapshot> findById(UUID jobId) {
        return jdbc.query(SELECT + " WHERE id = ?", MAPPER, jobId).stream().findFirst();
    }

    @Override
    public Map<UUID, JobSnapshot> findAllById(Collection<UUID> jobIds) {
        if (jobIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", Collections.nCopies(jobIds.size(), "?"));
        Map<UUID, JobSnapshot> found = new HashMap<>();
        jdbc.query(SELECT + " WHERE id IN (" + placeholders + ")", MAPPER, jobIds.toArray())
                .forEach(job -> found.put(job.id(), job));
        return found;
    }

    @Override
    public Optional<JobSnapshot> findOwnedByEmployer(UUID jobId, UUID employerId) {
        return jdbc.query(SELECT + " WHERE id = ? AND employer_id = ?", MAPPER, jobId, employerId)
                .stream().findFirst();
    }

    @Override
    public Map<UUID, JobSnapshot> findAllForEmployer(UUID employerId) {
        Map<UUID, JobSnapshot> found = new HashMap<>();
        jdbc.query(SELECT + " WHERE employer_id = ?", MAPPER, employerId)
                .forEach(job -> found.put(job.id(), job));
        return found;
    }
}
