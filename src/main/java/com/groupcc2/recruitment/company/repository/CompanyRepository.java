package com.groupcc2.recruitment.company.repository;

import com.groupcc2.recruitment.company.entity.Company;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, UUID> {
    List<Company> findAllByEmployerIdOrderByName(UUID employerId);
    Optional<Company> findByIdAndEmployerId(UUID id, UUID employerId);
    boolean existsByEmployerIdAndName(UUID employerId, String name);
}
