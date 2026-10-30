package com.groupcc2.recruitment.profile.service;

import java.util.UUID;

/** Profile-owned contract for checking application eligibility without exposing profile data. */
public interface JobSeekerProfileAccess {
    boolean hasProfile(UUID jobSeekerId);
}
