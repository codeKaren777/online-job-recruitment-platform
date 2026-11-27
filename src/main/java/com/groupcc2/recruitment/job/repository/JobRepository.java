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

    /** Job seekers can inspect currently active jobs and explicitly closed vacancies. */
    @Query("select j from Job j where j.id = :id and "
            + "((j.status = com.groupcc2.recruitment.job.entity.JobStatus.ACTIVE and j.dueDate > local_date) "
            + "or j.status = com.groupcc2.recruitment.job.entity.JobStatus.CLOSED)")
    Optional<Job> findVisibleToJobSeekerById(@Param("id") UUID id);
}
