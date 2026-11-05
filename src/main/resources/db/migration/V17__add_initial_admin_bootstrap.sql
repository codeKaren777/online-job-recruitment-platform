CREATE FUNCTION recruitment.auth_bootstrap_admin(
    p_email text,
    p_password_hash text
)
RETURNS smallint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog
AS $function$
BEGIN
    IF p_email IS NULL OR btrim(p_email) = ''
       OR p_password_hash IS NULL OR btrim(p_password_hash) = '' THEN
        RAISE EXCEPTION USING
            ERRCODE = '22023',
            MESSAGE = 'Bootstrap administrator data is invalid';
    END IF;

    PERFORM pg_advisory_xact_lock(hashtextextended('recruitment.bootstrap_admin', 0));

    IF EXISTS (
        SELECT 1
        FROM recruitment.users
        WHERE role = 'ADMIN'::recruitment.user_role
    ) THEN
        RETURN 0;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM recruitment.users
        WHERE email = p_email::public.citext
    ) THEN
        RETURN -1;
    END IF;

    INSERT INTO recruitment.users (email, password_hash, role)
    VALUES (p_email::public.citext, p_password_hash, 'ADMIN'::recruitment.user_role);

    RETURN 1;
END
$function$;

REVOKE ALL ON FUNCTION recruitment.auth_bootstrap_admin(text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION recruitment.auth_bootstrap_admin(text, text) TO CURRENT_USER;

COMMENT ON FUNCTION recruitment.auth_bootstrap_admin(text, text) IS
    'Creates the first active administrator only when no administrator exists.';
