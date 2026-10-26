package com.groupcc2.recruitment.application.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;

import com.groupcc2.recruitment.application.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {
    boolean existsByJobIdAndJobSeekerId(UUID jobId, UUID jobSeekerId);

    List<Application> findAllByJobSeekerIdOrderBySubmittedAtDesc(UUID jobSeekerId);

    /** Ownership is part of the query: another seeker's application id is simply "not found". */
    Optional<Application> findByIdAndJobSeekerId(UUID id, UUID jobSeekerId);

    List<Application> findAllByJobIdInOrderBySubmittedAtDesc(Collection<UUID> jobIds);

    List<Application> findAllByJobIdInAndStatusOrderBySubmittedAtDesc(Collection<UUID> jobIds,
                                                                        com.groupcc2.recruitment.application.entity.ApplicationStatus status);

    List<Application> findAllByJobIdOrderBySubmittedAtDesc(UUID jobId);

    List<Application> findAllByJobIdAndStatusOrderBySubmittedAtDesc(UUID jobId,
                                                                      com.groupcc2.recruitment.application.entity.ApplicationStatus status);

    Optional<Application> findByIdAndJobIdIn(UUID id, Collection<UUID> jobIds);
}
