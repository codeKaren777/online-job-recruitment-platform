# Authentication API

## Authentication and identity

Authentication is under `com.groupcc2.recruitment.auth`. Passwords are
BCrypt-hashed and limited to 72 UTF-8 bytes (BCrypt's input limit); request
validation requires 12-72 characters for new passwords. Public registration
always creates a `JOB_SEEKER`; the client cannot select an account role.
Administrator and employer creation/upgrade are not exposed through public
registration.

Access tokens are HS256 JWTs with a 30-minute expiry, issuer, audience, UUID
subject, role, and account `tokenVersion`. Spring Security verifies signature,
issuer, audience, expiry, account status, role, and current token version for
each bearer-authenticated request. `ROLE_JOB_SEEKER`, `ROLE_EMPLOYER`, and
`ROLE_ADMIN` are the resulting authorities. Logout, password change, and
successful password reset increment the user's token version, revoking every
existing token for that account (not just one device/session).

The verified subject and role are never taken from request parameters. JWT
validation sets `app.user_id` and `app.role` transaction-locally before checking
the account row; `/api/auth/me` repeats that context before its safe identity
query. `/api/users/me` establishes self-only RLS visibility for the validated
subject. Administrative user operations first query the administrator using
self-only context, verify the persisted active `ADMIN` role, and only then set
`ADMIN` context in the same transaction for the protected operation. Other
feature services must establish their verified transaction context likewise.

Other modules can inject the verified principal with
`@AuthenticationPrincipal Jwt principal`, read the UUID from
`principal.getSubject()`, and read the role claim with
`principal.getClaimAsString("role")`. Use Spring authorities such as
`@PreAuthorize("hasRole('EMPLOYER')")`, and enforce record ownership in the
service/query; never accept the user's effective ID or role from request data.
New module routes remain denied until their role and ownership checks are added
to `SecurityConfig` and tested.

## Endpoints

Base URL for local development: `http://localhost:8080`. JSON examples use
illustrative values only.

| Method and path | Authentication | Success |
|---|---|---|
| `POST /api/auth/register` | None | `201 Created` |
| `POST /api/auth/login` | None | `200 OK` |
| `GET /api/auth/me` | Bearer JWT | `200 OK` |
| `POST /api/auth/logout` | Bearer JWT | `204 No Content` |
| `POST /api/auth/forgot-password` | None | `202 Accepted` |
| `POST /api/auth/reset-password` | None; one-time reset token | `204 No Content` |
| `POST /api/auth/change-password` | Bearer JWT | `204 No Content` |

### Register

Request:

```json
{
  "email": "applicant@example.com",
  "password": "ApplicantPassword123!"
}
```

Response (`201`):

```json
{
  "id": "d06d52d7-e670-4d5b-b886-faa1ed9a1dab",
  "email": "applicant@example.com",
  "role": "JOB_SEEKER",
  "status": "ACTIVE"
}
```

Missing/invalid fields and passwords outside the validation limits return
`400 Bad Request`; an existing email returns `409 Conflict`. A legacy or
optional `role` field is accepted only when omitted or set to `JOB_SEEKER`;
explicit `EMPLOYER` and `ADMIN` values return `400 Bad Request`. The service
and database registration function also enforce this rule (migration V13).
Password hashes are never returned.

Employer upgrades use the protected employer-request workflow; an administrator
must approve a request before the user receives the employer role. Public
registration cannot grant `EMPLOYER` or `ADMIN`. Administrative user-role
changes are protected, and administrative role promotions are audit-recorded.

## Current authorization scope and dependencies

- `/api/admin/users` operations require `ADMIN` at the HTTP boundary and
  verify the persisted active administrator through the user service. Role
  changes use the protected admin workflow; promotion to `ADMIN` is audit-recorded.
- Employers can manage companies only when they own them. Jobs are associated
  with an owned company, and employer job operations verify the authenticated
  employer/company relationship as well as job ownership.
- Employer application reads and status decisions are limited to applications
  for their own postings. Job seekers can read their own applications and
  cannot change review status; forced RLS remains a second boundary.
- Administrator role changes are available only through the protected admin
  user workflow and retain the administrative role-change audit record.

### Login

Request:

```json
{
  "email": "applicant@example.com",
  "password": "ApplicantPassword123!"
}
```

Response (`200`):

```json
{
  "accessToken": "<signed JWT>",
  "tokenType": "Bearer",
  "expiresIn": 1800
}
```

Invalid credentials and suspended/removed accounts all return `401 Unauthorized`
with no account-specific error message. Use the JWT as `Authorization: Bearer
<accessToken>` in Postman.

### Current authenticated user

`GET /api/auth/me` takes no request body or user ID. Example (`200`):

```json
{
  "id": "d06d52d7-e670-4d5b-b886-faa1ed9a1dab",
  "email": "applicant@example.com",
  "role": "JOB_SEEKER",
  "status": "ACTIVE"
}
```

Missing/invalid/revoked JWTs return `401`. Password and reset fields are not
exposed.

### Logout

`POST /api/auth/logout` takes no body. Send a valid bearer token. A successful
logout returns `204`; the token and all other tokens with that account's old
version are rejected immediately afterward. An expired or already revoked
token returns `401`. The client should still discard the token.

### Forgot password and reset

Forgot-password request:

```json
{"email": "applicant@example.com"}
```

Both registered and unknown email addresses return `202` and the same generic
message:

```json
{
  "message": "If the account exists, the reset request has been accepted.",
  "resetToken": null
}
```

No email provider is configured, and the API does not claim that an email was
sent. Compose runs with the explicit `local` profile. In that profile only,
the response for an active registered account includes `resetToken`; copy it
for local/Postman testing. The database stores only its SHA-256 hash. Outside
the `local` profile the API never returns the raw token. Configure a real
delivery provider before making password recovery available to users in a
deployed environment; do not enable the `local` profile there.

Example response for an existing account in local development (`202`):

```json
{
  "message": "If the account exists, the reset request has been accepted.",
  "resetToken": "<one-time token>"
}
```

Reset request (`resetToken` is the value from the local response or a future
configured delivery channel):

```json
{
  "resetToken": "<one-time token>",
  "newPassword": "ReplacementPassword123!"
}
```

Successful reset returns `204`, consumes the token, and invalidates all
previously issued access tokens. Expired, malformed, or already-used tokens
return `400 Bad Request`; invalid request bodies also return `400`.

Tokens contain 256 random bits and expire after 15 minutes. A new reset request
replaces the prior token for that account.

### Change password

`POST /api/auth/change-password` requires a bearer token:

```json
{
  "currentPassword": "ApplicantPassword123!",
  "newPassword": "ReplacementPassword123!"
}
```

Success returns `204` and revokes all existing account tokens. A wrong current
password or stale token returns `401`; an invalid body or same old/new password
returns `400`.

## Postman walkthrough

1. Set `baseUrl` to `http://localhost:8080`.
2. Send the register example to `POST {{baseUrl}}/api/auth/register`; expect
   `201`. Repeat it to verify `409`, and try `"role":"ADMIN"` or
   `"role":"EMPLOYER"` to verify the response still assigns `JOB_SEEKER`.
3. Send login to `POST {{baseUrl}}/api/auth/login`; save `accessToken` in a
   Postman variable such as `accessToken`.
4. For `GET {{baseUrl}}/api/auth/me`, select Bearer Token authorization and
   use `{{accessToken}}`. Expect `200` and safe identity fields.
5. Send `POST {{baseUrl}}/api/auth/forgot-password` with the email. In Compose's
   local profile, copy the returned token for an existing active user. Try an
   unknown email too; both responses share status/message.
6. Send reset-password with that token and a new password; expect `204`.
   Re-submit the token to verify it returns `400`. The old access token should
   now return `401`.
7. Log in using the reset password. Use this new token to call
   `POST {{baseUrl}}/api/auth/change-password` with current/new passwords;
   expect `204`, then verify the old token returns `401`.
8. Log in again, call `POST {{baseUrl}}/api/auth/logout` with the new bearer
   token; expect `204`, then verify `/me` rejects that token with `401`.

## Database and local environment

Compose uses the `POSTGRES_USER` bootstrap account for Flyway and a separate
`POSTGRES_RUNTIME_USER` account for Spring Boot. The runtime role is created
with `NOSUPERUSER NOBYPASSRLS` and receives explicit schema, table, sequence,
and authentication-function grants. `DB_URL` is set by Compose; JWT uses
`JWT_SECRET_BASE64`, with documented defaults for `JWT_ISSUER` and
`JWT_AUDIENCE`. Keep values in the ignored `.env`; do not print or commit
secrets. See `docs/database.md` for the RLS and database-account design.

The migration history extends through V14. V13 limits the registration
function to JOB_SEEKER and rejects elevated role values while preserving the
forced RLS policies. V14 prevents deleting jobs that have submitted
applications, preserving applicant and review history. Back up existing data
before applying pending migrations.
Do not run `docker compose down -v`.

The Compose database volume remains named `postgres_data`. Do not run
`docker compose down -v`.

## Verification

Unit/MVC tests validate request handling, hashing, JWT claims/validation,
authorization boundaries, service behavior, local token exposure behavior,
and conflict/error responses. `FoundationIT` runs PostgreSQL 17 Testcontainers,
applies migrations to a disposable database, and covers registration, duplicate
registration, login/account status, `/me`, reset expiry/reuse, password changes,
logout revocation, and the existing RLS policy tests through a temporary
non-superuser role.

The regular integration datasource uses the PostgreSQL container's bootstrap
account for Flyway and integration fixtures; dedicated RLS tests create and
exercise separate non-superuser runtime roles. Testcontainers verification
does not migrate the existing local Docker database.
