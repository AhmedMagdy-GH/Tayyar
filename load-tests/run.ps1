param(
  [ValidateSet('health','discovery','auth','cart','checkout','idempotency','promotion_shared','promotion_distributed','operations','delivery','ratelimit','cart_checkout_race','accept_cancel_race')]
  [string]$Scenario='health',
  [string]$Stages='10:10s,10:20s,0:5s'
)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$results=Join-Path $PSScriptRoot 'results'
New-Item -ItemType Directory -Force -Path $results | Out-Null

# Authentication rate-limit rows are test traffic, not configuration. Clearing
# them between independently reported scenarios avoids cross-scenario pollution.
docker exec tayyar-load-postgres-1 psql -U postgres -d tayyar -v ON_ERROR_STOP=1 -c 'TRUNCATE auth_rate_limits' | Out-Null
$stamp=Get-Date -Format 'yyyyMMdd-HHmmss'
$summary="/results/$Scenario-$stamp-summary.json"
docker run --rm --network tayyar-load_default `
  -v "${PSScriptRoot}:/scripts:ro" -v "${results}:/results" `
  -e "SCENARIO=$Scenario" -e "STAGES=$Stages" -e 'BASE_URL=http://backend:8080' `
  grafana/k6:0.57.0 run --summary-export $summary /scripts/main.js
