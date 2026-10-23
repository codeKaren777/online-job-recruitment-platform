package com.groupcc2.recruitment.profile.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StorageConfigurationTest {
    private static final String ENDPOINT = "http://localhost:9000";

    @Test
    void acceptsCompletePrivateStorageSettings() {
        StorageConfiguration.Settings settings = StorageConfiguration.validateSettings(
                ENDPOINT, ENDPOINT, "us-east-1", "recruitment-documents",
                "document-service", "secret-value", true);

        assertThat(settings.endpoint().toString()).isEqualTo(ENDPOINT);
        assertThat(settings.bucket()).isEqualTo("recruitment-documents");
        assertThat(settings.pathStyle()).isTrue();
    }

    @Test
    void rejectsMissingCredentialsAndPlaceholders() {
        assertThatThrownBy(() -> StorageConfiguration.validateSettings(
                ENDPOINT, ENDPOINT, "us-east-1", "recruitment-documents", "", "secret", true))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> StorageConfiguration.validateSettings(
                ENDPOINT, ENDPOINT, "us-east-1", "recruitment-documents",
                "access", "REPLACE_WITH_SECRET", true))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsUnsafeEndpointsAndBucketNames() {
        assertThatThrownBy(() -> StorageConfiguration.validateSettings(
                "http://localhost:9000/path", ENDPOINT, "us-east-1",
                "recruitment-documents", "access", "secret", true))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> StorageConfiguration.validateSettings(
                ENDPOINT, ENDPOINT, "us-east-1", "bad..bucket",
                "access", "secret", true))
                .isInstanceOf(IllegalStateException.class);
    }
}
