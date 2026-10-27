package com.groupcc2.recruitment.company.service;

import com.groupcc2.recruitment.job.service.CompanyDirectory;
import com.groupcc2.recruitment.job.service.CompanyReference;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class CompanyDirectoryAdapter implements CompanyDirectory {
    private final JdbcTemplate jdbc;

    CompanyDirectoryAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public java.util.Optional<CompanyReference> findOwnedByEmployer(UUID companyId, UUID employerId) {
        return jdbc.query("SELECT id, employer_id FROM recruitment.companies WHERE id = ? AND employer_id = ?",
                (rs, row) -> new CompanyReference(rs.getObject("id", UUID.class),
                        rs.getObject("employer_id", UUID.class)), companyId, employerId).stream().findFirst();
    }
}
