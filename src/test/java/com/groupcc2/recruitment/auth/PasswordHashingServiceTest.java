package com.groupcc2.recruitment.auth;

import com.groupcc2.recruitment.auth.service.PasswordHashingService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordHashingServiceTest {
    private final PasswordHashingService service =
            new PasswordHashingService(new BCryptPasswordEncoder());

    @Test
    void hashesPasswordAndVerifiesItWithoutStoringPlaintext() {
        String rawPassword = "StrongPassword123!";

        String passwordHash = service.hash(rawPassword);

        assertThat(passwordHash).isNotEqualTo(rawPassword).startsWith("$2");
        assertThat(service.matches(rawPassword, passwordHash)).isTrue();
    }

    @Test
    void doesNotMatchAnIncorrectPassword() {
        String passwordHash = service.hash("StrongPassword123!");

        assertThat(service.matches("DifferentPassword123!", passwordHash)).isFalse();
    }

    @Test
    void rejectsPasswordsThatExceedBcryptUtf8ByteLimit() {
        String tooLong = "é".repeat(37);

        assertThat(tooLong.length()).isLessThanOrEqualTo(72);
        assertThatThrownBy(() -> service.hash(tooLong))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.matches(tooLong, service.hash("StrongPassword123!"))).isFalse();
    }
}
