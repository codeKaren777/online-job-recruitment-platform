package com.groupcc2.recruitment.profile.storage;
import java.nio.file.Path;
import java.time.Duration;
import java.net.URI;
public interface PrivateDocumentStorage {
 void upload(String key, Path file, String contentType);
 void delete(String key);
 URI signGet(String key, Duration expiry);
}
