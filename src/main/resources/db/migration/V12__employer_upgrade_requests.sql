DO $$
BEGIN
    CREATE TYPE recruitment.employer_request_status AS ENUM ('PENDING', 'APPROVED', 'REJECTED');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END
$$;

CREATE TABLE recruitment.employer_upgrade_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES recruitment.users (id) ON DELETE RESTRICT,
    company_name TEXT NOT NULL,
    business_description TEXT NOT NULL,
    contact_email CITEXT NOT NULL,
    contact_phone TEXT,
    location TEXT NOT NULL,
    status recruitment.employer_request_status NOT NULL DEFAULT 'PENDING',
    reviewed_by UUID REFERENCES recruitment.users (id) ON DELETE RESTRICT,
    reviewed_at TIMESTAMPTZ,
    rejection_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT employer_requests_company_name_not_blank CHECK (btrim(company_name) <> ''),
    CONSTRAINT employer_requests_description_not_blank CHECK (btrim(business_description) <> ''),
    CONSTRAINT employer_requests_email_not_blank CHECK (btrim(contact_email::TEXT) <> ''),
    CONSTRAINT employer_requests_location_not_blank CHECK (btrim(location) <> ''),
    CONSTRAINT employer_requests_review_fields_consistent CHECK (
        (status = 'PENDING' AND reviewed_by IS NULL AND reviewed_at IS NULL AND rejection_reason IS NULL)
        OR (status = 'APPROVED' AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL AND rejection_reason IS NULL)
        OR (status = 'REJECTED' AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL AND rejection_reason IS NOT NULL
            AND btrim(rejection_reason) <> '')
    )
);

CREATE INDEX idx_employer_requests_status_created_at
    ON recruitment.employer_upgrade_requests (status, created_at);
CREATE INDEX idx_employer_requests_user_id
    ON recruitment.employer_upgrade_requests (user_id, created_at DESC);
CREATE UNIQUE INDEX employer_requests_one_pending_per_user
    ON recruitment.employer_upgrade_requests (user_id)
    WHERE status = 'PENDING';

CREATE TRIGGER employer_requests_set_updated_at
    BEFORE UPDATE ON recruitment.employer_upgrade_requests
    FOR EACH ROW EXECUTE FUNCTION recruitment.set_updated_at();

CREATE OR REPLACE FUNCTION recruitment.enforce_employer_request_applicant_role()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM recruitment.users
        WHERE id = NEW.user_id AND role = 'JOB_SEEKER'
    ) THEN
        RAISE EXCEPTION 'only JOB_SEEKER users may submit employer requests';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER employer_requests_enforce_applicant_role
    BEFORE INSERT ON recruitment.employer_upgrade_requests
    FOR EACH ROW EXECUTE FUNCTION recruitment.enforce_employer_request_applicant_role();

ALTER TABLE recruitment.employer_upgrade_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE recruitment.employer_upgrade_requests FORCE ROW LEVEL SECURITY;

CREATE POLICY employer_requests_owner_or_admin ON recruitment.employer_upgrade_requests
    USING (
        current_setting('app.role', true) = 'ADMIN'
        OR user_id::TEXT = current_setting('app.user_id', true)
    )
    WITH CHECK (
        current_setting('app.role', true) = 'ADMIN'
        OR (
            current_setting('app.role', true) = 'JOB_SEEKER'
            AND user_id::TEXT = current_setting('app.user_id', true)
        )
    );

-- Keep the database registration function aligned with the public API contract.
CREATE OR REPLACE FUNCTION recruitment.auth_register_user(
    p_email text,
    p_password_hash text,
    p_role text
)
RETURNS TABLE (id uuid, email text, role text, status text)
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog
AS $function$
DECLARE
    created_id uuid;
    created_email text;
    created_role text;
    created_status text;
BEGIN
    IF p_role IS NULL OR p_role <> 'JOB_SEEKER' THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'Registration role is not permitted';
    END IF;
    IF p_email IS NULL OR btrim(p_email) = ''
       OR p_password_hash IS NULL OR btrim(p_password_hash) = '' THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'Registration data is invalid';
    END IF;

    INSERT INTO recruitment.users AS inserted_user (email, password_hash, role)
    VALUES (p_email::public.citext, p_password_hash, 'JOB_SEEKER'::recruitment.user_role)
    ON CONFLICT ON CONSTRAINT users_email_key DO NOTHING
    RETURNING inserted_user.id, inserted_user.email::text,
              inserted_user.role::text, inserted_user.status::text
    INTO created_id, created_email, created_role, created_status;

    IF NOT FOUND THEN RETURN; END IF;
    RETURN QUERY SELECT created_id, created_email, created_role, created_status;
END
$function$;
