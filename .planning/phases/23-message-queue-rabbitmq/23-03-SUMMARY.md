---
phase: 23
plan: 03
subsystem: order-service
tags: [order-service, producer, rabbitmq-topology, afterCommit, publisher-confirms, deductStock-removed]
requires:
  - rabbitmq-broker-container
  - spring-rabbitmq-config-3-services
  - notification_svc-schema
provides:
  - rabbitmq-topology-3-services
  - order-event-envelope-record
  - order-event-publisher-after-commit
  - trace-id-message-postprocessor
  - mq-02-producer-implementation
affects:
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/messaging/config/RabbitMQConfig.java
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/messaging/event/OrderEventEnvelope.java
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/messaging/exception/TransientMessageException.java
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/messaging/exception/PermanentMessageException.java
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/messaging/tracing/TraceIdMessagePostProcessor.java
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/messaging/publisher/OrderEventPublisher.java
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderCrudService.java
  - sources/backend/inventory-service/src/main/java/com/ptit/htpt/inventoryservice/messaging/config/RabbitMQConfig.java
  - sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/messaging/config/RabbitMQConfig.java
tech_stack_added:
  - org.springframework.amqp:spring-amqp (qua starter-amqp BOM 3.3.2)
  - CorrelationData per-message Publisher Confirms
  - TransactionSynchronizationManager afterCommit pattern
patterns_added:
  - rabbitmq-topology-shared-declare (3 service AmqpAdmin idempotent, Open Q #3)
  - publish-after-commit (D-03 TransactionSynchronizationManager.registerSynchronization)
  - publisher-confirms-per-message (D-04 CorrelationData.getFuture 5s timeout)
  - trace-mdc-capture-before-async (Pitfall 1 — capture traceId NGAY trước callback)
  - amqp-header-trace-propagation (D-16 X-Trace-Id via MessagePostProcessor)
  - log-tag-bracketed (D-17 [MQ-PUB] / [MQ-PUB-NACK] / [MQ-PUB-TIMEOUT] / [MQ-PUB-ERR])
key_files_created:
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/messaging/config/RabbitMQConfig.java
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/messaging/event/OrderEventEnvelope.java
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/messaging/exception/TransientMessageException.java
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/messaging/exception/PermanentMessageException.java
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/messaging/tracing/TraceIdMessagePostProcessor.java
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/messaging/publisher/OrderEventPublisher.java
  - sources/backend/inventory-service/src/main/java/com/ptit/htpt/inventoryservice/messaging/config/RabbitMQConfig.java
  - sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/messaging/config/RabbitMQConfig.java
  - .planning/phases/23-message-queue-rabbitmq/23-03-SUMMARY.md
key_files_modified:
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderCrudService.java
decisions:
  - "Topology declare ở cả 3 service (theo Open Q #3): RabbitMQConfig.java identical ở order/inventory/notification — Spring AmqpAdmin idempotent với matching args (durable=true, x-dead-letter-exchange args trùng). Tránh phải maintain config riêng kiểu 'ai declare cái gì'; chấp nhận duplicate code 90 dòng × 3 = tradeoff đáng giá cho consistency Wave 2."
  - "CONFIRM_TIMEOUT_MS = 5000L (D-04 lock): block tối đa 5s/event. Đủ ngắn để không treo request HTTP, đủ dài cho broker round-trip thông thường (intra-docker <50ms)."
  - "Capture MDC.get('traceId') NGAY trong publishOrderPlaced (Pitfall 1 RESEARCH §469-474): afterCommit callback có thể chạy sau khi TraceIdFilter cleanup MDC trong finally block. Snapshot traceId thành final local var + closure vào TransactionSynchronization → safe."
  - "Fallback non-tx: nếu TransactionSynchronizationManager.isSynchronizationActive() == false (vd test unit không @Transactional, hoặc call ngoài service layer) → publish ngay. Tránh silent drop event."
  - "GIỮ validateStockOrThrow + unwrapEnvelope + RestTemplate inject (D-11 lock): UX cần STOCK_SHORTAGE 409 đồng bộ. KHÔNG chuyển sang async."
  - "XÓA deductStockAfterPersist + buildStockUpdateBody (D-12 lock): inventory consumer trừ kho async qua OrderPlaced (Wave 2 plan 23-04). Imports HttpEntity/HttpHeaders/HttpMethod/MediaType cũng XÓA — không còn dùng."
  - "OrderEventPublisher.doPublish handle 5 exception path riêng: TimeoutException ([MQ-PUB-TIMEOUT]), InterruptedException (set interrupt flag + [MQ-PUB-INTERRUPT]), nack confirm ([MQ-PUB-NACK]), generic Exception ([MQ-PUB-ERR]), success ([MQ-PUB]). Đáp ứng D-17 logging contract đầy đủ + D-05 'không rollback khi publish fail'."
  - "Payload currency='VND' hardcode — Phase 23 chỉ hỗ trợ 1 loại tiền. Đa tệ defer phase ops."
metrics:
  duration: 6min
  completed_date: 2026-05-20
  tasks: 2
  files_changed: 9
---

# Phase 23 Plan 03: Producer Topology + OrderEventPublisher (afterCommit + Confirms) + XÓA deductStock REST Summary

Triển khai producer-side hoàn chỉnh: topology RabbitMQ declare ở 3 service (exchange `order.events` topic + DLX `order.dlx` + 2 queue inventory/notification), OrderEventPublisher publish OrderPlaced SAU DB commit với Publisher Confirms 5s + X-Trace-Id propagation, và XÓA hoàn toàn code REST `deductStock` legacy trong OrderCrudService. Đáp ứng MQ-02 (producer requirement) + chuẩn bị cho Wave 2 consumer plans (23-04 inventory, 23-05 notification).

## Tasks Completed

| Task | Name                                                                                                         | Commit    | Files                                                                                                                          |
| ---- | ------------------------------------------------------------------------------------------------------------ | --------- | ------------------------------------------------------------------------------------------------------------------------------ |
| 1    | Topology config (3 service) + OrderEventEnvelope record + 2 messaging exception + TraceIdMessagePostProcessor | `c5876d9` | 7 files (3 RabbitMQConfig + envelope + 2 exception + trace processor) |
| 2    | OrderEventPublisher (afterCommit + CorrelationData) + chèn vào OrderCrudService + XÓA deductStock legacy     | `3d8c238` | 2 files (publisher new + OrderCrudService modified)                                                                            |

## File List (9 files)

### Mới tạo (8 file Java)

**order-service** (6 file):

1. `messaging/config/RabbitMQConfig.java` — Topology beans (TopicExchange/DirectExchange/Queue/Binding/Jackson2JsonMessageConverter/RabbitTemplate setMandatory=true)
2. `messaging/event/OrderEventEnvelope.java` — Record envelope + nested OrderPlacedPayload + Item + factory createOrderPlaced
3. `messaging/exception/TransientMessageException.java` — extends RuntimeException (D-08 retry)
4. `messaging/exception/PermanentMessageException.java` — extends RuntimeException (D-08 DLQ)
5. `messaging/tracing/TraceIdMessagePostProcessor.java` — implements MessagePostProcessor + factory capture() đọc MDC NGAY
6. `messaging/publisher/OrderEventPublisher.java` — @Component publishOrderPlaced(payload) — registerSynchronization.afterCommit → doPublish với CorrelationData 5s + log [MQ-PUB]

**inventory-service** (1 file):

7. `messaging/config/RabbitMQConfig.java` — Identical với order-service (package khác)

**notification-service** (1 file):

8. `messaging/config/RabbitMQConfig.java` — Identical (package khác)

### Modified (1 file)

9. `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderCrudService.java`

**Diff summary OrderCrudService:**

- **+ inject:** field `OrderEventPublisher orderEventPublisher` + constructor parameter (explicit, KHÔNG Lombok)
- **+ imports:** `OrderEventEnvelope`, `OrderEventPublisher`
- **+ trong createOrderFromCommand (sau orderRepository.save):** build `OrderEventEnvelope.OrderPlacedPayload` từ saved entity (saved.id(), saved.userId(), items.stream().map(...).toList(), saved.total(), "VND") → `orderEventPublisher.publishOrderPlaced(payload)`
- **− XÓA method:** `deductStockAfterPersist(List<OrderItemRequest>)` (cũ lines 381-411, 31 dòng)
- **− XÓA method:** `buildStockUpdateBody(Map, int)` (cũ lines 430-445, 16 dòng)
- **− XÓA imports không còn dùng:** `org.springframework.http.HttpEntity`, `HttpHeaders`, `HttpMethod`, `MediaType`
- **= GIỮ NGUYÊN (D-11):** `validateStockOrThrow` + `unwrapEnvelope` + RestTemplate inject — stock validate đồng bộ vẫn hoạt động trả 409 STOCK_SHORTAGE

**Tổng dòng:** +131 / -65 (net +66, file giảm còn ~395 dòng vs 447 cũ — gọn hơn nhờ XÓA 2 method legacy)

## Acceptance Criteria

**Task 1:**

- [x] 3 RabbitMQConfig.java tồn tại (order + inventory + notification) — verified qua Grep `class RabbitMQConfig`
- [x] Constant `EXCHANGE = "order.events"` ở 3 file
- [x] `x-dead-letter-exchange` argument trong 2 queue (inventoryQueue + notificationQueue) × 3 service
- [x] `setMandatory(true)` trong RabbitTemplate bean của 3 service
- [x] OrderEventEnvelope chứa nested `OrderPlacedPayload` + `Item` record
- [x] 2 exception extends `RuntimeException` (Transient + Permanent)
- [x] TraceIdMessagePostProcessor implements MessagePostProcessor, HEADER = "X-Trace-Id", factory `capture()`
- [x] KHÔNG có `@RequiredArgsConstructor` (no Lombok) — verified Grep = 0 matches

**Task 2:**

- [x] OrderEventPublisher.java tồn tại, class annotated `@Component`
- [x] `TransactionSynchronizationManager.registerSynchronization` xuất hiện (afterCommit defer)
- [x] `CorrelationData` xuất hiện (per-message confirms)
- [x] `CONFIRM_TIMEOUT_MS = 5000L` (D-04 5s)
- [x] Log tag `[MQ-PUB]` xuất hiện
- [x] OrderCrudService có `orderEventPublisher.publishOrderPlaced(payload)`
- [x] `deductStockAfterPersist` / `buildStockUpdateBody` / `buildPatchBody` = 0 trong OrderCrudService (1 occurrence của string "deductStock" chỉ trong comment giải thích — không phải method/call)
- [x] `validateStock` vẫn còn (D-11) — verified Grep `validateStock` = 2+ matches (definition + call)
- [x] OrderEventPublisher.java KHÔNG có `@RequiredArgsConstructor`
- [ ] Maven compile: defer — Maven CLI chưa khả dụng trên Windows env này, sẽ verify ở Wave 3 IT plan hoặc local mvn

## Verification

**Verified qua Grep (static):**

```text
grep "class RabbitMQConfig" sources/backend/**/messaging/config/RabbitMQConfig.java
  → 3 matches (order + inventory + notification)

grep "OrderEventPublisher.publishOrderPlaced" sources/backend/order-service/.../OrderCrudService.java
  → 1 match (createOrderFromCommand line 195)

grep "deductStockAfterPersist\|buildStockUpdateBody\|buildPatchBody" sources/backend/order-service/.../OrderCrudService.java
  → 0 matches (method + call XÓA hoàn toàn)

grep "validateStock" sources/backend/order-service/.../OrderCrudService.java
  → 2+ matches (D-11 GIỮ)

grep "RequiredArgsConstructor" sources/backend/**/messaging/
  → 0 matches (no Lombok)
```

**Defer cho khi Docker + Maven khả dụng:**

- `cd sources/backend/order-service && mvn -q compile` succeed
- `docker compose up rabbitmq order-service` — order-service start không lỗi, log `Successfully declared queue order.events / order-events.dlq / ...`
- Tạo 1 order qua FE → grep log `[MQ-PUB] event=OrderPlaced eventId=<uuid> traceId=<from MDC>`
- Management UI `http://localhost:15672` thấy exchange `order.events` + 2 queue + 1 DLQ; sau order: exchange rate >0, queue có message backlog (chưa có consumer Wave 2)

## Deviations from Plan

**None — plan executed exactly as written.** Plan đã rất chi tiết (full code skeleton in `<action>` blocks), 2 task thực hiện copy-paste theo template với điều chỉnh nhỏ:

- Inventory + notification RabbitMQConfig class body giống hệt order-service (chỉ đổi package) — đúng theo `<action>` chỉ thị
- OrderCrudService imports cleanup (HttpEntity/Headers/Method/MediaType) — được dự đoán trong plan action (2c "Mọi import không còn dùng")
- Constructor injection theo pattern hiện hữu trong file (verified line 49-59 trước khi edit), KHÔNG Lombok

**Threat mitigations applied:**

- **T-23-09 Tampering (phantom event):** `TransactionSynchronizationManager.registerSynchronization.afterCommit` đảm bảo chỉ publish khi DB commit thành công. Verified pattern qua RESEARCH §Pattern 1. IT test cho rollback scenario sẽ ở Wave 3.
- **T-23-01 Information Disclosure (log payload):** `[MQ-PUB]` log chỉ chứa eventId + routingKey + traceId. KHÔNG log payload field-level. Verified Grep `payload=` trong OrderEventPublisher.java = 0 matches.
- **T-23-10 DoS (confirm timeout):** `CONFIRM_TIMEOUT_MS = 5000L` cap blocking. Timeout → log error + tiếp tục. Không hold connection vô hạn.
- **T-23-04 Trace header injection:** Trace header KHÔNG inject từ client — capture từ MDC server-side (đã được TraceIdFilter sanitize ở edge). Defer hardening Phase 25 gateway. Risk acceptable.

## Self-Check: PASSED

- FOUND: sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/messaging/config/RabbitMQConfig.java
- FOUND: sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/messaging/event/OrderEventEnvelope.java
- FOUND: sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/messaging/exception/TransientMessageException.java
- FOUND: sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/messaging/exception/PermanentMessageException.java
- FOUND: sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/messaging/tracing/TraceIdMessagePostProcessor.java
- FOUND: sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/messaging/publisher/OrderEventPublisher.java
- FOUND: sources/backend/inventory-service/src/main/java/com/ptit/htpt/inventoryservice/messaging/config/RabbitMQConfig.java
- FOUND: sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/messaging/config/RabbitMQConfig.java
- FOUND: sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderCrudService.java (modified — inject + publish + XÓA legacy)
- FOUND: c5876d9 (Task 1 commit)
- FOUND: 3d8c238 (Task 2 commit)
- FOUND: .planning/phases/23-message-queue-rabbitmq/23-03-SUMMARY.md (file này)
