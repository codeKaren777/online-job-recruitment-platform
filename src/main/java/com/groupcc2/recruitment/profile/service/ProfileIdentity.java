package com.groupcc2.recruitment.profile.service;
import java.util.UUID;
/** Integration contract: return the user UUID from validated authentication only.
 * No implementation until the authentication owner confirms JWT subject mapping. */
public interface ProfileIdentity { UUID authenticatedUserId(); }
