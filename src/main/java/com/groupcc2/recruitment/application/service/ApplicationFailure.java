package com.groupcc2.recruitment.application.service;

public class ApplicationFailure extends RuntimeException {
    public enum Code {
        FORBIDDEN,
        NOT_FOUND,
        DUPLICATE_APPLICATION,
        JOB_NOT_ACCEPTING_APPLICATIONS,
        INVALID_STATUS_TRANSITION,
        INTEGRATION_UNAVAILABLE
    }

    private final Code code;

    public ApplicationFailure(Code code) {
        super(code.name());
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
