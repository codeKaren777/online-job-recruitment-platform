package com.groupcc2.recruitment.auth;

import com.groupcc2.recruitment.auth.service.InvalidPasswordResetTokenException;
import com.groupcc2.recruitment.auth.service.PasswordResetTokenService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordResetTokenServiceTest {
    private final PasswordResetTokenService service = new PasswordResetTokenService();

    @Test
    void createsOpaqueHighEntropyTokenAndStoresOnlyItsOneWayHash() {
        String raw = service.generateToken();
        String hash = service.hashToken(raw);

        assertThat(raw).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(hash).hasSize(64).matches("[0-9a-f]+").isNotEqualTo(raw);
        assertThat(service.hashToken(raw)).isEqualTo(hash);
    }

    @Test
    void rejectsMalformedTokens() {
        for (String token : new String[] {"", "not-a-token", "x".repeat(44)}) {
            assertThatThrownBy(() -> service.hashToken(token))
                    .isInstanceOf(InvalidPasswordResetTokenException.class);
        }
    }
}
