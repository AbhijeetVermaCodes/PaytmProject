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
- **Event Proposal & Admin Approval Workflow**: Regular users submit proposals (`POST /event-requests`). Only on Admin review & approval (`POST /event-requests/{id}/approve`) does the event go live. The submitting user is appointed the **Event Manager** with seat roster inspection and booking cancellation powers for their event.
- **Full Observability**: Live Prometheus telemetry at `/metrics` and `/actuator/prometheus`, with liveness and readiness health checks at `/health/live` and `/health/ready`.
- **Real-Time Interactive HUD & Visualizer**: Dark-mode visualizer with seat map, SweetAlert2 modal receipts and conflict alerts, live telemetry stream, approval queue, and built-in hot-seat contention simulator.

---

## Technology Stack

### Backend & Core Platform
- **Java 17 / 18 / 25**: Core runtime language with native virtual thread / concurrency capabilities.
- **Spring Boot 3.3.4**: Modern microservice web framework with graceful shutdown support.
- **Spring Web (MVC)**: REST API layer with RFC 7807 compliant error responses.
- **Spring Data JPA & Hibernate**: ORM with deterministic row-level write locks (`PESSIMISTIC_WRITE`).
- **Spring Validation (Jakarta)**: Robust schema and payload constraints.
- **Jackson & jsr310**: High-speed JSON serialization and canonical SHA-256 payload caching.
- **SLF4J & MDC**: Distributed correlation and user request tracing via `X-Request-Id`.

### Database & Persistence
- **PostgreSQL 16**: Primary production ACID relational store.
- **H2 In-Memory Database**: Embedded PostgreSQL-mode database for zero-config local testing.
- **HikariCP**: Production-tuned connection pool with strict timeout bounds.

### Observability & Telemetry
- **Spring Boot Actuator**: Health probes (`/health/live`, `/health/ready`).
- **Micrometer & Prometheus**: Live real-time application metrics export (`/metrics`, `/actuator/prometheus`).

### Frontend & Visualizer
- **React 18.3 & TypeScript 5.5**: High-performance typed component architecture.
- **Vite 5.4**: Lightning-fast build tool and API reverse-proxy dev server.
- **Tailwind CSS 3.4**: Responsive dark-mode styling with glassmorphic aesthetics.
- **SweetAlert2**: Interactive dark-theme transaction receipts, conflict modals, and confirmation dialogs.
- **Lucide React**: Modern iconography for seats, telemetry, and status indicators.

### DevOps, Tooling & Load Testing
- **Docker & Docker Compose**: Full multi-container containerization with health checks.
- **JUnit 5 & Spring Boot Test**: Multi-threaded concurrency and integration test suites.
- **Custom Java Load Runner (`BurstLoadRunner`)**: Multi-threaded stampede load generator (up to 1,500 parallel requests).
- **Maven Wrapper (`./mvnw`)**: Portable build automation.

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
- **Frontend Dashboard**: `http://localhost:3000` (or `http://localhost:5173`)
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
Open `http://localhost:3000` in your browser.

---

## Role-Based Access Control & Event Workflow

| Capability | Regular User | Event Manager (Creator) | System Administrator |
| :--- | :---: | :---: | :---: |
| **Browse & Reserve Seats** | ✅ (Subject to limit) | ✅ (Subject to limit) | ✅ (Subject to limit) |
| **Submit Event Proposal** | ✅ (`POST /event-requests`) | ✅ (`POST /event-requests`) | ✅ (Can also direct create) |
| **Direct Show Creation** | ❌ (Requires Approval) | ❌ (Requires Approval) | ✅ (`POST /shows`) |
| **Approve / Reject Proposals** | ❌ | ❌ | ✅ (`/approve`, `/reject`) |
| **Customer Seat Roster Audit** | ❌ (Masked for privacy) | ✅ (Own event only) | ✅ (All events) |
| **Cancel Customer Bookings** | ❌ (Own bookings only) | ✅ (Own event only) | ✅ (All events) |

---

## API Reference

### 1. Submit Event Proposal (Regular User)
```http
POST /event-requests
Content-Type: application/json
Authorization: Bearer user-alice

{
  "name": "Indie Music Night",
  "seats": ["A1", "A2", "A3", "B1", "B2"],
  "price_paise": 35000,
  "per_user_limit": 4
}
```

### 2. Approve Event Proposal (Admin Only)
```http
POST /event-requests/{proposal_id}/approve
Authorization: Bearer admin
```

### 3. Direct Show Creation (Admin Only)
```http
POST /shows
Content-Type: application/json
Authorization: Bearer admin

{
  "name": "Coldplay World Tour 2026",
  "seats": ["A1", "A2", "A3", "A4", "A5", "B1", "B2", "B3", "B4", "B5"],
  "price_paise": 250000,
  "per_user_limit": 4
}
```

### 4. Get Show Details & Seat Grid
```http
GET /shows/{show_id}
Authorization: Bearer user-alice
```
*Note: If the requesting user is an Admin or the Event Manager, `booked_by` and `reservation_id` fields are revealed on occupied seats.*

### 5. Reserve Seats (Atomic & Idempotent)
```http
POST /shows/{show_id}/reserve
Content-Type: application/json
Authorization: Bearer user-alice
Idempotency-Key: 7c9e6679-7425-40de-944b-e07fc1f90ae7

{
  "seats": ["A1", "A2"]
}
```

### 6. Cancel a Reservation
```http
POST /reservations/{reservation_id}/cancel
Authorization: Bearer user-alice
```
*Allowed for the reserving user, the Event Manager, or an Admin.*

### 7. Health & Observability Telemetry
- **Liveness Probe**: `GET /health/live`
- **Readiness Probe**: `GET /health/ready`
- **Prometheus Metrics**: `GET /metrics` or `GET /actuator/prometheus`

---

## Running Automated Tests
```bash
# Execute all unit, integration, and multi-threaded concurrency tests:
.\mvnw.cmd test
```
All 14 automated test suites will execute, covering single-winner contention storms, user limit bursts, deadlock avoidance, idempotency replays, event proposal submissions, admin approval workflows, organizer manager permissions, and Prometheus metrics export.
