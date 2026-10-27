package com.groupcc2.recruitment.users.employer.repository;

import com.groupcc2.recruitment.users.employer.entity.EmployerRequestStatus;
import com.groupcc2.recruitment.users.employer.entity.EmployerUpgradeRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployerUpgradeRequestRepository extends JpaRepository<EmployerUpgradeRequest, UUID> {
    List<EmployerUpgradeRequest> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
    List<EmployerUpgradeRequest> findAllByStatusOrderByCreatedAtAsc(EmployerRequestStatus status);
    Optional<EmployerUpgradeRequest> findByIdAndUserId(UUID id, UUID userId);
    boolean existsByUserIdAndStatus(UUID userId, EmployerRequestStatus status);
}
