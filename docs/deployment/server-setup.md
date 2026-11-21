# Lightsail server setup

The workflow deploys as the existing `ubuntu` account to `/opt/recruitment`. It does not create Linux accounts. Docker Engine, Compose v2, Nginx, UFW, and Fail2ban are assumed to be installed as described in the deployment request. The host also needs `curl` for the deployment health probe. Confirm that `ubuntu` can run Docker without `sudo` before enabling Actions deployment.

## Directory and files

The server directory will contain:

```text
/opt/recruitment/
├── .env                         # manually provisioned; never replaced by Actions
├── docker-compose.prod.yml      # copied by Actions
├── deploy-production.sh         # copied by Actions
├── docker/
│   ├── db-runtime-init.sh
│   ├── minio-init.sh
│   ├── minio.Dockerfile
│   └── minio-client.Dockerfile
└── .deployed-image              # recorded by script after a healthy deployment
```

The `ubuntu` account needs ownership/write access to the directory and Docker socket access. As a one-time administrator operation, if the directory is new:

```sh
sudo install -d -o ubuntu -g ubuntu -m 0750 /opt/recruitment
```

From the repository root on a trusted workstation, transfer the placeholder template for reference (it contains no usable production secrets):

```sh
scp docs/deployment/production.env.example ubuntu@<LIGHTSAIL_HOST>:/opt/recruitment/production.env.example
```

Replace `<LIGHTSAIL_HOST>` with the actual configured host. Then follow the edit steps below to create `.env`; the deployment workflow never copies or overwrites either `.env` or the template.

Do not run `chown` over an existing deployment directory without checking its contents. Keep `.env` owner-readable only (`chmod 600 /opt/recruitment/.env`). Confirm existing volume names before first start:

```sh
docker volume ls
docker compose --project-name recruitment --env-file /opt/recruitment/.env \
  -f /opt/recruitment/docker-compose.prod.yml config --volumes
```

The production Compose file names the data volumes `recruitment_postgres_data` and `recruitment_minio_data`. If the current production stack already uses other volume names, stop and reconcile the names before starting; do not let a new empty database be mistaken for the existing one. Never use `docker compose down -v`.

## Production environment

Create `/opt/recruitment/.env` manually based on [production.env.example](production.env.example), not the repository's local development `.env.example`. Do not copy placeholder values into production and do not overwrite an existing `.env`. Generate separate strong random passwords for PostgreSQL bootstrap/runtime and MinIO. Generate `JWT_SECRET_BASE64` from 32 random bytes and base64 encode it. Do not paste these values into GitHub Actions or commit the file.

On the server, create the file only if it does not already exist, then edit it as `ubuntu`:

```sh
cd /opt/recruitment
test -e .env || install -m 600 /dev/null .env
nano .env
chmod 600 .env
```

Transfer/copy the contents of `production.env.example` through a trusted method and replace every placeholder before saving. Never run a command that overwrites `.env` on an existing server.

Set the actual API/database/MinIO values supported by this repository. In particular, `PROFILE_STORAGE_PUBLIC_ENDPOINT` must be a real HTTPS endpoint reachable by clients and routed by host Nginx to `127.0.0.1:9000`; no domain was available to put in the sample. MinIO's API and console ports bind only to localhost in Compose. The console should be reached through an SSH tunnel or a deliberate secured Nginx configuration, not opened in UFW/Lightsail firewall. `APP_OPENAPI_ENABLED` defaults false and `SPRING_PROFILES_ACTIVE` defaults to `production`, avoiding the development `local` profile.

The app connects to PostgreSQL and MinIO by Compose service DNS names. Flyway uses `POSTGRES_USER`; the app uses the restricted `POSTGRES_RUNTIME_USER`. Do not substitute a superuser for the runtime role. Preserve the current `SPRING_FLYWAY_BASELINE_ON_MIGRATE=false` unless a reviewed database recovery procedure requires otherwise.

## Host Nginx and firewall

Keep the installed host Nginx. Its API upstream should point to `http://127.0.0.1:8080`. Existing Nginx configuration was not available for inspection, so review it before reloading and retain its TLS, headers, and routing. If document links are enabled, configure a separate suitable HTTPS MinIO API route and use its externally reachable origin for `PROFILE_STORAGE_PUBLIC_ENDPOINT`; signed URLs require the route to preserve the request path and query. Do not proxy the MinIO console publicly.

Do not open ports 5432, 9000, or 9001 in UFW or the Lightsail firewall. Compose exposes MinIO only on host loopback and does not publish PostgreSQL. The backend is published only on host loopback for Nginx.

## Initial deployment

1. Verify SSH login as `ubuntu`, Docker/Compose access, Nginx upstream, firewall, and existing database/MinIO volume names.
2. Create `/opt/recruitment/.env` securely, filling all production values without changing any existing production file.
3. Add the GitHub Actions secrets/variables described in [GitHub Actions setup](github-actions-setup.md). Pin the verified server host key.
4. On the repository's `main` branch, run **Actions → Build and deploy production backend → Run workflow**.
5. Check the workflow's deployment step, then on the server run:

```sh
cd /opt/recruitment
docker compose --project-name recruitment --env-file .env -f docker-compose.prod.yml ps
docker compose --project-name recruitment --env-file .env -f docker-compose.prod.yml logs --tail=100 backend
curl --fail http://127.0.0.1:8080/actuator/health
```

The first run builds the existing source-defined MinIO server and client images, initializes the restricted MinIO bucket user and DB runtime user, starts PostgreSQL/MinIO, and starts the backend. The Compose volumes persist across later deployments. Startup may take several minutes while the MinIO source image layers are built.

To inspect/validate Compose without starting it, run `docker compose ... config --quiet`. Do not pass `--remove-orphans` or use `down -v` during deployment.
