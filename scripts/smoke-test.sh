#!/usr/bin/env sh
set -eu

BASE_URL="${1:-http://localhost:8080}"

echo "[1/2] Health: $BASE_URL/actuator/health/liveness"
curl -fsS "$BASE_URL/actuator/health/liveness"
echo

echo "[2/2] Public catalog: $BASE_URL/api/services"
curl -fsS "$BASE_URL/api/services"
echo

echo "Smoke test OK"
