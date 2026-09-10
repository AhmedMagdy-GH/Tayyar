# Foundation checkpoint

Status: Foundation is complete; `mvnw -B -ntp verify` passed on 2026-09-10.
All 12 tests passed (7 controller tests and 5 PostgreSQL integration tests).
No existing Tayyar database has
been migrated, baselined, or modified. No Git/GitHub commands were used.

## Architecture and schema

Preserve the health feature and constructor injection in production code.
`common/api` contains the error DTO, MVC exception handling, servlet error fallback,
and request correlation. It has no business logic or persistence abstractions.

Spring Boot manages the versions of JPA/Hibernate, Flyway, Bean Validation,
Testcontainers and the PostgreSQL driver. Flyway owns schema changes and Hibernate
validates mappings. Open Session in View and SQL script auto-initialization are off.
The default runtime does not migrate. An explicit `migrate` profile uses separate
credentials; clean and automatic baseline are disabled in all supplied profiles.

No production migration was needed for Foundation. The first production migration
remains `V1__create_users_and_roles.sql` in Identity. The test-only
`db/foundation-test/V1__create_foundation_probe.sql` creates a UUID-keyed table and
revokes runtime access to Flyway history. Its entity and SQL are under `src/test`
and must never be packaged in the runnable application.

Disposable container credentials are randomly generated. Container initialization
uses an isolated bootstrap/migration account; application connections use
`tayyar_app` with no superuser, role creation, database creation or schema creation
privileges. The temporary test schema grants DML for application tables only.
Production grant provisioning remains subject to inspection of the real database.

## API conventions

Errors contain `code`, `message`, UTC `timestamp`, `path`, `correlationId`, and a
`fieldErrors` array. Each field error contains `field` and `message`; never rejected
values. Request-body validation uses `@Valid` on immutable DTOs with Jakarta Bean
Validation constraints. Constraint messages must not interpolate submitted secrets.

Malformed input is 400, unsupported methods 405 (preserving `Allow`), unsupported
media types 415, and missing routes 404. Unexpected failures use a generic 500.
Exceptions from MVC and servlet error dispatch use the same response shape.
Authentication/authorization error handlers and business-specific errors will be
added with the modules that introduce those failure modes; they are not implemented yet.

The server generates `X-Correlation-ID` instead of trusting arbitrary client input.
The ID is attached to the response, request and logging MDC; MDC state is restored
after each request. Unexpected errors log the ID and exception type without exception
messages, request bodies or SQL. Richer sanitized diagnostics are a later observability
task; full stack/cause logging is deliberately absent from this baseline.

Future collection endpoints must use bounded pagination (proposed default 20,
maximum 100), deterministic ordering, and allowlisted sorts. Business validation
belongs in services and durable invariants also belong in database constraints.
Do not introduce unused paging repositories or dummy business entities now.

## Verification

Recorded on 2026-09-10, Java 21.0.11 / Maven 3.9.16 / Spring Boot 4.1.1:

| Check | Result |
| --- | --- |
| Final `mvnw -B -ntp verify`: compilation/package | Passed; runnable JAR created |
| Final controller regression within `verify` | 7 tests, 0 failures, 0 errors, 0 skipped |
| PostgreSQL 18 integration tests | 5 tests, 0 failures, 0 errors, 0 skipped; PostgreSQL 18.6, Testcontainers 2.0.5 |
| Full `verify` result | BUILD SUCCESS; 12 tests passed; 20.202 seconds |
| Packaged artifact isolation | No test fixtures or probe endpoints in production JAR |
| Existing local database smoke/inspection | Not run: DB_PASSWORD was unavailable |

After the user restored Docker Desktop, Testcontainers connected successfully and
downloaded PostgreSQL 18. The first database run exposed an assertion issue: Spring's
JDBC exception message did not include the underlying PostgreSQL permission error.
The test now verifies SQLSTATE `42501` (insufficient privilege) on the root SQL
exception for both schema creation and Flyway-history modification. No privileges
were relaxed. The full regression then passed against a fresh isolated container.
No unrelated system files, Docker settings, or existing database data were changed.

Run from `backend/`:

- `./mvnw test` (Windows: `.\mvnw.cmd test`): health/error controller regression.
- `./mvnw verify`: additionally builds the artifact and runs PostgreSQL 18 integration tests.
- `./mvnw -Plocal-db-smoke test`: explicit local `DB_*` connectivity check only.

Integration tests cover application context/JPA validation, Hikari and PostgreSQL
18 connectivity, Flyway validation and second-run behavior, disabled clean,
refusal to baseline an unknown nonempty schema, restricted runtime privileges,
and transactional rollback. Failure to start Docker is an error, not a skipped success.

Manual checks after a successful integration run:

1. Inspect the real database with `database-inspection.sql` before applying migrations.
2. Supply local `DB_PASSWORD` securely and run the explicit local smoke test.
3. Start the backend; verify `/api/v1/health` still returns its original JSON.
4. Request an unknown API route; expect 404, a generic error DTO and `X-Correlation-ID`.
5. Confirm logs/responses do not contain connection passwords or submitted secrets.

## Security and performance review

No new business endpoint is public. Test-only probe endpoints are absent from main
sources. Security/session implementation remains Identity work; Foundation is not
a production authentication system. Database credentials are environment-supplied,
and `.env.example` contains no password. Migration controls avoid implicit changes
to an unknown database. Runtime grants are tested with a distinct database role.

No new application queries, indexes, caches, outbox, thread pools or pool-size
overrides have been added. There is no N+1 query path in the health/error features.
Connection-pool tuning and capacity claims require measurements in later modules.

## Wrapper

The Windows wrapper previously indexed a null filesystem link target. It now checks
that a target exists before indexing, preserving ordinary-directory and linked-cache
behavior without hard-coded paths. Maven 3.9.16's archive was checked against Maven
Central's SHA-512; the resulting SHA-256 is pinned for future wrapper downloads.
The shell wrapper remains unchanged. Windows ordinary-directory execution was tested;
fresh-download, linked-cache, and other-OS execution have not all been exercised.

## Files created or modified

Paths below are relative to the repository root.

- `README.md`
- `.env.example`
- `backend/pom.xml`
- `backend/mvnw.cmd`
- `backend/.mvn/wrapper/maven-wrapper.properties`
- `backend/src/main/resources/application.properties`
- `backend/src/main/resources/application-migrate.properties`
- `backend/src/main/resources/db/migration/README.txt`
- `backend/src/main/java/com/tayyar/common/api/ApiError.java`
- `backend/src/main/java/com/tayyar/common/api/ApiExceptionHandler.java`
- `backend/src/main/java/com/tayyar/common/api/ApiErrorController.java`
- `backend/src/main/java/com/tayyar/common/api/RequestCorrelationFilter.java`
- `backend/src/test/java/com/tayyar/BackendApplicationTests.java` replaced by `BackendApplicationIT.java`
- `backend/src/test/java/com/tayyar/DatabaseConnectivityTests.java`
- `backend/src/test/java/com/tayyar/common/api/ApiExceptionHandlerTests.java`
- `backend/src/test/java/com/tayyar/support/PostgresIntegrationTest.java`
- `backend/src/test/java/com/tayyar/support/fixture/MigrationProbe.java`
- `backend/src/test/resources/db/foundation-test/V1__create_foundation_probe.sql`
- `docs/database-inspection.sql`
- `docs/foundation.md`

Generated Maven outputs are excluded from this manifest.

## Remaining operational prerequisites and risks

- Future PostgreSQL integration runs require a running Docker engine; the current gate passed.
- Local PostgreSQL inspection/smoke test requires securely supplied credentials.
- Mockito currently warns about dynamic agent attachment on Java 21; tests still run.
- Runtime Flyway is disabled by design: deployment must run migrations before starting
  an application version that requires new tables. Production orchestration is deferred.
- PostgreSQL's `18` image tag tracks patch updates; deployment reproducibility/pinning
  will be finalized with the deployment module.

Suggested commit for the completed Foundation module:
`feat(backend): establish persistence and API foundations`

## Reference guidance

- [Spring Boot database initialization](https://docs.spring.io/spring-boot/how-to/data-initialization.html)
- [Testcontainers PostgreSQL](https://java.testcontainers.org/modules/databases/postgres/)
