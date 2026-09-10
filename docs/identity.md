# Identity design and checkpoint

Status: Identity is complete. Full regression passed on 2026-09-10 at 16:07:28
Africa/Cairo. Restaurants remain out of scope and have not been started.

## Design before implementation

V1 creates users (UUID, normalized unique email, full name, optional E.164 contact
phone, adaptive password hash, ACTIVE/SUSPENDED/DISABLED status, email_verified_at,
authentication version, event timestamps), roles, and the user-role join table.
Public registration accepts identity fields only and assigns CUSTOMER internally.
No administrative provisioning endpoint is exposed. Email verification and recovery
delivery are deferred; registration starts ACTIVE with an unverified email.

V2 creates Spring Session JDBC tables/indexes and bounded-window authentication
rate counters. Session schema auto-initialization is disabled. Triggers increment
authentication version when password/status/email or roles change, invalidating
previous principals on their next request, including changes made outside JPA.

Flow: GET /api/v1/auth/csrf obtains a session and masked CSRF token. Submit this
token in X-CSRF-TOKEN for POST /auth/registrations and POST /auth/session. Login
rotates the session ID, clears the old CSRF token, and explicitly persists a
password-free principal. Fetch a fresh token after login. GET /users/me returns
the current identity DTO; DELETE /auth/session invalidates the server-side session.
Fetch a new CSRF token before logging in again after logout/expiry.

Session cookie: Secure, HttpOnly, SameSite=Lax, path=/, no broad Domain. Production
requires HTTPS and same-origin hosting. Idle expiry defaults to 30 minutes; absolute
expiry defaults to 12 hours from successful authentication. Both are configurable.
Authentication version/status is checked once per authenticated request; revoked or
expired sessions are invalidated and return a generic 401. No localStorage tokens.

BCrypt via a delegating encoder uses cost 12 initially, minimum 12 password characters
and maximum 72 UTF-8 bytes. Passwords are neither normalized nor trimmed. Hashing
happens outside the short registration transaction. Unknown-user login performs a
dummy hash verification. Duplicate normalized email returns generic registration
conflict; login does not disclose account existence/status. Email normalization is
strip plus Locale.ROOT lowercase; no provider-specific dot/plus transformations.
Phone is optional and accepts already international E.164 form; no country inferred.

RBAC uses Spring method security and a deny-by-default URL policy. Only health,
CSRF, registration and login are public. Ownership will be enforced inside future
feature services. Principal IDs come exclusively from authenticated server state.
Unknown registration fields (including role/status/id/hash) are rejected.

Database-backed per-IP and per-email login counters avoid per-instance bypass.
Counters store SHA-256 keys, expire by fixed windows and are cleaned hourly. Remote
addresses come from the connection; forwarded headers are not trusted by default.
Proxy-aware enforcement must be reviewed with the deployment topology.

Registration commits user plus CUSTOMER assignment atomically; uniqueness is also
enforced by PostgreSQL. No personal database is migrated without prior schema/grant
inspection. Integration tests use PostgreSQL 18 with a restricted runtime role.

## API contract and manual verification

All routes below are under `/api/v1`. Responses use JSON except successful
login/logout, which return 204. Registration does not log the customer in.

| Method and path | Request / response |
| --- | --- |
| GET `/auth/csrf` | Returns `headerName` and masked `token`; preserve the SESSION cookie |
| POST `/auth/registrations` | `fullName`, `email`, `password`, optional E.164 `phone`; returns 201 UserResponse |
| POST `/auth/session` | `email`, `password`, current CSRF header and cookie; returns 204 with rotated session |
| GET `/users/me` | Session cookie; returns id, fullName, email, phone, status, emailVerified and roles |
| DELETE `/auth/session` | Session cookie and refreshed CSRF header; returns 204 and invalidates the session |

Use the configured same-origin HTTPS address for browser verification. Obtain a
CSRF token first, preserve cookies, and send the returned token in `X-CSRF-TOKEN`.
After registration, log in and fetch another CSRF token. Verify `/users/me` returns
only the authenticated identity. Log out with the refreshed token, then verify
replaying the old cookie cannot access `/users/me`. Sending `roles`, `status`, `id`
or any other unknown registration field must return 400. Missing/invalid CSRF
returns 403; unauthenticated current-user access returns 401; throttling returns
429 with `Retry-After: 900`.

CSRF tokens belong in client memory and must be refreshed at authentication
boundaries. Do not refetch them before every ordinary authenticated request.
No email provider, recovery endpoint, administrator onboarding API, profile-edit
API, or role-management API is claimed as implemented.

## Database and security review

V1 contains relational user/role constraints and trigger functions for revocation.
V2 contains the Spring Session JDBC schema, session-ID/expiry/principal indexes,
and authentication counter storage/expiry index. Hibernate validates production
User mappings; session schema initialization is disabled. The old Foundation probe
migration was renamed to test-only V1000 so it does not collide with production V1.
Only disposable Testcontainers databases were migrated; the user's local database
has not been inspected or changed.

Runtime privileges must be provisioned after reviewing the target database: users
and user_roles need the operations used by registration, roles is reference data,
and session/counter tables need their lifecycle DML. Role-assignment triggers run
with caller privileges and require updates to users.auth_version/updated_at.
The runtime account must not own schemas, be a superuser, or write Flyway history.
The integration suite verifies runtime DDL/history access is denied.

Account/password/email/role mutations revoke all prior principals logically through
auth_version. Stale session rows are deleted when presented or by expiry cleanup;
physical deletion of every row is not required to deny access. Requests already
authorized before a concurrent change can finish. Administration and password-change
workflows are deferred, but database changes already enforce revocation.

Security checks passed for public privilege injection, password hash storage,
password-free session principals, CSRF rotation and missing tokens, session-ID
rotation, logout/replay, account/role/password revocation, idle/absolute expiry,
cross-user identity access, method-level ADMIN authorization, and generic login
errors. PostgreSQL error detail is disabled on the application datasource because
duplicate-key details otherwise contain the submitted email. A regression assertion
checks that concurrent registration logs expose neither the email nor password.

The generic registration conflict still reveals that registration was not accepted;
it is not a claim of complete account-enumeration resistance. Login responses do
not distinguish missing, suspended, or wrong-password accounts. Rate counters count
attempts, including successes, and can temporarily affect users sharing a source IP.

## Performance and query review

Login fetches the user and roles with an explicit entity graph; password hashing
runs after the repository read and outside a long database transaction. Registration
hashes first and atomically writes user plus CUSTOMER membership. Concurrent duplicate
registrations produce one identity/assignment and one 409 through the unique index.

Authenticated requests perform one narrow status/version lookup. `/users/me` also
fetches the identity and roles; JDBC session persistence adds its own queries. Role
collections are deliberately initialized, avoiding lazy loads after repository exit
and avoiding an N+1 pattern. User UUID/email and user-role primary keys support these
lookups. Session and counter expiry scans have indexes. Counter increments use atomic
PostgreSQL upserts. No Hikari/thread pool sizes were increased and no Redis was added.

These are code/query-path observations, not measured workload capacity. BCrypt cost
12 and default rate limits need calibration against the selected deployment hardware
and traffic. The approximately 1,000-user target remains unproven.

## Exact verification results

Command: `mvnw.cmd -B -ntp verify`, Java 21.0.11, Spring Boot 4.1.1,
Testcontainers 2.0.5, PostgreSQL 18.6.

| Suite | Passed | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| IdentityRulesTests | 5 | 0 | 0 | 0 |
| ApiExceptionHandlerTests | 6 | 0 | 0 | 0 |
| HealthControllerTests | 1 | 0 | 0 | 0 |
| IdentityIT (real HTTP and JDBC sessions) | 12 | 0 | 0 | 0 |
| BackendApplicationIT | 5 | 0 | 0 | 0 |
| Total | 29 | 0 | 0 | 0 |

Build/package succeeded in 37.157 seconds. Production JAR inspection confirmed both
production migrations are packaged and test probes/fixtures are absent. The final
read-only Docker check found no remaining Testcontainers-labelled containers.
No Git/GitHub operations were performed.

Earlier failures were corrected before this successful full regression: test imports
and MVC slice security setup, realistic idle-expiry test state, and PostgreSQL log
detail. MVC slices intentionally use a test security chain for parser/error behavior;
IdentityIT exercises the real application security chain through HTTP.

## Changed files

Paths are relative to the project root. Generated build outputs are excluded.

- `README.md`, `.env.example`, `docs/identity.md`
- `backend/pom.xml`
- `backend/src/main/resources/application.properties`
- `backend/src/main/resources/db/migration/V1__create_users_and_roles.sql`
- `backend/src/main/resources/db/migration/V2__create_sessions_and_auth_rate_limits.sql`
- Removed `backend/src/main/resources/db/migration/README.txt`
- `backend/src/main/java/com/tayyar/auth/AuthController.java`
- `backend/src/main/java/com/tayyar/auth/AuthRateLimiter.java`
- `backend/src/main/java/com/tayyar/auth/EmailAddress.java`
- `backend/src/main/java/com/tayyar/auth/IdentityAuthenticationManager.java`
- `backend/src/main/java/com/tayyar/auth/IdentityException.java`
- `backend/src/main/java/com/tayyar/auth/IdentityExceptionHandler.java`
- `backend/src/main/java/com/tayyar/auth/IdentityProperties.java`
- `backend/src/main/java/com/tayyar/auth/IdentitySecurityConfiguration.java`
- `backend/src/main/java/com/tayyar/auth/LoginRequest.java`
- `backend/src/main/java/com/tayyar/auth/PasswordValidator.java`
- `backend/src/main/java/com/tayyar/auth/RegistrationRequest.java`
- `backend/src/main/java/com/tayyar/auth/RegistrationService.java`
- `backend/src/main/java/com/tayyar/auth/SecurityErrorWriter.java`
- `backend/src/main/java/com/tayyar/auth/SessionLoginService.java`
- `backend/src/main/java/com/tayyar/auth/SessionPrincipal.java`
- `backend/src/main/java/com/tayyar/auth/SessionValidityFilter.java`
- `backend/src/main/java/com/tayyar/auth/ValidPassword.java`
- `backend/src/main/java/com/tayyar/user/AccountStatus.java`
- `backend/src/main/java/com/tayyar/user/CurrentUserController.java`
- `backend/src/main/java/com/tayyar/user/Role.java`
- `backend/src/main/java/com/tayyar/user/User.java`
- `backend/src/main/java/com/tayyar/user/UserRepository.java`
- `backend/src/main/java/com/tayyar/user/UserResponse.java`
- `backend/src/test/java/com/tayyar/auth/IdentityRulesTests.java`
- `backend/src/test/java/com/tayyar/auth/IdentityIT.java`
- `backend/src/test/java/com/tayyar/BackendApplicationIT.java`
- `backend/src/test/java/com/tayyar/support/PostgresIntegrationTest.java`
- `backend/src/test/java/com/tayyar/support/WebSliceSecurity.java`
- `backend/src/test/java/com/tayyar/common/api/ApiExceptionHandlerTests.java`
- `backend/src/test/java/com/tayyar/health/HealthControllerTests.java`
- Renamed test-only `V1__create_foundation_probe.sql` to `V1000__create_foundation_probe.sql`
  in `backend/src/test/resources/db/foundation-test/`

## Remaining prerequisites and technical debt

- Local database schema/grants must be inspected and migrations applied deliberately
  before running Identity there. The explicit local smoke test needs DB_PASSWORD.
- Email verification/recovery delivery and privileged-account provisioning are not
  exposed; no provider was selected. ACTIVE customer accounts currently have unverified emails.
- Production needs HTTPS, reviewed proxy/IP handling, ingress request-size limits,
  database secret controls, and measured password-hash/rate-limit settings.
- JDBC session attributes use framework serialization: database access must be trusted,
  and incompatible principal changes during deployment require a session migration or invalidation plan.
- Java/Mockito dynamic-agent and test API deprecation warnings remain non-blocking.
- The mutable PostgreSQL 18 image tag and deployment reproducibility will be addressed
  in the deployment module. No capacity or deployment-readiness claim is made.

Recommended Conventional Commit:
`feat(auth): implement customer identity and secure JDBC sessions`
