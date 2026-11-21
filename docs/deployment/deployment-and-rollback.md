# Deployment and rollback

## Normal deployment

For a relevant push to `main`, a published GitHub release, or a manual run on `main`, GitHub Actions checks out the repository, runs Java 21 Maven unit tests, packages the application, and builds the root-context Dockerfile. It publishes the image to GHCR with the full `sha-<40 character commit>` tag. `latest` is published for production branch/release builds, and releases also publish the release tag. The workflow deploys only the immutable SHA tag after the publish job succeeds.

The deploy job verifies SSH against the pinned `LIGHTSAIL_KNOWN_HOSTS`, copies the production Compose and supporting Docker files, logs in to GHCR with the read-only deployment token, and executes `/opt/recruitment/deploy-production.sh`. The script pulls the target image, builds the repository's MinIO server/client support images as needed, runs Compose, and polls `/actuator/health` on loopback. The currently running service may have a short restart interruption while Spring Boot and Flyway start; health is verified before success is reported. No live Lightsail deployment was performed as part of repository implementation.

## Identify the deployed image

The deploy script records the last healthy image in `/opt/recruitment/.deployed-image`. Check the Compose-resolved image and running container:

```sh
cd /opt/recruitment
cat .deployed-image
docker compose --project-name recruitment --env-file .env -f docker-compose.prod.yml images backend
docker inspect --format '{{.Config.Image}}' recruitment-backend-1
```

The Compose generated container name can differ if a custom project name is in use; `docker compose ps` shows the correct name. Always use a full SHA tag when selecting an image for deployment.

## Roll back

On a failed deployment, the script reports the error, prints recent backend logs, and tries to start the previously recorded healthy SHA image. It updates `.deployed-image` only after health succeeds, so the prior tag remains available as rollback metadata. Check workflow logs and verify server health after an automatic rollback.

For a manual rollback, first read the previous SHA from a trusted workflow/build record or image tag. Then, as `ubuntu`, run from the deployment directory (using an image name consistent with the configured GHCR package):

```sh
cd /opt/recruitment
BACKEND_IMAGE=ghcr.io/OWNER/online-job-recruitment-platform-backend \
IMAGE_TAG=sha-FULL_40_CHARACTER_COMMIT_SHA \
COMPOSE_PROJECT_NAME=recruitment \
docker compose --project-name recruitment --env-file .env -f docker-compose.prod.yml pull backend
BACKEND_IMAGE=ghcr.io/OWNER/online-job-recruitment-platform-backend \
IMAGE_TAG=sha-FULL_40_CHARACTER_COMMIT_SHA \
COMPOSE_PROJECT_NAME=recruitment \
docker compose --project-name recruitment --env-file .env -f docker-compose.prod.yml up -d --no-build backend
curl --fail --retry 12 --retry-delay 5 http://127.0.0.1:8080/actuator/health
```

Replace `OWNER` and the SHA with actual values. For a private package, run `docker login ghcr.io` first using a `read:packages` token via `--password-stdin`, and `docker logout ghcr.io` after. Update `.deployed-image` to the restored image only after health is verified. Rollback restores application code, but database migrations are forward-only; review migration compatibility before rolling back an image if the new release migrated the database.

## Troubleshooting

```sh
cd /opt/recruitment
docker compose --project-name recruitment --env-file .env -f docker-compose.prod.yml ps -a
docker compose --project-name recruitment --env-file .env -f docker-compose.prod.yml logs --tail=200 backend db minio
curl -i --max-time 5 http://127.0.0.1:8080/actuator/health
docker inspect --format '{{json .State}}' "$(docker compose --project-name recruitment --env-file .env -f docker-compose.prod.yml ps -q backend)"
```

Common causes include incorrect GHCR package access/token, untrusted SSH host key, missing production `.env` values, database credentials inconsistent with the initialized PostgreSQL volume, insufficient disk space for source image builds, MinIO endpoint configuration, Flyway failure, or Nginx pointing at the wrong upstream. The health endpoint uses the application's aggregate health status and returns a non-success response when unavailable. Never troubleshoot by deleting named volumes, Flyway history, or the production `.env`.
