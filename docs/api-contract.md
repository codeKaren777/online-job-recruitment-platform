# API contract

This guide describes the HTTP API implemented by the Spring controllers. Swagger/OpenAPI (`/swagger-ui/index.html`, `/v3/api-docs`) is the machine-readable reference when enabled. The base URL is the backend origin, for example `http://localhost:8080`. Production should use HTTPS. JSON requests use `Content-Type: application/json`; protected routes use `Authorization: Bearer <access-token>`.

## Authentication and common responses

Public registration creates only `JOB_SEEKER` accounts. Log in to receive a bearer token; use it in Swagger's **Authorize** dialog or send it as a Bearer header. Roles are `JOB_SEEKER`, `EMPLOYER`, and `ADMIN`. The server derives the caller identity from the token; do not send a user ID to act as another user.

Common outcomes include `400` invalid input, `401` missing/invalid/expired token, `403` insufficient role or ownership, `404` missing or invisible resource, `409` conflicting state (duplicate application, closed job, or invalid transition), `413` oversized upload, `429` rate limit, and `503` unavailable dependency. Error responses use the shared API error shape and include `X-Correlation-ID`. Password-reset initiation deliberately gives the same response whether or not the account exists.

## Auth

| Method and path | Access | Purpose / request |
|---|---|---|
| `POST /api/auth/register` | Public | JSON `{ "email", "password", "role": "JOB_SEEKER" }`; creates an account (`201`). |
| `POST /api/auth/login` | Public | JSON `{ "email", "password" }`; returns bearer token and expiry (`200`). |
| `GET /api/auth/me` | Any authenticated user | Returns caller identity and role. |
| `POST /api/auth/logout` | Any authenticated user | Invalidates the caller's current token version (`204`). |
| `POST /api/auth/change-password` | Any authenticated user | JSON `{ "currentPassword", "newPassword" }`; invalidates old sessions (`204`). |
| `POST /api/auth/forgot-password` | Public | JSON `{ "email" }`; returns generic accepted message (`202`), never a reset token. |
| `POST /api/auth/reset-password` | Public | JSON `{ "resetToken", "newPassword" }`; valid one-time token required (`204`). |

## User and employer verification

| Method and path | Access | Purpose / request |
|---|---|---|
| `GET /api/users/me` | Authenticated | Read caller account. |
| `POST /api/employer-requests` | Job seeker | `multipart/form-data`: `application` JSON part with `companyName`, `businessDescription`, `contactEmail`, `contactPhone`, `location`, `vatNumber`, `registrationNumber`; required `document` PDF part (max 5 MiB). Returns request (`201`). |
| `GET /api/employer-requests` | Job seeker or employer | List caller's submissions. |
| `GET /api/employer-requests/{requestId}` | Job seeker or employer | Read own submission and decision status. |
| `GET /api/admin/employer-requests` | Admin | List pending employer requests. |
| `GET /api/admin/employer-requests/{requestId}` | Admin | Read request details. |
| `GET /api/admin/employer-requests/{requestId}/document` | Admin | Access verification document. |
| `PATCH /api/admin/employer-requests/{requestId}/approve` | Admin | Approve; creates company and employer access. |
| `PATCH /api/admin/employer-requests/{requestId}/reject` | Admin | JSON decision body with rejection reason. |
| `GET /api/admin/users` | Admin | List users. |
| `POST /api/admin/users` | Admin | Create account using `CreateAccountRequest`; public registration remains job-seeker only. |
| `GET /api/admin/users/{id}` | Admin | Read user. |
| `PATCH /api/admin/users/{id}/suspend` | Admin | Suspend user. |
| `PATCH /api/admin/users/{id}/reactivate` | Admin | Reactivate a suspended employer. Jobs closed during suspension stay closed. |
| `PATCH /api/admin/users/{id}/remove` | Admin | Remove user. |
| `PATCH /api/admin/users/{id}/role` | Admin | Assign permitted admin role using `{ "role": "ADMIN" }`. |

## Companies and jobs

| Method and path | Access | Purpose / request |
|---|---|---|
| `POST /api/companies` | Employer | JSON `CompanyRequest`: `name` required; `description`, `contactEmail`, `contactPhone`, `location` optional. Returns `201`. |
| `GET /api/companies` | Employer | List caller-owned companies. |
| `GET /api/companies/{companyId}` | Employer | Read owned company. |
| `PUT /api/companies/{companyId}` | Employer | Replace company details. |
| `POST /api/jobs` | Employer | JSON `JobRequest`: `companyId`, `title`, `description`, `requiredQualifications`, future `dueDate` (`YYYY-MM-DD`), `location`. Company must belong to caller. Returns `201` and `Location`. |
| `GET /api/jobs?location=&q=&page=0&size=20` | Job seeker | Browse active jobs; page size maximum 100. |
| `GET /api/jobs/{jobId}` | Job seeker or employer | Read a visible job. |
| `GET /api/employer/jobs?status=&page=0&size=20` | Employer | List caller-owned jobs. |
| `PUT /api/jobs/{jobId}` | Employer | Replace job details using `JobRequest`; caller must own its company/job. |
| `PATCH /api/jobs/{jobId}/close` | Employer | Close an owned job. |
| `PATCH /api/jobs/{jobId}/publish` | Active employer | Explicitly publish an owned closed job with a future due date. |
| `DELETE /api/jobs/{jobId}` | Employer | Delete an owned job only if it has no applications (`204`). |

## Profiles and applications

| Method and path | Access | Purpose / request |
|---|---|---|
| `GET /api/profile` | Job seeker | Read own profile. |
| `PUT /api/profile` | Job seeker | Create/update profile JSON: required `displayName`; optional `fullName`, `dateOfBirth`, `phone`, `location`, `biography`. A profile is required before applying. |
| `POST /api/profile/documents` | Job seeker | Upload own PDF using `multipart/form-data` file part `file` (max 5 MiB). |
| `GET /api/profile/documents` | Job seeker | List own document metadata. |
| `GET /api/profile/documents/{documentId}/download` | Job seeker | Obtain short-lived private download link. |
| `GET /api/profile/documents/{documentId}/view` | Job seeker | Obtain short-lived private inline viewing link. |
| `DELETE /api/profile/documents/{documentId}` | Job seeker | Delete own document (`204`). |
| `POST /api/applications` | Job seeker | Submit `multipart/form-data` with required `jobId`, `cv` PDF (max 5 MiB), and `coverLetter`. Creates `PENDING` application (`201`); profile required; only one application per job. |
| `GET /api/applications` | Job seeker | List own applications. |
| `GET /api/applications/{applicationId}` | Job seeker | Read own application. |
| `PATCH /api/applications/{applicationId}` | Job seeker | Update own pending application. JSON supports application fields; multipart supports optional `coverLetter` and `cv`. |
| `DELETE /api/applications/{applicationId}` | Job seeker | Withdraw own pending application (`204`); history is retained. |
| `GET /api/employer/applications?jobId=&status=` | Employer | List applications only for caller-owned jobs. |
| `GET /api/employer/applications/{applicationId}` | Employer | Read application for caller-owned job. |
| `GET /api/employer/applications/{applicationId}/cv` | Employer | Get short-lived CV link after job ownership check. |
| `GET /api/employer/applications/{applicationId}/cv/view` | Employer | Get short-lived inline CV link after job ownership check. |
| `GET /api/employer/applications/{applicationId}/profile` | Employer | Read applicant profile after job ownership check. |
| `PATCH /api/employer/applications/{applicationId}/status` | Employer | JSON `{ "status": "SHORTLISTED" }` or `{ "status": "REJECTED" }`; only the job owner may decide. |
| `GET /api/employer/shortlisted-candidates` | Employer | List retained shortlisted candidates for caller's jobs. |
| `GET /api/employer/shortlisted-candidates/{candidateId}` | Employer | Read retained shortlist record after ownership checks. |

## Health

| Method and path | Access | Purpose |
|---|---|---|
| `GET /actuator/health` | Public, minimal | Aggregate health status. |
| `GET /actuator/health/liveness` | Public, minimal | Process liveness; excludes external dependencies. |
| `GET /actuator/health/readiness` | Public, minimal | Ready to receive traffic; includes database availability. |

Health details are hidden. Metrics and other management endpoints are not exposed.

## Examples

Register and log in:

```sh
curl -i http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"applicant@example.com","password":"StrongPassword123!","role":"JOB_SEEKER"}'

curl -i http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"applicant@example.com","password":"StrongPassword123!"}'
```

Use the returned access token as `Authorization: Bearer <token>` for protected routes. Do not paste tokens into tickets or logs. For multipart operations, use `curl -F` for each named part; employer request's `application` part must have JSON content type, and `document`/`cv` uploads must be PDFs.

## Keeping this contract current

Endpoint behavior is implemented in the Spring controllers and documented in generated OpenAPI. Before relying on an endpoint, check `/v3/api-docs` or Swagger UI for exact DTO fields and response schemas. In production, OpenAPI may be disabled. This document intentionally does not duplicate every response DTO property; it documents endpoint purpose, access, and required usage shape.
