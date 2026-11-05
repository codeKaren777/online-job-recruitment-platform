ALTER TABLE recruitment.profiles
    ADD COLUMN full_name TEXT,
    ADD COLUMN date_of_birth DATE,
    ADD COLUMN location TEXT;

UPDATE recruitment.profiles
SET full_name = display_name
WHERE full_name IS NULL;

ALTER TABLE recruitment.profiles
    ALTER COLUMN full_name SET NOT NULL,
    ADD CONSTRAINT profiles_full_name_not_blank CHECK (btrim(full_name) <> '');
