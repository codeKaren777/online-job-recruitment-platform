-- Job seekers may see a company's display name through a job response without
-- gaining row-level access to private company contact or registration details.
CREATE FUNCTION recruitment.public_job_company_name(p_company_id UUID)
RETURNS TEXT
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = pg_catalog
AS $function$
    SELECT c.name
    FROM recruitment.companies AS c
    WHERE c.id = p_company_id
$function$;

REVOKE ALL ON FUNCTION recruitment.public_job_company_name(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION recruitment.public_job_company_name(UUID) TO CURRENT_USER;
