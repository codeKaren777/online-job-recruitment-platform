package com.groupcc2.recruitment.security;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSecurityConfigurationTest {
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[] {
            13, 27, 41, 55, 69, 83, 97, 111, 125, -117, -103, -87, -73, -59, -45, -31,
            17, 34, 51, 68, 85, 102, 119, -120, -103, -86, -69, -52, -35, -18, -1, 9 });

    @Test void acceptsStrongProductionSettings() {
        assertThatCode(() -> new ProductionSecurityConfiguration(new MockEnvironment(), KEY,
                false, false, true, "https://jobs.example.com")).doesNotThrowAnyException();
    }

    @Test void rejectsWeakKeyOpenApiDisabledLimitsWildcardCorsAndLocalProfile() {
        assertThatThrownBy(() -> new ProductionSecurityConfiguration(new MockEnvironment(), "AA==",
                false, false, true, "https://jobs.example.com")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ProductionSecurityConfiguration(new MockEnvironment(), KEY,
                true, false, true, "https://jobs.example.com")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ProductionSecurityConfiguration(new MockEnvironment(), KEY,
                false, false, false, "https://jobs.example.com")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ProductionSecurityConfiguration(new MockEnvironment(), KEY,
                false, false, true, "*")).isInstanceOf(IllegalStateException.class);
        MockEnvironment local = new MockEnvironment();
        local.setActiveProfiles("local");
        assertThatThrownBy(() -> new ProductionSecurityConfiguration(
                local, KEY, false, false, true,
                "https://jobs.example.com")).isInstanceOf(IllegalStateException.class);
    }
}
