ALTER TABLE recruitment.users
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN email_verification_token_hash TEXT,
    ADD COLUMN email_verification_expires_at TIMESTAMPTZ,
    ADD CONSTRAINT users_email_verification_fields_paired CHECK (
        (email_verification_token_hash IS NULL) = (email_verification_expires_at IS NULL)
    );

CREATE UNIQUE INDEX users_email_verification_token_hash_key
    ON recruitment.users (email_verification_token_hash)
    WHERE email_verification_token_hash IS NOT NULL;

CREATE OR REPLACE FUNCTION recruitment.auth_register_user(p_email text, p_password_hash text, p_role text)
RETURNS TABLE (id uuid, email text, role text, status text)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog
AS $function$
BEGIN
    IF p_role IS DISTINCT FROM 'JOB_SEEKER' OR p_email IS NULL OR btrim(p_email) = ''
       OR p_password_hash IS NULL OR btrim(p_password_hash) = '' THEN
        RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'Registration data is invalid';
    END IF;
    RETURN QUERY
    INSERT INTO recruitment.users AS u (email, password_hash, role, email_verified)
    VALUES (p_email::public.citext, p_password_hash, 'JOB_SEEKER'::recruitment.user_role, FALSE)
    ON CONFLICT ON CONSTRAINT users_email_key DO NOTHING
    RETURNING u.id, u.email::text, u.role::text, u.status::text;
END
$function$;

DROP FUNCTION recruitment.auth_lookup_user(text);
CREATE FUNCTION recruitment.auth_lookup_user(p_email text)
RETURNS TABLE (id uuid, password_hash text, role text, status text, token_version integer,
               email_verified boolean)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog
AS $function$
    SELECT u.id, u.password_hash, u.role::text, u.status::text, u.token_version, u.email_verified
    FROM recruitment.users AS u WHERE u.email = p_email::public.citext
$function$;
REVOKE ALL ON FUNCTION recruitment.auth_lookup_user(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION recruitment.auth_lookup_user(text) TO CURRENT_USER;

CREATE OR REPLACE FUNCTION recruitment.auth_store_email_verification(p_email text, p_hash text, p_expiry timestamptz)
RETURNS integer LANGUAGE sql SECURITY DEFINER SET search_path = pg_catalog
AS $function$
    WITH changed AS (
      UPDATE recruitment.users SET email_verification_token_hash=p_hash,
        email_verification_expires_at=p_expiry
      WHERE email=p_email::public.citext AND role='JOB_SEEKER' AND status='ACTIVE'
        AND email_verified=FALSE RETURNING 1)
    SELECT count(*)::integer FROM changed
$function$;

CREATE OR REPLACE FUNCTION recruitment.auth_consume_email_verification(p_hash text)
RETURNS integer LANGUAGE sql SECURITY DEFINER SET search_path = pg_catalog
AS $function$
    WITH changed AS (
      UPDATE recruitment.users SET email_verified=TRUE, email_verification_token_hash=NULL,
        email_verification_expires_at=NULL
      WHERE email_verification_token_hash=p_hash AND email_verification_expires_at>CURRENT_TIMESTAMP
        AND status='ACTIVE' AND role='JOB_SEEKER' AND email_verified=FALSE RETURNING 1)
    SELECT count(*)::integer FROM changed
$function$;

CREATE OR REPLACE FUNCTION recruitment.auth_consume_password_reset_token(p_token_hash text, p_new_password_hash text)
RETURNS integer LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog
AS $function$
DECLARE affected integer;
BEGIN
    IF p_token_hash IS NULL OR p_new_password_hash IS NULL THEN RETURN 0; END IF;
    UPDATE recruitment.users SET password_hash=p_new_password_hash,
      token_version=token_version+1, password_reset_token_hash=NULL, password_reset_expires_at=NULL
    WHERE password_reset_token_hash=p_token_hash AND password_reset_expires_at>CURRENT_TIMESTAMP
      AND status='ACTIVE'::recruitment.account_status AND email_verified=TRUE;
    GET DIAGNOSTICS affected=ROW_COUNT;
    RETURN affected;
END
$function$;

CREATE OR REPLACE FUNCTION recruitment.auth_store_password_reset_token(
    p_email text, p_token_hash text, p_expires_at timestamptz)
RETURNS integer LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog
AS $function$
DECLARE affected integer;
BEGIN
    IF p_email IS NULL OR p_token_hash IS NULL OR p_expires_at IS NULL THEN RETURN 0; END IF;
    UPDATE recruitment.users SET password_reset_token_hash=p_token_hash,
      password_reset_expires_at=p_expires_at
    WHERE email=p_email::public.citext AND status='ACTIVE'::recruitment.account_status
      AND email_verified=TRUE;
    GET DIAGNOSTICS affected=ROW_COUNT;
    RETURN affected;
END
$function$;

REVOKE ALL ON FUNCTION recruitment.auth_store_email_verification(text,text,timestamptz) FROM PUBLIC;
REVOKE ALL ON FUNCTION recruitment.auth_consume_email_verification(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION recruitment.auth_store_email_verification(text,text,timestamptz) TO CURRENT_USER;
GRANT EXECUTE ON FUNCTION recruitment.auth_consume_email_verification(text) TO CURRENT_USER;
