CREATE OR REPLACE FUNCTION recruitment.auth_register_user(
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
SECURITY DEFINER
SET search_path = pg_catalog
AS $function$
DECLARE
    created_id uuid;
    created_email text;
    created_role text;
    created_status text;
BEGIN
    IF p_role IS DISTINCT FROM 'JOB_SEEKER' THEN
        RAISE EXCEPTION USING
            ERRCODE = '22023',
            MESSAGE = 'Public registration is limited to JOB_SEEKER';
    END IF;
    IF p_email IS NULL OR btrim(p_email) = ''
       OR p_password_hash IS NULL OR btrim(p_password_hash) = '' THEN
        RAISE EXCEPTION USING
            ERRCODE = '22023',
            MESSAGE = 'Registration data is invalid';
    END IF;

    INSERT INTO recruitment.users AS inserted_user (email, password_hash, role)
    VALUES (p_email::public.citext, p_password_hash, 'JOB_SEEKER'::recruitment.user_role)
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

REVOKE ALL ON FUNCTION recruitment.auth_register_user(text, text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION recruitment.auth_register_user(text, text, text) TO CURRENT_USER;

COMMENT ON FUNCTION recruitment.auth_register_user(text, text, text) IS
    'Creates public accounts as JOB_SEEKER only; employer upgrades require an approved workflow.';
