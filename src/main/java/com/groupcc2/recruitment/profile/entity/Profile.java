package com.groupcc2.recruitment.profile.entity;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import com.groupcc2.recruitment.profile.dto.*;
@Entity @Table(name="profiles", schema="recruitment")
public class Profile {
 @Id @Column(name="user_id") private UUID userId;
 @Column(name="display_name",nullable=false,columnDefinition="text") private String displayName;
 @Column(name="full_name",nullable=false,columnDefinition="text") private String fullName;
 @Column(name="date_of_birth") private LocalDate dateOfBirth;
 @Column(columnDefinition="text") private String phone;
 @Column(columnDefinition="text") private String location;
 @Column(columnDefinition="text") private String biography;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 protected Profile() {}
 public Profile(UUID userId, ProfileRequest request) {this.userId=userId; createdAt=Instant.now();update(request);}
 public void update(ProfileRequest r) {
  displayName=r.displayName().trim();
  fullName=(r.fullName()==null||r.fullName().isBlank()?displayName:r.fullName().trim());
  dateOfBirth=r.dateOfBirth();
  phone=r.phone();
  location=r.location();
  biography=r.biography();
  updatedAt=Instant.now();
 }
 public ProfileResponse response() {return new ProfileResponse(displayName,fullName,dateOfBirth,phone,location,biography,createdAt,updatedAt);}
 public UUID getUserId() {return userId;}
 public String displayName() {return displayName;}
}
