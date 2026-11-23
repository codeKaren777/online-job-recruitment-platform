package com.groupcc2.recruitment.security;

import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/** Fails startup when production is configured with development or weak security settings. */
@Configuration
@Profile("prod")
public class ProductionSecurityConfiguration {
    public ProductionSecurityConfiguration(
            Environment environment,
            @Value("${app.security.jwt.secret-base64}") String jwtSecret,
            @Value("${app.openapi.enabled:false}") boolean openApiEnabled,
            @Value("${app.openapi.public:false}") boolean openApiPublic,
            @Value("${app.rate-limit.enabled:true}") boolean rateLimitEnabled,
            @Value("${app.security.cors.allowed-origin-patterns}") String corsOrigins) {
        if (environment.acceptsProfiles(org.springframework.core.env.Profiles.of("local"))) {
            throw new IllegalStateException("The prod and local profiles cannot be active together");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(jwtSecret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Production JWT secret must be Base64 encoded", exception);
        }
        if (key.length < 32 || allZero(key)) {
            throw new IllegalStateException("Production JWT secret must contain at least 32 random bytes");
        }
        if (openApiEnabled || openApiPublic) throw new IllegalStateException("OpenAPI must be disabled in production");
        if (!rateLimitEnabled) throw new IllegalStateException("Rate limiting must be enabled in production");
        if (corsOrigins == null || corsOrigins.isBlank() || corsOrigins.contains("*")) {
            throw new IllegalStateException("Production CORS origins must be explicit and must not use wildcards");
        }
    }

    private static boolean allZero(byte[] bytes) {
        int combined = 0;
        for (byte value : bytes) combined |= value;
        return combined == 0;
    }
}
