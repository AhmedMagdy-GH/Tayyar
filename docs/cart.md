# Cart — Module 9 checkpoint

Status: implemented and verified; stopped at Module 9. Checkout, Orders, Payments,
delivery-address selection, promotions, taxes, platform fees, item options and
frontend UI are outside this module.

## Model and migration

`V8__create_customer_carts.sql` is the only new production migration. V1–V7 were
not edited. It creates `carts` and `cart_items` with UUID identities, foreign keys,
TIMESTAMPTZ timestamps, nonnegative BIGINT versions and constrained lifecycle/money/
quantity values.

A cart stores its authenticated customer, branch, derived restaurant context,
status and aggregate version. A cart item stores its cart, menu item, derived
restaurant context, quantity, last acknowledged server price and line version.
The repeated restaurant IDs are integrity keys, not client-editable domain fields:
composite foreign keys prove that the cart branch and every menu item belong to one
restaurant. Database triggers prohibit retargeting cart/customer/branch/item context.

`carts_one_active_customer_idx` is a PostgreSQL partial unique index on customer ID
where status is ACTIVE. This is the final database guarantee that a customer cannot
have two active carts. `carts_customer_history_idx` supports retained-cart history.
`UNIQUE(cart_id,menu_item_id)` provides deterministic one-line-per-item behavior and
an index for cart loading. Quantity is 1–99 in validation and a CHECK constraint.
Acknowledged price is `NUMERIC(12,2)`, nonnegative and non-NaN.

## Lifecycle and single-branch behavior

Carts are created lazily by the first successful item addition. `GET /cart` returns
204 when no active cart exists. ACTIVE carts may become ABANDONED when explicitly
cleared or when their last line is removed, REPLACED through explicit branch
replacement, or future CHECKED_OUT when an Order module exists. Retired carts and
their lines are preserved; active aggregates are never destructively retargeted.

An item addition for the current branch adds a new line or increments the existing
identical line. A request for another branch returns 409 and tells the client to use
explicit replacement. `POST /cart/replace` atomically retires the old cart as
REPLACED, creates a cart for the requested branch and adds its first validated line.
It never silently clears or switches a cart. Replacement requires a different
branch and an existing active cart.

## API

All paths are relative to `/api/v1`. Every endpoint requires an authenticated
CUSTOMER. Mutating requests use the existing session CSRF policy.

| Method | Path | Contract |
| --- | --- | --- |
| GET | `/cart` | Current active cart, or 204 |
| POST | `/cart/items` | Lazily create/add/increment; 201 for creation, otherwise 200 |
| PATCH | `/cart/items/{cartItemId}` | Replace quantity using cart/item concurrency tokens |
| DELETE | `/cart/items/{cartItemId}` | Remove owned line; returns cart or 204 when retired |
| DELETE | `/cart` | Retire active cart as ABANDONED |
| POST | `/cart/replace` | Explicit atomic cross-branch replacement |

First addition:

```json
{"branchId":"<branch UUID>","menuItemId":"<item UUID>","quantity":1}
```

Addition to an existing cart includes both identity and version:

```json
{
  "branchId":"<branch UUID>",
  "menuItemId":"<item UUID>",
  "quantity":1,
  "cartId":"<active cart UUID>",
  "cartVersion":0
}
```

Quantity replacement adds `itemVersion`. Explicit replacement uses the same five
fields as an existing-cart addition. Line deletion requires `cartId`, `cartVersion`
and `itemVersion` query parameters. Cart deletion requires `cartId` and `version`.
Unknown/repeated query parameters, unknown JSON fields and malformed IDs/versions
are rejected. No customerId, restaurantId, price, subtotal or availability input is
accepted.

Cart IDs accompany versions to prevent an ABA race: a delayed request for a retired
cart cannot mutate a newly created cart whose version also begins at zero.

## Price, availability and totals

New lines obtain their acknowledged unit price from the existing effective menu
expression: branch price override, otherwise base item price. Clients cannot submit
price. Reads batch all lines with current menu/category/item/override state and show:

- acknowledged and current unit price;
- `priceChanged` based on decimal comparison;
- current effective availability;
- current unit price × quantity line subtotal;
- sum of current line subtotals as merchandise subtotal.

Adding the same item again or changing quantity does not silently overwrite its
acknowledged price. A later menu change therefore remains visible until a future
explicit price-confirmation/Checkout design updates the acknowledgement. Checkout
must reread authoritative prices and reject stale acknowledgements before ordering.

New additions require an ACTIVE restaurant/branch and an effectively available
menu/category/item. Branch overrides cannot activate inactive ancestors because the
shared effective expression gates all levels. Existing carts survive later price,
availability, pause, branch-status or restaurant-status changes. Reads report the
current line availability and separate `OPEN`, `CLOSED`, `PAUSED`, `INACTIVE` or
`RESTAURANT_SUSPENDED` branch state. No cart is bound to an address and no delivery
fee, minimum order, tax, fee, discount or final order total is calculated.

Item customizations remain deferred. When options are introduced, line identity must
be expanded from menu item alone to a normalized configuration identity (or stable
configuration hash), with authoritative option pricing and availability included in
the acknowledged/current comparison. V1 intentionally permits one plain line per
menu item.

## Ownership, transactions and concurrency

Only the session principal determines customer ownership. Routes never accept a user
ID or expose `/users/{userId}/cart`. Every lookup and mutation starts from the
authenticated customer's active cart; a forged line ID is scoped to that cart and
returns generic 404. ADMIN, OWNER, STAFF and DRIVER roles receive no implicit cart
access. Existing session/account-status enforcement applies.

Writes lock the authenticated user row, then the active cart and affected line in a
fixed order. This serializes first creation and cart changes for one customer without
blocking another customer. Cart identity/version and line version checks return 409
for stale state. The partial unique index independently prevents duplicate active
carts. Create+first-line and retire+replacement+first-line are single transactions.
An injected failure after old-cart retirement proved that replacement rolls back to
the original active cart with no partial successor.

Concurrent first additions produce one 201 and one 409. Concurrent stale quantity
updates produce one 200 and one 409 without a lost update. Concurrent cross-branch
replacements produce one 201 and one 409, two retained carts total and exactly one
active cart. No external call occurs inside a transaction.

## Query and index review

A cart read uses two SQL statements independent of line count: one active-cart/
branch/schedule header and one joined item/menu/category/override line read, capped
at 100 lines. There is no per-item or per-override query. Fixture-scale PostgreSQL 18
`EXPLAIN (ANALYZE, BUFFERS)` output is recorded at
`backend/target/cart-explain.txt`; this is plan evidence, not a load benchmark.

The header uses `carts_one_active_customer_idx`, branch/restaurant primary keys and
opening-hours primary keys. Lines use the cart-item unique index plus menu/category/
override keys. Mutations use the same ownership/index paths and bounded counts. No
Redis, Hikari tuning, external calls or capacity claim was introduced.

## Verification

Targeted command:
`backend/mvnw.cmd -B -ntp -Dmaven.repo.local=C:\Users\DEATHX7\.m2\repository -Dit.test=CartIT verify`.
Completed **2026-09-11 07:42:15 Africa/Cairo**, total time **36.677 s**,
**BUILD SUCCESS**. CartIT: 16 tests, 0 failures/errors/skips. Unit/controller
suite: 43 tests, 0 failures/errors/skips.

Full command:
`backend/mvnw.cmd -B -ntp -Dmaven.repo.local=C:\Users\DEATHX7\.m2\repository verify`.
Completed **2026-09-11 07:44:53 Africa/Cairo**, total time **02:18 min**,
**BUILD SUCCESS**. PostgreSQL **18.6**, Java **21.0.11**. No tests were skipped.

| Suite | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| Unit/controller tests | 43 | 0 | 0 | 0 |
| Integration tests | 117 | 0 | 0 | 0 |
| **Total** | **160** | **0** | **0** | **0** |

Module 9 adds 4 unit tests and 16 integration tests. The packaged application
contains production Cart classes and V1–V8 migrations and excludes Cart test
classes. Logs are `backend/target/cart-targeted.log` and
`backend/target/cart-verification.log`; reports are under the Maven Surefire and
Failsafe report directories. The intentional replacement rollback test produces a
generic 500 log entry while proving the original cart remains active.

## Security review and remaining risks

Tests exercise IDOR/BOLA resistance, forged cart/line/branch/item IDs, cross-
restaurant injection, mass assignment and price injection, quantity overflow,
unknown/repeated parameters, stale identities/versions, anonymous/non-customer
denial, CSRF and suspended-session invalidation. Database probes cover duplicate
active carts, cross-context foreign keys, immutable identities, quantity and money
checks. This is a scoped code/integration review, not penetration-test certification.

The cart is intentionally not a reservation. Checkout must revalidate the current
restaurant, branch, item, price, availability, delivery zone/rule, fee, minimum and
ETA. Retained abandoned/replaced carts have no pruning/archive job yet. Cart options,
explicit price reconfirmation, address selection and order conversion remain future
schema/API work.

Recommended Conventional Commit:
`feat(cart): add single-branch customer carts`
