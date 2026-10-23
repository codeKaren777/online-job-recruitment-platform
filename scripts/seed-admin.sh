#!/usr/bin/env bash
set -euo pipefail

if [[ -f .env ]]; then
    while IFS= read -r line || [[ -n "$line" ]]; do
        [[ "$line" =~ ^[[:space:]]*$ || "$line" =~ ^[[:space:]]*# ]] && continue
        if [[ "$line" =~ ^([A-Za-z_][A-Za-z0-9_]*)=(.*)$ ]]; then
            export "${BASH_REMATCH[1]}=${BASH_REMATCH[2]}"
        fi
    done < .env
fi

if [[ -z "${ADMIN_EMAIL:-}" || -z "${ADMIN_PASSWORD:-}" ]]; then
    echo "ADMIN_EMAIL and ADMIN_PASSWORD must be set" >&2
    exit 1
fi

if [[ ! -f target/recruitment.jar ]]; then
    echo "target/recruitment.jar was not found; run ./mvnw -B -ntp package first" >&2
    exit 1
fi

exec java -jar target/recruitment.jar \
    --server.port=0 \
    --app.admin-seed.enabled=true
