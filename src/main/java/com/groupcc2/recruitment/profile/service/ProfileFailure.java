package com.groupcc2.recruitment.profile.service;
public class ProfileFailure extends RuntimeException {
 public enum Code { FORBIDDEN, NOT_FOUND, INVALID_FILE, FILE_TOO_LARGE, STORAGE_UNAVAILABLE, INTEGRATION_UNAVAILABLE }
 private final Code code;
 public ProfileFailure(Code code) {super(code.name());this.code=code;}
 public Code code() {return code;}
}
