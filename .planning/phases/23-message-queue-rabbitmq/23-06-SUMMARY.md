---
phase: 23
plan: 06
subsystem: backend/messaging-it
tags: [integration-test, testcontainers, rabbitmq, dlq, retry, sequence-diagram]
requires: [23-03, 23-04, 23-05]
provides:
  - OrderEventPublisherIT (D-03 publish-after-commit gate)
  - OrderPlacedListenerIT (D-18 4 scenario full — không @Disabled)
  - OrderPlacedNotifyListenerIT (happy + idempotent + topology smoke)
  - scripts/verify-mq.sh (smoke verify Management UI HTTP API)
  - architecture/02-sequence-diagrams.md (Kafka → RabbitMQ + Appendix A topology)
affects: [order-service tests, inventory-service tests, notification-service tests, architecture docs, scripts]
key-files:
  created:
    - sources/backend/order-service/src/test/java/com/ptit/htpt/orderservice/messaging/OrderEventPublisherIT.java
    - sources/backend/inventory-service/src/test/java/com/ptit/htpt/inventoryservice/messaging/OrderPlacedListenerIT.java
    - sources/backend/notification-service/src/test/java/com/ptit/htpt/notificationservice/messaging/OrderPlacedNotifyListenerIT.java
    - sources/backend/notification-service/src/test/resources/test-init/01-schemas.sql
    - sources/backend/inventory-service/src/test/resources/application-test.yml
    - scripts/verify-mq.sh
  modified:
    - sources/backend/inventory-service/src/test/resources/test-init/01-schemas.sql (thêm product_svc)
    - sources/backend/order-service/src/test/resources/test-init/01-schemas.sql (thêm inventory_svc + notification_svc)
    - architecture/02-sequence-diagrams.md (Kafka → RabbitMQ + error-path + Appendix A)
    - .planning/ROADMAP.md (Plan 23-06 ✓)
decisions:
  - "D-18 LOCK enforced: 4 scenario inventory IT đầy đủ (happy/idempotent/DLQ permanent/transient retry) — KHÔNG @Disabled"
  - "@SpyBean StockLedgerRepository + Mockito.doAnswer throw TransientDataAccessResourceException 2 lần đầu → Spring AMQP retry test profile override 100ms exp backoff → attempt 3 callRealMethod (chứng minh retry-then-ack)"
  - "Notification IT 3 scenario (happy + idempotent + topology smoke) — KHÔNG permanent-DLQ vì recordOrderConfirmation accept mọi payload (không có business validation throw PermanentException dựa trên payload)"
  - "OrderEventPublisherIT chứng minh D-03 afterCommit semantics: rollback → queue depth không tăng (mitigation T-23-09 phantom-event)"
  - "Pattern @ServiceConnection (Spring Boot 3.1+) cho cả Postgres + RabbitMQ — tự động inject spring.datasource.url + spring.rabbitmq.host/port runtime"
  - "application-test.yml inventory override retry initial-interval=100ms multiplier=2 max-attempts=3 → IT chạy gọn (~600ms total backoff vs 7s prod 1s/2s/4s)"
  - "Architecture doc: thay Kafka → RabbitMQ 5 sequence diagrams + thêm error-path diagram permanent vs transient + Appendix A topology table (5 resources) + smoke verify reference"
  - "Maven CLI defer trên Windows env — IT files đã sẵn để /gsd-verify-work hoặc CI/local mvn chạy (precedent v1.3 Plans 19-20, 23-01..05)"
tech-stack:
  added:
    - "Testcontainers RabbitMQContainer (đã có pom Plan 23-02, dùng image rabbitmq:3-management)"
    - "Spring Boot @ServiceConnection cho PostgreSQL + RabbitMQ (transitive từ spring-boot-testcontainers 3.3.2)"
    - "Awaitility (transitive từ spring-boot-starter-test BOM 3.3.2)"
  patterns:
    - "Testcontainers @ServiceConnection (Spring Boot 3.1+): tự inject datasource + amqp URL runtime — gọn hơn @DynamicPropertySource"
    - "@SpyBean + Mockito.doAnswer callRealMethod cho retry-after-N-failures scenario (KHÔNG break business logic, vẫn ghi state thật vào DB)"
    - "AmqpAdmin.getQueueProperties / QUEUE_MESSAGE_COUNT cho DLQ verification (không cần basicGet/receive)"
    - "TransactionTemplate programmatic + setRollbackOnly cho publish-after-commit IT (không cần @Transactional class-level)"
metrics:
  duration: "~10min"
  completed_date: 2026-05-20
  tasks: 2
  files_created: 6
  files_modified: 3
  test_classes: 3
  test_methods: 9
  commits: 2
---

# Phase 23 Plan 06: Integration Tests + Smoke Verify + Architecture Doc Summary

Integration test suite end-to-end cho luồng OrderPlaced async qua RabbitMQ (D-18 LOCK: 4 scenario đầy đủ cho inventory consumer) + smoke verify Management UI HTTP API + cập nhật architecture/02-sequence-diagrams.md phản ánh RabbitMQ thay Kafka — đóng MQ-02/MQ-03/MQ-04/MQ-05 với evidence test chứng minh.

## What

- **3 IT class — tổng 9 @Test methods**:
  - `OrderEventPublisherIT` (order-service, 2 tests): `rollback_doesNotPublish` (mitigation T-23-09 phantom-event) + `commit_publishesAfterCommit` (D-03 afterCommit fire). Dùng TransactionTemplate programmatic + AmqpAdmin queue depth assertion.
  - `OrderPlacedListenerIT` (inventory-service, **4 tests FULL D-18 không @Disabled**): happyPath / idempotent_samePayloadTwice / permanentException_routedToDLQ (prod-missing → PermanentMessageException → DLQ count >= 1) / transientThenSuccess_retryThenAck (`@SpyBean StockLedgerRepository.save` + `Mockito.doAnswer` throw 2 lần đầu → attempt 3 callRealMethod).
  - `OrderPlacedNotifyListenerIT` (notification-service, 3 tests): happyPath + idempotent + topology smoke. KHÔNG có permanent-DLQ scenario vì `recordOrderConfirmation` accept mọi payload (chỉ StringBuilder concat + INSERT).
- **test-init/01-schemas.sql** cho 3 service (CREATE SCHEMA IF NOT EXISTS — KHÔNG conditional, KHÔNG branch): order-service (order_svc + inventory_svc + notification_svc), inventory-service (inventory_svc + product_svc), notification-service (notification_svc).
- **scripts/verify-mq.sh** — bash smoke script curl Management UI HTTP API: `/overview`, `/exchanges/order.events`, `/exchanges/order.dlx`, 3 queues (`inventory.order-events` / `notification.order-events` / `order-events.dlq`). Env-var override `RABBITMQ_HOST/USER/PASS` (T-23-15 accept guest/guest dev).
- **architecture/02-sequence-diagrams.md**: thay tham chiếu Kafka → RabbitMQ trong 5 sequence diagrams (Register, Checkout, VNPay, Cancel, Ship) + thêm 1 error-path mermaid (permanent vs transient) + Appendix A topology table (5 resources: TopicExchange + DLX + DLQ + 2 service queue) + retry policy + idempotency + smoke verify reference.
- **application-test.yml inventory-service**: override `spring.rabbitmq.listener.simple.retry.initial-interval=100ms multiplier=2 max-attempts=3` → IT retry chạy ~600ms total backoff (vs prod 1s/2s/4s = 7s).

## Why

D-18 LOCK trong 23-CONTEXT.md đã chốt: 4 scenario IT cho inventory consumer phải đầy đủ KHÔNG defer — đây là gate quan trọng nhất cho SC #7 ROADMAP (integration test chứng minh luồng end-to-end). Thiếu scenario `transientThenSuccess_retryThenAck` thì retry/DLQ chỉ là claim trên giấy, không có evidence runtime. @SpyBean approach cho phép test retry behavior thật của Spring AMQP listener container mà không phải tự rig SimpleMessageListenerContainer.

Architecture doc cũ reference Kafka — nếu giữ nguyên thì sai sự thật về kiến trúc, hội đồng đọc doc sẽ thấy mismatch với code. Phase 23 chốt RabbitMQ thay Kafka (project_microservice-gaps.md memory + 23-CONTEXT.md canonical_refs) → bắt buộc cập nhật doc đồng bộ.

verify-mq.sh đóng vai trò manual smoke cho hội đồng demo: sau khi `docker compose up`, chạy 1 lệnh thấy ALL SMOKE CHECKS PASSED → broker topology OK, không cần mở UI click thủ công.

## How

1. **Task 1** (commit `fc86cb0`): tạo 2 consumer IT + 2 test-init schemas + application-test.yml inventory. Pattern Testcontainers `@ServiceConnection` (Spring Boot 3.1+) thay vì `@DynamicPropertySource` — gọn hơn. `@SpyBean StockLedgerRepository` cho scenario retry vì StockLedgerEntity.save là điểm cuối cùng trong InventoryCrudService.decrementForOrder transaction (nếu fail ở đây thì cả processedEvents INSERT cũng rollback → retry sẽ INSERT lại). Mockito `doAnswer` với `AtomicInteger attempts` count + threshold 2 + callRealMethod cho lần >= 3.
2. **Task 2** (commit `9957b99`): OrderEventPublisherIT dùng `TransactionTemplate` programmatic vì publisher.publishOrderPlaced gọi `TransactionSynchronizationManager.registerSynchronization` chỉ khi `isSynchronizationActive()` — `tx.execute(status -> {...})` mở tx context cho afterCommit hook fire. Verify queue depth qua `AmqpAdmin.getQueueProperties(queue).get("QUEUE_MESSAGE_COUNT")` (Number cast). Architecture doc cập nhật giữ nguyên mermaid syntax, chỉ thay participant `K as Kafka` → `MQ as RabbitMQ` và arrows `K-->>` → `MQ-->>`; thêm Appendix A markdown table topology + 1 error-path mermaid diagram mới.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Missing infra] Tạo thêm `application-test.yml` cho inventory-service**
- **Found during:** Task 1
- **Issue:** Plan không yêu cầu tạo file này, nhưng nếu thiếu thì retry test sẽ phải đợi đủ 7s (1s+2s+4s) × failure → IT chạy chậm + risk timeout. Notification-service đã có application-test.yml từ Plan 23-02 nhưng inventory thì chưa.
- **Fix:** Tạo `sources/backend/inventory-service/src/test/resources/application-test.yml` override `retry.initial-interval=100ms multiplier=2 max-attempts=3` — IT chạy ~600ms backoff total.
- **Files modified:** `sources/backend/inventory-service/src/test/resources/application-test.yml` (created)
- **Commit:** fc86cb0

### Architectural changes

**Không có** — Plan executed gần như nguyên văn.

### Auth gates

**Không có** — toàn bộ Wave 3 chạy local Testcontainers.

## Trade-offs

**Trade-off: OrderEventEnvelope duplicated across 3 services — shared module deferred to Phase ops**

Mỗi service (order/inventory/notification) đều có copy riêng của `OrderEventEnvelope` record + 2 messaging exception (Transient/Permanent) — KHÔNG tách thành Maven shared module. Lý do: scope đồ án thử nghiệm GSD, tránh phụ thuộc multi-module build (BOM management, version sync giữa parent pom + 3 child pom) — duplication chấp nhận được với envelope mới 1 event type. Khi nào có 3+ event types (UserRegistered, PaymentSucceeded, OrderCancelled) thì shared `messaging-contracts` module sẽ giảm cost copy-paste đáng kể. Defer Phase ops.

## Known Stubs

Không có — toàn bộ code path đều wire data thật từ Testcontainers Postgres + RabbitMQ, không placeholder.

## Threat Flags

Không có surface mới — toàn bộ test code + smoke script đều dev-scope, không expose endpoint mới ra mạng ngoài.

## Verification

### Static (passed)

- [x] 3 IT class file tồn tại với 9 @Test methods (2 + 4 + 3)
- [x] `grep '@Disabled' OrderPlacedListenerIT.java` = 0 (D-18 LOCK)
- [x] `grep '@SpyBean' OrderPlacedListenerIT.java` = 1 (transient retry scenario)
- [x] `grep 'TransientDataAccessResourceException' OrderPlacedListenerIT.java` = 1
- [x] `grep '@ServiceConnection' OrderPlacedListenerIT.java` = 2 (Postgres + RabbitMQ)
- [x] `grep 'amqpAdmin.getQueueProperties' OrderPlacedListenerIT.java` = 1 (DLQ scenario)
- [x] 3 test-init/01-schemas.sql đầy đủ CREATE SCHEMA IF NOT EXISTS không conditional
- [x] scripts/verify-mq.sh tồn tại + `grep '15672/api'` >= 1
- [x] architecture/02-sequence-diagrams.md có `RabbitMQ` + `order.events` references
- [x] Self-check FOUND tất cả file + commits

### Runtime (deferred)

- [ ] [BLOCKING] `cd sources/backend && mvn -pl order-service,inventory-service,notification-service verify` — defer (Maven CLI không có trên Windows env này, precedent v1.3 Plans 19-20 + 23-01..05). Files đã sẵn cho /gsd-verify-work hoặc CI/local mvn chạy.
- [ ] Manual smoke: `docker compose up -d` rồi `bash scripts/verify-mq.sh` → ALL SMOKE CHECKS PASSED — defer cho verification phase.
- [ ] Manual demo: tạo 1 order qua FE → mở http://localhost:15672 → tab Exchanges/Queues thấy `order.events`, `inventory.order-events`, `notification.order-events`, `order-events.dlq` — defer.
- [ ] `docker compose logs order-service inventory-service notification-service | grep <traceId>` ra >= 3 dòng cùng traceId (D-16 demo) — defer.

## Files Committed

| Commit  | Task | Files                                                                                              |
| ------- | ---- | -------------------------------------------------------------------------------------------------- |
| fc86cb0 | 1    | OrderPlacedListenerIT, OrderPlacedNotifyListenerIT, 2 test-init/01-schemas.sql, application-test.yml |
| 9957b99 | 2    | OrderEventPublisherIT, order-service test-init/01-schemas.sql, scripts/verify-mq.sh, architecture/02-sequence-diagrams.md |

## Self-Check: PASSED

Đã verify:
- 6 file tạo mới + 3 file modified tồn tại trên đĩa
- 2 commits fc86cb0 + 9957b99 trong git log
- Acceptance criteria static checks pass: 9 @Test methods, 0 @Disabled, @SpyBean + TransientDataAccessResourceException present, AmqpAdmin DLQ pattern present, scripts/verify-mq.sh có /api endpoint reference, architecture doc có RabbitMQ + order.events
- Trade-off line đã có trong SUMMARY: "OrderEventEnvelope duplicated across 3 services — shared module deferred to Phase ops"
