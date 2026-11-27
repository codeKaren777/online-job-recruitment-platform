package com.groupcc2.recruitment.profile.dto;
import java.time.Instant;
import java.time.LocalDate;
public record ProfileResponse(String displayName, String fullName, LocalDate dateOfBirth,
                              String phone, String location, String biography,
                              Instant createdAt, Instant updatedAt, boolean profileImageAvailable) {
 public ProfileResponse(String displayName, String fullName, LocalDate dateOfBirth,
                        String phone, String location, String biography,
                        Instant createdAt, Instant updatedAt) {
  this(displayName, fullName, dateOfBirth, phone, location, biography, createdAt, updatedAt, false);
 }
 public ProfileResponse(String displayName, String phone, String biography,
                        Instant createdAt, Instant updatedAt) {
  this(displayName, displayName, null, phone, null, biography, createdAt, updatedAt, false);
 }
}
