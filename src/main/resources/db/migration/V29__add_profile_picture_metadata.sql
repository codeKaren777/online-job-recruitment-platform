ALTER TABLE recruitment.profiles
    ADD COLUMN profile_picture_key TEXT,
    ADD COLUMN profile_picture_content_type TEXT,
    ADD CONSTRAINT profiles_picture_metadata_consistent CHECK (
        (profile_picture_key IS NULL AND profile_picture_content_type IS NULL)
        OR (profile_picture_key IS NOT NULL
            AND profile_picture_key LIKE 'profile-pictures/%'
            AND profile_picture_content_type IS NOT NULL
            AND profile_picture_content_type IN ('image/jpeg', 'image/png'))
    );
