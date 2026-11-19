package com.groupcc2.recruitment.application.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.groupcc2.recruitment.application.entity.ShortlistCandidate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShortlistCandidateRepository extends JpaRepository<ShortlistCandidate, UUID> {
    Page<ShortlistCandidate> findAllByEmployerId(UUID employerId, Pageable pageable);
    Optional<ShortlistCandidate> findByIdAndEmployerId(UUID id, UUID employerId);
}
