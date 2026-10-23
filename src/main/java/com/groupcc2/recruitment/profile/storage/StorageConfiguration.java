package com.groupcc2.recruitment.profile.storage;

import java.net.URI;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@ConditionalOnProperty(name = "app.profile.storage.enabled", havingValue = "true")
public class StorageConfiguration {
    @Bean
    Settings profileStorageSettings(
            @Value("${app.profile.storage.endpoint}") String endpoint,
            @Value("${app.profile.storage.public-endpoint}") String publicEndpoint,
            @Value("${app.profile.storage.region}") String region,
            @Value("${app.profile.storage.bucket}") String bucket,
            @Value("${app.profile.storage.access-key}") String accessKey,
            @Value("${app.profile.storage.secret-key}") String secretKey,
            @Value("${app.profile.storage.path-style:true}") boolean pathStyle) {
        return validateSettings(endpoint, publicEndpoint, region, bucket, accessKey, secretKey, pathStyle);
    }

    @Bean(destroyMethod = "close")
    S3Client profileS3Client(Settings settings) {
        return S3Client.builder()
                .endpointOverride(settings.endpoint())
                .region(Region.of(settings.region()))
                .credentialsProvider(settings.credentials())
                .overrideConfiguration(configuration -> configuration
                        .apiCallTimeout(Duration.ofSeconds(30))
                        .apiCallAttemptTimeout(Duration.ofSeconds(10)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(settings.pathStyle())
                        .build())
                .build();
    }

    @Bean(destroyMethod = "close")
    S3Presigner profilePresigner(Settings settings) {
        return S3Presigner.builder()
                .endpointOverride(settings.publicEndpoint())
                .region(Region.of(settings.region()))
                .credentialsProvider(settings.credentials())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(settings.pathStyle())
                        .build())
                .build();
    }

    @Bean
    PrivateDocumentStorage privateDocumentStorage(S3Client client, S3Presigner signer, Settings settings) {
        return new S3DocumentStorage(client, signer, settings.bucket());
    }

    static Settings validateSettings(String endpoint, String publicEndpoint, String region,
                                     String bucket, String accessKey, String secretKey, boolean pathStyle) {
        URI storageEndpoint = parseEndpoint("PROFILE_STORAGE_ENDPOINT", endpoint);
        URI signedUrlEndpoint = parseEndpoint("PROFILE_STORAGE_PUBLIC_ENDPOINT", publicEndpoint);
        if (region == null || !region.matches("[a-z0-9][a-z0-9-]{1,62}")) {
            throw invalid("PROFILE_STORAGE_REGION must be a nonblank region identifier");
        }
        if (bucket == null || bucket.length() < 3 || bucket.length() > 63
                || !bucket.matches("[a-z0-9][a-z0-9.-]*[a-z0-9]")
                || bucket.contains("..") || bucket.contains(".-") || bucket.contains("-.")) {
            throw invalid("PROFILE_STORAGE_BUCKET must be a valid 3-63 character S3 bucket name");
        }
        requireSecret("PROFILE_STORAGE_ACCESS_KEY", accessKey);
        requireSecret("PROFILE_STORAGE_SECRET_KEY", secretKey);
        return new Settings(storageEndpoint, signedUrlEndpoint, region, bucket, accessKey, secretKey, pathStyle);
    }

    private static URI parseEndpoint(String property, String value) {
        if (value == null || value.isBlank()) {
            throw invalid(property + " must be configured when profile storage is enabled");
        }
        try {
            URI endpoint = URI.create(value);
            String scheme = endpoint.getScheme();
            if (!endpoint.isAbsolute() || endpoint.getHost() == null
                    || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                    || endpoint.getUserInfo() != null || endpoint.getQuery() != null
                    || endpoint.getFragment() != null
                    || (endpoint.getPath() != null && !endpoint.getPath().isEmpty()
                    && !"/".equals(endpoint.getPath()))) {
                throw invalid(property + " must be an http(s) endpoint without credentials, path, or query");
            }
            return endpoint;
        } catch (IllegalArgumentException exception) {
            throw invalid(property + " must be a valid http(s) endpoint");
        }
    }

    private static void requireSecret(String property, String value) {
        if (value == null || value.isBlank() || value.startsWith("REPLACE_WITH_")) {
            throw invalid(property + " must be configured with a non-placeholder value when profile storage is enabled");
        }
    }

    private static IllegalStateException invalid(String message) {
        return new IllegalStateException(message);
    }

    record Settings(URI endpoint, URI publicEndpoint, String region, String bucket,
                    String accessKey, String secretKey, boolean pathStyle) {
        StaticCredentialsProvider credentials() {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        }
    }
}
