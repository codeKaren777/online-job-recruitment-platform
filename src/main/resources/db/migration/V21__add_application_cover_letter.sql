ALTER TABLE recruitment.applications
    ADD COLUMN cover_letter VARCHAR(10000);

ALTER TABLE recruitment.applications
    ADD CONSTRAINT applications_cover_letter_not_blank
        CHECK (cover_letter IS NULL OR btrim(cover_letter) <> '');
