#!/bin/bash
# ==============================================================================
# High-Concurrency Ticket Booking Engine — Automated Bootstrap & Launch Script
#
# This script:
# 1. Checks all required system prerequisites (Java 17+, Maven/mvnw, Node.js 18+, npm).
# 2. Automatically installs any missing dependencies (via Homebrew on macOS / package managers).
# 3. Ensures the portable Maven binary & wrapper are properly linked.
# 4. Installs frontend npm packages if needed.
# 5. Starts both Backend (Spring Boot :8080) and Frontend (Vite :3000) services.
# 6. Provides graceful shutdown (Ctrl+C cleanly kills all child processes).
# ==============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Text styles
BOLD='\033[1m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo ""
echo -e "${BLUE}${BOLD}================================================================================${NC}"
echo -e "${CYAN}${BOLD}       HIGH-CONCURRENCY TICKET BOOKING SERVICE — AUTO STARTUP WIZARD            ${NC}"
echo -e "${BLUE}${BOLD}================================================================================${NC}"
echo ""

# ------------------------------------------------------------------------------
# Helper: OS & Package Manager Detection
# ------------------------------------------------------------------------------
OS="$(uname -s)"
case "$OS" in
    Darwin*)    PLATFORM="macOS" ;;
    Linux*)     PLATFORM="Linux" ;;
    *)          PLATFORM="Unknown" ;;
esac

echo -e "${BOLD}[1/5] Detecting System Environment...${NC}"
echo -e "   → Platform: ${YELLOW}$PLATFORM ($OS)${NC}"

# Load .env file if present
if [ -f "$SCRIPT_DIR/.env" ]; then
    echo -e "   ${GREEN}✔ Loading environment variables from .env${NC}"
    set -a
    source "$SCRIPT_DIR/.env"
    set +a
fi

# ------------------------------------------------------------------------------
# Helper: Check and Install Java (JDK 17+)
# ------------------------------------------------------------------------------
check_java() {
    echo -e "\n${BOLD}[2/5] Checking Java JDK (JDK 17+ required)...${NC}"
    if command -v java >/dev/null 2>&1; then
        JAVA_VER=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
        if [ "$JAVA_VER" = "1" ]; then
            JAVA_VER=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f2)
        fi
        if [ "$JAVA_VER" -ge 17 ] 2>/dev/null; then
            echo -e "   ${GREEN}✔ Java JDK is installed (Detected version: $JAVA_VER)${NC}"
            return 0
        else
            echo -e "   ${YELLOW}⚠ Java version $JAVA_VER is older than required JDK 17.${NC}"
        fi
    else
        echo -e "   ${RED}✘ Java is not installed.${NC}"
    fi

    echo -e "   ${YELLOW}→ Attempting automated Java installation...${NC}"
    if [ "$PLATFORM" = "macOS" ]; then
        if command -v brew >/dev/null 2>&1; then
            echo -e "   → Running: brew install openjdk@17"
            brew install openjdk@17
            sudo ln -sfn "$(brew --prefix openjdk@17)/libexec/openjdk.jdk" /Library/Java/JavaVirtualMachines/openjdk-17.jdk 2>/dev/null || true
            export PATH="$(brew --prefix openjdk@17)/bin:$PATH"
        else
            echo -e "${RED}ERROR: Homebrew is required on macOS to auto-install Java. Please install Homebrew or JDK 17+.${NC}"
            exit 1
        fi
    elif [ "$PLATFORM" = "Linux" ]; then
        if command -v apt-get >/dev/null 2>&1; then
            echo -e "   → Running: sudo apt-get update && sudo apt-get install -y openjdk-17-jdk"
            sudo apt-get update && sudo apt-get install -y openjdk-17-jdk
        elif command -v dnf >/dev/null 2>&1; then
            echo -e "   → Running: sudo dnf install -y java-17-openjdk-devel"
            sudo dnf install -y java-17-openjdk-devel
        fi
    fi

    if ! command -v java >/dev/null 2>&1; then
        echo -e "${RED}ERROR: Failed to install Java. Please install JDK 17 manually.${NC}"
        exit 1
    fi
}

# ------------------------------------------------------------------------------
# Helper: Check and Install Maven / Portable Maven
# ------------------------------------------------------------------------------
check_maven() {
    echo -e "\n${BOLD}[3/5] Checking Maven / Maven Wrapper...${NC}"
    mkdir -p "$SCRIPT_DIR/.mvn/maven"

    # Check if local wrapper executable or maven exists
    if [ -f "$SCRIPT_DIR/.mvn/maven/apache-maven-3.9.6/bin/mvn" ]; then
        chmod +x "$SCRIPT_DIR/.mvn/maven/apache-maven-3.9.6/bin/mvn"
        echo -e "   ${GREEN}✔ Portable Apache Maven 3.9.6 ready at .mvn/maven/${NC}"
    elif [ -f "$HOME/.maven/apache-maven-3.9.6/bin/mvn" ]; then
        ln -sfn "$HOME/.maven/apache-maven-3.9.6" "$SCRIPT_DIR/.mvn/maven/apache-maven-3.9.6"
        echo -e "   ${GREEN}✔ Linked Maven from $HOME/.maven/apache-maven-3.9.6${NC}"
    elif command -v mvn >/dev/null 2>&1; then
        echo -e "   ${GREEN}✔ System Maven detected: $(mvn -v | head -n 1)${NC}"
    else
        echo -e "   ${YELLOW}→ Downloading standalone Apache Maven 3.9.6 binary...${NC}"
        mkdir -p "$HOME/.maven"
        curl -fsSL https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.tar.gz | tar -xz -C "$HOME/.maven"
        ln -sfn "$HOME/.maven/apache-maven-3.9.6" "$SCRIPT_DIR/.mvn/maven/apache-maven-3.9.6"
        echo -e "   ${GREEN}✔ Standalone Maven 3.9.6 installed successfully!${NC}"
    fi

    chmod +x "$SCRIPT_DIR/mvnw"
}

# ------------------------------------------------------------------------------
# Helper: Check and Install Node.js & npm (18+)
# ------------------------------------------------------------------------------
check_node() {
    echo -e "\n${BOLD}[4/5] Checking Node.js & npm (Node 18+ required)...${NC}"
    NEED_NODE_INSTALL=0

    if command -v node >/dev/null 2>&1; then
        NODE_VER=$(node -v | sed 's/v//' | cut -d'.' -f1)
        if [ "$NODE_VER" -ge 18 ] 2>/dev/null; then
            echo -e "   ${GREEN}✔ Node.js $(node -v) and npm $(npm -v) detected.${NC}"
        else
            echo -e "   ${YELLOW}⚠ Node.js version $NODE_VER is older than recommended Node 18.${NC}"
            NEED_NODE_INSTALL=1
        fi
    else
        echo -e "   ${RED}✘ Node.js / npm not found.${NC}"
        NEED_NODE_INSTALL=1
    fi

    if [ "$NEED_NODE_INSTALL" -eq 1 ]; then
        echo -e "   ${YELLOW}→ Installing Node.js LTS...${NC}"
        if [ "$PLATFORM" = "macOS" ]; then
            if command -v brew >/dev/null 2>&1; then
                brew install node
            else
                echo -e "${RED}ERROR: Homebrew required to auto-install Node.js. Please install Node.js from https://nodejs.org/${NC}"
                exit 1
            fi
        elif [ "$PLATFORM" = "Linux" ]; then
            if command -v apt-get >/dev/null 2>&1; then
                curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
                sudo apt-get install -y nodejs
            fi
        fi
    fi

    # Install Frontend packages if node_modules is missing
    if [ ! -d "$SCRIPT_DIR/frontend/node_modules" ]; then
        echo -e "   ${YELLOW}→ Installing frontend dependencies (npm install)...${NC}"
        (cd "$SCRIPT_DIR/frontend" && npm install)
        echo -e "   ${GREEN}✔ Frontend dependencies installed.${NC}"
    else
        echo -e "   ${GREEN}✔ Frontend node_modules verified.${NC}"
    fi
}

# Run all checks
check_java
check_maven
check_node

# ------------------------------------------------------------------------------
# Launching the Services
# ------------------------------------------------------------------------------
echo ""
echo -e "${BLUE}${BOLD}================================================================================${NC}"
echo -e "${GREEN}${BOLD}           ALL PREREQUISITES VERIFIED! LAUNCHING SERVICES...                    ${NC}"
echo -e "${BLUE}${BOLD}================================================================================${NC}"
echo ""

BACKEND_PID=""
FRONTEND_PID=""

cleanup() {
    echo ""
    echo -e "\n${YELLOW}${BOLD}Shutting down services...${NC}"
    if [ -n "$FRONTEND_PID" ]; then
        kill "$FRONTEND_PID" 2>/dev/null || true
    fi
    if [ -n "$BACKEND_PID" ]; then
        kill "$BACKEND_PID" 2>/dev/null || true
    fi
    # Also clean any process on ports 8080 or 3000 started in this session
    lsof -ti:8080 | xargs kill -9 2>/dev/null || true
    lsof -ti:3000 | xargs kill -9 2>/dev/null || true
    echo -e "${GREEN}All services stopped cleanly. Goodbye! 👋${NC}"
    exit 0
}

trap cleanup EXIT INT TERM

# 1. Start Spring Boot Backend
echo -e "${CYAN}${BOLD}[1/2] Starting Spring Boot Backend (Port 8080)...${NC}"
"$SCRIPT_DIR/mvnw" spring-boot:run > "$SCRIPT_DIR/backend.log" 2>&1 &
BACKEND_PID=$!
echo -e "   → Backend process PID: ${YELLOW}$BACKEND_PID${NC} (Logs: backend.log)"

# Poll until backend is ready (up to 30s)
echo -n "   → Waiting for Backend to be ready"
READY=0
for i in {1..30}; do
    if curl -s http://localhost:8080/health/ready 2>/dev/null | grep -q "UP"; then
        READY=1
        break
    fi
    echo -n "."
    sleep 1
done
echo ""

if [ "$READY" -eq 1 ]; then
    echo -e "   ${GREEN}✔ Backend is UP & READY on http://localhost:8080${NC}"
else
    echo -e "   ${RED}✘ Backend failed to start within 30s. Check backend.log for details.${NC}"
    exit 1
fi

# 2. Start Vite Frontend
echo -e "\n${CYAN}${BOLD}[2/2] Starting React + Tailwind Frontend (Port 3000)...${NC}"
(cd "$SCRIPT_DIR/frontend" && npm run dev > "$SCRIPT_DIR/frontend.log" 2>&1) &
FRONTEND_PID=$!
echo -e "   → Frontend process PID: ${YELLOW}$FRONTEND_PID${NC} (Logs: frontend.log)"

sleep 2

echo ""
echo -e "${GREEN}${BOLD}================================================================================${NC}"
echo -e "${GREEN}${BOLD}  🚀  SEATRUSH HIGH-CONCURRENCY TICKETING SERVICE IS LIVE & RUNNING!             ${NC}"
echo -e "${GREEN}${BOLD}================================================================================${NC}"
echo ""
echo -e "  🌐 ${BOLD}Interactive Visualizer Dashboard:${NC}  ${CYAN}${BOLD}http://localhost:3000${NC}"
echo -e "  ⚙️  ${BOLD}Spring Boot REST API Root:${NC}         ${BLUE}http://localhost:8080${NC}"
echo -e "  🩺 ${BOLD}Readiness Health Probe:${NC}            ${GREEN}http://localhost:8080/health/ready${NC}"
echo -e "  📊 ${BOLD}Prometheus Metrics Stream:${NC}         ${YELLOW}http://localhost:8080/metrics${NC}"
echo ""
echo -e "${YELLOW}Press [Ctrl + C] at any time to stop both services.${NC}"
echo -e "${BLUE}--------------------------------------------------------------------------------${NC}"

# Tail backend and frontend activity
wait
