# Protected API end-to-end tests

`ProtectedApiE2EIT` exercises the protected API through a real HTTP server
(`RANDOM_PORT`) and the production Spring Security/JWT filters. It covers
anonymous, tampered, and expired authentication; real job seeker registration
and login; application creation, reading, update, and withdrawal; seeker and
employer resource ownership; role boundaries; employer verification and
application review; CV metadata; and authorized/unauthorized private CV access.

These are not unit tests: requests reach the running Spring Boot application,
and data is persisted through Flyway and a restricted, non-superuser PostgreSQL
runtime role. They are also not MVC/controller integration tests: they use a
real HTTP client and port rather than MockMvc. No `@WithMockUser`, mocked
authentication, synthetic JWT, or security bypass is used. Each test run starts
isolated PostgreSQL 17 and MinIO containers; MinIO uses the project's
`docker/minio.Dockerfile`, and its document bucket is private. Test credentials
are generated for the containers and are not written to logs.

Docker Desktop (Linux containers), a working Docker daemon, and the Maven
Wrapper/JDK 21 are required. The E2E test manages its own Testcontainers
database and storage, so no shared developer database, production credential,
`.env`, Compose volume, prebuilt image, or pre-running backend is used.
Testcontainers builds the MinIO image directly from the repository's
`docker/minio.Dockerfile` and starts an isolated container; no Compose command
or manual image build is needed for E2E. For separate manual Compose deployment,
Docker Compose v2 and the configured `.env` are required:

```powershell
docker compose config --quiet
docker compose up --build -d
```

Run only this end-to-end suite (Maven also runs the normal unit tests in this
command):

```powershell
.\mvnw.cmd -B -ntp '-Dit.test=ProtectedApiE2EIT' verify
```

Run only the normal unit tests:

```powershell
.\mvnw.cmd -B -ntp test
```

Run all unit tests and all existing/new Testcontainers integration tests:

```powershell
.\mvnw.cmd -B -ntp verify
```

`verify` runs the existing Failsafe integration-test lifecycle, including
PostgreSQL migration/RLS checks and MinIO storage tests. Docker startup failures
are test failures with a message identifying the required service; the suite
does not skip or convert infrastructure failures into success. Failsafe reports
are written under `target/failsafe-reports`.

The test uses unique synthetic `example.test` accounts and a fresh isolated
database per run. It uploads only generated PDF fixtures, and containers stop
after the test class. The test-specific ten-second JWT lifetime is configured only in its Spring
test context. The expiry workflow waits beyond Spring Security's default
60-second JWT clock-skew allowance; tokens still come from the real login
endpoint and pass the unchanged production signature, issuer, audience,
account-state, role, and expiry validators. Flyway migration V26 preserves the
existing application ownership/status protections while allowing Hibernate's
managed `updated_at` timestamp during supported applicant edits.
