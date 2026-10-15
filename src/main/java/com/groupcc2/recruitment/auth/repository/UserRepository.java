package com.groupcc2.recruitment.auth.repository;

import java.util.Optional;
import java.util.UUID;

import com.groupcc2.recruitment.auth.dto.CurrentUserResponse;
import com.groupcc2.recruitment.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UserRepository extends JpaRepository<User, UUID> {
    interface LoginCredential {
        UUID getId();

        String getPasswordHash();

        String getRole();

        String getStatus();

        int getTokenVersion();
    }

    interface RegistrationResult {
        UUID getId();

        String getEmail();

        String getRole();

        String getStatus();
    }

    @Query(value = """
            SELECT id, password_hash, role, status, token_version AS "tokenVersion"
            FROM recruitment.auth_lookup_user(:email)
            """, nativeQuery = true)
    Optional<LoginCredential> findLoginCredentialsByEmail(@Param("email") String email);

    @Query(value = """
            SELECT id, password_hash, role, status, token_version AS "tokenVersion"
            FROM recruitment.users
            WHERE id = :id
            """, nativeQuery = true)
    Optional<LoginCredential> findLoginCredentialsById(@Param("id") UUID id);

    @Query(value = """
            SELECT id, email, role, status
            FROM recruitment.auth_register_user(:email, :passwordHash, :role)
            """, nativeQuery = true)
    Optional<RegistrationResult> registerUser(
            @Param("email") String email,
            @Param("passwordHash") String passwordHash,
            @Param("role") String role);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE recruitment.users
            SET token_version = token_version + 1
            WHERE id = :id AND token_version = :tokenVersion AND status = 'ACTIVE'
            """, nativeQuery = true)
    int revokeSessions(@Param("id") UUID id, @Param("tokenVersion") int tokenVersion);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE recruitment.users
            SET password_hash = :newPasswordHash, token_version = token_version + 1
            WHERE id = :id AND password_hash = :currentPasswordHash
              AND token_version = :tokenVersion AND status = 'ACTIVE'
            """, nativeQuery = true)
    int changePassword(
            @Param("id") UUID id,
            @Param("currentPasswordHash") String currentPasswordHash,
            @Param("newPasswordHash") String newPasswordHash,
            @Param("tokenVersion") int tokenVersion);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE recruitment.users
            SET password_reset_token_hash = :tokenHash,
                password_reset_expires_at = :expiresAt
            WHERE email = :email AND status = 'ACTIVE'
            """, nativeQuery = true)
    int storePasswordResetToken(
            @Param("email") String email,
            @Param("tokenHash") String tokenHash,
            @Param("expiresAt") java.time.Instant expiresAt);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE recruitment.users
            SET password_hash = :newPasswordHash,
                token_version = token_version + 1,
                password_reset_token_hash = NULL,
                password_reset_expires_at = NULL
            WHERE password_reset_token_hash = :tokenHash
              AND password_reset_expires_at > CURRENT_TIMESTAMP
              AND status = 'ACTIVE'
            """, nativeQuery = true)
    int resetPassword(
            @Param("tokenHash") String tokenHash,
            @Param("newPasswordHash") String newPasswordHash);

    @Query("""
            SELECT new com.groupcc2.recruitment.auth.dto.CurrentUserResponse(
                u.id, u.email, u.role, u.status)
            FROM User u
            WHERE u.id = :id
            """)
    Optional<CurrentUserResponse> findCurrentUserById(@Param("id") UUID id);
}
