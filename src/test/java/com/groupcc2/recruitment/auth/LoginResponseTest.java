package com.groupcc2.recruitment.auth;

import com.groupcc2.recruitment.auth.dto.LoginResponse;
import com.groupcc2.recruitment.auth.service.JwtTokenService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginResponseTest {
    @Test
    void representsAccessTokenLifetimeInSeconds() {
        LoginResponse response = new LoginResponse(
                "signed-token", "Bearer", JwtTokenService.ACCESS_TOKEN_LIFETIME_SECONDS);

        assertThat(response.accessToken()).isEqualTo("signed-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(1800);
    }
}
