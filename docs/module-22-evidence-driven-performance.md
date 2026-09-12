# Module 22 — evidence-driven performance investigation and tuning

Date: 2026-09-12 (Africa/Cairo)

## 1. Outcome and root cause

The accepted Module 21 baseline was preserved as the comparison point. The first
Discovery bottleneck was application/database work per response, not PostgreSQL's
connection limit: the six-request iteration executed 15 SQL statements, including
duplicate full candidate/count pipelines. At 500 VUs those requests filled Hikari's
10 connections and queued application threads, while PostgreSQL remained at 11 total
connections including the observer and showed no lock waiters or deadlocks.

The implemented Discovery rewrite reduces the normal page-0 iteration from 15 to 9
SQL statements (40%). It folds count and page work into one window-count query,
retains a count fallback for an empty nonzero page, omits delivery joins when there
is no location, and removes the branch-correlated override join from search when no
availability/category constraint needs it. Authorization, filter meaning,
deterministic ordering, pagination totals, and transaction isolation are unchanged.

No Promotion change was retained. A one-query usage-count experiment failed its
benchmark and was reverted. The Promotion row lock and final-slot invariant remain
unchanged.

## 2. Discovery SQL inventory and plans

| Request | Baseline statements | New normal-path statements | Shape |
|---|---:|---:|---|
| Restaurant list/search/zone/openNow | 2 | 1 | candidate branches → eligible → grouped restaurant cards; window total; ordered bounded page |
| Restaurant detail | 2 | 1 | same card shape with restaurant id predicate and size 1 |
| Branch choices | 3 | 2 | active restaurant existence check; eligible branch page with window total |
| Effective menu | 4 | 3 | header; category page with window total; one effective-item CTE/window batch |
| Delivery-zone list | 2 | 1 | active city/zone join with window total and bounded page |
| Saved-address location resolution | +1 | +1 | owned address id/user id lookup |

An empty page with a positive offset intentionally falls back to the separate count,
so its statement count is unchanged and its total remains correct.

Representative pre-change `EXPLAIN (ANALYZE, BUFFERS)` against the 20-restaurant,
40-branch, 100-category, 1,000-item seed found:

- Plain restaurant count: 1.497 ms, 5 shared hits; plain page: 0.948 ms,
  15 shared hits. Both performed the candidate/group work.
- Substring-search page: 1.741 ms, 347 shared hits. The search subplan was repeated
  after the restaurant/branch join and used the category/item relationship index;
  leading-wildcard `ILIKE` predicates were filters rather than index conditions.
- Zone-filter page: approximately 0.36 ms and 208 shared hits. The existing
  `(delivery_zone_id, branch_id)` and unique `(branch_id, delivery_zone_id)` indexes
  support the relationship lookup; fixture cardinality also makes sequential scans
  rational for several tiny relations.
- Restaurant detail and branch page: approximately 0.13 ms execution each at this
  fixture size. Restaurant/branch primary/relationship indexes exist; PostgreSQL
  often chose a small-relation sequential scan.
- Effective menu header/count/category page: 0.021/0.037/0.029 ms. The 50-item
  batch was 0.322 ms and 24 shared hits; PostgreSQL scanned the 1,000-row item table
  in that plan because it was cheaper at the seed size, while other representative
  plans use `menu_items_category_id_position_key`.

The exact post-change fixture-scale plans captured by `DiscoveryIT` were one query
at 0.215 ms/43 shared hits for listing, one at 0.269 ms/49 hits for search, one at
0.270 ms/114 hits for zone listing, and three menu statements at
0.051/0.043/0.164 ms. No new index is supported by this evidence. In particular,
`pg_trgm` was rejected: `%term%` is measurable, but at the current catalog size the
larger demonstrated waste was redundant/correlated work and the resulting throughput
gain did not require an extension.

## 3. Discovery before/after

The 10/25/50 rows use the same ramp/10-second plateau/ramp-down shape as their
retained Module 21 artifacts. The 500 row compares the retained correlated 500-VU
run with the same 5-second ramp, 15-second plateau, and 5-second cooldown used for
the official post-change run. Errors are unexpected errors.

| Scenario | Baseline RPS | New RPS | Difference | Baseline p50 | New p50 | Baseline p95 | New p95 | Baseline p99 | New p99 | Baseline errors | New errors |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Discovery 10 VUs | 2,291.47 | 4,019.23 | +75.4% | 10.82 | 6.16 | 23.06 | 13.91 | 30.16 | 18.44 | 0 | 0 |
| Discovery 25 VUs | 2,361.37 | 4,712.76 | +99.6% | 34.84 | 11.60 | 70.57 | 32.57 | 95.43 | 48.20 | 0 | 0 |
| Discovery 50 VUs | 2,137.51 | 4,691.14 | +119.5% | 73.64 | 21.90 | 152.77 | 65.31 | 654.83 | 104.71 | 0 | 0 |
| Discovery 100 VUs | not retained | 4,522.45 | n/a | not retained | 38.04 | not retained | 135.59 | not retained | 223.44 | not retained | 0 |
| Discovery 500 VUs | 2,388.17 | 4,557.09 | +90.8% | 1,082.14 | 543.51 | 1,218.91 | 661.30 | 1,370.32 | 726.83 | 0 | 0 |

The accepted baseline did not retain an isolated 100-VU plateau artifact; inventing
a baseline value would not be an apples-to-apples comparison. The required 100-VU
post-change run is reported above.

Search remained the slowest operation at 10 VUs (p95 14.94 ms), but only narrowly:
the other five endpoint p95s were 13.13–14.34 ms. At 50 VUs the six operation p95s
were 63.09–67.21 ms. This is no longer evidence for a search extension at the
current data size.

## 4. Hikari, PostgreSQL, CPU, and memory

At a sustained 500-VU post-change sample with pool 10:

- Hikari: 10 active, 0 idle, 185 pending, zero timeouts.
- PostgreSQL: 11 connections including the observer; a representative sample had
  3 executing sessions, other application sessions in client read/idle states, and
  no database lock waiter.
- `pg_stat_database`: zero deadlocks and zero conflicts.
- Docker samples: backend approximately 585–587% CPU and 0.92–1.12 GiB; PostgreSQL
  approximately 657–675% CPU and 126–127 MiB.
- Module 21 at 500 VUs: 10 active, 188 pending, zero Hikari timeouts; backend peak
  sampled working set 1.465 GiB and PostgreSQL 134–190 MiB.

Pending acquisition count therefore did not materially fall (188 → 185), but each
connection completed much more useful work: 500-VU throughput increased 90.8%.
PostgreSQL's 100-connection limit was not approached. The high-load boundary is now
CPU/database work plus pool queueing, not a demonstrated server-thread, heap, or
PostgreSQL connection-limit bottleneck.

### Controlled Hikari A/B

Only the isolated benchmark backend was temporarily restarted; production/default
configuration was not changed.

| Pool | 500-VU RPS | p50 | p95 | p99 | Errors |
|---:|---:|---:|---:|---:|---:|
| 10 | 4,557.09 | 543.51 | 661.30 | 726.83 | 0 |
| 15 | 4,764.14 | 434.19 | 610.47 | 751.41 | 0 |
| 20 | 5,381.90 | 404.92 | 588.58 | 706.63 | 0 |

Pool 20 improved this single shared-host 500-VU run by 18.1% and reduced p95 by
11.0% versus pool 10. It is not retained: backend, database, generator, and Docker
shared one host; CPU was already multi-core saturated; the practical low-concurrency
capacity result does not require 20 connections; and the result lacks isolated-host
replication. Pool 10 remains the default pending production-like validation.

No Tomcat thread, JVM memory, PostgreSQL limit, or timeout setting changed.

## 5. Promotion serialization and lock order

Shared Promotion p95 remains about three times distributed Promotion p95. The cause
is the required `SELECT ... FOR UPDATE` on one Promotion row, held from the final
eligibility/usage decision through order, snapshot/redemption, payment, cart consume,
receipt, and transaction commit. Total and per-customer counts occur after the lock,
so a waiter observes committed prior redemptions and the final slot cannot be
overdrawn.

| Promotion scenario | Baseline RPS | New RPS | Difference | Baseline checkout p50 | New p50 | Baseline p95 | New p95 | Baseline p99 | New p99 | Errors |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Shared Promotion | 25.78 | 24.54 | -4.8% | 109.36 | 120.25 | 145.97 | 153.70 | 150.39 | 155.10 | 0 |
| Distributed Promotions | 25.76 | 19.52 | -24.2% | 32.76 | 43.54 | 39.07 | 50.68 | 41.02 | 50.79 | 0 |

RPS includes login/setup for these one-iteration scenarios and is especially noisy.
All 20/20 checkouts succeeded in both final runs. The absolute latency regression
means no Promotion performance improvement is claimed. The approximately 3× shared
versus distributed gap still isolates row serialization.

An attempted conditional aggregate combined total/customer usage counts into one
round trip, but warmed p95 was 172.40 ms shared and 65.67 ms distributed. It was
reverted. Removing the lock, reserving outside the purchase transaction, adding
counters, or changing lock timing was rejected because current evidence does not
justify the extra final-slot/rollback/lock-order risk.

Promotion lock acquisition did not change, so the audited hierarchy remains:
User update → Cart update → configuration share locks → Address/City/Zone/Rule share
locks → Promotion update → new Order/redemption/payment → Cart/receipt writes. No new
Checkout ↔ Promotion ↔ Cart/Order edge or deadlock cycle was introduced.

## 6. Checkout regression and correctness

The first post-restart Checkout run was a cold outlier and is not used as the final
comparison. The warmed, reseeded final run was:

| Scenario | Baseline RPS | New RPS | Difference | Baseline checkout p50 | New p50 | Baseline p95 | New p95 | Baseline p99 | New p99 | Errors |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Checkout, 200 purchases | 103.64 | 121.52 | +17.3% | 27.03 | 34.22 | 43.86 | 47.31 | 49.02 | 53.53 | 0 |

All 200/200 purchases succeeded. Promotion and Checkout production behavior is
unchanged. Existing focused tests cover final-slot race, total and per-customer
limits, idempotent retry, failed-checkout rollback, unique redemption/order/payment,
and immutable historical totals. The Discovery test now also verifies reduced query
counts and correct totals on empty high-offset restaurant and branch pages.

## 7. Verification, schema, and errors

- Full Maven command: `mvnw.cmd -B -ntp '-Dmaven.repo.local=C:\Users\DEATHX7\.m2\repository' verify`.
- Final result: **BUILD SUCCESS**, exit 0, Maven elapsed **04:53 min**, finished
  **2026-09-12 08:35:39 +03:00**. Surefire: 58 tests; Failsafe: 201 tests;
  **259 total, 0 failures, 0 errors, 0 skipped**.
- Schema/index changes: none. V1–V15 are unchanged; no Flyway migration was needed.
- Load scenarios: zero unexpected failures and zero HTTP 5xx in retained final runs;
  final container-log scan also found zero `status=5xx` request lines.
- Database observations: zero deadlocks and zero conflicts.
- Final database checks found 20 redemptions for 20 distinct orders, zero orders with
  duplicate Payments, zero duplicate `(customer,idempotency_key)` receipts, and zero
  Promotions above their total limit. No invariant was weakened.

## 8. Rejected changes and remaining bottlenecks

- `pg_trgm`/full-text index: rejected at this scale; substring search was measurable
  but not dominant after removing redundant/correlated work.
- Promotion usage-count aggregation: benchmark regression; reverted.
- Promotion lock removal/early reservation/counters: correctness and rollback risk
  without sufficient evidence; rejected.
- Permanent pool 15/20: useful local A/B signal, insufficient production evidence;
  rejected for configuration.
- Tomcat/JVM/PostgreSQL limit increases: no evidence they are the limiter; rejected.

Remaining bottlenecks are CPU/database work and Hikari queueing above roughly
50–100 mixed Discovery VUs on this shared host, leading-wildcard search at catalog
sizes much larger than this seed, and unavoidable shared-Promotion row serialization
under exact-code bursts.

## 9. Files changed and capacity recommendation

- `backend/src/main/java/com/tayyar/discovery/DiscoveryQuery.java`
- `backend/src/test/java/com/tayyar/discovery/DiscoveryIT.java`
- `load-tests/main.js`
- `load-tests/module22-explain.sql`
- `load-tests/compose-pool-override.yaml`
- `load-tests/pool15.env`
- `load-tests/pool20.env`
- `load-tests/results/*-summary.json` (new Module 22 run artifacts)
- `docs/module-22-evidence-driven-performance.md`

Measured local capacity recommendation: use about 25 mixed Discovery VUs as the
low-latency local operating point (4,713 RPS aggregate, p95 32.57 ms in this harness).
50 VUs remains usable at 4,691 RPS/p95 65.31 ms. At 100 VUs throughput has already
flattened to 4,522 RPS while p95 doubles to 135.59 ms; 500 VUs is a queueing/soak
condition, not useful-capacity evidence. These are shared-host local measurements,
not production SLOs or deployment sizing.

Recommended Conventional Commit message:

`perf(discovery): eliminate redundant paged read queries`

No commit and no Git/GitHub operation was performed.
