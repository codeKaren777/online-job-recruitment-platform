ALTER TABLE recruitment.companies
    ADD COLUMN vat_number TEXT,
    ADD COLUMN registration_number TEXT;

CREATE UNIQUE INDEX companies_vat_number_unique
    ON recruitment.companies (upper(btrim(vat_number)))
    WHERE vat_number IS NOT NULL;

CREATE UNIQUE INDEX companies_registration_number_unique
    ON recruitment.companies (upper(btrim(registration_number)))
    WHERE registration_number IS NOT NULL;
