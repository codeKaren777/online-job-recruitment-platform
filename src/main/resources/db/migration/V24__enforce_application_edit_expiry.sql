DROP VIEW recruitment.current_jobs;
CREATE VIEW recruitment.current_jobs AS
SELECT
    j.id,
    j.employer_id,
    j.company_id,
    j.title,
    j.description,
    j.requirements,
    j.location,
    j.due_date,
    j.created_at,
    j.updated_at,
    CASE
        WHEN j.status = 'ACTIVE'
             AND j.due_date <= (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date
            THEN 'EXPIRED'::recruitment.job_status
        ELSE j.status
    END AS status
FROM recruitment.jobs j;
ALTER VIEW recruitment.current_jobs SET (security_invoker = true);

CREATE OR REPLACE FUNCTION recruitment.applicant_has_application_for_job(
    p_job_id UUID,
    p_job_seeker_id UUID
)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = recruitment, pg_catalog
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM recruitment.applications a
        WHERE a.job_id = p_job_id
          AND a.job_seeker_id = p_job_seeker_id
    )
$$;
REVOKE ALL ON FUNCTION recruitment.applicant_has_application_for_job(UUID, UUID)
    FROM PUBLIC;
GRANT EXECUTE ON FUNCTION recruitment.applicant_has_application_for_job(UUID, UUID)
    TO PUBLIC;

DROP POLICY jobs_owner_or_active_listing ON recruitment.jobs;
CREATE POLICY jobs_owner_or_active_listing ON recruitment.jobs
    USING (
        current_setting('app.role', true) = 'ADMIN'
        OR employer_id::TEXT = current_setting('app.user_id', true)
        OR (
            current_setting('app.role', true) = 'JOB_SEEKER'
            AND (
                (status = 'ACTIVE'
                 AND due_date > (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date)
                OR recruitment.applicant_has_application_for_job(
                    jobs.id,
                    current_setting('app.user_id', true)::UUID
                )
            )
        )
    )
    WITH CHECK (
        current_setting('app.role', true) = 'ADMIN'
        OR (
            current_setting('app.role', true) = 'EMPLOYER'
            AND employer_id::TEXT = current_setting('app.user_id', true)
        )
    );

DROP POLICY applications_update_applicant_or_employer_or_admin ON recruitment.applications;
CREATE POLICY applications_update_applicant_or_employer_or_admin ON recruitment.applications
    FOR UPDATE
    USING (
        current_setting('app.role', true) = 'ADMIN'
        OR (
            current_setting('app.role', true) = 'JOB_SEEKER'
            AND job_seeker_id::TEXT = current_setting('app.user_id', true)
            AND status = 'PENDING'
            AND withdrawn_at IS NULL
            AND EXISTS (
                SELECT 1
                FROM recruitment.jobs j
                WHERE j.id = applications.job_id
                  AND j.status = 'ACTIVE'
                  AND j.due_date > (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date
            )
        )
        OR EXISTS (
            SELECT 1
            FROM recruitment.jobs j
            WHERE j.id = applications.job_id
              AND current_setting('app.role', true) = 'EMPLOYER'
              AND j.employer_id::TEXT = current_setting('app.user_id', true)
        )
    )
    WITH CHECK (
        current_setting('app.role', true) = 'ADMIN'
        OR (
            current_setting('app.role', true) = 'JOB_SEEKER'
            AND job_seeker_id::TEXT = current_setting('app.user_id', true)
            AND status = 'PENDING'
            AND withdrawn_at IS NULL
            AND EXISTS (
                SELECT 1
                FROM recruitment.jobs j
                WHERE j.id = applications.job_id
                  AND j.status = 'ACTIVE'
                  AND j.due_date > (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date
            )
        )
        OR EXISTS (
            SELECT 1
            FROM recruitment.jobs j
            WHERE j.id = applications.job_id
              AND current_setting('app.role', true) = 'EMPLOYER'
              AND j.employer_id::TEXT = current_setting('app.user_id', true)
        )
    );

CREATE OR REPLACE FUNCTION recruitment.enforce_application_rules()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.status <> 'PENDING' THEN
            RAISE EXCEPTION 'new applications must start with PENDING status';
        END IF;
        RETURN NEW;
    END IF;

    IF NEW.job_id IS DISTINCT FROM OLD.job_id
       OR NEW.job_seeker_id IS DISTINCT FROM OLD.job_seeker_id THEN
        RAISE EXCEPTION 'application job and applicant cannot be changed';
    END IF;
    IF OLD.status <> 'PENDING' AND NEW.status <> OLD.status THEN
        RAISE EXCEPTION 'application status cannot be changed after review';
    END IF;
    IF NEW.status NOT IN ('PENDING', 'SHORTLISTED', 'REJECTED') THEN
        RAISE EXCEPTION 'invalid application status transition';
    END IF;
    IF current_setting('app.role', true) = 'JOB_SEEKER' AND (
        NEW.status IS DISTINCT FROM OLD.status
        OR NEW.shortlisted_at IS DISTINCT FROM OLD.shortlisted_at
        OR NEW.rejected_at IS DISTINCT FROM OLD.rejected_at
        OR NEW.submitted_at IS DISTINCT FROM OLD.submitted_at
        OR NEW.job_id IS DISTINCT FROM OLD.job_id
        OR NEW.job_seeker_id IS DISTINCT FROM OLD.job_seeker_id
        OR NEW.updated_at IS DISTINCT FROM OLD.updated_at
    ) THEN
        RAISE EXCEPTION 'applicant may only edit cover letter, application CV, or withdraw';
    END IF;
    IF current_setting('app.role', true) = 'JOB_SEEKER'
       AND OLD.withdrawn_at IS NOT NULL
       AND NEW.withdrawn_at IS DISTINCT FROM OLD.withdrawn_at THEN
        RAISE EXCEPTION 'withdrawal is immutable';
    END IF;
    RETURN NEW;
END
$$;
