# Application module (FR-08, FR-09, FR-10, FR-11, FR-12)

Job seeker submission/tracking and employer review of applications for owned jobs.

## Endpoints (all require a valid JWT whose `sub` is the user's UUID)

| Method and path | Result |
|---|---|
| `POST /api/applications` body `{"jobId": "<uuid>"}` | 201 with the new application (`status: PENDING`) |
| `GET /api/applications` | 200, caller's applications, newest first |
| `GET /api/applications/{applicationId}` | 200, or 404 if it is not the caller's |
| `GET /api/employer/applications` | 200, applications for the caller's jobs, newest first |
| `GET /api/employer/applications?jobId=<uuid>&status=<status>` | 200, applications for one owned job, optionally filtered by `PENDING`, `SHORTLISTED`, or `REJECTED` |
| `GET /api/employer/applications/{applicationId}` | 200, or 404 if it is not for one of the caller's jobs |
| `PATCH /api/employer/applications/{applicationId}/status` body `{"status":"SHORTLISTED"}` | 200, shortlist or reject an application for an owned job |

Employers may delete a job only when they own its associated company. Jobs
with applications are retained so application and decision history cannot be
removed as a side effect; deleting such a job returns `409 Conflict`.

Errors are `{"code": "..."}`: 400 `INVALID_REQUEST`, 403 `FORBIDDEN`, 404 `NOT_FOUND`,
409 `DUPLICATE_APPLICATION`, 409 `JOB_NOT_ACCEPTING_APPLICATIONS`, 409 `INVALID_STATUS_TRANSITION`,
503 `INTEGRATION_UNAVAILABLE`.

The applicant is always the token subject. The role is read from the persisted, active user
(like ProfileService), never from token claims or the request body.

Employer reads derive the employer from the token subject and filter through the Jobs module's
ownership contract. A client-supplied job or application ID cannot grant access to another employer's data.

## Rules implemented

- Only an active JOB_SEEKER can apply (FR-13).
- Job must exist, have state ACTIVE and `dueDate` after today (UTC). Due date reached means closed (BR-02).
- New applications are PENDING (BR-03).
- Only PENDING applications can transition once to SHORTLISTED or REJECTED. The response exposes
  `shortlistedAt` or `rejectedAt` for the decision timestamp.
- Every shortlist/reject decision is stored in `application_decision_audits` with the employer,
  application, job, previous status, new status, and decision time.
- One per seeker per job: service check plus the `applications_one_per_job_seeker` unique
  constraint, which also catches two concurrent requests (BR-01).
- Profile sharing (BR-05) needs no copy: the existing RLS policies let the employer read the seeker's
  profile and documents as soon as an application row exists.

## Open dependencies (what other owners must supply)

1. **Jobs module:** `application.service.JobDirectory` is implemented by `JpaJobDirectory`; restricted-role
   RLS behavior still requires end-to-end verification.
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
5. **Employer profile access**: `profile.service.EmployerApplicationAccess` is meant to be implemented by
   the application owner. It is only needed for employer profile/document access and is not implemented here.

## Decisions to confirm

- Should applying require an existing profile or a CV? FR-08 does not say, so nothing is enforced.
- Due date uses the UTC date; PostgreSQL `CURRENT_DATE` uses the session time zone.
- Pagination is not implemented.
- Email/SMS/notification delivery is outside the current architecture scope; status changes are
  persisted and auditable, but no unconfigured or fake mail sender is invoked.
