ALTER TABLE recruitment.profiles
    ADD COLUMN profile_image_storage_key TEXT,
    ADD COLUMN profile_image_content_type TEXT,
    ADD CONSTRAINT profiles_image_fields_paired CHECK (
        (profile_image_storage_key IS NULL) = (profile_image_content_type IS NULL)
    ),
    ADD CONSTRAINT profiles_image_content_type_allowed CHECK (
        profile_image_content_type IS NULL
        OR profile_image_content_type IN ('image/jpeg', 'image/png', 'image/webp')
    );

CREATE UNIQUE INDEX profiles_image_storage_key_key
    ON recruitment.profiles (profile_image_storage_key)
    WHERE profile_image_storage_key IS NOT NULL;
