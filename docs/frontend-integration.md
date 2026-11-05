# Frontend integration guide

This document describes how to use the provided backend Docker image. The
backend is a REST/JSON API with a PostgreSQL database and private MinIO
document storage.

## Run the supplied backend image

The backend listens on port `8080`. Replace the example image name and tag with
the image that was provided. Run it with the required environment variables and
a network route to PostgreSQL:

```text
http://localhost:8080
```

Example image-only startup:

```bash
docker run --name recruitment-backend \
  --publish 8080:8080 \
  --env-file backend.env \
  registry.example.com/recruitment-backend:1.0
```

`backend.env` contains backend-only configuration and must not be exposed to
the browser. The image expects a reachable PostgreSQL database and a valid JWT
signing secret.

For a complete local stack, run PostgreSQL and MinIO separately and provide
their connection values to the backend. The API is ready when this returns
successfully:

```bash
curl --fail http://localhost:8080/actuator/health
```

For local Compose deployments, publish the backend on localhost. In another
environment, publish it through an HTTPS reverse proxy and use that public URL
as the frontend API base URL. Do not run `docker compose down -v` during normal
development because it deletes the PostgreSQL and MinIO volumes.

When the `local` profile is enabled, API documentation is available at
`http://localhost:8080/swagger-ui/index.html` and
`http://localhost:8080/v3/api-docs`. Swagger operations still require the
appropriate bearer token.

## Frontend environment values

Only the API base URL belongs in a browser-exposed frontend environment:

```text
VITE_API_BASE_URL=http://localhost:8080
# or, for another frontend build tool:
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
```

Use the variable name required by the frontend build system. Do not put
`POSTGRES_*`, `DB_URL`, `JWT_SECRET_BASE64`, MinIO credentials, SMTP
credentials, or reset-mail settings in frontend environment variables. Values
with `VITE_`, `NEXT_PUBLIC_`, or equivalent public prefixes are delivered to
the browser and are not secrets.

The backend configuration is supplied to the container by the deployment
environment. The important values are:

| Variable | Purpose | Required |
|---|---|---|
| `POSTGRES_DB` | PostgreSQL database name | Yes |
| `DB_URL` | JDBC URL used by the backend, for example `jdbc:postgresql://db:5432/recruitment` inside Compose | Yes |
| `POSTGRES_RUNTIME_USER` / `POSTGRES_RUNTIME_PASSWORD` | Restricted runtime database account used by Spring Boot in Compose | Yes |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | Bootstrap database account used by PostgreSQL initialization and Flyway | Yes |
| `SPRING_FLYWAY_USER` / `SPRING_FLYWAY_PASSWORD` | Bootstrap account used to apply migrations | Yes in Compose |
| `JWT_SECRET_BASE64` | Base64-encoded random JWT signing key; use at least 32 random bytes | Yes |
| `JWT_ISSUER` / `JWT_AUDIENCE` | JWT issuer and audience; defaults are `groupcc2-recruitment` and `groupcc2-api` | No |
| `APP_BOOTSTRAP_ADMIN_EMAIL` / `APP_BOOTSTRAP_ADMIN_PASSWORD` | Optional first-start administrator credentials; use only when no administrator exists, then remove them | No |
| `SPRING_PROFILES_ACTIVE` | Spring profile; Compose uses `local` to enable local API documentation | No |
| `APP_OPENAPI_ENABLED` | Enables OpenAPI/Swagger when set to `true` | No; defaults to `false` |
| `PROFILE_STORAGE_ENABLED` | Enables private document storage | No; defaults to `false` |
| `PROFILE_STORAGE_ENDPOINT` | Backend-to-MinIO endpoint | No |
| `PROFILE_STORAGE_PUBLIC_ENDPOINT` | Hostname used in short-lived browser download URLs | No |
| `PROFILE_STORAGE_REGION` | MinIO/S3 region; defaults to `us-east-1` | No |
| `PROFILE_STORAGE_BUCKET` | Private document bucket | No |
| `PROFILE_STORAGE_ACCESS_KEY` / `PROFILE_STORAGE_SECRET_KEY` | Restricted MinIO application credentials | Required when storage is enabled |
| `PROFILE_DOWNLOAD_SECONDS` | Signed download URL lifetime, from 30 to 300 seconds | No; defaults to `120` |
| `APP_MAIL_ENABLED` | Enables password-reset/application notification delivery | No; defaults to `false` |
| `APP_MAIL_FROM` | Sender address when mail is enabled | Required when mail is enabled |
| `APP_MAIL_POLL_INTERVAL_MS` | Notification outbox polling interval | No; defaults to `30000` |
| `PASSWORD_RESET_URL` | Frontend URL included in password-reset mail flows | Required for usable reset emails |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | SMTP connection settings when mail is enabled | Required when mail is enabled |
| `SPRING_MAIL_AUTH` / `SPRING_MAIL_STARTTLS_ENABLE` | SMTP authentication and STARTTLS switches | No; both default to `true` |

Use `.env.example` as the backend configuration template. Keep backend
environment files outside source control and inject production values through
the deployment secret manager. Only the API base URL belongs in frontend
configuration.

### Create the first administrator

Public registration creates `JOB_SEEKER` accounts only. To create the first
administrator, set both backend-only variables before the first startup:

```text
APP_BOOTSTRAP_ADMIN_EMAIL=admin@example.com
APP_BOOTSTRAP_ADMIN_PASSWORD=Use-a-new-12-character-password
```

The backend creates an active `ADMIN` account only if the database does not
already contain an administrator. Passwords must be 12–72 UTF-8 bytes. After
the backend starts successfully, remove both bootstrap variables and restart
the container. They are never sent through the frontend or exposed as API
fields.

If an administrator already exists, the bootstrap values are ignored and the
existing account is not changed. If the configured email already belongs to a
non-admin account, startup fails instead of changing that account. Log in using
`POST /api/auth/login`, then use the protected admin endpoints to manage users.

## Calling the API

All paths below are relative to the API base URL. Send JSON with
`Content-Type: application/json`; send uploaded files as
`multipart/form-data`. For protected routes, send:

```http
Authorization: Bearer <accessToken>
```

The login response contains `accessToken`, `tokenType` (`Bearer`), and
`expiresIn`. Store the token only according to the frontend's security policy,
never in URLs, and remove it after logout or a `401` response. The token's
subject is the user UUID; clients must not send an alternative user ID to
choose an owner.

### Public and health endpoints

| Method | Path | Use |
|---|---|---|
| `GET` | `/actuator/health` | Backend readiness check. |
| `POST` | `/api/auth/register` | Create a `JOB_SEEKER` account. Public registration cannot create employers or admins. |
| `POST` | `/api/auth/login` | Exchange email and password for a bearer token. |
| `POST` | `/api/auth/forgot-password` | Start password recovery; response does not reveal whether the email exists. |
| `POST` | `/api/auth/reset-password` | Set a new password with the one-time reset token. |

Example registration:

```json
{
  "email": "applicant@example.com",
  "password": "StrongPassword123!",
  "role": "JOB_SEEKER"
}
```

Passwords must be 12–72 characters and no more than 72 UTF-8 bytes.

### Authenticated account endpoints

| Role | Method | Path | Success |
|---|---|---|---|
| Any active role | `GET` | `/api/auth/me` or `/api/users/me` | Current account |
| Any active role | `POST` | `/api/auth/logout` | `204`; invalidates the current token version |
| Any active role | `POST` | `/api/auth/change-password` | `204` |
| `JOB_SEEKER`, `EMPLOYER` | `POST` | `/api/employer-requests` | `201`; submit employer upgrade request |
| `JOB_SEEKER`, `EMPLOYER` | `GET` | `/api/employer-requests` | Own upgrade requests |

Change-password body:

```json
{
  "currentPassword": "StrongPassword123!",
  "newPassword": "EvenStrongerPassword123!"
}
```

### Job seeker routes

| Method | Path | Use |
|---|---|---|
| `GET` | `/api/profile` | Read own profile |
| `PUT` | `/api/profile` | Create/replace own profile; `displayName` is required |
| `POST` | `/api/profile/documents` | Upload a PDF in multipart part `file`; maximum 5 MiB |
| `GET` | `/api/profile/documents` | List own document metadata |
| `GET` | `/api/profile/documents/{documentId}/download` | Get a short-lived private download URL |
| `DELETE` | `/api/profile/documents/{documentId}` | Delete an own document; returns `204` |
| `GET` | `/api/jobs` | Browse active, non-expired jobs |
| `GET` | `/api/jobs/{jobId}` | Read a visible job |
| `POST` | `/api/applications` | Submit a job application as multipart `jobId` plus PDF `cv` |
| `GET` | `/api/applications` | List own applications |
| `GET` | `/api/applications/{applicationId}` | Read an own application |

Job browsing query parameters are optional: `location`, `q`, zero-based `page`
(default `0`), and `size` (default `20`, maximum `100`). The response is:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

Applications start as `PENDING`. A seeker must have a profile display name and
must upload a separate PDF CV for each application. A seeker can apply only
once to a job. Profile documents referenced by an application cannot be
deleted.

### Employer routes

An account must first be upgraded through the employer-request workflow and
approved by an administrator.

| Method | Path | Use |
|---|---|---|
| `POST` | `/api/companies` | Create an owned company |
| `GET` | `/api/companies` | List owned companies |
| `GET` | `/api/companies/{companyId}` | Read an owned company |
| `PUT` | `/api/companies/{companyId}` | Replace an owned company |
| `POST` | `/api/jobs` | Create a job for an owned company |
| `GET` | `/api/employer/jobs` | List own jobs; optional `status`, `page`, `size` |
| `GET` | `/api/jobs/{jobId}` | Read an own job |
| `PUT` | `/api/jobs/{jobId}` | Replace an own job |
| `PATCH` | `/api/jobs/{jobId}/close` | Close an own job |
| `DELETE` | `/api/jobs/{jobId}` | Delete an own job with no applications; returns `204` |
| `GET` | `/api/employer/applications` | List applications for own jobs |
| `GET` | `/api/employer/applications/{applicationId}` | Read an application for an own job |
| `GET` | `/api/employer/applications/{applicationId}/cv` | Get a short-lived private CV download URL |
| `PATCH` | `/api/employer/applications/{applicationId}/status` | Change `PENDING` to `SHORTLISTED` or `REJECTED` |

Employer application listing accepts optional `jobId` and `status` filters.
Allowed statuses are `PENDING`, `SHORTLISTED`, and `REJECTED`. Job create/update
requires `companyId`, `title`, `description`, `requiredQualifications`,
`dueDate` (a future ISO date such as `2099-12-31`), and `location`.

### Administrator routes

Only an `ADMIN` token may call these routes:

| Method | Path | Use |
|---|---|---|
| `GET` | `/api/admin/users` | List users |
| `GET` | `/api/admin/users/{id}` | Read a user |
| `PATCH` | `/api/admin/users/{id}/suspend` | Suspend a user |
| `PATCH` | `/api/admin/users/{id}/remove` | Remove a user |
| `PATCH` | `/api/admin/users/{id}/role` | Promote a user to `ADMIN`; body `{"role":"ADMIN"}` |
| `GET` | `/api/admin/employer-requests` | List pending employer requests |
| `PATCH` | `/api/admin/employer-requests/{requestId}/approve` | Approve an employer request |
| `PATCH` | `/api/admin/employer-requests/{requestId}/reject` | Reject it; body `{"reason":"..."}` |

## Error handling

Treat these statuses consistently:

- `400`: malformed JSON, validation failure, invalid UUID/query value, or
  invalid multipart input.
- `401`: missing, expired, malformed, logged-out, or otherwise invalid token.
- `403`: valid token but wrong role, inactive account, or failed ownership check.
- `404`: resource does not exist or is intentionally hidden from the caller.
- `409`: duplicate application, invalid state transition, or job deletion blocked
  by application history.
- `413`: upload exceeds the 5 MiB file limit.
- `503`: database, storage, or other required integration is unavailable.

Feature-service errors generally have a safe JSON shape such as
`{"code":"FORBIDDEN"}`. Authentication validation errors may be empty responses.
Do not assume that a `404` proves a resource exists for another user.

## Database and storage connectivity

The frontend must **not** connect directly to PostgreSQL or MinIO. The browser
only calls the backend API. Direct database credentials in frontend code would
bypass JWT authorization, ownership checks, and PostgreSQL row-level security.

For the supplied Compose deployment:

- PostgreSQL is the `db` service on the Docker network at
  `jdbc:postgresql://db:5432/${POSTGRES_DB}`.
- Spring Boot uses the restricted `POSTGRES_RUNTIME_USER` and
  `POSTGRES_RUNTIME_PASSWORD`.
- Flyway uses the separate bootstrap `POSTGRES_USER` and
  `POSTGRES_PASSWORD` account for migrations.
- PostgreSQL is published to `127.0.0.1:5432` for operator/admin tools only.
- MinIO is private; the frontend receives short-lived signed URLs from the API
  and must not construct object URLs or use MinIO root credentials.

If a developer needs to inspect the database, use an operator tool from the
host, for example:

```bash
psql "postgresql://<runtime-user>:<runtime-password>@localhost:5432/<database>"
```

This command is for database troubleshooting only. Never put that connection
string in frontend configuration or commit it. The API client does not need
database credentials, PostgreSQL client libraries, MinIO credentials, or access
to the backend image's internal filesystem.

## Browser and deployment notes

The backend does not currently configure permissive CORS. Prefer serving the
frontend and API behind the same origin or configure the deployment reverse
proxy for the approved frontend origin. Do not solve this by opening the
database, MinIO, or all API origins to the public internet.

Use the API's returned IDs and timestamps as authoritative. Do not infer job
expiry, application status, ownership, or account role in the client.
