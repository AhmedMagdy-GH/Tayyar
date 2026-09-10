# Menu design and checkpoint

Status: implemented and verified; design recorded before implementation. Stopped at Menu.

One primary operational menu per restaurant, enforced by a unique restaurant key.
Categories belong to that menu; items belong to one immutable category/restaurant
context. Names, descriptions, active flags and server-managed positions describe
the catalog. No image storage/provider, scheduling, options or customization engine.

Item prices use BigDecimal and NUMERIC(12,2), nonnegative, EGP only. Item active is a
global deactivation gate; base available is a separate operational flag. Branch
overrides contain optional price and availability, never copies of the item. Effective
price is COALESCE(override price, base price). Effective availability requires active
menu, category and item, then COALESCE(override availability, base availability).
This is menu eligibility, not branch opening/serviceability or checkout validation.

V5 adds restaurant_menus, menu_categories, menu_items, branch_menu_item_overrides.
Composite foreign keys prohibit cross-restaurant parent links and overrides. Parent
contexts cannot be reassigned. Unique deferred position constraints allow atomic
reordering. New categories/items append under a menu lock; reorder requests supply
the exact complete unique sibling ID set. Categories are capped at 100/menu and items
at 500/category, allowing bounded reorder operations. Reads use pages up to 100.

Every mutation after menu creation supplies the menu aggregate version, including
branch override changes. Locking the menu and checking that version defines one
consistent conflict rule for prices, availability, child edits and reorders. JPA
manages menu versioning and validates entity mappings; JDBC child writes/projections
share its transaction. This deliberately serializes writes per restaurant in v1.

Only matching OWNER memberships and ADMIN can manage. STAFF/CUSTOMER gain no menu
permissions. Actor comes from SessionPrincipal; bodies cannot assign parent IDs,
currency, roles or positions. Menu/category/item deletion is not exposed: deactivate
instead. Override removal restores inheritance and is versioned. No broad cascades.

## Persistence and money

New migration: `V5__create_restaurant_menus.sql`. V1–V4 are untouched. UUIDs identify
menus/categories/items; branch/item is the override primary key. A unique restaurant
key enforces the single-menu rule. Composite foreign keys carry restaurant context
through menu/category/item and both sides of each branch override. Triggers reject
restaurant/menu/category reassignment. Parent foreign keys have no cascade deletion.

Menu, category and item have JPA mappings, validated at startup. The menu entity owns
the aggregate `@Version`. Category/item changes and override projections use JDBC
inside the same Spring transaction; children are never loaded as lazy collections.
All production schema changes belong to Flyway. Runtime DDL remains disabled.

Prices are BigDecimal and NUMERIC(12,2), range 0.00–9999999999.99 EGP. Zero is permitted;
negative values, more than two fractional digits and overflow are rejected by DTO
validation. Database checks reject negative and NaN values; numeric precision rejects
overflow. PostgreSQL can round excess fractional digits on direct SQL assignment,
so privileged SQL is not a supported price-write API. Currency is fixed to EGP in
the menu and constrained in the database; clients cannot choose it in request bodies.

The effective resolver uses one joined projection and SQL COALESCE:

- no price override: base price;
- price override: that decimal price;
- no availability override: base availability;
- availability override: that boolean, still gated by active menu/category/item.

For example, a 120.00 base item can resolve to 125.50 at one branch while another
inherits 120.00. Setting an override's price to null restores price inheritance while
retaining its availability override. A PUT replaces both override fields: omitted
nullable fields become null. Both null is rejected; DELETE removes the override.
There are no persisted effective prices or duplicated branch menus.

## API contract

Restaurant menu base: `/api/v1/restaurants/{restaurantId}/menu`.
All routes are management-only, with existing session and CSRF requirements.

| Method / suffix | Body or response |
| --- | --- |
| POST base | `{name}`; create primary menu, 201 and menu view |
| GET base | Menu view, including currency and version |
| PUT base | `{name,active,version}` |
| GET `/categories` | Paginated categories |
| POST `/categories` | `{name,description?,version}`; 201 |
| GET `/categories/{categoryId}` | Category snapshot |
| PUT `/categories/{categoryId}` | `{name,description?,active,version}` |
| PUT `/categories/order` | `{ids,version}`; complete ordered sibling IDs |
| GET `/categories/{categoryId}/items` | Paginated items |
| POST `/categories/{categoryId}/items` | `{name,description?,basePrice,available,version}`; 201 |
| PUT `/categories/{categoryId}/items/order` | `{ids,version}`; complete ordered sibling IDs |
| GET `/items/{itemId}` | Item snapshot |
| PUT `/items/{itemId}` | `{name,description?,basePrice,available,active,version}` |

Branch management base:
`/api/v1/restaurants/{restaurantId}/branches/{branchId}/menu/items`.

| Method / suffix | Body or response |
| --- | --- |
| GET base | Paginated effective item projections |
| GET `/{itemId}` | Effective item snapshot |
| PUT `/{itemId}/override` | `{price?,available?,version}` |
| DELETE `/{itemId}/override` | `{version}` JSON body |

Except menu creation, mutations return `{id,version}` where id identifies the affected
resource and version is the new **menu aggregate version**, not the branch version.
Fetch GET menu for the latest aggregate version. Individual reads wrap DTOs in
`{data,version}`; collections return `{items,page,size,total,version}`. Creation starts
the menu at version 0. New categories/items default active; new item availability is
explicit. New siblings append at the next zero-based position. Deactivation preserves
their position and identity. Reordering includes active and inactive siblings.

Names are nonblank and at most 120 characters; optional descriptions at most 2000.
Collections default to 20, maximum 100, page range 0–10000. Invalid DTOs/order sets
return 400; stale versions or duplicate menu creation return 409. Missing/inaccessible
objects return 404; customer/staff access is denied. No category/item/menu DELETE
handler exists. These endpoints do not implement discovery, public search or Cart.

## Authorization and security review

The existing `/restaurants/**` route gate requires OWNER or ADMIN. Menu service method
security applies the same role requirement; Restaurant service checks actual OWNER
membership. Branch service checks parent/branch identity before override reads or
writes. Category/item queries include restaurant identity, and the database repeats
cross-restaurant checks through composite keys.

Actor identity comes from SessionPrincipal, never a payload user ID. Bodies exclude
restaurantId, menuId, categoryId reassignment, role, currency and raw position fields;
unknown fields are rejected. Availability and price changes are only exposed through
these owner/admin resources. RESTAURANT_STAFF, even with a branch assignment, receives
no menu privilege. SQL values are bound; the two reorder table names are private
constants. Entities, passwords and SQL errors are not serialized to the client.

No authorization bypass was found in reviewed/exercised paths. Existing session,
CSRF, no-store and generic unexpected-error handling remain unchanged. This review
does not constitute a penetration-test certification.

## Transactions and concurrency

Menu creation uses an atomic unique insert; competing creations cannot make two menus.
Subsequent writes lock the menu, verify the submitted aggregate version, perform child
work, advance updated_at and flush the JPA version before responding. This serializes
writes across categories/items/branch overrides within a restaurant, conservatively
rejecting even independent stale edits. Read traffic does not acquire write locks.

Reorder requests must contain every sibling exactly once: duplicates, omissions and
foreign IDs fail before the batch update. Deferred uniqueness allows position swaps
without temporary collisions; transaction rollback restores all positions/version.
Override upserts cannot create duplicates, and stale updates cannot replace a newer
override. No provider call or other external side effect occurs in these transactions.

Read views use READ COMMITTED; count/data/version queries can observe different
concurrent commits. Clients may need to reload after a conflict. These are management
views, not checkout quotes. Future Cart/Checkout must recompute authoritative prices
and validate current branch/menu state in its own deliberate transaction boundary.

## Performance/query review

The unique menu restaurant key serves menu lookup. Deferred unique `(menu_id,position)`
and `(category_id,position)` keys serve sibling reads and deterministic ordering.
Items have a `(restaurant_id,active,id)` index for restaurant and future active-item
access. Overrides use `(branch_id,item_id)` as their primary key plus an item/restaurant
index for referenced item lookup. Composite referenced keys have their unique indexes.

Effective item pages use a joined projection across items, categories, menu and a
branch-filtered override LEFT JOIN, plus a count query. No per-item lookup or lazy
child collection occurs. Authorization and menu lookup add fixed queries independent
of page size. Reordering uses one bounded JDBC batch. Creation loads at most 101/501
sibling IDs to enforce caps and append consistently under the menu lock.

No caching, Redis, Hikari sizing or thread-pool changes. Cross-category effective
ordering may sort and exact counts/offset pages can become expensive. Aggregate write
contention and real query plans should be measured with realistic data before finer
locking/index/cursor changes. No production EXPLAIN/load benchmark or capacity claim
is made at this checkpoint.

## Executed verification

Final command: `backend/mvnw.cmd -B -ntp verify`, completed 2026-09-11 at
00:43:12 Africa/Cairo. **BUILD SUCCESS**, Maven total time **01:31 min**.

| Suite | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| IdentityRulesTests | 5 | 0 | 0 | 0 |
| BranchRulesTests | 7 | 0 | 0 | 0 |
| ApiExceptionHandlerTests | 6 | 0 | 0 | 0 |
| HealthControllerTests | 1 | 0 | 0 | 0 |
| MenuRulesTests | 4 | 0 | 0 | 0 |
| RestaurantRulesTests | 4 | 0 | 0 | 0 |
| IdentityIT | 12 | 0 | 0 | 0 |
| BackendApplicationIT | 5 | 0 | 0 | 0 |
| BranchesIT | 13 | 0 | 0 | 0 |
| MenuIT | 14 | 0 | 0 | 0 |
| RestaurantsIT | 14 | 0 | 0 | 0 |
| **Total** | **85** | **0** | **0** | **0** |

27 unit/controller and 58 integration tests passed. Menu adds 18 tests. They cover
owner/admin management, customer/staff/anonymous denials, CSRF, cross-restaurant and
cross-branch access, injection, money boundaries, ordering validation, deactivation,
override inheritance/replacement/removal, aggregate-version conflicts, rollback and
database constraints. Caps were tested with 100 categories and 500 category items.

Concurrent item edits returned one 200 and one 409. A stale override write returned
409 and preserved the newer price. Injected failure after a reorder batch returned
500 and restored original positions and menu version. The expected generic-error
log entries from failure-injection tests do not represent test failures. All earlier
Foundation, Identity, Restaurants and Branches regressions passed on PostgreSQL 18.6.

The packaged JAR includes Menu.class and V1–V5 production migrations; it contains
no MenuIT/MenuRulesTests classes. The post-run Docker label query found no remaining
Testcontainers containers. Evidence: `backend/target/menu-verification.log`,
`backend/target/surefire-reports`, and `backend/target/failsafe-reports`.

## Options, retention and remaining debt

- OptionGroup/Option and customization pricing are explicitly deferred; no current
  requirement justifies adding that engine to this checkpoint.
- One menu per restaurant; no meal/day/date schedules or multiple primary menus.
- No cross-category move workflow. Recreate/deactivate is possible, but future move
  behavior needs deliberate identity/history rules rather than parent-ID updates.
- Category/item caps are application rules; privileged direct SQL can exceed them.
- Menu/category/item identifiers are retained through deactivation. Override DELETE
  restores current inheritance; future OrderItem snapshots must preserve purchased
  names/prices/options independently. There is no immutable price-change ledger yet.
- Restaurant suspension, branch opening/pause, geographic serviceability and future
  cart/checkout eligibility are additional checks, not part of effective menu availability.
- Granular staff permissions, media integration and consumer discovery are deferred.
- Direct SQL writes bypass the menu version protocol and are unsupported operationally.
- Test fixtures use isolated PostgreSQL 18.6. No personal/production database was
  migrated; target schema inspection and runtime-grant review remain prerequisites.

## Changed-file manifest

New files under `backend/src/main/java/com/tayyar/menu/`:
`Menu.java`, `MenuCategory.java`, `MenuItem.java`, `MenuController.java`, `MenuDtos.java`,
`MenuException.java`, `MenuExceptionHandler.java`, `MenuJournal.java`,
`MenuRepository.java`, `MenuRules.java`, `MenuService.java`.

Other new files:
- `backend/src/main/resources/db/migration/V5__create_restaurant_menus.sql`
- `backend/src/test/java/com/tayyar/menu/MenuRulesTests.java`
- `backend/src/test/java/com/tayyar/menu/MenuIT.java`
- `docs/menu.md`

Modified files:
- `backend/src/test/java/com/tayyar/BackendApplicationIT.java` — entity scan/migration count.
- `README.md` — implemented module and migration prerequisite.

Generated logs, reports and packaged JARs remain under `backend/target`. No Git/GitHub
operations are used to implement or inspect this module.
