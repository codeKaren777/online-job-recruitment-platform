# Frontend integration guide

This guide is for frontend developers integrating with the recruitment API.

## Start the complete development API

The repository includes a development bundle. It starts the backend,
PostgreSQL, and MinIO together, creates the database and private document
bucket automatically, and exposes the API at `http://localhost:8080`.

The frontend team does not need to install or provision PostgreSQL or MinIO.
From the repository root, run:

```bash
./scripts/start-frontend-dev.sh
```

The first run builds the containers and creates a local
`.frontend-dev.env`. That file is generated automatically, ignored by Git,
and must not be shared. Later runs reuse the same local development data.

The frontend team should receive the repository (or a release archive) with
these development files:

- `docker-compose.yml`
- `Dockerfile`
- `docker/`
- `scripts/start-frontend-dev.sh`
- the backend source and migration files used by the build

They do not need `.frontend-dev.env` or any backend `.env` file.

## Updating the development API

After receiving backend changes, update the repository and run the same command:

```bash
git pull
./scripts/start-frontend-dev.sh
```

The script rebuilds changed containers automatically. Existing local database
and document data is preserved. To force only the backend image to rebuild:

```bash
COMPOSE_PROJECT_NAME=frontend-dev docker compose \
  --env-file .frontend-dev.env build backend
COMPOSE_PROJECT_NAME=frontend-dev docker compose \
  --env-file .frontend-dev.env up -d --wait backend
```

There is no need to publish or manually update a registry image for this
development workflow. If the backend is distributed as a prebuilt image later,
the image must be rebuilt and published by the backend team; frontend developers
would then receive the new image tag and run the updated Compose configuration.

Check the API:

```bash
curl --fail http://localhost:8080/actuator/health
```

Stop the development bundle without deleting its data:

```bash
COMPOSE_PROJECT_NAME=frontend-dev docker compose --env-file .frontend-dev.env down
```

To delete local development database and document data, use:

```bash
COMPOSE_PROJECT_NAME=frontend-dev docker compose --env-file .frontend-dev.env down -v
```

This bundle is for local development only. It is not a production deployment.
The frontend still communicates only with the API; it never connects directly
to PostgreSQL or MinIO.

## Frontend environment

Create an environment file in the frontend project. For Vite:

```env
VITE_API_BASE_URL=https://api.example.com
```

For Next.js:

```env
NEXT_PUBLIC_API_BASE_URL=https://api.example.com
```

For another build tool, use its public environment-variable convention.

Only the API base URL belongs in this file. Never add database credentials,
`JWT_SECRET_BASE64`, MinIO credentials, SMTP credentials, or backend bootstrap
credentials. Public frontend variables are visible in the browser.

For local frontend development, use:

```env
VITE_API_BASE_URL=http://localhost:8080
```

This URL is available after `./scripts/start-frontend-dev.sh` has completed.

## Calling the API

All paths are relative to `VITE_API_BASE_URL` (or the equivalent variable).
Use JSON for normal requests:

```http
Content-Type: application/json
```

For authenticated requests, include:

```http
Authorization: Bearer <accessToken>
```

For file uploads, use `FormData`. Do not manually set the multipart boundary.

The complete endpoint list, payloads, response fields, roles, and status codes
is in [frontend-api-reference.md](frontend-api-reference.md).

## Authentication flow

1. Register with `POST /api/auth/register`.
2. Log in with `POST /api/auth/login`.
3. Keep the returned `accessToken` according to the frontend application's
   security policy.
4. Send it as a bearer token on protected requests.
5. On `401`, clear the token and send the user to login again.
6. Call `POST /api/auth/logout` when the user logs out.

Public registration creates `JOB_SEEKER` accounts only. An employer account
must be approved through the employer-request flow. Administrators are created
through the protected administrator workflow.

## Roles

| Role | Main capabilities |
|---|---|
| `JOB_SEEKER` | Manage a profile, browse jobs, and submit applications |
| `EMPLOYER` | Manage owned companies and jobs, and review applications |
| `ADMIN` | Manage users and approve or reject employer requests |

The API is authoritative for roles, ownership, application status, job expiry,
and permissions. Do not rely on values supplied by the browser to determine
what a user may access.

## Files and private downloads

Profile documents and application CVs are private. Upload PDFs through the API
using the documented multipart fields. The API returns short-lived signed
download URLs when the current user is authorized.

The frontend must:

- use the returned URL before its expiry;
- never construct storage URLs;
- never access MinIO directly;
- never receive or store MinIO credentials.

The URL host is the configured `PROFILE_STORAGE_PUBLIC_ENDPOINT` and must be
reachable from the user's browser. Production defaults this to
`https://files.mikhy.me`. The `/download` API routes return attachment URLs;
the corresponding `/view` routes return inline URLs. Open each URL using the
returned GET URL unchanged, before its `expiresAt`; do not edit the hostname,
path, query parameters, method, or signed headers. The URL is a temporary
bearer credential. In production, Nginx must route `files.mikhy.me` to the
MinIO API while preserving the signed request as described in
[the deployment setup](deployment/server-setup.md#minio-file-domain-routing).
The backend's internal `http://minio:9000` address is not a download URL.

## Browser connectivity

The API enables browser CORS for arbitrary origins by default using origin
patterns. CORS preflight supports the API methods and the `Authorization`,
`Content-Type`, and `Accept` headers. This does not make feature routes public:
preflight is handled without a bearer token, while the actual protected
request still requires its JWT and all role/ownership checks.

The default shared-image setting can be narrowed for production by setting
`CORS_ALLOWED_ORIGIN_PATTERNS` to a comma-separated list, for example:

```env
CORS_ALLOWED_ORIGIN_PATTERNS=https://app.example.com,https://*.customer.example
```

Cookies and other credentialed cross-origin requests are disabled. JWT bearer
authentication remains the only cross-origin authentication mechanism.

If CORS is not desired, the deployment team can instead:

- serve the frontend and API from the same origin;
- configure a development proxy;
- allow the deployed frontend origin through a narrowly scoped CORS policy.

Do not expose PostgreSQL or MinIO. The broad default is intended for the shared
image; production deployments should prefer the narrow origin setting.

If the frontend runs at `http://localhost:5173` and the API at
`http://localhost:8080`, the backend team must provide the approved proxy or
CORS setup. The frontend should not alter backend security settings.

## Health and API documentation

The local development bundle exposes these URLs at `http://localhost:8080`:

- Health: `/actuator/health`
- Swagger UI: `/swagger-ui/index.html`
- OpenAPI JSON: `/v3/api-docs`
- OpenAPI YAML: `/v3/api-docs.yaml`

Docker Compose activates the `local` Spring profile, which enables the
documentation endpoints and permits unauthenticated access to them only in
that profile. OpenAPI generation is disabled by default in other profiles.
For frontend integration against a deployed API, the deployment operator can
deliberately set both `APP_OPENAPI_ENABLED=true` and `APP_OPENAPI_PUBLIC=true`;
this makes only the Swagger UI and OpenAPI specification public, not the
business API routes. Disable both settings after integration. The frontend can
use the live OpenAPI document for tooling, while
[frontend-api-reference.md](frontend-api-reference.md) remains the human-readable
contract guide.

## Errors

| Status | Meaning |
|---|---|
| `400` | Invalid request or validation failure |
| `401` | Missing or invalid access token |
| `403` | Insufficient role or access |
| `404` | Resource not found or not visible |
| `409` | Duplicate application, invalid status change, or blocked deletion |
| `413` | File too large |
| `503` | Backend or file-storage service unavailable |

Feature errors may include a safe code, for example:

```json
{
  "code": "FORBIDDEN"
}
```

Do not display internal infrastructure details to users.
