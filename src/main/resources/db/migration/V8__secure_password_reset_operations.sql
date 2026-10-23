-- Password recovery is a public authentication flow, so it cannot rely on the
-- caller's app.user_id/app.role RLS context. Keep the database contract narrow:
-- callers can only store or consume a one-time reset token, never read users.

CREATE FUNCTION recruitment.auth_store_password_reset_token(
    p_email text,
    p_token_hash text,
    p_expires_at timestamptz
)
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog
AS $function$
DECLARE
    affected integer;
BEGIN
    IF p_email IS NULL OR p_token_hash IS NULL OR p_expires_at IS NULL THEN
        RETURN 0;
    END IF;

    UPDATE recruitment.users
    SET password_reset_token_hash = p_token_hash,
        password_reset_expires_at = p_expires_at
    WHERE email = p_email::public.citext
      AND status = 'ACTIVE'::recruitment.account_status;

    GET DIAGNOSTICS affected = ROW_COUNT;
    RETURN affected;
END
$function$;

CREATE FUNCTION recruitment.auth_consume_password_reset_token(
    p_token_hash text,
    p_new_password_hash text
)
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog
AS $function$
DECLARE
    affected integer;
BEGIN
    IF p_token_hash IS NULL OR p_new_password_hash IS NULL THEN
        RETURN 0;
    END IF;

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

REVOKE ALL ON FUNCTION recruitment.auth_store_password_reset_token(text, text, timestamptz) FROM PUBLIC;
REVOKE ALL ON FUNCTION recruitment.auth_consume_password_reset_token(text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION recruitment.auth_store_password_reset_token(text, text, timestamptz) TO CURRENT_USER;
GRANT EXECUTE ON FUNCTION recruitment.auth_consume_password_reset_token(text, text) TO CURRENT_USER;

COMMENT ON FUNCTION recruitment.auth_store_password_reset_token(text, text, timestamptz) IS
    'Stores a password-reset hash for an active account without exposing user-table access.';
COMMENT ON FUNCTION recruitment.auth_consume_password_reset_token(text, text) IS
    'Consumes one unexpired password-reset hash and invalidates active sessions.';
