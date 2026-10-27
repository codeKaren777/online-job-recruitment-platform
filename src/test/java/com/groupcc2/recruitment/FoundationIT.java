package com.groupcc2.recruitment;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import com.groupcc2.recruitment.auth.dto.CurrentUserResponse;
import com.groupcc2.recruitment.users.entity.AccountStatus;
import com.groupcc2.recruitment.users.entity.UserRole;
import com.groupcc2.recruitment.auth.service.AuthService;
import com.groupcc2.recruitment.auth.service.PasswordHashingService;
import com.groupcc2.recruitment.auth.service.PasswordResetTokenService;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class FoundationIT {
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");
    private static final String APPLICATION_USERNAME = "recruitment_app";
    private static final String APPLICATION_PASSWORD = "recruitment-app-test-password";

    static {
        POSTGRES.start();
        bootstrapNonSuperuserApplicationRole();
    }

    @AfterAll
    static void stopPostgres() {
        POSTGRES.stop();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> APPLICATION_USERNAME);
        registry.add("spring.datasource.password", () -> APPLICATION_PASSWORD);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("app.security.jwt.secret-base64", () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    }

    private static void bootstrapNonSuperuserApplicationRole() {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            Flyway.configure()
                    .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                    .defaultSchema("public")
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();
            statement.execute("CREATE ROLE " + APPLICATION_USERNAME
                    + " LOGIN PASSWORD '" + APPLICATION_PASSWORD + "'");
            statement.execute("GRANT USAGE ON SCHEMA recruitment TO " + APPLICATION_USERNAME);
            statement.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA recruitment TO "
                    + APPLICATION_USERNAME);
            statement.execute("GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA recruitment TO "
                    + APPLICATION_USERNAME);
            statement.execute("GRANT USAGE ON TYPE recruitment.user_role, recruitment.account_status, "
                    + "recruitment.job_status, recruitment.application_status TO " + APPLICATION_USERNAME);
            statement.execute("GRANT SELECT ON recruitment.current_jobs TO " + APPLICATION_USERNAME);
            statement.execute("GRANT EXECUTE ON FUNCTION recruitment.auth_lookup_user(text) TO "
                    + APPLICATION_USERNAME);
            statement.execute("GRANT EXECUTE ON FUNCTION recruitment.auth_register_user(text, text, text) TO "
                    + APPLICATION_USERNAME);
            statement.execute("GRANT EXECUTE ON FUNCTION recruitment.auth_store_password_reset_token(text, text, timestamptz) TO "
                    + APPLICATION_USERNAME);
            statement.execute("GRANT EXECUTE ON FUNCTION recruitment.auth_consume_password_reset_token(text, text) TO "
                    + APPLICATION_USERNAME);
            statement.execute("REVOKE ALL ON recruitment.application_notification_outbox FROM "
                    + APPLICATION_USERNAME);
            statement.execute("GRANT EXECUTE ON FUNCTION recruitment.enqueue_application_notification(uuid, uuid, text), "
                    + "recruitment.claim_application_notifications(integer), "
                    + "recruitment.mark_application_notification_sent(uuid, uuid), "
                    + "recruitment.record_application_notification_failure(uuid, uuid, text, boolean) TO "
                    + APPLICATION_USERNAME);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to bootstrap the non-superuser integration account", exception);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired Flyway flyway;
    @Autowired DataSource dataSource;
    @Autowired AuthService authService;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired PasswordHashingService passwordHashingService;
    @Autowired PasswordResetTokenService passwordResetTokenService;

    @Test
    void bootsAgainstPostgresAndAppliesMigrationOnlyOnce() throws Exception {
        assertThat(flyway.getConfiguration().getDefaultSchema()).isEqualTo("public");
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("14");
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT schema_name FROM information_schema.schemata WHERE schema_name = 'recruitment'")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("recruitment");
        }
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT current_user, rolsuper, rolbypassrls
                     FROM pg_roles WHERE rolname = current_user
                     """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("current_user")).isEqualTo(APPLICATION_USERNAME);
            assertThat(result.getBoolean("rolsuper")).isFalse();
            assertThat(result.getBoolean("rolbypassrls")).isFalse();
        }
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT installed_by FROM public.flyway_schema_history
                     WHERE version = '6'
                     """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo(POSTGRES.getUsername());
            try (var ownership = statement.executeQuery("""
                    SELECT pg_get_userbyid(relowner)
                    FROM pg_class WHERE oid = 'recruitment.users'::regclass
                    """)) {
                assertThat(ownership.next()).isTrue();
                assertThat(ownership.getString(1)).isEqualTo(POSTGRES.getUsername());
            }
        }
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}", true));
    }

    @Test
    void applicationRlsRestrictsRowsAndPreventsApplicantsFromChangingReviewStatus() throws Exception {
        UUID employerId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        UUID otherApplicantId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID otherApplicationId = UUID.randomUUID();

        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE ROLE recruitment_rls_test NOLOGIN
                    """);
            statement.execute("""
                    GRANT USAGE ON SCHEMA recruitment TO recruitment_rls_test
                    """);
            statement.execute("""
                    GRANT SELECT ON recruitment.users, recruitment.jobs, recruitment.applications
                    TO recruitment_rls_test
                    """);
            statement.execute("""
                    GRANT INSERT, UPDATE ON recruitment.applications TO recruitment_rls_test
                    """);
            statement.execute("""
                    INSERT INTO recruitment.users (id, email, password_hash, role)
                    VALUES
                      ('%s', 'rls-employer@example.com', 'test-hash', 'EMPLOYER'),
                      ('%s', 'rls-applicant@example.com', 'test-hash', 'JOB_SEEKER'),
                      ('%s', 'rls-other-applicant@example.com', 'test-hash', 'JOB_SEEKER')
                    """.formatted(employerId, applicantId, otherApplicantId));
            statement.execute("""
                    INSERT INTO recruitment.companies (id, employer_id, name)
                    VALUES ('%s', '%s', 'RLS test company')
                    """.formatted(UUID.randomUUID(), employerId));
            statement.execute("""
                    INSERT INTO recruitment.jobs (id, employer_id, company_id, title, description, due_date)
                    SELECT '%s', '%s', id, 'RLS test job', 'RLS test description', CURRENT_DATE + 1
                    FROM recruitment.companies WHERE employer_id = '%s'
                    """.formatted(jobId, employerId, employerId));
            statement.execute("""
                    INSERT INTO recruitment.applications (id, job_id, job_seeker_id)
                    VALUES
                      ('%s', '%s', '%s'),
                      ('%s', '%s', '%s')
                    """.formatted(applicationId, jobId, applicantId,
                    otherApplicationId, jobId, otherApplicantId));
        }

        try (Connection connection = adminConnection()) {
            connection.setAutoCommit(false);
            setRlsTestRole(connection);

            setRlsIdentity(connection, applicantId, "JOB_SEEKER");
            assertThat(countApplication(connection, applicationId)).isEqualTo(1);
            assertThat(countApplication(connection, otherApplicationId)).isZero();

            try (Statement statement = connection.createStatement()) {
                assertThat(statement.executeUpdate("""
                        UPDATE recruitment.applications
                           SET status = 'REJECTED', rejected_at = CURRENT_TIMESTAMP
                         WHERE id = '%s'
                        """.formatted(applicationId))).isZero();
            }
            connection.commit();

            setRlsTestRole(connection);
            setRlsIdentity(connection, employerId, "EMPLOYER");
            try (Statement statement = connection.createStatement()) {
                assertThat(statement.executeUpdate("""
                        UPDATE recruitment.applications
                           SET status = 'SHORTLISTED', shortlisted_at = CURRENT_TIMESTAMP
                         WHERE id = '%s'
                        """.formatted(applicationId))).isEqualTo(1);
            }
            connection.commit();

            assertThat(countApplicationWithoutRlsIdentity(connection, applicationId)).isZero();
            connection.commit();

            setRlsTestRole(connection);
            setRlsUserId(connection, applicantId);
            assertThat(countApplication(connection, applicationId)).isZero();
            connection.commit();

            setRlsTestRole(connection);
            setRlsIdentity(connection, applicantId, "INVALID_ROLE");
            assertThat(countApplication(connection, applicationId)).isZero();
            connection.commit();

            setRlsTestRole(connection);
            setRlsIdentity(connection, UUID.randomUUID(), "JOB_SEEKER");
            assertThat(countApplication(connection, applicationId)).isZero();
            connection.rollback();
        }

        try (Connection connection = adminConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT status::TEXT FROM recruitment.applications WHERE id = ?")) {
            statement.setObject(1, applicationId);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("SHORTLISTED");
            }
        }
    }

    @Test
    void notificationOutboxCapturesTrustedDataIdempotentlyUnderRestrictedRole() throws Exception {
        UUID employerId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();

        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM recruitment.application_notification_outbox");
            statement.execute("""
                    INSERT INTO recruitment.users (id, email, password_hash, role)
                    VALUES ('%s', 'notify-employer@example.com', 'test-hash', 'EMPLOYER'),
                           ('%s', 'notify-applicant@example.com', 'test-hash', 'JOB_SEEKER')
                    """.formatted(employerId, applicantId));
            statement.execute("""
                    INSERT INTO recruitment.profiles (user_id, display_name)
                    VALUES ('%s', '<Applicant & One>')
                    """.formatted(applicantId));
            statement.execute("""
                    INSERT INTO recruitment.companies (id, employer_id, name)
                    VALUES ('%s', '%s', 'Trusted Company')
                    """.formatted(companyId, employerId));
            statement.execute("""
                    INSERT INTO recruitment.jobs (id, employer_id, company_id, title, description, due_date)
                    VALUES ('%s', '%s', '%s', '<Backend & API Engineer>', 'Integration test job', CURRENT_DATE + 10)
                    """.formatted(jobId, employerId, companyId));
            statement.execute("""
                    INSERT INTO recruitment.applications (id, job_id, job_seeker_id)
                    VALUES ('%s', '%s', '%s')
                    """.formatted(applicationId, jobId, applicantId));
        }

        try (Connection connection = runtimeConnection()) {
            connection.setAutoCommit(false);
            setRlsIdentity(connection, applicantId, "JOB_SEEKER");
            try (PreparedStatement enqueue = connection.prepareStatement(
                    "SELECT recruitment.enqueue_application_notification(?, ?, 'SUBMITTED')")) {
                enqueue.setObject(1, applicationId);
                enqueue.setObject(2, applicationId);
                try (var result = enqueue.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getBoolean(1)).isTrue();
                }
                try (var result = enqueue.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getBoolean(1)).isFalse();
                }
            }
            connection.commit();
        }

        try (Connection connection = runtimeConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                try {
                    statement.executeQuery(
                            "SELECT recipient_email FROM recruitment.application_notification_outbox");
                    throw new AssertionError("Runtime role must not query outbox recipient data directly");
                } catch (java.sql.SQLException exception) {
                    assertThat(exception.getSQLState()).isEqualTo("42501");
                    connection.rollback();
                }
            }
        }

        try (Connection connection = runtimeConnection();
             PreparedStatement claim = connection.prepareStatement(
                     "SELECT * FROM recruitment.claim_application_notifications(10)")) {
            UUID claimToken = null;
            try (var result = claim.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("recipient_email")).isEqualTo("notify-applicant@example.com");
                assertThat(result.getString("applicant_name")).isEqualTo("<Applicant & One>");
                assertThat(result.getString("job_title")).isEqualTo("<Backend & API Engineer>");
                assertThat(result.getString("company_name")).isEqualTo("Trusted Company");
                assertThat(result.getString("application_status")).isEqualTo("PENDING");
                assertThat(result.getInt("attempt_count")).isEqualTo(1);
                claimToken = result.getObject("claim_token", UUID.class);
            }
            try (var result = claim.executeQuery()) {
                assertThat(result.next()).isFalse();
            }
            assertThat(recordNotificationFailure(connection, applicationId, claimToken,
                    "SMTP error containing secret")).isTrue();

            for (int attempt = 2; attempt <= 5; attempt++) {
                try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
                    statement.executeUpdate("""
                            UPDATE recruitment.application_notification_outbox
                               SET next_attempt_at = CURRENT_TIMESTAMP - INTERVAL '1 second'
                             WHERE event_id = '%s'
                            """.formatted(applicationId));
                }
                try (var result = claim.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getInt("attempt_count")).isEqualTo(attempt);
                    claimToken = result.getObject("claim_token", UUID.class);
                }
                assertThat(recordNotificationFailure(connection, applicationId, claimToken,
                        "SMTP error containing secret")).isTrue();
            }
            try (var result = claim.executeQuery()) {
                assertThat(result.next()).isFalse();
            }
        }

        try (Connection connection = adminConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT delivery_state, attempt_count, last_error_code
                       FROM recruitment.application_notification_outbox
                      WHERE event_id = ?
                     """)) {
            statement.setObject(1, applicationId);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("delivery_state")).isEqualTo("FAILED");
                assertThat(result.getInt("attempt_count")).isEqualTo(5);
                assertThat(result.getString("last_error_code")).isEqualTo("DELIVERY_ERROR");
            }
        }
    }

    @Test
    void localOpenApiDocumentsPublicRoutesAndJwtProtectedOperations() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Group CC2 Recruitment API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/actuator/health'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/register'].post").exists())
                .andExpect(jsonPath("$.paths['/api/jobs'].get.tags[0]").value("Jobs"))
                .andExpect(jsonPath("$.paths['/api/jobs'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/companies'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.components.schemas.JobRequest.properties.companyId").exists())
                .andExpect(jsonPath("$.paths['/api/applications'].post.tags[0]").value("Applications"))
                .andExpect(jsonPath("$.paths['/api/applications'].get.tags[0]").value("Applications"))
                .andExpect(jsonPath("$.paths['/api/employer/applications'].get.tags[0]").value("Applications"))
                .andExpect(jsonPath("$.tags[?(@.name == 'application controller')]").doesNotExist())
                .andExpect(jsonPath("$.tags[?(@.name == 'job controller')]").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.ApplyRequest.properties.jobId").exists())
                .andExpect(jsonPath("$.components.schemas.ApplyRequest.properties.jobSeekerId").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.JobRequest.properties.employerId").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/users/register']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/users/me'].get").exists())
                .andExpect(jsonPath("$.paths['/api/admin/users'].get").exists())
                .andExpect(jsonPath("$.paths['/api/admin/users'].get.tags[0]").value("Users"))
                .andExpect(jsonPath("$.paths['/api/admin/users'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/admin/users'].get.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/users'].get.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/users/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/admin/users/{id}'].get.tags[0]").value("Users"))
                .andExpect(jsonPath("$.paths['/api/admin/users/{id}'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/admin/users/{id}'].get.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/users/{id}'].get.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/users/{id}/suspend'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/admin/users/{id}/suspend'].patch.tags[0]").value("Users"))
                .andExpect(jsonPath("$.paths['/api/admin/users/{id}/suspend'].patch.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/admin/users/{id}/suspend'].patch.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/users/{id}/suspend'].patch.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/users/{id}/remove'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/employer-requests'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/admin/employer-requests'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/admin/employer-requests/{requestId}/approve'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/admin/users/{id}/remove'].patch.tags[0]").value("Users"))
                .andExpect(jsonPath("$.paths['/api/admin/users/{id}/remove'].patch.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/admin/users/{id}/remove'].patch.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/users/{id}/remove'].patch.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/users/{id}/role'].patch.tags[0]").value("Users"))
                .andExpect(jsonPath("$.paths['/api/admin/users/{id}/role'].patch.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/admin/users/{id}/role'].patch.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/users/{id}/role'].patch.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/users/{id}/role'].patch.requestBody.content['application/json'].schema.$ref")
                        .value("#/components/schemas/AssignAdminRoleRequest"))
                .andExpect(jsonPath("$.tags[?(@.name == 'Administration')]").exists())
                .andExpect(jsonPath("$.tags[?(@.name == 'Users')]").exists())
                .andExpect(jsonPath("$.paths['/api/employer/applications'].get").exists())
                .andExpect(jsonPath("$.paths['/api/employer/applications/{applicationId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/applications'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/employer/applications'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/profile'].put.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/auth/me'].get.security[0].bearerAuth").exists());

        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    void currentUserLookupWorksWithNonSuperuserRlsEnforcement() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO recruitment.users (id, email, password_hash, role)
                    VALUES
                      ('%s', 'auth-rls-test@example.com', 'not-returned-hash', 'JOB_SEEKER'),
                      ('%s', 'auth-rls-other@example.com', 'not-returned-hash', 'JOB_SEEKER')
                    """.formatted(userId, otherUserId));
        }

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CurrentUserResponse response = transaction.execute(status -> {
            return authService.currentUser(userId.toString(), UserRole.JOB_SEEKER.name());
        });

        assertThat(response).isEqualTo(new CurrentUserResponse(
                userId, "auth-rls-test@example.com", UserRole.JOB_SEEKER, AccountStatus.ACTIVE));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> transaction.execute(status -> {
                    return authService.currentUser(UUID.randomUUID().toString(), UserRole.JOB_SEEKER.name());
                }))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            try (var result = statement.executeQuery(
                    "SELECT current_user, rolsuper, rolbypassrls FROM pg_roles WHERE rolname = current_user")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo(APPLICATION_USERNAME);
                assertThat(result.getBoolean(2)).isFalse();
                assertThat(result.getBoolean(3)).isFalse();
            }
            try (var result = statement.executeQuery("SELECT count(id) FROM recruitment.users")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong(1)).isZero();
            }
            try (var result = statement.executeQuery("""
                    SELECT has_function_privilege(
                        current_user, 'recruitment.auth_lookup_user(text)', 'EXECUTE'),
                           has_function_privilege(
                        current_user, 'recruitment.auth_register_user(text,text,text)', 'EXECUTE'),
                           rolsuper, rolbypassrls,
                           NOT EXISTS (
                               SELECT 1
                               FROM aclexplode(COALESCE(
                                   (SELECT proacl FROM pg_proc
                                    WHERE oid = 'recruitment.auth_lookup_user(text)'::regprocedure),
                                   acldefault('f', (SELECT proowner FROM pg_proc
                                    WHERE oid = 'recruitment.auth_lookup_user(text)'::regprocedure))))
                               WHERE grantee = 0 AND privilege_type = 'EXECUTE')
                    FROM pg_roles WHERE rolname = current_user
                    """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getBoolean(1)).isTrue();
                assertThat(result.getBoolean(2)).isTrue();
                assertThat(result.getBoolean(3)).isFalse();
                assertThat(result.getBoolean(4)).isFalse();
                assertThat(result.getBoolean(5)).isTrue();
            }
            try (var result = statement.executeQuery("""
                    SELECT pg_get_userbyid(relowner) = current_user
                    FROM pg_class WHERE oid = 'recruitment.users'::regclass
                    """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getBoolean(1)).isFalse();
            }
        }

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE recruitment_unauthorized_test NOLOGIN");
            statement.execute("GRANT USAGE ON SCHEMA recruitment TO recruitment_unauthorized_test");
        }
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("SET ROLE recruitment_unauthorized_test");
            try {
                statement.execute("SELECT recruitment.auth_lookup_user('auth-rls-test@example.com')");
                throw new AssertionError("Unauthorized role must not execute the auth function");
            } catch (java.sql.SQLException exception) {
                assertThat(exception.getSQLState()).isEqualTo("42501");
            }
        }
    }

    @Test
    void registrationLoginAndCurrentUserWorkThroughExistingDatabaseAccount() throws Exception {
        String email = "auth-flow-" + UUID.randomUUID() + "@example.com";
        String password = "StrongPassword123!";

        var registration = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","role":"JOB_SEEKER"}
                                """.formatted(email, password)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("JOB_SEEKER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();
        UUID userId = UUID.fromString(
                com.jayway.jsonpath.JsonPath.read(registration.getResponse().getContentAsString(), "$.id"));

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","role":"JOB_SEEKER"}
                                """.formatted(email, password)))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin-%s@example.com","password":"%s","role":"ADMIN"}
                                """.formatted(UUID.randomUUID(), password)))
                .andExpect(status().isBadRequest());

        var login = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(
                login.getResponse().getContentAsString(), "$.accessToken");

        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get("/api/auth/me?userId=" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()));

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"incorrect-password"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized());

        String employerEmail = "employer-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"EmployerPassword123!","role":"EMPLOYER"}
                                """.formatted(employerEmail)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void authenticatedModulesWorkTogetherAndEnforceOwnership() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String seekerEmail = "workflow-seeker-" + suffix + "@example.com";
        String employerEmail = "workflow-employer-" + suffix + "@example.com";
        String otherEmployerEmail = "workflow-other-employer-" + suffix + "@example.com";
        String password = "WorkflowPassword123!";

        UUID seekerId = register(seekerEmail, password, "JOB_SEEKER");
        register(employerEmail, password, "EMPLOYER");
        register(otherEmployerEmail, password, "EMPLOYER");
        String seekerToken = loginAndGetToken(seekerEmail, password);
        String employerToken = loginAndGetToken(employerEmail, password);
        String otherEmployerToken = loginAndGetToken(otherEmployerEmail, password);

        var companyResponse = mvc.perform(post("/api/companies")
                        .header("Authorization", "Bearer " + employerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Workflow Company\",\"location\":\"Blantyre\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID companyId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(
                companyResponse.getResponse().getContentAsString(), "$.id"));
        mvc.perform(get("/api/companies/" + companyId)
                        .header("Authorization", "Bearer " + otherEmployerToken))
                .andExpect(status().isNotFound());

        mvc.perform(put("/api/profile")
                        .header("Authorization", "Bearer " + seekerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Workflow Applicant\",\"phone\":\"+265999000111\","
                                + "\"biography\":\"Integration test profile\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Workflow Applicant"));

        var jobResponse = mvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + employerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyId":"%s","title":"Integration Engineer","description":"Build secure APIs",
                                 "requiredQualifications":"Java","dueDate":"2099-12-31","location":"Blantyre"}
                                """.formatted(companyId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employerId").value(org.hamcrest.Matchers.not(seekerId.toString())))
                .andExpect(jsonPath("$.companyId").value(companyId.toString()))
                .andReturn();
        UUID jobId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(
                jobResponse.getResponse().getContentAsString(), "$.id"));

        mvc.perform(get("/api/jobs").header("Authorization", "Bearer " + seekerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(jobId.toString()));

        var applicationResponse = mvc.perform(post("/api/applications")
                        .header("Authorization", "Bearer " + seekerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":\"" + jobId + "\",\"jobSeekerId\":\""
                                + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        UUID applicationId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(
                applicationResponse.getResponse().getContentAsString(), "$.id"));

        mvc.perform(get("/api/employer/applications").header("Authorization", "Bearer " + employerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(applicationId.toString()));
        mvc.perform(get("/api/employer/applications/" + applicationId)
                        .header("Authorization", "Bearer " + otherEmployerToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/employer/applications/" + applicationId)
                        .header("Authorization", "Bearer " + employerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.applicantEmail").doesNotExist());
        mvc.perform(get("/api/applications/" + applicationId)
                        .header("Authorization", "Bearer " + otherEmployerToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/employer/applications").header("Authorization", "Bearer " + seekerToken))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/applications")
                        .header("Authorization", "Bearer " + seekerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":\"" + jobId + "\"}"))
                .andExpect(status().isConflict());
        try (Connection connection = adminConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT count(*) FROM recruitment.application_notification_outbox
                     WHERE application_id = ? AND event_type = 'SUBMITTED'
                     """)) {
            statement.setObject(1, applicationId);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong(1)).isEqualTo(1);
            }
        }

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/employer/applications/" + applicationId + "/status")
                        .header("Authorization", "Bearer " + employerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHORTLISTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHORTLISTED"));
        try (Connection connection = adminConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT event_type, recipient_email::TEXT, job_title, company_name, application_status::TEXT
                     FROM recruitment.application_notification_outbox
                     WHERE application_id = ? AND event_type IN ('SUBMITTED', 'SHORTLISTED')
                     ORDER BY event_type
                     """)) {
            statement.setObject(1, applicationId);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("event_type")).isEqualTo("SHORTLISTED");
                assertThat(result.getString("recipient_email")).isEqualTo(seekerEmail);
                assertThat(result.getString("job_title")).isEqualTo("Integration Engineer");
                assertThat(result.getString("company_name")).isEqualTo("Workflow Company");
                assertThat(result.getString("application_status")).isEqualTo("SHORTLISTED");
                assertThat(result.next()).isTrue();
                assertThat(result.getString("event_type")).isEqualTo("SUBMITTED");
                assertThat(result.getString("application_status")).isEqualTo("PENDING");
                assertThat(result.next()).isFalse();
            }
        }

        try (Connection connection = adminConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE recruitment.users SET status = 'SUSPENDED' WHERE id = ?")) {
            statement.setObject(1, seekerId);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + seekerToken))
                .andExpect(status().isUnauthorized());
    }

    private UUID register(String email, String password, String role) throws Exception {
        var response = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password
                                + "\",\"role\":\"JOB_SEEKER\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID userId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(
                response.getResponse().getContentAsString(), "$.id"));
        if ("EMPLOYER".equals(role)) {
            try (Connection connection = adminConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "UPDATE recruitment.users SET role = 'EMPLOYER' WHERE id = ?")) {
                statement.setObject(1, userId);
                assertThat(statement.executeUpdate()).isEqualTo(1);
            }
        }
        return userId;
    }

    @Test
    void suspendedAndRemovedAccountsCannotLogIn() throws Exception {
        for (AccountStatus accountStatus : new AccountStatus[] {
                AccountStatus.SUSPENDED, AccountStatus.REMOVED}) {
            String email = accountStatus.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
            try (Connection connection = adminConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         INSERT INTO recruitment.users (email, password_hash, role, status)
                         VALUES (?, ?, 'JOB_SEEKER', ?::recruitment.account_status)
                         """)) {
                statement.setString(1, email);
                statement.setString(2, passwordHashingService.hash("StrongPassword123!"));
                statement.setString(3, accountStatus.name());
                statement.executeUpdate();
            }

            mvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"%s","password":"StrongPassword123!"}
                                    """.formatted(email)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void jwtIsRejectedWhenAccountBecomesSuspendedOrChangesRole() throws Exception {
        String email = "status-change-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"StatusPassword123!","role":"JOB_SEEKER"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
        String token = loginAndGetToken(email, "StatusPassword123!");

        try (Connection connection = adminConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE recruitment.users
                     SET status = 'SUSPENDED'
                     WHERE email = ?
                     """)) {
            statement.setString(1, email);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resetChangeAndLogoutInvalidateSessionsAndResetTokensAreSingleUse() throws Exception {
        String email = "recovery-" + UUID.randomUUID() + "@example.com";
        String originalPassword = "OriginalPassword123!";
        String resetPassword = "ResetPassword123!";
        String changedPassword = "ChangedPassword123!";
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","role":"JOB_SEEKER"}
                                """.formatted(email, originalPassword)))
                .andExpect(status().isCreated());

        var knownResponse = mvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(
                        "If the account exists, the reset request has been accepted."))
                .andReturn();
        String resetToken = com.jayway.jsonpath.JsonPath.read(
                knownResponse.getResponse().getContentAsString(), "$.resetToken");

        mvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"missing-%s@example.com"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(
                        "If the account exists, the reset request has been accepted."))
                .andExpect(jsonPath("$.resetToken").doesNotExist());

        String storedTokenHash;
        try (Connection connection = adminConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT password_reset_token_hash
                     FROM recruitment.users WHERE email = ?
                     """)) {
            statement.setString(1, email);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                storedTokenHash = result.getString(1);
            }
        }
        assertThat(storedTokenHash).isEqualTo(passwordResetTokenService.hashToken(resetToken));
        assertThat(storedTokenHash).isNotEqualTo(resetToken);

        mvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resetToken":"%s","newPassword":"%s"}
                                """.formatted(resetToken, resetPassword)))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resetToken":"%s","newPassword":"%s"}
                                """.formatted(resetToken, changedPassword)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, originalPassword)))
                .andExpect(status().isUnauthorized());

        String resetSession = loginAndGetToken(email, resetPassword);
        mvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + resetSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"wrong-password","newPassword":"%s"}
                                """.formatted(changedPassword)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + resetSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"%s","newPassword":"%s"}
                                """.formatted(resetPassword, changedPassword)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + resetSession))
                .andExpect(status().isUnauthorized());

        String logoutSession = loginAndGetToken(email, changedPassword);
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + logoutSession))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + logoutSession))
                .andExpect(status().isUnauthorized());

        var expiredResponse = mvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isAccepted())
                .andReturn();
        String expiredResetToken = com.jayway.jsonpath.JsonPath.read(
                expiredResponse.getResponse().getContentAsString(), "$.resetToken");
        try (Connection connection = adminConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE recruitment.users
                     SET password_reset_expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second'
                     WHERE password_reset_token_hash = ?
                     """)) {
            statement.setString(1, passwordResetTokenService.hashToken(expiredResetToken));
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
        mvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resetToken":"%s","newPassword":"AnotherPassword123!"}
                                """.formatted(expiredResetToken)))
                .andExpect(status().isBadRequest());
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        var response = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(
                response.getResponse().getContentAsString(), "$.accessToken");
    }

    private Connection adminConnection() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private Connection runtimeConnection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), APPLICATION_USERNAME, APPLICATION_PASSWORD);
    }

    private void setRlsTestRole(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET LOCAL ROLE recruitment_rls_test");
        }
    }

    private void setRlsIdentity(Connection connection, UUID userId, String role) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT set_config('app.user_id', ?, true), set_config('app.role', ?, true)")) {
            statement.setString(1, userId.toString());
            statement.setString(2, role);
            statement.execute();
        }
    }

    private static boolean recordNotificationFailure(Connection connection, UUID eventId, UUID claimToken,
                                                      String errorCode) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT recruitment.record_application_notification_failure(?, ?, ?, false)")) {
            statement.setObject(1, eventId);
            statement.setObject(2, claimToken);
            statement.setString(3, errorCode);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getBoolean(1);
            }
        }
    }

    private void setRlsUserId(Connection connection, UUID userId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT set_config('app.user_id', ?, true)")) {
            statement.setString(1, userId.toString());
            statement.execute();
        }
    }

    private int countApplication(Connection connection, UUID applicationId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT count(*) FROM recruitment.applications WHERE id = ?")) {
            statement.setObject(1, applicationId);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private int countApplicationWithoutRlsIdentity(Connection connection, UUID applicationId) throws Exception {
        setRlsTestRole(connection);
        return countApplication(connection, applicationId);
    }

    @Test
    void featureRoutesRemainClosedAndAuthenticationDoesNotExposeAdminRegistration() throws Exception {
        for (String path : new String[] {"/api/auth/login", "/api/jobs",
                "/api/profiles/me", "/api/applications", "/api/admin/users", "/actuator/env"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).with(jwt())).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content("{\"email\":\"admin@example.com\",\"password\":\"StrongPassword123!\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/jobs").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
    }
}
