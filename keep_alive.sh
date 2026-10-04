#!/usr/bin/env bash
# ==============================================================================
# Render Service Keep-Alive Daemon & Monitor
#
# Render Free Tier services spin down after 15 minutes of inactivity.
# This script pings the backend health endpoint and frontend every 5 minutes (300s)
# to keep services warm and eliminate cold-start delays.
#
# Usage:
#   ./keep_alive.sh             # Run in foreground (live monitoring)
#   ./keep_alive.sh --daemon    # Run in background (logs to keep_alive.log)
#   ./keep_alive.sh --status    # Check if background daemon is active
#   ./keep_alive.sh --stop      # Stop running background daemon
#   ./keep_alive.sh --once      # Ping once and exit (exit code 0 if healthy)
# ==============================================================================

set -uo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PID_FILE="${PROJECT_ROOT}/.keep_alive.pid"
LOG_FILE="${PROJECT_ROOT}/keep_alive.log"

BACKEND_URL="${BACKEND_URL:-https://ticket-booking-backend-mvhs.onrender.com}"
FRONTEND_URL="${FRONTEND_URL:-https://ticket-booking-frontend-e57y.onrender.com}"
INTERVAL_SECONDS="${INTERVAL_SECONDS:-300}"

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

timestamp() {
    date +"%Y-%m-%d %H:%M:%S"
}

ping_services() {
    local ts
    ts=$(timestamp)

    # 1. Ping Backend Ready Endpoint
    local be_start be_end be_latency be_code be_body
    be_start=$(python3 -c 'import time; print(int(time.time() * 1000))' 2>/dev/null || date +%s000)

    # Use curl with timeout
    local curl_output
    curl_output=$(curl -s -m 20 -w "\n%{http_code}" "${BACKEND_URL}/health/ready" 2>/dev/null || echo -e "\n000")
    be_code=$(echo "${curl_output}" | tail -n 1)
    be_body=$(echo "${curl_output}" | sed '$d')

    be_end=$(python3 -c 'import time; print(int(time.time() * 1000))' 2>/dev/null || date +%s000)
    be_latency=$((be_end - be_start))

    local be_status_text="ERROR"
    if [ "${be_code}" = "200" ]; then
        if echo "${be_body}" | grep -q '"database":"CONNECTED"'; then
            be_status_text="${GREEN}UP (DB: CONNECTED)${NC}"
        else
            be_status_text="${YELLOW}UP (DB: DEGRADED)${NC}"
        fi
        echo -e "[${ts}] ${GREEN}✔ BACKEND${NC}  | HTTP ${GREEN}${be_code}${NC} | ${be_latency}ms | Status: ${be_status_text}"
    else
        echo -e "[${ts}] ${RED}✘ BACKEND${NC}  | HTTP ${RED}${be_code}${NC} | ${be_latency}ms | FAILED"
    fi

    # 2. Ping Frontend Static Site (Keep CDN edge warm)
    local fe_code fe_start fe_end fe_latency
    fe_start=$(python3 -c 'import time; print(int(time.time() * 1000))' 2>/dev/null || date +%s000)
    fe_code=$(curl -s -m 15 -o /dev/null -w "%{http_code}" "${FRONTEND_URL}/" 2>/dev/null || echo "000")
    fe_end=$(python3 -c 'import time; print(int(time.time() * 1000))' 2>/dev/null || date +%s000)
    fe_latency=$((fe_end - fe_start))

    if [ "${fe_code}" = "200" ]; then
        echo -e "[${ts}] ${GREEN}✔ FRONTEND${NC} | HTTP ${GREEN}${fe_code}${NC} | ${fe_latency}ms | OK"
    else
        echo -e "[${ts}] ${YELLOW}⚠ FRONTEND${NC} | HTTP ${YELLOW}${fe_code}${NC} | ${fe_latency}ms"
    fi

    if [ "${be_code}" = "200" ]; then
        return 0
    else
        return 1
    fi
}

start_daemon() {
    if [ -f "${PID_FILE}" ]; then
        local old_pid
        old_pid=$(cat "${PID_FILE}")
        if kill -0 "${old_pid}" 2>/dev/null; then
            echo -e "${YELLOW}Keep-alive daemon is already running (PID: ${old_pid})${NC}"
            echo "Log file: ${LOG_FILE}"
            return 0
        fi
        rm -f "${PID_FILE}"
    fi

    echo -e "${CYAN}Starting keep-alive daemon in background...${NC}"
    echo -e "Ping target: ${BACKEND_URL}/health/ready"
    echo -e "Interval: ${INTERVAL_SECONDS} seconds (5 minutes)"
    echo -e "Logs: ${LOG_FILE}"

    nohUP_CMD() {
        while true; do
            ping_services >> "${LOG_FILE}" 2>&1
            sleep "${INTERVAL_SECONDS}"
        done
    }

    # Start detached in background
    (nohUP_CMD) &
    local new_pid=$!
    echo "${new_pid}" > "${PID_FILE}"

    sleep 1
    if kill -0 "${new_pid}" 2>/dev/null; then
        echo -e "${GREEN}✔ Daemon started successfully with PID: ${new_pid}${NC}"
        echo "Run './keep_alive.sh --status' to inspect status or './keep_alive.sh --stop' to terminate."
    else
        echo -e "${RED}✘ Failed to start daemon.${NC}"
        rm -f "${PID_FILE}"
        return 1
    fi
}

stop_daemon() {
    if [ ! -f "${PID_FILE}" ]; then
        echo "No daemon PID file found (.keep_alive.pid)."
        return 0
    fi

    local pid
    pid=$(cat "${PID_FILE}")
    if kill -0 "${pid}" 2>/dev/null; then
        echo -e "Stopping keep-alive daemon (PID: ${pid})..."
        kill "${pid}" 2>/dev/null || true
        # Also kill child processes if any
        pkill -P "${pid}" 2>/dev/null || true
        rm -f "${PID_FILE}"
        echo -e "${GREEN}✔ Daemon stopped.${NC}"
    else
        echo -e "${YELLOW}Daemon process ${pid} was not running.${NC}"
        rm -f "${PID_FILE}"
    fi
}

check_status() {
    if [ -f "${PID_FILE}" ]; then
        local pid
        pid=$(cat "${PID_FILE}")
        if kill -0 "${pid}" 2>/dev/null; then
            echo -e "${GREEN}● Daemon is ACTIVE${NC} (PID: ${pid})"
            echo -e "Target:   ${BACKEND_URL}"
            echo -e "Interval: Every ${INTERVAL_SECONDS}s"
            echo "--- Recent Activity (last 6 entries) ---"
            if [ -f "${LOG_FILE}" ]; then
                tail -n 12 "${LOG_FILE}"
            else
                echo "(Log file not populated yet)"
            fi
            return 0
        else
            echo -e "${RED}● Daemon is INACTIVE${NC} (stale PID file cleaned up)"
            rm -f "${PID_FILE}"
            return 1
        fi
    else
        echo -e "${YELLOW}● Daemon is NOT running.${NC}"
        echo "Start it with: ./keep_alive.sh --daemon"
        return 1
    fi
}

run_foreground() {
    echo -e "${BOLD}${CYAN}======================================================${NC}"
    echo -e "${BOLD}${GREEN}   Render Service Keep-Alive Monitor (Every ${INTERVAL_SECONDS}s)   ${NC}"
    echo -e "${BOLD}${CYAN}======================================================${NC}"
    echo -e "Backend:  ${BLUE}${BACKEND_URL}/health/ready${NC}"
    echo -e "Frontend: ${BLUE}${FRONTEND_URL}${NC}"
    echo -e "Interval: ${BOLD}${INTERVAL_SECONDS}s${NC} (Pings before the 15-minute sleep threshold)"
    echo -e "Press ${BOLD}Ctrl+C${NC} anytime to stop.\n"

    local cycle=1
    while true; do
        echo -e "${CYAN}--- Ping Cycle #${cycle} ---${NC}"
        ping_services
        cycle=$((cycle + 1))
        echo -e "${BLUE}Waiting ${INTERVAL_SECONDS}s until next ping...${NC}\n"
        sleep "${INTERVAL_SECONDS}"
    done
}

# ------------------------------------------------------------------------------
# Argument Routing
# ------------------------------------------------------------------------------
ACTION="${1:-}"

case "${ACTION}" in
    --daemon|-d)
        start_daemon
        ;;
    --stop|-s)
        stop_daemon
        ;;
    --status)
        check_status
        ;;
    --once)
        ping_services
        ;;
    --help|-h)
        echo "Usage: $0 [options]"
        echo "Options:"
        echo "  (none)      Run keep-alive loop interactively in foreground"
        echo "  --daemon    Run keep-alive in background as a daemon"
        echo "  --status    Show status and recent logs of the daemon"
        echo "  --stop      Stop the running daemon"
        echo "  --once      Run a single ping check and exit"
        ;;
    *)
        run_foreground
        ;;
esac
