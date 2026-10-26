package com.groupcc2.recruitment.users.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "users", schema = "recruitment")
public class User {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, columnDefinition = "citext")
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "recruitment.user_role")
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "recruitment.account_status")
    private AccountStatus accountStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    @Column(name = "password_reset_token_hash")
    private String passwordResetTokenHash;

    @Column(name = "password_reset_expires_at")
    private Instant passwordResetExpiresAt;

    protected User() {
    }

    public User(String email, String passwordHash, UserRole role) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.accountStatus = AccountStatus.ACTIVE;
    }

    @PrePersist
    void initializeTimestamps() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public UserRole getRole() {
        return role;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    public void promoteToAdministrator() {
        if (role == UserRole.ADMIN) {
            throw new IllegalStateException("User is already an administrator");
        }
        if (!canAuthenticate()) {
            throw new IllegalStateException("Only active users can become administrators");
        }
        role = UserRole.ADMIN;
        tokenVersion++;
    }

    public void suspend() {
        if (accountStatus == AccountStatus.REMOVED) {
            throw new IllegalStateException("Removed accounts cannot be suspended");
        }
        accountStatus = AccountStatus.SUSPENDED;
    }

    public void remove() {
        accountStatus = AccountStatus.REMOVED;
    }

    public boolean canAuthenticate() {
        return accountStatus == AccountStatus.ACTIVE;
    }

    /** Internal authentication contract; API DTOs never expose this value. */
    public String passwordHashForAuthentication() {
        return passwordHash;
    }
}
