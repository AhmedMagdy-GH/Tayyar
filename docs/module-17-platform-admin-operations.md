# Module 17 — Platform Admin Operations

Module 17 adds explicit, ADMIN-only operational reads and account-state commands. It does not
add impersonation, arbitrary role assignment, generic entity mutation, order/payment mutation,
billing, refunds, or reporting/SQL endpoints.

## Existing authoritative ADMIN capabilities

- Restaurant application queue, details, submission/decision history, and review decisions:
  `RestaurantAdminController` / `ApplicationService`.
- Restaurant suspension/reactivation and immutable status history:
  `PUT /api/v1/admin/restaurants/{id}/status`.
- City and Delivery Zone administration: `DeliveryController` / `DeliveryService`.
- Driver provisioning and delivery assignment: `AdminDriverController` and
  `AdminDeliveryAssignmentController`.
- Review moderation: `PATCH /api/v1/admin/reviews/{reviewId}/moderation`.
- Restaurant-scoped Promotion administration: the existing Promotion API. No duplicate APIs
  were introduced for these capabilities. Notifications remain authenticated per-user reads;
  no platform notification mutation was justified.

## New API

All routes are under `/api/v1/admin` and require an active authenticated ADMIN session.

| Method | Route | Purpose |
|---|---|---|
| GET | `/users` | Bounded exact-email/status/role account search |
| GET | `/users/{id}` | Safe account summary |
| POST | `/users/{id}/suspension` | Explicit audited ACTIVE → SUSPENDED command |
| POST | `/users/{id}/reactivation` | Explicit audited SUSPENDED → ACTIVE command |
| GET | `/restaurants?status=` | Existing restaurant list with optional status filter |
| GET | `/restaurants/{id}` | Restaurant operational overview with at most 100 branches |
| GET | `/orders` | Recent support search by status/restaurant/branch/customer |
| GET | `/orders/{id}` | Safe support detail, address snapshot, payment/assignment summary, and history |
| GET | `/drivers` | Driver/account state and active assignment projection |
| GET | `/audit-log` | Deterministic audit read filtered by action/target/actor |

Every collection uses page 0–10000 and size 1–100. Results use deterministic timestamp/UUID
ordering. User responses exclude password hashes, auth-version internals, session identifiers,
and phone numbers. Payment responses exclude provider references and all card secrets.

## Account policy and concurrency

Account status reuses `users.status`. The row is locked before mutation, and the status transition
and audit insert share one transaction. The existing `users_security_version` trigger increments
`auth_version`; `SessionValidityFilter` therefore rejects all pre-change sessions on their next
request. Reactivation changes only account state and never changes Driver state or another domain.

The simple suspension command returns 409 for self-suspension, a BUSY Driver with an ACTIVE
assignment, a Customer with a non-terminal Order, or an owner who is the only ACTIVE owner of a
Restaurant. Explicit reassignment, failed-delivery recovery, ownership transfer, and active-order
support recovery remain deferred.

Roles remain controlled by their existing workflows: public registration grants CUSTOMER,
Restaurant approval grants RESTAURANT_OWNER, Driver provisioning requires a pre-granted DRIVER
role, staff membership remains restaurant-scoped, and ADMIN provisioning remains deployment/
bootstrap responsibility. There is no general role mutation endpoint.

## Database and security

V15 creates only `admin_audit_log` plus indexes supporting the concrete admin list paths. Audit
records contain actor, controlled action and target types, target UUID, bounded reason, selected
before/after state, and timestamp. A PostgreSQL trigger rejects UPDATE and DELETE. No request body
can supply the actor. CSRF applies to all commands, unknown JSON properties fail, malformed UUIDs
are handled by the shared API layer, and both HTTP and method security enforce ADMIN access.
