# Tayyar

Tayyar is a food-delivery marketplace being developed as a modular monolith.

- `backend/`: Java 21, Spring Boot 4.1.1, Spring MVC, JDBC/JPA, Flyway, PostgreSQL 18 and HikariCP.
- `frontend/`: reserved for React + TypeScript; not initialized. UI/UX will be designed separately in Figma and Google Stitch.
- `docs/`: architecture, database inspection, and Foundation verification guidance.

Implemented API: `GET /api/v1/health` returns HTTP 200 with `{"status":"UP","service":"tayyar-backend"}`.
It identifies the service; it does not establish database readiness. PostgreSQL
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
Delivery Zones, Cart, ordering and frontend remain unimplemented.
See [Customer Addresses API and checkpoint](docs/addresses.md).
See [Menu API and checkpoint](docs/menu.md).
See [Branches API and checkpoint](docs/branches.md).
See [Restaurants API and checkpoint](docs/restaurants.md).
See [Identity API and verification](docs/identity.md) and the earlier
[Foundation checkpoint](docs/foundation.md).

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

The health endpoint is available at `http://localhost:8080/api/v1/health`.
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
All six must be applied before normal startup. The migration account needs
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
