-- Email verification is not part of the current registration workflow.
-- Preserve the V28 columns/functions for migration compatibility, but mark all
-- existing accounts verified and stop creating/consuming verification tokens.
UPDATE recruitment.users
SET email_verified = TRUE,
    email_verification_token_hash = NULL,
    email_verification_expires_at = NULL
WHERE email_verified = FALSE
   OR email_verification_token_hash IS NOT NULL;

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
    VALUES (p_email::public.citext, p_password_hash, 'JOB_SEEKER'::recruitment.user_role, TRUE)
    ON CONFLICT ON CONSTRAINT users_email_key DO NOTHING
    RETURNING u.id, u.email::text, u.role::text, u.status::text;
END
$function$;

CREATE OR REPLACE FUNCTION recruitment.auth_consume_password_reset_token(
    p_token_hash text, p_new_password_hash text)
RETURNS integer
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog
AS $function$
DECLARE affected integer;
BEGIN
    IF p_token_hash IS NULL OR p_new_password_hash IS NULL THEN RETURN 0; END IF;
    UPDATE recruitment.users
    SET password_hash = p_new_password_hash,
        token_version = token_version + 1,
        password_reset_token_hash = NULL,
        password_reset_expires_at = NULL
    WHERE password_reset_token_hash = p_token_hash
      AND password_reset_expires_at > CURRENT_TIMESTAMP
      AND status = 'ACTIVE'::recruitment.account_status;
    GET DIAGNOSTICS affected = ROW_COUNT;
    RETURN affected;
END
$function$;

CREATE OR REPLACE FUNCTION recruitment.auth_store_password_reset_token(
    p_email text, p_token_hash text, p_expires_at timestamptz)
RETURNS integer
LANGUAGE sql SECURITY DEFINER SET search_path = pg_catalog
AS $function$
    WITH changed AS (
        UPDATE recruitment.users
        SET password_reset_token_hash = p_token_hash,
            password_reset_expires_at = p_expires_at
        WHERE email = p_email::public.citext
          AND status = 'ACTIVE'::recruitment.account_status
        RETURNING 1)
    SELECT count(*)::integer FROM changed
$function$;

COMMENT ON FUNCTION recruitment.auth_register_user(text, text, text) IS
    'Registers active job seekers without requiring email verification.';
