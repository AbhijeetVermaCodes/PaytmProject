# ==============================================================================
# High-Concurrency Ticket Booking Engine — Automated Startup Wizard (PowerShell)
# ==============================================================================

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host "       HIGH-CONCURRENCY TICKET BOOKING SERVICE — AUTO STARTUP WIZARD            " -ForegroundColor Yellow -BackgroundColor Black
Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host ""

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

# 1. Check Java
Write-Host "[1/4] Checking Java JDK (JDK 17+)..." -ForegroundColor Cyan
try {
    $javaVer = java -version 2>&1
    Write-Host "   ✔ Java detected: $($javaVer[0])" -ForegroundColor Green
} catch {
    Write-Host "   ✘ Java not detected! Please install JDK 17+ from https://adoptium.net/" -ForegroundColor Red
    exit 1
}

# 2. Check Node.js & npm
Write-Host "`n[2/4] Checking Node.js & npm..." -ForegroundColor Cyan
try {
    $nodeVer = node -v
    $npmVer = npm -v
    Write-Host "   ✔ Node.js $nodeVer & npm $npmVer detected" -ForegroundColor Green
} catch {
    Write-Host "   ✘ Node.js / npm not detected! Please install Node.js 18+ from https://nodejs.org/" -ForegroundColor Red
    exit 1
}

# 3. Check Frontend node_modules
Write-Host "`n[3/4] Checking Frontend dependencies..." -ForegroundColor Cyan
if (-not (Test-Path "$ScriptDir\frontend\node_modules")) {
    Write-Host "   → Installing frontend npm dependencies..." -ForegroundColor Yellow
    Set-Location "$ScriptDir\frontend"
    npm install
    Set-Location $ScriptDir
    Write-Host "   ✔ Frontend dependencies installed." -ForegroundColor Green
} else {
    Write-Host "   ✔ Frontend dependencies ready." -ForegroundColor Green
}

# 4. Launch Services
Write-Host ""
Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host "           ALL PREREQUISITES VERIFIED! LAUNCHING SERVICES...                    " -ForegroundColor Green
Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "Starting Spring Boot Backend (Port 8080)..." -ForegroundColor Cyan
$backendJob = Start-Process -FilePath ".\mvnw.cmd" -ArgumentList "spring-boot:run" -PassThru -NoNewWindow

Write-Host "Starting Vite Frontend (Port 3000)..." -ForegroundColor Cyan
Set-Location "$ScriptDir\frontend"
$frontendJob = Start-Process -FilePath "npm" -ArgumentList "run", "dev" -PassThru -NoNewWindow
Set-Location $ScriptDir

Write-Host ""
Write-Host "================================================================================" -ForegroundColor Green
Write-Host "  🚀  SEATRUSH HIGH-CONCURRENCY TICKETING SERVICE IS LIVE & RUNNING!             " -ForegroundColor Green
Write-Host "================================================================================" -ForegroundColor Green
Write-Host ""
Write-Host "  🌐 Interactive Visualizer Dashboard:  http://localhost:3000" -ForegroundColor Cyan
Write-Host "  ⚙️  Spring Boot REST API Root:         http://localhost:8080" -ForegroundColor Blue
Write-Host "  🩺 Readiness Health Probe:            http://localhost:8080/health/ready" -ForegroundColor Green
Write-Host "  📊 Prometheus Metrics Stream:         http://localhost:8080/metrics" -ForegroundColor Yellow
Write-Host ""
Write-Host "Press [Ctrl + C] or close this window to stop services." -ForegroundColor Yellow
