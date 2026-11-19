CREATE TABLE recruitment.shortlist_candidates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID REFERENCES recruitment.applications(id) ON DELETE SET NULL,
    decision_audit_id UUID NOT NULL UNIQUE
        REFERENCES recruitment.application_decision_audits(id) ON DELETE RESTRICT,
    job_id UUID NOT NULL REFERENCES recruitment.jobs(id) ON DELETE RESTRICT,
    employer_id UUID NOT NULL REFERENCES recruitment.users(id) ON DELETE RESTRICT,
    job_seeker_id UUID NOT NULL REFERENCES recruitment.users(id) ON DELETE RESTRICT,
    job_title_snapshot TEXT NOT NULL,
    display_name_snapshot TEXT NOT NULL,
    full_name_snapshot TEXT,
    date_of_birth_snapshot DATE,
    phone_snapshot TEXT,
    location_snapshot TEXT,
    biography_snapshot TEXT,
    cv_document_id UUID,
    cv_filename_snapshot TEXT,
    cv_content_type_snapshot TEXT,
    cv_byte_size_snapshot BIGINT,
    shortlisted_at TIMESTAMPTZ NOT NULL,
    retained_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_shortlist_candidates_employer
    ON recruitment.shortlist_candidates (employer_id, shortlisted_at DESC);
CREATE INDEX idx_shortlist_candidates_job
    ON recruitment.shortlist_candidates (job_id, shortlisted_at DESC);

ALTER TABLE recruitment.shortlist_candidates ENABLE ROW LEVEL SECURITY;
ALTER TABLE recruitment.shortlist_candidates FORCE ROW LEVEL SECURITY;

CREATE POLICY shortlist_candidates_employer_read ON recruitment.shortlist_candidates
    FOR SELECT
    USING (
        current_setting('app.role', true) = 'ADMIN'
        OR employer_id::TEXT = current_setting('app.user_id', true)
    );

CREATE POLICY shortlist_candidates_no_mutation ON recruitment.shortlist_candidates
    FOR UPDATE
    USING (current_setting('app.role', true) = 'ADMIN')
    WITH CHECK (current_setting('app.role', true) = 'ADMIN');

CREATE POLICY shortlist_candidates_employer_insert ON recruitment.shortlist_candidates
    FOR INSERT
    WITH CHECK (
        current_setting('app.role', true) = 'EMPLOYER'
        AND employer_id::TEXT = current_setting('app.user_id', true)
    );

CREATE FUNCTION recruitment.reject_shortlist_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'shortlist candidate records are immutable';
END;
$$;

CREATE TRIGGER shortlist_candidates_immutable
    BEFORE UPDATE OR DELETE ON recruitment.shortlist_candidates
    FOR EACH ROW EXECUTE FUNCTION recruitment.reject_shortlist_mutation();
