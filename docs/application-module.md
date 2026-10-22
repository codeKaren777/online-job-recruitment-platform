# Application module (FR-08, FR-09, FR-12)

Job seeker side of applications: submit an application, never twice, and track its status.
Employer review and Shortlist/Reject (FR-10, FR-11) are not part of this module yet.

## Endpoints (all require a valid JWT whose `sub` is the user's UUID)

| Method and path | Result |
|---|---|
| `POST /api/applications` body `{"jobId": "<uuid>"}` | 201 with the new application (`status: PENDING`) |
| `GET /api/applications` | 200, caller's applications, newest first |
| `GET /api/applications/{applicationId}` | 200, or 404 if it is not the caller's |

Errors are `{"code": "..."}`: 400 `INVALID_REQUEST`, 403 `FORBIDDEN`, 404 `NOT_FOUND`,
409 `DUPLICATE_APPLICATION`, 409 `JOB_NOT_ACCEPTING_APPLICATIONS`, 503 `INTEGRATION_UNAVAILABLE`.

The applicant is always the token subject. The role is read from the persisted, active user
(like ProfileService), never from token claims or the request body.

## Rules implemented

- Only an active JOB_SEEKER can apply (FR-13).
- Job must exist, have state ACTIVE and `dueDate` after today (UTC). Due date reached means closed (BR-02).
- New applications are PENDING (BR-03).
- One per seeker per job: service check plus the `applications_one_per_job_seeker` unique
  constraint, which also catches two concurrent requests (BR-01).
- Profile sharing (BR-05) needs no copy: the existing RLS policies let the employer read the seeker's
  profile and documents as soon as an application row exists.

## Open dependencies (what other owners must supply)

1. **Jobs module: implement `application.service.JobDirectory`** (`findById`, `findAllById`) and read
   through `recruitment.current_jobs`. Until then every endpoint answers 503 `INTEGRATION_UNAVAILABLE`.
2. **Auth module: issue JWTs with `sub` = user UUID** (issuer/audience from `.env`). There is no login yet.
3. **Database role**: Compose uses the PostgreSQL bootstrap superuser, which bypasses RLS even with
   FORCE RLS. This module sets `app.user_id`/`app.role` (so it works with a restricted role) but
   does not refuse superusers like `ProfileRls` does. Decide as a team; see `docs/profile-module.md`.
4. **RLS gap for FR-12 (needs a team decision and a new V4 migration)**: policy `jobs_owner_or_active_listing`
   only lets a JOB_SEEKER see ACTIVE jobs before their due date. With a restricted DB role, an applicant
   cannot see the posting of an expired job, so `job` is omitted from that application's response.
   Fixing it means a policy that lets a seeker see jobs they applied to. A naive policy on `jobs` that
   queries `applications` recurses (applications' policy queries jobs), so use a SECURITY DEFINER
   helper function. Do not edit V1-V3; add V4 and update the `FoundationIT` version check ("3").
5. **Employer access**: `profile.service.EmployerApplicationAccess` is meant to be implemented by the
   application owner. It is only needed for FR-10 and is not implemented here.

## Decisions to confirm

- Should applying require an existing profile or a CV? FR-08 does not say, so nothing is enforced.
- Due date uses the UTC date; PostgreSQL `CURRENT_DATE` uses the session time zone.
- Pagination and a status filter on `GET /api/applications` are not implemented.

## Trying it locally before Jobs and Auth exist (temporary, do not commit)

Mint a token (replace the secret with `JWT_SECRET_BASE64` from `.env`, `sub` with a registered seeker's id):

```bash
python3 - <<'PY'
import base64, hmac, hashlib, json, time
secret = base64.b64decode("PUT_JWT_SECRET_BASE64_HERE")
b = lambda d: base64.urlsafe_b64encode(d).rstrip(b"=")
h = b(json.dumps({"alg":"HS256","typ":"JWT"}).encode())
p = b(json.dumps({"iss":"groupcc2-recruitment","aud":["groupcc2-api"],"sub":"PUT_USER_UUID_HERE","exp":int(time.time())+3600}).encode())
s = b(hmac.new(secret, h+b"."+p, hashlib.sha256).digest())
print((h+b"."+p+b"."+s).decode())
PY
```

Temporary `JobDirectory` for testing only (delete when the Jobs module lands):

```java
@Component
class TempJobDirectory implements JobDirectory {
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    TempJobDirectory(org.springframework.jdbc.core.JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public java.util.Optional<JobSnapshot> findById(java.util.UUID id) {
        return jdbc.query("SELECT id,title,location,due_date,status::text FROM recruitment.current_jobs WHERE id=?",
                (rs, n) -> new JobSnapshot(rs.getObject(1, java.util.UUID.class), rs.getString(2), rs.getString(3),
                        rs.getDate(4).toLocalDate(), JobSnapshot.State.valueOf(rs.getString(5))), id).stream().findFirst();
    }
    public java.util.Map<java.util.UUID, JobSnapshot> findAllById(java.util.Collection<java.util.UUID> ids) {
        var m = new java.util.HashMap<java.util.UUID, JobSnapshot>();
        ids.forEach(i -> findById(i).ifPresent(j -> m.put(i, j)));
        return m;
    }
}
```
