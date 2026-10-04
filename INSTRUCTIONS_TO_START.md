# Instructions to Start — High-Concurrency Ticket Booking Service

This guide provides step-by-step instructions to set up, build, test, and run both the **Spring Boot Backend** and the **React + Tailwind Frontend Visualizer** from scratch.

---

## 1. System Prerequisites

Ensure the following tools are installed on your system:

| Prerequisite | Recommended Version | Verification Command |
| :--- | :--- | :--- |
| **Java JDK** | JDK 17, 18, 21, or 25 | `java -version` |
| **Node.js** | Node 18+ (LTS recommended) | `node -v` |
| **npm** | npm 9+ | `npm -v` |
| **Docker & Compose** | *(Optional)* Docker 24+ | `docker --version` |

---

## 2. Option 1: Automatic One-Command Setup & Launch (Recommended)

Run the included automated bootstrap wizard which verifies all prerequisites, auto-installs missing dependencies, builds frontend packages, and launches both services simultaneously:

- **macOS / Linux:**
  ```bash
  ./start.sh
  ```
- **Windows (PowerShell):**
  ```powershell
  .\start.ps1
  ```

> 💡 **What this script does:**
> 1. Checks and verifies Java 17+, Maven/wrapper, Node.js 18+, and npm.
> 2. Auto-downloads and links the portable Apache Maven 3.9.6 binary if missing.
> 3. Installs frontend packages (`npm install`) if `node_modules` is absent.
> 4. Launches the Spring Boot Backend on **`:8080`** and waits for health readiness.
> 5. Launches the React Frontend Visualizer on **`:3000`**.
> 6. Pressing `Ctrl + C` gracefully terminates both services.

---

## 3. Option 2: Running Manually (Two-Terminal Mode)

### Step 1: Open Terminal & Navigate to Project Root
```bash
cd /path/to/PaytmProject
```

### Step 2: Start the Backend Service
Run the Maven wrapper to build and start the Spring Boot server:

- **macOS / Linux:**
  ```bash
  ./mvnw spring-boot:run
  ```
- **Windows (Command Prompt / PowerShell):**
  ```powershell
  .\mvnw.cmd spring-boot:run
  ```

> ℹ️ **Backend Port:** `http://localhost:8080`  
> ℹ️ **Database:** Runs automatically using in-memory H2 (PostgreSQL dialect compatibility mode) with pre-seeded show data.

---

### Step 3: Install Dependencies & Start Frontend
Open a **new terminal window/tab**, navigate into the `frontend` folder, and launch Vite:

```bash
# 1. Enter the frontend directory
cd frontend

# 2. Install NPM packages (required on first run)
npm install

# 3. Start the local development server
npm run dev
```

> ℹ️ **Frontend Port:** `http://localhost:3000` (or `http://localhost:5173`)  
> ℹ️ **Proxy:** Automatically forwards API calls (`/shows`, `/reservations`, `/event-requests`, `/health`, `/metrics`) to port `8080`.

---

## 4. Option 3: Running with Docker Compose (One-Command Containerization)

To spin up PostgreSQL 16 and the Spring Boot backend service in isolated Docker containers:

```bash
# From the project root:
docker-compose up --build
```

To run in detached background mode:
```bash
docker-compose up -d --build
```

To stop all containers and tear down networks:
```bash
docker-compose down
```

---

## 5. Key Endpoints & Dashboard Access

Once running, you can access the following services in your web browser:

| Interface / Endpoint | URL | Description |
| :--- | :--- | :--- |
| **Frontend Visualizer** | [http://localhost:3000](http://localhost:3000) | Interactive seating grid, contention sim, telemetry HUD |
| **Readiness Health Check** | [http://localhost:8080/health/ready](http://localhost:8080/health/ready) | Verifies database connectivity and readiness |
| **Liveness Health Check** | [http://localhost:8080/health/live](http://localhost:8080/health/live) | Liveness probe for orchestrators |
| **Prometheus Telemetry** | [http://localhost:8080/metrics](http://localhost:8080/metrics) | Plaintext Prometheus metrics stream |
| **Show Inventory API** | [http://localhost:8080/shows](http://localhost:8080/shows) | Lists all active shows and seat states |

---

## 6. Running Automated Tests & Verification

### A. Run Full Test Suite (14 Unit, Integration & Concurrency Tests)
```bash
# macOS / Linux:
./mvnw test

# Windows:
.\mvnw.cmd test
```

### B. Run High-Concurrency Burst Stampede Load Runner
Simulates 1,500 simultaneous requests contending for seats to verify race-freedom, per-user limits, and zero 5xx errors:

- **macOS / Linux:**
  ```bash
  ./burst.sh http://localhost:8080
  ```
- **Windows PowerShell:**
  ```powershell
  .\burst.ps1 http://localhost:8080
  ```
