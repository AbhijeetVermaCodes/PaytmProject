# ==============================================================================
# One-Command High-Concurrency Burst Stampede Runner (PowerShell)
# Usage: .\burst.ps1 [BASE_URL]
# Example: .\burst.ps1 http://localhost:8080
# ==============================================================================

param(
    [string]$BaseUrl = "http://localhost:8080"
)

$BaseUrl = $BaseUrl.TrimEnd('/')

Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host "          HIGH-CONCURRENCY TICKET BOOKING SERVICE - BURST LOAD RUNNER          " -ForegroundColor Cyan
Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host "Target Base URL: $BaseUrl`n"

# Step 1: Health Readiness check
Write-Host "[Step 1/5] Checking service readiness ($BaseUrl/health/ready)..." -ForegroundColor Yellow
try {
    $readyRes = Invoke-RestMethod -Uri "$BaseUrl/health/ready" -Method Get -TimeoutSec 5 -ErrorAction Stop
    Write-Host "   --> Service is READY! (Database: $($readyRes.database))`n" -ForegroundColor Green
} catch {
    Write-Host "FATAL: Service readiness check failed: $_" -ForegroundColor Red
    exit 1
}

# Step 2: Create a fresh on-sale show with 50 seats
Write-Host "[Step 2/5] Creating fresh on-sale show with 50 seats (A1..A50)..." -ForegroundColor Yellow
$seatArray = 1..50 | ForEach-Object { "A$_" }
$showPayload = @{
    name = "burst-show-$((Get-Date).Ticks)"
    seats = $seatArray
    price_paise = 25000
    per_user_limit = 4
} | ConvertTo-Json -Compress

try {
    $showRes = Invoke-RestMethod -Uri "$BaseUrl/shows" -Method Post -Body $showPayload -ContentType "application/json" -Headers @{ "Authorization" = "Bearer admin" }
    $showId = $showRes.id
    Write-Host "   --> Created Show ID: $showId`n" -ForegroundColor Green
} catch {
    Write-Host "FATAL: Failed to create show: $_" -ForegroundColor Red
    exit 1
}

# Step 3: Storm 1 - Hot Seat Storm (500 users racing for seat A12)
Write-Host "[Step 3/5] Launching Storm 1: Hot-Seat Stampede (500 concurrent requests for seat A12)..." -ForegroundColor Yellow

$confirmed = 0
$declinedSeatTaken = 0
$declinedUserLimit = 0
$declinedIdempotency = 0
$other4xx = 0
$server5xx = 0

# Compile and invoke Java BurstLoadRunner for maximum throughput and accuracy
if (Test-Path ".\mvnw.cmd") {
    .\mvnw.cmd test-compile -q
    $cp = "target/classes;target/test-classes"
    java -cp $cp com.ticketbooking.loadtest.BurstLoadRunner "$BaseUrl"
} else {
    Write-Host "Running standalone PowerShell async requests..."
}
