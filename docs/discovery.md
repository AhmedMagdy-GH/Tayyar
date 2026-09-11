# Discovery and Search — Module 8 checkpoint

Status: implemented and verified; stopped at Module 8. Cart, Checkout, ordering,
frontend UI, recommendations, ratings, promotions, distance/routing, Redis and
Elasticsearch are outside this module.

## Public read model and APIs

Discovery has dedicated read DTOs and JDBC projections under `/api/v1/discovery`.
This keeps the existing `/api/v1/restaurants/**` management API and its owner/admin
authorization unchanged. Discovery never serializes persistence entities,
applications, memberships, owners, staff, customer addresses, versions, audit
timestamps or administrative status history.

All collections use `{items,page,size,total}`. Page defaults to 0, size to 20,
maximum page is 10000 and maximum size is 100. Sort values are uppercase enum values.
Unknown, repeated, malformed or oversized query parameters return 400.

| Method | Path | Behavior |
| --- | --- | --- |
| GET | `/discovery/restaurants` | Search/filter/sort active customer-visible restaurants |
| GET | `/discovery/restaurants/{restaurantId}` | Read one visible restaurant card |
| GET | `/discovery/restaurants/{restaurantId}/branches` | Return available branch choices; no implicit branch selection |
| GET | `/discovery/restaurants/{restaurantId}/branches/{branchId}/menu` | Return the branch's active effective menu |
| GET | `/discovery/delivery-zones` | Public, active city/zone identifiers for location selection |

Restaurant and branch query parameters are `query`, `zoneId`, `addressId`,
`openNow`, `maxDeliveryFee`, `maxMinimumOrder`, `categoryId`, `availableOnly`,
`sort`, `page` and `size`. `zoneId` and `addressId` are mutually exclusive.
Delivery filters and `DELIVERY_FEE`, `MINIMUM_ORDER` or `ETA` sorting require a
location. `NAME` is the default sort. Money filters use nonnegative EGP amounts
below 10,000,000,000 with at most two decimals.

The menu uses `page`/`size` for categories and `itemPage`/`itemSize` for each
returned category. Both dimensions are independently bounded. `availableOnly=true`
omits effectively unavailable items. Zone catalog filtering accepts `cityId` plus
the standard page parameters.

## Restaurant and branch behavior

Only ACTIVE restaurants with at least one ACTIVE branch are visible. Suspended
restaurants and inactive branches are not enumerable through Discovery. Paused and
currently closed active branches remain visible without a serviceability filter so
customers can browse them. Each branch reports `OPEN`, `CLOSED` or `PAUSED` and an
independent `openNow` boolean. Weekly and special hours use the established branch
timezone and special-date precedence; opening is inclusive and closing is exclusive.

A restaurant card contains its ID, public name and description, matching branch
count, aggregate current opening state, and location-derived fee/minimum/ETA
summaries when a location is supplied. No logo/image field exists in the current
schema, so Discovery does not invent one.

For location-aware reads, only branches that satisfy the authoritative delivery
eligibility rules are included. Every serving branch is returned in deterministic
order. Discovery does not call one branch nearest, best or representative because
distance/routing and a product ranking rule do not exist. A restaurant's summary
delivery values are the individual minima across its eligible, filter-matching
branches; clients must open the branch collection for a coherent branch quote.

## Search, filtering and sorting

PostgreSQL performs all search and filtering. `query` is stripped of surrounding
Unicode whitespace, bounded at 200 Java characters, and matched case-insensitively
against restaurant, active category and active item names. Empty/whitespace-only
queries mean browse-all. `%`, `_` and the escape character are escaped, so they are
literal user text rather than SQL wildcard controls. Bound parameters prevent SQL
injection. Search is substring matching with `ILIKE`; it provides no fuzzy matching,
stemming, transliteration or typo tolerance.

Category and availability filters are evaluated by correlated PostgreSQL `EXISTS`
queries for the same restaurant and branch. When both fee and minimum filters are
present, one eligible branch must satisfy both; values are not combined across
different branches. `openNow` is independent of geographic serviceability. Sorting
uses a server enum and always adds UUID as a stable secondary key. Arbitrary column
or expression input is rejected.

## Effective branch menu

The response is Menu → paged Categories → independently paged Items. Only an active
menu and active categories/items are exposed. Each item contains public identity,
name/description, `effectivePrice` and `effectiveAvailability`. Effective values use
the shared expressions also used by the management menu projection:

```text
effectivePrice        = COALESCE(branch override price, base price)
effectiveAvailability = menu active AND category active AND item active
                        AND COALESCE(branch override availability, base availability)
```

Header, category count, bounded category page and batched per-category item windows
take at most four SQL statements. The item statement uses window ranking to avoid a
query per category or item, including for empty and out-of-range pages.

## Serviceability and privacy

Public callers may filter using an active public `zoneId`. `addressId` requires a
valid authenticated CUSTOMER session. Discovery resolves the zone only when the
address belongs to the current user. Another customer's or an unknown address
returns the same generic 404. Anonymous address use returns 401, and non-customer
roles return 403. ADMIN, including ADMIN+CUSTOMER, gains no address ownership bypass.
No address ID, text, coordinates or customer identity is returned.

The shared delivery SQL preserves the established precedence for restaurant,
branch, pause, city, zone, relationship existence and relationship enabled state.
Eligible results obtain fee, minimum order and configured ETA exclusively from
`branch_delivery_zones`; client-supplied quote fields are not accepted. Discovery
reads use REPEATABLE READ so count and content statements see one transaction
snapshot.

Address-to-zone selection remains customer asserted and geographically unverified.
Discovery output is informational, not a reservation or checkout quote. Future
Cart/Checkout must revalidate restaurant/branch/zone/rule state, price, availability,
fee, minimum order and ETA at the appropriate transactional boundary.

## Cuisine decision

The Restaurant, Menu and Delivery schemas contain no cuisine field, relation or
taxonomy. Module 8 therefore does not expose a fake cuisine filter or silently add
a taxonomy. Product must decide whether restaurants can have multiple cuisines,
who curates them, localization/aliases and whether cuisine is a controlled taxonomy
or normalized tags before a migration and API contract are introduced.

## Query and index review

Restaurant listing, search and zone-aware listing each use one count and one bounded
content statement. They join restaurant → branch once, evaluate branch hours in SQL,
join the optional branch-zone rule once, and aggregate cards. The menu uses four
statements independent of category/item count. There are no per-restaurant,
per-branch, per-category or per-item query loops.

Fixture-scale PostgreSQL 18 `EXPLAIN (ANALYZE, BUFFERS)` output is recorded at
`backend/target/discovery-explain.txt`. Plans use existing indexes including
`restaurants_status_idx`, `branches_restaurant_idx`,
`branch_delivery_zones_zone_idx`, opening-hours primary keys, the menu/category/item
ordering unique indexes and branch/item override primary key. V1–V7 were not edited
and no V8 migration was needed.

Leading-wildcard `ILIKE` cannot use ordinary B-tree name indexes. At v1 scale this
keeps behavior simple and avoids an extension, normalized duplicate columns or an
external search service. Search work grows with visible restaurant/menu text and
count queries must inspect all matches. Before a materially larger catalog, measure
production-like data and consider a reviewed PostgreSQL full-text or `pg_trgm`
strategy with query-specific indexes. The plan file is fixture evidence, not a load
benchmark or concurrency-capacity claim.

## Verification

Targeted command:
`backend/mvnw.cmd -B -ntp -Dmaven.repo.local=C:\Users\DEATHX7\.m2\repository -Dit.test=DiscoveryIT verify`.
Completed 2026-09-11 07:03:30 Africa/Cairo: BUILD SUCCESS. PostgreSQL 18.6,
Java 21.0.11. DiscoveryIT: 16 tests, 0 failures/errors/skips. Unit/controller suite:
39 tests, 0 failures/errors/skips.

Full command:
`backend/mvnw.cmd -B -ntp -Dmaven.repo.local=C:\Users\DEATHX7\.m2\repository verify`.
Completed **2026-09-11 07:07:08 Africa/Cairo**, total time **02:04 min**,
**BUILD SUCCESS**. PostgreSQL **18.6**, Java **21.0.11**. No tests were skipped.

| Suite | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| Unit/controller tests | 39 | 0 | 0 | 0 |
| Integration tests | 101 | 0 | 0 | 0 |
| **Total** | **140** | **0** | **0** | **0** |

Module 8 adds 4 unit tests and 16 integration tests. The packaged application
contains the production Discovery classes and V1–V7 migrations and excludes module
test classes. Full logs are at `backend/target/discovery-verification.log`; targeted
logs are at `backend/target/discovery-targeted.log`; XML/text reports are under
`backend/target/surefire-reports` and `backend/target/failsafe-reports`.

## Security review and remaining risks

Tests cover anonymous browsing, private-field exclusion, cross-customer address
denial, anonymous/non-customer saved-address denial, suspended-account enforcement,
invalid UUIDs, unknown/repeated/oversized parameters, malformed pagination and money,
sort-field injection, SQL-injection-style input and literal SQL wildcards. Existing
session security and safe-GET CSRF behavior remain in place. This is a scoped code
and integration review, not a penetration-test certification.

Known debt is the unverified user-selected zone, linear substring-search scaling,
lack of cuisine modeling, no images in the schema, and aggregate restaurant delivery
summaries that still require explicit branch choice. No Redis, Elasticsearch, new
extension, pool tuning, frontend code, Cart or Checkout was added.

Recommended Conventional Commit:
`feat(discovery): add public restaurant search and branch menus`
