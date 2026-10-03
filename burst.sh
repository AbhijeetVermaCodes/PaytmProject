#!/bin/bash
# ==============================================================================
# One-Command High-Concurrency Burst Stampede Runner
# Usage: ./burst.sh [BASE_URL]
# Example: ./burst.sh http://localhost:8080
# ==============================================================================

set -e

BASE_URL="${1:-http://localhost:8080}"
BASE_URL="${BASE_URL%/}"

echo "================================================================================"
echo "          HIGH-CONCURRENCY TICKET BOOKING SERVICE - BURST LOAD RUNNER          "
echo "================================================================================"
echo "Target Base URL: $BASE_URL"
echo ""

# Compile test runner if needed and run Java Load Runner
if [ -f "./mvnw" ]; then
    ./mvnw test-compile -q
    CLASSPATH="target/classes:target/test-classes:$(./mvnw dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout 2>/dev/null || echo '')"
    java -cp "target/classes:target/test-classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout 2>/dev/null || echo 'target/*')" com.ticketbooking.loadtest.BurstLoadRunner "$BASE_URL" || true
else
    java -cp target/classes:target/test-classes com.ticketbooking.loadtest.BurstLoadRunner "$BASE_URL"
fi
