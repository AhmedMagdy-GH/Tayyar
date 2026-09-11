# Tayyar

Tayyar is a food-delivery marketplace being developed as a modular monolith.

- `backend/`: Java 21, Spring Boot 4.1.1, Spring MVC, JDBC/JPA, Flyway, PostgreSQL 18 and HikariCP.
- `frontend/`: reserved for React + TypeScript; not initialized. UI/UX will be designed separately in Figma and Google Stitch.
- `docs/`: architecture, database inspection, and Foundation verification guidance.

Implemented API: `GET /api/v1/health` remains the compatibility service-status endpoint.
Actuator supplies `GET /actuator/health/liveness` and `/actuator/health/readiness`;
only those two infrastructure probes are anonymous. PostgreSQL
connectivity, Foundation, and Identity are implemented. Identity provides customer
registration, login/logout, JDBC sessions, CSRF, and the current-user endpoint.
Restaurants provides customer applications, admin reviews, immutable resubmission
history, owner memberships, scoped profile management, and audited status changes.
Branches provides structured locations, delivery models, weekly/special hours,
operational status and existing-member staff assignments with owner/admin controls.
Menu provides one primary restaurant menu, ordered categories/items, EGP base prices,
branch overrides, effective item projections and owner/admin controls.
Customer Addresses provides private saved addresses, structured locations and atomic
default selection with ownership and version checks.
Delivery Zones provides managed cities/areas, versioned branch delivery rules,
private address-zone selection and server-side serviceability evaluation.
Discovery provides public restaurant search, branch choices, effective branch menus,
bounded filtering/sorting/pagination and optional zone or owned-address serviceability.
Cart provides one lazy active cart per customer, single-branch lines, authoritative
effective pricing/availability, totals, explicit replacement and concurrency checks.
Order and Payment Foundation provides durable order/item/address snapshots, explicit
order/payment state machines, immutable histories and transactional transition primitives.
Checkout provides atomic CASH purchases, durable idempotency, authoritative revalidation
and explicit Cart price reconfirmation.
Order Operations provides customer order history/details and safe cancellation plus
OWNER/assigned-STAFF queues and explicit preparation transitions.
Delivery and Driver Operations provides privileged Driver provisioning/assignment,
Driver availability and assigned queues, pickup, delivery and atomic CASH collection.
Card-provider integration and frontend remain unimplemented.
See [Delivery and Driver Operations API and checkpoint](docs/driver-operations.md).
See [Order Operations API and checkpoint](docs/order-operations.md).
See [Checkout API and checkpoint](docs/checkout.md).
See [Notifications API and checkpoint](docs/module-16-notifications.md).
See [Order and Payment Foundation checkpoint](docs/order-payment-foundation.md).
See [Cart API and checkpoint](docs/cart.md).
See [Discovery and Search API and checkpoint](docs/discovery.md).
See [Delivery Zones API and checkpoint](docs/delivery-zones.md).
See [Customer Addresses API and checkpoint](docs/addresses.md).
See [Menu API and checkpoint](docs/menu.md).
See [Branches API and checkpoint](docs/branches.md).
See [Restaurants API and checkpoint](docs/restaurants.md).
See [Identity API and verification](docs/identity.md) and the earlier
[Foundation checkpoint](docs/foundation.md).
See [Production hardening and observability](docs/production-hardening.md) for
logging, probes, management access, metrics, profiles, limits, and shutdown behavior.
See [Docker and deployment readiness](docs/docker-deployment-readiness.md) for the
local container workflow and the provider-neutral production deployment contract.

## Tests

Java 21 is required. The Maven wrapper downloads Maven 3.9.16 and dependencies
on first use. Set `JAVA_HOME` to your JDK and ensure `java` is available on PATH.

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd verify
```

`test` runs unit/controller tests without Docker or a personal database.
`verify` additionally runs `*IT` integration tests against disposable PostgreSQL
18 containers and builds the application. A running Docker Linux-container engine
is required. Missing Docker fails the integration gate; tests are not silently skipped.
Full regression means `verify`, not just `test`.

## Run the backend

Provide `DB_PASSWORD` through your shell/IDE secret environment. Defaults are
`DB_URL=jdbc:postgresql://localhost:5432/tayyar` and `DB_USERNAME=tayyar_app`.
Do not use `postgres` or another superuser as the application identity.
See [.env.example](.env.example) for variable names. Spring Boot does **not**
automatically load `.env` files. Never commit real credentials.

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

On macOS/Linux, use `./mvnw` instead of `.\mvnw.cmd`.

The compatibility health endpoint is available at `http://localhost:8080/api/v1/health`.
Infrastructure probes are `/actuator/health/liveness` and
`/actuator/health/readiness`. Do not route public traffic to other Actuator paths.
Browser authentication requires HTTPS because the session cookie is Secure by
default. Use same-origin HTTPS for frontend/API integration. Tests exercise HTTP
with an explicit cookie harness; they do not require weakening the secure default.

For an explicit read-only connectivity smoke test against your configured local database:

```powershell
.\mvnw.cmd -Plocal-db-smoke test
```

This opt-in smoke test disables Flyway and Hibernate schema management and is
excluded from the default suite. It does not replace integration tests.

## Schema management

Flyway is the sole schema writer. Hibernate uses `ddl-auto=validate`; SQL auto-init
and Open Session in View are disabled. Normal startup has migrations disabled so
the application runtime user does not need DDL privileges.

Before the first migration, inspect the target schema and role grants using
[the read-only audit](docs/database-inspection.sql) and review the findings.
Do not automatically baseline a nonempty database. Identity supplies
`V1__create_users_and_roles.sql` and `V2__create_sessions_and_auth_rate_limits.sql`.
Restaurants adds `V3__create_restaurant_applications_and_memberships.sql`.
Branches adds `V4__create_branches.sql`.
Menu adds `V5__create_restaurant_menus.sql`.
Customer Addresses adds `V6__create_customer_addresses.sql`.
Delivery Zones adds `V7__create_delivery_zones.sql`.
Cart adds `V8__create_customer_carts.sql`.
Order and Payment Foundation adds `V9__create_orders_and_payments.sql`.
Checkout adds `V10__create_checkout_receipts.sql`.
Delivery and Driver Operations adds `V11__create_driver_delivery_operations.sql`.
Reviews and Favorites adds `V12__create_reviews_and_favorites.sql`.
Promotions adds `V13__create_promotions.sql`.
Notifications adds `V14__create_notifications.sql`.
Platform Admin Operations adds `V15__create_admin_operations.sql`.
All fifteen must be applied before normal startup. The migration account needs
schema DDL privileges; the runtime account needs only reviewed application-table
privileges, including the updates used by authentication-version triggers.

After inspection and when reviewed migrations exist, provide separate
`DB_MIGRATION_USERNAME` / `DB_MIGRATION_PASSWORD` and run:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=migrate"
```

The `migrate` profile applies Flyway migrations using the migration account,
then runs the application with its normal `DB_*` runtime account. Stop this process
after validation and run normally without the migration credentials. This is a
local workflow, not the future production deployment procedure.

Do not enable `DB_MIGRATIONS_ENABLED=true` with a production runtime account;
that switch uses the runtime datasource unless separate Flyway credentials are set.
Never use Hibernate `create`/`update`, Flyway `clean`, or automatic baselining as a shortcut.

See [Identity notes](docs/identity.md) for API usage, security decisions, results,
remaining prerequisites, and the changed-file manifest. No CI/CD or deployment
configuration has been added.

## Local Docker environment

Docker Compose runs PostgreSQL 18, a finite Flyway migration job, and the backend.
It uses an ignored developer-local `.env`; the repository contains placeholders only.

```powershell
Copy-Item .env.example .env
# Edit .env and set POSTGRES_SUPERUSER_PASSWORD, DB_MIGRATION_PASSWORD, and DB_PASSWORD.
docker compose build backend migrate
docker compose up -d
docker compose ps --all
curl.exe http://localhost:8080/actuator/health/liveness
curl.exe http://localhost:8080/actuator/health/readiness
docker compose logs -f backend
docker compose down
```

`docker compose down` is a normal stop and preserves PostgreSQL data. See the
deployment-readiness guide before intentionally running the destructive
`docker compose down --volumes` reset command.
