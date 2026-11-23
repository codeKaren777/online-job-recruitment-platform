package com.groupcc2.recruitment.common.exception;

/** Raised by a rate-limiting integration when the caller exceeds its request quota. */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException() { super("Request rate limit exceeded"); }
}
