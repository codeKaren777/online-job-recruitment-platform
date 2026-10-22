package com.groupcc2.recruitment.application.service;

import java.time.LocalDate;
import java.util.UUID;

/** Read-only view of a job posting, supplied by the Jobs module through {@link JobDirectory}. */
public record JobSnapshot(UUID id, String title, String location, LocalDate dueDate, State state) {
    public enum State { ACTIVE, EXPIRED, CLOSED }

    /** BR-02: a posting stops accepting applications once its due date is reached. */
    public boolean acceptsApplications(LocalDate today) {
        return state == State.ACTIVE && dueDate.isAfter(today);
    }
}
