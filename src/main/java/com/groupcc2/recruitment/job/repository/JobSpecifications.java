package com.groupcc2.recruitment.job.repository;

import com.groupcc2.recruitment.job.entity.Job;
import com.groupcc2.recruitment.job.entity.JobStatus;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class JobSpecifications {

    private JobSpecifications() {
    }

    public static Specification<Job> all() {
        return (root, query, cb) -> cb.conjunction();
    }

    public static Specification<Job> ownedBy(UUID employerId) {
        return (root, query, cb) -> cb.equal(root.get("employerId"), employerId);
    }

    /** ACTIVE and due date strictly after the database's current date. */
    public static Specification<Job> activeAndNotExpired() {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("status"), JobStatus.ACTIVE),
                cb.greaterThan(root.<LocalDate>get("dueDate"), cb.localDate()));
    }

    /**
     * Filter by effective status. The expiry scheduler may not have run yet, so a stored ACTIVE job
     * whose due date has arrived counts as EXPIRED.
     */
    public static Specification<Job> hasEffectiveStatus(JobStatus status) {
        if (status == null) {
            return all();
        }
        return (root, query, cb) -> {
            Path<JobStatus> stored = root.get("status");
            Expression<LocalDate> due = root.get("dueDate");
            Expression<LocalDate> today = cb.localDate();
            return switch (status) {
                case ACTIVE -> cb.and(cb.equal(stored, JobStatus.ACTIVE), cb.greaterThan(due, today));
                case EXPIRED -> cb.or(
                        cb.equal(stored, JobStatus.EXPIRED),
                        cb.and(cb.equal(stored, JobStatus.ACTIVE), cb.lessThanOrEqualTo(due, today)));
                case CLOSED -> cb.equal(stored, JobStatus.CLOSED);
            };
        };
    }

    public static Specification<Job> locationContains(String location) {
        if (location == null || location.isBlank()) {
            return all();
        }
        String pattern = like(location);
        return (root, query, cb) -> cb.like(cb.lower(root.get("location")), pattern, '\\');
    }

    /** Free-text search over title and description. */
    public static Specification<Job> textMatches(String text) {
        if (text == null || text.isBlank()) {
            return all();
        }
        String pattern = like(text);
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), pattern, '\\'),
                cb.like(cb.lower(root.get("description")), pattern, '\\'));
    }

    private static String like(String raw) {
        String escaped = raw.trim().toLowerCase()
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
