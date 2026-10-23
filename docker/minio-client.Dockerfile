# Build the MinIO Client from the verified upstream release source. Keep this
# separate from docker/minio.Dockerfile: the latter builds the MinIO server.
FROM golang:1.24.9-bookworm AS build
ARG MC_VERSION=d6541ea280b73a834b64d4097e21f2be77676104
ENV CGO_ENABLED=0 GOBIN=/out
RUN go install github.com/minio/mc@${MC_VERSION}

FROM debian:bookworm-slim
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /out/mc /usr/local/bin/mc
ENTRYPOINT ["mc"]
