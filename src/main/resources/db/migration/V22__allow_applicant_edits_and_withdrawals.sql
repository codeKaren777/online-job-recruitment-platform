ALTER TABLE recruitment.applications ADD COLUMN withdrawn_at TIMESTAMPTZ;

DROP POLICY applications_update_employer_or_admin ON recruitment.applications;
CREATE POLICY applications_update_applicant_or_employer_or_admin ON recruitment.applications
    FOR UPDATE
    USING (
        current_setting('app.role', true) = 'ADMIN'
        OR (current_setting('app.role', true) = 'JOB_SEEKER'
            AND job_seeker_id::TEXT = current_setting('app.user_id', true)
            AND status = 'PENDING' AND withdrawn_at IS NULL)
        OR EXISTS (SELECT 1 FROM recruitment.jobs j WHERE j.id = applications.job_id
            AND current_setting('app.role', true) = 'EMPLOYER'
            AND j.employer_id::TEXT = current_setting('app.user_id', true))
    )
    WITH CHECK (
        current_setting('app.role', true) = 'ADMIN'
        OR (current_setting('app.role', true) = 'JOB_SEEKER'
            AND job_seeker_id::TEXT = current_setting('app.user_id', true)
            AND status = 'PENDING')
        OR EXISTS (SELECT 1 FROM recruitment.jobs j WHERE j.id = applications.job_id
            AND current_setting('app.role', true) = 'EMPLOYER'
            AND j.employer_id::TEXT = current_setting('app.user_id', true))
    );

CREATE OR REPLACE FUNCTION recruitment.enforce_application_rules()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' AND NEW.status <> 'PENDING' THEN RAISE EXCEPTION 'new applications must start with PENDING status'; END IF;
    IF NEW.job_id <> OLD.job_id OR NEW.job_seeker_id <> OLD.job_seeker_id THEN RAISE EXCEPTION 'application job and applicant cannot be changed'; END IF;
    IF OLD.status <> 'PENDING' AND NEW.status <> OLD.status THEN RAISE EXCEPTION 'application status cannot be changed after review'; END IF;
    IF NEW.status NOT IN ('PENDING', 'SHORTLISTED', 'REJECTED') THEN RAISE EXCEPTION 'invalid application status transition'; END IF;
    IF current_setting('app.role', true) = 'JOB_SEEKER' AND (NEW.status IS DISTINCT FROM OLD.status OR NEW.shortlisted_at IS DISTINCT FROM OLD.shortlisted_at OR NEW.rejected_at IS DISTINCT FROM OLD.rejected_at OR NEW.submitted_at IS DISTINCT FROM OLD.submitted_at OR NEW.job_id IS DISTINCT FROM OLD.job_id OR NEW.job_seeker_id IS DISTINCT FROM OLD.job_seeker_id OR NEW.cv_document_id IS DISTINCT FROM OLD.cv_document_id OR NEW.updated_at IS DISTINCT FROM OLD.updated_at) THEN RAISE EXCEPTION 'applicant may only edit cover letter or withdraw'; END IF;
    IF current_setting('app.role', true) = 'JOB_SEEKER' AND OLD.withdrawn_at IS NOT NULL AND NEW.withdrawn_at IS DISTINCT FROM OLD.withdrawn_at THEN RAISE EXCEPTION 'withdrawal is immutable'; END IF;
    RETURN NEW;
END $$;
