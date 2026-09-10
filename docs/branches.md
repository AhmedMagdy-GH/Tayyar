# Branches design and checkpoint

Status: implemented and verified; design was recorded before implementation. Stopped at Branches.

## Domain and flow

A branch has an immutable restaurant reference, label, structured address (line1,
line2, city, region, postal code, two-letter country code), optional international
phone, optional paired decimal latitude/longitude, IANA timezone, delivery model,
administrative ACTIVE/INACTIVE status, independent paused flag, timestamps and version.
It does not copy restaurant brand information. Creation defaults to ACTIVE/unpaused
with an empty (closed) schedule. Matching owners and ADMIN may manage all branch data;
status and pause changes use a dedicated versioned resource, never profile fields.

Weekly hours have one same-day interval per ISO weekday (1 Monday to 7 Sunday).
Omitted days are closed. Times are minute-precision local SQL TIME, opening < closing.
Overnight/multiple daily intervals are deferred. Date-specific overrides are separate:
both times null means closed, otherwise a valid same-day interval replaces that day's
weekly hours. Branch timezone converts the server Instant before schedule evaluation;
opening is inclusive and closing exclusive. Effective state also respects restaurant
suspension, branch inactivity and pause, in that precedence order. No order acceptance
or geographic serviceability is implied by this management-time observation.

Staff assignments link branch and existing restaurant membership through composite
foreign keys, preventing cross-restaurant assignment. Assignment requires an active
user. OWNER is currently the only approved membership type. Assignments grant no new
roles or management access. Owners/ADMIN can assign/unassign existing members; no
invitations, onboarding, arbitrary user enrollment or staff permission engine is added.

V4 adds branches, branch_opening_hours, branch_special_hours, branch_staff_assignments.
JPA manages the branch aggregate and version; JDBC handles bounded schedules and
assignment projections in the same transaction. Restaurant access is checked through
the Restaurant service. All mutations lock the branch, check a supplied version and
advance it, including child changes. Branch restaurant reassignment is rejected by a
database trigger as well as excluded from DTOs. Concurrent stale edits return 409.
Schedule replacement is atomic and limited to 7 weekly / 366 special-date entries.
Lists default to 20, max 100. No previous migration or Hikari configuration is changed.

## Migration and persistence

`V4__create_branches.sql` is the only production migration added. V1–V3 are unchanged.
Branches have UUID identifiers, immutable restaurant foreign keys, `NUMERIC(9,6)`
coordinate pairs, `TIMESTAMPTZ` audit times and a JPA `@Version`. PostgreSQL checks
coordinate ranges, country/phone shape, delivery/status enums and timezone validity.
Java and PostgreSQL must both recognize a timezone; timezone data should stay current
on both runtimes. Country codes are checked for two uppercase letters, not against a
country registry. Phone is optional and must use international `+` plus 8–15 digits;
this is formatting validation, not proof of ownership or reachability.

Weekly and special schedules use SQL `TIME` without timezone (local wall time).
Unique primary keys prohibit duplicate weekday/date records. Database checks enforce
valid weekdays, paired override times, minute precision and opening before closing.
Special dates replace the entire weekly interval for that date, not just part of it.
One API replacement contains at most 7 weekly and 366 special-date records; empty
lists clear the schedules. Weekly closed days have no row, special full-day closures
have a row with two null times. Closing at `24:00` is not supported in this version.

Assignment `(branch_id,user_id)` is unique. Composite foreign keys connect both the
branch and the membership to the same restaurant. A user/branch cannot be attached
through a membership belonging to a different restaurant. Existing assignments must
be removed before deleting their referenced membership; no cascading deletion is
introduced. Assignment operations neither grant RESTAURANT_STAFF nor any other role.

JPA validates the branch mapping. Child persistence uses JdbcTemplate on the same
Spring transaction/datasource, with explicit flushes before returning versions.
No production/local personal database was migrated; verification uses disposable
PostgreSQL 18.6 Testcontainers under the existing restricted runtime-user setup.

## API contract

The base is `/api/v1/restaurants/{restaurantId}/branches`. Every route requires
ADMIN or RESTAURANT_OWNER; the latter must own the parent restaurant. Inaccessible
parents and mismatched branch/parent IDs return 404. CUSTOMER has no management
access. Acting identity always comes from the authenticated session. Existing secure
cookie, CSRF and session-validity policies apply without changed security configuration.

| Method and suffix | Request / response |
| --- | --- |
| POST base | Profile body; 201, Location header and branch view |
| GET base | Paginated branch views; `page`, `size` |
| GET `/{branchId}` | Branch view |
| PUT `/{branchId}` | `{profile,version}`; full profile replacement |
| PUT `/{branchId}/operation` | `{status,paused,version}`; branch view |
| GET `/{branchId}/hours` | `{weekly,special,version}` |
| PUT `/{branchId}/hours` | `{weekly,special,version}`; atomic full replacement |
| GET `/{branchId}/availability` | `{state,scheduleOpen,evaluatedAt,timezone}` |
| GET `/{branchId}/staff` | Paginated assignments |
| PUT `/{branchId}/staff/{userId}` | `{version}`; assign existing active member; branch view |
| DELETE `/{branchId}/staff/{userId}` | `{version}` body; unassign; branch view |

Profile fields: `name`, `addressLine1`, optional `addressLine2`, `city`, optional
`region`, `postalCode`, `countryCode`, optional `phone`, paired optional `latitude`
and `longitude`, `timezone`, `deliveryModel`. Names/addresses are trimmed where
required; no brand description, branch delivery fee, zone or menu fields are present.

Example hours replacement (version must come from the latest branch/hours response):

```json
{
  "version": 0,
  "weekly": [{"weekday": 1, "opensAt": "09:00", "closesAt": "23:00"}],
  "special": [{"date": "2026-12-25", "opensAt": null, "closesAt": null}]
}
```

`status` accepts ACTIVE or INACTIVE; `paused` is independent. Both matching owners
and ADMIN can change these via the dedicated operation resource. Parent restaurant
suspension always takes precedence in effective availability. Resuming a branch does
not reactivate a suspended restaurant. New branches remain schedule-closed until
hours are set. No client may set effective `OPEN` or supply an evaluation timestamp.

All collection pages default to 20, max 100; page range is 0–10000. Unknown fields,
invalid coordinates/timezones/delivery models and invalid hours return 400. A stale
version or duplicate staff assignment returns 409. A missing assignment on removal
returns 404. Updates cannot move a branch, set roles, or bypass membership checks.
There is no patch/merge ambiguity: profile and schedule PUTs replace their resources.

## Transactions and concurrency

Branch writes take a pessimistic branch lock and validate the client version; JPA
also maintains an optimistic version column. Profile, operation, hours and staff
changes all advance the same aggregate version, even for an identical replacement.
Conflicting clients must reload and retry; no update silently overwrites a stale edit.

Assignment locks the target user while checking ACTIVE, then locks the existing
membership against deletion and inserts within the branch transaction. Duplicate
insertion returns conflict without leaking a database constraint message. Deletion
is atomic and leaves restaurant ownership intact. Schedule replacement deletes and
batch-inserts bounded records in the same transaction. No external provider calls
occur inside transactions. Read-only views use READ COMMITTED; multi-query views
can reflect concurrent committed changes and are not future checkout decisions.

## Security review

Routes inherit the existing `/restaurants/**` role gate. Branch service method
security and Restaurant service ownership checks enforce access again. Every lookup
includes the parent restaurant ID. Staff target IDs identify recipients only; the
assigning actor is taken from SessionPrincipal. Staff relationships do not authorize
management, even if a stale assignment remains after account suspension.

Strict DTO deserialization rejects restaurant/status/role/actor injection into profile
requests. Only the operation resource accepts the two permitted administrative
statuses; neither staff nor profile endpoints expose privileged membership choices.
SQL parameters are bound. Entities and credentials are never serialized directly.
Existing error handling returns generic unexpected-error responses and logs exception
type/correlation ID without SQL or submitted values. No authorization bypass was
found in the reviewed and exercised paths; this is not a penetration-test certification.

## Performance/database review

`branches(restaurant_id,status,created_at DESC,id DESC)` supports restaurant/status
access, with the leading restaurant key also supporting FK lookups. Unfiltered
restaurant lists may sort within that restaurant because status precedes ordering;
measure realistic cardinality before adding another index. Schedule primary keys
cover branch-scoped reads. Staff primary key covers branch lists; user/branch and
restaurant/user indexes cover user lookup and referenced membership checks.

Branch pages fetch scalar JPA fields without relationships or child collections,
avoiding per-row lazy queries. Staff lists use one count plus a bounded projection.
Hours/availability add two bounded schedule reads; parent authorization adds fixed
membership/existence/detail queries. Schedule writes use two JDBC batches instead of
one network round trip per interval. Timezone catalog checks occur on profile writes
and in the database trigger. No Hikari or thread-pool settings changed.

This is a code/query-path and database-constraint review. No production-data EXPLAIN,
load test, latency target or 1,000-concurrent-user capacity claim is made. Exact counts
and deep offset pagination may need replacement after measurement.

## Executed verification

Final command: `backend/mvnw.cmd -B -ntp verify`, completed 2026-09-10 at
20:29:03 Africa/Cairo. **BUILD SUCCESS**. Maven reported total time **01:15 min**.

| Suite | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| IdentityRulesTests | 5 | 0 | 0 | 0 |
| BranchRulesTests | 7 | 0 | 0 | 0 |
| ApiExceptionHandlerTests | 6 | 0 | 0 | 0 |
| HealthControllerTests | 1 | 0 | 0 | 0 |
| RestaurantRulesTests | 4 | 0 | 0 | 0 |
| IdentityIT | 12 | 0 | 0 | 0 |
| BackendApplicationIT | 5 | 0 | 0 | 0 |
| BranchesIT | 13 | 0 | 0 | 0 |
| RestaurantsIT | 14 | 0 | 0 | 0 |
| **Total** | **67** | **0** | **0** | **0** |

23 unit/controller tests and 44 integration tests passed. Branches adds 20 tests.
Coverage includes owner/admin creation and edits, customer/anonymous/cross-owner
denials, CSRF, wrong parent IDs, payload injection, delivery/address/coordinate/zone
validation, weekly/special hours, closed days, local timezone and DST boundaries,
pause/inactive/parent suspension precedence, existing-member assignments, duplicate
and unauthorized assignment, stale/concurrent updates and direct database constraints.

Concurrent profile edits returned one 200 and one 409. Injected failures after schedule
replacement and staff insertion returned 500 while restoring prior child data and
branch version. These expected failure-test log entries do not indicate failed tests.
Identity and Restaurants tests, including approval rollback and last-owner protection,
also passed. Flyway validated all migrations and found no migrations to reapply.

Packaged `backend-0.0.1-SNAPSHOT.jar` contains Branch.class and production V1–V4,
with no Branches test classes. The post-run Docker label query found no remaining
Testcontainers containers. Logs/reports are under `backend/target/branches-verification.log`,
`backend/target/surefire-reports` and `backend/target/failsafe-reports`.

## Remaining risks / technical debt

- Overnight hours, multiple intervals/day and midnight-as-24:00 need an explicit
  schedule extension; requests that would imply them are rejected.
- Special-date overrides are bounded full replacements; pruning past dates is the
  caller's responsibility. There is no holiday calendar or background pruning job.
- Staff invitation/onboarding and detailed permissions remain deferred. Only already
  authorized restaurant members can be assigned, currently OWNER memberships.
- Assignment records and operational changes have no immutable change-history ledger;
  assignment creator/timestamp and branch updated/version metadata are retained.
- Stored assignments can outlive account suspension. Future staff-authorized workflows
  must revalidate user status, membership and branch assignment at execution time.
- Address/phone/coordinates are user-supplied metadata without geocoding or verification.
- Local-time schedules follow timezone rules, including repeated hours during fall-back;
  no special DST exception policy or future ordering guarantee is introduced.
- Menu, Delivery Zones, driver dispatch and frontend remain unimplemented.
- Production schema inspection and migration/runtime grant review remain prerequisites.

## Changed-file manifest

New files under `backend/src/main/java/com/tayyar/branch/`:
`Branch.java`, `BranchController.java`, `BranchDtos.java`, `BranchException.java`,
`BranchExceptionHandler.java`, `BranchJournal.java`, `BranchRepository.java`,
`BranchRules.java`, `BranchService.java`, `BranchStatus.java`, `DeliveryModel.java`.

Other new files:
- `backend/src/main/resources/db/migration/V4__create_branches.sql`
- `backend/src/test/java/com/tayyar/branch/BranchRulesTests.java`
- `backend/src/test/java/com/tayyar/branch/BranchesIT.java`
- `docs/branches.md`

Modified:
- `backend/src/test/java/com/tayyar/BackendApplicationIT.java` — branch entity scan and migration count.
- `README.md` — implemented module and migration prerequisites.

Generated Maven outputs remain under `backend/target`. This manifest records edits
without Git; no Git/GitHub operation is used.
