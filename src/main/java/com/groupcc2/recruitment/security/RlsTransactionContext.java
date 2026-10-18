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
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("RLS identity must be set inside an active database transaction");
        }
        if (userId == null || role == null) {
            throw new IllegalArgumentException("A verified user ID and role are required");
        }

        jdbcTemplate.queryForObject(
                "SELECT set_config('app.user_id', ?, true), set_config('app.role', ?, true)",
                (resultSet, rowNumber) -> resultSet.getString(1),
                userId.toString(), role.name());
    }
}
