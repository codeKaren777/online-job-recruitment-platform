# Production deployment architecture

The production workflow builds this repository's root Docker context. Its existing `Dockerfile` compiles and packages the Spring Boot application with Java 21 and runs it on a Java 21 runtime image. GitHub Actions runs the Maven unit tests before building and publishing the container to GHCR.

```text
API client --HTTPS--> host Nginx --HTTP loopback--> backend :8080
                                                   |         |
                                                   |         +-- health: /actuator/health
                                                   +-- private Compose network --> PostgreSQL
                                                                            `--> MinIO

GitHub Actions --GITHUB_TOKEN--> GHCR --anonymous pull--> Lightsail Docker
      |                                                    Docker Compose
      +-- pinned SSH connection ---------------------------> /opt/recruitment
```

GitHub Actions publishes a traceable `sha-<full commit SHA>` image and `latest` for production branch/release builds. A release also gets its release tag. Only the immutable SHA tag is deployed. A published image is then pulled by the Lightsail host and Compose recreates the backend. Production deployment is serialized by workflow concurrency.

The GHCR package is public. The workflow's `GITHUB_TOKEN` publishes the image with `packages: write`; Lightsail pulls it anonymously without registry credentials or a server-side token. The Dockerfile's OCI source label associates the image with this GitHub repository.

The backend, database, MinIO, and one-shot initialization services share Compose's private default network. PostgreSQL has no host port. The backend binds only to `127.0.0.1:8080`, which is the upstream expected by host Nginx. MinIO API and console bind only to loopback (`127.0.0.1:9000` and `:9001`) so the host can proxy or administer them without public container ports. UFW and the Lightsail firewall should continue to allow only the intended public web and SSH ports.

`postgres_data` and `minio_data` are named volumes using the `recruitment` Compose project by default. The deployment never runs `down`, `down -v`, or volume pruning. Compose also keeps the existing Flyway bootstrap and restricted PostgreSQL runtime account initialization. MinIO initialization creates the private document bucket and restricted application user. MinIO and its data remain separate from backend image updates.

The backend exposes only Actuator health. Liveness is `GET /actuator/health/liveness` and checks that the process can serve requests; readiness is `GET /actuator/health/readiness` and includes PostgreSQL connectivity. A database outage makes readiness return 503 while liveness stays UP, allowing Docker/orchestrators to stop routing requests without restart loops. Health details and components are hidden. Probes share the application listener on port 8080, which Compose publishes only on host loopback; host Nginx can use `http://127.0.0.1:8080/actuator/health/readiness` for a local upstream check. Do not configure a public Nginx location for Actuator paths.

Application logs go to stdout/stderr for `docker compose logs`. Each request returns a server-generated `X-Correlation-ID`; matching structured key/value request log entries include method, path, response status, duration, and that identifier. Do not configure Nginx access logs with request/response bodies, Authorization headers, query strings, or reset endpoints' payloads. Preserve the correlation response header in Nginx. 4xx request entries are warnings and 5xx entries are errors. For a 503 readiness result, inspect backend logs and PostgreSQL health first; if liveness fails, inspect startup/backend logs and container restart state. For elevated latency, correlate request IDs with Nginx upstream timing and database logs. Diagnostic and metrics endpoints remain unexposed; scrape metrics only after defining a private authenticated access path.

The deployment script stores the last image tag only after the new backend answers HTTP 200 at `http://127.0.0.1:8080/actuator/health/readiness`. If readiness fails, it prints backend logs and attempts to restore the previous recorded tag. It does not remove old images or change production `.env`.

Host Nginx configuration is not managed in this repository. Ensure its API upstream targets `http://127.0.0.1:8080` and it sets/overwrites `X-Forwarded-Host` and `X-Forwarded-Proto`; Spring's forwarded-header support uses these to generate the public HTTPS OpenAPI server URL. For private documents, production Compose uses `http://minio:9000` for backend-to-MinIO operations and `PROFILE_STORAGE_PUBLIC_ENDPOINT=https://files.mikhy.me` for signing client URLs. Nginx must proxy `files.mikhy.me` to `http://127.0.0.1:9000` without changing the HTTP method, request path/query, or signed Host header (`files.mikhy.me`). A signed URL's signature includes its host, so changing the upstream Host invalidates it. The server configuration was not available to verify that this route is installed. Do not expose MinIO console or database ports publicly.
