CREATE TABLE recruitment.rate_limit_windows (
    bucket_key varchar(64) PRIMARY KEY,
    window_started_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    request_count integer NOT NULL CHECK (request_count >= 0)
);

CREATE INDEX rate_limit_windows_expires_idx
    ON recruitment.rate_limit_windows (expires_at);

CREATE FUNCTION recruitment.increment_rate_limit(
    p_bucket_key varchar,
    p_window_seconds integer
) RETURNS integer
LANGUAGE plpgsql
VOLATILE
SECURITY DEFINER
SET search_path = recruitment, pg_catalog
AS $$
DECLARE
    next_count integer;
BEGIN
    DELETE FROM recruitment.rate_limit_windows WHERE expires_at <= now();

    INSERT INTO recruitment.rate_limit_windows (
        bucket_key,
        window_started_at,
        expires_at,
        request_count
    )
    VALUES (
        p_bucket_key,
        now(),
        now() + (p_window_seconds * interval '1 second'),
        1
    )
    ON CONFLICT (bucket_key) DO UPDATE
    SET request_count = recruitment.rate_limit_windows.request_count + 1
    RETURNING request_count INTO next_count;

    RETURN next_count;
END;
$$;

ALTER FUNCTION recruitment.increment_rate_limit(varchar, integer) OWNER TO CURRENT_USER;
