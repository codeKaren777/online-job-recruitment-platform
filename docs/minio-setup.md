# Local MinIO and document uploads: team setup

MinIO stores the PDF files. PostgreSQL stores their private metadata. Use the
bucket name **recruitment-documents** and keep it **private**.

Each teammate using their own Docker instance creates that same bucket locally.
The names match, but each machine has separate data and credentials. For one
shared MinIO server, an administrator creates the bucket once and provisions
restricted accounts for the team. Our Compose ports bind to localhost, so this
local setup is not a shared server.

These commands run from the repository root in Bash (Linux/macOS or Windows Git
Bash). Docker must be running. Java 21 is needed for Maven tests; Docker builds
MinIO from pinned source, so you do not need Go to run the server.

## 1. Prepare your .env

Copy `.env.example` to `.env` only if you do not already have `.env`. Otherwise
copy only the missing variables. Preserve your existing database/JWT secrets.
The template has all MinIO variables and a link to this guide.

Choose your own local admin username. Generate a unique random password with
at least 32 characters using a password manager; use letters/numbers or hex for
simple KEY=VALUE parsing. Replace MINIO_ROOT_USER and MINIO_ROOT_PASSWORD.
Never commit .env or share your local passwords with teammates.

Keep PROFILE_STORAGE_ENABLED=false until the private bucket and restricted
application credentials are provisioned in step 5. Compose now initializes the
bucket idempotently through the `minio-init` service; the application still
uses a separate restricted identity and never uses the MinIO root credentials.

## 2. Start MinIO

```bash
docker compose config --quiet
docker compose up --build -d --wait minio
curl --fail http://localhost:9000/minio/health/ready
```

When starting the full stack, `minio-init` waits for MinIO readiness, creates
the configured bucket if absent, and reapplies the private anonymous policy:

```bash
docker compose up --build -d --wait
```

The first build can take several minutes. Readiness returns HTTP 200, normally
with an empty body. Open http://localhost:9001 and sign in using MINIO_ROOT_USER
and MINIO_ROOT_PASSWORD from your .env. Port 9001 is the browser interface;
port 9000 is the S3 API used by clients and the backend.

If a command fails, use `docker compose ps` and `docker compose logs --tail 50
minio`. Don't paste credentials or full resolved Compose configuration into chats.

## 3. Get the MinIO command-line client (mc)

The pinned server uses the reduced object-browser interface. Older screenshots
show account/policy controls that may not exist here. If the interface offers
Create Bucket, enter recruitment-documents; otherwise use mc below. Policy/user
administration uses mc regardless. See the
[upstream interface change](https://github.com/minio/minio/releases/tag/RELEASE.2025-05-24T17-08-30Z).

Install mc for your OS following its [official instructions](https://github.com/minio/mc#install-from-source).
If binary downloads are unavailable, with Go 1.24 or newer installed you can build
the pinned client:

```bash
go install github.com/minio/mc@RELEASE.2025-08-13T08-35-41Z
export PATH="$(go env GOPATH)/bin:$PATH"
mc --version
```

Windows Git Bash users whose executable directory is not found should add their
Go bin directory to PATH. This client-install step is needed only for manual
administration; automated project tests do not require mc.

Load your trusted local .env without printing its values, then configure an alias:

```bash
set -a
. ./.env
set +a
mc alias set recruitment-local http://localhost:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"
```

Only source your own .env with simple KEY=VALUE entries. mc stores credentials in
its local configuration (usually ~/.mc/config.json); keep that file private too.
The alias points to port 9000, not the browser port 9001.

## 4. Create the private bucket

```bash
mc mb --ignore-existing recruitment-local/recruitment-documents
mc anonymous set none recruitment-local/recruitment-documents
mc anonymous get recruitment-local/recruitment-documents
```

The access result should be private/no anonymous access. This is sufficient for
the bucket setting: you do not need to switch it to public. MinIO supports public
policies, but our document adapter rejects bucket policies/group ACLs; making this
bucket public would expose CVs and cause application uploads/signing to fail.
Private downloads use temporary signed links instead. Attach the next step's
policy to an application identity, **not to the bucket**.

## 5. Provision the restricted application account

Choose a distinct access-key name and a fresh random secret. Put them in
PROFILE_STORAGE_ACCESS_KEY and PROFILE_STORAGE_SECRET_KEY in your .env, replacing
the placeholders. They must differ from the root credentials. Reload .env as in
step 3, then run:

```bash
mc admin user add recruitment-local "$PROFILE_STORAGE_ACCESS_KEY" "$PROFILE_STORAGE_SECRET_KEY"
mc admin policy create recruitment-local recruitment-document-service docs/minio-document-policy.json
mc admin policy attach recruitment-local recruitment-document-service --user "$PROFILE_STORAGE_ACCESS_KEY"
```

The supplied policy is scoped to recruitment-documents/documents/* and bucket
privacy inspection/location lookup (needed by mc). If you change the bucket name, update both resource names in
a local copy of that policy and PROFILE_STORAGE_BUCKET. Do not grant root or
full-server permissions to the application.

The client commands follow the official [user creation](https://docs.min.io/aistor/reference/cli/admin/mc-admin-user/mc-admin-user-add/)
and [policy attachment](https://docs.min.io/aistor/reference/cli/admin/mc-admin-policy/mc-admin-policy-attach/) syntax.

## 6. Understand the endpoints and activation

| Setting | Local value/purpose |
|---|---|
| PROFILE_STORAGE_BUCKET | recruitment-documents |
| PROFILE_STORAGE_ENDPOINT | http://localhost:9000 for a host JVM; Compose overrides it to http://minio:9000 |
| PROFILE_STORAGE_PUBLIC_ENDPOINT | http://localhost:9000; hostname used in signed links, not public bucket access |
| PROFILE_STORAGE_REGION | us-east-1 |
| PROFILE_DOWNLOAD_SECONDS | 120; allowed range 30..300 |
| PROFILE_STORAGE_ENABLED | false until bucket/account setup is complete |

After steps 4–5, set PROFILE_STORAGE_ENABLED=true and recreate the backend:

```bash
docker compose up -d backend
curl --fail http://localhost:8080/actuator/health
```

This enables the storage adapter and private document HTTP flow. Health UP proves
backend/database readiness; it does not prove bucket credentials or an
authenticated upload workflow.
For a shared server, the signed-link endpoint must be reachable by recipients;
localhost points to each recipient's own machine. Do not rewrite a signed URL.

## 7. Test while authentication is being developed

Start with automated tests; no developer .env, manual bucket or auth module is
needed for them:

```bash
# Validation, ownership/service rules and protected-route regression checks.
sh ./mvnw -B -ntp test
# Real isolated PostgreSQL and MinIO containers; Docker required.
sh ./mvnw -B -ntp verify
```

PowerShell equivalents are `.\mvnw.cmd -B -ntp test` and
`.\mvnw.cmd -B -ntp verify`. Individual focused runs are:

```bash
sh ./mvnw -B -ntp -Dtest=DocumentValidatorTest,ProfileServiceTest test
sh ./mvnw -B -ntp -Dit.test=MinioStorageIT,ProfileRlsIT verify
```

| Check | What it proves |
|---|---|
| DocumentValidatorTest | PDF/MIME/filename checks and bounded streaming |
| ProfileServiceTest | Unit tests with test-only mocks: authorization calls, safe metadata, upload/commit cleanup and delete failures |
| MinioStorageIT | Actual adapter upload, unsigned GET denial, 120-second signed GET, PUT rejection, idempotent deletion |
| ProfileRlsIT | Actual non-bypass PostgreSQL role: user isolation, relevant employer visibility, employer mutation denial, transaction-local identity |
| SecurityBoundaryTest | Profile HTTP routes stay closed for anonymous callers and all roles until integration is complete |

MinIO and PostgreSQL integration tests validate their respective components;
these are not a combined authenticated HTTP-to-database-to-MinIO test. Failsafe
runs *IT tests in verify; test alone does not run them. Reports are in
target/surefire-reports and target/failsafe-reports. Integration failures must not
be skipped or described as passes.

For a manual storage-only check after steps 4–5, configure an application alias
and use a disposable local PDF (no real CV or personal information):

```bash
mc alias set recruitment-documents http://localhost:9000 "$PROFILE_STORAGE_ACCESS_KEY" "$PROFILE_STORAGE_SECRET_KEY" --api S3v4 --path on
mc cp ./sample.pdf recruitment-documents/recruitment-documents/documents/setup-check.pdf
mc share download --expire 2m recruitment-documents/recruitment-documents/documents/setup-check.pdf
curl -i http://localhost:9000/recruitment-documents/documents/setup-check.pdf
```

Expect unsigned curl access to fail (normally 403); open the signed URL produced
by mc in a browser to download the test PDF. Treat the link as a temporary secret.
See [signed-link client syntax](https://docs.min.io/aistor/reference/cli/mc-share/mc-share-download/).
Remove only that disposable object after checking:

```bash
mc rm recruitment-documents/recruitment-documents/documents/setup-check.pdf
```

This manual check exercises the restricted MinIO account only. It does not create
PostgreSQL metadata or test backend validation, ownership, JWTs or rollback logic.
Do not call the Profile API without authentication expecting upload success, add
a fake logged-in user, disable RLS or make feature routes public to test them.

When your friend's auth module is integrated, complete the identity/role mapping,
restricted database role and employer application checks described in
[the document-upload module](profile-module.md#integration-status), then add real
HTTP tests before opening those routes. Only then use Postman/curl for the full
create-profile → upload → list → authorized download → delete workflow.

## 8. Stop safely

`docker compose stop minio` stops the service while preserving its data. The
minio_data volume holds buckets, objects and account configuration across restarts.
Keep .env so the root credentials remain consistent. Never use `down -v` for
routine cleanup. Every teammate provisions their own local bucket/account once;
restarting does not require recreating them.

For exact provider/consumer expectations and integration acceptance cases, see
[Profile contract testing](profile-contract-testing.md).

For the actual HTTP routes, Postman setup, curl requests and response/error
contracts, see [Profile and document endpoints](profile-endpoints.md).
