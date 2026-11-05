# Frontend API reference

This is the API handoff for the frontend application.

## Setup

The frontend needs only the backend URL. You do not need the backend .env
`.env` file.

```env
# frontend/.env.local
VITE_API_BASE_URL=http://localhost:8080
```

Use the equivalent public variable name for another frontend tool, such as
`NEXT_PUBLIC_API_BASE_URL` for Next.js.

Use this base URL before every path below. Authenticated requests must include:

```http
Authorization: Bearer <accessToken>
```

Use `Content-Type: application/json` for JSON requests. For file uploads, use
`FormData` and let the browser set the `multipart/form-data` boundary.

## Common values

- Roles: `JOB_SEEKER`, `EMPLOYER`, `ADMIN`
- Account statuses: `ACTIVE`, `SUSPENDED`, `REMOVED`
- Application statuses: `PENDING`, `SHORTLISTED`, `REJECTED`
- IDs are UUID strings.
- Dates use `YYYY-MM-DD`.
- Timestamps are ISO-8601 strings.

## Authentication

### Register

`POST /api/auth/register`

Request:

```json
{
  "email": "applicant@example.com",
  "password": "StrongPassword123!",
  "role": "JOB_SEEKER"
}
```

`role` may be omitted. Public registration only creates `JOB_SEEKER`
accounts. Passwords must be 12–72 characters.

Response `201`:

```json
{
  "id": "11111111-1111-4111-8111-111111111111",
  "email": "applicant@example.com",
  "role": "JOB_SEEKER",
  "status": "ACTIVE"
}
```

### Login

`POST /api/auth/login`

```json
{
  "email": "applicant@example.com",
  "password": "StrongPassword123!"
}
```

Response `200`:

```json
{
  "accessToken": "<JWT>",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

Store the access token according to the frontend application's security
policy. Do not put it in a URL.

### Current user

`GET /api/auth/me` or `GET /api/users/me`

Response `200`:

```json
{
  "id": "11111111-1111-4111-1111-111111111111",
  "email": "applicant@example.com",
  "role": "JOB_SEEKER",
  "status": "ACTIVE"
}
```

### Logout

`POST /api/auth/logout` — response `204`.

### Change password

`POST /api/auth/change-password`

```json
{
  "currentPassword": "StrongPassword123!",
  "newPassword": "EvenStrongerPassword123!"
}
```

Response `204`.

### Password reset

`POST /api/auth/forgot-password`

```json
{
  "email": "applicant@example.com"
}
```

`POST /api/auth/reset-password`

```json
{
  "resetToken": "<one-time-token>",
  "newPassword": "NewStrongPassword123!"
}
```

## Job-seeker endpoints

All endpoints in this section require a `JOB_SEEKER` token.

### Profile

`PUT /api/profile`

```json
{
  "displayName": "Jane Applicant",
  "fullName": "Jane Mary Applicant",
  "dateOfBirth": "1995-06-15",
  "phone": "+265999000111",
  "location": "Blantyre",
  "biography": "Backend developer"
}
```

`displayName` is required. The response is:

```json
{
  "displayName": "Jane Applicant",
  "fullName": "Jane Mary Applicant",
  "dateOfBirth": "1995-06-15",
  "phone": "+265999000111",
  "location": "Blantyre",
  "biography": "Backend developer",
  "createdAt": "2026-10-13T12:00:00Z",
  "updatedAt": "2026-10-13T12:00:00Z"
}
```

`GET /api/profile` — returns the profile. Returns `404` if no profile exists.

### Documents

`POST /api/profile/documents` — send a PDF in the `file` multipart field.
Maximum file size is 5 MiB. Response `201`:

```json
{
  "id": "22222222-2222-4222-8222-222222222222",
  "originalFilename": "resume.pdf",
  "contentType": "application/pdf",
  "byteSize": 12345,
  "createdAt": "2026-10-13T12:00:00Z"
}
```

`GET /api/profile/documents` — returns an array of document metadata.

`GET /api/profile/documents/{documentId}/download` — returns:

```json
{
  "url": "https://example.com/signed-download-url",
  "expiresAt": "2026-10-13T12:02:00Z"
}
```

Open the returned URL before `expiresAt`; do not construct storage URLs.

`DELETE /api/profile/documents/{documentId}` — response `204`.

### Employer upgrade request

`POST /api/employer-requests`

```json
{
  "companyName": "Acme Technologies",
  "businessDescription": "Software and recruitment technology company.",
  "contactEmail": "contact@acme.example",
  "contactPhone": "+265999000111",
  "location": "Blantyre"
}
```

Response `201`. `GET /api/employer-requests` returns the user's requests.
Each request contains `id`, `userId`, the submitted company fields, `status`,
`reviewedBy`, `reviewedAt`, `rejectionReason`, `createdAt`, and `updatedAt`.

### Browse and apply for jobs

`GET /api/jobs` — browse active jobs. Optional query parameters:
`location`, `q`, `page` (default `0`), and `size` (default `20`, maximum `100`).

`GET /api/jobs/{jobId}` — read a job.

Job fields:

```json
{
  "id": "33333333-3333-4333-8333-333333333333",
  "employerId": "44444444-4444-4444-8444-444444444444",
  "companyId": "55555555-5555-4555-8555-555555555555",
  "title": "Senior Java Developer",
  "description": "Build recruitment platform services.",
  "requiredQualifications": "Java 21, Spring Boot, PostgreSQL",
  "dueDate": "2099-12-31",
  "location": "Blantyre",
  "status": "ACTIVE",
  "createdAt": "2026-10-13T12:00:00Z",
  "updatedAt": "2026-10-13T12:00:00Z"
}
```

`POST /api/applications` — send multipart fields:

- `jobId`: job UUID
- `cv`: PDF file, maximum 5 MiB

The profile must have a `displayName`. A user can apply to a job only once.
Applications start with status `PENDING`.

`GET /api/applications` — list the user's applications.
`GET /api/applications/{applicationId}` — read one application.

Application fields include `id`, `jobId`, `applicantId`, `applicantDisplayName`,
`cv`, `status`, `submittedAt`, `updatedAt`, `shortlistedAt`,
`rejectedAt`, and `job`. The `cv` and `job` fields use the shapes described
above.

## Employer endpoints

All endpoints in this section require an approved `EMPLOYER` token.

### Companies

`POST /api/companies` and `PUT /api/companies/{companyId}`:

```json
{
  "name": "Acme Technologies",
  "description": "Software company.",
  "contactEmail": "contact@acme.example",
  "contactPhone": "+265999000111",
  "location": "Blantyre"
}
```

Company responses contain `id`, `employerId`, the submitted company fields,
`createdAt`, and `updatedAt`.

`GET /api/companies` — list owned companies.
`GET /api/companies/{companyId}` — read an owned company.

### Jobs

`POST /api/jobs` and `PUT /api/jobs/{jobId}`:

```json
{
  "companyId": "55555555-5555-4555-8555-555555555555",
  "title": "Senior Java Developer",
  "description": "Build recruitment platform services.",
  "requiredQualifications": "Java 21, Spring Boot, PostgreSQL",
  "dueDate": "2099-12-31",
  "location": "Blantyre"
}
```

All fields are required. `dueDate` must be in the future.

`GET /api/employer/jobs` — list owned jobs. Optional query parameters:
`status`, `page`, and `size`.

`PATCH /api/jobs/{jobId}/close` — close an owned job.
`DELETE /api/jobs/{jobId}` — delete an owned job with no applications;
response `204`.

### Review applications

`GET /api/employer/applications` — list applications for owned jobs. Optional
query parameters: `jobId` and `status`.

`GET /api/employer/applications/{applicationId}` — read an application.

`GET /api/employer/applications/{applicationId}/profile` — read the applicant's
profile.

`GET /api/employer/applications/{applicationId}/cv` — receive a temporary CV
download URL.

`PATCH /api/employer/applications/{applicationId}/status`

```json
{
  "status": "SHORTLISTED"
}
```

Only `PENDING` applications can be changed to `SHORTLISTED` or `REJECTED`.

## Administrator endpoints

These endpoints require an `ADMIN` token:

| Method | Path | Body |
|---|---|---|
| GET | `/api/admin/users` | — |
| GET | `/api/admin/users/{id}` | — |
| PATCH | `/api/admin/users/{id}/suspend` | — |
| PATCH | `/api/admin/users/{id}/remove` | — |
| PATCH | `/api/admin/users/{id}/role` | `{"role":"ADMIN"}` |
| GET | `/api/admin/employer-requests` | — |
| PATCH | `/api/admin/employer-requests/{requestId}/approve` | — |
| PATCH | `/api/admin/employer-requests/{requestId}/reject` | `{"reason":"..."}` |

User responses contain `id`, `email`, `role`, `accountStatus`, `createdAt`,
and `updatedAt`.

## Errors

| Status | Meaning |
|---|---|
| `400` | Invalid request or validation failure |
| `401` | Missing or invalid access token |
| `403` | Insufficient role or access |
| `404` | Resource not found or not visible |
| `409` | Duplicate application, invalid status change, or blocked deletion |
| `413` | File too large |
| `503` | Backend or file storage unavailable |

On `401`, clear the access token and send the user to login. Error responses
may include a safe `code` field, for example `{"code":"FORBIDDEN"}`.

## Local development

The local API URL is `http://localhost:8080`. If the frontend runs on another
port, configure a development proxy or use the deployment's approved CORS
configuration.

