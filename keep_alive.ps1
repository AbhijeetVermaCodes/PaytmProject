# ==============================================================================
# Render Service Keep-Alive Daemon & Monitor (PowerShell)
#
# Pings the backend health endpoint and frontend every 5 minutes (300s)
# to keep Render free tier services awake and prevent cold-start delays.
# ==============================================================================

param(
    [string]$BackendUrl = "https://ticket-booking-backend-mvhs.onrender.com",
    [string]$FrontendUrl = "https://ticket-booking-frontend-e57y.onrender.com",
    [int]$IntervalSeconds = 300,
    [switch]$Once
)

function Ping-Services {
    $timestamp = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
    
    # 1. Backend ping
    $beSw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $beRes = Invoke-RestMethod -Uri "$BackendUrl/health/ready" -Method Get -TimeoutSec 20 -ErrorAction Stop
        $beSw.Stop()
        $beLatency = $beSw.ElapsedMilliseconds
        if ($beRes.status -eq "UP" -and $beRes.database -eq "CONNECTED") {
            Write-Host "[$timestamp] " -NoNewline
            Write-Host "✔ BACKEND " -ForegroundColor Green -NoNewline
            Write-Host "| HTTP 200 | ${beLatency}ms | Status: UP (DB: CONNECTED)" -ForegroundColor Green
        } else {
            Write-Host "[$timestamp] " -NoNewline
            Write-Host "⚠ BACKEND " -ForegroundColor Yellow -NoNewline
            Write-Host "| HTTP 200 | ${beLatency}ms | Status: DEGRADED" -ForegroundColor Yellow
        }
    } catch {
        $beSw.Stop()
        $beLatency = $beSw.ElapsedMilliseconds
        Write-Host "[$timestamp] " -NoNewline
        Write-Host "✘ BACKEND " -ForegroundColor Red -NoNewline
        Write-Host "| ${beLatency}ms | Failed: $($_.Exception.Message)" -ForegroundColor Red
    }

    # 2. Frontend ping
    $feSw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $feRes = Invoke-WebRequest -Uri "$FrontendUrl/" -Method Get -TimeoutSec 15 -ErrorAction Stop
        $feSw.Stop()
        $feLatency = $feSw.ElapsedMilliseconds
        Write-Host "[$timestamp] " -NoNewline
        Write-Host "✔ FRONTEND" -ForegroundColor Green -NoNewline
        Write-Host "| HTTP $($feRes.StatusCode) | ${feLatency}ms | OK" -ForegroundColor Green
    } catch {
        $feSw.Stop()
        $feLatency = $feSw.ElapsedMilliseconds
        Write-Host "[$timestamp] " -NoNewline
        Write-Host "⚠ FRONTEND" -ForegroundColor Yellow -NoNewline
        Write-Host "| ${feLatency}ms | Response: $($_.Exception.Message)" -ForegroundColor Yellow
    }
}

if ($Once) {
    Ping-Services
    exit 0
}

Write-Host "======================================================" -ForegroundColor Cyan
Write-Host "   Render Service Keep-Alive Monitor (Every ${IntervalSeconds}s)   " -ForegroundColor Green
Write-Host "======================================================" -ForegroundColor Cyan
Write-Host "Backend:  $BackendUrl/health/ready" -ForegroundColor Gray
Write-Host "Frontend: $FrontendUrl" -ForegroundColor Gray
Write-Host "Interval: ${IntervalSeconds}s (Pings before the 15-minute sleep threshold)`n" -ForegroundColor Gray

$cycle = 1
while ($true) {
    Write-Host "--- Ping Cycle #$cycle ---" -ForegroundColor Cyan
    Ping-Services
    $cycle++
    Write-Host "Waiting ${IntervalSeconds}s until next ping...`n" -ForegroundColor DarkGray
    Start-Sleep -Seconds $IntervalSeconds
}
