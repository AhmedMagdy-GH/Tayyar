# Delivery and Driver Operations — Module 13 checkpoint

Status: implemented; verification is recorded below. This module extends the Order
lifecycle from `READY_FOR_PICKUP` through `OUT_FOR_DELIVERY` and `DELIVERED` for
platform delivery. It adds Driver operational state, explicit ADMIN assignment,
assigned-Driver reads and atomic CASH collection. It does not add card-provider
integration, refunds, GPS/live tracking, routing, notifications or frontend UI.

## Schema and v1 rules

V11 adds `driver_profiles`, `delivery_assignments`, `driver_state_history` and
`delivery_assignment_history`. V1–V10 are unchanged. All identifiers are UUIDs and
times are `timestamptz`. Profiles and assignments have optimistic versions. Partial
unique indexes enforce one ACTIVE assignment per Order and one ACTIVE assignment per
Driver. This is the v1 compatibility rule: one Driver may carry only one active
delivery. Reassignment is deferred, so an assignment progresses only `ACTIVE ->
COMPLETED`.

Foreign keys retain Driver, Order and actor records; no historical delivery record is
cascade-deleted. Database triggers protect immutable profile identity, assignment
context and all state/assignment history rows. CHECK constraints protect Driver and
assignment states, completion timestamps and valid transitions. Targeted indexes
cover active Driver/Order lookup and Driver/Order assignment history.

Driver states are `OFFLINE`, `AVAILABLE` and `BUSY`. A provisioned profile starts
OFFLINE. A Driver explicitly moves `OFFLINE -> AVAILABLE` and `AVAILABLE -> OFFLINE`.
ADMIN assignment performs `AVAILABLE -> BUSY`; successful delivery performs `BUSY ->
AVAILABLE`. A BUSY Driver cannot change state through the self-service state routes.

## APIs and authorization

| Actor | Method | Path | Contract |
| --- | --- | --- | --- |
| ADMIN | POST | `/api/v1/admin/drivers` | Provision an active user who already has DRIVER role |
| ADMIN | POST | `/api/v1/admin/delivery-assignments` | Assign an AVAILABLE Driver to an eligible Order |
| DRIVER | GET | `/api/v1/driver/profile` | Read own operational profile |
| DRIVER | POST | `/api/v1/driver/profile/available` | Explicit `OFFLINE -> AVAILABLE` |
| DRIVER | POST | `/api/v1/driver/profile/offline` | Explicit `AVAILABLE -> OFFLINE` |
| DRIVER | GET | `/api/v1/driver/orders?page=0&size=20` | Bounded own ACTIVE assignment queue |
| DRIVER | GET | `/api/v1/driver/orders/{orderId}` | Read own active assigned Order |
| DRIVER | POST | `/api/v1/driver/orders/{orderId}/pickup` | `READY_FOR_PICKUP -> OUT_FOR_DELIVERY` |
| DRIVER | POST | `/api/v1/driver/orders/{orderId}/deliver` | `OUT_FOR_DELIVERY -> DELIVERED` |

Profile state writes accept `{ "version": 0 }`. Assignment accepts only `orderId`,
`driverId` and `orderVersion`. Pickup/delivery accept only `orderVersion` and
`assignmentVersion`. Unknown fields are rejected. No Driver/assignment/payment actor,
amount, method or status can be supplied as authority.

ADMIN provisioning does not grant DRIVER role. The target must already be an active
central User with DRIVER role, preserving privileged role-management separation.
Only ADMIN can assign. CUSTOMER, DRIVER, RESTAURANT_OWNER and RESTAURANT_STAFF cannot
self-assign or assign platform Drivers. Driver identity for operational writes always
comes from the authenticated session.

Assignment requires an existing `READY_FOR_PICKUP` Order with the expected version, a
`TAYYAR_DELIVERY` Branch, no active Order assignment, and an active DRIVER profile in
AVAILABLE state. The service key-share locks the Branch delivery model while assigning.
`RESTAURANT_DELIVERY` is explicitly unsupported by this platform-driver workflow and
awaits a separately approved restaurant-delivery design.

## Driver reads and privacy

The queue is page-bounded (page 0–10000, size 1–100) and constrained to the session
Driver's ACTIVE assignment. Since the v1 database invariant permits at most one active
assignment, no client-selected sort is exposed. A single joined projection returns
Order/assignment versions, public restaurant/branch pickup identity and address,
immutable destination address/instructions, Order status, Payment method/currency and
the server-authoritative CASH amount to collect. Customer email/authentication data,
membership data, restaurant administration/financial data, provider metadata and audit
actors are omitted. No customer delivery contact snapshot currently exists, so live
User contact data is deliberately not substituted.

Another Driver's assignment and forged/inaccessible resource identifiers receive safe
404 behavior. Repeating delivery against the same Driver's completed assignment gives
a deterministic 409; another Driver still receives 404.

## Pickup, delivery and CASH

Only the active assigned Driver can pick up an Order. Pickup locks and validates the
Order, assignment and Driver, then delegates the explicit transition to the existing
Order service. Its conditional version update and immutable Order history insertion
remain atomic. Assignment remains ACTIVE and the Driver remains BUSY.

Delivery completion requires `OUT_FOR_DELIVERY`, the active assigned Driver, matching
Order/assignment versions and BUSY state. For CASH, the single authoritative Checkout
Payment must still be PENDING. In one transaction the operation:

1. performs `OUT_FOR_DELIVERY -> DELIVERED` and appends Order history;
2. performs CASH `PENDING -> PAID` through `PaymentService` and appends Payment history;
3. performs assignment `ACTIVE -> COMPLETED` and appends assignment history; and
4. performs Driver `BUSY -> AVAILABLE` and appends Driver-state history.

The Driver confirms collection and never submits an amount. The immutable Checkout
Payment amount is unchanged. A CARD Payment is never marked PAID here; delivery is
permitted only if a future approved provider workflow has already made it PAID.
Otherwise it conflicts. Multiple Payments for one Order are treated as operationally
ambiguous and conflict rather than guessing.

Injected failures at Order history, Payment history, assignment completion and Driver
release prove that all preceding writes roll back. A failed CASH transition leaves the
Order `OUT_FOR_DELIVERY`, Payment `PENDING`, assignment ACTIVE and Driver BUSY. There
are no external calls inside these transactions.

## Locks and concurrency

The Delivery write lock order is Order, Branch configuration when needed, active
assignment, Driver profile, then Payment. Assignment follows Order, Branch, assignment
check and Driver. Pickup follows Order, assignment and Driver. Completion follows
Order, assignment, Driver and Payment. Profile self-service locks only its own Driver
row and does not lock an Order. Existing Order/Payment conditional version updates and
database uniqueness constraints remain final conflict guards.

PostgreSQL race tests cover two ADMIN assignments for one Order, one Driver assigned
to two Orders, duplicate pickup and duplicate delivery. Exactly one conflicting request
wins. The losing request receives deterministic 409, and Order/Payment/assignment
histories contain one successful transition. READY_FOR_PICKUP assignment cannot race a
legal customer cancellation or restaurant rejection because those operations are
already prohibited by the Order state machine.

## Security and deferred workflows

Session authentication, current roles, account suspension and CSRF remain authoritative.
Tests cover assignment privilege escalation, Driver impersonation, cross-Driver IDOR,
forged/malformed identifiers, wrong delivery model, inactive/OFFLINE Drivers, stale
versions, unknown JSON/query fields, pagination abuse, Payment amount/status injection,
duplicate cash collection and suspended sessions. This is a scoped code and integration
review rather than penetration-test certification.

Failed delivery, customer refusal, cash discrepancy, returns, partial delivery,
reattempts, post-pickup reassignment, restaurant-operated delivery, live location,
routing, notifications, refunds and card-provider processing remain deferred pending
approved product rules.

## Query/performance review

The Driver queue uses one profile check, one count and one joined bounded projection;
it does not query per assignment, Order, address or Payment. Instrumentation records a
fixed 10 `JdbcTemplate` read-method invocations including delegating overloads. This is
not a wire SQL count or capacity claim. Fixture-scale EXPLAIN uses
`delivery_assignments_active_driver_uq` for the active Driver queue and
`delivery_assignments_active_order_uq` for active Order authorization. Evidence is at
`backend/target/driver-operations-query-review.txt`. No cache, broker, search service,
mapping service or Hikari change was introduced.

## Verification

Targeted command:
`backend/mvnw.cmd -B -ntp -Dmaven.repo.local=C:\Users\DEATHX7\.m2\repository -Dit.test=DriverOperationsIT verify`.
It completed **2026-09-11 13:12:20 Africa/Cairo** in **01:03 min** with
**BUILD SUCCESS**: 12 tests, zero failures/errors/skips.

Full command:
`backend/mvnw.cmd -B -ntp -Dmaven.repo.local=C:\Users\DEATHX7\.m2\repository verify`.
It completed **2026-09-11 13:21:15 Africa/Cairo** in **03:57 min** with
**BUILD SUCCESS**, Java **21.0.11** and PostgreSQL **18.6**.

| Suite | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| Unit/controller | 50 | 0 | 0 | 0 |
| Integration | 172 | 0 | 0 | 0 |
| **Total** | **222** | **0** | **0** | **0** |

The initial full regression identified the expected Foundation migration-inventory
update from 11 to 12 applied test migrations after V11; the assertion was updated and
the complete gate then passed. Intentional injected write failures produce generic 500
log entries while verifying transaction rollback. Logs are
`backend/target/driver-operations-targeted.log` and
`backend/target/driver-operations-verification.log`; standard Surefire/Failsafe XML
reports contain suite details.

## Changed files and recommendation

V11, Driver/assignment enums, DTOs, exception handling, persistence/query components,
profile/assignment/Order services and four explicit controllers were added under
`com.tayyar.delivery`. Security routing, README, and the PostgreSQL integration suite
were updated or added.

Recommended Conventional Commit:
`feat(delivery): add driver assignment and cash delivery operations`
