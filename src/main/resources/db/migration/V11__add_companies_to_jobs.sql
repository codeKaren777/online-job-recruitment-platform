CREATE TABLE recruitment.companies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employer_id UUID NOT NULL REFERENCES recruitment.users (id) ON DELETE RESTRICT,
    name TEXT NOT NULL,
    description TEXT,
    contact_email CITEXT,
    contact_phone TEXT,
    location TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT companies_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT companies_contact_email_not_blank CHECK (
        contact_email IS NULL OR btrim(contact_email::TEXT) <> ''
    ),
    CONSTRAINT companies_owner_name_unique UNIQUE (employer_id, name)
);

CREATE INDEX idx_companies_employer_id ON recruitment.companies (employer_id);

CREATE TRIGGER companies_set_updated_at
    BEFORE UPDATE ON recruitment.companies
    FOR EACH ROW EXECUTE FUNCTION recruitment.set_updated_at();

CREATE OR REPLACE FUNCTION recruitment.enforce_company_employer_role()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM recruitment.users AS u
        WHERE u.id = NEW.employer_id AND u.role = 'EMPLOYER'
    ) THEN
        RAISE EXCEPTION 'company owner must have EMPLOYER role';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER companies_enforce_employer_role
    BEFORE INSERT OR UPDATE OF employer_id ON recruitment.companies
    FOR EACH ROW EXECUTE FUNCTION recruitment.enforce_company_employer_role();

-- Preserve existing jobs by assigning one deterministic migration company per employer.
INSERT INTO recruitment.companies (employer_id, name, description, location)
SELECT u.id,
       'Legacy company ' || u.id::TEXT,
       'Company created while migrating existing job postings.',
       NULL
FROM recruitment.users AS u
WHERE u.role = 'EMPLOYER'
  AND EXISTS (SELECT 1 FROM recruitment.jobs AS j WHERE j.employer_id = u.id);

ALTER TABLE recruitment.jobs ADD COLUMN company_id UUID;

UPDATE recruitment.jobs AS j
SET company_id = c.id
FROM recruitment.companies AS c
WHERE c.employer_id = j.employer_id
  AND c.name = 'Legacy company ' || j.employer_id::TEXT;

ALTER TABLE recruitment.jobs
    ALTER COLUMN company_id SET NOT NULL,
    ADD CONSTRAINT jobs_company_fk FOREIGN KEY (company_id)
        REFERENCES recruitment.companies (id) ON DELETE RESTRICT;

CREATE INDEX idx_jobs_company_id ON recruitment.jobs (company_id);

CREATE OR REPLACE FUNCTION recruitment.enforce_job_company_owner()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM recruitment.companies AS c
        WHERE c.id = NEW.company_id AND c.employer_id = NEW.employer_id
    ) THEN
        RAISE EXCEPTION 'job company must belong to job employer';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER jobs_enforce_company_owner
    BEFORE INSERT OR UPDATE OF employer_id, company_id ON recruitment.jobs
    FOR EACH ROW EXECUTE FUNCTION recruitment.enforce_job_company_owner();

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
    CASE
        WHEN j.status = 'ACTIVE' AND j.due_date <= CURRENT_DATE THEN 'EXPIRED'::recruitment.job_status
        ELSE j.status
    END AS status,
    j.created_at,
    j.updated_at
FROM recruitment.jobs AS j;

ALTER VIEW recruitment.current_jobs SET (security_invoker = true);

ALTER TABLE recruitment.companies ENABLE ROW LEVEL SECURITY;
ALTER TABLE recruitment.companies FORCE ROW LEVEL SECURITY;

CREATE POLICY companies_owner_or_admin ON recruitment.companies
    USING (
        current_setting('app.role', true) = 'ADMIN'
        OR employer_id::TEXT = current_setting('app.user_id', true)
    )
    WITH CHECK (
        current_setting('app.role', true) = 'ADMIN'
        OR (
            current_setting('app.role', true) = 'EMPLOYER'
            AND employer_id::TEXT = current_setting('app.user_id', true)
        )
    );
