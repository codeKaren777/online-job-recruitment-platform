package com.groupcc2.recruitment.profile.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class StorageConfigurationTest {
    private static final String ENDPOINT = "http://localhost:9000";
    private static final String PUBLIC_ENDPOINT = "http://localhost:9000";
    private static final String REGION = "us-east-1";
    private static final String BUCKET = "recruitment-documents";
    private static final String ACCESS_KEY = "profile-service";
    private static final String SECRET_KEY = "local-test-secret-value";

    @Test
    void acceptsValidPrivateStorageSettings() {
        StorageConfiguration.Settings settings = StorageConfiguration.validateSettings(
                ENDPOINT, PUBLIC_ENDPOINT, REGION, BUCKET, ACCESS_KEY, SECRET_KEY, true);

        assertThat(settings.endpoint().toString()).isEqualTo(ENDPOINT);
        assertThat(settings.publicEndpoint().toString()).isEqualTo(PUBLIC_ENDPOINT);
        assertThat(settings.bucket()).isEqualTo(BUCKET);
        assertThat(settings.pathStyle()).isTrue();
    }

    @Test
    void rejectsMissingCredentialsWithoutEchoingSecretValues() {
        assertThatThrownBy(() -> StorageConfiguration.validateSettings(
                ENDPOINT, PUBLIC_ENDPOINT, REGION, BUCKET, " ", SECRET_KEY, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PROFILE_STORAGE_ACCESS_KEY")
                .hasMessageNotContaining(SECRET_KEY);
        assertThatThrownBy(() -> StorageConfiguration.validateSettings(
                ENDPOINT, PUBLIC_ENDPOINT, REGION, BUCKET, ACCESS_KEY, "REPLACE_WITH_SECRET", true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PROFILE_STORAGE_SECRET_KEY");
    }

    @Test
    void rejectsInvalidEndpointAndBucketConfiguration() {
        assertThatThrownBy(() -> StorageConfiguration.validateSettings(
                "file:///tmp/minio", PUBLIC_ENDPOINT, REGION, BUCKET, ACCESS_KEY, SECRET_KEY, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PROFILE_STORAGE_ENDPOINT");
        assertThatThrownBy(() -> StorageConfiguration.validateSettings(
                ENDPOINT, PUBLIC_ENDPOINT, REGION, "Not_A_Bucket", ACCESS_KEY, SECRET_KEY, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PROFILE_STORAGE_BUCKET");
    }

    @Test
    void enabledStorageContextFailsClearlyWhenCredentialsAreMissing() {
        new ApplicationContextRunner()
                .withUserConfiguration(StorageConfiguration.class)
                .withPropertyValues(
                        "app.profile.storage.enabled=true",
                        "app.profile.storage.endpoint=" + ENDPOINT,
                        "app.profile.storage.public-endpoint=" + PUBLIC_ENDPOINT,
                        "app.profile.storage.region=" + REGION,
                        "app.profile.storage.bucket=" + BUCKET,
                        "app.profile.storage.access-key=",
                        "app.profile.storage.secret-key=" + SECRET_KEY)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure().toString())
                            .contains("PROFILE_STORAGE_ACCESS_KEY")
                            .doesNotContain(SECRET_KEY);
                });
    }

    @Test
    void enabledStorageContextRegistersPrivateStorageWithValidConfiguration() {
        new ApplicationContextRunner()
                .withUserConfiguration(StorageConfiguration.class)
                .withPropertyValues(
                        "app.profile.storage.enabled=true",
                        "app.profile.storage.endpoint=" + ENDPOINT,
                        "app.profile.storage.public-endpoint=" + PUBLIC_ENDPOINT,
                        "app.profile.storage.region=" + REGION,
                        "app.profile.storage.bucket=" + BUCKET,
                        "app.profile.storage.access-key=" + ACCESS_KEY,
                        "app.profile.storage.secret-key=" + SECRET_KEY)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(PrivateDocumentStorage.class);
                });
    }
}
