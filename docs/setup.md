# Development Setup

This guide sets up the Online Job Recruitment Platform backend locally. Run all commands from the repository root.

The project uses Java 21, Spring Boot, the Maven Wrapper, PostgreSQL 17, Docker Compose, Flyway, and JWT configuration.

## 1. Install prerequisites

| Tool | Linux | Windows |
| --- | --- | --- |
| Git | Git package from your distribution | [Git for Windows](https://git-scm.com/download/win) |
| Java | JDK 21 | JDK 21 |
| Docker | Docker Engine and Compose plugin | Docker Desktop with Linux containers |

Check the installations:

```text
java -version
docker version
docker compose version
```

The Maven Wrapper downloads the required Maven version automatically, so a global Maven installation is not needed.

## 2. Get the project

```bash
git clone <repository-url>
cd online-job-recruitment-platform
```

On Windows PowerShell, use `Set-Location` if preferred:

```powershell
Set-Location 'C:\path\to\online-job-recruitment-platform'
```

## 3. Create local configuration

Copy `.env.example` to `.env` and replace both placeholders. Never commit `.env`.

### Linux

```bash
cp .env.example .env
sed -i "s|REPLACE_WITH_RANDOM_DATABASE_PASSWORD|$(openssl rand -base64 32)|" .env
sed -i "s|REPLACE_WITH_BASE64_ENCODED_32_RANDOM_BYTES|$(openssl rand -base64 32)|" .env
```

If OpenSSL is not installed, install the `openssl` package using your distribution's package manager and run the commands again.

### Windows PowerShell

```powershell
if (-not (Test-Path -LiteralPath '.env')) { Copy-Item '.env.example' '.env' }
$config = Get-Content -Raw -LiteralPath '.env'
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $bytes = New-Object byte[] 32
    if ($config.Contains('REPLACE_WITH_RANDOM_DATABASE_PASSWORD')) {
        $rng.GetBytes($bytes)
        $config = $config.Replace('REPLACE_WITH_RANDOM_DATABASE_PASSWORD', [Convert]::ToBase64String($bytes))
    }
    if ($config.Contains('REPLACE_WITH_BASE64_ENCODED_32_RANDOM_BYTES')) {
        $rng.GetBytes($bytes)
        $config = $config.Replace('REPLACE_WITH_BASE64_ENCODED_32_RANDOM_BYTES', [Convert]::ToBase64String($bytes))
    }
    [IO.File]::WriteAllText((Join-Path (Get-Location) '.env'), $config, (New-Object Text.UTF8Encoding($false)))
} finally { $rng.Dispose() }
```

The file must contain values for `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `DB_URL`, and `JWT_SECRET_BASE64`.

## 4. Start the application with Docker

Docker must be running. Compose starts PostgreSQL and the backend together.

### Linux

```bash
docker compose config --quiet
docker compose up --build -d
docker compose ps
curl http://localhost:8080/actuator/health
```

### Windows PowerShell

```powershell
docker compose config --quiet
docker compose up --build -d
docker compose ps
Invoke-RestMethod 'http://localhost:8080/actuator/health'
```

The health response should report `UP`. The backend is available at `http://localhost:8080`; PostgreSQL is available locally on port `5432`.

View backend logs with `docker compose logs --tail 100 backend`.

Stop the services without deleting database data:

```text
docker compose down
```

Do not use `docker compose down -v` unless you intend to delete the PostgreSQL volume.

## 5. Run tests and build

Unit tests do not require `.env` or a running database.

### Linux

```bash
sh ./mvnw -B -ntp test
sh ./mvnw -B -ntp package
```

### Windows PowerShell

```powershell
.\mvnw.cmd -B -ntp test
.\mvnw.cmd -B -ntp package
```

For the full verification suite, leave Docker running. It starts an isolated PostgreSQL container with Testcontainers:

```bash
sh ./mvnw -B -ntp verify
```

```powershell
.\mvnw.cmd -B -ntp verify
```

The packaged application is created at `target/recruitment.jar`.

## 6. Run only the backend locally

Use this option when PostgreSQL is already running on `localhost:5432`, or after starting only the Compose database with `docker compose up -d db`.

### Linux

```bash
set -a
. ./.env
set +a
docker compose up -d db
sh ./mvnw -B -ntp spring-boot:run
```

### Windows PowerShell

```powershell
. .\scripts\Import-LocalEnv.ps1
docker compose up -d db
.\mvnw.cmd -B -ntp spring-boot:run
```

Stop the backend with `Ctrl+C`. The local environment import must be repeated in each new terminal window.

## Troubleshooting

- **Docker cannot connect:** Start Docker Desktop on Windows or the Docker service on Linux.
- **Port 5432 is in use:** Stop the other PostgreSQL service, or change the host-side Compose port and update `DB_URL` for local runs.
- **Port 8080 is in use:** Stop the other application using it.
- **Database authentication fails after changing `.env`:** Existing volumes retain their original credentials. Use the old credentials, or intentionally remove the volume with `docker compose down -v`.
- **The health endpoint is unavailable:** Check `docker compose logs --tail 100 backend` and `docker compose ps`.

## Useful links

- [Architecture](architecture.md)
- [Main README](../README.md)