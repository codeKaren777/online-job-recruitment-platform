package com.groupcc2.recruitment.job.repository;

import com.groupcc2.recruitment.job.entity.Job;
import com.groupcc2.recruitment.job.entity.JobStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobRepository extends JpaRepository<Job, UUID>, JpaSpecificationExecutor<Job> {

    /** Ownership is enforced in the query itself: someone else's job simply isn't found. */
    Optional<Job> findByIdAndEmployerId(UUID id, UUID employerId);

    /**
     * Public lookup for job seekers. Uses the database's own current date (local_date) so the
     * rule "due date of today or earlier is expired" matches the DB policy and view exactly.
     */
    @Query("select j from Job j where j.id = :id and j.status = :status and j.dueDate > local_date")
    Optional<Job> findPublicById(@Param("id") UUID id, @Param("status") JobStatus status);
}
