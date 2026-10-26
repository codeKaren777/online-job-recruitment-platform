ALTER TABLE recruitment.applications
    ADD COLUMN shortlisted_at TIMESTAMPTZ,
    ADD COLUMN rejected_at TIMESTAMPTZ,
    ADD CONSTRAINT applications_status_timestamp_check CHECK (
        (status = 'PENDING' AND shortlisted_at IS NULL AND rejected_at IS NULL)
        OR (status = 'SHORTLISTED' AND shortlisted_at IS NOT NULL AND rejected_at IS NULL)
        OR (status = 'REJECTED' AND shortlisted_at IS NULL AND rejected_at IS NOT NULL)
    );

CREATE TABLE recruitment.application_decision_audits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID NOT NULL REFERENCES recruitment.applications (id) ON DELETE RESTRICT,
    job_id UUID NOT NULL REFERENCES recruitment.jobs (id) ON DELETE RESTRICT,
    employer_id UUID NOT NULL REFERENCES recruitment.users (id) ON DELETE RESTRICT,
    previous_status recruitment.application_status NOT NULL,
    new_status recruitment.application_status NOT NULL,
    decided_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT application_decision_audits_status_change CHECK (
        previous_status = 'PENDING'
        AND new_status IN ('SHORTLISTED', 'REJECTED')
    )
);

CREATE INDEX idx_application_decision_audits_application
    ON recruitment.application_decision_audits (application_id, decided_at DESC);
