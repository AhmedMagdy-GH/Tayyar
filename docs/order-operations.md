# Order Operations — Module 12 checkpoint

Status: implemented and verified; stopped at Module 12. This module exposes the
existing Order aggregate through customer and restaurant operations. Payment
collection/refunds, driver assignment, delivery progression/tracking, notifications,
promotions and frontend UI remain outside this module.

## Schema decision

No migration was added and V1–V10 were not edited. V9 already provides the required
indexes: `orders_customer_history_idx` for recent customer history,
`orders_restaurant_queue_idx` and `orders_branch_queue_idx` for operational queues,
plus aggregate item/history and Payment-by-Order indexes. Existing Order optimistic
versions, transition guards and immutable history are reused.

## Customer API

All customer routes require an authenticated CUSTOMER. Ownership comes only from the
session; no customer ID is accepted.

| Method | Path | Contract |
| --- | --- | --- |
| GET | `/api/v1/orders?page=0&size=20&status=PLACED` | Lightweight owned history, newest first |
| GET | `/api/v1/orders/{orderId}` | Owned detail with snapshots, Payment and safe history |
| POST | `/api/v1/orders/{orderId}/cancel` | Explicit owned `PLACED -> CANCELLED` transition |

Page is 0–10000 and size is 1–100. Status is a controlled enum. Repeated, unknown,
malformed and arbitrary sort parameters are rejected. Ordering is deterministic by
`created_at DESC,id DESC`; lists do not load items, addresses, Payments or histories.

Order detail returns public restaurant/branch identity and name, immutable purchased
item name/unit price/quantity/subtotal snapshots, immutable delivery address snapshot,
Order totals/currency, CASH Payment method/status, creation time, current version and
safe status history. History exposes previous/new status, bounded reason and timestamp.
It omits actor IDs/kinds and internal history IDs. It does not read live MenuItem names
or prices and exposes no membership, staff or provider metadata.

Cancellation requires JSON `{ "version": 0, "reason": "Changed my mind" }`.
Reason is nonblank and at most 1000 characters. Only PLACED is accepted by the
customer operation. ACCEPTED, PREPARING, READY_FOR_PICKUP and every terminal state
return 409. The session customer becomes the history actor. Payment remains PENDING;
no fake refund or Payment transition occurs.

## Restaurant operations

Restaurant routes require RESTAURANT_OWNER or RESTAURANT_STAFF. ADMIN, CUSTOMER and
DRIVER do not gain operational authority.

| Method | Path | Contract |
| --- | --- | --- |
| GET | `/api/v1/restaurant-orders?restaurantId=...&branchId=...&status=...` | Authorized active queue |
| GET | `/api/v1/restaurant-orders/{orderId}` | Authorized operational detail |
| POST | `/api/v1/restaurant-orders/{orderId}/accept` | `PLACED -> ACCEPTED` |
| POST | `/api/v1/restaurant-orders/{orderId}/reject` | `PLACED -> REJECTED`, reason required |
| POST | `/api/v1/restaurant-orders/{orderId}/start-preparation` | `ACCEPTED -> PREPARING` |
| POST | `/api/v1/restaurant-orders/{orderId}/ready-for-pickup` | `PREPARING -> READY_FOR_PICKUP` |

Version-only operations accept `{ "version": 0 }`; rejection accepts version plus a
nonblank reason of at most 1000 characters. There is no generic status endpoint.
Although the Module 10 foundation remains future-capable, the Module 12 rejection
operation deliberately accepts only PLACED as its source. It therefore rejects
ACCEPTED-to-REJECTED, as required by the current operational workflow. Delivery
transitions are not exposed.

An OWNER must have a server-side restaurant membership and may list/operate all
branches for that owned restaurant. A STAFF actor must have the RESTAURANT_STAFF role,
an active session account and an existing assignment for the Order's exact branch.
An unscoped STAFF queue includes only assigned branches. A branch-filtered STAFF queue
first verifies that exact assignment. Path/query restaurant and branch identifiers are
selectors, never authority. Forged or inaccessible Order/restaurant/branch identities
receive safe 404 responses.

Queue results include only PLACED, ACCEPTED, PREPARING and READY_FOR_PICKUP. Optional
status filtering is restricted to those four values. Terminal history is excluded.
Ordering is oldest first by `created_at,id`, so waiting Orders are handled before newer
ones. Pagination has the same bounded page/size rules as customer history. The queue
uses summary projections and never loads address, items or audit history per row.

## Transactions, concurrency and Payment boundary

Every operation authorizes the Order from immutable database context, verifies the
operation-specific source state, then calls the existing `OrderService.transition`.
That service performs a conditional version update and immutable history insert in
one transaction. Authorization membership/assignment rows receive key-share locks for
writes, preventing their concurrent removal before the transition commits. Reads do
not take write locks. Unrelated Orders are not locked.

A stale version or changed/invalid state returns 409. Tests show exactly one winner
for customer cancel versus restaurant accept, accept versus reject, duplicate accept,
and duplicate start-preparation. Each race leaves exactly one new history entry; no
state is silently overwritten. An injected history failure rolls cancellation back to
PLACED with only its initial history. No external call occurs in these transactions.

Restaurant acceptance/preparation never changes the separate CASH Payment. It remains
PENDING throughout this module. No Payment mutation API, collection, CARD operation,
refund or compensation behavior was added.

## Errors and security review

The API uses 400 for malformed JSON/UUID/query/version/reason input, 401 for anonymous
access, 403 for route-role, CSRF and existing suspended-session enforcement, safe 404
for inaccessible resource identities, and 409 for stale or invalid transitions.
Responses use `Cache-Control: no-store` for operational domain errors.

Tests cover customer and restaurant IDOR/BOLA, forged Order/restaurant/branch IDs,
unassigned STAFF, foreign OWNER, ADMIN/DRIVER/customer route separation, CSRF,
suspended sessions, malformed UUIDs, pagination abuse, repeated/unknown parameters,
unknown status/actor fields and oversized reasons. Status, actor ID, customer ID,
Payment state and monetary values cannot be assigned through these requests. Existing
database triggers keep Order context and history immutable. This is a scoped code and
integration review, not penetration-test certification.

## Query/performance review

Customer and restaurant lists each use one count and one bounded summary query,
independent of result count. A detail uses a fixed set of authorization/header/items/
address/Payment/history reads, with item/history caps of 100. There is no query per
Order in lists and no query per OrderItem in details.

The integration query check records stable JdbcTemplate read-method invocation counts
before and after expanding fixtures from one to five Orders: customer list 7,
restaurant queue 10 and detail 18. These counts include delegating overloads and are
not claimed as wire SQL statement counts. Fixture-scale EXPLAIN confirms the customer
path uses `orders_customer_history_idx` and the branch/status queue path uses
`orders_branch_queue_idx`. Evidence is at
`backend/target/order-operations-query-review.txt`. No cache, message broker, search
engine, Hikari change or capacity claim was introduced.

## Verification

Targeted command:
`backend/mvnw.cmd -B -ntp -Dmaven.repo.local=C:\Users\DEATHX7\.m2\repository -Dit.test=OrderOperationsIT verify`.
It completed **2026-09-11 12:27:55 Africa/Cairo** in **59.496 s** with **BUILD
SUCCESS**: 14 tests, zero failures/errors/skips.

Full command:
`backend/mvnw.cmd -B -ntp -Dmaven.repo.local=C:\Users\DEATHX7\.m2\repository verify`.
It completed **2026-09-11 12:39:15 Africa/Cairo** in **03:21 min** with
**BUILD SUCCESS**, Java **21.0.11** and PostgreSQL **18.6**.

| Suite | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| Unit/controller | 50 | 0 | 0 | 0 |
| Integration | 160 | 0 | 0 | 0 |
| **Total** | **210** | **0** | **0** | **0** |

OrderOperationsIT contributes 14 integration tests. The focused Order regression
run executed OrderOperationsIT plus OrderPaymentFoundationIT: 27 tests, zero
failures/errors/skips. The older foundation assertion that deliberately prohibited
all Order endpoints was narrowed to its continuing guarantee that Payment has no
HTTP API. Logs are `backend/target/order-operations-targeted.log`,
`backend/target/order-operations-targeted-regression.log` and
`backend/target/order-operations-verification.log`; standard Surefire/Failsafe XML
reports contain the suite details. The intentional history-insert failure produces a
generic 500 log entry while proving transaction rollback.

## Changed files and remaining work

New production files under `com.tayyar.order`: `CustomerOrderController`,
`CustomerOrderOperationsService`, `RestaurantOrderController`,
`RestaurantOrderOperationsService`, `OrderOperationsAccess`, `OrderOperationsDtos`,
`OrderOperationsException`, `OrderOperationsExceptionHandler`,
`OrderOperationsParameters` and `OrderOperationsQuery`. Security routing and README
were updated; `OrderOperationsIT` and this checkpoint document were added.

The current offset pagination is intentionally bounded but may later move to cursor
pagination for very deep histories. Payment collection/refund coordination and
cancellation after acceptance require explicit future workflows. Delivery owns
OUT_FOR_DELIVERY and DELIVERED. Notifications and administrative intervention are
also deferred.

Recommended Conventional Commit:
`feat(order): add customer and restaurant order operations`
