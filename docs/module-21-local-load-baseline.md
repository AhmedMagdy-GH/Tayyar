# Module 21 — controlled local load-test baseline

Date: 2026-09-12 (Africa/Cairo). This is local capacity evidence only. The load
generator, backend, PostgreSQL, Docker Desktop, and Windows host shared one
physical machine. Results must not be interpreted as cloud or production
capacity, and they do not support the blanket claim “Tayyar supports 1,000
users.”

## 1. Host and test environment

- Host: Windows 11 Home 10.0.26200, 14 physical cores / 20 logical processors,
  15.7 GB RAM. No user name, device name, or other machine identifier recorded.
- Docker Desktop 4.68.0, Engine 29.3.1, Linux/WSL2 kernel 6.6.87.2.
- Docker allocation visible to the engine: 20 CPUs and 7.601 GiB RAM; no
  per-container CPU or memory limits in Compose.
- Java: Eclipse Temurin OpenJDK 21.0.11 LTS.
- PostgreSQL: 18.6 (Debian bookworm image).
- k6: pinned Docker image `grafana/k6:0.57.0`.

## 2. Untuned configuration recorded before load

- Hikari: no pool properties are set in application configuration. The observed
  effective maximum was 10 and idle baseline was 10. Framework defaults therefore
  apply (30 s connection timeout, 10 min idle timeout, 30 min max lifetime, 5 s
  validation timeout unless changed by the dependency defaults).
- Tomcat: `server.tomcat.connection-timeout=10s`; maximum threads, minimum spare
  threads, maximum connections, and accept count are not configured by Tayyar and
  therefore use the embedded-server defaults (nominally 200, 10, 8192, and 100).
- Graceful shutdown: 30 s. Session idle timeout: 30 min. Absolute authenticated
  lifetime: 12 h.
- PostgreSQL: `max_connections=100`, `statement_timeout=0`, `lock_timeout=0`,
  `deadlock_timeout=1s`, `shared_buffers=128 MiB`, `work_mem=4 MiB`, and
  `effective_cache_size=4 GiB`.
- Checkout limiter: 20 attempts per customer in a fixed 15-minute window. The
  counter is persisted in `auth_rate_limits` and increments before checkout.
- `pg_stat_statements` was not installed; only `plpgsql` was present. It was not
  enabled for this benchmark.
- Existing indexes relevant to the exercised work include restaurant/branch
  discovery indexes, branch-zone lookup indexes, category/item order indexes,
  customer address and order-history indexes, the active-cart partial unique
  index, checkout idempotency primary key, promotion redemption indexes, and
  active order/driver assignment partial unique indexes. No index was added.

## 3. Dataset and harness

The deterministic seed contains 60 users (40 customers, 1 admin, 1 owner, 10
drivers, 8 staff), 20 restaurants, 40 branches, 100 menu categories, 1,000 menu
items, 10 delivery zones, 200 service rules, 40 addresses, 21 promotions, 120
favorites, 800 notifications, and 800 seeded orders. The order set includes 600
delivered history rows, 100 PLACED operational rows, and 100 READY_FOR_PICKUP
rows. Ten ready orders have valid active assignments to ten BUSY drivers.

Tests ran in Compose project `tayyar-load`, which uses its own
`tayyar-load_tayyar-postgres-data` volume. `load-tests/seed.sql` resets only that
database. Authentication scenarios use the real login endpoint, JDBC-backed
sessions, CSRF tokens, and ordinary authorization. No security bypass exists.

Two early discovery/authentication attempts were rejected as harness validation
runs: one used incorrect discovery parameter names and another reused k6's
default cookie jar. Their artifacts were removed and their measurements are not
included below. The corrected low-load checks passed before staged execution.

## 4. Scenarios and load reached

- Health sanity: 5 VUs.
- Public discovery: staged 10, 25, 50, 100, 250, 500, 750, and 1,000 VUs,
  including a 15-second 1,000-VU plateau and cooldown. Each iteration issued six
  varied requests: page, search, zone filter, detail, branches, and effective menu.
- Authenticated reads: the same stages through 1,000 VUs using 20 independent real
  customer sessions. Each iteration read current user, addresses, cart, orders,
  notifications, and favorites.
- Cart writes: 10 then 20 independent customer VUs.
- CASH checkout: 20 independent customers, 10 iterations each (200 successful
  checkouts) with fresh cart and idempotency key per purchase.
- Idempotency: two concurrent equivalent requests with one customer, cart, and key.
- Promotion hotspot: 20 independent customers using one promotion, compared with
  20 customers using 20 different promotions.
- Restaurant operations: 100 independent orders; each performed PLACED → ACCEPTED
  → PREPARING → READY_FOR_PICKUP.
- Delivery: 10 independently assigned orders; each performed READY_FOR_PICKUP →
  OUT_FOR_DELIVERY → DELIVERED.
- Targeted races: cart update versus checkout and restaurant accept versus customer
  cancellation.
- Checkout rate limit: one customer repeatedly retried one valid idempotent request.

Menu/config update versus checkout, promotion last-slot over-subscription, driver
assignment contention, and duplicate delivery completion were not executed in
this run. They remain explicit follow-up scenarios; this report does not claim
coverage of those interleavings.

## 5. Results

Latency is milliseconds. RPS is the k6 aggregate over the complete run, including
ramp/cooldown and (for authenticated scenarios) setup requests. It is not a
production SLO.

| Scenario | Max VUs | Requests/s | HTTP p50 | HTTP p95 | HTTP p99 | Unexpected errors |
|---|---:|---:|---:|---:|---:|---:|
| Health sanity | 5 | 5,516.2 | 0.49 | 0.86 | not captured | 0 |
| Discovery staged | 1,000 | 2,372.9 | 470.65 | 2,467.26 | 3,703.11 | 0 |
| Authenticated reads staged | 1,000 | 2,081.0 | 548.15 | 2,901.14 | 3,934.22 | 0 |
| Cart reads/writes | 20 | 1,476.2 | 7.90 | 12.81 | 14.79 | 0 |
| CASH checkout (all requests) | 20 | 103.6 | 16.11 | 39.61 | 215.09 | 0 |
| Restaurant order transitions | 100 | 395.5 | 141.11 | 210.15 | 232.83 | 0 |
| Delivery transitions | 10 | 21.1 | 34.26 | 222.20 | 232.08 | 0 |

Checkout-specific latency excluded cart setup and login: p50 27.03 ms, p95
43.86 ms, p99 49.02 ms. All 200 attempted purchases succeeded; the k6 counter
rate was 31.4 successful checkouts/s when login/setup time was included. The
actual 200-operation burst completed in approximately 0.6 seconds, but that
short burst is not a sustainable-throughput claim.

The discovery plateau comparison located the first clear bend:

| Discovery plateau | Requests/s | HTTP p50 | HTTP p95 | HTTP p99 |
|---:|---:|---:|---:|---:|
| 10 VUs | 2,291.5 | 10.82 | 23.06 | 30.16 |
| 25 VUs | 2,361.4 | 34.84 | 70.57 | 95.43 |
| 50 VUs | 2,137.5 | 73.64 | 152.77 | 654.83 |

Throughput gained only 3.1% from 10 to 25 VUs while p95 tripled, then throughput
fell at 50 VUs. Significant local degradation therefore begins around 25 VUs
for this six-request discovery iteration. At 1,000 VUs the application remained
correct and available, but latency represented queueing, not additional useful
throughput.

## 6. Contention and correctness

- Idempotency: both duplicate responses were 201 with the same logical result.
  Database verification found exactly one receipt, one order, and one payment.
- Shared promotion: 20/20 checkouts and 20 unique redemptions succeeded. Checkout
  p50/p95/p99 was 109.36/145.97/150.39 ms.
- Distributed promotions: 20/20 succeeded. Checkout p50/p95/p99 was
  32.76/39.07/41.02 ms. The shared row was therefore about 3.3× slower at p50
  and 3.7× slower at p95 in this controlled burst, strong evidence that the
  promotion lock is a material local hotspot.
- Cart update versus checkout: the update won; checkout returned one expected
  409. The cart remained ACTIVE at version 1 and no receipt/order/payment was
  created.
- Accept versus cancel: accept won and cancellation returned one expected 409.
  The order ended ACCEPTED at version 1 with one committed transition.
- Restaurant operations: 100/100 independent orders completed all three legal
  transitions; the final seeded distribution contained 200 READY_FOR_PICKUP
  orders and no partially transitioned tested order.
- Delivery: 10/10 ended DELIVERED with PAID CASH payments, COMPLETED assignments,
  and AVAILABLE drivers.
- Rate limit: the first 20 equivalent attempts returned the cached successful
  checkout result; the next 822 requests returned expected 429 responses. This
  confirms the limiter would dominate a same-customer checkout benchmark.
- No duplicate purchase, payment, receipt, or promotion redemption was found.
  No over-redemption, data corruption, or invariant violation was observed.

k6's built-in `http_req_failed` counts expected 409 and 429 responses as failed
HTTP responses. The harness separately classified them: two expected 409s in
the targeted races and 822 expected 429s in the limiter test. The custom
`unexpected_failure` rate was zero in every retained result.

## 7. Hikari, PostgreSQL, JVM, and host observations

- During a correlated 500-VU discovery run, Hikari reached 10 active, 0 idle,
  and 188 pending acquisition requests. The cumulative Hikari timeout count was
  zero; maximum observed acquisition time was 130 ms.
- PostgreSQL exposed 11 connections including the observer, so the application
  used its full 10-connection pool but stayed far below PostgreSQL's limit of
  100. Operational samples showed zero PostgreSQL lock waiters.
- `pg_stat_database` ended with zero deadlocks and zero database conflicts.
  Four transaction rollbacks accumulated across the entire session, including
  expected/diagnostic request failures; no repeated abort pattern appeared.
- No backend 5xx response was present in the final 15-minute structured-log
  inspection. Backend and database containers did not crash or restart.
- Existing JVM metrics accumulated 2,288 GC pauses totaling 7.876 seconds, with
  a 5 ms maximum at final sampling. Final JVM used memory was about 452 MiB; the
  container working set rose from about 588 MiB at idle to a maximum sampled
  1.465 GiB, then stabilized around 1.43 GiB. No runaway memory trend or OOM was
  observed.
- PostgreSQL container memory stabilized around 134–190 MiB. Docker CPU samples
  are instantaneous and coarse; active discovery samples showed substantial
  multi-core use. Because generator, backend, database, and Docker shared the
  host, CPU percentages cannot be assigned to production tiers.

The first measured bottleneck is Hikari/application queueing under CPU-heavy
discovery work, visible by 25 VUs and severe by 50 VUs. PostgreSQL connection
capacity and row-lock waits were not the limiting signals. This does **not** mean
the correct next action is simply to enlarge the pool: the database and CPU cost
of each discovery iteration must be separated first.

Representative out-of-load `EXPLAIN (ANALYZE, BUFFERS)` checks found:

- Search across the 1,000-item dataset: 0.859 ms, 28 shared-buffer hits; the
  `%term%` predicates performed sequential scans over menu items/categories.
- Effective menu for 50 items: 0.086 ms, 21 buffer hits; category item retrieval
  used `menu_items_category_id_position_key`.
- Customer order history: 0.099 ms, 20 buffer hits; it used
  `orders_customer_history_idx` through a bitmap index scan.
- Checkout receipt lookup: 0.007 ms at the tiny post-reseed table; PostgreSQL
  chose a sequential scan because the relation was one page. This is not evidence
  against the composite primary key at realistic receipt scale.
- Promotion redemption count: 0.011 ms on an empty post-reseed relation; it is
  not a useful scale test. The concurrent checkout comparison is the stronger
  hotspot evidence.

## 8. What to investigate next (no tuning applied)

1. Add per-stage Hikari acquisition histograms and low-cardinality discovery
   operation tags to separate connection wait from SQL and response serialization.
2. Profile the six discovery operations independently at 10/25/50 VUs. The
   current batch scenario identifies the boundary but not which endpoint consumes
   most CPU/pool time.
3. Re-run with backend and PostgreSQL isolated from the generator, then compare
   CPU, pool wait, and throughput before considering any pool/thread change.
4. Expand search data by at least an order of magnitude and evaluate the
   `%term%` sequential-scan path with representative terms.
5. Run the unexecuted deterministic races: final promotion slot, menu/config
   update versus checkout, driver assignment contention, and duplicate delivery
   completion.
6. For the promotion hotspot, capture lock-wait duration around the promotion
   row at several concurrency levels before designing a change.

No index, query, Hikari, Tomcat, JVM, PostgreSQL, timeout, rate limit, schema, or
business-logic setting was changed.

## 9. Files added and reproduction

Added:

- `load-tests/main.js` — k6 scenarios and expected-error classification.
- `load-tests/seed.sql` — deterministic disposable dataset.
- `load-tests/observe.sql` — safe PostgreSQL operational snapshot.
- `load-tests/run.ps1` — pinned Docker runner and JSON summary export.
- `load-tests/README.md` — usage and isolation contract.
- `load-tests/results/*-summary.json` — retained valid raw k6 summaries.
- `docs/module-21-local-load-baseline.md` — this report.

Exact setup and principal runs from the repository root:

```powershell
docker compose -p tayyar-load up -d --build --wait
Get-Content -Raw load-tests/seed.sql | docker exec -i tayyar-load-postgres-1 psql -U postgres -d tayyar
docker pull grafana/k6:0.57.0
./load-tests/run.ps1 health '5:5s,5:10s,0:5s'
./load-tests/run.ps1 discovery '10:5s,10:10s,25:5s,25:10s,50:5s,50:10s,100:5s,100:10s,250:5s,250:10s,500:5s,500:10s,750:5s,750:10s,1000:5s,1000:15s,0:5s'
./load-tests/run.ps1 auth '10:5s,10:10s,25:5s,25:10s,50:5s,50:10s,100:5s,100:10s,250:5s,250:10s,500:5s,500:10s,750:5s,750:10s,1000:5s,1000:15s,0:5s'
./load-tests/run.ps1 cart '10:5s,10:15s,20:5s,20:20s,0:5s'
./load-tests/run.ps1 checkout
./load-tests/run.ps1 idempotency
./load-tests/run.ps1 promotion_shared
./load-tests/run.ps1 promotion_distributed
./load-tests/run.ps1 operations
./load-tests/run.ps1 delivery
./load-tests/run.ps1 cart_checkout_race
./load-tests/run.ps1 accept_cancel_race
./load-tests/run.ps1 ratelimit '1:2s,1:2s,0:1s'
Get-Content -Raw load-tests/observe.sql | docker exec -i tayyar-load-postgres-1 psql -U postgres -d tayyar
docker stats --no-stream tayyar-load-backend-1 tayyar-load-postgres-1
```

Reseed between write scenarios to reproduce the independent measurements. When
review is complete, remove only the benchmark project and its disposable volume:

```powershell
docker compose -p tayyar-load down -v --remove-orphans
```

After final verification, the `tayyar-load` containers, network, and disposable
database volume were removed. The JSON summaries and documentation remain for
inspection. No Git or GitHub operation was performed.
