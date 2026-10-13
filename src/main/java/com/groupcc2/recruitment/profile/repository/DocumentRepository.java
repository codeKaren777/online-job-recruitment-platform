package com.groupcc2.recruitment.profile.repository;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.groupcc2.recruitment.profile.entity.Document;
public interface DocumentRepository extends JpaRepository<Document,UUID> {
 List<Document> findAllByProfileUserIdOrderByCreatedAtDesc(UUID owner);
 Optional<Document> findByIdAndProfileUserId(UUID id, UUID owner);
}
