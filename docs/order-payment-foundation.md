# Order and Payment Foundation — Module 10 checkpoint

Status: implemented and verified; stopped at Module 10. Checkout, cart conversion,
payment-provider calls, restaurant operations, driver assignment and frontend UI are
outside this module. No Order or Payment HTTP endpoint was added.

## Aggregate and migration design

`V9__create_orders_and_payments.sql` is the only new production migration. V1–V8
were not edited. It creates `orders`, `order_items`, `order_address_snapshots`,
`order_status_history`, `payments` and `payment_status_history` with UUID primary
keys, `TIMESTAMPTZ` timestamps, constrained enums, foreign keys, decimal money,
immutable history guards and optimistic versions.

An Order is a durable record of one customer's purchase from one restaurant branch.
Its customer, restaurant, branch, currency and monetary totals cannot change after
creation. A composite foreign key proves that the selected branch belongs to the
Order's restaurant. Order creation accepts internal trusted draft data, validates it,
calculates item subtotals, merchandise subtotal and final total, then persists the
aggregate and its initial history entry in one transaction. The service is a primitive
for the future Checkout module; it does not read or consume a Cart.

Each OrderItem preserves the source menu item ID, purchased name, decimal unit price,
quantity and calculated line subtotal. It is deliberately separate from the live
MenuItem. Menu name, price and availability changes therefore do not alter the
purchase record. The source foreign key uses restrictive behavior, preventing the
source item from being deleted while historical orders reference it. Future option
snapshots can be added alongside this fixed purchase meaning without introducing an
options engine now.

The one-to-one address snapshot preserves label, street, building, floor, apartment,
landmark, delivery instructions, city, region, postal code, country code, optional
coordinate pair, and optional delivery-zone identity/name and managed-city display
name. It intentionally has no foreign key to `customer_addresses` or delivery-zone
tables: normalized source IDs may be recorded as historical values, while the copied
display and delivery fields remain authoritative for the Order. Editing or deleting a
saved address cannot mutate the snapshot.

## State machines and audit

Allowed Order transitions are explicit in both application rules and a database
trigger:

```text
PENDING_PAYMENT -> PLACED | PAYMENT_FAILED | CANCELLED
PLACED          -> ACCEPTED | REJECTED | CANCELLED
ACCEPTED        -> PREPARING | REJECTED
PREPARING       -> READY_FOR_PICKUP
READY_FOR_PICKUP -> OUT_FOR_DELIVERY
OUT_FOR_DELIVERY -> DELIVERED
```

`DELIVERED`, `PAYMENT_FAILED`, `REJECTED` and `CANCELLED` are terminal. Cancellation
is limited to the safe pre-acceptance states. Rejection is limited to the period
before preparation. A reason is required for payment failure, rejection and
cancellation. Actor authorization remains the responsibility of later endpoint
services, but the transition actor type already distinguishes a session-derived USER
from trusted SYSTEM and PROVIDER actors without accepting arbitrary user IDs.

Every creation and status change appends an immutable relational history row with
previous/new status, timestamp, actor kind, optional authenticated user and optional
reason. The aggregate update and history insert share one transaction. Database
triggers reject history update/deletion and invalid direct status changes; the model
does not require event sourcing. History reads are chronological and bounded to 101
rows so a future API must make an explicit pagination decision rather than eagerly
loading an unbounded audit trail.

Payment is a separate aggregate linked to an Order. It stores method, state,
authoritative Order amount/currency, nullable provider metadata, timestamps and an
optimistic version. Payment creation reads the persisted Order terms; callers cannot
supply an amount. A composite foreign key requires `(order_id, currency, amount)` to
match the Order's final total. CASH forbids provider metadata. CARD permits optional
provider name/reference as future reconciliation metadata, with a partial unique
index when both are present. There are no PAN, CVV, token, credential or secret
columns and no external gateway behavior.

Payment transitions are also explicit in application and database rules:

```text
CASH: PENDING -> PAID -> REFUNDED
CARD: PENDING -> AUTHORIZED | FAILED
CARD: AUTHORIZED -> PAID | FAILED
CARD: PAID -> REFUNDED
```

`FAILED` and `REFUNDED` are terminal, and both require a reason. CARD cannot jump
from PENDING to PAID, so the foundation does not simulate provider success. Payment
history mirrors Order history because later callbacks and reconciliation need a
durable transition record. Payment transition and history insertion are atomic.

Order and Payment state remain independent. A CASH Order may be `PLACED` while its
Payment is `PENDING`. A future prepaid CARD flow may retain an Order in
`PENDING_PAYMENT` while Payment is `PENDING` or `AUTHORIZED`. Checkout and a selected
provider workflow will later decide when coordinated transitions occur; this module
does not couple them implicitly.

## Money, transactions and concurrency

All money uses Java `BigDecimal`, PostgreSQL `NUMERIC(12,2)` and the single supported
currency `EGP`. Application validation requires nonnegative, scale-at-most-two values
below 10,000,000,000. Database checks enforce the same range, the Order total formula,
discount not exceeding merchandise plus delivery, and each item subtotal formula.
There is no floating-point arithmetic, client-supplied Payment amount, tax, service
fee or promotion engine.

Order and Payment writes use conditional `UPDATE ... WHERE version = ?` statements.
One successful transition increments the version; a stale or racing transition gets
a domain conflict suitable for a future HTTP 409. A two-actor integration test proves
one deterministic winner and one conflict for simultaneous accept/cancel attempts.
Injected history failures prove transaction rollback restores the original aggregate
status and version. No external network operation occurs in these transactions.

## Retention, query and security review

Order, item snapshot, address snapshot, status history, Payment and payment history
rows are protected from hard deletion or historical mutation by database triggers.
Source relationships do not use destructive cascades. Restaurant, branch, customer
and menu item deletion is restricted while referenced; saved addresses remain
independently mutable because Orders contain their own address snapshot. A later
reviewed retention/privacy workflow may add controlled archival or erasure handling.

Targeted indexes support customer history by `(customer_id, created_at)`, restaurant
and branch queues by context/status/time, item and history loading by aggregate,
Payment lookup by Order, and provider reconciliation by a unique non-null provider
reference. Order details use bounded aggregate queries, and audit reads are capped.
No Redis, Kafka, Elasticsearch, Hikari change or capacity claim was introduced.

The security review covered status mass assignment, money injection, cross-restaurant
branch/item references, immutable context, destructive cascades, audit mutation,
actor identity and accidental card-data storage. State changes are only internal
services and database-guarded transitions. No new request mapping exposes Order or
Payment information or mutation. This is a scoped code and integration review, not a
penetration-test certification.

## Verification

The targeted PostgreSQL 18 integration command and full regression command are:

```powershell
backend\mvnw.cmd -B -ntp -Dmaven.repo.local=C:\Users\DEATHX7\.m2\repository -Dit.test=OrderPaymentFoundationIT verify
backend\mvnw.cmd -B -ntp -Dmaven.repo.local=C:\Users\DEATHX7\.m2\repository verify
```

Targeted verification completed **2026-09-11 08:01:28 Africa/Cairo** in
**25.806 s** with **BUILD SUCCESS**: OrderPaymentFoundationIT ran 13 tests with no
failures, errors or skips.

Full verification completed **2026-09-11 08:05:43 Africa/Cairo** in **02:34 min**
with **BUILD SUCCESS**, Java **21.0.11** and PostgreSQL **18.6**. No test was skipped.

| Suite | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| Unit/controller tests | 50 | 0 | 0 | 0 |
| Integration tests | 130 | 0 | 0 | 0 |
| **Total** | **180** | **0** | **0** | **0** |

Module 10 adds 7 rule tests and 13 integration tests. The packaged application
contains production Order/Payment classes and V9, and excludes test classes. Logs
are `backend/target/order-payment-targeted.log` and
`backend/target/order-payment-verification.log`; XML/text reports are under the
Maven Surefire and Failsafe report directories.

## Remaining work

Checkout must authoritatively revalidate Cart contents, branch/menu availability,
price, customer address, delivery zone/rule, fee and minimum; choose the initial
Order state; create Order and Payment atomically; retire the Cart; and add idempotency.
Provider selection, secure token handling, callbacks, reconciliation, prepaid refund
coordination, actor-specific operational endpoints, driver assignment and a reviewed
retention/privacy process remain future modules.

Recommended Conventional Commit:
`feat(order): add order and payment foundations`
