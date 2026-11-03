package com.groupcc2.recruitment.profile.repository;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.groupcc2.recruitment.profile.entity.Document;
public interface DocumentRepository extends JpaRepository<Document,UUID> {
 List<Document> findAllByProfileUserIdOrderByCreatedAtDesc(UUID owner);
 Optional<Document> findByIdAndProfileUserId(UUID id, UUID owner);
 @org.springframework.data.jpa.repository.Query(value = "select exists(select 1 from recruitment.applications where cv_document_id = :id)", nativeQuery = true)
 boolean existsApplicationReference(@org.springframework.data.repository.query.Param("id") UUID id);
}
