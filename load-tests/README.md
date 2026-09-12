# Controlled local baseline load tests

These tests are measurement-only. They run against the isolated Compose project
`tayyar-load`, whose named PostgreSQL volume is distinct from the normal
`tayyar-local` volume. Results describe the combined application, PostgreSQL,
Docker, load generator, and host—not production capacity.

## Dataset

`seed.sql` deterministically creates 60 users (40 customers, 1 admin, 1 owner,
10 drivers, 8 staff), 20 restaurants, 40 branches, 100 menu categories, 1,000
menu items, 10 delivery zones, 200 service rules, 40 addresses, 21 promotions,
120 favorites, 800 notifications, and 800 historical/operational orders.

All accounts use the local-only password `Load test password!`. Never reuse it.

## Setup and execution

Run from the repository root in PowerShell:

```powershell
docker compose -p tayyar-load up -d --build --wait
Get-Content -Raw load-tests/seed.sql | docker exec -i tayyar-load-postgres-1 psql -U postgres -d tayyar
docker pull grafana/k6:0.57.0
./load-tests/run.ps1 health '5:5s,5:10s,0:5s'
./load-tests/run.ps1 discovery '10:5s,10:10s,25:5s,25:10s,50:5s,50:10s,100:5s,100:10s,250:10s,250:15s,500:10s,500:15s,750:10s,750:15s,1000:10s,1000:20s,0:10s'
```

Use shorter scenario-appropriate stages for write tests. Reseed before any test
that requires pristine carts/orders. The runner clears only the isolated test
database's rate-limit observation rows between separately reported scenarios;
the `ratelimit` scenario itself does not clear them during its run.

Inspect database state with:

```powershell
Get-Content -Raw load-tests/observe.sql | docker exec -i tayyar-load-postgres-1 psql -U postgres -d tayyar
docker stats --no-stream tayyar-load-backend-1 tayyar-load-postgres-1
docker exec tayyar-load-postgres-1 psql -U postgres -d tayyar -c 'SHOW max_connections'
```

Remove only benchmark resources when finished:

```powershell
docker compose -p tayyar-load down -v --remove-orphans
```
