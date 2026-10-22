package com.groupcc2.recruitment.profile.service;
import java.util.UUID;
/** Resolves the user UUID from validated authentication only. */
public interface ProfileIdentity { UUID authenticatedUserId(); }
