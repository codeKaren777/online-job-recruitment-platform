-- Preserve each submitted CV as a distinct, profile-owned document row. Existing
-- applications predate this requirement and retain a NULL association.
ALTER TABLE recruitment.applications
    ADD COLUMN cv_document_id UUID;

ALTER TABLE recruitment.applications
    ADD CONSTRAINT applications_cv_document_fk
        FOREIGN KEY (cv_document_id) REFERENCES recruitment.documents(id) ON DELETE RESTRICT;

CREATE INDEX idx_applications_cv_document_id
    ON recruitment.applications (cv_document_id);

CREATE FUNCTION recruitment.require_application_cv()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = recruitment, pg_catalog
AS $$
BEGIN
    IF NEW.cv_document_id IS NULL THEN
        RAISE EXCEPTION 'new applications require an application CV';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM recruitment.documents d
        WHERE d.id = NEW.cv_document_id AND d.profile_user_id = NEW.job_seeker_id
    ) THEN
        RAISE EXCEPTION 'application CV must belong to the applicant';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER applications_require_cv
    BEFORE INSERT ON recruitment.applications
    FOR EACH ROW EXECUTE FUNCTION recruitment.require_application_cv();
REVOKE ALL ON FUNCTION recruitment.require_application_cv() FROM PUBLIC;

-- Application documents remain immutable associations after submission.
CREATE FUNCTION recruitment.prevent_application_cv_change()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = recruitment, pg_catalog
AS $$
BEGIN
    IF NEW.cv_document_id IS DISTINCT FROM OLD.cv_document_id THEN
        RAISE EXCEPTION 'application CV cannot be changed';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER applications_cv_immutable
    BEFORE UPDATE OF cv_document_id ON recruitment.applications
    FOR EACH ROW EXECUTE FUNCTION recruitment.prevent_application_cv_change();
REVOKE ALL ON FUNCTION recruitment.prevent_application_cv_change() FROM PUBLIC;
