# Delivery Zones and Serviceability — Module 7 checkpoint

Status: implemented and verified; stopped at Module 7.

Scope: managed geography, branch delivery rules, private address-zone selection and
server-side serviceability. Discovery, Cart, Checkout, orders, frontend, maps,
geocoding, polygons, routing, PostGIS and Redis are outside this module.

## Model and migration

`V7__create_delivery_zones.sql` is the new production migration. V1–V6 were not
edited. It creates `cities`, `delivery_zones` and `branch_delivery_zones`, and adds
nullable `customer_addresses.delivery_zone_id`. Existing addresses retain their
profile, coordinates, ownership, default flag and version; their zone starts null.
No automatic backfill from legacy city text or coordinates is attempted.

City → DeliveryZone → BranchDeliveryZone is a managed, named-area model. A zone
belongs to exactly one city. A branch serves many zones and a zone supports many
branches. Delivery fees, minimum orders, ETA and enabled state belong exclusively
to the branch-zone relationship. A zone's city and a relationship's branch/zone
identity are immutable (JPA fields plus database triggers).

All new tables have UUID primary keys, required fields, nonnegative BIGINT versions
and TIMESTAMPTZ created/updated timestamps. Foreign keys have no destructive
cascades. City/zone names are bounded to 100 characters. Database uniqueness ignores
case, whitespace and ASCII hyphens, globally for cities and within a city for zones.
Thus `Nasr City`, `NasrCity`, `nasr city` and `Nasr-City` share an identity. Display
names retain their spelling after outer trimming. Separator-only names are invalid.
This deliberately conservative normalization is not fuzzy matching, transliteration
or an alias registry; admins must still curate Arabic/English spelling variants.
Geography is initially for the EGP marketplace, not a worldwide country registry.

JPA entities map all new tables and the address reference using scalar UUID foreign
keys, consistent with earlier modules. Hibernate validates only; Flyway remains the
schema writer. No pool, database credentials, session or cache settings changed.

## APIs

All paths below are relative to `/api/v1`; existing sessions and CSRF apply.

| Method | Path | Contract |
| --- | --- | --- |
| GET / POST | `/admin/cities` | List / create city |
| GET / PUT | `/admin/cities/{id}` | Read / replace name and active status |
| GET / POST | `/admin/delivery-zones` | List by required `cityId` / create zone |
| GET / PUT | `/admin/delivery-zones/{id}` | Read / replace name and active status |
| GET | `/geography/cities` and `/{id}` | Read managed geography for selection |
| GET | `/geography/delivery-zones?cityId=…` and `/delivery-zones/{id}` | Read managed zones |
| GET | `/restaurants/{restaurantId}/branches/{branchId}/delivery-zones` | List branch rules |
| GET / POST / PUT | `/restaurants/{restaurantId}/branches/{branchId}/delivery-zones/{zoneId}` | Read / create / replace rule |
| PUT | `/users/me/addresses/{id}/delivery-zone` | Select or clear own address zone |
| GET | `/branches/{branchId}/serviceability?addressId=…` | Resolve eligibility for own saved address |

Lists return `{items,page,size,total}`, with page 0 and size 20 defaults, maximum
size 100 and maximum page 10000. Ordering is deterministic by UUID. Geography reads
include inactive definitions with explicit active flags; selectors should present
active city/zone combinations. An inactive city also makes its zones ineligible.
DTOs are returned, never entities. Missing or inaccessible resources return 404;
invalid data 400; duplicate definitions/relationships and stale versions 409.
Create returns 201. Updates use PUT full replacement rather than ambiguous partial
patch semantics. No DELETE endpoints are offered for geography or branch rules.

City create:

```json
{"name":"Cairo","active":true}
```

Zone create:

```json
{"cityId":"<city UUID>","profile":{"name":"Nasr Street","active":true}}
```

Geography replacement:

```json
{"profile":{"name":"Nasr Street","active":false},"version":0}
```

Branch rule create:

```json
{"deliveryFee":25.00,"minimumOrder":100.00,"etaMinMinutes":30,"etaMaxMinutes":45,"enabled":true}
```

Rule replacement wraps the same fields in `rule` and supplies the last returned
`version`. Every successful replacement, including identical content, advances
that rule's version. Rule views include IDs, currency, rule fields, version and
created/updated timestamps. No editable actor/owner/currency fields exist.

Address selection:

```json
{"deliveryZoneId":"<zone UUID>","version":0}
```

Explicit null clears the reference. New selections require an active zone and city.
The address view now includes top-level `deliveryZoneId`; existing profile create
and PUT payloads are unchanged. Profile updates preserve the selected zone. The
selection endpoint uses the same customer lock and address version as other address
mutations, and never changes default status or ownership. A failed selection leaves
the previous zone and version intact. Existing address deletion behavior remains.

## Serviceability and authoritative rules

Resolution checks address ownership and branch existence in one joined database
statement. Missing branches and inaccessible addresses produce a generic 404. For
existing owned addresses/branches it returns `serviceable`, `reason`, branch/zone IDs,
currency and (only when serviceable) authoritative delivery fee, minimum order and
ETA. Failure reasons have this deterministic precedence:

1. `ADDRESS_ZONE_REQUIRED`
2. `RESTAURANT_SUSPENDED`
3. `BRANCH_INACTIVE`
4. `BRANCH_PAUSED`
5. `CITY_INACTIVE`
6. `ZONE_INACTIVE`
7. `ZONE_NOT_SERVED`
8. `RELATIONSHIP_DISABLED`
9. Otherwise `SERVICEABLE`.

Branch inactivity and operational pause are respected. Branches have no separate
suspended status in the existing model; restaurant suspension disables eligibility.
Opening hours are deliberately not evaluated. `serviceable=true` describes area
coverage subject to operational status, not whether the branch is open right now.
The existing opening-hours facility remains separate. No address text, coordinates,
customer identity or other private address components appear in this response.

Money is EGP, BigDecimal / NUMERIC(12,2), with values from 0 through 9,999,999,999.99.
API/domain validation rejects negative amounts, excess precision and more than two
fractional digits. Database checks reject negative/nonfinite/overflow amounts.
PostgreSQL NUMERIC coercion rounds excessive fractional digits in privileged direct
SQL before checks; API validation rejects such submissions instead of rounding.
Customer query parameters cannot set or override the configured price.

**Minimum order means merchandise/item subtotal before delivery fee. Delivery fee
does not count toward it.** This module reports the rule; Cart/Checkout enforcement
is not implemented. Future Checkout must reevaluate current authoritative rules.
ETA uses positive integer minutes; maximum must be at least minimum. This is a
configured estimate, not traffic prediction, geocoding or live routing.

## Authorization and retention

ADMIN manages global geography. ADMIN and RESTAURANT_OWNER manage branch rules;
owners must pass the existing restaurant ownership check and branch-within-restaurant
check on every read/create/update. An arbitrary restaurantId or branchId cannot
redirect configuration. Global zones carry no user-editable owner identity.

CUSTOMER, OWNER and ADMIN can read the managed geography catalog. Only CUSTOMER
can query serviceability for their own address. ADMIN has no address bypass, including
an ADMIN+CUSTOMER account. STAFF and DRIVER gain no delivery-rule/geography management
permission. URL guards and service method role guards both apply. Acting users are
resolved from SessionPrincipal. Existing unknown-body-field rejection, CSRF,
account/session invalidation, generic error handling and bound SQL parameters apply.

Deactivation preserves definitions, branch rules and address references. Disabled
rules can be reenabled by a versioned replacement. Inactive geography may be
configured in advance, but cannot become serviceable or be newly selected on an
address until its city and zone are active. There is no full audit/event history or
hard-delete API. Foreign keys prevent deleting referenced records via direct SQL.

## Transactions and concurrency

Each change is transactional. Geography and branch-rule replacements lock their
record with a database pessimistic write lock, compare the submitted version, then
use JPA @Version and flush to advance it. Concurrent stale replacements return 409
instead of silently overwriting; unrelated branch-zone pairs do not share a lock.
Concurrent creates race against database unique constraints; one succeeds and the
other returns 409. A duplicate rename also returns 409 and rolls back its version.

Address zone selection uses the established owning-user lock and address version.
It does not acquire a branch or geography write lock. A concurrent deactivation can
occur after selection; the next eligibility query always checks current active
states. Configuration remains editable while a restaurant/zone is inactive so it
can be prepared safely; editing never bypasses eligibility status checks.

Serviceability uses a single READ COMMITTED statement snapshot. The result is not
a reservation or guarantee across subsequent transactions. No locks are held for
customers to place a future order. Direct SQL writes must honor application locking
and versioning conventions; ordinary runtime endpoints enforce those conventions.

## Performance and database observations

The unique `(branch_id,delivery_zone_id)` index supports exact rule lookup and
branch-prefix listing. `(delivery_zone_id,branch_id)` supports reverse zone lookups.
The zone unique index starts with city_id, supporting city filtering. A partial
address-zone index covers nonnull references. Primary keys support single-resource
lookups; no broad indexing of fees, flags or address text was added.

Serviceability performs one joined scalar query, avoiding JPA lazy loads. An internal
`ServiceabilityQuery.forZone` method accepts up to 100 branches and evaluates them
in one statement. It is not a Discovery endpoint; future callers must apply their
own visibility/authorization rules and consume its snapshot semantics. Unknown
branches (or an unknown zone) yield no batch row. Lists are bounded; counts and page
contents may observe different committed snapshots. No production EXPLAIN, load
benchmark or throughput claim is made. No Redis or Hikari tuning was introduced.

## Verification

Full command: `backend/mvnw.cmd -B -ntp -Dmaven.repo.local=C:\Users\DEATHX7\.m2\repository verify`.
Completed **2026-09-11 01:31:17 Africa/Cairo**, total time **02:27 min**, **BUILD SUCCESS**.
PostgreSQL version **18.6**, Java **21.0.11**. No tests were skipped.

| Suite | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| AddressRulesTests | 4 | 0 | 0 | 0 |
| IdentityRulesTests | 5 | 0 | 0 | 0 |
| BranchRulesTests | 7 | 0 | 0 | 0 |
| ApiExceptionHandlerTests | 6 | 0 | 0 | 0 |
| DeliveryRulesTests | 4 | 0 | 0 | 0 |
| HealthControllerTests | 1 | 0 | 0 | 0 |
| MenuRulesTests | 4 | 0 | 0 | 0 |
| RestaurantRulesTests | 4 | 0 | 0 | 0 |
| AddressesIT | 14 | 0 | 0 | 0 |
| IdentityIT | 12 | 0 | 0 | 0 |
| BackendApplicationIT | 5 | 0 | 0 | 0 |
| BranchesIT | 13 | 0 | 0 | 0 |
| DeliveryZonesIT | 13 | 0 | 0 | 0 |
| MenuIT | 14 | 0 | 0 | 0 |
| RestaurantsIT | 14 | 0 | 0 | 0 |
| **Total** | **120** | **0** | **0** | **0** |

35 unit/controller and 85 integration tests passed. Module 7 adds 17 tests.
Concurrent rule updates returned one 200 and one 409; concurrent creates returned
one 201 and one 409. The rollback test flushed fee 99 into its transaction, observed
that value, then injected an exception; the subsequent transaction saw fee 25 and
version 0. The resulting generic 500 log is expected. Existing modules also log
intentional error-injection/constraint cases during their successful regressions.

An earlier targeted verify passed 35 unit/controller plus 17 integration tests.
Review then strengthened the rollback test to prove an actual flush, and added one
security/read test before the final full verify. Initial Maven attempts encountered
sandbox/cache access errors; using the existing dependency cache with reviewed
execution permission resolved them without changing project dependencies.
Source formatting used the already-present target/google-java-format.jar. A package
refresh passed after formatting (BUILD SUCCESS, 10.312 seconds); the full regression counts above are from verify, not
the subsequent packaging-only command. Packaged contents include production delivery
classes and V1–V7, and exclude module test classes. The post-run Docker label query
found no remaining Testcontainers containers.

Logs: `backend/target/delivery-verification.log`, `delivery-targeted.log`,
`delivery-unit.log`, `delivery-package.log`; XML/text reports are in
`backend/target/surefire-reports` and `backend/target/failsafe-reports`.

Security review found no authorization bypass in the exercised routes: owner scope,
branch/restaurant identity, self-address privacy, role/CSRF/session guards and fee
injection behavior passed. This is a scoped implementation review, not a penetration
test certification. Customer-selected area accuracy remains the principal domain risk.

Tests use the existing
PostgreSQL 18 Testcontainers fixture, restricted runtime role and Flyway migration
account, never a personal/production database. The module tests exercise geography
creation/normalization, role/session/CSRF denials, ID manipulation, DTO injection,
many-to-many relationships, money and ETA boundaries, address ownership, all
unavailable reasons, concurrent creates/edits, rollback after a real database flush,
foreign keys, immutable references and database uniqueness/check constraints.

## Remaining risks and technical debt

- Managed zone selection is customer asserted. It does not verify physical address
  correctness; legacy city text may differ. Coordinate/radius/polygon strategies can
  replace the resolution query later without changing Restaurant/Branch identity.
- Existing addresses need an explicit zone selection before they are serviceable.
- Static fees and ETA are snapshots; future Checkout must recalculate, enforce the
  merchandise minimum, check opening hours and snapshot delivery data for orders.
- Naming normalization intentionally merges spacing/hyphen variants; bilingual
  synonyms, aliases and international geography require later design.
- No full historical revisions or audit ledger; deactivation preserves current
  configuration only. No load benchmarks or production data migration performed.
- Production schema inspection and reviewed runtime/migrator grants remain required.

## Changed-file manifest

New production package `backend/src/main/java/com/tayyar/delivery/`:

- BranchDeliveryZone.java, BranchDeliveryZoneRepository.java
- City.java, CityRepository.java
- DeliveryZone.java, DeliveryZoneRepository.java
- DeliveryRecord.java, DeliveryRules.java, DeliveryDtos.java
- DeliveryService.java, DeliveryController.java
- DeliveryException.java, DeliveryExceptionHandler.java
- ServiceabilityQuery.java, ServiceabilityService.java

Other new files:

- backend/src/main/resources/db/migration/V7__create_delivery_zones.sql
- backend/src/test/java/com/tayyar/delivery/DeliveryRulesTests.java
- backend/src/test/java/com/tayyar/delivery/DeliveryZonesIT.java
- docs/delivery-zones.md

Modified:

- backend/src/main/java/com/tayyar/address/AddressDtos.java
- backend/src/main/java/com/tayyar/address/AddressController.java
- backend/src/main/java/com/tayyar/address/AddressService.java
- backend/src/main/java/com/tayyar/address/CustomerAddress.java
- backend/src/main/java/com/tayyar/auth/IdentitySecurityConfiguration.java
- backend/src/test/java/com/tayyar/BackendApplicationIT.java
- README.md

Generated verification logs, reports and application JAR remain under backend/target.
No Git/GitHub operations were performed and no commit was created.

Recommended Conventional Commit:
`feat(delivery): add managed zones and branch serviceability`


