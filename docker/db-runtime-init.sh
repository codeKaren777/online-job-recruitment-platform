#!/bin/sh
set -eu

psql -v ON_ERROR_STOP=1 \
  --set=runtime_user="$POSTGRES_RUNTIME_USER" \
  --set=runtime_password="$POSTGRES_RUNTIME_PASSWORD" <<'SQL'
SELECT format(
    CASE WHEN EXISTS (
        SELECT 1 FROM pg_roles WHERE rolname = :'runtime_user'
    )
    THEN 'ALTER ROLE %I WITH LOGIN PASSWORD %L NOSUPERUSER NOBYPASSRLS'
    ELSE 'CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOBYPASSRLS'
    END,
    :'runtime_user',
    :'runtime_password'
)
\gexec

SELECT format('GRANT USAGE ON SCHEMA recruitment TO %I', :'runtime_user')
\gexec

SELECT format(
    'ALTER DEFAULT PRIVILEGES IN SCHEMA recruitment GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO %I',
    :'runtime_user'
)
\gexec

SELECT format(
    'ALTER DEFAULT PRIVILEGES IN SCHEMA recruitment GRANT USAGE, SELECT ON SEQUENCES TO %I',
    :'runtime_user'
)
\gexec

SELECT format(
    'ALTER DEFAULT PRIVILEGES IN SCHEMA recruitment GRANT EXECUTE ON FUNCTIONS TO %I',
    :'runtime_user'
)
\gexec

SELECT format(
    'GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA recruitment TO %I',
    :'runtime_user'
)
\gexec

SELECT format(
    'GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA recruitment TO %I',
    :'runtime_user'
)
\gexec
SQL

psql -v ON_ERROR_STOP=1 \
  --set=runtime_user="$POSTGRES_RUNTIME_USER" <<'SQL'
WITH function_signatures(signature) AS (
    VALUES
        ('auth_lookup_user(text)'),
        ('auth_register_user(text, text, text)'),
        ('auth_store_password_reset_token(text, text, timestamptz)'),
        ('auth_consume_password_reset_token(text, text)'),
        ('enqueue_application_notification(uuid, uuid, text)'),
        ('claim_application_notifications(integer)'),
        ('mark_application_notification_sent(uuid, uuid)'),
        ('record_application_notification_failure(uuid, uuid, text, boolean)')
)
SELECT format(
    'GRANT EXECUTE ON FUNCTION recruitment.%s TO %I',
    signature,
    :'runtime_user'
)
FROM function_signatures
WHERE to_regprocedure('recruitment.' || signature) IS NOT NULL
\gexec
SQL
