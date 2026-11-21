# Production deployment architecture

The production workflow builds this repository's root Docker context. Its existing `Dockerfile` compiles and packages the Spring Boot application with Java 21 and runs it on a Java 21 runtime image. GitHub Actions runs the Maven unit tests before building and publishing the container to GHCR.

```text
API client --HTTPS--> host Nginx --HTTP loopback--> backend :8080
                                                   |         |
                                                   |         +-- health: /actuator/health
                                                   +-- private Compose network --> PostgreSQL
                                                                            `--> MinIO

GitHub Actions --GITHUB_TOKEN--> GHCR --deploy token/read:packages--> Lightsail Docker
      |                                                               Docker Compose
      +-- pinned SSH connection -------------------------------------> /opt/recruitment
```

GitHub Actions publishes a traceable `sha-<full commit SHA>` image and `latest` for production branch/release builds. A release also gets its release tag. Only the immutable SHA tag is deployed. A published image is then pulled by the Lightsail host and Compose recreates the backend. Production deployment is serialized by workflow concurrency.

The GHCR package is private-capable. The workflow's `GITHUB_TOKEN` publishes the image with `packages: write`; the host uses a separate read-only token. The Dockerfile's OCI source label associates the image with this GitHub repository.

The backend, database, MinIO, and one-shot initialization services share Compose's private default network. PostgreSQL has no host port. The backend binds only to `127.0.0.1:8080`, which is the upstream expected by host Nginx. MinIO API and console bind only to loopback (`127.0.0.1:9000` and `:9001`) so the host can proxy or administer them without public container ports. UFW and the Lightsail firewall should continue to allow only the intended public web and SSH ports.

`postgres_data` and `minio_data` are named volumes using the `recruitment` Compose project by default. The deployment never runs `down`, `down -v`, or volume pruning. Compose also keeps the existing Flyway bootstrap and restricted PostgreSQL runtime account initialization. MinIO initialization creates the private document bucket and restricted application user. MinIO and its data remain separate from backend image updates.

The deployment script stores the last image tag only after the new backend answers HTTP 200 at `http://127.0.0.1:8080/actuator/health`. If health fails, it prints backend logs and attempts to restore the previous recorded tag. It does not remove old images or change production `.env`.

Host Nginx configuration and domain names are intentionally not included because the server's existing site configuration and hostname were not available during implementation. Ensure its API upstream targets `http://127.0.0.1:8080`. When private document storage is enabled, configure an HTTPS client-reachable endpoint that Nginx proxies to the loopback MinIO API, and set `PROFILE_STORAGE_PUBLIC_ENDPOINT` accordingly. Do not expose MinIO console or database ports publicly.
