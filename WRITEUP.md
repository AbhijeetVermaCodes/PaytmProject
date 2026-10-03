# High-Concurrency Ticket Booking Service: Architectural Write-Up & Deep Dive

## 1. Executive Summary & Design Goals
The **Ticket Booking Service** is an enterprise-grade, high-throughput, ACID-compliant distributed seat reservation engine built for flash-sale conditions (thousands of concurrent users stampeding a limited inventory of reserved seats simultaneously).

### Core Invariants Guaranteed Under Any Concurrency Load:
1. **Zero Double-Selling**: A seat cannot be simultaneously held or booked by more than one user under any burst condition.
2. **Deterministic Deadlock-Free Multi-Seat Allocation**: Multi-seat bookings (`["A1", "A2", "B1"]`) are sorted before locking, guaranteeing a global lock acquisition order across all transactions.
3. **Atomic All-or-Nothing Partial Booking**: If even 1 of $N$ requested seats is already occupied, the transaction immediately rolls back cleanly, returning a `409 CONFLICT` with precise conflicting seat identifiers.
4. **Strict Per-User Quota Enforcement**: A user cannot circumvent the maximum allowed ticket limit (e.g. 4 seats) via parallelized concurrent burst requests.
5. **Exact Idempotency & Mismatch Rejection**: Duplicate requests with identical keys return the cached `201 CREATED` response without re-executing inventory writes. Requests with the same key but differing payload parameters are rejected with `409 IDEMPOTENCY_PAYLOAD_MISMATCH`.
6. **Reconciliation Invariant**: At all instants in time:
   $$\text{Available Seats} + \text{Held Seats} + \text{Confirmed Seats} = \text{Total Seats}$$
7. **Monetary Precision**: All currency calculations (seat prices, reservation totals, refunds) use integer paise / minor currency units to prevent floating-point drift.

---

## 2. High-Level Architecture & Component Flow

```
                                  [ HTTPS Traffic / CDN ]
                                             │
                                             ▼
                          [ Spring Boot 3.3.4 Application ]
                       ┌─────────────────────────────────────┐
                       │  • AuthFilter (Bearer + MDC Tracing)│
                       │  • MetricsService (Micrometer)      │
                       │  • GlobalExceptionHandler (RFC 7807)│
                       └──────────────────┬──────────────────┘
                                          │
                                          ▼
                             [ BookingService (ACID) ]
                       ┌─────────────────────────────────────┐
                       │ 1. User-Show Striped Lock           │
                       │ 2. SHA-256 Canonical Idempotency    │
                       │ 3. Sorted PESSIMISTIC_WRITE Locks   │
                       │ 4. Active Quota Verification        │
                       │ 5. Atomic State Transition          │
                       └──────────────────┬──────────────────┘
                                          │
                                          ▼
                       [ PostgreSQL 16 / Multi-Core DB ]
                       ┌─────────────────────────────────────┐
                       │  • shows (id, name, max_per_user)   │
                       │  • seats (UNIQUE: show_id, seat_no) │
                       │  • reservations (status, amount)    │
                       │  • idempotency_records (key, hash)  │
                       └─────────────────────────────────────┘
```

---

## 3. The Atomic Decision Mechanism: Race-Free Seat Allocation

### 3.1 The Concurrency Challenge
When 1,000 threads simultaneously attempt to reserve the same seat (`A1`):
- Naive `SELECT ...` followed by `UPDATE ...` creates classic **Read-Modify-Write (Lost Update)** race conditions.
- Optimistic Locking (`@Version`) under high contention causes 99% of threads to throw `OptimisticLockException` and waste database CPU on repeated retries.
- Naive multi-seat Pessimistic Locking can cause **Deadlocks** (Cycle in Wait-For Graph: User 1 locks `A1` and waits for `A2`; User 2 locks `A2` and waits for `A1`).

### 3.2 The Solution: Deterministic Row-Level Locking
To achieve maximum throughput without deadlocks or race conditions:

1. **Input Normalization & Canonical Sorting**:
   When a user requests `["C3", "A1", "B2"]`, the seat list is immediately deduplicated and sorted lexicographically:
   $$\text{Sorted Seats} = [ \text{"A1"}, \text{"B2"}, \text{"C3"} ]$$
2. **Pessimistic Row-Level Lock Acquisition**:
   The query executes with explicit row-level exclusive locks in ascending alphabetical order:
   ```sql
   SELECT s FROM Seat s 
   WHERE s.show.id = :showId AND s.seatNumber IN :seatNumbers 
   ORDER BY s.seatNumber ASC
   FOR UPDATE
   ```
3. **Wait-For Graph Deadlock Elimination**:
   Because every concurrent transaction acquires row locks in identical alphabetical order, circular wait conditions ($T_1 \to T_2 \to T_1$) are mathematically impossible:
   $$\forall T_i, T_j: \text{LockOrder}(T_i) = \text{LockOrder}(T_j) \implies \text{Wait-For Graph is a DAG (Acyclic)}$$
4. **All-or-Nothing Atomicity**:
   Once rows are locked:
   - If `count(lockedSeats) != requestedSeats.size()`, non-existent seats are flagged $\to$ abort with `404 NOT_FOUND`.
   - If any locked seat has `status != AVAILABLE`, all conflicting seats are collected $\to$ transaction rolls back cleanly with `409 CONFLICT` and response payload:
     ```json
     {
       "error_code": "SEATS_ALREADY_RESERVED",
       "message": "One or more requested seats are unavailable",
       "conflicting_seats": ["A1", "B2"]
     }
     ```
   - If all seats are `AVAILABLE`, all seats transition to `CONFIRMED` (or `HELD`), reservation record is inserted, and the transaction commits in a single round-trip.

---

## 4. Per-User Quota Serialization (Preventing Phantom Limit Exceedance)

### 4.1 The Phantom Quota Vulnerability
Suppose a show has a `max_seats_per_user = 4`. A malicious or automated user fires 10 concurrent requests at the exact same millisecond, each booking 1 distinct seat (`A1`, `A2`, `A3`, ..., `A10`):
- Because the seats are completely distinct, seat row locks do **not** conflict with each other!
- If each thread concurrently queries `SELECT SUM(seat_count) FROM reservations WHERE user_id = :u` before any thread commits, all 10 threads read `active_seats = 0`.
- All 10 threads commit, resulting in the user booking 10 seats instead of the allowable 4!

### 4.2 The Solution: Striped In-Memory Concurrency Guards
To prevent this without introducing global database table-level locks:
1. A striped concurrency mutex pool (`Striped<Lock>` / concurrent key lock keyed by `userId + ":" + showId`) serializes multi-threaded requests originating from the *same user* targeting the *same show*.
2. Requests from *different users* execute concurrently with zero synchronization overhead or contention.
3. Within the user's critical section, the database calculates active reservations:
   $$\text{active\_seats} = \sum_{\text{status} \in \{\text{HELD}, \text{CONFIRMED}\}} \text{reservation.seatCount}$$
4. If $\text{active\_seats} + \text{requested\_seats} > \text{max\_seats\_per\_user}$, the request is rejected with `409 USER_LIMIT_EXCEEDED`:
   ```json
   {
     "error_code": "USER_LIMIT_EXCEEDED",
     "message": "User user-42 has 3 active seats, cannot reserve 2 more (max 4 allowed per user)",
     "status": 409
   }
   ```

---

## 5. End-to-End Idempotency Engine

Network instability, mobile client timeouts, and automatic retry loops regularly cause duplicate HTTP requests.

### 5.1 Protocol & Storage Specifications
- **Header**: Clients provide `Idempotency-Key: <UUIDv4>` with every mutation request (`POST /shows/{id}/reserve`).
- **Canonical Hash**: The server computes a SHA-256 digest over the canonical request body:
  $$\text{RequestHash} = \text{SHA-256}(\text{CanonicalJson}(\text{showId}, \text{sorted(seats)}))$$

### 5.2 State Transitions & Handling Matrix
```
Client Request (Key: K, Hash: H)
       │
       ├──► Check idempotency_records WHERE idempotency_key = K
       │
       ├───► [Found Record]:
       │       ├── If record.request_hash == H:
       │       │      └── Return cached status (201) & cached JSON body (Zero DB writes)
       │       └── If record.request_hash != H:
       │              └── Return 409 IDEMPOTENCY_PAYLOAD_MISMATCH (Reject tampering)
       │
       └───► [No Record Found]:
               ├── Insert Idempotency Record (Status: IN_PROGRESS)
               ├── Execute Business Transaction (Lock Seats, Create Reservation)
               ├── Update Idempotency Record (Status: COMPLETED, ResponseBody: JSON)
               └── Return 201 CREATED
```

---

## 6. Seat Hold Lifecycle & Background Expiry Worker

For reservation workflows with two-phase checkout (Hold $\to$ Payment $\to$ Confirmation):

### 6.1 State Transition Diagram
```
              ┌───────────────────────────┐
              │         AVAILABLE         │
              └─────────────┬─────────────┘
                            │ Reserve (Hold with TTL)
                            ▼
              ┌───────────────────────────┐
              │           HELD            │
              └──────┬─────────────┬──────┘
                     │             │
   Confirm / Settle  │             │ TTL Expiry / Cancel
                     ▼             ▼
  ┌────────────────────┐   ┌───────────────────────────┐
  │     CONFIRMED      │   │   CANCELLED / AVAILABLE   │
  └────────────────────┘   └───────────────────────────┘
```

### 6.2 Expiry Engine Implementation
- Holds store an `expires_at` timestamp (default: 5 minutes from creation).
- A non-blocking asynchronous scheduled background task (`SeatHoldExpiryWorker`) sweeps expired holds in indexed batches:
  ```sql
  SELECT r FROM Reservation r 
  WHERE r.status = 'HELD' AND r.expiresAt < :now
  ```
- Expired reservations transition to `CANCELLED`, and associated seat statuses transition back to `AVAILABLE` inside an atomic transaction.
- Prompts metric increment `booking.holds.expired.total`.

---

## 7. Consistency vs. Availability: CAP Theorem Trade-Offs

| Dimension | Strategy & Rationale |
| :--- | :--- |
| **Consistency (C)** | **Strong Consistency Required (CP System)**. In ticketing, selling the same physical seat twice to two paying customers is a catastrophic domain failure. Eventual consistency is rejected. |
| **Availability (A)** | Optimized high availability under high concurrency via row-level locks, fast indexed lookups, connection pooling (HikariCP with 20ms timeout guards), and non-blocking metric telemetry. |
| **Partition Tolerance (P)** | Under network split or database failover, the service prioritizes consistency: nodes unable to reach the primary DB instance return `503 Service Unavailable` rather than allowing split-brain double bookings. |

---

## 8. Observability & 2:00 AM Alerting Playbook

### 8.1 Critical Prometheus Metrics
| Metric Name | Type | Description |
| :--- | :--- | :--- |
| `booking.requests.total{status, endpoint}` | Counter | Total reservation attempts categorized by outcome |
| `booking.reservations.active` | Gauge | Current active reservations |
| `booking.conflicts.total` | Counter | Number of 409 conflict responses under contention |
| `booking.idempotency.replays.total` | Counter | Number of safe duplicate replays served |
| `booking.idempotency.mismatches.total` | Counter | Security alerts for payload tampering on reuse of key |
| `booking.reservation.duration.seconds` | Timer/Histogram | P50, P95, P99 reservation transaction latency |
| `booking.holds.expired.total` | Counter | Background TTL expiry cleanups |

### 8.2 Production Paging Alerts (Runbook)
1. **`HighDeadlockRateDetected`**:
   - *Condition*: `rate(postgresql_deadlocks[1m]) > 0`
   - *Severity*: P1 Critical
   - *Action*: Verify seat sorting invariant in `SeatRepository`. Check if external un-sorted update queries bypass `BookingService`.
2. **`SeatReconciliationDrift`**:
   - *Condition*: `available_seats + held_seats + confirmed_seats != total_seats`
   - *Severity*: P1 Critical
   - *Action*: Check for untracked manual DB updates or uncommitted orphan records. Trigger read-only audit lock.
3. **`ConnectionPoolExhaustion`**:
   - *Condition*: `hikaricp_pending_threads > 10` for > 30 seconds
   - *Severity*: P2 High
   - *Action*: Inspect slow transactions; check database lock contention; auto-scale read replicas if query offloading is enabled.

---

## 9. AI Usage & Engineering Ownership Disclosure

- **AI Collaboration Model**: Gemini / Antigravity AI was leveraged as an interactive pair programmer for rapid boilerplate generation, DTO structures, test scaffolding, and initial UI component templates.
- **Architectural Ownership & Critical Decisions**:
  1. Row-level pessimistic locking with deterministic alphabetical ordering to prevent deadlocks.
  2. Striped synchronization on per-user concurrency limits to prevent phantom multi-request limit bypasses.
  3. Strict SHA-256 payload verification for idempotency with immediate tamper rejection.
  4. Mathematical verification of the reconciliation invariant (`available + held + confirmed == total`).
  5. Minor-unit integer financial representation (paise).

---

## 10. Summary Matrix of Concurrency Test Verifications

| Test Suite | Scenario | Result |
| :--- | :--- | :--- |
| `ConcurrentBookingTest.testHotSeatConcurrencyStorm` | 50 threads fighting simultaneously for 1 seat | **1 Winner (201 Created), 49 Rejections (409 Conflict), 0 Double-Sells** |
| `ConcurrentBookingTest.testUserBookingLimitUnderConcurrencyBurst` | 10 parallel threads from same user requesting distinct seats (Limit=4) | **Exactly 4 seats booked, 6 requests rejected with 409 Limit Exceeded** |
| `ConcurrentBookingTest.testDeadlockAvoidanceUnderOppositeOrderRequests` | Thread 1: `[S1, S2]`, Thread 2: `[S2, S1]` | **Zero deadlocks, one thread wins both seats, one gets clean 409** |
| `BookingServiceIntegrationTest.testIdempotencyDuplicateRequest` | Sequential & concurrent identical retry | **Exact cached response replayed, single DB reservation record** |
| `BookingServiceIntegrationTest.testIdempotencyPayloadMismatchRejection` | Same Idempotency-Key, modified seats | **Rejected with 409 IDEMPOTENCY_PAYLOAD_MISMATCH** |
| `HealthAndMetricsTest.testPrometheusMetricsExport` | Scrape `/metrics` and `/actuator/prometheus` | **Live metrics exposed with custom booking telemetry** |
