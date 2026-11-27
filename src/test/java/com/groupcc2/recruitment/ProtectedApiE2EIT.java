package com.groupcc2.recruitment;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.lifecycle.Startable;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * Full HTTP/security-stack workflows against isolated PostgreSQL and private MinIO containers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProtectedApiE2EIT {
    private static final String RUNTIME_USER = "protected_api_e2e";
    private static final String RUNTIME_PASSWORD = "e2e-" + UUID.randomUUID().toString().replace("-", "");
    private static final String MINIO_ROOT_USER = "e2e-" + UUID.randomUUID().toString().replace("-", "");
    private static final String MINIO_ROOT_PASSWORD = "e2e-" + UUID.randomUUID().toString().replace("-", "");
    private static final String DOCUMENT_BUCKET = "protected-api-e2e-documents";
    private static final String BOOTSTRAP_ADMIN_EMAIL = "e2e-admin-"
            + UUID.randomUUID().toString().replace("-", "") + "@example.test";
    private static final String BOOTSTRAP_ADMIN_PASSWORD = "SyntheticE2eAdminPassword123!";
    private static final String USER_PASSWORD = "SyntheticE2eUserPassword123!";
    private static final long ACCESS_TOKEN_LIFETIME_SECONDS = 10;
    private static final long DEFAULT_JWT_CLOCK_SKEW_SECONDS = 60;
    private static final byte[] SYNTHETIC_PDF =
            "%PDF-1.7\nSynthetic E2E document only.\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);
    private static final String JWT_SECRET_BASE64 = createJwtSecret();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");
    private static final GenericContainer<?> MINIO = new GenericContainer<>(
            new ImageFromDockerfile().withFileFromPath("Dockerfile", Path.of("docker/minio.Dockerfile")))
            .withEnv("MINIO_ROOT_USER", MINIO_ROOT_USER)
            .withEnv("MINIO_ROOT_PASSWORD", MINIO_ROOT_PASSWORD)
            .withCommand("server", "/data", "--console-address", ":9001")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    static {
        startRequiredService("PostgreSQL", POSTGRES);
        startRequiredService("MinIO", MINIO);
        initializePostgres();
        initializePrivateBucket();
    }

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> RUNTIME_USER);
        registry.add("spring.datasource.password", () -> RUNTIME_PASSWORD);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.flyway.default-schema", () -> "public");
        registry.add("app.security.jwt.secret-base64", () -> JWT_SECRET_BASE64);
        registry.add("app.security.jwt.access-token-expiration",
                () -> "PT" + ACCESS_TOKEN_LIFETIME_SECONDS + "S");
        registry.add("app.auth.bootstrap-admin.email", () -> BOOTSTRAP_ADMIN_EMAIL);
        registry.add("app.auth.bootstrap-admin.password", () -> BOOTSTRAP_ADMIN_PASSWORD);
        registry.add("app.profile.storage.enabled", () -> "true");
        registry.add("app.profile.storage.endpoint", ProtectedApiE2EIT::minioEndpoint);
        registry.add("app.profile.storage.public-endpoint", ProtectedApiE2EIT::minioEndpoint);
        registry.add("app.profile.storage.region", () -> "us-east-1");
        registry.add("app.profile.storage.bucket", () -> DOCUMENT_BUCKET);
        registry.add("app.profile.storage.access-key", () -> MINIO_ROOT_USER);
        registry.add("app.profile.storage.secret-key", () -> MINIO_ROOT_PASSWORD);
        registry.add("app.profile.storage.path-style", () -> "true");
    }

    @AfterAll
    static void stopDockerServices() {
        MINIO.stop();
        POSTGRES.stop();
    }

    @Test
    void authenticationUsesRealLoginTokensAndRejectsAnonymousTamperedAndExpiredTokens() throws Exception {
        Account seeker = registerJobSeeker();

        String validToken = login(seeker);
        JsonNode currentUser = expectJson(
                request(HttpMethod.GET, "/api/auth/me", validToken, null), 200,
                "A real job seeker JWT authenticates through HTTP");
        assertThat(currentUser.path("email").asText()).isEqualTo(seeker.email());
        assertThat(currentUser.path("role").asText()).isEqualTo("JOB_SEEKER");

        assertApiError(request(HttpMethod.GET, "/api/auth/me", null, null), 401,
                "AUTHENTICATION_REQUIRED", "Authentication is required.",
                "Anonymous protected request");

        assertApiError(request(HttpMethod.GET, "/api/auth/me", tamperSignature(validToken), null), 401,
                "AUTHENTICATION_REQUIRED", "Authentication is required.",
                "Tampered JWT");

        String expiredToken = login(seeker);
        Thread.sleep(Duration.ofSeconds(
                ACCESS_TOKEN_LIFETIME_SECONDS + DEFAULT_JWT_CLOCK_SKEW_SECONDS + 2).toMillis());
        assertApiError(request(HttpMethod.GET, "/api/auth/me", expiredToken, null), 401,
                "AUTHENTICATION_REQUIRED", "Authentication is required.",
                "Legitimately issued expired JWT");
    }

    @Test
    void jobSeekersCanManageOnlyTheirOwnApplications() throws Exception {
        Employer employer = createEmployer();
        UUID jobId = createJob(employer, "Seeker ownership E2E job");
        Account seekerA = registerJobSeeker();
        Account seekerB = registerJobSeeker();
        saveProfile(seekerA, "Synthetic Applicant A");
        saveProfile(seekerB, "Synthetic Applicant B");

        UUID applicationA = submitApplication(seekerA, jobId);
        JsonNode ownApplications = expectJson(
                requestAs(seekerA, HttpMethod.GET, "/api/applications", null), 200,
                "Job seeker A lists their applications");
        assertThat(ownApplications.isArray()).isTrue();
        assertThat(ownApplications.size()).isEqualTo(1);
        assertThat(ownApplications.get(0).path("id").asText()).isEqualTo(applicationA.toString());

        JsonNode ownApplication = expectJson(requestAs(seekerA, HttpMethod.GET,
                        "/api/applications/" + applicationA, null), 200,
                "Job seeker A reads their application");
        assertThat(ownApplication.path("status").asText()).isEqualTo("PENDING");

        JsonNode updated = expectJson(requestAs(seekerA, HttpMethod.PATCH,
                        "/api/applications/" + applicationA, Map.of("coverLetter", "Updated synthetic cover letter.")),
                200, "Job seeker A updates their pending application");
        assertThat(updated.path("coverLetter").asText()).isEqualTo("Updated synthetic cover letter.");

        ResponseEntity<JsonNode> withdrawn = requestAs(seekerA, HttpMethod.DELETE,
                "/api/applications/" + applicationA, null);
        assertThat(withdrawn.getStatusCode().value()).as("Job seeker A withdraws their application").isEqualTo(204);

        UUID applicationB = submitApplication(seekerB, jobId);
        JsonNode seekerBOwnApplication = expectJson(requestAs(seekerB, HttpMethod.GET,
                        "/api/applications/" + applicationB, null), 200,
                "Job seeker B reads their application");
        assertThat(seekerBOwnApplication.path("id").asText()).isEqualTo(applicationB.toString());
        assertApiError(requestAs(seekerA, HttpMethod.GET, "/api/applications/" + applicationB, null), 404,
                "NOT_FOUND", "The requested resource was not found.",
                "Job seeker A cannot access job seeker B application");
        assertApiError(requestAs(seekerA, HttpMethod.PATCH, "/api/applications/" + applicationB,
                        Map.of("coverLetter", "Unauthorized synthetic change.")), 404,
                "NOT_FOUND", "The requested resource was not found.",
                "Job seeker A cannot modify job seeker B application");

        assertApiError(requestAs(seekerA, HttpMethod.GET, "/api/employer/applications", null), 403,
                "FORBIDDEN", "You do not have permission to perform this action.",
                "Job seeker cannot use employer application workflow");
        assertApiError(requestAs(employer.account(), HttpMethod.GET, "/api/applications", null), 403,
                "FORBIDDEN", "You do not have permission to perform this action.",
                "Employer cannot use job seeker application workflow");
    }

    @Test
    void employersCanReviewOnlyOwnedApplicationsAndAccessPrivateApplicationCvs() throws Exception {
        Employer employerA = createEmployer();
        Employer employerB = createEmployer();
        UUID jobA = createJob(employerA, "Employer A E2E job");
        UUID jobB = createJob(employerB, "Employer B E2E job");
        Account seeker = registerJobSeeker();
        saveProfile(seeker, "Synthetic CV Applicant");
        UUID applicationA = submitApplication(seeker, jobA);
        UUID applicationB = submitApplication(seeker, jobB);

        JsonNode employerAList = expectJson(requestAs(employerA.account(), HttpMethod.GET,
                        "/api/employer/applications", null), 200,
                "Employer A lists applications for owned jobs");
        assertThat(employerAList.isArray()).isTrue();
        assertThat(employerAList.size()).isEqualTo(1);
        assertThat(employerAList.get(0).path("id").asText()).isEqualTo(applicationA.toString());
        assertThat(employerAList.get(0).path("cv").path("originalFilename").asText())
                .isEqualTo("synthetic-cv.pdf");
        assertThat(employerAList.get(0).path("cv").path("contentType").asText())
                .isEqualTo("application/pdf");

        JsonNode employerADetail = expectJson(requestAs(employerA.account(), HttpMethod.GET,
                        "/api/employer/applications/" + applicationA, null), 200,
                "Employer A reads an application for an owned job");
        assertThat(employerADetail.path("cv").path("originalFilename").asText())
                .isEqualTo("synthetic-cv.pdf");
        JsonNode applicantProfile = expectJson(requestAs(employerA.account(), HttpMethod.GET,
                        "/api/employer/applications/" + applicationA + "/profile", null), 200,
                "Employer A reads the profile for an owned application");
        assertThat(applicantProfile.path("displayName").asText()).isEqualTo("Synthetic CV Applicant");

        JsonNode download = expectJson(requestAs(employerA.account(), HttpMethod.GET,
                        "/api/employer/applications/" + applicationA + "/cv", null), 200,
                "Employer A receives a private CV download");
        downloadSyntheticPdfWithoutExposingUrl(download);

        JsonNode changedStatus = expectJson(requestAs(employerA.account(), HttpMethod.PATCH,
                        "/api/employer/applications/" + applicationA + "/status",
                        Map.of("status", "SHORTLISTED")), 200,
                "Employer A reviews an application for an owned job");
        assertThat(changedStatus.path("status").asText()).isEqualTo("SHORTLISTED");

        assertApiError(requestAs(employerB.account(), HttpMethod.GET,
                        "/api/employer/applications/" + applicationA, null), 404,
                "NOT_FOUND", "The requested resource was not found.",
                "Employer B cannot access Employer A application");
        assertApiError(requestAs(employerB.account(), HttpMethod.PATCH,
                        "/api/employer/applications/" + applicationA + "/status",
                        Map.of("status", "REJECTED")), 404,
                "NOT_FOUND", "The requested resource was not found.",
                "Employer B cannot modify Employer A application");
        assertApiError(requestAs(employerB.account(), HttpMethod.GET,
                        "/api/employer/applications/" + applicationA + "/cv", null), 404,
                "NOT_FOUND", "The requested resource was not found.",
                "Employer B cannot download Employer A application CV");

        JsonNode employerBOwnApplication = expectJson(requestAs(employerB.account(), HttpMethod.GET,
                        "/api/employer/applications/" + applicationB, null), 200,
                "Employer B reads an application for an owned job");
        assertThat(employerBOwnApplication.path("id").asText()).isEqualTo(applicationB.toString());

        assertApiError(requestAs(seeker, HttpMethod.GET,
                        "/api/employer/applications/" + applicationA + "/cv", null), 403,
                "FORBIDDEN", "You do not have permission to perform this action.",
                "Job seeker cannot access employer-only CV endpoint");
        assertApiError(request(HttpMethod.GET,
                        "/api/employer/applications/" + applicationA + "/cv", null, null), 401,
                "AUTHENTICATION_REQUIRED", "Authentication is required.",
                "Anonymous user cannot access application CV");
        assertApiError(requestAs(employerA.account(), HttpMethod.GET, "/api/applications", null), 403,
                "FORBIDDEN", "You do not have permission to perform this action.",
                "Employer cannot use job seeker application workflow");

        assertAnonymousMinioAccessIsDenied();
    }

    private Account registerJobSeeker() throws Exception {
        Account account = new Account(
                "e2e-" + UUID.randomUUID().toString().replace("-", "") + "@example.test",
                USER_PASSWORD);
        JsonNode registered = expectJson(request(HttpMethod.POST, "/api/auth/register", null,
                        Map.of("email", account.email(), "password", account.password())),
                201, "Synthetic job seeker registration");
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.prepareStatement(
                     "UPDATE recruitment.users SET email_verified = TRUE WHERE email = ?")) {
            statement.setString(1, account.email());
            statement.executeUpdate();
        }
        assertThat(registered.path("role").asText()).isEqualTo("JOB_SEEKER");
        return account;
    }

    private String login(Account account) throws Exception {
        JsonNode response = expectJson(request(HttpMethod.POST, "/api/auth/login", null,
                        Map.of("email", account.email(), "password", account.password())),
                200, "Login issues a real bearer token");
        assertThat(response.path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(response.path("expiresIn").asLong()).isEqualTo(ACCESS_TOKEN_LIFETIME_SECONDS);
        String token = response.path("accessToken").asText();
        assertThat(token).isNotBlank();
        return token;
    }

    private Employer createEmployer() throws Exception {
        Account applicant = registerJobSeeker();
        String suffix = UUID.randomUUID().toString().replace("-", "");
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("application", new HttpEntity<>(objectMapper.writeValueAsString(Map.of(
                "companyName", "Synthetic E2E Company " + suffix,
                "businessDescription", "Synthetic company for protected API E2E tests.",
                "contactEmail", "company-" + suffix + "@example.test",
                "contactPhone", "+265999000111",
                "location", "Blantyre",
                "vatNumber", "E2E-VAT-" + suffix,
                "registrationNumber", "E2E-REG-" + suffix)), jsonPartHeaders()));
        parts.add("document", pdfPart("synthetic-registration.pdf"));

        JsonNode submitted = expectJson(requestMultipartAs(applicant,
                        "/api/employer-requests", parts), 201,
                "Job seeker submits synthetic employer verification");
        String requestId = submitted.path("id").asText();
        assertThat(submitted.path("status").asText()).isEqualTo("PENDING");

        Account admin = new Account(BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD);
        JsonNode approved = expectJson(requestAs(admin, HttpMethod.PATCH,
                        "/api/admin/employer-requests/" + requestId + "/approve", null), 200,
                "Administrator approves synthetic employer verification");
        assertThat(approved.path("status").asText()).isEqualTo("APPROVED");
        String companyId = approved.path("companyId").asText();
        assertThat(companyId).isNotBlank();

        JsonNode authenticatedEmployer = expectJson(requestAs(applicant, HttpMethod.GET,
                        "/api/auth/me", null), 200,
                "Approved employer authenticates with the employer role");
        assertThat(authenticatedEmployer.path("role").asText()).isEqualTo("EMPLOYER");
        return new Employer(applicant, UUID.fromString(companyId));
    }

    private UUID createJob(Employer employer, String title) throws Exception {
        Map<String, Object> job = new LinkedHashMap<>();
        job.put("companyId", employer.companyId().toString());
        job.put("title", title);
        job.put("description", "Synthetic job posting for protected API E2E tests.");
        job.put("requiredQualifications", "Java 21 and Spring Boot");
        job.put("dueDate", java.time.LocalDate.now().plusYears(1).toString());
        job.put("location", "Blantyre");
        JsonNode created = expectJson(requestAs(employer.account(), HttpMethod.POST, "/api/jobs", job),
                201, "Employer creates a job for its own company");
        return UUID.fromString(created.path("id").asText());
    }

    private void saveProfile(Account seeker, String displayName) throws Exception {
        JsonNode profile = expectJson(requestAs(seeker, HttpMethod.PUT, "/api/profile",
                        Map.of("displayName", displayName, "biography", "Synthetic E2E test profile.")),
                200, "Job seeker creates their profile");
        assertThat(profile.path("displayName").asText()).isEqualTo(displayName);
    }

    private UUID submitApplication(Account seeker, UUID jobId) throws Exception {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("jobId", jobId.toString());
        parts.add("coverLetter", "Synthetic cover letter for an isolated E2E workflow.");
        parts.add("cv", pdfPart("synthetic-cv.pdf"));
        JsonNode submitted = expectJson(requestMultipartAs(seeker, "/api/applications", parts),
                201, "Job seeker submits a synthetic application CV");
        assertThat(submitted.path("status").asText()).isEqualTo("PENDING");
        return UUID.fromString(submitted.path("id").asText());
    }

    private ResponseEntity<JsonNode> requestAs(Account account, HttpMethod method,
                                                String path, Object body) throws Exception {
        return request(method, path, login(account), body);
    }

    private ResponseEntity<JsonNode> request(HttpMethod method, String path,
                                             String token, Object body) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        if (token != null) {
            headers.setBearerAuth(token);
        }
        String json = body == null ? null : objectMapper.writeValueAsString(body);
        return http.exchange(path, method, new HttpEntity<>(json, headers), JsonNode.class);
    }

    private ResponseEntity<JsonNode> requestMultipartAs(Account account, String path,
                                                         MultiValueMap<String, Object> parts) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(login(account));
        return http.exchange(path, HttpMethod.POST, new HttpEntity<>(parts, headers), JsonNode.class);
    }

    private static HttpHeaders jsonPartHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private static HttpEntity<ByteArrayResource> pdfPart(String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        ByteArrayResource resource = new ByteArrayResource(SYNTHETIC_PDF) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        return new HttpEntity<>(resource, headers);
    }

    private void downloadSyntheticPdfWithoutExposingUrl(JsonNode download) {
        assertThat(download.path("expiresAt").asText()).isNotBlank();
        String url = download.path("url").asText();
        assertThat(url).isNotBlank();

        URI signedUri;
        try {
            signedUri = URI.create(url);
        } catch (IllegalArgumentException invalidPrivateUrl) {
            throw new AssertionError("Authorized employer CV download returned an invalid private URL");
        }

        HttpResponse<byte[]> document;
        try {
            document = HTTP_CLIENT.send(HttpRequest.newBuilder(signedUri).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException downloadFailure) {
            throw new AssertionError("Authorized employer could not retrieve the synthetic application CV");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Authorized employer CV download was interrupted");
        }
        assertThat(document.statusCode()).as("Authorized employer retrieves the permitted private CV").isEqualTo(200);
        assertThat(document.headers().firstValue("Content-Type").orElse(""))
                .as("Authorized download returns a PDF")
                .startsWith("application/pdf");
        assertThat(document.body().length).as("Synthetic PDF has content").isGreaterThan(12);
    }

    private void assertAnonymousMinioAccessIsDenied() throws Exception {
        try (S3Client client = S3Client.builder()
                .endpointOverride(URI.create(minioEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(MINIO_ROOT_USER, MINIO_ROOT_PASSWORD)))
                .region(Region.US_EAST_1)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build()) {
            var objects = client.listObjectsV2(request -> request.bucket(DOCUMENT_BUCKET));
            assertThat(objects.contents()).isNotEmpty();
            String objectKey = objects.contents().get(0).key();
            URI unsignedObjectUri = URI.create(minioEndpoint() + "/" + DOCUMENT_BUCKET + "/" + objectKey);
            HttpResponse<Void> response = HTTP_CLIENT.send(
                    HttpRequest.newBuilder(unsignedObjectUri).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            assertThat(response.statusCode()).as("MinIO application documents remain private").isEqualTo(403);
        }
    }

    private static void assertApiError(ResponseEntity<JsonNode> response, int status, String code,
                                       String message, String workflow) {
        assertThat(response.getStatusCode().value()).as(workflow).isEqualTo(status);
        JsonNode error = response.getBody();
        assertThat(error).as(workflow + " uses the shared API error contract").isNotNull();
        assertThat(error.path("status").asInt()).as(workflow + " error status").isEqualTo(status);
        assertThat(error.path("code").asText()).as(workflow + " stable error code").isEqualTo(code);
        assertThat(error.path("message").asText()).as(workflow + " safe error message").isEqualTo(message);
        assertThat(error.path("correlationId").asText()).as(workflow + " correlation ID").isNotBlank();
    }

    private static JsonNode expectJson(ResponseEntity<JsonNode> response, int status, String workflow) {
        assertThat(response.getStatusCode().value()).as(workflow).isEqualTo(status);
        JsonNode body = response.getBody();
        assertThat(body).as(workflow + " response body").isNotNull();
        return body;
    }

    private static String tamperSignature(String token) {
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || parts[2].isEmpty()) {
            throw new AssertionError("Login did not return a signed JWT");
        }
        char first = parts[2].charAt(0);
        parts[2] = (first == 'A' ? "B" : "A") + parts[2].substring(1);
        return String.join(".", parts);
    }

    private static void startRequiredService(String name, Startable service) {
        try {
            service.start();
        } catch (RuntimeException failure) {
            throw new IllegalStateException(
                    "Protected API E2E requires a working Docker daemon and its " + name + " container.", failure);
        }
    }

    private static void initializePostgres() {
        try {
            Flyway.configure()
                    .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                    .defaultSchema("public")
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();
            try (Connection connection = DriverManager.getConnection(
                    POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                 Statement statement = connection.createStatement()) {
                statement.execute("CREATE ROLE " + RUNTIME_USER + " LOGIN PASSWORD '"
                        + RUNTIME_PASSWORD + "' NOSUPERUSER NOBYPASSRLS");
                statement.execute("GRANT USAGE ON SCHEMA recruitment TO " + RUNTIME_USER);
                statement.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA recruitment TO "
                        + RUNTIME_USER);
                statement.execute("GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA recruitment TO "
                        + RUNTIME_USER);
                statement.execute("GRANT USAGE ON TYPE recruitment.user_role, recruitment.account_status, "
                        + "recruitment.job_status, recruitment.application_status, "
                        + "recruitment.employer_request_status TO " + RUNTIME_USER);
                statement.execute("GRANT SELECT ON recruitment.current_jobs TO " + RUNTIME_USER);
                statement.execute("GRANT EXECUTE ON FUNCTION recruitment.auth_lookup_user(text), "
                        + "recruitment.auth_bootstrap_admin(text, text) TO " + RUNTIME_USER);
                statement.execute("GRANT EXECUTE ON FUNCTION recruitment.auth_register_user(text, text, text) TO "
                        + RUNTIME_USER);
                statement.execute("GRANT EXECUTE ON FUNCTION recruitment.auth_store_password_reset_token"
                        + "(text, text, timestamptz), recruitment.auth_consume_password_reset_token(text, text) TO "
                        + RUNTIME_USER);
                statement.execute("GRANT EXECUTE ON FUNCTION recruitment.auth_store_email_verification"
                        + "(text, text, timestamptz), recruitment.auth_consume_email_verification(text) TO "
                        + RUNTIME_USER);
                statement.execute("GRANT EXECUTE ON FUNCTION recruitment.auth_store_email_verification"
                        + "(text, text, timestamptz), recruitment.auth_consume_email_verification(text) TO "
                        + RUNTIME_USER);
                statement.execute("REVOKE ALL ON recruitment.application_notification_outbox FROM " + RUNTIME_USER);
                statement.execute("GRANT EXECUTE ON FUNCTION recruitment.enqueue_application_notification"
                        + "(uuid, uuid, text), recruitment.claim_application_notifications(integer), "
                        + "recruitment.mark_application_notification_sent(uuid, uuid), "
                        + "recruitment.record_application_notification_failure(uuid, uuid, text, boolean) TO "
                        + RUNTIME_USER);
            }
        } catch (Exception failure) {
            throw new ExceptionInInitializerError(new IllegalStateException(
                    "Unable to initialize the isolated PostgreSQL runtime role for Protected API E2E.", failure));
        }
    }

    private static void initializePrivateBucket() {
        try (S3Client client = S3Client.builder()
                .endpointOverride(URI.create(minioEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(MINIO_ROOT_USER, MINIO_ROOT_PASSWORD)))
                .region(Region.US_EAST_1)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build()) {
            client.createBucket(request -> request.bucket(DOCUMENT_BUCKET));
        } catch (RuntimeException failure) {
            throw new ExceptionInInitializerError(new IllegalStateException(
                    "Unable to initialize the isolated private MinIO bucket for Protected API E2E.", failure));
        }
    }

    private static String minioEndpoint() {
        return "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
    }

    private static String createJwtSecret() {
        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    private record Account(String email, String password) {
    }

    private record Employer(Account account, UUID companyId) {
    }
}
