ALTER TABLE recruitment.users
    ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN password_reset_token_hash TEXT,
    ADD COLUMN password_reset_expires_at TIMESTAMPTZ,
    ADD CONSTRAINT users_token_version_nonnegative CHECK (token_version >= 0),
    ADD CONSTRAINT users_password_reset_fields_paired CHECK (
        (password_reset_token_hash IS NULL) = (password_reset_expires_at IS NULL)
    );

CREATE UNIQUE INDEX users_password_reset_token_hash_key
    ON recruitment.users (password_reset_token_hash)
    WHERE password_reset_token_hash IS NOT NULL;

DROP FUNCTION recruitment.auth_lookup_user(text);

CREATE FUNCTION recruitment.auth_lookup_user(p_email text)
RETURNS TABLE (
    id uuid,
    password_hash text,
    role text,
    status text,
    token_version integer
)
LANGUAGE sql
STABLE
SECURITY INVOKER
SET search_path = pg_catalog
AS $function$
    SELECT u.id, u.password_hash, u.role::text, u.status::text, u.token_version
    FROM recruitment.users AS u
    WHERE u.email = p_email::public.citext
$function$;

REVOKE ALL ON FUNCTION recruitment.auth_lookup_user(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION recruitment.auth_lookup_user(text) TO CURRENT_USER;

COMMENT ON COLUMN recruitment.users.token_version IS
    'Incremented on logout or password change to invalidate previously issued access tokens.';
COMMENT ON COLUMN recruitment.users.password_reset_token_hash IS
    'SHA-256 hash of the current one-time password-reset token; raw tokens are never stored.';
