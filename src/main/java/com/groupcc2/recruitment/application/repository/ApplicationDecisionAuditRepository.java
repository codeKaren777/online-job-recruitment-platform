package com.groupcc2.recruitment.application.repository;

import java.util.UUID;

import com.groupcc2.recruitment.application.entity.ApplicationDecisionAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationDecisionAuditRepository extends JpaRepository<ApplicationDecisionAudit, UUID> {
}
