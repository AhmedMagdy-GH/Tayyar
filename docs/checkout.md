# Checkout — Module 11 checkpoint

Checkout provides the authoritative CASH purchase boundary. It revalidates the active
Cart, live menu, selected owned saved address, opening schedule and managed delivery
rule, then creates Order, item/address snapshots, Payment, initial histories and a
durable receipt while marking the Cart CHECKED_OUT in one transaction.

## API contract

`POST /api/v1/checkout` requires an authenticated CUSTOMER, existing session CSRF
token, and exactly one `Idempotency-Key` header. The key must contain 16–128 ASCII
letters, digits, underscores or hyphens. UUID strings are suitable. It is scoped to
the customer and is not a credential. Missing, repeated or malformed keys return 400.

```json
{
  "cartId": "<current cart UUID>",
  "cartVersion": 0,
  "savedAddressId": "<owned saved address UUID>",
  "paymentMethod": "CASH"
}
```

No customer, restaurant, branch, item price, fee, subtotal, total, zone, Order status
or Payment status is accepted. Unknown JSON fields are rejected. CARD returns 409
`PAYMENT_METHOD_UNAVAILABLE`; it never creates a Payment or fake provider reference.

Both the first success and an identical retry return 201 with `Cache-Control: no-store`:

```json
{
  "orderId": "<order UUID>",
  "orderStatus": "PLACED",
  "paymentMethod": "CASH",
  "paymentStatus": "PENDING",
  "merchandiseSubtotal": 80.00,
  "deliveryFee": 20.00,
  "discountTotal": 0.00,
  "finalTotal": 100.00,
  "currency": "EGP",
  "createdAt": "<creation timestamp>"
}
```

This is the original Checkout confirmation, not a live Order-status lookup. Retries
return its original statuses even after later Order or Payment transitions. No
customer address, audit history, internal version, staff data or provider metadata
is included in the response. There is no Order operations endpoint in this module.

## Price reconfirmation

`POST /api/v1/cart/reconfirm-prices` is a separate CUSTOMER-only, CSRF-protected action:

```json
{"cartId":"<active cart UUID>","cartVersion":0}
```

It locks the customer's active Cart and current menu configuration, validates the
Cart identity/version and item availability, rereads server prices and explicitly
updates each line's acknowledged price. Line and aggregate versions increment and
the updated Cart is returned with 200. Clients must display prices before requesting
confirmation and then use the returned Cart version for Checkout. Clients cannot
submit prices. Any later price change again produces 409 at Checkout. Reconfirmation
does not create an Order or reserve a price.

## Schema and durable idempotency

Only `V10__create_checkout_receipts.sql` was added; V1–V9 were not edited. Its
`checkout_receipts` table has a `(customer_id,idempotency_key)` primary key and unique
Cart, Order and Payment references. It records typed request identity (Cart UUID,
version, saved-address UUID and method) rather than hashing arbitrary JSON. Changing
any of these selections under a previously successful key returns 409
`IDEMPOTENCY_MISMATCH`. Whitespace or JSON field order does not change the request.

The customer row lock serializes that customer's Cart/Checkout operations across
application instances. After acquiring it, Checkout checks for an existing receipt
before looking for an active Cart or revalidating mutable sources. A retry therefore
works after address deletion or creation of a subsequent Cart. Successful receipt
insertion is the final write in the same transaction. A failure rolls back all writes
and leaves no ambiguous pending idempotency record; the same key can be retried.
Only successes bind keys. Different keys cannot consume the same Cart twice.

A receipt context trigger validates matching customer/restaurant/branch relationships,
the consumed Cart version and initial CASH Order/Payment states. Receipts are immutable.
The receipt is the retained Cart-to-Order link; no redundant Order tables or nullable
Cart-to-Order lifecycle columns were added. `saved_address_id` is historical request
identity without a foreign key, allowing the original saved address to be deleted.
Consumed Carts and their CartItems reject later updates/deletes; their snapshots and
Order history retain the existing Module 10 protections.

## Authoritative validation and money

Checkout uses the session customer, verifies Cart ownership and locks their ACTIVE
Cart. It requires the exact UUID/version and 1–100 lines. The bounded joined CartQuery
loads current names, effective branch override/base prices, menu/category/item active
state and effective availability without per-item reads. Unavailable lines or changed
acknowledged prices fail the whole request; nothing is silently removed or repriced.

The restaurant and branch must be ACTIVE, unpaused and open at the validation instant.
The existing timezone-aware weekly/special-hours expression is reused. There is no
scheduled-order exception. The saved address must be owned and have an active zone
in an active managed city. The branch-zone relationship must exist and be enabled.
Delivery fee and minimum come from that current row, not Discovery or prior Cart data.

Address-to-zone assignment remains customer-selected and is not geographically
verified. Serviceability proves platform configuration compatibility, not physical
geospatial truth. No map/geocoding provider was invented. Configured ETA is not
included in the Checkout confirmation and no live traffic estimate is claimed.

Money is BigDecimal, EGP and NUMERIC(12,2). Merchandise subtotal is the sum of current
effective unit price times quantity. The minimum is applied to merchandise alone.
Discount is 0.00 and final total is merchandise plus current delivery fee. Out-of-range
arithmetic is rejected with a controlled conflict. Promotions, taxes and other fees
remain absent.

Existing OrderService creates the PLACED Order, purchased-item/address snapshots and
initial USER history from the authenticated principal. Existing PaymentService derives
the amount from that persisted Order and creates a CASH/PENDING Payment and initial
history. No external call occurs. Cart conversion and receipt insertion then complete
the transaction. GET /cart returns 204 until a new item creates another active Cart.

## Transaction isolation and locking

Checkout and price reconfirmation use READ_COMMITTED locally. The rest of the
application's isolation and Hikari configuration are unchanged. The lock order is:

1. Customer row FOR UPDATE, then owned ACTIVE Cart FOR UPDATE.
2. Restaurant, branch and menu parents FOR SHARE.
3. Relevant categories and items in UUID order FOR SHARE; existing branch overrides
   in item order FOR SHARE; CartItems in UUID order FOR UPDATE.
4. Owned address FOR SHARE, then city, zone and branch-zone rule FOR SHARE.
5. Order/Payment creation, Cart conversion and successful receipt insertion.

Shared configuration locks allow different customers to use the same unchanged
restaurant configuration concurrently. Existing menu writers exclusively lock their
menu parent, including insertion/removal of branch overrides. Existing opening-hours
writers lock the branch parent, including absent special-hours rows. Those parent
locks cover changes that cannot be protected by locking an absent child row. Future
configuration writers must preserve these established parent-lock conventions.

When a writer wins first, Checkout waits and rereads its committed values under
READ_COMMITTED. When Checkout holds the locks first, writers wait until its immutable
purchase is committed. Tests cover both directions for price, availability and
serviceability changes. Cart changes use the existing customer-first lock order; an
old version cannot silently overwrite a consumed Cart. Database deadlock/concurrency
exceptions are translated to a safe 409 and the caller can retry. Unrelated customers
are not exclusively locked together; no SERIALIZABLE application-wide setting or
Java synchronized block is used.

## Errors and security

400 covers malformed bodies/keys and unknown fields; 401 covers unauthenticated
requests; 403 covers role/CSRF denial and the existing suspended-account policy.
Foreign/missing selected Cart or address identities use generic 404. Business and
concurrency conflicts return 409 with actionable codes such as STALE_CART, EMPTY_CART,
PRICE_RECONFIRMATION_REQUIRED, ITEM_UNAVAILABLE, BRANCH_NOT_ACCEPTING,
ADDRESS_ZONE_REQUIRED, NOT_SERVICEABLE, MINIMUM_ORDER_NOT_MET and IDEMPOTENCY_MISMATCH.
Existing Cart mutation privacy semantics can return 404 after the Cart is consumed.
Every unsuccessful transaction preserves its pre-request Cart and purchase state.

The security review and tests cover IDOR/BOLA, forged Cart/version/address identities,
unknown price/fee/total/status fields, role restrictions, CSRF, suspended sessions,
unsupported CARD, replay/key mismatch and duplicate consumption. No session cookie,
address details or provider secrets are logged by Checkout. Unexpected failures use
the existing generic error response and exception-type-only logging. There are no
card-data columns, card tokens, provider references for CASH or external payment calls.

## Query/performance review

Checkout uses targeted lookups for customer, active Cart, selected address, geography,
branch rule and receipt. Item locking is batched and effective revalidation is one
joined query capped at 101 to detect the 100-line limit; Order item writes use one
JDBC batch. Remaining aggregate writes and reads have fixed count independent of
the number of CartItems.

An integration comparison records 73 JdbcTemplate read-method invocations for both
one-line and five-line successful purchases. These include delegating overloads and
are not claimed as 73 SQL statements or a whole-system network query count. They
verify that read behavior does not grow per CartItem. Fixture-scale EXPLAIN output
uses `checkout_receipts_pkey` and the existing CartItems unique index. Evidence is in
`backend/target/checkout-query-review.txt`. This is not a load/capacity benchmark.
No Redis, Kafka, Elasticsearch or pool tuning was introduced.

## Verification

Targeted command: `backend/mvnw.cmd -B -ntp -Dmaven.repo.local=C:\Users\DEATHX7\.m2\repository -Dit.test=CheckoutIT verify`.
Full command: `backend/mvnw.cmd -B -ntp -Dmaven.repo.local=C:\Users\DEATHX7\.m2\repository verify`.

Full verification completed **2026-09-11 12:09:10 Africa/Cairo** in **02:53 min**,
with **BUILD SUCCESS**, Java **21.0.11** and PostgreSQL **18.6**.

| Suite | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| Unit/controller | 50 | 0 | 0 | 0 |
| Integration | 146 | 0 | 0 | 0 |
| **Total** | **196** | **0** | **0** | **0** |

CheckoutIT contributes 16 integration tests, all passing in the full run. Earlier
targeted verification passed 15 tests in 45.052 seconds at 08:26:08; the final full
run also includes the added writer-first concurrency case and fixed test clock.
The application JAR contains Checkout classes and V10 and excludes CheckoutIT.
Logs are `backend/target/checkout-targeted.log` and
`backend/target/checkout-verification.log`, with Surefire/Failsafe XML reports in
their standard target directories. Injected rollback failures intentionally produce
generic 500 log entries; they are passing negative tests.

Test coverage includes CASH snapshots/totals/histories, replay after source deletion and
later status changes, price reconfirmation and override changes, all availability and
serviceability failure classes, minimum calculation, security, key abuse, five
write-stage rollback injections, duplicate/different-key races, Cart quantity and
replacement races, and configuration writers in both lock acquisition orders.

## Changed files and remaining work

New production files: `checkout/CheckoutController.java`, `CheckoutDtos.java`,
`CheckoutException.java`, `CheckoutExceptionHandler.java`, `CheckoutService.java`,
`CheckoutStore.java`, `cart/CartValidationLocks.java`, and migration V10.
Modified production files: `cart/CartController.java`, `CartDtos.java`,
`CartService.java`, `CartStore.java`, and `auth/IdentitySecurityConfiguration.java`.
Tests: new `cart/CheckoutIT.java`; updated `BackendApplicationIT.java` migration count.
Documentation: this file and root README.

Receipts intentionally have no expiration/pruning policy yet; removing successful
receipts would weaken durable replay semantics and needs a reviewed retention plan.
Idempotency key format/length is bounded, but this module does not introduce a new
checkout-specific rate limiter. The existing authentication protections remain.
Operational role permissions, driver assignment/tracking, a selected card provider,
refund coordination, promotions, frontend UI, and geographic verification remain
future work. Stop at this checkpoint before restaurant operations.

Recommended Conventional Commit: `feat(checkout): add atomic idempotent cash checkout`
