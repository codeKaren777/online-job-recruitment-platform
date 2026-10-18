package com.groupcc2.recruitment.security;

import java.util.UUID;

import com.groupcc2.recruitment.users.entity.AccountStatus;
import com.groupcc2.recruitment.users.entity.UserRole;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class AccountJwtValidator implements OAuth2TokenValidator<Jwt> {
    private static final OAuth2Error INVALID_ACCOUNT_TOKEN =
            new OAuth2Error("invalid_token", "Token is no longer valid", null);

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final RlsTransactionContext rlsTransactionContext;

    public AccountJwtValidator(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            RlsTransactionContext rlsTransactionContext) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.rlsTransactionContext = rlsTransactionContext;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        UUID userId;
        UserRole claimedRole;
        int tokenVersion;
        try {
            userId = UUID.fromString(jwt.getSubject());
            claimedRole = UserRole.valueOf(jwt.getClaimAsString("role"));
            Object rawVersion = jwt.getClaims().get("tokenVersion");
            if (!(rawVersion instanceof Number version)
                    || version.longValue() < 0
                    || version.longValue() > Integer.MAX_VALUE
                    || version.doubleValue() != version.longValue()) {
                return OAuth2TokenValidatorResult.failure(INVALID_ACCOUNT_TOKEN);
            }
            tokenVersion = version.intValue();
        } catch (IllegalArgumentException | NullPointerException exception) {
            return OAuth2TokenValidatorResult.failure(INVALID_ACCOUNT_TOKEN);
        }

        AccountState current = transactionTemplate.execute(status -> {
            rlsTransactionContext.setAuthenticatedUser(userId, claimedRole);
            return jdbcTemplate.query("""
                            SELECT role::text, status::text, token_version
                            FROM recruitment.users
                            WHERE id = ?
                            """,
                    resultSet -> resultSet.next()
                            ? new AccountState(
                                    resultSet.getString("role"),
                                    resultSet.getString("status"),
                                    resultSet.getInt("token_version"))
                            : null,
                    userId);
        });
        if (current != null
                && current.status().equals(AccountStatus.ACTIVE.name())
                && current.role().equals(claimedRole.name())
                && current.tokenVersion() == tokenVersion) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(INVALID_ACCOUNT_TOKEN);
    }

    private record AccountState(String role, String status, int tokenVersion) {
    }
}
