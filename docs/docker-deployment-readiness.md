# Docker and Deployment Readiness

## Audit and production image

The backend is a Java 21 Spring Boot module under `backend/`, built by the checked-in
Maven 3.9.16 wrapper into an executable JAR. Normal startup uses Hibernate `validate`,
SQL initialization is disabled, and Flyway is opt-in. The existing `production` profile
requires external `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`; retains Secure cookies;
validates HTTPS CORS origins; disables Flyway clean and baselining; ignores forwarded
headers; and writes structured JSON logs to stdout.

The existing anonymous, detail-free probes are `/actuator/health/liveness` and
`/actuator/health/readiness`. Readiness includes the cheap database indicator. Graceful
shutdown has a 30-second default phase timeout. Management shares port 8080. Runtime and
migration credential properties were already separate, so this workflow extends them.

Build from the repository root:

```powershell
docker build --pull --tag tayyar-backend:local backend
```

`backend/Dockerfile` uses the official `maven:3.9.16-eclipse-temurin-21` builder, matching
the checked-in wrapper's Maven release and Java 21 toolchain, and Eclipse Temurin
`21-jre-jammy` for execution. These deliberately avoid `latest`; rebuild with `--pull` for
base-image security updates. The builder copies the POM before source and uses a BuildKit
Maven cache mount. It never consumes `target/` or requires a developer Maven cache.
The audited wrapper's pinned Maven ZIP SHA-256 matches an archive independently verified
against Maven Central's published SHA-512. The official Maven builder is used because the
plain Temurin builder lacks `unzip`; the shell wrapper then downloads a tarball while still
checking the deliberately ZIP-specific checksum.

The final stage contains the JRE, executable JAR, and one compiled Java health-probe
class. It contains no Maven, compiler, source tree, tests, test resources, or Testcontainers
runtime dependency. `/opt/tayyar` is root-owned and read-only to UID/GID 10001. Java is the
exec-form PID 1 and receives SIGTERM directly. Java 21 container-awareness is used without
guessed heap flags. The image exposes 8080, while `SERVER_PORT` remains configurable. It
defaults to the existing `production` profile, which fails safely without mandatory DB
settings. No credential is an image argument, default, layer, or label.

`.dockerignore` excludes Git/IDE state, build output, tests, environment/key files, logs,
temporary content, and documentation. Explicit Dockerfile copies further limit content.

## Local Compose workflow

Create an ignored developer-local file, then replace the three empty password values with
different, non-production values:

```powershell
Copy-Item .env.example .env
notepad .env
```

The stack contains:

1. `postgres`: `postgres:18.6-bookworm` with a named volume and no host-published port.
2. `migrate`: waits for database health, applies V1-V15 as the migration role, validates
   the full application/schema through the runtime datasource on an unpublished random port,
   and exits.
3. `backend`: waits for database health and successful migration, receives only runtime
   credentials, and publishes its configurable port on host loopback.

Containers use Compose DNS (`postgres:5432`), never fixed internal IPs. Local Compose uses
the explicit `container-local` profile because local HTTP cannot support a browser Secure
cookie flow. It does not weaken the production profile. Application containers drop all
Linux capabilities, enable `no-new-privileges`, use a read-only root filesystem, and get
only a bounded `/tmp` tmpfs. PostgreSQL is the only persistent filesystem state.

```powershell
# Build (also happens automatically during up)
docker compose build --pull backend migrate

# Start database, execute migrations, then start backend
docker compose up -d

# Inspect status and the completed job
docker compose ps --all
docker compose logs migrate

# Probe application state
curl.exe --fail http://localhost:8080/actuator/health/liveness
curl.exe --fail http://localhost:8080/actuator/health/readiness

# View structured logs
docker compose logs --follow backend

# Normal stop: removes containers but preserves database data
docker compose down
```

`docker compose stop` retains containers. `docker compose restart backend` restarts the
stateless app while retaining data. The next command is intentionally destructive: it
permanently deletes the named local PostgreSQL volume and recreates roles/database/V1-V15
on the next start. Use it only when a fresh local schema is intended.

```powershell
docker compose down --volumes
```

Initialization scripts run only for an empty volume. Changing role names or passwords in
`.env` does not rewrite an existing volume; reset intentionally or administer it explicitly.

## Credentials, bootstrap, and migrations

First-run bootstrap creates a database owned by `DB_MIGRATION_USERNAME` and a separate
`DB_USERNAME` login. It revokes public database access and public schema creation. The
migration owner gets schema creation. The runtime role gets schema usage plus default DML
and sequence privileges on objects subsequently created by the migration owner. It owns
neither database, schema, Flyway history, nor tables and cannot create/drop schema objects.

Passwords are injected through local `.env`. The superuser password reaches only PostgreSQL.
Migration credentials reach only PostgreSQL initialization and the finite migration job.
The backend receives only runtime credentials. Production must inject secrets using the
future platform's secret manager/environment facility; `.env` is local-only.

Production must run migration mode as a one-off, release-gating job before new replicas
become ready. Never let every replica race to migrate. The job uses the migration identity,
keeps clean disabled and auto-baseline disabled, completes V1-V15, and succeeds before the
rollout. Replicas then run with only runtime credentials and Hibernate validation. A failed
migration blocks deployment progression.

## Health, termination, networking, and metrics

Liveness deliberately excludes the database to avoid dependency-loss restart loops.
Readiness includes it and returns 503 before dependencies are usable. Compose uses a
compiled two-second Java readiness probe, avoiding curl/wget installation. The exec-form
entrypoint has no shell wrapper. `docker stop` sends SIGTERM to Java; Spring stops accepting
work and drains up to `SHUTDOWN_TIMEOUT` (30 seconds default), while Compose grants 35
seconds. Production termination grace must exceed the application drain timeout.

Forwarded headers remain untrusted (`server.forward-headers-strategy=none`). Configure them
only after selecting and isolating a reviewed edge proxy; never base security on spoofable
forwarded IPs. Management remains on the app port. Public routing should expose only needed
API/probe paths. Metrics, Prometheus, aggregate health, and other management routes must be
network-restricted in addition to ADMIN authentication. A future internal scraper may use
`/actuator/prometheus` with an approved internal identity. No monitoring server is deployed.

## Provider-neutral deployment contract

A future target must provide:

- an OCI container runtime/registry with image vulnerability and update policy;
- PostgreSQL 18 compatibility, durable storage, tested backups, and restoration;
- secret injection for distinct database, migration, and runtime identities;
- HTTPS termination and an explicitly trusted reverse-proxy boundary;
- a release-gating one-off migration job and separately credentialed app replicas;
- liveness/readiness probes, SIGTERM, and sufficient termination grace;
- network restrictions for management routes and database reachability;
- structured stdout/stderr log collection with retention/access controls;
- internal authenticated Prometheus-compatible collection if approved later; and
- measured CPU/RAM, Hikari-pool, and server-thread settings after load testing.

No cloud, Kubernetes, CI/CD, monitoring server, or final load-test configuration is chosen.
Do not add speculative heap, `UseContainerSupport`, `MaxRAMFraction`, pool, or thread tuning.

## Verification commands

```powershell
# Runtime identity and Java version
docker compose run --rm --no-deps --entrypoint id backend
docker compose run --rm --no-deps --entrypoint java backend -version

# Size, layers, configured user/entrypoint, and environment names
docker image inspect tayyar-backend:local
docker history --no-trunc tayyar-backend:local

# Only the JAR and compiled probe should appear
docker compose run --rm --no-deps --entrypoint sh backend -c "find /opt/tayyar -maxdepth 3 -type f -print"

# Expected output: f (runtime role cannot CREATE in public schema)
docker compose exec postgres psql -U postgres -d tayyar -Atc `
  "SELECT has_schema_privilege('tayyar_app', 'public', 'CREATE');"

# Expected: V1 through V15, all successful
docker compose exec postgres psql -U postgres -d tayyar -c `
  'SELECT installed_rank, version, success FROM flyway_schema_history ORDER BY installed_rank;'
```

Inspect image history/configuration for variable names, not by printing a live environment
that contains secrets. Failure checks should use isolated Compose projects/volumes and confirm:
unavailable DB fails readiness; bad runtime credentials fail safely; missing production DB
settings and unsafe production cookie/origin settings fail fast; migration errors block backend
startup; and schema mismatch fails Hibernate validation.

## Module 19 verification result

The clean BuildKit build resolved dependencies inside the builder and produced a Java 21.0.12
runtime image of 155,525,490 bytes. Image configuration uses UID/GID 10001, exec-form Java,
and only port 8080. `/opt/tayyar` contained only the root-owned, group-readable application
JAR and compiled probe. The 658-entry JAR contained all 15 production migrations and no
test class, test resource, source path, foundation-test fixture, or Testcontainers match.
Image history/configuration contained none of the isolated test secrets or secret variable
names.

On a fresh isolated PostgreSQL 18.6 volume, initialization created distinct roles and Flyway
applied exactly V1-V15. The finite migration job exited 0; the backend then became healthy.
Liveness, readiness, and compatibility health returned 200. Database loss changed readiness
to 503 and restoration recovered it to 200. The runtime role connected and queried but both
database CREATE and public-schema CREATE checks were false; an actual `CREATE TABLE` failed
with permission denied. A reversible missing-table check made Hibernate startup fail schema
validation. Missing production DB configuration, invalid runtime/migration passwords, and an
HTTP production origin all exited nonzero. SIGTERM produced Spring's graceful-start and
graceful-complete messages, and the named volume survived backend restart.

The final Maven `verify` run passed 58 unit/MVC tests and 201 PostgreSQL 18 integration tests
(259 total) with zero failures, errors, or skips. The disposable Compose containers, network,
and named volume were removed afterward; no isolated verification container or volume remains.
