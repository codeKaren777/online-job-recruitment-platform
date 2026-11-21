#!/usr/bin/env bash
set -Eeuo pipefail

DEPLOY_PATH="${DEPLOY_PATH:-/opt/recruitment}"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-recruitment}"
COMPOSE_FILE="${DEPLOY_PATH}/docker-compose.prod.yml"
DEPLOYED_IMAGE_FILE="${DEPLOY_PATH}/.deployed-image"
HEALTH_URL="http://127.0.0.1:8080/actuator/health"

fail() {
  printf 'Deployment error: %s\n' "$*" >&2
  exit 1
}

on_exit() {
  local status=$?
  docker logout ghcr.io >/dev/null 2>&1 || true
  [[ "$status" -ne 0 ]] || return 0
  trap - EXIT
  printf '\nDeployment failed (exit %s). Recent backend logs:\n' "$status" >&2
  docker compose --project-name "$COMPOSE_PROJECT_NAME" --env-file .env -f "$COMPOSE_FILE" logs --tail=100 backend >&2 || true

  if [[ -n "${PREVIOUS_TAG:-}" && -n "${IMAGE_TAG:-}" && "$PREVIOUS_TAG" != "$IMAGE_TAG" ]]; then
    printf '\nAttempting rollback to %s:%s\n' "$BACKEND_IMAGE" "$PREVIOUS_TAG" >&2
    IMAGE_TAG="$PREVIOUS_TAG" docker compose --project-name "$COMPOSE_PROJECT_NAME" --env-file .env -f "$COMPOSE_FILE" up -d --no-build backend >&2 || true
    if wait_for_health 12; then
      printf 'Rollback health check passed. Current deployment restored to %s:%s\n' "$BACKEND_IMAGE" "$PREVIOUS_TAG" >&2
    else
      printf 'Rollback could not be verified. Inspect backend logs and container state immediately.\n' >&2
    fi
  fi
  exit "$status"
}

wait_for_health() {
  local attempts="$1"
  local attempt
  for ((attempt = 1; attempt <= attempts; attempt++)); do
    if curl --silent --show-error --fail --max-time 4 "$HEALTH_URL" >/dev/null 2>&1; then
      return 0
    fi
    sleep 5
  done
  return 1
}

trap on_exit EXIT
cd "$DEPLOY_PATH"

[[ -f .env ]] || fail "missing ${DEPLOY_PATH}/.env; create it from .env.example and fill production values"
[[ -f "$COMPOSE_FILE" ]] || fail "missing $COMPOSE_FILE"
[[ -x "${DEPLOY_PATH}/docker/db-runtime-init.sh" ]] || chmod 755 "${DEPLOY_PATH}/docker/db-runtime-init.sh"
[[ -x "${DEPLOY_PATH}/docker/minio-init.sh" ]] || chmod 755 "${DEPLOY_PATH}/docker/minio-init.sh"
[[ -n "${BACKEND_IMAGE:-}" ]] || fail 'BACKEND_IMAGE is required'
[[ -n "${IMAGE_TAG:-}" ]] || fail 'IMAGE_TAG is required'
[[ "$IMAGE_TAG" =~ ^sha-[0-9a-f]{40}$ ]] || fail 'IMAGE_TAG must be an immutable sha-<40 character commit> tag'
[[ -n "${GHCR_USER:-}" ]] || fail 'GHCR_USER is required'
[[ "${1:-}" == "--token-stdin" ]] || fail 'expected --token-stdin; GHCR token must be passed through standard input'

GHCR_TOKEN=''
IFS= read -r -d '' GHCR_TOKEN || true
[[ -n "$GHCR_TOKEN" ]] || fail 'no GHCR token received on standard input'

export COMPOSE_PROJECT_NAME BACKEND_IMAGE IMAGE_TAG
compose=(docker compose --project-name "$COMPOSE_PROJECT_NAME" --env-file .env -f "$COMPOSE_FILE")
PREVIOUS_TAG=''
if [[ -f "$DEPLOYED_IMAGE_FILE" ]]; then
  IFS= read -r previous_image < "$DEPLOYED_IMAGE_FILE" || true
  if [[ "${previous_image:-}" == "$BACKEND_IMAGE:sha-"* ]]; then
    PREVIOUS_TAG="${previous_image##*:}"
  fi
fi

printf '%s' "$GHCR_TOKEN" | docker login ghcr.io --username "$GHCR_USER" --password-stdin >/dev/null
unset GHCR_TOKEN
"${compose[@]}" pull backend
# These two supporting images are built from the repository's pinned source
# configurations. Docker reuses cached layers on routine deployments.
"${compose[@]}" build minio minio-init
"${compose[@]}" up -d --no-build

if ! wait_for_health 60; then
  printf 'Health check did not pass at %s\n' "$HEALTH_URL" >&2
  false
fi

printf '%s:%s\n' "$BACKEND_IMAGE" "$IMAGE_TAG" > "$DEPLOYED_IMAGE_FILE.tmp"
chmod 600 "$DEPLOYED_IMAGE_FILE.tmp"
mv "$DEPLOYED_IMAGE_FILE.tmp" "$DEPLOYED_IMAGE_FILE"
docker logout ghcr.io >/dev/null 2>&1 || true
trap - EXIT
printf 'Deployment healthy: %s:%s\n' "$BACKEND_IMAGE" "$IMAGE_TAG"
