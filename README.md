# High-Concurrency Ticket Booking Engine

An enterprise-grade, high-concurrency ticket reservation engine built with **Java 25 / Spring Boot 3.3.4**, **PostgreSQL 16**, **Micrometer Prometheus**, and **React 18 + Tailwind CSS**.

Engineered specifically to handle extreme on-sale stampedes (thousands of concurrent users contending for limited assigned seats) with **mathematical correctness**, **zero double-selling**, **deadlock elimination**, **per-user quota safety**, and **robust idempotency**.

---

## Architecture Highlights
- **Deterministic Row-Level Locking**: Automatically sorts requested seat IDs lexicographically before acquiring exclusive locks (`ORDER BY s.seatNumber ASC FOR UPDATE`), eliminating circular deadlocks across concurrent multi-seat requests.
- **Striped Per-User Concurrency Locks**: Serializes concurrent requests for the same user without blocking other users, ensuring strict enforcement of per-user ticket limits even during parallel bursts.
- **SHA-256 Payload-Validated Idempotency**: Caches successful responses by `Idempotency-Key` and compares SHA-256 request digests, rejecting mismatched payloads with `409 IDEMPOTENCY_PAYLOAD_MISMATCH`.
- **Atomic All-or-Nothing Partial Booking**: If even 1 of $N$ requested seats is already occupied, the transaction rolls back cleanly with a `409 CONFLICT` specifying the conflicting seats.
- **Strict Reconciliation Invariant**: Enforces `available_seats + held_seats + confirmed_seats == total_seats`.
- **Full Observability**: Live Prometheus telemetry at `/metrics` and `/actuator/prometheus`, with liveness and readiness health checks at `/health/live` and `/health/ready`.
- **Real-Time Interactive HUD & Visualizer**: Dark-mode visualizer with seat map, live telemetry stream, show creator, and built-in hot-seat contention simulator.

---

## Quick Start Guide

### Prerequisites
- **JDK 17, 21, or 25**
- **Node.js 18+ & npm** (for frontend)
- **Docker & Docker Compose** (optional, for full-stack containerization)

---

### Option 1: Running with Docker Compose (One-Command Launch)
To spin up PostgreSQL, the Spring Boot Backend, and the React Frontend:
```bash
docker-compose up --build
```
- **Backend API**: `http://localhost:8080`
- **Frontend Dashboard**: `http://localhost:5173`
- **Prometheus Metrics**: `http://localhost:8080/metrics`

---

### Option 2: Running Locally

#### 1. Start the Backend:
```bash
# On Linux / macOS:
./mvnw spring-boot:run

# On Windows:
.\mvnw.cmd spring-boot:run
```
The server will start on port `8080` using in-memory H2 for development or PostgreSQL as configured in `application.yml`.

#### 2. Start the Frontend Visualizer:
```bash
cd frontend
npm install
npm run dev
```
Open `http://localhost:5173` in your browser.

---

## High-Concurrency Load & Burst Testing

The repository includes a dedicated high-concurrency burst test harness (`BurstLoadRunner`) that fires hundreds of concurrent requests simultaneously and verifies all system invariants.

### Run via Shell Script (Linux / macOS / Git Bash):
```bash
chmod +x burst.sh
./burst.sh
```

### Run via PowerShell (Windows):
```powershell
.\burst.ps1
```

### Run via Maven:
```bash
.\mvnw.cmd test-compile exec:java -Dexec.mainClass="com.ticketbooking.loadtest.BurstLoadRunner" -Dexec.classpathScope=test
```

### Test Suite Output Summary:
```
================================================================================
                    HIGH-CONCURRENCY BURST TEST HARNESS                        
================================================================================
>>> Show initialized: show-id (Seats: 100, Max Per User: 4)
>>> Firing 50 concurrent threads contending for Hot Seat 'A1'...
[BURST RESULTS] Total Requests: 50 | 201 Created: 1 | 409 Conflicts: 49 | 5xx Errors: 0
[VERIFICATION] Invariant Check: Exactly 1 seat booked. PASS!
[VERIFICATION] Zero double-selling invariant: PASS!
[VERIFICATION] Reconciliation (available + held + confirmed == total): PASS!
================================================================================
```

---

## API Reference

### 1. Create a Show
```http
POST /shows
Content-Type: application/json
Authorization: Bearer test-token

{
  "name": "Coldplay World Tour 2026",
  "total_seats": 50,
  "max_seats_per_user": 4,
  "seat_price": 250000
}
```

### 2. Get Show Details & Seat Grid
```http
GET /shows/{show_id}
Authorization: Bearer test-token
```

### 3. Reserve Seats (Atomic & Idempotent)
```http
POST /shows/{show_id}/reserve
Content-Type: application/json
Authorization: Bearer test-token
Idempotency-Key: 7c9e6679-7425-40de-944b-e07fc1f90ae7

{
  "seats": ["A1", "A2"]
}
```

### 4. Cancel a Reservation
```http
POST /reservations/{reservation_id}/cancel
Authorization: Bearer test-token
```

### 5. Health & Observability Telemetry
- **Liveness Probe**: `GET /health/live`
- **Readiness Probe**: `GET /health/ready`
- **Prometheus Metrics**: `GET /metrics` or `GET /actuator/prometheus`

---

## Concurrency Invariants & Architecture Verification

Comprehensive architectural decisions, locking algorithms, idempotency models, failure recovery, CAP theorem trade-offs, and 2 AM paging runbooks are thoroughly documented in [WRITEUP.md](WRITEUP.md).

---

## Running Automated Tests
```bash
# Execute all unit, integration, and multi-threaded concurrency tests:
.\mvnw.cmd test
```
All 11 automated test suites will execute, covering single-winner contention storms, user limit bursts, deadlock avoidance, idempotency replays, mismatch rejections, and Prometheus metrics export.
