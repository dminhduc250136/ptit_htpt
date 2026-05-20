#!/usr/bin/env bash
# Phase 23: smoke verify RabbitMQ topology + Management UI.
# Chạy SAU `docker compose up -d rabbitmq order-service inventory-service notification-service`.
#
# Usage:
#   bash scripts/verify-mq.sh
#   RABBITMQ_HOST=rabbitmq.example.com RABBITMQ_USER=admin RABBITMQ_PASS=*** bash scripts/verify-mq.sh

set -e
HOST="${RABBITMQ_HOST:-localhost}"
USER="${RABBITMQ_USER:-guest}"
PASS="${RABBITMQ_PASS:-guest}"
BASE="http://${USER}:${PASS}@${HOST}:15672/api"

echo "[1/4] Management UI reachable..."
curl -fsS "${BASE}/overview" >/dev/null
echo "  OK"

echo "[2/4] Exchange order.events exists..."
curl -fsS "${BASE}/exchanges/%2F/order.events" >/dev/null
echo "  OK"

echo "[3/4] Queues inventory.order-events + notification.order-events + order-events.dlq exist..."
for q in "inventory.order-events" "notification.order-events" "order-events.dlq"; do
  # vhost / = %2F
  curl -fsS "${BASE}/queues/%2F/${q}" >/dev/null || { echo "  MISSING: $q"; exit 1; }
  echo "  $q OK"
done

echo "[4/4] DLX order.dlx exists..."
curl -fsS "${BASE}/exchanges/%2F/order.dlx" >/dev/null
echo "  OK"

echo ""
echo "ALL SMOKE CHECKS PASSED"
