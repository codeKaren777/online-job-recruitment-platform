package com.groupcc2.recruitment.admin.entity;

import java.time.Instant;
import java.util.UUID;

import com.groupcc2.recruitment.users.entity.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "admin_role_changes", schema = "recruitment")
public class AdminRoleChange {
    @Id
    private UUID id;

    @Column(name = "administrator_id", nullable = false)
    private UUID administratorId;

    @Column(name = "target_user_id", nullable = false)
    private UUID targetUserId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "previous_role", nullable = false, columnDefinition = "recruitment.user_role")
    private UserRole previousRole;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "new_role", nullable = false, columnDefinition = "recruitment.user_role")
    private UserRole newRole;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    protected AdminRoleChange() {
    }

    public AdminRoleChange(UUID administratorId, UUID targetUserId, UserRole previousRole, UserRole newRole) {
        this.id = UUID.randomUUID();
        this.administratorId = administratorId;
        this.targetUserId = targetUserId;
        this.previousRole = previousRole;
        this.newRole = newRole;
    }

    @PrePersist
    void initializeTimestamp() {
        if (changedAt == null) {
            changedAt = Instant.now();
        }
    }
}
