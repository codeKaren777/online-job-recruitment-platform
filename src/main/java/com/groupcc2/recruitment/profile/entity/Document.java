package com.groupcc2.recruitment.profile.entity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import com.groupcc2.recruitment.profile.dto.DocumentResponse;
@Entity @Table(name="documents",schema="recruitment")
public class Document {
 @Id private UUID id;
 @Column(name="profile_user_id",nullable=false) private UUID profileUserId;
 @Column(name="storage_key",nullable=false,columnDefinition="text") private String storageKey;
 @Column(name="original_filename",nullable=false,columnDefinition="text") private String originalFilename;
 @Column(name="content_type",nullable=false,columnDefinition="text") private String contentType;
 @Column(name="byte_size",nullable=false) private long byteSize;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 protected Document() {}
 public Document(UUID owner, String key, String filename, String type, long size) {
 id=UUID.randomUUID();profileUserId=owner;storageKey=key;originalFilename=filename;contentType=type;byteSize=size;createdAt=Instant.now();}
 public String storageKey() {return storageKey;}
 public DocumentResponse response() {return new DocumentResponse(id,originalFilename,contentType,byteSize,createdAt);}
}
