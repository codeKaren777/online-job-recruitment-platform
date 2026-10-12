# Database Design

The database is PostgreSQL 17 and is created by Flyway when the application starts. The schema is in `recruitment`; the migration files are under `src/main/resources/db/migration`.

## Data model

The model is in third normal form:

- `users` stores identity, password hashes, role, and account status.
- `profiles` stores one job seeker profile per user.
- `documents` stores private document metadata and a storage key. File contents are not stored in PostgreSQL.
- `jobs` stores employer-owned job postings and due dates.
- `applications` links one job seeker to one job and stores the application status.

Applications have a database-enforced `UNIQUE (job_id, job_seeker_id)` constraint, so a job seeker cannot apply to the same job twice. New applications default to `PENDING`. Employers can change applications for their own jobs to `SHORTLISTED` or `REJECTED`; the application service must enforce the allowed transition rules.

## Enums and expiry

PostgreSQL enums define user roles, account status, job status, and application status. A job is considered expired when its `due_date` is today or earlier. The `recruitment.current_jobs` view calculates this status immediately, even before maintenance runs. The `recruitment.expire_due_job_postings()` function persists `ACTIVE` due jobs as `EXPIRED` and should be called by a scheduler or application maintenance task.

## Indexes and constraints

Indexes cover employer job lookups, due-date listing, active jobs, application lookups by job and applicant, and application status filtering. Foreign keys, non-blank checks, positive document sizes, role consistency triggers, and timestamp triggers protect data integrity at the database boundary.

## Row-level security integration

Row-level security is enabled and forced on all application tables. Every authenticated database connection must set these transaction-local settings before querying:

```sql
SELECT set_config('app.user_id', '<authenticated-user-uuid>', true);
SELECT set_config('app.role', 'JOB_SEEKER', true);
```

Use `EMPLOYER` or `ADMIN` as appropriate. The application must set these values after obtaining a connection and clear them at transaction end. Do not accept these values from an HTTP request. The API authentication layer must derive them from the verified JWT or server-side session.

The policies enforce that:

- Job seekers see their own profile and jobs that are currently available.
- An employer sees a job seeker's profile and documents only after that job seeker has applied to the employer's job.
- Employers can see and manage applications belonging only to their own job postings.
- Administrators can access the protected records through the `ADMIN` policy path.

RLS does not replace service-layer authorization. The Java services must still validate roles, ownership, job expiry, and application status transitions.

## Local database

The Compose `db` service creates the database from `.env`, exposes PostgreSQL only on `127.0.0.1:5432`, and persists data in the `postgres_data` volume. Start it with:

```bash
docker compose up -d db
```

The detached flag belongs after `up`. If Docker socket permissions require elevated access, use `sudo docker compose up -d db` temporarily. The preferred permanent fix is to add your Linux user to the `docker` group and start a new login session.

Flyway applies `V1__initialize_schema.sql` and `V2__create_recruitment_data_model.sql` automatically when the Spring Boot application starts. Do not edit an applied migration; add a new versioned migration for future changes.

## Database commands

Run these commands from the repository root. They work in Linux shells and Windows PowerShell. The command uses the database credentials already configured inside the `db` container.

### Start and stop

Start only PostgreSQL:

```bash
docker compose up -d db
```

Start PostgreSQL and the backend. The backend waits for PostgreSQL to become healthy, then Flyway applies the migrations:

```bash
docker compose up --build -d
```

Check container status and database readiness:

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

Check Flyway migrations:

```bash
docker compose exec db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT installed_rank, version, description, success FROM recruitment.flyway_schema_history ORDER BY installed_rank;"'
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