# Customer Addresses design and checkpoint

Status: implemented and verified; design recorded before implementation. Stopped at Customer Addresses.

Saved addresses belong to the authenticated CUSTOMER. No arbitrary-user routes or
ADMIN bypass. UUID ownership is immutable. Fields: custom label, street, building,
optional floor/apartment/landmark/instructions, city, optional region/postal code,
two-letter country code, optional paired decimal coordinates, default flag, timestamps
and optimistic version. No provider, serviceability, Cart, Checkout or Order tables.

The first saved address is default; additional addresses are not. A dedicated default
selection replaces the old default atomically. A partial unique user index enforces
at most one default. Deleting the default leaves none; no other address is chosen
implicitly. All mutations lock the owning user row, and updates/deletes/default
selection check the addressed row's version. Concurrent selections of different
nondefault addresses serialize; the last successful selection wins. Clearing an old
default also advances that address's version, preventing stale profile edits.

Saved-address removal is hard deletion: no current historical references exist.
Future orders must snapshot all relevant components/instructions/coordinates and
must not depend on mutable saved-address rows. Profile PUT is full replacement;
owner/default/version fields cannot be mass-assigned. Reads are paginated (20 default,
100 maximum); each customer may save at most 100 addresses to bound account storage.

V6 adds customer_addresses, immutable-owner trigger, ownership/order index and unique
default index. JPA owns entity state/version and validates mappings; JDBC clears the
previous default within the same transaction. Flyway alone creates production schema.

## Domain, database and retention

`V6__create_customer_addresses.sql` is the only new production migration. V1–V5 are
unchanged. It adds a UUID primary key, user foreign key, structured bounded fields,
decimal coordinate pair, default flag, version and `TIMESTAMPTZ` created/updated times.
A trigger prohibits owner reassignment; checks validate coordinate ranges/pairing,
required nonblank components, country-code shape and nonnegative version. Hibernate
continues to validate mappings only, with Open Session in View disabled.

Labels are custom text; duplicate labels are allowed. Label, street, building and city
are trimmed and required. Optional fields can be null. Country code must be two
uppercase letters (shape validation, not a country registry lookup). No address
geocoding, postal verification, maps provider or geographic serviceability is implied.
Coordinates are both absent or both present: latitude -90..90, longitude -180..180,
at most six fractional digits, stored as NUMERIC(9,6). They are not exposed to other
customers, restaurants, staff or drivers through this module.

Hard deletion is chosen because no current table depends on a saved address. It removes
the saved row only. Future checkout must copy street/building/floor/apartment/city/
region/postal/country, landmark, instructions and coordinates into an immutable order
snapshot. Historical orders must not obtain delivery data by dereferencing a saved
address that can change or disappear. No Order tables or cascades are added here.

## Ownership and default behavior

Every resource lookup includes `id` and the authenticated `user_id`. Route and service
guards require CUSTOMER. An ADMIN, DRIVER, OWNER or STAFF without CUSTOMER cannot
use these routes. A multi-role user with CUSTOMER may manage only their own addresses;
ADMIN provides no bypass. The API neither accepts nor returns an owner ID as editable
profile data and exposes no `/users/{userId}/addresses` routes.

The first address created when the customer has zero addresses becomes default.
Subsequent creates are nondefault, even if a previous default was deleted. Selecting
another default clears the previous flag and advances both changed rows' versions
within one transaction. Selecting the current default is allowed and advances its
version. Deleting a nondefault leaves the current default untouched; deleting the
default leaves none. After all addresses are removed, a new first address is default.

The partial unique index `(user_id) WHERE is_default` enforces **at most one**, not
exactly one. Clients explicitly choose a replacement when none exists.

## API contract

Base: `/api/v1/users/me/addresses`. Existing secure session cookies and CSRF apply.

| Method / suffix | Body / result |
| --- | --- |
| GET base | Own address page: `{items,page,size,total}` |
| POST base | Profile body; 201 with Location and address view |
| GET `/{id}` | Own address view |
| PUT `/{id}` | `{profile,version}`; full profile replacement |
| DELETE `/{id}` | `{version}` JSON body; 204 |
| PUT `/{id}/default` | `{version}`; selected address view |

Profile example:

```json
{
  "label": "Home",
  "street": "Example Street",
  "building": "12A",
  "floor": "3",
  "apartment": "7",
  "landmark": "Beside the pharmacy",
  "instructions": "Ring the bell once",
  "city": "Cairo",
  "region": "Cairo",
  "postalCode": null,
  "countryCode": "EG",
  "latitude": 30.044400,
  "longitude": 31.235700
}
```

Limits: label/building 80, street/landmark 200, floor/apartment 40, instructions 1000,
city/region 100, postalCode 20. Profile PUT replaces all profile fields; omitted optional
fields become null. It cannot change owner/default state. Responses contain `id`,
`profile`, `isDefault`, `version`, `createdAt`, `updatedAt`, never a JPA entity.

Page defaults: page 0, size 20; maximum size 100, page range 0–10000. Customers may
save at most 100 addresses. Unknown fields/invalid values return 400, inaccessible
or missing IDs 404, and stale versions 409. Missing/invalid CSRF on mutations returns
403; unauthenticated requests with valid CSRF return 401. Client versions must come
from the latest address representation. Changes to another default can stale a
previously loaded address even if its profile fields did not change.

## Transactions and concurrency

Each write locks the owning `users` row before reading count/address/default state.
All address mutations for one customer serialize; different customers do not contend
on a shared application lock. The target row's JPA version prevents stale edits,
deletes and default changes. Clearing another default uses JDBC to increment its
version and update its timestamp, in the same datasource transaction as the target
JPA write. Explicit flushes return the committed-intent version and enforce constraints.

Concurrent selections of distinct nondefault rows with current versions can both
succeed in lock order; the last successful selection is the final default. If a prior
selection changed the submitted target's version, the stale request gets 409 instead.
There is no guarantee based on HTTP arrival order. Concurrent first-address creates
also serialize around count/default assignment. A failed replacement rolls back
the clear, timestamps, target change and versions; no half-replaced default remains.

Default selection does not update Identity auth_version or rotate sessions. Existing
Identity account/session validity checks remain in force. Reads use READ COMMITTED;
page count and data can reflect different committed snapshots during concurrent writes.

## Security review

URL and service role checks combine with owner-scoped repository methods. The owning
user and actor come from SessionPrincipal, never request fields. Unknown owner/role/
default/status fields are rejected, preventing mass assignment. Sensitive address
components are returned only through authenticated self-service routes. ADMIN has
no extra address permission. No public listing/discovery endpoint was added.

Bean Validation and database checks both protect locations; DTO limits constrain
input sizes. SQL uses bound parameters. Existing error/correlation handling avoids
echoing supplied address values or SQL details. CSRF, secure cookie/session settings,
account-status enforcement and generic unexpected-error behavior are preserved.
No authorization bypass was found in the reviewed and exercised paths; this is not
a penetration-test certification.

## Performance/database review

The `(user_id,created_at DESC,id DESC)` index supports owned pages and counts; the
partial unique default index supports default lookup/clearing. Primary-key access
plus owner predicate handles individual resources. No indexes are added to labels,
street or other unused search fields. No caching or Hikari changes.

Pages load scalar entity fields and map to DTOs without user relationships or lazy
collections, avoiding N+1 queries. Default clearing is one indexed UPDATE, not a loop.
Lists are bounded and account storage capped at 100 records. The user lock is shared
with other workflows that lock that account; contention should be measured if write
volume grows. No production-data EXPLAIN, load benchmark or capacity claim is made.

## Executed verification

Command: `backend/mvnw.cmd -B -ntp verify`, completed 2026-09-11 at 00:57:04
Africa/Cairo. **BUILD SUCCESS**; Maven reported total time **01:59 min**.

| Suite | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| AddressRulesTests | 4 | 0 | 0 | 0 |
| IdentityRulesTests | 5 | 0 | 0 | 0 |
| BranchRulesTests | 7 | 0 | 0 | 0 |
| ApiExceptionHandlerTests | 6 | 0 | 0 | 0 |
| HealthControllerTests | 1 | 0 | 0 | 0 |
| MenuRulesTests | 4 | 0 | 0 | 0 |
| RestaurantRulesTests | 4 | 0 | 0 | 0 |
| AddressesIT | 14 | 0 | 0 | 0 |
| IdentityIT | 12 | 0 | 0 | 0 |
| BackendApplicationIT | 5 | 0 | 0 | 0 |
| BranchesIT | 13 | 0 | 0 | 0 |
| MenuIT | 14 | 0 | 0 | 0 |
| RestaurantsIT | 14 | 0 | 0 | 0 |
| **Total** | **103** | **0** | **0** | **0** |

31 unit/controller and 72 integration tests passed. Addresses adds 18 tests.
Coverage includes own creation/list/read, multiple/custom-label addresses, anonymous
and noncustomer-role denials, cross-customer reads/updates/deletes/default selection
(including ADMIN+CUSTOMER), injection, coordinate ranges/precision/pairing, field
limits, bounded lists/storage cap, default replacement/deletion, stale edits/deletes,
CSRF, suspended account sessions, rollback and database constraints.

Concurrent selections of two different nondefault addresses both returned 200 and
left exactly one default. Concurrent profile updates returned one 200 and one 409.
Injected failure after clearing the old default returned 500 and restored both flags
and versions. Its generic-error log entry is expected, not a failed test. The previous
modules' full regressions passed against PostgreSQL 18.6 Testcontainers as well.

The packaged JAR contains CustomerAddress.class and V1–V6 production migrations,
without AddressesIT/AddressRulesTests classes. The post-run Docker label query found
no remaining Testcontainers containers. Reports/logs: `backend/target/surefire-reports`,
`backend/target/failsafe-reports`, `backend/target/addresses-verification.log`.

## Remaining risks / technical debt

- Addresses and coordinates are user-supplied, not verified or geocoded.
- Saved-address hard deletion requires future orders to snapshot delivery details;
  a mutable saved-address foreign key cannot substitute for historical data.
- There is no address revision/audit-history ledger, recovery bin or retention job.
- Default choice uses serialized last-successful-selection semantics, not a global
  address-book version or HTTP-arrival ordering.
- The 100-address cap is an application rule; privileged direct SQL is unsupported
  and can bypass the application version/locking/cap protocol.
- Production schema inspection and reviewed migration/runtime grants remain required.
- Delivery Zones, Cart, Checkout and frontend remain unimplemented.

## Changed-file manifest

New production files under `backend/src/main/java/com/tayyar/address/`:
`CustomerAddress.java`, `AddressController.java`, `AddressDtos.java`,
`AddressException.java`, `AddressExceptionHandler.java`, `AddressJournal.java`,
`AddressRepository.java`, `AddressService.java`.

Other new files:
- `backend/src/main/resources/db/migration/V6__create_customer_addresses.sql`
- `backend/src/test/java/com/tayyar/address/AddressRulesTests.java`
- `backend/src/test/java/com/tayyar/address/AddressesIT.java`
- `docs/addresses.md`

Modified files:
- `backend/src/main/java/com/tayyar/auth/IdentitySecurityConfiguration.java` — CUSTOMER-only address routes.
- `backend/src/test/java/com/tayyar/BackendApplicationIT.java` — entity scan and migration count.
- `README.md` — implemented scope and migration prerequisite.

Generated reports/logs/JARs remain under `backend/target`. No Git/GitHub operations
were used, and no local/personal production database migration was attempted.
