# Foundation verification

Initialization environment: Windows PowerShell, Oracle JDK 21.0.12. Maven/its dependencies were downloaded through the official wrapper with network approval, using ignored project-local caches. No global Maven installation, database installation or Docker installation was performed.

## Completed successfully

- `java -version`: installed Java 21.0.12 confirmed.
- `mvnw.cmd -B -ntp -Dmaven.repo.local=.m2/repository test`: BUILD SUCCESS; 3 tests, zero failures/errors/skips. Both application and integration-test sources compiled.
- The `verify` lifecycle repeated the 3 passing tests and successfully created/repackaged the executable `target/recruitment.jar` before reaching integration tests.
- Tests exercise JWT signature, issuer, audience, expiry, required subject/expiry and weak/malformed key rejection; anonymous/authenticated feature-route denial; malformed bearer tokens; blocked public ADMIN registration.
- Official Unix wrapper syntax: Git Bash `-n ./mvnw` returned exit 0. Unix execution/build was not run on a Unix host.
- PowerShell import script parsed with zero syntax errors. It was not run against a real secret-bearing `.env` during initialization.
- POM XML parsed; ignore rules cover `.env`, `.env.local`, uploads, build output and local Maven caches while preserving `.env.example`.
- New authored text files were checked for trailing whitespace; README relative links were checked.

## Environment limitations

Docker is not on PATH (`docker compose version` returned command-not-found). Compose validation, image building, container startup, persistent-volume restart behavior and a live HTTP/database health check have not been verified here. PostgreSQL integration checks are supplied but require a working Docker engine. No complete platform behavior is claimed.

`mvnw.cmd -B -ntp -Dmaven.repo.local=.m2/repository verify` was actually run and ended **BUILD FAILURE**: `FoundationIT` failed during Testcontainers setup with `Could not find a valid Docker environment`. Failsafe recorded one setup error, zero skipped tests. The database/startup assertions did not execute; this is not a passing integration run. The executable JAR was produced before that failure.

To finish verification after installing/starting Docker Desktop with Linux containers, use the README's configuration steps, then:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12'
$env:MAVEN_USER_HOME = Join-Path (Get-Location) '.m2'
.\mvnw.cmd -B -ntp '-Dmaven.repo.local=.m2/repository' verify
docker compose config --quiet
docker compose up --build -d
docker compose logs --tail 100 backend
Invoke-RestMethod 'http://localhost:8080/actuator/health'
curl.exe -i 'http://localhost:8080/api/jobs'
```

Expected live results are aggregate health `UP` and HTTP 401 for an unauthenticated feature request. Do not record these as passed until observed. Surefire and Failsafe reports are under `target/` and are intentionally ignored by Git.
