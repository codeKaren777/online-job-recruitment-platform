package com.groupcc2.recruitment.profile.service;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
@Component
public class ProfileRls {
 private final EntityManager em;
 public ProfileRls(EntityManager em) {this.em=em;}
 public void establish(UUID id, String role) {
  if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("RLS requires a transaction");
  boolean bypass=(Boolean) em.createNativeQuery("SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname=current_user").getSingleResult();
  if (bypass) throw new ProfileFailure(ProfileFailure.Code.INTEGRATION_UNAVAILABLE);
  em.createNativeQuery("SELECT set_config('app.user_id', :id, true), set_config('app.role', :role, true)")
   .setParameter("id",id.toString()).setParameter("role",role).getSingleResult();
 }
}
