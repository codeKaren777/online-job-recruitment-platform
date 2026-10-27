package com.groupcc2.recruitment.company.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "companies", schema = "recruitment")
public class Company {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "employer_id", nullable = false, updatable = false)
    private UUID employerId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "contact_email", columnDefinition = "citext")
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    private String location;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Company() {
    }

    public Company(UUID employerId, String name, String description, String contactEmail,
                   String contactPhone, String location) {
        this.employerId = employerId;
        update(name, description, contactEmail, contactPhone, location);
    }

    public void update(String name, String description, String contactEmail,
                       String contactPhone, String location) {
        this.name = name;
        this.description = description;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.location = location;
    }

    public UUID getId() { return id; }
    public UUID getEmployerId() { return employerId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getContactEmail() { return contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public String getLocation() { return location; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
