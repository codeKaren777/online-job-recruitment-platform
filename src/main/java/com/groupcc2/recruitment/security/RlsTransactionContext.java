package com.groupcc2.recruitment.security;

import java.util.UUID;

import com.groupcc2.recruitment.users.entity.UserRole;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class RlsTransactionContext {
    private final JdbcTemplate jdbcTemplate;

    public RlsTransactionContext(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void setAuthenticatedUser(UUID userId, UserRole role) {
        if (role == null) {
            throw new IllegalArgumentException("A verified user role is required");
        }
        setContext(userId, role.name());
    }

    public void setSelfOnlyUser(UUID userId) {
        setContext(userId, "");
    }

    private void setContext(UUID userId, String role) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("RLS identity must be set inside an active database transaction");
        }
        if (userId == null) {
            throw new IllegalArgumentException("A verified user ID is required");
        }

        jdbcTemplate.queryForObject(
                "SELECT set_config('app.user_id', ?, true), set_config('app.role', ?, true)",
                (resultSet, rowNumber) -> resultSet.getString(1),
                userId.toString(), role);
    }
}
