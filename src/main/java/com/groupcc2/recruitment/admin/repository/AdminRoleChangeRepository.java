package com.groupcc2.recruitment.admin.repository;

import com.groupcc2.recruitment.admin.entity.AdminRoleChange;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminRoleChangeRepository extends JpaRepository<AdminRoleChange, UUID> {
}
