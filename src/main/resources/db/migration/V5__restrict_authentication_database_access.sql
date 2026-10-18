CREATE FUNCTION recruitment.auth_lookup_user(p_email text)
RETURNS TABLE (
    id uuid,
    password_hash text,
    role text,
    status text
)
LANGUAGE sql
STABLE
SECURITY INVOKER
SET search_path = pg_catalog
AS $function$
    SELECT u.id, u.password_hash, u.role::text, u.status::text
    FROM recruitment.users AS u
    WHERE u.email = p_email::public.citext
$function$;

CREATE FUNCTION recruitment.auth_register_user(
    p_email text,
    p_password_hash text,
    p_role text
)
RETURNS TABLE (
    id uuid,
    email text,
    role text,
    status text
)
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
    IF p_role IS NULL OR p_role NOT IN ('JOB_SEEKER', 'EMPLOYER') THEN
        RAISE EXCEPTION USING
            ERRCODE = '22023',
            MESSAGE = 'Registration role is not permitted';
    END IF;
    IF p_email IS NULL OR btrim(p_email) = ''
       OR p_password_hash IS NULL OR btrim(p_password_hash) = '' THEN
        RAISE EXCEPTION USING
            ERRCODE = '22023',
            MESSAGE = 'Registration data is invalid';
    END IF;

    INSERT INTO recruitment.users AS inserted_user (email, password_hash, role)
    VALUES (
        p_email::public.citext,
        p_password_hash,
        p_role::recruitment.user_role)
    ON CONFLICT ON CONSTRAINT users_email_key DO NOTHING
    RETURNING inserted_user.id, inserted_user.email::text,
              inserted_user.role::text, inserted_user.status::text
    INTO created_id, created_email, created_role, created_status;

    IF NOT FOUND THEN
        RETURN;
    END IF;

    RETURN QUERY
    SELECT created_id, created_email, created_role, created_status;
END
$function$;

REVOKE ALL ON FUNCTION recruitment.auth_lookup_user(text) FROM PUBLIC;
REVOKE ALL ON FUNCTION recruitment.auth_register_user(text, text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION recruitment.auth_lookup_user(text) TO CURRENT_USER;
GRANT EXECUTE ON FUNCTION recruitment.auth_register_user(text, text, text) TO CURRENT_USER;

COMMENT ON FUNCTION recruitment.auth_lookup_user(text) IS
    'Authentication lookup; execution is limited to the configured application database account.';
COMMENT ON FUNCTION recruitment.auth_register_user(text, text, text) IS
    'Registration insert; accepts only a pre-hashed password and non-admin role.';
