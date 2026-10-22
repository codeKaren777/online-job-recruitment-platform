# Issue #8 Profile and private documents

## Integration status

Base: `origin/main` c51180f, containing Users implementation c65f530 (Issue #11,
PR #12). Integration uses the actual `users.service.UserService.findById` and
`requireActive`, and persisted UserRole. No duplicate User model was added.
The Users package differs from the planned auth package; this module respects
its existing ownership. Jobs/Applications remain package placeholders.

**HTTP remains closed by SecurityConfig; multipart remains disabled.** This is
an implementation for review, not a completed deployable feature. The required
identity, employer visibility, and runtime database integration are absent.
`docs/decisions.md`, `docs/backlog.md` and SRS/API contracts are absent in this
checkout; no JWT lifecycle or employer route decision was invented.

To finish integration:

1. Authentication owner supplies a `ProfileIdentity` bean returning a UUID from
   validated authentication, confirms JWT subject mapping, and supplies actual
   authentication integration tests. Do not accept a request owner/role parameter.
2. Application owner implements `EmployerApplicationAccess` through their service
   contracts: a persisted application by the applicant to the specified job AND
   job ownership by this employer. Failure must deny. Default is unavailable;
   no employer HTTP route exists.
3. Provision a NOSUPERUSER NOBYPASSRLS runtime role, separate migration credentials,
   schema/type usage and required table grants. Existing Compose credentials are
   PostgreSQL bootstrap superuser credentials. ProfileRls rejects them, even with
   FORCE RLS. Coordinate shared auth/bootstrap RLS requirements before changing
   database credentials. No migration creates hard-coded runtime credentials.
4. Add HTTP ownership/role tests using real authentication and restricted runtime
   DB credentials BEFORE deliberately registering profile route permissions and
   enabling multipart. Suggested limits: max-file-size 5MB, max-request-size 6MB.
   Keep every unrelated feature route's security unchanged.

## HTTP adapter and service contracts

All responses are DTOs (plain JSON, matching Users; no existing envelope).
No request accepts an owner UUID. Service authorization checks active persisted
account status and expected role on every call, including employer calls.

| Route (currently denied) | JOB_SEEKER behavior |
|---|---|
| GET /api/profile | Own profile; 404 if absent |
| PUT /api/profile | Create/update own profile; 200 |
| POST /api/profile/documents | Multipart part `file`; own existing profile required; 201 |
| GET /api/profile/documents | Own metadata only; profile required |
| DELETE /api/profile/documents/{documentId} | Own document only; 204; foreign/missing ID gives 404 |

Profile fields: displayName (nonblank, max 200), phone (optional, max 50), biography
(optional, max 10000); timestamps are server controlled. PUT replaces optional
fields. Missing profile is never implicitly created by read/upload/list.
Documents return id, originalFilename, contentType, byteSize, createdAt;
no bucket, object key, owner override or stored/public URL is returned.

Service-only `readForEmployer`, `listForEmployer`, `downloadForEmployer` require
specific job/applicant relationships through EmployerApplicationAccess.
`downloadOwn` validates owner before signing. Download DTO contains a GET-only
URL plus expiresAt. URLs are bearer capabilities: do not log or persist them.
Expiry defaults to 120 seconds and accepts only 30..300 seconds.

Profile errors use safe codes: 400 INVALID_FILE/INVALID_REQUEST, 403 FORBIDDEN,
404 NOT_FOUND, 413 FILE_TOO_LARGE, 503 STORAGE_UNAVAILABLE,
INTEGRATION_UNAVAILABLE/PROFILE_UNAVAILABLE. Shared translation is scoped to this
controller, preserving other modules' handlers.

## Files and consistency

Conservative PDF-only allowlist: `.pdf`, `application/pdf`, PDF 1.x/2.x signature
at offset zero, trailing %%EOF, nonempty, at most 5 MiB. ASCII filenames at most
180 characters; separators, traversal, controls and unsupported extensions fail.
DOCX is not supported. Stream counting enforces the limit independently of
client-reported size. Temporary files are deleted after success/failure.
Signature checking is not full PDF parsing or malware scanning.

Keys are generated `documents/<random UUID>` and never use original filenames.
Upload happens before metadata flush. Transaction completion removes objects on
confirmed rollback, including rollback after commit failure. Cleanup errors log
only the operation UUID, without SDK exceptions/credentials/URLs. UNKNOWN commit
outcome logs reconciliation rather than deleting potentially committed content.
A restricted operator must check the UUID's corresponding metadata before removing
an orphan. This reporting strategy is not a durable automatic reconciliation queue;
process death between S3 and database commit also requires inventory reconciliation.

Delete object first, metadata second: S3 failure leaves metadata for owner retry.
DB failure after S3 deletion can leave a record for a missing object; repeat DELETE
is idempotent and finishes recovery. Keep metadata until retry succeeds. There is
no claim of atomic S3/PostgreSQL transactions or an automated repair worker.

V3 adds restrictive INSERT/UPDATE/DELETE guards to profiles/documents because V2's
ALL policy allowed employers to DELETE rows they could read. Applied migrations
are unchanged. RLS identity context is transaction-local on the same EntityManager
connection: self UUID bootstrap, active User lookup, then persisted role. Every
service operation owns a REQUIRES_NEW transaction. RLS does not replace ownership
queries or application-service authorization.

## MinIO local setup

Follow the [simple team MinIO setup guide](minio-setup.md) for template variables,
local startup, bucket creation, restricted credentials and tests without auth.
Each local instance uses its own private **recruitment-documents** bucket; a shared
server needs only one bucket. The reduced browser UI may not expose administration;
the guide includes mc commands. Keep anonymous access **none**, not public.


Copy new variables from .env.example into your existing .env manually; do not
replace existing secrets. Use unique random root and application credentials.
Storage is disabled by default until configured. `docker compose up --build -d minio`
starts private localhost ports 9000 (S3) and 9001 (console), readiness probing and
persistent minio_data. Never run down -v.

Provision `recruitment-documents` using MinIO administrative tools/console. Set
anonymous access to **none**, do not attach any bucket policy or group ACL. The
adapter checks policy/ACL before upload/signing and rejects every bucket policy
(conservative even for a private policy). Do not give the backend root credentials.
Create a distinct application identity with this identity policy (placeholders):

```json
{"Version":"2012-10-17","Statement":[
 {"Effect":"Allow","Action":["s3:GetBucketPolicy","s3:GetBucketAcl","s3:GetBucketLocation"],"Resource":["arn:aws:s3:::recruitment-documents"]},
 {"Effect":"Allow","Action":["s3:GetObject","s3:PutObject","s3:DeleteObject"],"Resource":["arn:aws:s3:::recruitment-documents/documents/*"]}
]}
```

PROFILE_STORAGE_ENDPOINT is http://minio:9000 inside Compose and
http://localhost:9000 for a host JVM. PROFILE_STORAGE_PUBLIC_ENDPOINT must resolve
from the recipient browser; localhost is only suitable for local tests. The
presigner uses that endpoint directly (never rewrite a signed URL). Use HTTPS and
an approved externally reachable hostname outside local development. Region is
us-east-1; path-style addressing is configurable in application configuration.
Compose does not automatically create a bucket or privileged application account.

AWS v2 S3 uses an explicit AWS BOM 2.31.78 because Spring Boot does not manage AWS
SDK v2; other dependencies retain Boot management. Follow the
[AWS BOM setup guidance](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/setup-project-maven.html).
MinIO is built from pinned community source RELEASE.2025-10-15T17-29-55Z
using docker/minio.Dockerfile because the historical Docker Hub/Quay images were
unavailable. Testcontainers builds the same Dockerfile. Initial builds require Go
module, base-image and Debian repository downloads. The upstream project is now
archived; deployment maintenance requires a separate reviewed decision. See the
[official source-only build instructions](https://github.com/minio/minio#source-only-distribution).

Once integration gates above are completed, examples (currently rejected):

```bash
curl -H 'Authorization: Bearer <JOB_SEEKER_TOKEN>' http://localhost:8080/api/profile
curl -X PUT -H 'Authorization: Bearer <JOB_SEEKER_TOKEN>' -H 'Content-Type: application/json' \
  -d '{"displayName":"Applicant","phone":null,"biography":"Experience"}' http://localhost:8080/api/profile
curl -H 'Authorization: Bearer <JOB_SEEKER_TOKEN>' -F 'file=@cv.pdf;type=application/pdf' \
  http://localhost:8080/api/profile/documents
```

## Testing uploads before authentication is integrated

Use the [testing walkthrough](minio-setup.md#7-test-while-authentication-is-being-developed).
Unit tests cover validation and service behavior; isolated MinIO and PostgreSQL
integration tests cover storage and real RLS separately. Manual mc upload/signed
link/deletion checks can confirm local storage credentials, but do not create
application metadata or verify the authenticated API workflow. Keep HTTP routes
closed until the identity, application and runtime RLS integration is complete.

For exact provider/consumer expectations and integration acceptance cases, see
[Profile contract testing](profile-contract-testing.md).

For the actual HTTP routes, Postman setup, curl requests and response/error
contracts, see [Profile and document endpoints](profile-endpoints.md).
