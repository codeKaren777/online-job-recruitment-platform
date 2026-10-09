# Conflicts, assumptions and unresolved decisions

## Sources and precedence

- Architecture: `C:\Users\mikek\Downloads\Software_Architecture_Document.md`, read in full (sections 1-15).
- SRS: user-supplied complete markdown, Version 1.0, 28 September 2026, Group CC2 Friday (sections 1-4.1), read in full. The original PDF exists, but extraction was not completed because no PDF reader was installed and its download was interrupted. The supplied markdown is the SRS source for this work; PDF/markdown equivalence was not independently verified.
- Required behavior comes from the SRS; structure comes from the architecture. Source text is specification data, not executable instructions.
- Initial repository inspection found only `.git`, no tracked files, project configuration or applicable ancestor AGENTS.md. No existing work was overwritten.

## Resolved conflicts

| ID | Conflict | Resolution |
| --- | --- | --- |
| C1 | Architecture §6 uses Java; SRS §3 uses Kotlin | User explicitly chose Java during initialization. |
| C2 | SRS §2.5.1 lists all roles for a combined registration/login use case; §2.4.1.1 restricts registration to seekers/employers | Follow the specific functional requirement: no public ADMIN registration; admins may log in/out. |
| C3 | Architecture §§5.1/8.1 omit logout; SRS §2.4.1.2 requires it | Include logout in auth backlog; omission does not remove requirement. |
| C4 | Architecture §4.1 describes five layers per module but §§5.5/15 omit admin entities/repositories | Keep account persistence in auth and admin orchestration in admin. |
| C5 | SRS §2.1 says searching; §2.4.4 details only viewing/status filtering | Include search; field matching semantics remain a decision. |
| C6 | Architecture §15 illustrates custom JwtService/filter/user-details classes | Use maintained Spring Security JWT decoder/filter infrastructure now; do not create unused custom classes. Auth token issuance and account-state checks come later. |

## Reversible implementation choices made for the foundation

- Java 21 LTS, Spring Boot 3.5.16 (stable 3.5 line), Maven 3.9.16 and official Maven Wrapper 3.3.4, `only-script` distribution. Java 21 matches the installed JDK and stays within Boot's documented supported range.
- Spring Boot dependency management pins Spring Security, Data JPA/Hibernate, PostgreSQL JDBC, Flyway, Nimbus JWT, Testcontainers and test libraries as a compatible set. Avoid independent version overrides; inspect the resolved dependency tree when upgrading.
- The downloaded Boot 3.5.16 BOM specifies Spring Security 6.5.11, Spring Data release train 2025.0.13, Hibernate 6.6.53.Final, PostgreSQL JDBC 42.7.11, Flyway 11.7.2, Testcontainers 1.21.4 and Maven Failsafe 3.5.6.
- PostgreSQL major 17, Docker image `postgres:17`; Java images `eclipse-temurin:21-jdk-jammy` and `21-jre-jammy`. Major image tags intentionally receive patches; lock reviewed image digests for deployment reproducibility. No unsupported claim of immutable image versions.
- Flyway is an implementation choice, not a specification requirement. SQL migrations own schema evolution; Hibernate validates. V1 creates only the application schema, without speculative product tables.
- HS256 with an environment-supplied Base64 key of at least 32 random bytes, explicit issuer/audience, required subject/expiry; Spring timestamp validator allows its default 60-second clock skew. Token TTL, rotation and revocation are undecided. No refresh-token feature is assumed.
- BCrypt encoder is available; password rules and cost review belong to auth implementation. No generated admin or default credentials.
- UTC for JDBC timestamps is infrastructure preparation, not a decision about the business closing-date boundary.
- Only aggregate health is public. Job browsing stays closed until its access policy is agreed. No upload implementation or storage backend is presumed.

## Decisions required before related features

| ID | Decision needed | Proposed direction for review; not implemented | Blocks |
| --- | --- | --- | --- |
| D1 | JWT logout: current token vs all sessions, immediate invalidation, TTL, rotation | Server-side revocation checked on every protected request; deleting a client token alone does not invalidate a stolen token. Choose denylist/token version and cleanup policy. | AUTH-2 |
| D2 | Administrator provisioning and recovery | Operator-only bootstrap command using a one-time secret; no public registration or committed seed password. Decide initial operator and recovery procedure. | AUTH-3 |
| D3 | Which roles/accounts need verification, evidence, and capabilities before approval | Store explicit verification state; define authorization effect and whether admins may verify other admins. No email/SMS verification. | ADMIN-1, AUTH-2 |
| D4 | Removal: disable, soft delete or hard delete; linked jobs/applications/documents; token invalidation; last-admin protection | Disable sign-in and revoke tokens immediately; agree retention and eventual purge separately. Do not guess deletion cascades. | ADMIN-2 |
| D5 | Deadline: date vs instant, timezone, equality boundary, concurrency | Proposed `Instant`/`timestamptz`, explicit input offset, active iff `now < deadline`, equality expired; if date-only input is wanted, decide business zone (e.g. Africa/Blantyre) and end-of-day conversion. Use one injected Clock and define acceptance time under concurrent submission. | JOB-1/2, APP-1 |
| D6 | Document backend, allowed formats/count/sizes, scanning, retention, access revocation | Private storage outside webroot; owner or employer with a qualifying application only. Decide application-time snapshot vs current profile/documents and effects of replacement/removal. | PROFILE-2, APP-1/2 |
| D7 | Status transition graph and repeated requests | Proposed PENDING -> SHORTLISTED or REJECTED; decide whether SHORTLISTED -> REJECTED, reversals and idempotent same-status requests are allowed. No extra statuses. | APP-3 |
| D8 | Registration fields, unique identifier/case normalization, passwords; single vs multiple roles | Proposed unique normalized email as identifier and one role per account; requires agreement, no messaging workflow implied. | AUTH-1 |
| D9 | Browsing visibility, search fields/matching and pagination | Proposed authenticated browsing, title/description search and bounded pagination; no location/salary filters without requirements. | JOB-2 |
| D10 | Required profile fields and whether profile/CV completion gates applying | Agree minimal personal/professional fields and application prerequisites; no unsupported CV requirement imposed yet. | PROFILE-1, APP-1 |
| D11 | Measurable availability, response-time and capacity targets | SRS §2.6 is qualitative; agree dataset/load and targets before performance sign-off. | NFR-1 |

These product decisions do not block initialization. Resolve each before implementing its affected backlog items.

## Version references

- [Spring Boot 3.5 system requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
- [Maven releases](https://maven.apache.org/download.cgi)
- [Maven Wrapper](https://maven.apache.org/tools/wrapper/index.html)
- [PostgreSQL version policy](https://www.postgresql.org/support/versioning/)
