# Authentication API

## Authentication and identity

Authentication is under `com.groupcc2.recruitment.auth`. Passwords are
BCrypt-hashed and limited to 72 UTF-8 bytes (BCrypt's input limit); request
validation requires 12-72 characters for new passwords. Public registration
accepts `JOB_SEEKER` and `EMPLOYER` only. Administrator creation is deliberately
not exposed.

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
query. Other feature services must do likewise in their own transactions.

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
  "password": "ApplicantPassword123!",
  "role": "JOB_SEEKER"
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

`EMPLOYER` is also accepted. `ADMIN`, missing/invalid fields, and passwords
outside the validation limits return `400 Bad Request`; an existing email
returns `409 Conflict`. Password hashes are never returned.

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
   `201`. Repeat it to verify `409`, and try `"role":"ADMIN"` to verify `400`.
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

The local Compose development setup intentionally uses the existing
`POSTGRES_USER` and `POSTGRES_PASSWORD` for database initialization, Spring Boot
runtime, and Flyway. No additional PostgreSQL accounts are provisioned.
`DB_URL` is set by Compose for the backend. `JWT_SECRET_BASE64` is required;
`JWT_ISSUER` and `JWT_AUDIENCE` have documented defaults. Keep values in the
ignored `.env`; do not print or commit secrets.

The current existing database was inspected read-only: the account is
`recruitment`, is a superuser with `BYPASSRLS`, and Flyway history is V1-V3.
Thus current local runtime access bypasses RLS even though policies remain
enabled. No production RLS protection is claimed. The updated backend will
apply V4 and V5 at startup. V4 is pending in this development database; V5
adds `token_version` and hashed reset-token columns/index. These migrations do
not delete rows, but make a protected backup before applying them. No migration
has been run against the existing volume as part of this change.

V4's invoker functions and the password-reset/change SQL are not a complete
pre-authentication access design for a non-superuser. The forced users policy
has no anonymous lookup context, and no additional database account/policy
path is configured. Use the existing superuser only for this local development
setup; a least-privilege deployment requires a separately reviewed,
RLS-compatible auth data-access design.

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

The regular integration datasource uses the PostgreSQL container's default
superuser account to match local single-account development, so those auth
flows do not demonstrate RLS isolation. The existing local Docker stack has
not been restarted or migrated; end-to-end results below must distinguish
disposable test verification from that existing database.
