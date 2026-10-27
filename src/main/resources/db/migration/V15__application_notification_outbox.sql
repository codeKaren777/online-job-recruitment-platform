CREATE TABLE recruitment.application_notification_outbox (
    event_id UUID PRIMARY KEY,
    application_id UUID NOT NULL REFERENCES recruitment.applications (id) ON DELETE CASCADE,
    event_type TEXT NOT NULL CHECK (event_type IN ('SUBMITTED', 'SHORTLISTED', 'REJECTED')),
    recipient_email CITEXT NOT NULL,
    applicant_name TEXT,
    job_title TEXT NOT NULL,
    company_name TEXT,
    application_status recruitment.application_status NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL,
    event_at TIMESTAMPTZ NOT NULL,
    delivery_state TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (delivery_state IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED')),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    next_attempt_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    last_attempted_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    claim_token UUID,
    claimed_until TIMESTAMPTZ,
    last_error_code TEXT,
    CONSTRAINT application_notification_status_matches_event CHECK (
        (event_type = 'SUBMITTED' AND application_status = 'PENDING')
        OR (event_type = 'SHORTLISTED' AND application_status = 'SHORTLISTED')
        OR (event_type = 'REJECTED' AND application_status = 'REJECTED')
    ),
    CONSTRAINT application_notification_claim_matches_state CHECK (
        (delivery_state = 'PROCESSING' AND claim_token IS NOT NULL AND claimed_until IS NOT NULL)
        OR (delivery_state <> 'PROCESSING' AND claim_token IS NULL AND claimed_until IS NULL)
    ),
    CONSTRAINT application_notification_sent_matches_state CHECK (
        (delivery_state = 'SENT' AND sent_at IS NOT NULL)
        OR (delivery_state <> 'SENT' AND sent_at IS NULL)
    ),
    CONSTRAINT application_notification_retry_matches_state CHECK (
        (delivery_state = 'PENDING' AND next_attempt_at IS NOT NULL)
        OR (delivery_state <> 'PENDING' AND next_attempt_at IS NULL)
    )
);

CREATE INDEX idx_application_notification_due
    ON recruitment.application_notification_outbox (next_attempt_at, created_at)
    WHERE delivery_state = 'PENDING';
CREATE INDEX idx_application_notification_expired_claim
    ON recruitment.application_notification_outbox (claimed_until)
    WHERE delivery_state = 'PROCESSING';
CREATE INDEX idx_application_notification_retention
    ON recruitment.application_notification_outbox (delivery_state, sent_at, created_at);

ALTER TABLE recruitment.application_notification_outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE recruitment.application_notification_outbox FORCE ROW LEVEL SECURITY;

CREATE POLICY application_notification_internal_access
    ON recruitment.application_notification_outbox
    USING (current_setting('app.notification_worker', true) = 'true')
    WITH CHECK (current_setting('app.notification_worker', true) = 'true');

REVOKE ALL ON recruitment.application_notification_outbox FROM PUBLIC;

CREATE FUNCTION recruitment.enqueue_application_notification(
    p_event_id UUID,
    p_application_id UUID,
    p_event_type TEXT
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, recruitment
AS $function$
DECLARE
    v_user_id UUID;
    v_role TEXT;
    v_recipient_email TEXT;
    v_applicant_name TEXT;
    v_job_title TEXT;
    v_company_name TEXT;
    v_status recruitment.application_status;
    v_submitted_at TIMESTAMPTZ;
    v_event_at TIMESTAMPTZ;
    v_delivery_state TEXT;
    v_inserted INTEGER;
BEGIN
    IF p_event_id IS NULL OR p_application_id IS NULL THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'Notification event identifiers are required';
    END IF;

    v_user_id := NULLIF(current_setting('app.user_id', true), '')::UUID;
    v_role := current_setting('app.role', true);

    IF p_event_type = 'SUBMITTED' THEN
        IF v_role IS DISTINCT FROM 'JOB_SEEKER' OR p_event_id <> p_application_id THEN
            RAISE EXCEPTION USING ERRCODE = '42501', MESSAGE = 'Notification event is not authorized';
        END IF;

        SELECT u.email::TEXT, p.display_name, j.title, c.name, a.status, a.submitted_at, a.submitted_at
          INTO v_recipient_email, v_applicant_name, v_job_title, v_company_name, v_status,
               v_submitted_at, v_event_at
          FROM recruitment.applications AS a
          JOIN recruitment.users AS u ON u.id = a.job_seeker_id
          LEFT JOIN recruitment.profiles AS p ON p.user_id = u.id
          JOIN recruitment.jobs AS j ON j.id = a.job_id
          LEFT JOIN recruitment.companies AS c ON c.id = j.company_id AND c.employer_id = j.employer_id
         WHERE a.id = p_application_id
           AND a.job_seeker_id = v_user_id
           AND a.status = 'PENDING'
           AND u.role = 'JOB_SEEKER'
           AND u.status = 'ACTIVE';
    ELSIF p_event_type IN ('SHORTLISTED', 'REJECTED') THEN
        IF v_role IS DISTINCT FROM 'EMPLOYER' THEN
            RAISE EXCEPTION USING ERRCODE = '42501', MESSAGE = 'Notification event is not authorized';
        END IF;

        SELECT u.email::TEXT, p.display_name, j.title, c.name, a.status, a.submitted_at, d.decided_at
          INTO v_recipient_email, v_applicant_name, v_job_title, v_company_name, v_status,
               v_submitted_at, v_event_at
          FROM recruitment.application_decision_audits AS d
          JOIN recruitment.applications AS a ON a.id = d.application_id
          JOIN recruitment.users AS u ON u.id = a.job_seeker_id
          LEFT JOIN recruitment.profiles AS p ON p.user_id = u.id
          JOIN recruitment.jobs AS j ON j.id = a.job_id
          LEFT JOIN recruitment.companies AS c ON c.id = j.company_id AND c.employer_id = j.employer_id
         WHERE d.id = p_event_id
           AND d.application_id = p_application_id
           AND d.new_status::TEXT = p_event_type
           AND a.status = d.new_status
           AND d.employer_id = v_user_id
           AND j.employer_id = d.employer_id
           AND u.role = 'JOB_SEEKER'
           AND u.status = 'ACTIVE'
           AND EXISTS (
               SELECT 1 FROM recruitment.users AS employer
                WHERE employer.id = d.employer_id
                  AND employer.role = 'EMPLOYER'
                  AND employer.status = 'ACTIVE'
           );
    ELSE
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'Unsupported notification event';
    END IF;

    IF NOT FOUND THEN
        RAISE EXCEPTION USING ERRCODE = '42501', MESSAGE = 'Notification event is not authorized or available';
    END IF;

    v_delivery_state := CASE WHEN v_company_name IS NULL THEN 'FAILED' ELSE 'PENDING' END;
    PERFORM set_config('app.notification_worker', 'true', true);

    INSERT INTO recruitment.application_notification_outbox (
        event_id, application_id, event_type, recipient_email, applicant_name, job_title, company_name,
        application_status, submitted_at, event_at, delivery_state, next_attempt_at, last_error_code
    )
    VALUES (
        p_event_id, p_application_id, p_event_type, v_recipient_email, v_applicant_name,
        v_job_title, v_company_name, v_status, v_submitted_at, v_event_at, v_delivery_state,
        CASE WHEN v_delivery_state = 'PENDING' THEN CURRENT_TIMESTAMP ELSE NULL END,
        CASE WHEN v_delivery_state = 'FAILED' THEN 'COMPANY_INFORMATION_UNAVAILABLE' ELSE NULL END
    )
    ON CONFLICT (event_id) DO NOTHING;

    GET DIAGNOSTICS v_inserted = ROW_COUNT;
    RETURN v_inserted = 1;
END
$function$;

CREATE FUNCTION recruitment.claim_application_notifications(p_limit INTEGER)
RETURNS TABLE (
    event_id UUID,
    event_type TEXT,
    recipient_email TEXT,
    applicant_name TEXT,
    job_title TEXT,
    company_name TEXT,
    application_status TEXT,
    submitted_at TIMESTAMPTZ,
    event_at TIMESTAMPTZ,
    attempt_count INTEGER,
    claim_token UUID
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, recruitment
AS $function$
DECLARE
    v_now TIMESTAMPTZ := clock_timestamp();
BEGIN
    IF p_limit IS NULL OR p_limit < 1 OR p_limit > 100 THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'Notification batch size is invalid';
    END IF;

    PERFORM set_config('app.notification_worker', 'true', true);

    DELETE FROM recruitment.application_notification_outbox AS o
     WHERE (o.delivery_state = 'SENT' AND o.sent_at < v_now - INTERVAL '90 days')
        OR (o.delivery_state = 'FAILED' AND o.created_at < v_now - INTERVAL '365 days');

    UPDATE recruitment.application_notification_outbox AS o
       SET delivery_state = 'FAILED',
           claim_token = NULL,
           claimed_until = NULL,
           next_attempt_at = NULL,
           last_error_code = 'DELIVERY_ATTEMPTS_EXHAUSTED'
     WHERE o.delivery_state = 'PROCESSING'
       AND o.claimed_until <= v_now
       AND o.attempt_count >= 5;

    RETURN QUERY
    WITH candidates AS (
        SELECT o.event_id
          FROM recruitment.application_notification_outbox AS o
         WHERE (o.delivery_state = 'PENDING' AND o.next_attempt_at <= v_now AND o.attempt_count < 5)
            OR (o.delivery_state = 'PROCESSING' AND o.claimed_until <= v_now AND o.attempt_count < 5)
         ORDER BY COALESCE(o.next_attempt_at, o.claimed_until), o.created_at
         FOR UPDATE SKIP LOCKED
         LIMIT p_limit
    ),
    claimed AS (
        UPDATE recruitment.application_notification_outbox AS o
           SET delivery_state = 'PROCESSING',
               attempt_count = o.attempt_count + 1,
               last_attempted_at = v_now,
               next_attempt_at = NULL,
               claim_token = gen_random_uuid(),
               claimed_until = v_now + INTERVAL '5 minutes'
          FROM candidates AS c
         WHERE o.event_id = c.event_id
        RETURNING o.*
    )
    SELECT c.event_id, c.event_type, c.recipient_email::TEXT, c.applicant_name, c.job_title,
           c.company_name, c.application_status::TEXT, c.submitted_at, c.event_at,
           c.attempt_count, c.claim_token
      FROM claimed AS c;
END
$function$;

CREATE FUNCTION recruitment.mark_application_notification_sent(p_event_id UUID, p_claim_token UUID)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, recruitment
AS $function$
DECLARE
    v_updated INTEGER;
BEGIN
    PERFORM set_config('app.notification_worker', 'true', true);
    UPDATE recruitment.application_notification_outbox
       SET delivery_state = 'SENT',
           sent_at = CURRENT_TIMESTAMP,
           claim_token = NULL,
           claimed_until = NULL,
           last_error_code = NULL
     WHERE event_id = p_event_id
       AND delivery_state = 'PROCESSING'
       AND claim_token = p_claim_token;
    GET DIAGNOSTICS v_updated = ROW_COUNT;
    RETURN v_updated = 1;
END
$function$;

CREATE FUNCTION recruitment.record_application_notification_failure(
    p_event_id UUID,
    p_claim_token UUID,
    p_error_code TEXT,
    p_permanent BOOLEAN
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, recruitment
AS $function$
DECLARE
    v_attempt_count INTEGER;
    v_updated INTEGER;
    v_delivery_state TEXT;
    v_safe_error_code TEXT;
BEGIN
    IF p_permanent IS NULL THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'Failure type is required';
    END IF;

    PERFORM set_config('app.notification_worker', 'true', true);
    v_safe_error_code := CASE
        WHEN p_error_code IN (
            'MAIL_PERMANENT_ERROR', 'MAIL_DELIVERY_ERROR', 'PERMANENT_CONTENT_ERROR',
            'COMPANY_INFORMATION_UNAVAILABLE', 'DELIVERY_ATTEMPTS_EXHAUSTED'
        ) THEN p_error_code
        ELSE 'DELIVERY_ERROR'
    END;

    SELECT attempt_count
      INTO v_attempt_count
      FROM recruitment.application_notification_outbox
     WHERE event_id = p_event_id
       AND delivery_state = 'PROCESSING'
       AND claim_token = p_claim_token
     FOR UPDATE;

    IF NOT FOUND THEN
        RETURN FALSE;
    END IF;

    v_delivery_state := CASE WHEN p_permanent OR v_attempt_count >= 5 THEN 'FAILED' ELSE 'PENDING' END;

    UPDATE recruitment.application_notification_outbox
       SET delivery_state = v_delivery_state,
           next_attempt_at = CASE
               WHEN v_delivery_state = 'PENDING' THEN
                   CURRENT_TIMESTAMP + (INTERVAL '30 seconds' * POWER(
                       2::DOUBLE PRECISION, LEAST(v_attempt_count - 1, 6)::DOUBLE PRECISION))
               ELSE NULL
           END,
           claim_token = NULL,
           claimed_until = NULL,
           last_error_code = COALESCE(NULLIF(v_safe_error_code, ''), 'DELIVERY_ERROR')
     WHERE event_id = p_event_id
       AND claim_token = p_claim_token;
    GET DIAGNOSTICS v_updated = ROW_COUNT;
    RETURN v_updated = 1;
END
$function$;

REVOKE ALL ON FUNCTION recruitment.enqueue_application_notification(uuid, uuid, text) FROM PUBLIC;
REVOKE ALL ON FUNCTION recruitment.claim_application_notifications(integer) FROM PUBLIC;
REVOKE ALL ON FUNCTION recruitment.mark_application_notification_sent(uuid, uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION recruitment.record_application_notification_failure(uuid, uuid, text, boolean)
    FROM PUBLIC;

COMMENT ON TABLE recruitment.application_notification_outbox IS
    'Durable application email events; event data are captured from trusted records at transaction time.';
COMMENT ON FUNCTION recruitment.enqueue_application_notification(uuid, uuid, text) IS
    'Validates actor, application/decision ownership and status before capturing only notification fields.';
COMMENT ON FUNCTION recruitment.claim_application_notifications(integer) IS
    'Claims due notifications with SKIP LOCKED, bounded attempts and recoverable leases.';
