# Production Hardening and Observability

## Initial audit

The backend already generated a new UUID correlation ID for every request, returned it in
`X-Correlation-ID` and API errors, and ignored client-supplied IDs. Its global error contract
removed stack traces, exception messages, binding values, and servlet error details. Identity
already used rotated JDBC sessions, server-side CSRF tokens, `HttpOnly`/`Secure`/`SameSite=Lax`
cookies, idle and absolute expiry, logout invalidation, and database-backed atomic authentication
throttling. Forwarded headers were deliberately ignored. Hibernate validated the schema; Flyway
clean and baselining were disabled, with separate migrate-profile credentials. The compatibility
health endpoint was lightweight but did not express database readiness. Actuator, metrics,
request completion logs, explicit CORS/header policy, bounded JSON bodies, and graceful shutdown
were absent.

No production logging of request or response bodies was found. The targeted logger search found
no `System.out`, `printStackTrace`, SQL trace, or application log statement containing passwords,
emails, phones, addresses, coordinates, review text, notification bodies, or payment details.

## Logging and correlation

`RequestCorrelationFilter` creates a server-trusted UUID on every dispatch. Incoming
`X-Correlation-ID` is intentionally ignored, preventing spoofing, oversized values, and log
injection. The ID is placed in MDC, returned in the response header, included in safe API error
bodies, and attached to one completion event containing method, request path (never the query
string), status, duration, and either an authenticated internal user UUID or `anonymous`.

The `production` profile emits Logstash-compatible structured JSON to stdout. Logs never include
global request/response bodies, headers, cookies, CSRF values, credentials, or resolved secrets.
Expected 4xx/domain errors are not logged with application stack traces. Unexpected failures log
the correlation ID, exception class, and top code location, but deliberately omit the exception
message because JDBC/provider messages may contain submitted data. Domain histories remain the
authoritative audit records for checkout, order, and delivery transitions.

## Health and management policy

- `GET /api/v1/health`: existing compatibility status; anonymous; does not claim dependency health.
- `GET /actuator/health/liveness`: anonymous, details suppressed, contains only application
  liveness state. Database loss does not make it DOWN and therefore should not create restart loops.
- `GET /actuator/health/readiness`: anonymous, details suppressed, includes application readiness
  and the auto-configured cheap `DataSource` check. Database loss returns HTTP 503.
- `/actuator/health`, `/actuator/info`, `/actuator/metrics`, and `/actuator/prometheus`: require an
  authenticated `ADMIN` session. CSRF still applies to any state-changing management request.
- Only `health`, `info`, `metrics`, and `prometheus` are exposed. `env`, `beans`, `configprops`,
  `heapdump`, `threaddump`, and `mappings` are not exposed.

Detailed health is never returned. Database credentials, URLs, topology, or exception detail are
not included. A separate management port is deferred until deployment topology is known; future
infrastructure must restrict it at the network layer as defense in depth. A future Prometheus
scraper should authenticate through an approved internal mechanism or management identity; no
Prometheus/Grafana service is deployed in this module.

## Metrics

Actuator/Micrometer provides bounded metrics for HTTP server requests, JVM, process, system, and
Hikari pool behavior. Prometheus registry support is present. Checkout adds
`tayyar.checkout.outcomes` with only `outcome=success|conflict`; it never labels user, cart, order,
restaurant, email, or other high-cardinality values. No business identifiers are metric labels.

## Abuse protection

Existing login, registration, and CSRF issuance limits remain PostgreSQL-backed, atomic, and
multi-instance. Authenticated checkout now uses the same fixed 15-minute counter table keyed by a
SHA-256 digest of the internal customer UUID, default 20 attempts. It returns the existing safe
429 contract and `Retry-After: 900`. Counters are capped and entries older than one day are removed
hourly. This creates bounded cardinality per registered customer and does not trust forwarded IP
headers. Review and promotion abuse already passes through authenticated, bounded business rules;
additional limits should be justified by production evidence rather than applied globally.

## Browser and transport security

Spring Security explicitly applies `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`,
`Referrer-Policy: no-referrer`, and HSTS on secure HTTPS responses. HSTS is not emitted on local
HTTP. CSP is deferred because this is a JSON API and the frontend/deployment policy is not yet
defined.

CORS defaults to same-origin only. `ALLOWED_FRONTEND_ORIGINS` may contain explicit origins;
credentialed requests never use `*`. Production rejects HTTP origins, user-info, paths, queries,
fragments, and invalid origins at startup. Allowed methods and headers are narrowly enumerated.
Forwarded client-IP headers remain disabled until deployment defines trusted proxy boundaries.

The production profile requires the existing Secure cookie setting and preserves `HttpOnly`,
`SameSite=Lax`, rotation, revocation, idle timeout, and absolute timeout. CSRF remains enabled for
session-authenticated state changes; GET routes were not changed to mutate data.

## Configuration, limits, and shutdown

Activate the production profile with `spring.profiles.active=production`. In that profile
`DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` have no localhost/default fallback. Startup validation
rejects unsafe cookies, Flyway clean/baseline settings, invalid production CORS origins, and request
body limits outside 64KB–10MB. Values come from environment/deployment secret management and are
never printed by application code. `.env.example` contains names and placeholders only.

The default body limit is 1MB. A filter enforces it for declared and streamed JSON bodies; Tomcat
also limits form posts and swallowed bodies to the same value. Multipart handling is disabled
until a file-upload API exists. Request headers default to 16KB. Connection timeout defaults to
10 seconds. DTO field constraints remain authoritative.

Spring Boot graceful shutdown is enabled with a configurable 30-second phase timeout. The server
stops accepting new work and allows in-flight checkout/order/delivery transactions a bounded time
to finish. No global database transaction timeout was added: write-path timeouts need query and
load evidence, and no external network call was introduced inside critical transactions.

## Database and build safety

No migration was added or changed. Runtime credentials still need DML only; migration credentials
remain separate. Normal runtime Flyway remains opt-in, while clean and automatic baseline remain
disabled. Production SQL/bind logging is not enabled. Hikari pool size and server thread counts
remain Spring defaults and must be selected using later load-test evidence.

Actuator and the Prometheus registry are the only new dependencies. Existing production
dependencies were not broadly upgraded. Testcontainers and Spring Security/MVC test artifacts
remain test-scoped. The Spring Boot executable JAR build is verified to exclude test classes and
the `db/foundation-test` migration.

## Deployment prerequisites and limitations

The next module must provide TLS termination, a documented trusted-proxy/network boundary,
firewall protection for management endpoints, authenticated Prometheus scraping, production
database and migration roles, secret injection, log collection/redaction retention policy, and
shutdown grace periods aligned with the orchestrator. Validate cookie/CORS origin choices against
the final frontend origin.

No Docker, CI/CD, Kubernetes, Redis, Kafka, Elasticsearch, external monitoring SaaS, frontend,
penetration test, or final load test is part of this checkpoint. Per-endpoint limits and pool/thread
sizing should be revisited from production telemetry and the later load-test results.

## Verification result

The final `mvnw verify` run succeeded against PostgreSQL 18.6 Testcontainers: 58 unit/MVC tests
and 201 integration tests, 259 total, with zero failures, errors, or skips. Coverage includes healthy
and database-down probes, restricted management routes, administrator metrics/Prometheus access,
credentialed CORS denial, security headers, correlation/error privacy, body limits, production
configuration rules, checkout throttling, session/CSRF regressions, Flyway privilege/clean/baseline
safety, and every prior business module. The packaged executable JAR contains no test classes,
foundation-test migration, or named test-only dependency JARs.

`mvn dependency:analyze` also completed successfully. Its direct-bytecode analysis reports the
usual Spring Boot starter false positives (starter POMs appear unused while their transitive classes
appear undeclared); manual scope review found no duplicate direct dependency and confirmed all test
framework/container dependencies remain test-scoped. No broad dependency upgrades were made.
