package com.groupcc2.recruitment.profile.entity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import com.groupcc2.recruitment.profile.dto.*;
@Entity @Table(name="profiles", schema="recruitment")
public class Profile {
 @Id @Column(name="user_id") private UUID userId;
 @Column(name="display_name",nullable=false,columnDefinition="text") private String displayName;
 @Column(columnDefinition="text") private String phone;
 @Column(columnDefinition="text") private String biography;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 protected Profile() {}
 public Profile(UUID userId, ProfileRequest request) {this.userId=userId; createdAt=Instant.now();update(request);}
 public void update(ProfileRequest r) {displayName=r.displayName().trim();phone=r.phone();biography=r.biography();updatedAt=Instant.now();}
 public ProfileResponse response() {return new ProfileResponse(displayName,phone,biography,createdAt,updatedAt);}
}
