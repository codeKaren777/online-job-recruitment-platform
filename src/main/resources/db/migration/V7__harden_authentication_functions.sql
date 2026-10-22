-- Authentication must remain available to the application role while ordinary
-- user-table access continues to be governed by RLS. These functions expose
-- only the deliberately narrow authentication contract.
ALTER FUNCTION recruitment.auth_lookup_user(text) SECURITY DEFINER;
ALTER FUNCTION recruitment.auth_lookup_user(text) SET search_path = pg_catalog;

ALTER FUNCTION recruitment.auth_register_user(text, text, text) SECURITY DEFINER;
ALTER FUNCTION recruitment.auth_register_user(text, text, text) SET search_path = pg_catalog;

REVOKE ALL ON FUNCTION recruitment.auth_lookup_user(text) FROM PUBLIC;
REVOKE ALL ON FUNCTION recruitment.auth_register_user(text, text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION recruitment.auth_lookup_user(text) TO CURRENT_USER;
GRANT EXECUTE ON FUNCTION recruitment.auth_register_user(text, text, text) TO CURRENT_USER;
