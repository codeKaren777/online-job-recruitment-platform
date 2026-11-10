ALTER TABLE recruitment.employer_upgrade_requests
    ADD COLUMN vat_number TEXT NOT NULL DEFAULT 'LEGACY',
    ADD COLUMN registration_number TEXT NOT NULL DEFAULT 'LEGACY',
    ADD COLUMN document_storage_key TEXT NOT NULL DEFAULT 'legacy/unavailable',
    ADD COLUMN document_original_filename TEXT NOT NULL DEFAULT 'legacy.pdf',
    ADD COLUMN document_content_type TEXT NOT NULL DEFAULT 'application/pdf',
    ADD COLUMN document_size BIGINT NOT NULL DEFAULT 1,
    ADD COLUMN company_id UUID;

ALTER TABLE recruitment.employer_upgrade_requests
    ADD CONSTRAINT employer_requests_document_size_valid CHECK (document_size BETWEEN 1 AND 5242880),
    ADD CONSTRAINT employer_requests_document_metadata_valid CHECK (
        btrim(document_storage_key) <> '' AND btrim(document_original_filename) <> ''
        AND document_content_type = 'application/pdf'
    ),
    ADD CONSTRAINT employer_requests_vat_number_valid CHECK (
        btrim(vat_number) <> '' AND char_length(vat_number) <= 80
    ),
    ADD CONSTRAINT employer_requests_registration_number_valid CHECK (
        btrim(registration_number) <> '' AND char_length(registration_number) <= 80
    ),
    ADD CONSTRAINT employer_requests_company_unique UNIQUE (company_id);

ALTER TABLE recruitment.companies
    ADD CONSTRAINT companies_id_employer_unique UNIQUE (id, employer_id);

ALTER TABLE recruitment.employer_upgrade_requests
    ADD CONSTRAINT employer_requests_company_owner_fk FOREIGN KEY (company_id, user_id)
        REFERENCES recruitment.companies(id, employer_id) ON DELETE RESTRICT;

ALTER TABLE recruitment.employer_upgrade_requests
    ADD CONSTRAINT employer_requests_approval_company_consistent CHECK (
        (status = 'APPROVED' AND company_id IS NOT NULL)
        OR (status <> 'APPROVED' AND company_id IS NULL)
    ) NOT VALID;

CREATE OR REPLACE FUNCTION recruitment.require_company_verification_document()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.document_storage_key NOT LIKE 'documents/company-verifications/%'
       OR NEW.document_original_filename = 'legacy.pdf'
       OR upper(btrim(NEW.vat_number)) = 'LEGACY'
       OR upper(btrim(NEW.registration_number)) = 'LEGACY' THEN
        RAISE EXCEPTION 'a private registration document is required';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER employer_requests_require_registration_document
    BEFORE INSERT OR UPDATE OF document_storage_key, document_original_filename
    ON recruitment.employer_upgrade_requests
    FOR EACH ROW EXECUTE FUNCTION recruitment.require_company_verification_document();
