#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.frontend-dev.env"

if [[ ! -f "$ENV_FILE" ]]; then
    command -v openssl >/dev/null 2>&1 || {
        printf 'openssl is required to create local development credentials.\n' >&2
        exit 1
    }

    jwt_secret="$(openssl rand -base64 32 | tr -d '\n')"
    database_password="$(openssl rand -hex 32)"
    runtime_password="$(openssl rand -hex 32)"
    minio_password="$(openssl rand -hex 32)"
    storage_secret="$(openssl rand -hex 32)"

    umask 077
    cat > "$ENV_FILE" <<EOF
POSTGRES_DB=recruitment
POSTGRES_USER=recruitment
POSTGRES_PASSWORD=$database_password
POSTGRES_RUNTIME_USER=recruitment_app
POSTGRES_RUNTIME_PASSWORD=$runtime_password
DB_URL=jdbc:postgresql://localhost:5432/recruitment
JWT_SECRET_BASE64=$jwt_secret
JWT_ISSUER=groupcc2-recruitment
JWT_AUDIENCE=groupcc2-api
SPRING_FLYWAY_BASELINE_ON_MIGRATE=true
SPRING_FLYWAY_BASELINE_VERSION=1
MINIO_ROOT_USER=frontend-dev
MINIO_ROOT_PASSWORD=$minio_password
PROFILE_STORAGE_ENABLED=true
PROFILE_STORAGE_ENDPOINT=http://localhost:9000
PROFILE_STORAGE_PUBLIC_ENDPOINT=http://localhost:9000
PROFILE_STORAGE_REGION=us-east-1
PROFILE_STORAGE_BUCKET=recruitment-documents
PROFILE_STORAGE_ACCESS_KEY=recruitment_app
PROFILE_STORAGE_SECRET_KEY=$storage_secret
PROFILE_DOWNLOAD_SECONDS=120
APP_MAIL_ENABLED=false
APP_MAIL_POLL_INTERVAL_MS=30000
SPRING_MAIL_PORT=587
SPRING_MAIL_AUTH=true
SPRING_MAIL_STARTTLS_ENABLE=true
EOF
    printf 'Created local development configuration at %s\n' "$ENV_FILE"
fi

cd "$ROOT_DIR"
export COMPOSE_PROJECT_NAME=frontend-dev
exec docker compose --env-file "$ENV_FILE" up --build -d --wait
