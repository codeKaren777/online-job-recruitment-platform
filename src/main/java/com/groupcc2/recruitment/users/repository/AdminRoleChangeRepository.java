package com.groupcc2.recruitment.users.repository;

import com.groupcc2.recruitment.users.entity.AdminRoleChange;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminRoleChangeRepository extends JpaRepository<AdminRoleChange, UUID> {
}
