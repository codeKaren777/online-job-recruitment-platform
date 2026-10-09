# Group CC2 Online Job Board backend

Backend foundation only. Java 21, Spring Boot 3.5.16, Maven 3.9.16 via official Wrapper 3.3.4, PostgreSQL 17, Spring Data JPA/Hibernate, Spring Security JWT and Flyway. Spring Boot manages compatible library versions; see [decisions](docs/decisions.md) for rationale and primary version references.

Implemented: application startup wiring, environment configuration, private-by-default route policy, JWT validation, password encoder, aggregate database health, schema migration, Docker/Compose, package boundaries and foundation checks. Registration/login/logout, users, profiles, documents, jobs, applications and administration remain [backlog items](docs/backlog.md). Only GET `/actuator/health` is public. All feature paths currently reject access, including valid token holders. There are no demo credentials or seeded administrators.

## Prerequisites

For a clear first-time setup on Linux or Windows, see the [development setup guide](docs/setup.md).

- For local Maven builds: JDK 21 and internet access to Maven Central on first use. A global Maven install is not required.
- For Compose or integration tests: Docker Desktop running Linux containers with Docker Compose v2. PostgreSQL is supplied by Docker; alternatively use a local PostgreSQL 17 server for local application runs.
- PowerShell commands below run from this repository. Installation of system tools is not performed by this project.

```powershell
Set-Location 'C:\Users\mikek\OneDrive\Documents\online-job-recruitment-platform'
# This is the installed JDK path on the initialization machine; adjust elsewhere.
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
docker version
docker compose version
```

## Configure once

Create `.env` without overwriting an existing configuration, then replace placeholders with cryptographically random values. This script does not print the secrets. Values use literal KEY=VALUE syntax, without quotes or variable interpolation.

```powershell
if (-not (Test-Path -LiteralPath '.env')) { Copy-Item '.env.example' '.env' }
$config = Get-Content -Raw -LiteralPath '.env'
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    if ($config.Contains('REPLACE_WITH_RANDOM_DATABASE_PASSWORD')) {
        $bytes = New-Object byte[] 32
        $rng.GetBytes($bytes)
        $config = $config.Replace('REPLACE_WITH_RANDOM_DATABASE_PASSWORD', [Convert]::ToBase64String($bytes))
    }
    if ($config.Contains('REPLACE_WITH_BASE64_ENCODED_32_RANDOM_BYTES')) {
        $bytes = New-Object byte[] 32
        $rng.GetBytes($bytes)
        $config = $config.Replace('REPLACE_WITH_BASE64_ENCODED_32_RANDOM_BYTES', [Convert]::ToBase64String($bytes))
    }
    [IO.File]::WriteAllText((Join-Path (Get-Location) '.env'), $config, (New-Object Text.UTF8Encoding($false)))
} finally { $rng.Dispose() }
. .\scripts\Import-LocalEnv.ps1
```

Spring Boot does not automatically load `.env`; the import script sets process environment variables for local runs. Compose reads `.env` itself. Never commit `.env`, private keys or uploads. Changing POSTGRES_DB requires a matching local DB_URL. Production uses externally injected secrets; do not reuse test keys. Database users must be permitted to create the initial schema; separate migration/runtime privileges can be introduced for production.

## Test and build

```powershell
.\mvnw.cmd -B -ntp test
.\mvnw.cmd -B -ntp package
# Full startup, real PostgreSQL, Flyway, health and route checks; Docker must be running.
.\mvnw.cmd -B -ntp verify
```

`test` runs token validation and MVC route-security unit/slice checks without a database. `package` also produces `target/recruitment.jar`. `verify` additionally runs `FoundationIT` with an isolated Testcontainers PostgreSQL database, validates/re-runs Flyway and checks aggregate health. Integration tests deliberately fail if Docker is unavailable rather than silently skipping. Tests inject test-only settings and do not require `.env`. Reports are in `target/surefire-reports` and `target/failsafe-reports`.

On Unix, use `sh ./mvnw -B -ntp verify`. Wrapper downloads Maven on first run; no wrapper JAR is required. Optional project-local caches (used during initialization):

```powershell
$env:MAVEN_USER_HOME = Join-Path (Get-Location) '.m2'
.\mvnw.cmd -B -ntp '-Dmaven.repo.local=.m2/repository' verify
```

## Run locally with a Docker database

```powershell
. .\scripts\Import-LocalEnv.ps1
docker compose config --quiet
docker compose up -d --wait db
.\mvnw.cmd -B -ntp spring-boot:run
```

In a second PowerShell window:

```powershell
Invoke-RestMethod 'http://localhost:8080/actuator/health'
# Expect 401; feature routes have not been opened or implemented.
curl.exe -i 'http://localhost:8080/api/jobs'
```

With an independently installed PostgreSQL 17 server, create the database/user matching `.env`, omit `docker compose up`, import `.env` and use the same Maven command. Stop the local backend with Ctrl+C. You can also run the packaged app after importing `.env`:

```powershell
java -jar .\target\recruitment.jar
```

## Run everything in Docker Compose

Stop any local backend already using port 8080 first. No host JDK is needed for this path; the image builds with JDK 21 and runs unit tests during packaging.

```powershell
docker compose config --quiet
docker compose up --build -d
docker compose ps
docker compose logs --tail 100 backend
# Retry until the backend has finished startup; expected status is UP.
Invoke-RestMethod 'http://localhost:8080/actuator/health'
```

Startup requires a reachable database, successful migrations and a valid JWT key. Invalid credentials/secrets should fail startup, not activate fallback configuration. The health endpoint reports DOWN/503 for database failure once running. It never exposes health component details. Docker image packaging does not run Testcontainers integration tests inside the build; run `verify` separately on the host.

```powershell
# Stop containers while preserving database data.
docker compose down
```

PostgreSQL lives in the named `postgres_data` volume across container recreation. Do not use `down -v` unless you intend to erase it. Changing `.env` database credentials does not change credentials inside an already initialized volume; update the database credentials deliberately. Ports 8080 and 5432 bind only to localhost. Production needs HTTPS termination, managed secrets, backup/restore planning and reviewed image digests.

## Project guidance

- [Architecture and boundaries](docs/architecture.md)
- [Source conflicts, choices and open decisions](docs/decisions.md)
- [Requirements traceability and acceptance backlog](docs/backlog.md)
- [Verification record](docs/verification.md)
- [Future contributor/agent conventions](AGENTS.md)

Next milestone: identity and access (AUTH-1/2/3), after agreeing JWT logout/revocation, controlled administrator provisioning and verification/removal policies. Email, SMS, notifications and interviews are out of scope.
