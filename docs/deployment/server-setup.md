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

Set the actual API/database/MinIO values supported by this repository. `PROFILE_STORAGE_PUBLIC_ENDPOINT` defaults to `https://files.mikhy.me` and must be a real HTTPS endpoint reachable by clients and routed by host Nginx to `127.0.0.1:9000`. MinIO's API and console ports bind only to localhost in Compose. The console should be reached through an SSH tunnel or a deliberate secured Nginx configuration, not opened in UFW/Lightsail firewall. `APP_OPENAPI_ENABLED` and `APP_OPENAPI_PUBLIC` both default to `false`; set both to `true` only when intentionally exposing Swagger for frontend integration. `SPRING_PROFILES_ACTIVE` defaults to `production`, avoiding the development `local` profile.

The app connects to PostgreSQL and MinIO by Compose service DNS names. Flyway uses `POSTGRES_USER`; the app uses the restricted `POSTGRES_RUNTIME_USER`. Do not substitute a superuser for the runtime role. Preserve the current `SPRING_FLYWAY_BASELINE_ON_MIGRATE=false` unless a reviewed database recovery procedure requires otherwise.

## Host Nginx and firewall

Keep the installed host Nginx. Its API upstream should point to `http://127.0.0.1:8080`. Existing Nginx configuration was not available for inspection, so review it before reloading and retain its TLS, headers, and routing. The backend uses forwarded headers so OpenAPI reflects the public HTTPS host; Nginx must set/overwrite `X-Forwarded-Host` and `X-Forwarded-Proto`, not pass through untrusted client values. If document links are enabled, configure a separate suitable HTTPS MinIO API route and use its externally reachable origin for `PROFILE_STORAGE_PUBLIC_ENDPOINT`; signed URLs require the route to preserve the request path and query. Do not proxy the MinIO console publicly.

### MinIO file domain routing

Production Compose keeps backend storage calls on the internal Docker endpoint
`http://minio:9000`. Set `PROFILE_STORAGE_PUBLIC_ENDPOINT` to
`https://files.mikhy.me` (the Compose default) for client URLs. Configure the
host's TLS server for that domain to proxy only the MinIO API on loopback. The
existing server configuration is outside this repository and was not inspected
or changed. The relevant Nginx location should preserve the request method and
full URI, including the presigned query string, and forward the signed host:

```nginx
server {
    listen 443 ssl;
    server_name files.mikhy.me;

    # Keep the existing certificate and TLS directives here.
    location / {
        proxy_pass http://127.0.0.1:9000;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header Connection "";
        proxy_request_buffering off;
        proxy_buffering off;
    }
}
```

Do not add a URI suffix to `proxy_pass`, rewrite the request URI, or change the
upstream Host to `127.0.0.1:9000`: the SigV4 signature covers the Host, path,
query and HTTP method. Keep the MinIO bucket private and do not proxy its
console. Install this in the existing server configuration after checking its
TLS setup; this repository cannot verify the live Nginx route.

Do not open ports 5432, 9000, or 9001 in UFW or the Lightsail firewall. Compose exposes MinIO only on host loopback and does not publish PostgreSQL. The backend is published only on host loopback for Nginx.

## Abuse controls and proxy trust

The backend uses PostgreSQL-backed fixed-window counters, so limits remain effective across application restarts and multiple instances. The default policies are 10 login attempts per 15 minutes, 5 registrations per hour, 5 recovery requests per hour, 10 password resets per hour, and 20 uploads per hour. Login and recovery limits combine client-address and account identity; upload limits combine JWT principal and client address. Exceeding a limit returns `429` with `Retry-After` and the standardized `ApiErrorResponse` contract using code `RATE_LIMIT_EXCEEDED`.

Forwarded addresses are accepted only when the direct peer is in `APP_RATE_LIMIT_TRUSTED_PROXIES`. Nginx is on host loopback in the documented topology, so `127.0.0.1,::1` is the default. Confirm the actual proxy topology before changing this value. Add only verified proxy IP addresses or CIDRs; never set the trusted list to `0.0.0.0/0`. Configure Nginx to replace or remove client-supplied forwarding headers and set the correct leftmost original address. Tuning variables are listed in `docs/deployment/production.env.example`. Set `APP_RATE_LIMIT_ENABLED=false` only for a reviewed maintenance or test environment.

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
curl --fail http://127.0.0.1:8080/actuator/health/readiness
```

The first run builds the existing source-defined MinIO server and client images, initializes the restricted MinIO bucket user and DB runtime user, starts PostgreSQL/MinIO, and starts the backend. The Compose volumes persist across later deployments. Startup may take several minutes while the MinIO source image layers are built.

To inspect/validate Compose without starting it, run `docker compose ... config --quiet`. Do not pass `--remove-orphans` or use `down -v` during deployment.
