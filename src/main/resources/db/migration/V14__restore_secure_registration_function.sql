-- V12 recreated this function as SECURITY INVOKER, which cannot insert into
-- the forced-RLS users table during unauthenticated registration. Restore the
-- narrow SECURITY DEFINER contract established in V7.
ALTER FUNCTION recruitment.auth_register_user(text, text, text) SECURITY DEFINER;
ALTER FUNCTION recruitment.auth_register_user(text, text, text) SET search_path = pg_catalog;

REVOKE ALL ON FUNCTION recruitment.auth_register_user(text, text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION recruitment.auth_register_user(text, text, text) TO CURRENT_USER;
