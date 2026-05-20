---
status: partial
phase: 23-message-queue-rabbitmq
source: [23-VERIFICATION.md]
started: 2026-05-20T00:00:00Z
updated: 2026-05-20T00:00:00Z
---

## Current Test

[awaiting human testing — cần docker + Maven + browser khả dụng]

## Tests

### 1. Management UI khả dụng + topology declared

expected: Khởi động `docker compose up -d rabbitmq` rồi 3 backend service. Truy cập http://localhost:15672 (guest/guest). Management UI hiển thị + exchange `order.events` + DLX `order.dlx` + 2 queue `inventory.order-events` + `notification.order-events` + DLQ `order-events.dlq` xuất hiện sau khi 3 service backend khởi động và declare topology.
result: [pending]

### 2. mvn verify chạy 9 IT @Test thành công

expected: Chạy `cd sources/backend/order-service && mvn verify -Dtest=OrderEventPublisherIT` (lặp cho inventory-service + notification-service). 3 IT class chạy thành công với tổng 9 @Test PASS (2 publisher + 4 inventory listener + 3 notification listener). KHÔNG có test bị skip/disabled.
result: [pending]

### 3. traceId propagate xuyên 3 service log

expected: Tạo 1 order qua FE → `docker compose logs order-service inventory-service notification-service | grep <traceId>` (lấy traceId từ log `[MQ-PUB]`). Cùng 1 traceId xuất hiện trong log của cả 3 service: `[MQ-PUB]` ở order-service, `[MQ-CONSUME]` ở inventory-service và notification-service.
result: [pending]

### 4. DLQ retry behavior quan sát trong Management UI

expected: Tạm thời disable inventory DB hoặc throw TransientMessageException → quan sát Management UI tab Queues. Message retry 3 lần (max-attempts=3) → cuối cùng rơi vào `order-events.dlq` với message count >= 1.
result: [pending]

### 5. scripts/verify-mq.sh smoke pass

expected: Chạy `scripts/verify-mq.sh` sau khi docker compose lên đủ 3 service. Script smoke verify topology declared + healthchecks pass — exit 0.
result: [pending]

## Summary

total: 5
passed: 0
issues: 0
pending: 5
skipped: 0
blocked: 0

## Gaps

(none recorded yet — chờ human verification kết quả)
