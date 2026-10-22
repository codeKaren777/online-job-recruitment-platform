package com.groupcc2.recruitment.application.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.groupcc2.recruitment.application.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {
    boolean existsByJobIdAndJobSeekerId(UUID jobId, UUID jobSeekerId);

    List<Application> findAllByJobSeekerIdOrderBySubmittedAtDesc(UUID jobSeekerId);

    /** Ownership is part of the query: another seeker's application id is simply "not found". */
    Optional<Application> findByIdAndJobSeekerId(UUID id, UUID jobSeekerId);
}
