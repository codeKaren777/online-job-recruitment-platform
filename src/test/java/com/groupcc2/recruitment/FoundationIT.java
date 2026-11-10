package com.groupcc2.recruitment;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import com.groupcc2.recruitment.auth.dto.CurrentUserResponse;
import com.groupcc2.recruitment.auth.dto.LoginRequest;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.groupcc2.recruitment.profile.storage.PrivateDocumentStorage;
import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

    @MockitoBean
    PrivateDocumentStorage privateDocumentStorage;

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
            statement.execute("GRANT EXECUTE ON FUNCTION recruitment.auth_lookup_user(text), "
                    + "recruitment.auth_bootstrap_admin(text, text) TO "
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
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("20");
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema = 'recruitment' AND table_name = 'applications'
                  AND column_name = 'cv_document_id'
                """); var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("YES");
        }
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT p.prosecdef, p.proconfig,
                            NOT EXISTS (
                                SELECT 1
                                FROM aclexplode(COALESCE(p.proacl, acldefault('f', p.proowner)))
                                WHERE grantee = 0 AND privilege_type = 'EXECUTE')
                     FROM pg_proc AS p
                     WHERE p.oid = 'recruitment.auth_register_user(text,text,text)'::regprocedure
                     """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getBoolean(1)).isTrue();
            assertThat(result.getString(2)).contains("search_path=pg_catalog");
            assertThat(result.getBoolean(3)).isTrue();
        }
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
    void employerVerificationApprovalPersistsCompanyRoleAndRequestAtomically() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String password = "VerificationPassword123!";
        UUID applicantId = register("verification-" + suffix + "@example.com", password);
        UUID adminId = createAccount("verification-admin-" + suffix + "@example.com", password, UserRole.ADMIN);
        String applicantToken = loginAndGetToken("verification-" + suffix + "@example.com", password);
        String adminToken = loginAndGetToken("verification-admin-" + suffix + "@example.com", password);

        var submitted = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/employer-requests")
                        .file(new MockMultipartFile("application", "application.json", "application/json",
                                ("{\"companyName\":\"Verified Company " + suffix
                                        + "\",\"businessDescription\":\"Recruitment services\","
                                        + "\"contactEmail\":\"contact@example.com\",\"contactPhone\":\"+265999000111\","
                                        + "\"location\":\"Blantyre\",\"vatNumber\":\"vat-" + suffix
                                        + "\",\"registrationNumber\":\"reg-" + suffix + "\"}")
                                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .file(new MockMultipartFile("document", "registration.pdf", "application/pdf",
                                "%PDF-1.7\nregistration proof\n%%EOF\n"
                                        .getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        String requestId = com.jayway.jsonpath.JsonPath.read(
                submitted.getResponse().getContentAsString(), "$.id");

        mvc.perform(patch("/api/admin/employer-requests/{requestId}/approve", requestId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.companyId").isNotEmpty());
        mvc.perform(patch("/api/admin/employer-requests/{requestId}/approve", requestId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        assertThat(userRole(applicantId)).isEqualTo(UserRole.EMPLOYER.name());
        try (Connection connection = adminConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT r.status::TEXT, r.company_id, c.vat_number, c.registration_number,
                            (SELECT count(*) FROM recruitment.companies WHERE employer_id = r.user_id)
                     FROM recruitment.employer_upgrade_requests r
                     JOIN recruitment.companies c ON c.id = r.company_id
                     WHERE r.id = ? AND r.user_id = ?
                     """)) {
            statement.setObject(1, UUID.fromString(requestId));
            statement.setObject(2, applicantId);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("APPROVED");
                assertThat(result.getObject(2, UUID.class)).isNotNull();
                assertThat(result.getString(3)).isEqualTo("VAT-" + suffix.toUpperCase(java.util.Locale.ROOT));
                assertThat(result.getString(4)).isEqualTo("REG-" + suffix.toUpperCase(java.util.Locale.ROOT));
                assertThat(result.getLong(5)).isEqualTo(1);
            }
        }
    }

    @Test
    void employerVerificationApprovalRollsBackFlushedRoleWhenCompanyInsertFails() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String password = "VerificationRollback123!";
        String applicantEmail = "verification-rollback-" + suffix + "@example.com";
        String adminEmail = "verification-rollback-admin-" + suffix + "@example.com";
        String companyName = "Forced rollback company " + suffix;
        UUID applicantId = register(applicantEmail, password);
        UUID adminId = createAccount(adminEmail, password, UserRole.ADMIN);
        String applicantToken = loginAndGetToken(applicantEmail, password);
        String adminToken = loginAndGetToken(adminEmail, password);
        UUID requestId = null;
        boolean failureConstraintCreated = false;

        try {
            var submitted = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .multipart("/api/employer-requests")
                            .file(new MockMultipartFile("application", "application.json", "application/json",
                                    ("{\"companyName\":\"" + companyName
                                            + "\",\"businessDescription\":\"Recruitment services\","
                                            + "\"contactEmail\":\"contact@example.com\",\"contactPhone\":\"+265999000111\","
                                            + "\"location\":\"Blantyre\",\"vatNumber\":\"vat-rollback-" + suffix
                                            + "\",\"registrationNumber\":\"reg-rollback-" + suffix + "\"}")
                                            .getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                            .file(new MockMultipartFile("document", "registration.pdf", "application/pdf",
                                    "%PDF-1.7\nregistration proof\n%%EOF\n"
                                            .getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
                            .header("Authorization", "Bearer " + applicantToken))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andReturn();
            requestId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(
                    submitted.getResponse().getContentAsString(), "$.id"));

            // The company role trigger runs before this check. A 409 therefore confirms the
            // flushed applicant role was visible to that trigger before the forced insert failure.
            try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE recruitment.companies ADD CONSTRAINT "
                        + "companies_it_force_verification_rollback CHECK (name <> '" + companyName + "')");
                failureConstraintCreated = true;
            }

            mvc.perform(patch("/api/admin/employer-requests/{requestId}/approve", requestId)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isConflict());

            try (Connection connection = adminConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT u.role::TEXT, r.status::TEXT, r.company_id,
                                (SELECT count(*) FROM recruitment.companies c WHERE c.employer_id = u.id),
                                (SELECT count(*) FROM recruitment.companies c WHERE c.name = ?)
                         FROM recruitment.users u
                         JOIN recruitment.employer_upgrade_requests r ON r.user_id = u.id
                         WHERE u.id = ? AND r.id = ?
                         """)) {
                statement.setString(1, companyName);
                statement.setObject(2, applicantId);
                statement.setObject(3, requestId);
                try (var result = statement.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString(1)).isEqualTo(UserRole.JOB_SEEKER.name());
                    assertThat(result.getString(2)).isEqualTo("PENDING");
                    assertThat(result.getObject(3)).isNull();
                    assertThat(result.getLong(4)).isZero();
                    assertThat(result.getLong(5)).isZero();
                    assertThat(result.next()).isFalse();
                }
            }
        } finally {
            try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
                if (failureConstraintCreated) {
                    statement.execute("ALTER TABLE recruitment.companies DROP CONSTRAINT "
                            + "companies_it_force_verification_rollback");
                }
                if (requestId != null) {
                    try (PreparedStatement deleteRequest = connection.prepareStatement(
                            "DELETE FROM recruitment.employer_upgrade_requests WHERE id = ?")) {
                        deleteRequest.setObject(1, requestId);
                        deleteRequest.executeUpdate();
                    }
                }
                try (PreparedStatement deleteCompanies = connection.prepareStatement(
                        "DELETE FROM recruitment.companies WHERE employer_id = ?")) {
                    deleteCompanies.setObject(1, applicantId);
                    deleteCompanies.executeUpdate();
                }
                try (PreparedStatement deleteUsers = connection.prepareStatement(
                        "DELETE FROM recruitment.users WHERE id IN (?, ?)")) {
                    deleteUsers.setObject(1, applicantId);
                    deleteUsers.setObject(2, adminId);
                    deleteUsers.executeUpdate();
                }
            }
        }
    }

    @Test
    void newApplicationsRequireAnApplicantOwnedCvButLegacyColumnRemainsNullable() throws Exception {
        UUID employerId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        UUID otherApplicantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID applicantCvId = UUID.randomUUID();
        UUID otherApplicantCvId = UUID.randomUUID();

        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO recruitment.users (id, email, password_hash, role)
                    VALUES ('%s', '%s@example.test', 'hash', 'EMPLOYER'),
                           ('%s', '%s@example.test', 'hash', 'JOB_SEEKER'),
                           ('%s', '%s@example.test', 'hash', 'JOB_SEEKER')
                    """.formatted(employerId, employerId, applicantId, applicantId, otherApplicantId, otherApplicantId));
            statement.execute("""
                    INSERT INTO recruitment.profiles (user_id, display_name, full_name)
                    VALUES ('%s', 'Applicant', 'Applicant'), ('%s', 'Other Applicant', 'Other Applicant')
                    """.formatted(applicantId, otherApplicantId));
            statement.execute("""
                    INSERT INTO recruitment.documents (id, profile_user_id, storage_key, original_filename, content_type, byte_size)
                    VALUES ('%s', '%s', 'documents/%s', 'applicant.pdf', 'application/pdf', 20),
                           ('%s', '%s', 'documents/%s', 'other.pdf', 'application/pdf', 20)
                    """.formatted(applicantCvId, applicantId, applicantCvId,
                    otherApplicantCvId, otherApplicantId, otherApplicantCvId));
            statement.execute("""
                    INSERT INTO recruitment.companies (id, employer_id, name)
                    VALUES ('%s', '%s', 'CV trigger company')
                    """.formatted(companyId, employerId));
            statement.execute("""
                    INSERT INTO recruitment.jobs (id, employer_id, company_id, title, description, due_date)
                    VALUES ('%s', '%s', '%s', 'CV trigger job', 'Description', CURRENT_DATE + 2)
                    """.formatted(jobId, employerId, companyId));
        }

        try (Connection connection = adminConnection()) {
            assertApplicationInsertRejected(connection, jobId, applicantId, null,
                    "new applications require an application CV");
            assertApplicationInsertRejected(connection, jobId, applicantId, otherApplicantCvId,
                    "application CV must belong to the applicant");
            UUID applicationId = UUID.randomUUID();
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO recruitment.applications (id, job_id, job_seeker_id, cv_document_id)
                    VALUES (?, ?, ?, ?)
                    """)) {
                insert.setObject(1, applicationId);
                insert.setObject(2, jobId);
                insert.setObject(3, applicantId);
                insert.setObject(4, applicantCvId);
                assertThat(insert.executeUpdate()).isEqualTo(1);
            }
        }
    }

    private void assertApplicationInsertRejected(Connection connection, UUID jobId, UUID applicantId,
                                                 UUID cvId, String message) throws Exception {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO recruitment.applications (job_id, job_seeker_id, cv_document_id)
                VALUES (?, ?, ?)
                """)) {
            insert.setObject(1, jobId);
            insert.setObject(2, applicantId);
            insert.setObject(3, cvId);
            try {
                insert.executeUpdate();
                throw new AssertionError("Application CV ownership trigger must reject the insert");
            } catch (java.sql.SQLException exception) {
                assertThat(exception.getMessage()).contains(message);
            }
        }
    }

    @Test
    void applicationRlsRestrictsRowsAndPreventsApplicantsFromChangingReviewStatus() throws Exception {
        UUID employerId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        UUID otherApplicantId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID otherApplicationId = UUID.randomUUID();
        UUID applicantCvId = UUID.randomUUID();
        UUID otherApplicantCvId = UUID.randomUUID();

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
                    INSERT INTO recruitment.profiles (user_id, display_name, full_name)
                    VALUES ('%s', 'Applicant', 'Applicant'), ('%s', 'Other Applicant', 'Other Applicant')
                    """.formatted(applicantId, otherApplicantId));
            statement.execute("""
                    INSERT INTO recruitment.documents (id, profile_user_id, storage_key, original_filename, content_type, byte_size)
                    VALUES ('%s', '%s', 'documents/%s', 'applicant.pdf', 'application/pdf', 20),
                           ('%s', '%s', 'documents/%s', 'other-applicant.pdf', 'application/pdf', 20)
                    """.formatted(applicantCvId, applicantId, applicantCvId,
                    otherApplicantCvId, otherApplicantId, otherApplicantCvId));
            statement.execute("""
                    INSERT INTO recruitment.applications (id, job_id, job_seeker_id, cv_document_id)
                    VALUES
                      ('%s', '%s', '%s', '%s'),
                      ('%s', '%s', '%s', '%s')
                    """.formatted(applicationId, jobId, applicantId, applicantCvId,
                    otherApplicationId, jobId, otherApplicantId, otherApplicantCvId));
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
        UUID applicantCvId = UUID.randomUUID();

        try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM recruitment.application_notification_outbox");
            statement.execute("""
                    INSERT INTO recruitment.users (id, email, password_hash, role)
                    VALUES ('%s', 'notify-employer@example.com', 'test-hash', 'EMPLOYER'),
                           ('%s', 'notify-applicant@example.com', 'test-hash', 'JOB_SEEKER')
                    """.formatted(employerId, applicantId));
            statement.execute("""
                    INSERT INTO recruitment.profiles (user_id, display_name, full_name)
                    VALUES ('%s', '<Applicant & One>', '<Applicant & One>')
                    """.formatted(applicantId));
            statement.execute("""
                    INSERT INTO recruitment.documents (id, profile_user_id, storage_key, original_filename, content_type, byte_size)
                    VALUES ('%s', '%s', 'documents/%s', 'notification-cv.pdf', 'application/pdf', 20)
                    """.formatted(applicantCvId, applicantId, applicantCvId));
            statement.execute("""
                    INSERT INTO recruitment.companies (id, employer_id, name)
                    VALUES ('%s', '%s', 'Trusted Company')
                    """.formatted(companyId, employerId));
            statement.execute("""
                    INSERT INTO recruitment.jobs (id, employer_id, company_id, title, description, due_date)
                    VALUES ('%s', '%s', '%s', '<Backend & API Engineer>', 'Integration test job', CURRENT_DATE + 10)
                    """.formatted(jobId, employerId, companyId));
            statement.execute("""
                    INSERT INTO recruitment.applications (id, job_id, job_seeker_id, cv_document_id)
                    VALUES ('%s', '%s', '%s', '%s')
                    """.formatted(applicationId, jobId, applicantId, applicantCvId));
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
                .andExpect(jsonPath("$.paths['/api/jobs'].get.tags[0]").value("Job"))
                .andExpect(jsonPath("$.paths['/api/jobs'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/companies'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.components.schemas.JobRequest.properties.companyId").exists())
                .andExpect(jsonPath("$.paths['/api/applications'].post.tags[0]").value("Application"))
                .andExpect(jsonPath("$.paths['/api/applications'].get.tags[0]").value("Application"))
                .andExpect(jsonPath("$.paths['/api/employer/applications'].get.tags[0]").value("Application"))
                .andExpect(jsonPath("$.tags[?(@.name == 'application controller')]").doesNotExist())
                .andExpect(jsonPath("$.tags[?(@.name == 'job controller')]").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.ApplicationSubmissionRequest.properties.jobId").exists())
                .andExpect(jsonPath("$.components.schemas.ApplicationSubmissionRequest.properties.cv.format").value("binary"))
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
                .andExpect(jsonPath("$.tags[?(@.name == 'Users')]").exists())
                .andExpect(jsonPath("$.paths['/api/employer/applications'].get").exists())
                .andExpect(jsonPath("$.paths['/api/employer/applications/{applicationId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/applications'].post.requestBody.required").value(true))
                .andExpect(jsonPath("$.paths['/api/applications'].post.requestBody.content['multipart/form-data'].schema.$ref")
                        .value("#/components/schemas/ApplicationSubmissionRequest"))
                .andExpect(jsonPath("$.components.schemas.ApplicationSubmissionRequest.required").isArray())
                .andExpect(jsonPath("$.components.schemas.ApplicationSubmissionRequest.properties.jobId.format").value("uuid"))
                .andExpect(jsonPath("$.components.schemas.ApplicationSubmissionRequest.properties.cv.format").value("binary"))
                .andExpect(jsonPath("$.paths['/api/applications'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/applications'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/applications'].post.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/applications'].post.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/applications'].post.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/applications'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/applications'].post.responses['503']").exists())
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

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","role":"ADMIN"}
                                """.formatted(email, password)))
                .andExpect(status().isBadRequest());

        var registration = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("JOB_SEEKER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();
        UUID userId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(
                registration.getResponse().getContentAsString(), "$.id"));

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

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM recruitment.auth_register_user(?, ?, ?)")) {
            statement.setString(1, "db-employer-" + UUID.randomUUID() + "@example.com");
            statement.setString(2, passwordHashingService.hash(password));
            statement.setString(3, UserRole.EMPLOYER.name());
            try {
                statement.executeQuery();
                throw new AssertionError("The public registration database contract must reject EMPLOYER");
            } catch (java.sql.SQLException exception) {
                assertThat(exception.getSQLState()).isEqualTo("22023");
            }
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM recruitment.auth_register_user(?, ?, ?)")) {
            statement.setString(1, "db-admin-" + UUID.randomUUID() + "@example.com");
            statement.setString(2, passwordHashingService.hash(password));
            statement.setString(3, UserRole.ADMIN.name());
            try {
                statement.executeQuery();
                throw new AssertionError("The public registration database contract must reject ADMIN");
            } catch (java.sql.SQLException exception) {
                assertThat(exception.getSQLState()).isEqualTo("22023");
            }
        }
    }

    @Test
    void authenticatedModulesWorkTogetherAndEnforceOwnership() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String seekerEmail = "workflow-seeker-" + suffix + "@example.com";
        String employerEmail = "workflow-employer-" + suffix + "@example.com";
        String otherEmployerEmail = "workflow-other-employer-" + suffix + "@example.com";
        String password = "WorkflowPassword123!";

        UUID seekerId = register(seekerEmail, password);
        createAccount(employerEmail, password, UserRole.EMPLOYER);
        createAccount(otherEmployerEmail, password, UserRole.EMPLOYER);
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

        assertThat(userRole(seekerId)).isEqualTo(UserRole.JOB_SEEKER.name());

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

        try (Connection connection = adminConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT count(*) FROM recruitment.profiles WHERE user_id = ?")) {
            statement.setObject(1, seekerId);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong(1)).isZero();
            }
        }

        mvc.perform(put("/api/profile")
                        .header("Authorization", "Bearer " + seekerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Workflow Applicant\",\"phone\":\"+265999000111\","
                                + "\"biography\":\"Integration test profile\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Workflow Applicant"));

        UUID clientApplicationId = UUID.randomUUID();
        MockMultipartFile applicationCv = new MockMultipartFile("cv", "integration-cv.pdf",
                "application/pdf", "%PDF-1.7\nIntegration CV\n%%EOF\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        var applicationResponse = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/applications")
                        .file(applicationCv)
                        .param("jobId", jobId.toString())
                        .header("Authorization", "Bearer " + seekerToken)
                        .param("jobSeekerId", UUID.randomUUID().toString())
                        .param("id", clientApplicationId.toString())
                        .param("status", "REJECTED"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        UUID applicationId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(
                applicationResponse.getResponse().getContentAsString(), "$.id"));
        assertThat(applicationId).isNotEqualTo(clientApplicationId);
        try (Connection connection = adminConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT a.job_id, a.job_seeker_id, a.status::text, a.submitted_at, a.updated_at,
                            a.cv_document_id, d.profile_user_id, d.original_filename
                     FROM recruitment.applications a
                     JOIN recruitment.documents d ON d.id = a.cv_document_id
                     WHERE a.id = ?
                     """)) {
            statement.setObject(1, applicationId);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getObject("job_id", UUID.class)).isEqualTo(jobId);
                assertThat(result.getObject("job_seeker_id", UUID.class)).isEqualTo(seekerId);
                assertThat(result.getString("status")).isEqualTo("PENDING");
                assertThat(result.getObject("cv_document_id", UUID.class)).isNotNull();
                assertThat(result.getObject("profile_user_id", UUID.class)).isEqualTo(seekerId);
                assertThat(result.getString("original_filename")).isEqualTo("integration-cv.pdf");
                assertThat(result.getTimestamp("submitted_at").toInstant())
                        .isAfter(Instant.parse("2000-01-01T00:00:00Z"));
                assertThat(result.getTimestamp("updated_at").toInstant())
                        .isAfter(Instant.parse("2000-01-01T00:00:00Z"));
            }
        }

        mvc.perform(get("/api/employer/applications").header("Authorization", "Bearer " + employerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(applicationId.toString()))
                .andExpect(jsonPath("$[0].cv.originalFilename").value("integration-cv.pdf"))
                .andExpect(jsonPath("$[0].applicantDisplayName").value("Workflow Applicant"));
        mvc.perform(get("/api/employer/applications/" + applicationId)
                        .header("Authorization", "Bearer " + otherEmployerToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/employer/applications/" + applicationId)
                        .header("Authorization", "Bearer " + employerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.applicantEmail").doesNotExist());
        org.mockito.Mockito.when(privateDocumentStorage.signGet(
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(Duration.ofSeconds(120))))
                .thenReturn(URI.create("https://documents.example.test/application-cv"));
        mvc.perform(get("/api/employer/applications/" + applicationId + "/cv")
                        .header("Authorization", "Bearer " + employerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://documents.example.test/application-cv"));
        mvc.perform(get("/api/employer/applications/" + applicationId + "/cv")
                        .header("Authorization", "Bearer " + otherEmployerToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/applications/" + applicationId)
                        .header("Authorization", "Bearer " + otherEmployerToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/employer/applications").header("Authorization", "Bearer " + seekerToken))
                .andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/applications")
                        .file(new MockMultipartFile("cv", "second-cv.pdf", "application/pdf",
                                "%PDF-1.7\nSecond CV\n%%EOF\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
                        .header("Authorization", "Bearer " + seekerToken)
                        .param("jobId", jobId.toString()))
                .andExpect(status().isConflict());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/applications")
                        .header("Authorization", "Bearer " + seekerToken)
                        .param("jobId", jobId.toString()))
                .andExpect(status().isBadRequest());
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

    private UUID register(String email, String password) throws Exception {
        var response = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("JOB_SEEKER"))
                .andReturn();
        UUID userId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(
                response.getResponse().getContentAsString(), "$.id"));
        return userId;
    }

    private UUID createAccount(String email, String password, UserRole role) throws Exception {
        UUID userId = UUID.randomUUID();
        try (Connection connection = adminConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO recruitment.users (id, email, password_hash, role)
                     VALUES (?, ?, ?, ?::recruitment.user_role)
                     """)) {
            statement.setObject(1, userId);
            statement.setString(2, email);
            statement.setString(3, passwordHashingService.hash(password));
            statement.setString(4, role.name());
            statement.executeUpdate();
        }
        return userId;
    }

    private String userRole(UUID userId) throws Exception {
        try (Connection connection = adminConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT role::text FROM recruitment.users WHERE id = ?")) {
            statement.setObject(1, userId);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString(1);
            }
        }
    }

    @Test
    void adminAccountOperationsUseVerifiedRoleAndRestrictedRlsContext() throws Exception {
        String password = "StrongPassword123!";
        String adminEmail = "rbac-admin-" + UUID.randomUUID() + "@example.com";
        String seekerEmail = "rbac-seeker-" + UUID.randomUUID() + "@example.com";
        String employerEmail = "rbac-employer-" + UUID.randomUUID() + "@example.com";
        String targetEmail = "rbac-target-" + UUID.randomUUID() + "@example.com";
        UUID adminId = createAccount(adminEmail, password, UserRole.ADMIN);
        UUID seekerId = createAccount(seekerEmail, password, UserRole.JOB_SEEKER);
        UUID employerId = createAccount(employerEmail, password, UserRole.EMPLOYER);
        UUID targetId = createAccount(targetEmail, password, UserRole.JOB_SEEKER);

        String adminToken = authService.login(new LoginRequest(adminEmail, password)).accessToken();
        String seekerToken = authService.login(new LoginRequest(seekerEmail, password)).accessToken();
        String employerToken = authService.login(new LoginRequest(employerEmail, password)).accessToken();
        String targetToken = authService.login(new LoginRequest(targetEmail, password)).accessToken();

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(adminEmail));
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + seekerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(seekerEmail));
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + employerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(employerEmail));

        mvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + seekerToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + employerToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.containsString(targetEmail)));

        mvc.perform(get("/api/admin/users/{id}", targetId))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users/{id}", targetId)
                        .header("Authorization", "Bearer " + seekerToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users/{id}", targetId)
                        .header("Authorization", "Bearer " + employerToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users/{id}", targetId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(targetEmail));

        mvc.perform(patch("/api/admin/users/{id}/suspend", targetId)
                        .header("Authorization", "Bearer " + seekerToken))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/users/{id}/suspend", targetId)
                        .header("Authorization", "Bearer " + employerToken))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/users/{id}/suspend", targetId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("SUSPENDED"));

        assertThat(userRole(adminId)).isEqualTo(UserRole.ADMIN.name());
        assertThat(userRole(seekerId)).isEqualTo(UserRole.JOB_SEEKER.name());
        assertThat(userRole(employerId)).isEqualTo(UserRole.EMPLOYER.name());
        try (Connection connection = adminConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT status::text FROM recruitment.users WHERE id = ?")) {
            statement.setObject(1, targetId);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("SUSPENDED");
            }
        }
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + targetToken))
                .andExpect(status().isUnauthorized());

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             var result = statement.executeQuery("SELECT count(*) FROM recruitment.users")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getLong(1)).isZero();
        }
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
    void featureRoutesRemainClosedAndRegistrationCannotChooseAnAdminRole() throws Exception {
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
