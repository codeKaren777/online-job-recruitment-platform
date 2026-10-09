# Architecture summary

One Spring Boot process exposes a JSON REST API and uses one PostgreSQL database. Maven builds the application; Docker Compose runs the application and database. No frontend framework or microservices are selected.

Base package: `com.groupcc2.recruitment`.

| Module | Ownership | Planned interactions |
| --- | --- | --- |
| `auth` | Users, credentials, roles, registration, login/logout | Supplies user identity and account lifecycle services |
| `profile` | Job seeker profiles, CV/document metadata and storage access | Uses authenticated identity; checks application-based employer access |
| `job` | Job posts, employer ownership, requirements, deadline and search | Supplies eligibility and ownership checks to application services |
| `application` | Applications, duplicate prevention, review/status transitions | Calls job/profile services for eligibility and authorized applicant access |
| `admin` | User listing, verification/removal orchestration | Uses auth-owned account services; does not duplicate User persistence |
| `security` | Spring Security, JWT validation, password encoding | Auth will issue tokens; features enforce roles and record ownership |
| `common.exception` | Future shared HTTP exception translation | No business logic or database access |

Each feature owns controller, DTO, service, entity and repository subpackages. Admin has controller, DTO and service only, following Architecture §§5.5 and 15; add persistence only if it later owns data. Package documentation preserves boundaries without dummy classes.

Controllers translate HTTP/DTOs; services own business validation, authorization and transactions; repositories own persistence. Do not expose entities as response DTOs. Cross-module operations use service contracts, not another module's repository or controller. Coordinate foreign keys and schema changes across owners.

The scaffold has no business entities or feature endpoints. Flyway V1 creates the `recruitment` schema, Hibernate targets it with `ddl-auto=validate`, and Flyway history remains in PostgreSQL's default `public` schema. Add versioned SQL migrations alongside real entities; never edit an applied migration. No automatic schema update or production sample data.

Only GET `/actuator/health` is public. It reports aggregate health including the database, with no component details. All other routes are denied, including valid JWT callers, until a feature deliberately adds tested route/role/ownership rules. Authentication failures use HTTP 401; authenticated callers to closed routes receive 403. There is no development bypass, default user, HTTP Basic login, form login, or fabricated feature response.

Spring Security's resource-server implementation validates HS256 signatures, issuer, audience, subject, expiry and timestamp validity. It replaces the illustrative custom JWT filter in Architecture §15. No token issuance, role mapping or logout implementation exists yet. Bearer headers and stateless sessions justify disabled CSRF; reassess if cookies are introduced. No cross-origin allowlist is enabled. Multipart uploads remain disabled until document security is implemented. Production HTTPS termination and secret injection belong to deployment configuration.

Compose binds host ports to loopback for local development, waits for database readiness, persists PostgreSQL data, and runs the application as a non-root user with a read-only root filesystem and writable temporary directory. This is a development foundation, not a complete production deployment.
