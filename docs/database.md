# Database Design

The database is PostgreSQL 17 and is created by Flyway when the application starts. The schema is in `recruitment`; the migration files are under `src/main/resources/db/migration`.

## Data model

The model is in third normal form:

- `users` stores identity, password hashes, role, and account status.
- `profiles` stores one job seeker profile per user.
- `documents` stores private document metadata and a storage key. File contents are not stored in PostgreSQL.
- `jobs` stores employer-owned job postings and due dates.
- `applications` links one job seeker to one job and stores the application status.

Applications have a database-enforced `UNIQUE (job_id, job_seeker_id)` constraint, so a job seeker cannot apply to the same job twice. New applications default to `PENDING`. Employers can change applications for their own jobs to `SHORTLISTED` or `REJECTED`; the application service must enforce the allowed transition rules. Migration V4 separates application SELECT and UPDATE policies so applicants can read their own submissions but cannot update review status.

## Enums and expiry

PostgreSQL enums define user roles, account status, job status, and application status. A job is considered expired when its `due_date` is today or earlier. The `recruitment.current_jobs` view calculates this status immediately, even before maintenance runs. The `recruitment.expire_due_job_postings()` function persists `ACTIVE` due jobs as `EXPIRED` and should be called by a scheduler or application maintenance task.

## Indexes and constraints

Indexes cover employer job lookups, due-date listing, active jobs, application lookups by job and applicant, and application status filtering. Foreign keys, non-blank checks, positive document sizes, role consistency triggers, and timestamp triggers protect data integrity at the database boundary.

## Row-level security integration

Row-level security is enabled and forced on all application tables. Every authenticated database transaction must set these transaction-local settings after obtaining its connection and before querying:

```sql
SELECT set_config('app.user_id', '<authenticated-user-uuid>', true);
SELECT set_config('app.role', 'JOB_SEEKER', true);
```

Use `EMPLOYER` or `ADMIN` as appropriate. Use `set_config(..., true)` or `SET LOCAL` inside the same database transaction as the protected queries. Do not use session-scoped `SET` with a connection pool: the connection can be reused for a different request. Transaction-local settings reset at transaction end. The application must derive identity and role from verified server-side authentication, never from HTTP input. Missing settings result in no rows passing the policies; an invalid identity does not authorize another user's rows.

The policies enforce that:

- Job seekers see their own profile and jobs that are currently available.
- An employer sees a job seeker's profile and documents only after that job seeker has applied to the employer's job.
- Employers can see and manage applications belonging only to their own job postings.
- Administrators can access the protected records through the `ADMIN` policy path.

RLS does not replace service-layer authorization. The Java services must still validate roles, ownership, job expiry, and application status transitions.

Flyway's history table is pinned to `public`, where the existing V1/V2
history is stored. This avoids choosing `recruitment` as Flyway's default
history schema when the PostgreSQL runtime user has a same-named schema and
therefore changes `current_schema()`.

The authentication module applies `app.user_id` and `app.role` transaction
locally from a signature-verified JWT before reading the current user. JWT
validation also checks that the account remains `ACTIVE`, its role is unchanged,
and the JWT's `tokenVersion` matches the database value. Other feature services
must set the same transaction-local context before touching RLS-protected data.

### Local database account and RLS boundary

To preserve the existing local database and avoid creating more accounts,
Compose uses the same `POSTGRES_USER` and `POSTGRES_PASSWORD` for Spring Boot
and Flyway. The currently running development database was inspected read-only:
the connected account is `recruitment`, it is a superuser with `BYPASSRLS`, and
Flyway history is at V3. Therefore, **RLS policies remain enabled but do not
isolate data from this application connection**. The test suite verifies RLS
policy behavior only through its isolated temporary policy-test role; it does
not prove that the local runtime is protected by RLS. A least-privilege role
model is required before claiming that RLS protects production data.

Migration V5 defines the narrow authentication functions and grants execution
to the current migration account. V6 adds a monotonically increasing account
token version plus a hashed, expiring password-reset token on
`recruitment.users`. V7 makes only those authentication functions
`SECURITY DEFINER`, allowing a restricted runtime role to authenticate without
granting it unrestricted pre-authentication table access.
Reset completion is an atomic update, so expired or reused tokens cannot reset
the password. The existing `users_self_or_admin` and other RLS policies are not
removed or weakened.

The integration suite provisions a separate non-superuser runtime role,
executes Flyway as the container administrator, and verifies that JWT-backed
feature operations run with forced RLS. Local Compose still uses the existing
single account for developer convenience; that account may bypass RLS and is
not a production least-privilege model.

The current development credentials are `POSTGRES_DB`, `POSTGRES_USER`, and
`POSTGRES_PASSWORD`; `DB_URL` is overridden inside Compose to use the `db`
service, and JWT configuration uses `JWT_SECRET_BASE64` with optional
`JWT_ISSUER` and `JWT_AUDIENCE`. Do not display or commit `.env` values.

The existing database has V1-V3 applied and has not been changed by this work.
Before starting the updated backend, make a protected backup. Startup will run
the pending V4, V5, V6, V7, and V8 Flyway migrations using the existing account. V6 adds
columns and an index; it does not delete application data. Do not use
`docker compose down -v`.

## Local database

The Compose `db` service creates the database from `.env`, exposes PostgreSQL only on `127.0.0.1:5432`, and persists data in the `postgres_data` volume. Start it with:

```bash
docker compose up -d db
```

The detached flag belongs after `up`. If Docker socket permissions require elevated access, use `sudo docker compose up -d db` temporarily. The preferred permanent fix is to add your Linux user to the `docker` group and start a new login session.

Compose starts PostgreSQL and then Spring Boot; Flyway runs V4, V5, V6, V7, and V8 during
application startup using the same `recruitment` database account. No role
provisioner or extra database account is used by this local development setup.

## Database commands

Run these commands from the repository root. They work in Linux shells and Windows PowerShell. The command uses the database credentials already configured inside the `db` container.

### Start and stop

Start only PostgreSQL:

```bash
docker compose up -d db
```

Start PostgreSQL, Flyway migrations, and the backend. This requires the existing
database variables and JWT secret in `.env`. Back up the database before this
command because it applies pending V4, V5, V6, V7, and V8 migrations:

```bash
docker compose up --build -d
```

Check container status and database readiness. Do not print rendered Compose
configuration or service environments because they contain credentials:

```bash
docker compose ps
docker compose exec db sh -c 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

Stop containers while keeping the database volume:

```bash
docker compose down
```

Recreate the containers while keeping existing data:

```bash
docker compose down
docker compose up --build -d
```

Recreate the database from empty. This permanently deletes all local database data:

```bash
docker compose down -v
docker compose up --build -d
```

### Enter PostgreSQL

Open an interactive `psql` session inside the database container:

```bash
docker compose exec db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

Exit `psql` with `\\q`.

Run one SQL command without opening an interactive session:

```bash
docker compose exec db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT current_database(), current_user;"'
```

### Inspect the database

List all application tables:

```bash
docker compose exec db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT table_name FROM information_schema.tables WHERE table_schema = '\''recruitment'\'' ORDER BY table_name;"'
```

List enums and their values:

```bash
docker compose exec db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT t.typname, e.enumlabel FROM pg_type t JOIN pg_enum e ON e.enumtypid = t.oid JOIN pg_namespace n ON n.oid = t.typnamespace WHERE n.nspname = '\''recruitment'\'' ORDER BY t.typname, e.enumsortorder;"'
```

List indexes:

```bash
docker compose exec db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT tablename, indexname, indexdef FROM pg_indexes WHERE schemaname = '\''recruitment'\'' ORDER BY tablename, indexname;"'
```

Check row-level security:

```bash
docker compose exec db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT relname, relrowsecurity, relforcerowsecurity FROM pg_class WHERE relnamespace = '\''recruitment'\''::regnamespace AND relkind = '\''r'\'' ORDER BY relname;"'
```

Check Flyway migrations in the preserved `public` history schema:

```bash
docker compose exec db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT installed_rank, version, description, success FROM public.flyway_schema_history ORDER BY installed_rank;"'
```

The Flyway history table may not exist until the backend has started successfully.

### Run database maintenance manually

Mark due active jobs as expired:

```bash
docker compose exec db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT recruitment.expire_due_job_postings();"'
```

Inspect the canonical job view, including jobs that are due today:

```bash
docker compose exec db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT id, title, due_date, status FROM recruitment.current_jobs ORDER BY due_date;"'
```

Do not manually run Flyway migration files against a database that the backend manages. Add a new versioned migration, then restart the backend so Flyway can apply it and record it in its history table.
