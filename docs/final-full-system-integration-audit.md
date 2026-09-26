# Tayyar — final full-system integration audit

Audit dates: 21–22 September 2026. Local Windows / Java 21 / PostgreSQL 18.6 / real HTTP sessions and browser UI.

## 1. Overall result

**PASS for the exercised security, automated-verification, and representative integration gates after two High fixes; qualified product-readiness result because Medium/Low findings remain.** No unresolved Critical/High finding was identified in the reviewed paths. This is not a claim of exhaustive security coverage, WCAG certification, or production deployment readiness.

Backend: **58 unit/web-slice + 209 integration tests, 0 failures, 0 errors, 0 skipped**. Frontend: **96 tests in 21 files**, TypeScript, ESLint, and production build pass; production dependency audit reports **0 vulnerabilities**. Disposable Compose build succeeds; PostgreSQL/backend healthy; Flyway V1–V16 applied successfully; readiness, liveness, public discovery return 200.

Two independent real flows reached DELIVERED / PAID / COMPLETED / AVAILABLE: one HTTP smoke and one browser-driven Customer → Staff → Admin → Driver flow. All 16 requested representative pages were measured at 390, 768, 1024, and 1440px with no document-level horizontal overflow in the exercised states.

Only the two High defects were fixed. No new business features, UI redesign, infrastructure, migration edits, Git/GitHub operations, or commits were performed. Docker state was not reset or deleted. Findings below explicitly distinguish browser observations, source review, and automated coverage.

## 2. Critical findings

None identified in the reviewed and exercised paths.

## 3. High findings — fixed

### H1. Private cross-role cache survives logout/account changes

Root cause: query keys are role-scoped rather than account-scoped, while each logout path removed only its own role's cache. Owner data could survive Customer-header logout; Driver/Admin data could remain for a later account. Session-expiry handling was also fragmented. In-flight requests and mounted form state needed the same boundary.

Reproduction: the strengthened `AccountPage.test.tsx` initially failed because `restaurantContext` remained after logout; the test also seeds private Driver and Admin data.

Fix: central `sessionCache.ts` clears non-session queries and mutation cache, cancels reads, discards CSRF state, and handles session identity/role/status changes and protected-request 401s. All logout paths and successful login use this boundary. `client.ts` rejects responses from a previous session generation, including late mutation results, and prevents submitting a mutation after its CSRF acquisition crosses a session change. `SessionBoundary` remounts private component state on identity changes. Checkout session expiry now sets the session to null rather than attempting to write undefined.

Regression evidence: 8 cache-boundary tests, 3 stale HTTP/CSRF response tests, 1 mounted private-form test, and the expanded logout regression pass. Full frontend suite passes. Browser role switches and direct Driver navigation to the former Owner route deny access without rendering the old Owner view.

### H2. Global Owner role elevates a STAFF membership in another restaurant

Root cause: `OrderOperationsAccess.member()` accepted any restaurant membership. After V16 introduced STAFF memberships, an account with global RESTAURANT_OWNER and RESTAURANT_STAFF roles could own restaurant A, be Staff in restaurant B, and receive Owner-level order access throughout B, bypassing its assigned-branch boundary. This affects private order/address reads and transitions, not merely navigation.

Reproduction: `OrderOperationsIT.ownerRoleElsewhereDoesNotElevateStaffMembership` initially expected 404 for an unassigned branch's order but received 200 containing its address snapshot.

Fix: require `membership_type='OWNER'` in the existing Owner authorization query, preserving its authorization lock and Staff fallback.

Regression: unassigned detail, transition, and branch-filtered queue return 404; the general queue excludes the unassigned order; assigned Staff and genuinely owned restaurant transitions still succeed. The full 209-test backend integration suite passes after the fix. Restaurant management already uses `RestaurantJournal.isOwner`, context queries distinguish membership types, and Admin sole-owner safety checks also filter OWNER; these adjacent paths were re-reviewed.

## 4. Medium findings — deferred, not silently accepted

| ID | Finding and evidence | Impact / bounded follow-up |
| --- | --- | --- |
| M1 | Numeric JSON/form mismatch. `BranchDtos.Profile` returns numeric coordinates, but `BranchesPages.tsx` passes them unchanged into `z.string()` fields. Browser: Edit profile → Save branch without changing coordinates displays “Invalid input: expected string, received number”. `DeliveryDtos.RuleInput` similarly returns numeric money, while `DeliveryPage.tsx` resets a string-only form with those numbers; unchanged Save rule focused the fee field without a useful error. | Existing profiles/rules cannot reliably be saved until numeric fields are re-entered. Separate response types from input types and normalize numeric form values to decimal strings. No demonstrated financial miscalculation. |
| M2 | Incomplete 409 recovery in Owner editors. Branch/category/delivery/override editors retain captured versions after list refetch; menu item writes, reorder/reset paths do not consistently refetch on conflict. Item creation can substitute version 0 when its source query is unavailable. | Repeated stale conflicts or misleading recovery, not silent server overwrites. Reopen editors as workaround; synchronize versions explicitly and disable writes until version data exists. |
| M3 | Empty Restaurant queue stops polling. `OrdersPages.tsx` polls only when the current page already contains active orders. | A first incoming order is not discovered automatically on an empty queue. “Refresh now” remains available. Poll visible operational queues independently of current row count. |
| M4 | Checkout uncertainty survives only the current component mount. `CheckoutPage.tsx` keeps the key/input in a ref; navigation/reload loses it and can generate another key. | Fails the requested durable uncertain-attempt UX. The backend locks the customer and atomically consumes the cart, so a second key for that consumed cart was rejected with 409; duplicate charging/orders were not reproduced. Persist a customer/cart-scoped pending attempt without storing authentication secrets. |
| M5 | Owner editor state is not consistently reset on Restaurant/Branch selection changes (`RestaurantContext`, Delivery, Hours, BranchOverrides, Staff/Branch editors). | Stale branch IDs/forms can target an old context or fail with 404; a delivery-rule editor can retain a previous branch's values/version. Backend scope checks constrain access, but context changes should cancel editors explicitly. |
| M6 | Mobile accessibility/navigation gaps. At 390px every Owner navigation link has no accessible name: its text span is `display:none` and the icon is aria-hidden. Admin Sign out is hidden below 820px with no replacement in that shell; Restaurant mobile shell also hides the Customer-app return link. | Screen-reader navigation is impaired and logout is hard to discover on privileged mobile screens. Preserve accessible names and an explicit exit path. |
| M7 | Form errors/focus are inconsistent. Several Owner forms lack associated per-field errors. Admin reason dialog traps Tab and supports Escape, but cancellation leaves focus on BODY rather than the invoking button (browser reproduced). | Keyboard/screen-reader recovery is incomplete. Restore invoking focus and associate validation messages. |
| M8 | Admin assignment selects load only the first 100 ready orders/available drivers; existing assignments are not excluded from the ready-order list (`DriversPage.tsx`). | Valid records beyond the first page are inaccessible and already-assigned orders cause avoidable server conflicts. Keep backend authority; paginate/search the selections. |
| M9 | Demo mode substitutes restaurant, branch, menu, price and geography fixtures, not only unsupported visual fields (`HomePage`, `RestaurantPage`, `demo/data`). | Exceeds the requested presentation-only demo scope. It is explicitly labelled, demo cart/favorites actions are disabled, and no fake checkout/order/Driver/Admin flow was found. Narrow demo data in a separately approved change. |

## 5. Low findings — deferred

- **L1 — Contract/display drift:** `EffectiveItem` is typed as extending `ManagedItem`, but backend effective items do not include `available`, `active`, or `position`. The Owner override panel reads `item.available`, incorrectly showing the restaurant default as unavailable. Admin account-status unions omit backend DISABLED. Several response money fields are typed as strings although JSON contains numbers. No PLATFORM_DELIVERY/TAYYAR_DELIVERY mismatch was found in submitted commands.
- **L2 — Small/cramped Admin presentation:** 7–10px status/metadata text and small controls; mobile user emails fall into the narrow label column. `.admin-page-header > div:last-child` also makes the sole title/description container a horizontal flex row when there are no actions. Browser screenshot confirmed cramped, though non-overflowing, mobile content. No redesign performed.
- **L3 — Polling/retry rough edges:** externally completed Driver details can continue polling after 404 because local completion is absent. Some query paths inherit one retry even for 429. There is no unbounded 429 retry loop; mutation retry is not enabled. Notifications refresh on query lifecycle/mutations rather than a live delivery channel.
- **L4 — Scale/discoverability debt:** Owner menu loads item queries per category; several Owner lists and favorites membership checks use only the first 100 records. Customer main bundle remains above Vite's advisory size. Non-Customer accounts can land on Customer-oriented home/account paths and receive benign access errors. These are not authorization bypasses.

## 6. Informational findings and evidence limits

- Initial backend runs failed because Docker Desktop was unavailable; another run/build was interrupted during engine repair. Those failures are superseded by the successful final run, not counted as passing tests.
- The user repaired Docker. No factory reset, Docker-state deletion, or broad cleanup was attempted.
- The HTTP smoke harness initially expected 201 for assignment (actual documented response: 200), then checked `status` instead of `orderStatus` in completion. These harness errors were corrected and the remaining flow resumed without repeating completed business operations. Database reads confirmed atomic completion. They were not product findings.
- Existing deterministic load-test Driver UUIDs do not meet the frontend's strict UUID validation. A new standards-shaped local Driver fixture was inserted; Admin UI provisioning/assignment then succeeded. Production-generated IDs are UUIDs from the application's normal creation paths.
- Test-only emails, addresses, passwords, orders, and reviews were used. No real customer/provider/payment information was transmitted.
- Not every negative status was manually induced on every screen. Automated tests and source review supplement representative browser flows. No formal contrast, screen-reader, exhaustive browser/device, or long-duration polling certification is claimed.

## 7. Authentication/session result

PASS in covered paths after H1. Real registration grants CUSTOMER only; login/current-user/logout work. Identity integration tests cover fixation/CSRF rotation, persisted credential-free principal, logout revocation, status/password/role invalidation, idle/absolute expiry, enumeration resistance, and rate limiting. JDBC session configuration remains intact. A later expired Driver browser session safely returned to login.

Local cookie flags observed without printing values: HttpOnly=true, SameSite=Lax, Secure=false, as intended for `container-local` HTTP. Base/production configuration sets Secure=true. No auth/session token storage in localStorage; the only application localStorage key found is the non-secret selected Restaurant ID.

## 8. Authorization/IDOR and route protection result

PASS in covered paths after H2. Real CUSTOMER requests to Admin, Driver and Restaurant operations returned 403; OWNER to Admin/Driver returned 403; foreign Restaurant/Driver assignment and unassigned Staff branch returned privacy-preserving 404. Automated coverage includes foreign users/orders/branches/staff, mixed memberships, stale changes, and bounded Admin safety checks.

Customer, Owner/Staff, Driver and Admin guards wait for session resolution; Owner routes additionally use the selected membership role. Direct URL loads/refreshes were exercised. Staff navigation exposes only Overview/Orders; direct Staff Menu URL redirects to the queue. Driver navigation to Restaurant operations redirects away. Backend authorization remains authoritative. No generic role mutation, impersonation, or arbitrary Admin payment/order transition UI was found.

## 9. Customer E2E result

Real API: registration → login → address and zone → cart → CASH checkout → same-key replay → order → delivery → notifications → review → favorites. A second key for the consumed cart returned 409.

Real browser: anonymous discovery → login → Restaurant/menu → add item → cart → saved-address checkout → confirmation → tracking/orders → notifications/favorites/account. The browser order `4640fa95-7d1c-4435-894b-7d8ca0a28d98` displayed 53 EGP merchandise + 21 EGP delivery = 74 EGP server-confirmed total. Keyboard activation of cart/checkout controls succeeded. Browser pointer activation was not consistently reliable through the automation surface; this is not presented as a blanket mouse-interaction pass.

## 10. Restaurant Owner result

Real multi-Restaurant context, populated queue/menu/branches/staff and delivery rules were viewed. Staff membership/branch assignment succeeded via real API. Existing backend suites cover Restaurant profile, branches/hours/menu/overrides/delivery/membership writes, scope and concurrency. Browser unchanged profile/rule editing exposed M1; Owner workflow is therefore **qualified, not defect-free**. M2/M5 also remain.

## 11. Restaurant Staff result

Real assigned-branch context showed Branch 1 only and no Owner management links. Browser order acceptance → preparation → READY_FOR_PICKUP succeeded. API stale acceptance returned 409; unassigned branch read returned 404. Reject transitions and races are covered by the passing order integration suite. Mixed Owner/Staff privilege escalation is fixed by H2.

## 12. Driver result

Real browser provisioning produced OFFLINE; Go available produced AVAILABLE. After Admin assignment, dashboard showed BUSY/“On a delivery”, correct pickup/destination and 74 EGP cash due. Pickup produced OUT_FOR_DELIVERY; explicit cash completion produced “Delivery complete”, “Cash · Paid”, and Available, with focus on the result heading. PostgreSQL confirmed DELIVERED / PAID / COMPLETED / AVAILABLE for that exact browser order.

API foreign assignment returned 404 and stale pickup returned 409. Tests cover uncertainty recovery and guarded transitions. Local successful completion disables detail polling; externally completed/reloaded cases retain L3.

## 13. Admin result

Real Users/Restaurants/Orders/Drivers/Audit views loaded; support projections exclude security internals. API suspension/reactivation and immutable audit read succeeded, old session denied 401. Application approval succeeded and invalidated the applicant's old session. Browser Driver provisioning and assignment succeeded with standard UUIDs. Order support is read-only and exposes version.

Passing backend suites cover safety conflicts, restaurant state/history, application approve/reject/version conflicts, assignment races and audit immutability. Browser reason dialog was opened and cancelled without issuing suspension; focus evidence is in M7. Selection scalability and mobile exit/readability debt remain.

## 14. Frontend/backend contract result

QUALIFIED: compared API wrappers/TypeScript contracts with Java DTOs and real JSON. Core Cart, Checkout, Order, Payment, Driver, Staff-context and Admin assignment commands worked end to end. Numeric edit forms, effective-item fields and account-status unions have M1/L1 drift. Pagination uses `items/page/size/total`, but some controls request only one bounded page. Driver assignment response is 200; application decisions and checkout return 201. TAYYAR_DELIVERY is used consistently in delivery commands.

## 15. Money, cart and checkout result

Server authoritative: decimal BigDecimal totals, fees, promotion eligibility/redemptions and historical snapshots. Frontend formats server amounts in EGP and does not submit calculated authoritative totals or collect CARD details. Cart is one active cart/one branch; explicit cross-branch replacement, acknowledged/current prices, reconfirmation and version checks have automated coverage. Double submit is guarded and same mounted uncertain attempt reuses key/input. Backend same-key replay returned the same order; different key on consumed cart failed. M4 remains for navigation/reload durability; M1 for decimal edit forms.

## 16. Order/payment state result

PASS in exercised paths. Restaurant buttons expose only PLACED accept/reject, ACCEPTED preparation, PREPARING readiness. Driver exposes pickup and subsequent explicit completion. Customer cancellation is constrained and backend checked. CASH completion atomically updates order, payment, assignment and driver state. Pending/card-failure/refund foundation states remain server governed; no arbitrary frontend payment transition found.

## 17. Concurrency/version result

Cart/address/review, Restaurant profile/branches/hours/menu/override/rule, Staff branch assignment, order transitions, Driver pickup/delivery and Admin restaurant/application/assignment mutation paths were reviewed. Core APIs submit server versions; backend locks/conditional writes reject stale commands. Real Staff/Driver 409 checks and passing race tests support this. Mutations are not blindly retried (except one explicit CSRF-invalid recovery before an authorized handler). Owner editor recovery is incomplete as detailed in M2; this is not an all-green UX result.

## 18. CSRF result

PASS in covered paths. All state-changing frontend wrappers use central `mutate`, including auth, cart/address/checkout/review/favorites, Restaurant/Staff, Driver and Admin. Requests include session cookies and CSRF headers. Missing-token real login returned 403; integration coverage verifies domain writes and token/session rotation. Recovery retries only explicit CSRF_INVALID once. H1 also prevents cross-session CSRF acquisition from dispatching a later mutation.

## 19. Query/cache result

H1 fixed cross-account confidentiality. Session boundaries intentionally clear broadly, including address-sensitive discovery queries; ordinary mutations retain scoped invalidation. Pending reads and old-session HTTP results cannot restore private data, and mounted forms remount. Keys include resource/filter/pagination inputs in the reviewed operational lists. Per-category menu requests and first-page-only membership checks remain L4; this is not a query architecture rewrite.

## 20. Polling result

Restaurant/Driver intervals are approximately 15–20 seconds; Customer active-order tracking is 20 seconds, not live GPS. No one-second custom timer or duplicate manual interval loop found. TanStack Query does not enable background polling; several callbacks also test visibility. Customer terminal state and local Driver completion stop their detail interval. Empty Restaurant queues and external Driver completion retain M3/L3. Browser tracking/transition updates were observed; long-duration hidden-tab timing was not exhaustively profiled.

## 21. Error-handling result

Safe user-facing messages, no rendered raw stack traces/security details in observed states. Loading/empty states, backend-unavailable login, profile validation, stale commands and expired sessions were exercised across browser/API/tests. 401 centrally clears session data. 403/404 deny access; 409 does not silently overwrite. 429 has bounded retry behavior, not a loop, but Retry-After handling is richer in Checkout than other areas. Failed Owner numeric validation can be silent (M1/M7). Error coverage is representative rather than every status on every route.

## 22. Privacy result

Two identified High boundary leaks fixed. Reviewed DTOs/UI do not render password hashes, session IDs, CSRF token internals, auth_version, provider secrets, or arbitrary Admin metadata. Driver destination access is assignment-bound; customer orders/addresses are owner-bound; Restaurant order authorization now honors actual OWNER membership. Admin support addresses are intentionally available within its bounded support role. Test fixtures, not personal data, were used throughout.

## 23. Demo-mode result

Normal mode remained real in browser/API smoke. Demo mutations for preview menu/favorites are disabled and notices identify fixtures. Authentication, checkout/orders, notifications, reviews, Driver and Admin were not replaced with fabricated success state. However M9 fails the stricter requirement that demo affect only unsupported visual presentation fields; this is a documented deferment, not a full pass.

## 24. Performance/bundle result

Production chunks (minified / gzip): Customer **536.65 / 162.55 kB**; Restaurant **41.93 / 11.49 kB**; Driver **15.84 / 4.62 kB**; Admin **36.04 / 8.96 kB**. CSS **106.21 / 23.45 kB**. Role apps are already lazy-loaded. No obvious render loop or repeated library installation was identified.

`App.tsx` eagerly imports Customer screens including auth/forms/checkout. A bounded future split of the form-heavy Customer route group and/or RestaurantApplication could reduce the initial chunk, but shared Zod/form dependencies and actual navigation trade-offs require measurement. No unsupported byte-saving claim is made. The >500 kB advisory is not a High defect; no performance refactor was made under the High-only policy.

## 25. Accessibility result

QUALIFIED. Browser keyboard activation completed Customer checkout, Staff transitions, Admin provisioning/assignment and Driver completion. Confirmation/result headings receive focus; status labels include text, favorites expose pressed state; global focus-visible and reduced-motion CSS exist. Admin dialog initially focuses Reason, wraps Tab from its last button, and Escape closes it. Focus restoration fails (M7). Mobile Owner links lose accessible names (M6), Admin mobile exit is hidden, and small text/controls and validation associations need follow-up. No formal WCAG/contrast certification.

## 26. Responsive result

For every row below, all four widths were measured with `document.documentElement.scrollWidth <= clientWidth`; scrollbars account for some client widths being 15px narrower. These are loaded representative states, not a promise about all possible long user content.

| Area | Pages | 390 | 768 | 1024 | 1440 |
| --- | --- | --- | --- | --- | --- |
| Customer | Home, Restaurant, populated Cart, Checkout, Orders | Pass | Pass | Pass | Pass |
| Restaurant | Queue, loaded Menu, Branches, Staff | Pass | Pass | Pass | Pass |
| Driver | Dashboard, Active Delivery | Pass | Pass | Pass | Pass |
| Admin | Users, Restaurants, Orders, Drivers, Audit | Pass | Pass | Pass | Pass |

Screenshots inspected for narrow Customer/menu, Owner Menu, Driver delivery and Admin Users/Audit. Mobile Admin layout is cramped despite no document overflow (L2); Owner icon-only navigation is inaccessible (M6). Temporary browser viewport override was reset.

## 27. Backend full verification

Command: set `JAVA_HOME` to `C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot`, then run `.\mvnw.cmd verify` from the `backend` directory.

Final log: `backend/target/final-integration-verification.log` — BUILD SUCCESS, 5:56 elapsed, 21 September 2026 17:29:20 +03:00. Unit/web-slice: 58; integration: 209; both report zero failures/errors/skips. `backend/target/failsafe-reports/failsafe-summary.xml` confirms completed=209, errors=0, failures=0, skipped=0. Focused pre-fix H2 failure: `backend/target/final-mixed-role-before.log`.

Flyway V1–V16 were applied to the clean Compose database; all 16 history entries report success. The post-fix application image also migrated/validated successfully. No migration file changed. JVM/Mockito dynamic-agent advisories are test-runtime warnings, not skipped tests.

## 28. Frontend full verification

Completed after the final frontend security change: `npx tsc --noEmit`, `npm run lint`, `npm test`, `npm run build`, `npm audit --omit=dev`. All passed. **96 tests / 21 files**, zero failures; npm reports **0 vulnerabilities** in audited production dependencies.

Build advisories only: main chunk >500 kB; Rollup removes two uninterpretable PURE comments in Zod. No dependency upgrade or warning suppression was applied. Passed frontend checks were not rerun after the user's instruction to continue only remaining checks; subsequent production code changes were confined to the separately verified backend authorization fix.

## 29. Docker and real-stack smoke

Project: `tayyar-full-audit-20260921`; command `docker compose -p tayyar-full-audit-20260921 up --build -d`, local frontend origins explicitly allowed. Backend bound to 127.0.0.1:8080; Vite at 127.0.0.1:5173 proxies `/api`. PostgreSQL 18.6 and backend healthy; migration job exits successfully. Readiness/liveness/public discovery returned 200.

Before fixtures, database users count was 0. Existing load-test SQL was streamed with both TRUNCATE statements removed; only the isolated empty database received fixtures. No user database or Docker volume was reset. Additional audit records were created by real APIs and one local Driver identity fixture, followed by real UI provisioning.

HTTP smoke order: `fe724a0c-94dd-42af-be27-8c8bef18f47f`; browser order: `4640fa95-7d1c-4435-894b-7d8ca0a28d98`. Database verification for both: DELIVERED / PAID / COMPLETED / AVAILABLE. HTTP smoke additionally exercised registration, CSRF denial, role denials, Staff scope, idempotency, stale versions, review/favorites, suspension/reactivation, application approval, session invalidation and audit reads. Harness: `docs/audit/full-system-smoke.ps1`; resume parameters avoid repeating completed mutation stages. It is a disposable-fixture helper, not a replacement for integration tests.

Per user instruction, the audit Compose containers/volume and fixture state were **left intact**. No Docker state was deleted. The local frontend dev server was left available. Logs/build outputs are under existing target/dist directories.

## 30. Files changed

Production security fixes:

- `backend/src/main/java/com/tayyar/order/OrderOperationsAccess.java`
- `frontend/src/api/client.ts`
- `frontend/src/api/sessionCache.ts` (new)
- `frontend/src/app/SessionBoundary.tsx` (new)
- `frontend/src/app/App.tsx`
- `frontend/src/components/AppHeader.tsx`
- `frontend/src/pages/AuthPage.tsx`
- `frontend/src/pages/AccountPage.tsx`
- `frontend/src/pages/CheckoutPage.tsx`
- `frontend/src/driver/DriverApp.tsx`
- `frontend/src/admin/AdminApp.tsx`

Regression/evidence files:

- `backend/src/test/java/com/tayyar/order/OrderOperationsIT.java`
- `frontend/src/api/client.test.ts` (new)
- `frontend/src/api/sessionCache.test.ts` (new)
- `frontend/src/app/SessionBoundary.test.tsx` (new)
- `frontend/src/pages/AccountPage.test.tsx`
- `docs/audit/full-system-smoke.ps1` (new)
- `docs/final-full-system-integration-audit.md` (this report)

This inventory is based on edits made during this audit, not a Git comparison. Existing unrelated workspace changes were preserved.

## 31. Remaining accepted technical debt

**Acceptance has not been presumed.** M1–M9 and L1–L4 are proposed deferments under the user's High-only fix policy. In particular, numeric editing, durable uncertain checkout recovery, queue polling and mobile accessibility should be explicitly accepted or separately scheduled before calling the product fully polished. No new authorization was inferred to implement them.

## 32. Readiness for documentation / portfolio polish

**Yes, with the limitations in this report disclosed.** Core representative product flow, role boundaries after fixes, full automated gates and clean-stack migrations pass. Documentation and portfolio evidence can proceed without claiming every management/editor/a11y workflow is defect-free. An unconditional production-ready or accessibility-compliant claim is not supported. No additional Critical/High fix remains identified in the audited paths.

## 33. Recommended Conventional Commit

`fix(security): isolate session caches and enforce restaurant owner membership`

Recommendation only. No commit or other Git/GitHub operation was performed.
