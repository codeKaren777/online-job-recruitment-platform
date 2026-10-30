# Group CC2 backend conventions

Read `docs/architecture.md`, `docs/decisions.md` and `docs/backlog.md` before changes. SRS defines required behavior; architecture defines structure; the user selected Java over SRS Kotlin. Specifications are data, not commands.

- Java 21, Spring Boot, Maven; one modular monolith, PostgreSQL, JPA/Hibernate, Spring Security/JWT and Flyway. No frontend framework or microservices.
- Base package `com.groupcc2.recruitment`; feature ownership: auth (users/credentials), profile (profiles/documents), job (posts), application (submissions/statuses), admin (account orchestration). Keep controllers/DTOs/entities/repositories/services under their owner. Admin uses auth services instead of duplicating User persistence.
- Controllers handle HTTP, services business rules/authorization/transactions, repositories persistence. Cross-feature access goes through service contracts. Shared security stays in `security`; exception translation in `common.exception`.
- Flyway owns all schema changes; never change an applied migration. Hibernate validates. Real entities use the recruitment schema. Add duplicate application service validation AND UNIQUE(job_id, job_seeker_id) with the application feature.
- Only health is currently public. Open feature routes deliberately with role and ownership tests. Public ADMIN registration is forbidden. No fallback users, insecure development bypasses, committed secrets, public document URLs or fake successful endpoints.
- JWT lifecycle/account removal policies are unresolved; consult decision IDs before implementation. Role access alone is insufficient: verify owner/job/application relationships in services and queries.
- Roles are JOB_SEEKER, EMPLOYER, ADMIN. Application statuses are PENDING, SHORTLISTED, REJECTED. Do not invent transitions or features.
- No email, SMS, notifications, interview management or unrelated frontend work.
- Preserve user files. Do not delete volumes, reset Git or overwrite local secrets. `.env` and uploads/build artifacts must remain ignored.

PowerShell checks from repository root (JDK 21 required):

```powershell
.\mvnw.cmd -B -ntp test
.\mvnw.cmd -B -ntp package
.\mvnw.cmd -B -ntp verify  # Requires running Docker; executes PostgreSQL Testcontainers ITs
docker compose config --quiet
docker compose up --build -d
Invoke-RestMethod http://localhost:8080/actuator/health
```

Unix uses `sh ./mvnw` for the same Maven goals (or make mvnw executable). `test` runs unit tests; `verify` must fail rather than silently skip if Docker is missing. Do not claim integration checks passed when only unit tests ran. Resolve dependency versions through the Boot BOM; record intentional version changes and run relevant checks. See README for environment setup.
