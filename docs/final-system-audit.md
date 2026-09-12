# Final full-system audit — 2026-09-12

## 1. Executive system assessment

Tayyar's implemented CASH/platform-delivery workflow has sound API ownership boundaries, server-derived money, transactional checkout, versioned state transitions, and substantial PostgreSQL integration coverage. **No Critical or High finding was identified in the reviewed implementation.** This is not a claim of deadlock freedom, complete database enforcement, or readiness for public production.

The read-only audit preceded verification and any file changes. It covered the HTTP/security boundary, services and JDBC/JPA persistence, migrations V1–V15, tests, Maven, configuration, Dockerfile, Compose and bootstrap. This document is the audit deliverable; no application, migration, dependency or infrastructure configuration changes were made. No Git/GitHub operations, feature work, deployment, load testing, or resource tuning were performed.

Severity reflects demonstrated impact and prerequisites. Retryable transaction aborts under uncommon multi-role/admin interleavings are Medium; missing database defense against direct DML is distinguished from an exploitable HTTP route. Business workflows explicitly deferred in the accepted module contracts are not silently implemented by this audit. Findings below remain open, rather than being represented as user-accepted debt.

**Final gates passed:** 259 tests, zero failures/errors/skips; isolated Docker smoke passed and its resources were removed. Final findings: **0 Critical, 0 High, 7 Medium, 4 Low, 3 Informational**. Controlled local load testing of the implemented CASH/platform-delivery flow can proceed with the documented limitations.

## 2. Critical findings

None identified.

## 3. High findings

None identified. No demonstrated authentication bypass, arbitrary cross-tenant read/write, client-controlled financial amount, duplicate successful checkout, or partial financial commit was found on the implemented API paths.

## 4. Medium findings

### M1 — Cross-module lock order is not globally consistent

- **Files:** `BranchService.assign`, `BranchJournal.activeUser`, `CartStore.lockCustomer`, `CartValidationLocks.lock`, `DriverOperationsStore.lockDriver`, `DriverOrderService.deliver`, `NotificationStore.insert`, `AdminService.change`; V1/V14/V15 foreign keys.
- **Invariant/risk:** transactions can take the same conflicting resources in opposite orders. The system has local lock conventions, not one acyclic global hierarchy.
- **Scenarios:** (a) staff assignment holds a Branch write lock, then requests User UPDATE; the same member's checkout/reconfirmation holds User UPDATE, then requests Branch SHARE. This can occur even for an eventually rejected nonmember assignment because `activeUser` is evaluated before membership validation. (b) two CUSTOMER+DRIVER users delivering orders for one another each hold their own User UPDATE lock, then notification inserts request key-share locks on the other's user row. (c) two administrators suspending each other hold target User UPDATE and then audit inserts reference the other locked user as actor. PostgreSQL aborts a participant; financial writes roll back, but requests can incur delay and conflict responses.
- **Coverage:** existing cart/checkout, assignment, duplicate-delivery and rollback tests cover same-aggregate races. They do not establish absence of these cross-module/user cycles.
- **Recommendation:** design the user/branch/order hierarchy together, including implicit FK locks. Consider whether User NO KEY UPDATE meets serialization requirements while avoiding unnecessary FK conflicts; acquire target users before branches where appropriate; order multi-user acquisitions explicitly. Preserve account-state rereads and regression-test each interleaving. Do not merely move all locks earlier without checking new cycles.
- **Scope:** moderate, several service/store methods and deterministic database race tests. Left unchanged because impact is retryable availability loss, not an integrity bypass; no evidence supports a Critical/High outage classification at current usage.

### M2 — Concurrent owner suspensions can leave no active owner

- **Files:** `admin/AdminService.java` (`suspend`, `isSoleActiveOwner`); V3 restaurant membership guard.
- **Invariant/risk:** “do not suspend the sole active owner” is checked against other users without serializing the shared restaurant/ownership invariant.
- **Scenario:** restaurant has active owners A and B. Independent admins lock A and B respectively; each query observes the other still ACTIVE, so both suspensions can commit. Membership rows remain, satisfying V3, but neither owner can operate the restaurant. Existing-admin reactivation can recover it.
- **Coverage:** `AdminOperationsIT.unsafeSuspensionsAndGenericMutationsAreRejected` covers sequential sole-owner protection; `RestaurantsIT` covers concurrent membership removal, which is a different invariant. No concurrent owner-suspension test was found.
- **Recommendation:** serialize owner-state decisions per affected restaurant or lock the relevant owner set in a deterministic order, with a fresh reread after waiting. Coordinate this with M1 and membership-change paths.
- **Scope:** moderate, admin ownership checks plus one focused race test; no new ownership recovery feature required.

### M3 — Driver assignment's key-share lock does not freeze delivery configuration

- **Files:** `delivery/DriverOperationsStore.java:lockDeliveryModel`, `DeliveryAssignmentService.assign`, `branch/BranchService.edit`, `docs/driver-operations.md`.
- **Invariant/risk:** FOR KEY SHARE protects a referenced key, not non-key `delivery_model` changes. It is not, by itself, protection for the model value used to authorize platform delivery.
- **Scenario:** assignment reads TAYYAR_DELIVERY while a non-key update changes the branch to RESTAURANT_DELIVERY. These lock modes can coexist, allowing an assignment validated against the earlier value. Branch delivery model is also mutable after assignment and is not snapshotted on the order.
- **Coverage/evidence:** wrong-model requests are tested sequentially; no assignment-versus-model-write interleaving was found. Bytecode inspection of Hibernate 7.4.5.Final from the built runtime JAR confirmed that PostgreSQLDialect's default `getForUpdateString()` returns `for no key update`, so this is relevant to the current JPA writer, not only hypothetical direct SQL. Evidence: `backend/target/final-audit/hibernate-lock-bytecode.txt`.
- **Recommendation:** use a lock that conflicts with configuration updates and define the treatment of model changes with active orders/assignments. Recheck lock ordering before strengthening a lock. Do not infer that restaurant suspension should cancel or block fulfillment of already accepted orders; that is a separate product rule.
- **Scope:** small for locking and a regression; moderate for a future explicit active-order configuration policy. This is dispatch/configuration consistency, with ADMIN-only assignment and no cross-driver ownership bypass.

### M4 — Several aggregate invariants remain service-only

- **Files:** migrations V3, V9–V13; `OrderService`, `PaymentService`, `DriverOrderService`, `ReviewService`, `PromotionEvaluator`.
- **Invariant/risk:** immutable UPDATE/DELETE guards do not guarantee aggregate completeness or prevent later INSERTs. The schema does not require every order to have matching summed lines, an address, a payment, initial/history rows, or a checkout receipt. It permits more than one payment; BUSY/active-assignment and delivered/CASH-PAID equivalence are not enforced across tables. Reviews' composite FK proves ownership context but does not require DELIVERED status. Promotion usage limits are serialized in Java, not constrained by PostgreSQL. Role/membership correspondence and status/history correspondence are also not fully enforced.
- **Scenario:** a maintenance script or future internal caller inserts another order item after purchase, changes a legal order status without inserting history, inserts a review of a PLACED order, or commits BUSY without an assignment. Existing HTTP flows do not expose these direct mutations. A promotion snapshot can be inserted without its redemption; a consumed cart can exist without a receipt through direct DML.
- **Coverage:** many negative FK/CHECK/immutability tests and service rollback tests exist. They prove their specific constraints, not these missing cross-table guarantees. Foundation tests intentionally exercise standalone order/payment primitives.
- **Recommendation:** specify which aggregate states must be valid at transaction commit, then add forward migrations with deferred constraint triggers and locking where justified. Separate supported foundation construction from committed purchase completeness. Restrict maintenance writers. Do not edit V1–V15 or add constraints that break intentional staged construction without a design decision.
- **Scope:** moderate/large, several forward schema constraints and direct-SQL/concurrent regressions. Medium because no exposed endpoint was found bypassing the service protections.

### M5 — Runtime default grants include Flyway history DML

- **Files:** `docker/postgres/init/10-create-local-roles-and-database.sh`; `support/PostgresIntegrationTest.java`.
- **Invariant/risk:** granting DML on every future migration-owned table also grants INSERT/UPDATE/DELETE on `flyway_schema_history`. Runtime identity does not own the table and cannot perform DDL, but migration metadata is not isolated from runtime DML.
- **Scenario:** an erroneous maintenance command executed with application credentials can rewrite migration checksums or delete migration history, undermining a subsequent migration check. This requires DB credentials/direct DML; no arbitrary SQL endpoint was found.
- **Coverage:** tests verify no superuser/schema-create capability; they do not verify denial of Flyway-history DML.
- **Recommendation:** revoke runtime writes on migration metadata after migration/bootstrap, and use reviewed grants for application tables. Keep migration credentials separate.
- **Scope:** small bootstrap/migration grant change plus a privilege test. Current local defaults remain deliberately local-only.

### M6 — Throughput-sensitive work grows despite bounded responses

- **Files:** `PromotionStore.totalUses/customerUses`, `PromotionEvaluator`, `DiscoveryQuery`, `NotificationStore.markAllRead/list`, collection count queries.
- **Invariant/risk:** bounded page output is not bounded database work. Promotion checkout serializes on a promotion row while counting growing redemption history; discovery performs repeated eligibility/grouping/count work and substring ILIKE; notification read-all updates the entire unread inbox, and retention prevents routine pruning.
- **Scenario:** a popular promotion or a large inbox/catalog causes longer transactions and lock waits. No measured capacity or outage is claimed.
- **Coverage:** fixed-query-count checks exist for cart, checkout, discovery and driver reads. Fixture-scale plans/query counts do not establish large-data cost or throughput.
- **Recommendation:** measure scanned rows, plans, lock-wait time and transaction duration at representative data sizes. Consider counters, query/index changes and bounded inbox processing only from measured evidence and an agreed retention policy.
- **Scope:** measurement first; later changes vary from small indexes to moderate data-model work. No load test or pool/thread adjustment in this task.

### M7 — Production guards are narrower than a fully enforced deployment contract

- **Files:** `operations/OperationsConfiguration.java`, `application-production.properties`, `application.properties`, Docker profiles, deployment guide.
- **Invariant/risk:** production validates Secure cookies, origins and Flyway clean/baseline flags, but does not inspect runtime role privileges, require a separate migration identity if Flyway is enabled, or guard every property override (for example DDL mode, HttpOnly/SameSite, schema initialization). The image's default profile can be replaced by external configuration.
- **Scenario:** deployment supplies a privileged DB identity or overrides migration/schema properties. The documented contract forbids this, but startup does not enforce all of it. This is operator configuration risk, not an attacker-controlled request path.
- **Coverage:** unsafe Secure cookie/origin/Flyway-clean/baseline tests exist; complete credential/privilege/profile-policy tests do not.
- **Recommendation:** before production deployment, validate the resolved security/schema settings and DB privileges, and enforce profile/secret separation in the deployment contract. Empty allowed origins correctly means same-origin and need not be rejected.
- **Scope:** moderate startup validation and negative configuration tests, outside the accepted local-only deployment scope.

## 5. Low and informational findings

### L1 — Admin driver assignment projection mislabels an order UUID

- **Files:** `admin/AdminQuery.java:drivers`, `AdminDtos.AssignmentSummary`.
- **Risk/scenario:** the shared DTO's second field is named `driverId`, but the driver-list query passes `order_id`; support clients can treat an order ID as a driver ID. The order-list path populates that field correctly. No authority decision uses this response.
- **Coverage:** admin read tests do not assert this identity mapping specifically.
- **Fix/scope:** use an appropriate driver-list assignment projection or populate the declared identity; small DTO/query correction and focused assertion.

### L2 — Request logging retains arbitrary path text

- **Files:** `common/api/RequestCorrelationFilter.java`.
- **Risk/scenario:** query strings and bodies are excluded, but raw request URI is logged and placed in MDC, including rejected/unmatched paths. A client embedding an email or sensitive string in a path can place it in logs. This is not evidence that normal payload passwords, cookies or addresses are logged.
- **Coverage:** existing redaction tests do not exhaust arbitrary paths.
- **Fix/scope:** log matched route templates with a bounded safe fallback; small filter/logging change and tests.

### L3 — Input strictness is uneven

- **Files:** early-module controllers using individual `@RequestParam`; `ReviewDtos.Update/Moderation`; parameter helpers.
- **Risk/scenario:** unknown JSON fields are globally rejected, but unknown query keys are rejected only in modules using strict parameter helpers. Primitive review versions can default to zero if omitted, permitting an initial version-zero edit without an explicit version. Some UUID parsing accepts noncanonical representations. These do not grant foreign ownership or bypass version equality.
- **Coverage:** strict helper modules have focused tests; omission and duplicate-query behavior is not uniform across every controller.
- **Fix/scope:** boxed required versions and consistent parameter contracts; small/moderate, no API expansion.

### L4 — Multi-query reads can show mixed committed states

- **Files:** order/admin/review/notification query services using READ COMMITTED.
- **Risk/scenario:** an order header can be read before delivery commits and its payment/history afterward; public review page/count/average can straddle moderation. This is a response consistency issue, not a partial database commit. Cart/discovery reads explicitly use REPEATABLE READ.
- **Coverage:** no deterministic cross-query read-snapshot tests found.
- **Fix/scope:** choose consistent snapshots or joined projections where the response contract requires it; small/moderate. Avoid globally extending read transactions without measurement.

### Informational

- **I1 — Deferred fulfillment/recovery:** RESTAURANT_DELIVERY checkout is possible but the implemented fulfillment path is platform-driver-only. Restaurant delivery, failed delivery, reassignment, refusal, cash discrepancy, refunds and card provider handling remain deferred. Accepted/preparing orders cannot use customer cancellation. Use TAYYAR_DELIVERY/CASH for end-to-end local load scenarios. These limits prevent calling the whole marketplace production-ready. A future release must either close unsupported entry paths or approve the missing workflows.
- **I2 — Throttle behavior:** DB upserts make counters shared and atomic, with hourly cleanup. Fixed windows allow boundary bursts; retries consume checkout quota before receipt lookup. With forwarded headers disabled, an eventual reverse proxy may collapse clients onto one IP bucket. Current behavior is safe from forged forwarded IPs; configure a trusted proxy boundary before internet deployment. No broad limiter added.
- **I3 — Dependency assurance boundary:** POM uses one Spring Security stack; test libraries are test-scoped, PostgreSQL is runtime-scoped, and build/runtime target Java 21. No needless dependency upgrade is justified by this audit. This is not a complete transitive CVE/SBOM or base-image vulnerability scan; tags are mutable and a future release must record exact image digests and scan results.

For I1, affected code is CheckoutService, BranchService, DeliveryAssignmentService and the order/payment state machines; existing tests assert unsupported CARD/platform-driver model operations, but not a restaurant-delivery completion path because none exists. A future fix ranges from a small eligibility gate to a separately approved fulfillment/recovery module. For I2, affected code is AuthController, AuthRateLimiter, CheckoutController and forwarded-header configuration; atomic limiter tests exist, but trusted-proxy/multiple-application-instance tests do not. A future change is a small reviewed proxy configuration plus boundary tests, with retry-quota policy chosen explicitly. For I3, affected files are pom.xml, the Maven wrapper and Dockerfile; the current build and JAR-content inspection verify scope/version consistency, while scanner-backed vulnerability assurance remains a separate release check rather than an unsolicited upgrade.

## 6. Authentication/session assessment

Registration normalizes email with Locale.ROOT, hashes passwords with delegated BCrypt strength 12, applies a minimum of 12 code points and maximum of 72 UTF-8 bytes, and assigns CUSTOMER only. The unique normalized-email constraint protects concurrent duplicates. Login uses a dummy hash for absent users and a generic credential error, rejects inactive users, and stores a credential-free immutable principal.

Login rotates session ID and CSRF token. JDBC Spring Session persists sessions; idle timeout defaults to 30 minutes and an authenticated-at timestamp enforces a 12-hour absolute lifetime. The validity filter rereads status/auth_version on authenticated requests; password/email/status and role writes increment the version via triggers. Logout is DELETE with CSRF protection, session invalidation and cookie clearing. Cookie defaults are Secure, HttpOnly, SameSite=Lax, path `/`; container-local explicitly disables Secure for loopback HTTP and production rejects an insecure combination.

No business GET mutation was found; CSRF retrieval creates security/session/rate-counter state as expected. Authentication/session decisions apply at request boundaries, not as cancellation of already running requests. Checkout and driver operations also reread target account state under locks. Other in-flight operations may finish after suspension, and method security uses the request's principal; this should not be advertised as instantaneous cancellation of all work.

## 7. Authorization/IDOR assessment

| Surface | Authority and data scope |
|---|---|
| Public discovery/reviews | Active restaurant/branch projections; no account, order, address or payment identities in public review output |
| CUSTOMER addresses/cart/checkout/orders/reviews/favorites | Session customer ID; owned-address/order/cart predicates; review context derived from owned order |
| RESTAURANT_OWNER management | Central role plus DB restaurant OWNER membership; branch/menu/promotion parent IDs checked |
| RESTAURANT_STAFF order operations | Central role plus DB branch assignment; mutation holds assignment key-share against removal |
| DRIVER | Active assigned-order predicate and session driver identity; completed assignments no longer expose destination details |
| ADMIN | Explicit support/configuration/assignment/moderation/account commands; no impersonation, arbitrary checkout, generic status/payment API or generic role grant |
| Notifications | Session recipient predicate on every read/write; no user notification-send endpoint |

HTTP rules end in denyAll; method authorization reinforces services. Request `userId` for staff/provisioning and `driverId` for admin assignment identify a controlled target, never the actor. Admin `customerId`/`actorId` query fields are filters. Ownership is not delegated to client money/owner/actor fields. No persistence entity is bound as an HTTP request body.

## 8. Database-integrity assessment

V1–V2 protect normalized user identity, roles, session keys and rate counters. V3 provides deferred current-submission/owner checks, immutable decision/status history and self-review prevention. V4–V7 protect restaurant/branch/menu context, sibling ordering, address ownership/default uniqueness, coordinates and immutable geography identities. V8–V10 protect single active cart, cart-item context, one receipt per cart/order/payment, price/quantity ranges, payment/order amount equality, purchase snapshots, legal status transitions and consumed carts. V11 provides single ACTIVE assignment per order/driver and immutable delivery history. V12 provides review/order/customer/branch context and retention. V13 provides promotion snapshots/redemption context and merchandise-only discount caps. V14–V15 protect notification ownership/read monotonicity and admin history.

The only ON DELETE CASCADE found in production migrations is session-to-session-attributes cleanup. Historical business rows generally reject update/delete or retain referenced source records. Nullable saved-address/geography snapshot references deliberately allow historical snapshots to survive live-address deletion.

M4 is a material limit: FK existence is not business eligibility, and immutable existing rows are not a guarantee that missing rows cannot be omitted or extra rows appended. Promotion definition CHECK expressions also lack explicit non-null operands for the selected discount type; PostgreSQL CHECK accepts UNKNOWN, so direct SQL can create invalid definitions that Java rejects. Fixed discount NaN should likewise be explicitly excluded at the schema boundary in a future forward migration.

## 9. Global lock-order map

`U` = explicit FOR UPDATE; `S` = FOR SHARE; `K` = FOR KEY SHARE (including FK checks); `W` = conditional DML/JPA write lock. The built Hibernate 7.4.5.Final PostgreSQL dialect uses FOR NO KEY UPDATE for its default pessimistic write; that still conflicts with checkout's S but is compatible with K. New rows also acquire uniqueness/FK locks. Lists below show acquisition order, not an approved universal hierarchy.

| Path | Locks in order / relevant dependencies |
|---|---|
| Registration | Insert User → insert Role → role trigger updates same User |
| Application approval | Application W → applicant User U → Role write → new Restaurant/owner membership (restaurant trigger U) → reviewer/applicant FK K → decision/notification |
| Restaurant edit/status | Restaurant W → status history actor User K where applicable |
| Branch edit/schedule | Branch W → schedule rows; authorization reads do not freeze restaurant membership |
| Branch staff assign | Branch W → target User U → Membership K → assignment insert, actor User K |
| Branch staff unassign | Branch W → Assignment delete W |
| Menu writes/reorder | Menu W → category/item/override rows; new overrides also reference Branch K |
| Address writes/default | User U → owned Address W / default-address rows |
| City/zone/rule edits | Corresponding City/Zone/Rule W; creations acquire parent FK K |
| Cart add/update/remove/replace | User U → active Cart U → existing Item U or new cart/item FK locks |
| Cart reconfirm | User U → Cart U → Restaurant S → Branch S → Menu S → Categories S sorted → Items S sorted → Overrides S sorted → CartItems U sorted |
| Checkout | User U → Cart U → reconfirm configuration hierarchy → Address S → City S → Zone S → delivery Rule S → Promotion U if selected → new Order/items/address/history → redemption/payment/history → Cart update → receipt |
| Promotion management | Promotion W (versioned update); creation references Restaurant K |
| Customer cancel | Owned-order read → Order conditional W → actor User K through history |
| Restaurant order transition | Membership/StaffAssignment K → Order conditional W → actor User K/history → customer User K/notification |
| Driver assignment | Order U → Branch K → active Assignment U if present → DriverProfile + User U (one joined query, no explicit internal ordering) → new Assignment/history → driver state/history → Notification |
| Pickup | Order U → Assignment U → DriverProfile + User U → order/history → customer User K/notification |
| Delivery | Order U → Assignment U → DriverProfile + User U → Payment U → order/payment/history → assignment/history → driver/history → customer User K/notification |
| Driver available/offline | DriverProfile + User U → driver/history; active-assignment check is a read |
| Review creation | Owned Order U → new Review FK locks |
| Review edit/moderation | Review conditional W |
| Account suspension/reactivation | target User U → eligibility reads → User update → actor User K/admin audit |
| Notification mark/read-all | owned Notification W; read-all has no explicit row ordering |
| Rate limiting | counter UPSERT W; login IP and email acquisitions are separate calls/transactions |

The explicit checkout/configuration chain is orderly. M1 records concrete inversions outside that chain; implicit FK locks cannot be omitted. M3 records an insufficient configuration lock. No deadlock-free claim is made. PostgreSQL's lock compatibility and deadlock-abort behavior were checked against the [PostgreSQL 18 locking documentation](https://www.postgresql.org/docs/18/explicit-locking.html).

## 10. Concurrency/race assessment

| Required race | Result / limitation |
|---|---|
| Cart update or replacement vs checkout | Same User/Cart serialization and expected versions; at most one consumption; existing tests |
| Price/availability vs checkout | Configuration locks then authoritative reread; changed price requires versioned reconfirmation; tests cover both writer-first and checkout-first patterns |
| Promotion update/final slot | Promotion row lock plus fresh usage counts; one final redemption slot; API limit test exists; expired-time boundary is evaluated using time captured before a potentially blocking promotion lock |
| Address update/delete vs checkout | Shared User U serialization, then address ownership/read; snapshot remains immutable |
| City/zone/rule vs checkout | Existing rows S-locked and reread; absent required configuration causes rejection or is observed after creation; no invalid serviceable purchase inferred from an absent lock |
| Restaurant suspension/branch pause vs checkout | Restaurant/Branch S prevents relevant updates while purchase is built; fresh cart query checks status/pause/schedule |
| Customer suspension vs checkout | Shared User U; checkout rechecks ACTIVE after waiting; suspension refuses a committed nonterminal customer order |
| Accept vs cancel / reject vs accept | Versioned conditional order update and legal-transition guard; one winner, loser conflicts |
| Assignment vs terminal order change | Only READY_FOR_PICKUP eligible; cancellation/rejection have no legal edge from that state; model-write limitation M3 |
| Driver suspension vs assignment | Assignment locks/rereads active User; suspension locks same User and refuses a committed BUSY active assignment |
| Pickup vs reassignment/terminal change | Reassignment absent; order/assignment/user checks and versions; no legal terminal edge from pickup's source |
| Delivery vs payment mutation | Order/assignment/driver/payment locked; CASH paid and all histories commit together; generic payment HTTP mutation absent |
| Review creation vs delivery | Owned Order lock sees committed delivery; terminal status is stable |
| Review edit vs moderation | Same review version CAS; one winner; edit does not unhide a review |
| Admin suspension vs active operations | Request-time revocation; guarded checkout/driver paths reread state; M2 for multiple owners and in-flight boundary noted in section 6 |
| Notification vs rollback/retry | MANDATORY transaction propagation, dedup unique key, no external side effect; existing failure-injection tests |

Clock-based eligibility is a decision-time contract, not a guarantee that every transaction commits before a schedule/promotion end. If commit-time eligibility is desired, a fresh evaluation after lock waits and a stated boundary policy are needed. Tests should explicitly cover this before changing semantics.

## 11. Checkout assessment

Trace: authenticated CUSTOMER → validated idempotency key/promotion code → User lock and active check → durable previous receipt comparison → owned active Cart and version → configuration locks → authoritative lines/open state/availability/prices → owned saved Address/active City/Zone/Rule → money-range/minimum checks → locked Promotion/usage decision → server-calculated Order/items/address/history → promotion snapshot/redemption → CASH Payment/history → CHECKED_OUT Cart → immutable receipt.

No submitted unit price, discount or total is trusted. Same customer/key with different cart/version/address/method/normalized promotion fails. Successful retry returns the original PLACED/PENDING receipt semantics, even if the live order later progressed. One cart has at most one receipt/order through unique receipt keys. Only successful attempts persist receipts, in the purchase transaction; rollback leaves the key reusable and cart unconsumed. CASH is supported; CARD fails before purchase writes. Existing tests inject failures at each write stage and exercise concurrent identical/different keys.

The sum-before-discount range check can conservatively reject a cart whose subtotal-plus-fee exceeds the supported limit even if a discount would bring the final total back under it. This is a safe upper-bound rejection, not overflow or undercharge.

## 12. Money/payment assessment

Money uses BigDecimal and NUMERIC(12,2); no float/double money path was found. Server validation rejects negative/out-of-range or over-scale values. Merchandise totals sum unit price × integer quantity. Final total is merchandise subtotal − discount total + delivery fee, enforced by SQL CHECK, with V13 additionally capping discount to merchandise. Percentages use HALF_UP to two decimal places; fixed/percentage discounts are capped to merchandise and optional maximum. Payment's composite FK fixes currency/amount to its order.

Order and payment Java transition maps agree with their SQL update guards. Terminal order states have no outgoing edges. Customer cancellation is PLACED-only; restaurant endpoints cover accept/reject/prepare/ready; only assigned platform driver performs pickup/delivery. Restaurant transitions never mark CASH paid. Delivery marks CASH PAID with assignment completion and driver release in one transaction. Internal foundation APIs support future card/refund states but no provider/status mutation endpoint is exposed. Cancelled/rejected orders retaining PENDING cash records represent uncollected cash, not successful payment; operational reporting must distinguish these from collectible live orders.

## 13. Historical-data assessment

Purchased names/unit prices/quantities, address details and applied promotion identity/code/name/type/discount are snapshots. Source menu/address/promotion edits do not rewrite them. Order/payment/assignment/driver/admin histories reject UPDATE/DELETE, reviews and notifications are retained, and business cascades do not erase them. Live restaurant/branch display names in order summaries are not historical snapshots. Review content remains deliberately editable and moderation has no dedicated actor/reason history; do not call that a complete moderation audit trail. M4 covers missing-row/late-INSERT limits.

## 14. Privacy/data-exposure assessment

Public discovery exposes restaurant/branch/menu/geography fields. Public review output omits customer/order identifiers and account details. Customer reads are owned; restaurant operators receive order fulfillment details scoped to membership/assignment; active drivers receive only needed pickup/destination/payment-collection fields. Driver details disappear when assignment completes. ADMIN support sees deliberate email/customer/audit context but not password hashes, phone in user summaries, session/auth versions or provider secrets. Current-user output intentionally includes its own identity/roles. Notification deduplication keys stay internal. Free-text reviews/instructions/reasons may themselves contain user-provided personal data; no content-scrubbing guarantee is made.

## 15. Logging/observability and management assessment

Correlation IDs are generated server-side. Request logs use method/path/status/duration and an authenticated actor UUID; credentials, cookies, CSRF headers, bodies and query strings are not explicitly logged. Generic exception handling logs exception type/origin rather than message/payload; JDBC disables server error details. Production suppresses Hibernate SQL/bind logs. Mapped validation responses do not echo rejected values. Raw path limitation is L2.

Checkout counters have fixed success/conflict labels, and normal metrics use bounded route/status labels rather than user/order IDs. Counter success includes idempotent replay; it is not a unique-orders metric, and conflict counting is limited to CheckoutException. Probes are anonymous and detail-free; readiness includes DB and liveness excludes it. Other exposed Actuator paths require ADMIN; unlisted routes deny by default. Management shares the application port, so network restriction remains part of production deployment.

## 16. Docker/configuration assessment

Two-stage build copies main sources into the builder and only the executable JAR/compiled probe into runtime. Test dependencies are test-scoped and Docker build skips tests explicitly; the separate Maven gate must pass. Runtime is UID/GID 10001, exec-form Java PID 1, with root-owned application files; Compose uses read-only root, bounded tmpfs, dropped capabilities and no-new-privileges. PostgreSQL has a persistent named volume and no published host port. Backend is bound to 127.0.0.1; migrations gate startup through a finite job.

Local passwords are explicit examples, not embedded in the image. Runtime and migration environment credentials are separated for the backend/job. No secret-bearing environment dump was needed. Production image defaults fail without external DB settings and enforce reviewed cookie/origin/Flyway flags; M5/M7 describe limits. No Hikari/thread/heap/resource tuning was made.

## 17. Query/performance architecture assessment

Scalar JDBC projections and bounded pagination dominate. Cart reads are two queries and discovery menu uses a batch/window query rather than one query per category. Driver/admin order reads use joined/lateral payment/assignment projections. Critical customer/order queues, membership lookup, active carts/assignments, promotion redemptions and notification inboxes have indexes. JPA is used for comparatively small configuration aggregates; OSIV is disabled and no large lazy entity graph is returned through HTTP. Dynamic SQL identifiers/sort/filter fragments inspected are selected from private constants, enums or bounded server-owned branches; user values use bind parameters. Discovery explicitly escapes `!`, `%` and `_` before ILIKE. No raw request SQL fragment or injectable identifier path was found.

Measure in the next local load phase: p50/p95/p99 latency and error mix; DB/pool wait time versus transaction time; locks/deadlocks under M1 interleavings; same-customer checkout contention; popular-promotion counts and lock duration; discovery count/filter/ILIKE plans at large catalogs; offset pagination; inbox read-all size; rate-limit key contention/cleanup; session writes/expiry cleanup; CPU, memory, connection use and GC. Record data sizes and exact image/runtime versions. Do not infer capacity from fixture query counts or tune pools/threads before those measurements.

## 18. Test-gap assessment

| Area | Existing evidence | Remaining focused gaps |
|---|---|---|
| Identity/session | IdentityIT and IdentityRulesTests: normalized duplicates, role injection, hashing, rotation, logout, expiry, revocation, limiter race | Full multi-instance application replay/clock behavior; in-flight revocation contract |
| Authorization | Per-module ITs for anonymous/wrong-role/foreign-parent/IDOR/CSRF/mass assignment | Exhaustive role × route inventory as a maintained artifact |
| Checkout/transactions | CheckoutIT: snapshots, receipt durability, both key races, rollback stages, configuration writers, promotion final slot | Cross-module M1 cycles, suspension interleavings, promotion time boundary after wait |
| Order/payment/delivery | Foundation/OrderOperations/DriverOperations ITs: state maps, CAS races, history/assignment/release rollback, cash collection | Model-write race M3; aggregate direct-DML invariants M4 |
| Database/history | Negative FK/CHECK/immutability tests across modules | Completeness/late inserts, review eligibility, nullable promotion CHECK operands, privilege M5 |
| Admin/reviews/notifications | Sequential suspension safeguards, moderation versions, owned inbox, required notification rollback | Concurrent owner suspension M2; L1 projection identity; edit/moderation interleaving; full moderation audit policy |
| Runtime/observability | Probes, DB-loss readiness, CORS, fixed metrics, unsafe production settings | Automated image-content/grant/profile matrix; future CVE/SBOM verification |

No tests were added solely for Medium/Low issues, as instructed. Verification executes the existing PostgreSQL 18 suite without skipping tests.

## 19. Fixes made

None. No Critical/High fix was required. Only this audit report and generated verification evidence were written.

## 20. Tests added

None. Medium/Low regression work remains recommended, outside this task's authorized fix scope.

## 21. Exact final Maven results

**BUILD SUCCESS**, exit **0**, completed **2026-09-12 00:18:20 Africa/Cairo (+03:00)**, Maven elapsed **05:31 min**.

Executed in `C:\Users\DEATHX7\Desktop\Tayyar\backend`:

```powershell
.\mvnw.cmd -B -ntp '-Dmaven.repo.local=C:\Users\DEATHX7\.m2\repository' verify
```

Maven **3.9.16**, host Eclipse Adoptium Java **21.0.11**; Testcontainers configured with **postgres:18**, resolved PostgreSQL **18.6** as shown by Flyway connections in this run.

| Suite | Test classes | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| Unit/controller (Surefire) | 14 | 58 | 0 | 0 | 0 |
| PostgreSQL integration (Failsafe) | 19 | 201 | 0 | 0 | 0 |
| **Total** | **33** | **259** | **0** | **0** | **0** |

Counts were cross-checked against this invocation's log and XML reports written after the run began. Historical Surefire XML from earlier manually selected integration runs was excluded from the count. The existing opt-in `local-db-smoke` profile is excluded by the normal POM configuration; it is not a skipped Testcontainers test or part of the requested default verification gate. Expected failure-injection tests log errors while asserting rollback; all suite assertions passed.

Evidence: `backend/target/final-audit/maven-verify.log`, `maven-results.json`, `surefire-suites.json`, `failsafe-suites.json`, and standard Surefire/Failsafe XML. Final Docker inspection found no remaining containers; label-filtered Testcontainers volume/network checks were also empty. Final SHA-256 comparison again found **0 changes across 269 source/configuration/test files**.

## 22. Docker smoke result

**PASS.** Ran from `C:\Users\DEATHX7\Desktop\Tayyar` with `BACKEND_HOST_PORT=18080`:

```powershell
docker compose -p tayyar-final-audit-20260912 up --build -d
```

This uses the documented Compose workflow with an isolated project/volume and loopback port. PostgreSQL **18.6** was healthy; Flyway history had **15 rows, maximum version 15, all successful**; the migration job exited **0**; backend health was **healthy**.

| Anonymous request | HTTP |
|---|---:|
| `/actuator/health/liveness` | 200 |
| `/actuator/health/readiness` | 200 |
| `/api/v1/health` | 200 |
| `/api/v1/discovery/restaurants` | 200 (valid empty page on fresh DB) |
| `/actuator/metrics` | 401 |
| `/actuator/prometheus` | 401 |
| `/actuator/health` | 401 |

Runtime inspection: Temurin **21.0.12+8**, UID/GID **10001**, read-only root filesystem, exec Java entrypoint, ALL capabilities dropped and no-new-privileges. `/opt/tayyar` contains only the JAR and compiled health probe. The runtime JAR has **658 entries**, **15 migrations**, and no matched source/test/fixture/Testcontainers/JUnit/Mockito/devtools entries. Runtime DB role is neither superuser nor role/database creator, and cannot CREATE in public; its Flyway-history UPDATE privilege is **true**, confirming M5.

Observed runtime libraries include Spring Boot **4.1.1**, Spring Security **7.1.1**, Hibernate **7.4.5.Final**, Flyway **12.4.0**, HikariCP **7.0.2**, and PostgreSQL JDBC **42.7.13**. The Maven host JDK and container JRE differ in patch level (21.0.11 versus 21.0.12) while retaining Java 21 compatibility. SHA-256 comparison of **269 source/configuration/test files** before/after verification preparation found **zero changes**; evidence is `source-hashes-before.json` and `source-unchanged.json` in the audit evidence directory.

Backend image: `sha256:eaffcb410c8290a24b5f2d8f150ae3d475ee74e5e7c21bff208fd912f2a259d8`, **155,525,500 bytes**. PostgreSQL digest: `postgres@sha256:1c59e2c3c818eaa0f0628f695b36e7c9e362d6b219b36a54a32df645cbd7e1af`.

Cleanup succeeded with `docker compose -p tayyar-final-audit-20260912 down --volumes`. Follow-up label-filtered container/volume/network listings were empty. Only the audit project's disposable resources were removed; the built local image/cache is retained as a normal build artifact. Evidence: `backend/target/final-audit/compose-build.log`, `compose-runtime.log`, `compose-cleanup.log`, `http-smoke.json`, and `image-inspection.json`.

## 23. Remaining technical debt

M1–M7 and L1–L4 remain open. I1–I3 distinguish previously deferred product/deployment work from newly identified findings. This report does not presume user acceptance of new findings. No production certification, complete CVE assessment, or load capacity claim is made.

## 24. Local load-testing recommendation

**Yes, for controlled local load testing of the implemented TAYYAR_DELIVERY/CASH paths:** both verification gates passed. Include M1 deadlocks/lock waits and M6 data-size costs in the measurement plan. The implementation is not deadlock-free; successful verification does not close the Medium findings. Do not use load-test results to claim readiness for unsupported restaurant-delivery or recovery workflows, or public production deployment. No load testing was performed by this audit.

## 25. Conventional Commit recommendation

Omitted: no implementation changes were required. No commit or other Git operation was performed.
