package com.groupcc2.recruitment.application.service;

import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Sets the transaction-local PostgreSQL settings that the row-level-security policies read
 * (see docs/database.md). The values come from the authenticated user and their persisted role,
 * never from request data.
 *
 * Unlike ProfileRls this does not refuse superuser connections, so the feature runs with the
 * bootstrap Compose credentials. Those credentials bypass RLS entirely; the service-layer checks are
 * then the only enforcement. See "Open dependencies" in docs/application-module.md.
 */
@Component
public class ApplicationRls {
    private final EntityManager entityManager;

    public ApplicationRls(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void establish(UUID userId, String role) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("RLS settings require an active transaction");
        }
        entityManager.createNativeQuery("SELECT set_config('app.user_id', :id, true), set_config('app.role', :role, true)")
                .setParameter("id", userId.toString())
                .setParameter("role", role)
                .getSingleResult();
    }
}
