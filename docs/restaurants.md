# Restaurants design and checkpoint

Status: implemented; verification results below. Branches, Menu and frontend are excluded.

## Design before implementation

V3 introduces restaurants, restaurant_applications, immutable application_submissions,
immutable application_decisions, restaurant_memberships and restaurant_status_history.
Only restaurant-level name/description are included. Cuisine taxonomy, media uploads,
contacts, branch operations and invitations need no implementation for this workflow.

A CUSTOMER submits revision 1 (PENDING). An ADMIN other than the applicant may record
APPROVED or REJECTED against the current version. Approval atomically creates an ACTIVE
restaurant, grants RESTAURANT_OWNER without removing CUSTOMER, inserts its first OWNER
membership and records the decision. Rejection requires a reason. Only the applicant
may submit revised details after rejection; the prior submission and decision remain
immutable, and the application returns to PENDING with an incremented revision.

Reviews and resubmissions lock the application row and check a client-supplied version.
Competing reviews of the same version produce one success and one 409. Profile/status
changes lock the restaurant row and also check a version. No external services are called.

Restaurant membership is a unique restaurant/user relationship. This module supports
OWNER memberships and multiple owners without exposing invitation/transfer/write APIs.
Membership reads are restricted to owners of that restaurant and ADMIN. Row locks plus
deferred database checks prevent a restaurant from losing its last OWNER at commit.
OWNER is a restaurant-level membership; the global RESTAURANT_OWNER role alone does
not confer access to other restaurants.

Brands start ACTIVE. ADMIN can suspend/reactivate them with an audited reason; owners
can edit brand descriptions while suspended but cannot change status. Future discovery/
ordering eligibility must enforce brand status. No branch behavior is implied.

Authenticated IDs are read from SessionPrincipal. DTOs cannot assign applicant IDs,
roles, memberships or status. Public application routes require CUSTOMER, admin routes
require ADMIN, and restaurant routes require OWNER or ADMIN plus resource checks.
Unauthorized object reads return 404. All queues and histories are paginated with
default size 20, maximum 100 and a fixed deterministic order.

## Migration and persistence

`V3__create_restaurant_applications_and_memberships.sql` is the only new production
migration. V1 and V2 are unchanged. UUID keys, foreign keys, status/reason checks,
unique application-to-restaurant and restaurant/user relationships, timestamps and
version checks constrain persisted state. The current-submission foreign key is
deferred to allow creating the application and its first snapshot in one transaction.
History triggers prohibit updates/deletes; another trigger rejects self-review and
approval references to a restaurant from a different application.

Membership mutations lock the parent restaurant. Deferred checks require at least
one OWNER at commit, allowing restaurant creation and initial membership insertion
in the same transaction. This is a structural owner guarantee: it does not require
an owner's user account to remain active.

JPA manages the application/restaurant entities and optimistic versions, with
pessimistic locks for competing mutations. JDBC handles immutable journals and
bounded DTO projections using the same datasource and Spring transaction. Explicit
JPA flushes make state visible to JDBC reads. The injected audit-failure test proves
rollback across JPA writes, JDBC membership/audit work, and the Identity role grant.
Hibernate remains `validate`; Flyway remains the only production schema writer.
Only isolated PostgreSQL 18.6 Testcontainers databases were migrated in this work.

## API contract

All paths start with `/api/v1`. Mutations require the existing session cookie and
`X-CSRF-TOKEN`. Use Identity's CSRF/login flow first. Unknown JSON fields are rejected.

| Method / resource | Access and input |
| --- | --- |
| POST `/restaurant-applications` | CUSTOMER; `name`, `description`; returns 201 |
| GET `/restaurant-applications` | CUSTOMER; own applications |
| GET `/restaurant-applications/{id}` | Applicant or ADMIN |
| POST `/restaurant-applications/{id}/submissions` | Applicant CUSTOMER; `name`, `description`, `version`; rejected applications only; 201 |
| GET `/restaurant-applications/{id}/submissions` or `/decisions` | Applicant or ADMIN; immutable history |
| GET `/admin/restaurant-applications` | ADMIN; `status` defaults to PENDING |
| GET `/admin/restaurant-applications/{id}` and its `/submissions` or `/decisions` | ADMIN |
| POST `/admin/restaurant-applications/{id}/decisions` | ADMIN other than applicant; `outcome`, `reason`, `version`; 201 |
| GET `/restaurants` | Own memberships, or all restaurants for ADMIN |
| GET `/restaurants/{id}` | Matching OWNER membership or ADMIN |
| PUT `/restaurants/{id}` | Matching OWNER membership or ADMIN; `name`, `description`, `version` |
| GET `/restaurants/{id}/memberships` | Matching OWNER membership or ADMIN |
| GET `/admin/restaurants` | ADMIN |
| PUT `/admin/restaurants/{id}/status` | ADMIN; `status`, `reason`, `version` |
| GET `/admin/restaurants/{id}/status-history` | ADMIN |

Collections accept zero-based `page` (0–10000) and `size` (1–100, default 20), and
return `items`, `page`, `size`, `total`. Names are nonblank, at most 120 characters;
descriptions are required (may be empty), at most 2000; reasons at most 1000.
Rejection and status changes require nonblank reasons. `version` is the value from
the latest resource representation. Stale versions and invalid transitions return
409, missing rejection reason 422, invalid DTOs 400, private inaccessible resources
404, and role/self-review denials 403. Unauthenticated requests with valid CSRF
return 401; absent/invalid CSRF on mutations returns 403.

Approval creates an ACTIVE restaurant from the approved snapshot. When the global
owner role is newly granted, existing sessions fail their next authentication-version
check; the applicant must log in again to receive the role. Existing owners retain
their current role and gain access through the new membership. No public DTO or
endpoint grants arbitrary roles, creates memberships, or transfers ownership.

## Security review

URL authorization and service method guards combine with applicant/membership
checks. The actor comes from the authenticated SessionPrincipal. ADMIN may manage
brands but may not review their own application. Public registration's existing
CUSTOMER-only behavior remains covered by the Identity regression.

SQL values are bound parameters; dynamic query fragments are internal fixed choices.
DTOs exclude credentials and persistence internals. Existing CSRF, no-store, secure
session cookies and generic error responses remain in force. Expected injected
persistence failure returns a generic 500 and logs only exception type/correlation ID.
No unresolved authorization bypass was found in this review and exercised cases;
this is not a penetration-test certification.

## Performance and database review

Applicant and status queue indexes follow filter plus `(created_at DESC,id DESC)`.
The membership primary key covers restaurant-scoped reads; `(user_id,restaurant_id)`
covers owner lists. Submission/decision primary keys cover revision histories; the
status-history index covers restaurant/time ordering. A restaurant-status index
supports future status-filtered reads. No Hikari sizing changes were made.

Each collection uses a count plus one bounded projection query, with no per-row JPA
lazy loading. Resource checks add fixed membership/existence queries. Approval takes
application then applicant locks; profile/status writes and membership changes lock
the restaurant. Two reviews of the same version yield one 201 and one 409.

This was a query/code and database-invariant review, not a production-scale benchmark
or EXPLAIN study. Offset pagination and exact counts can become costly at scale;
unfiltered admin restaurant ordering may sort. Measure realistic data before adding
indexes or replacing pagination with cursors. Counts and page contents may reflect
different committed snapshots under READ COMMITTED.

## Executed verification

Final command: `backend/mvnw.cmd -B -ntp verify`, completed 2026-09-10 at
20:08:25 Africa/Cairo. BUILD SUCCESS; Maven elapsed time 57.685 seconds.

| Suite | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| IdentityRulesTests | 5 | 0 | 0 | 0 |
| ApiExceptionHandlerTests | 6 | 0 | 0 | 0 |
| HealthControllerTests | 1 | 0 | 0 | 0 |
| RestaurantRulesTests | 4 | 0 | 0 | 0 |
| IdentityIT | 12 | 0 | 0 | 0 |
| BackendApplicationIT | 5 | 0 | 0 | 0 |
| RestaurantsIT | 14 | 0 | 0 | 0 |
| **Total** | **47** | **0** | **0** | **0** |

16 unit/controller and 31 integration tests passed. Restaurants adds 18 tests.
Coverage includes authenticated submission, CSRF and unauthenticated rejection,
role/status/actor injection, self-review, cross-applicant and cross-restaurant access,
approval role preservation/session invalidation, rejection/resubmission history,
stale versions, bounded queues, audited suspension/reactivation, multiple owners,
duplicate memberships, sequential and concurrent last-owner removal, immutable
history, concurrent double approval and rollback after injected audit failure.

The owner-removal race committed one removal and rejected the other with SQLSTATE
23514, preserving one owner. Double approval returned one 201 and one 409. The
simulated audit failure intentionally produces a generic 500/log entry; all approval
side effects, including auth-version changes, roll back. It is not a failed test.

The packaged JAR includes the Restaurants classes and V1–V3 production migrations,
and excludes Restaurants test classes. A post-run Docker label query found no
remaining Testcontainers containers. Reports are in `backend/target/surefire-reports`
and `backend/target/failsafe-reports`; the run log is
`backend/target/restaurants-verification.log`.

## Remaining scope and risks

- Multiple owners are supported relationally and tested. Additional-owner invitation,
  acceptance, removal and transfer APIs require an approved policy and are deferred.
- Account suspension can leave a restaurant with only inactive owner accounts;
  administrative recovery and ownership continuity need a later policy.
- Application spam throttling, notification delivery and duplicate-business detection
  are not implemented. Multiple applications per customer are allowed.
- Future consumer discovery/order eligibility must enforce restaurant suspension.
- Production target-schema inspection and reviewed runtime grants remain deployment
  prerequisites. No local/personal database migration was attempted.
- Complete approval consistency is enforced by the service transaction plus database
  constraints; direct privileged database writes are not a supported workflow.

## Changed-file manifest

New production files under `backend/src/main/java/com/tayyar/restaurant/`:
`ApplicationController.java`, `ApplicationRepository.java`, `ApplicationService.java`,
`ApplicationStatus.java`, `Restaurant.java`, `RestaurantAdminController.java`,
`RestaurantApplication.java`, `RestaurantController.java`, `RestaurantDtos.java`,
`RestaurantException.java`, `RestaurantExceptionHandler.java`, `RestaurantJournal.java`,
`RestaurantRepository.java`, `RestaurantService.java`, `RestaurantStatus.java`,
`ReviewOutcome.java`.

Other new files:
- `backend/src/main/java/com/tayyar/user/RestaurantOwnerRoleService.java`
- `backend/src/main/resources/db/migration/V3__create_restaurant_applications_and_memberships.sql`
- `backend/src/test/java/com/tayyar/restaurant/RestaurantRulesTests.java`
- `backend/src/test/java/com/tayyar/restaurant/RestaurantsIT.java`
- `docs/restaurants.md`

Modified files:
- `backend/src/main/java/com/tayyar/auth/IdentitySecurityConfiguration.java` — guarded routes.
- `backend/src/test/java/com/tayyar/BackendApplicationIT.java` — entity validation and migration count.
- `README.md` — module status and schema prerequisite.

Generated Maven reports/logs/JARs remain under `backend/target`. No Git/GitHub
operations were performed; this manifest records task edits without using Git.
