package com.groupcc2.recruitment.profile.dto;

import java.net.URI;
import java.time.Instant;

/** A private picture URL is a short-lived bearer link; request another response to renew it. */
public record ProfilePictureResponse(boolean hasPicture, URI url, Instant expiresAt) {
    public static ProfilePictureResponse absent() {
        return new ProfilePictureResponse(false, null, null);
    }
}
