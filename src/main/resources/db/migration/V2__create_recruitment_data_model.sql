-- Core data model for users, profiles, jobs and applications.
-- Application code must set app.user_id and app.role on every authenticated connection.

CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO $$
BEGIN
    CREATE TYPE recruitment.user_role AS ENUM ('JOB_SEEKER', 'EMPLOYER', 'ADMIN');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END
$$;

DO $$
BEGIN
    CREATE TYPE recruitment.account_status AS ENUM ('ACTIVE', 'SUSPENDED', 'REMOVED');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END
$$;

DO $$
BEGIN
    CREATE TYPE recruitment.job_status AS ENUM ('ACTIVE', 'EXPIRED', 'CLOSED');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END
$$;

DO $$
BEGIN
    CREATE TYPE recruitment.application_status AS ENUM ('PENDING', 'SHORTLISTED', 'REJECTED');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END
$$;

CREATE TABLE recruitment.users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email CITEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    role recruitment.user_role NOT NULL,
    status recruitment.account_status NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT users_email_not_blank CHECK (btrim(email::TEXT) <> ''),
    CONSTRAINT users_password_hash_not_blank CHECK (btrim(password_hash) <> '')
);

CREATE TABLE recruitment.profiles (
    user_id UUID PRIMARY KEY REFERENCES recruitment.users (id) ON DELETE CASCADE,
    display_name TEXT NOT NULL,
    phone TEXT,
    biography TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT profiles_display_name_not_blank CHECK (btrim(display_name) <> '')
);

CREATE TABLE recruitment.documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_user_id UUID NOT NULL REFERENCES recruitment.profiles (user_id) ON DELETE CASCADE,
    storage_key TEXT NOT NULL UNIQUE,
    original_filename TEXT NOT NULL,
    content_type TEXT NOT NULL,
    byte_size BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT documents_storage_key_not_blank CHECK (btrim(storage_key) <> ''),
    CONSTRAINT documents_filename_not_blank CHECK (btrim(original_filename) <> ''),
    CONSTRAINT documents_content_type_not_blank CHECK (btrim(content_type) <> ''),
    CONSTRAINT documents_byte_size_positive CHECK (byte_size > 0)
);

CREATE TABLE recruitment.jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employer_id UUID NOT NULL REFERENCES recruitment.users (id) ON DELETE RESTRICT,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    requirements TEXT,
    location TEXT,
    due_date DATE NOT NULL,
    status recruitment.job_status NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT jobs_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT jobs_description_not_blank CHECK (btrim(description) <> '')
);

CREATE TABLE recruitment.applications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL REFERENCES recruitment.jobs (id) ON DELETE CASCADE,
    job_seeker_id UUID NOT NULL REFERENCES recruitment.users (id) ON DELETE RESTRICT,
    status recruitment.application_status NOT NULL DEFAULT 'PENDING',
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT applications_one_per_job_seeker UNIQUE (job_id, job_seeker_id)
);

CREATE INDEX idx_profiles_updated_at ON recruitment.profiles (updated_at DESC);
CREATE INDEX idx_documents_profile_user_id ON recruitment.documents (profile_user_id);
CREATE INDEX idx_jobs_employer_id ON recruitment.jobs (employer_id);
CREATE INDEX idx_jobs_due_date ON recruitment.jobs (due_date);
CREATE INDEX idx_jobs_active_due_date ON recruitment.jobs (due_date)
    WHERE status = 'ACTIVE';
CREATE INDEX idx_applications_job_id ON recruitment.applications (job_id);
CREATE INDEX idx_applications_job_seeker_id ON recruitment.applications (job_seeker_id);
CREATE INDEX idx_applications_status ON recruitment.applications (status);

CREATE OR REPLACE FUNCTION recruitment.set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END
$$;

CREATE TRIGGER users_set_updated_at
    BEFORE UPDATE ON recruitment.users
    FOR EACH ROW EXECUTE FUNCTION recruitment.set_updated_at();

CREATE TRIGGER profiles_set_updated_at
    BEFORE UPDATE ON recruitment.profiles
    FOR EACH ROW EXECUTE FUNCTION recruitment.set_updated_at();

CREATE TRIGGER jobs_set_updated_at
    BEFORE UPDATE ON recruitment.jobs
    FOR EACH ROW EXECUTE FUNCTION recruitment.set_updated_at();

CREATE TRIGGER applications_set_updated_at
    BEFORE UPDATE ON recruitment.applications
    FOR EACH ROW EXECUTE FUNCTION recruitment.set_updated_at();

CREATE OR REPLACE FUNCTION recruitment.enforce_user_roles()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    expected_role recruitment.user_role;
BEGIN
    IF TG_TABLE_NAME = 'jobs' THEN
        expected_role := 'EMPLOYER';
        IF (SELECT role FROM recruitment.users WHERE id = NEW.employer_id) <> expected_role THEN
            RAISE EXCEPTION 'job owner must have EMPLOYER role';
        END IF;
    ELSIF TG_TABLE_NAME = 'applications' THEN
        expected_role := 'JOB_SEEKER';
        IF (SELECT role FROM recruitment.users WHERE id = NEW.job_seeker_id) <> expected_role THEN
            RAISE EXCEPTION 'applicant must have JOB_SEEKER role';
        END IF;
    END IF;
    RETURN NEW;
END
$$;

CREATE OR REPLACE FUNCTION recruitment.enforce_application_rules()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' AND NEW.status <> 'PENDING' THEN
        RAISE EXCEPTION 'new applications must start with PENDING status';
    END IF;

    IF TG_OP = 'UPDATE' THEN
        IF NEW.job_id <> OLD.job_id OR NEW.job_seeker_id <> OLD.job_seeker_id THEN
            RAISE EXCEPTION 'application job and applicant cannot be changed';
        END IF;
        IF OLD.status <> 'PENDING' AND NEW.status <> OLD.status THEN
            RAISE EXCEPTION 'application status cannot be changed after review';
        END IF;
        IF NEW.status NOT IN ('PENDING', 'SHORTLISTED', 'REJECTED') THEN
            RAISE EXCEPTION 'invalid application status transition';
        END IF;
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER jobs_enforce_employer_role
    BEFORE INSERT OR UPDATE OF employer_id ON recruitment.jobs
    FOR EACH ROW EXECUTE FUNCTION recruitment.enforce_user_roles();

CREATE TRIGGER applications_enforce_job_seeker_role
    BEFORE INSERT OR UPDATE OF job_seeker_id ON recruitment.applications
    FOR EACH ROW EXECUTE FUNCTION recruitment.enforce_user_roles();

CREATE TRIGGER applications_enforce_status_rules
    BEFORE INSERT OR UPDATE ON recruitment.applications
    FOR EACH ROW EXECUTE FUNCTION recruitment.enforce_application_rules();

CREATE OR REPLACE FUNCTION recruitment.expire_due_job_postings()
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = recruitment, pg_catalog
AS $$
DECLARE
    expired_count INTEGER;
BEGIN
    UPDATE recruitment.jobs
       SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP
     WHERE status = 'ACTIVE'
       AND due_date <= CURRENT_DATE;
    GET DIAGNOSTICS expired_count = ROW_COUNT;
    RETURN expired_count;
END
$$;

REVOKE ALL ON FUNCTION recruitment.expire_due_job_postings() FROM PUBLIC;

CREATE OR REPLACE VIEW recruitment.current_jobs AS
SELECT
    j.id,
    j.employer_id,
    j.title,
    j.description,
    j.requirements,
    j.location,
    j.due_date,
    CASE
        WHEN j.status = 'ACTIVE' AND j.due_date <= CURRENT_DATE THEN 'EXPIRED'::recruitment.job_status
        ELSE j.status
    END AS status,
    j.created_at,
    j.updated_at
FROM recruitment.jobs AS j;

ALTER VIEW recruitment.current_jobs SET (security_invoker = true);

ALTER TABLE recruitment.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE recruitment.users FORCE ROW LEVEL SECURITY;
ALTER TABLE recruitment.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE recruitment.profiles FORCE ROW LEVEL SECURITY;
ALTER TABLE recruitment.documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE recruitment.documents FORCE ROW LEVEL SECURITY;
ALTER TABLE recruitment.jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE recruitment.jobs FORCE ROW LEVEL SECURITY;
ALTER TABLE recruitment.applications ENABLE ROW LEVEL SECURITY;
ALTER TABLE recruitment.applications FORCE ROW LEVEL SECURITY;

CREATE POLICY users_self_or_admin ON recruitment.users
    USING (
        current_setting('app.role', true) = 'ADMIN'
        OR id::TEXT = current_setting('app.user_id', true)
    )
    WITH CHECK (
        current_setting('app.role', true) = 'ADMIN'
        OR id::TEXT = current_setting('app.user_id', true)
    );

CREATE POLICY profiles_owner_or_relevant_employer ON recruitment.profiles
    USING (
        current_setting('app.role', true) = 'ADMIN'
        OR user_id::TEXT = current_setting('app.user_id', true)
        OR EXISTS (
            SELECT 1
            FROM recruitment.applications AS a
            JOIN recruitment.jobs AS j ON j.id = a.job_id
            WHERE a.job_seeker_id = profiles.user_id
              AND j.employer_id::TEXT = current_setting('app.user_id', true)
        )
    )
    WITH CHECK (
        current_setting('app.role', true) = 'ADMIN'
        OR user_id::TEXT = current_setting('app.user_id', true)
    );

CREATE POLICY documents_owner_or_relevant_employer ON recruitment.documents
    USING (
        current_setting('app.role', true) = 'ADMIN'
        OR EXISTS (
            SELECT 1
            FROM recruitment.profiles AS p
            WHERE p.user_id = documents.profile_user_id
              AND (
                  p.user_id::TEXT = current_setting('app.user_id', true)
                  OR EXISTS (
                      SELECT 1
                      FROM recruitment.applications AS a
                      JOIN recruitment.jobs AS j ON j.id = a.job_id
                      WHERE a.job_seeker_id = p.user_id
                        AND j.employer_id::TEXT = current_setting('app.user_id', true)
                  )
              )
        )
    )
    WITH CHECK (
        current_setting('app.role', true) = 'ADMIN'
        OR profile_user_id::TEXT = current_setting('app.user_id', true)
    );

CREATE POLICY jobs_owner_or_active_listing ON recruitment.jobs
    USING (
        current_setting('app.role', true) = 'ADMIN'
        OR employer_id::TEXT = current_setting('app.user_id', true)
        OR (
            current_setting('app.role', true) = 'JOB_SEEKER'
            AND status = 'ACTIVE'
            AND due_date > CURRENT_DATE
        )
    )
    WITH CHECK (
        current_setting('app.role', true) = 'ADMIN'
        OR (
            current_setting('app.role', true) = 'EMPLOYER'
            AND employer_id::TEXT = current_setting('app.user_id', true)
        )
    );

CREATE POLICY applications_participant_or_admin ON recruitment.applications
    USING (
        current_setting('app.role', true) = 'ADMIN'
        OR job_seeker_id::TEXT = current_setting('app.user_id', true)
        OR EXISTS (
            SELECT 1
            FROM recruitment.jobs AS j
            WHERE j.id = applications.job_id
              AND j.employer_id::TEXT = current_setting('app.user_id', true)
        )
    );

CREATE POLICY applications_insert_job_seeker ON recruitment.applications
    FOR INSERT
    WITH CHECK (
        current_setting('app.role', true) = 'ADMIN'
        OR (
            current_setting('app.role', true) = 'JOB_SEEKER'
            AND job_seeker_id::TEXT = current_setting('app.user_id', true)
            AND EXISTS (
                SELECT 1
                FROM recruitment.jobs AS j
                WHERE j.id = applications.job_id
                  AND j.status = 'ACTIVE'
                  AND j.due_date > CURRENT_DATE
            )
        )
    );

CREATE POLICY applications_update_owner_or_admin ON recruitment.applications
    FOR UPDATE
    WITH CHECK (
        current_setting('app.role', true) = 'ADMIN'
        OR EXISTS (
            SELECT 1
            FROM recruitment.jobs AS j
            WHERE j.id = applications.job_id
              AND j.employer_id::TEXT = current_setting('app.user_id', true)
        )
    );

COMMENT ON VIEW recruitment.current_jobs IS
    'Canonical job listing view; reports ACTIVE jobs as EXPIRED once due_date is reached.';
COMMENT ON FUNCTION recruitment.expire_due_job_postings() IS
    'Marks due ACTIVE jobs as EXPIRED; invoke from a scheduler or application maintenance task.';