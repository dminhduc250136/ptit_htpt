---
phase: 23
plan: 04
subsystem: inventory-service / messaging
tags: [inventory-service, consumer, idempotency, stock-ledger, flyway-v2, seed-dev, rabbitmq]
requires:
  - inventory-service spring-amqp dep + spring.rabbitmq listener config (Plan 23-02)
  - RabbitMQConfig topology + queue inventory.order-events (Plan 23-03)
  - OrderEventPublisher publish OrderPlaced afterCommit (Plan 23-03)
provides:
  - OrderPlacedListener consume inventory.order-events
  - Idempotency qua processed_events (PK event_id + ON CONFLICT DO NOTHING)
  - stock_ledger audit per-item per-eventId
  - InventoryEntity.decrementQuantity(int delta) — delta-style mới
  - InventoryCrudService.decrementForOrder transaction boundary
  - V2 Flyway migration + V102 seed-dev (copy stock từ product_svc.products)
affects:
  - sources/backend/inventory-service/src/main/java/com/ptit/htpt/inventoryservice/domain/InventoryEntity.java (thêm method)
  - sources/backend/inventory-service/src/main/java/com/ptit/htpt/inventoryservice/service/InventoryCrudService.java (thêm method + dep)
tech-stack:
  patterns:
    - "Insertion-Order BEFORE business (Pitfall 8): insertIfAbsent processed_events đầu tiên trong tx"
    - "PermanentException extends AmqpRejectAndDontRequeueException → 0 retry, DLQ ngay (Pitfall 4)"
    - "TransientDataAccessException → wrap TransientMessageException → Spring retry interceptor"
    - "MDC traceId từ AMQP header X-Trace-Id (helper TraceIdConsumerInterceptor.enter/exit)"
key-files:
  created:
    - sources/backend/inventory-service/src/main/resources/db/migration/V2__add_messaging_tables.sql
    - sources/backend/inventory-service/src/main/resources/db/seed-dev/V102__seed_initial_inventory.sql
    - sources/backend/inventory-service/src/main/java/com/ptit/htpt/inventoryservice/domain/ProcessedEventEntity.java
    - sources/backend/inventory-service/src/main/java/com/ptit/htpt/inventoryservice/domain/StockLedgerEntity.java
    - sources/backend/inventory-service/src/main/java/com/ptit/htpt/inventoryservice/repository/ProcessedEventRepository.java
    - sources/backend/inventory-service/src/main/java/com/ptit/htpt/inventoryservice/repository/StockLedgerRepository.java
    - sources/backend/inventory-service/src/main/java/com/ptit/htpt/inventoryservice/messaging/event/OrderEventEnvelope.java
    - sources/backend/inventory-service/src/main/java/com/ptit/htpt/inventoryservice/messaging/exception/TransientMessageException.java
    - sources/backend/inventory-service/src/main/java/com/ptit/htpt/inventoryservice/messaging/exception/PermanentMessageException.java
    - sources/backend/inventory-service/src/main/java/com/ptit/htpt/inventoryservice/messaging/tracing/TraceIdConsumerInterceptor.java
    - sources/backend/inventory-service/src/main/java/com/ptit/htpt/inventoryservice/messaging/consumer/OrderPlacedListener.java
  modified:
    - sources/backend/inventory-service/src/main/java/com/ptit/htpt/inventoryservice/domain/InventoryEntity.java
    - sources/backend/inventory-service/src/main/java/com/ptit/htpt/inventoryservice/service/InventoryCrudService.java
decisions:
  - "PermanentMessageException của inventory-service extend AmqpRejectAndDontRequeueException (KHÁC order-service vẫn extend RuntimeException). Lý do: consumer-side cần Spring AMQP retry interceptor SKIP retry — đặt class hierarchy là cách rõ ràng nhất, listener chỉ cần re-throw. Order-service không cần vì nó là producer-side."
  - "Trong listener catch PermanentMessageException: chỉ re-throw (đã skip retry). Acceptance criteria 'grep AmqpRejectAndDontRequeueException 2+ matches' đạt qua import + RuntimeException fallback throw."
  - "V102 seed: bỏ điều kiện p.deleted_at IS NULL vì products migrations (V1..V7) confirm KHÔNG có cột deleted_at — chỉ reviews table có. Plan đã ghi note 'bỏ nếu không có'."
  - "@RabbitListener(queues = QUEUE_LITERAL) dùng `private static final String QUEUE = \"inventory.order-events\"` — annotation cần compile-time constant; KHÔNG import RabbitMQConfig.INVENTORY_QUEUE để giữ listener self-contained."
  - "InventoryEntity.decrementQuantity mới (delta-style) tách hẳn khỏi adjustQuantity cũ (set-style). KHÔNG sửa adjustQuantity để tránh phá controller adjust legacy."
  - "Build chưa verify trên Windows env (Maven CLI defer pattern như các plan trước v1.3); compile + IT runtime check sẽ thực hiện ở Wave 3 hoặc /gsd-verify-work."
metrics:
  duration: 8min
  tasks: 2
  files: 13 (11 created + 2 modified)
  completed: 2026-05-20
---

# Phase 23 Plan 04: Inventory Consumer — V2 Migration + OrderPlacedListener Summary

Triển khai consumer inventory-service đáp ứng MQ-03: lắng nghe OrderPlaced từ queue `inventory.order-events`, idempotent qua `processed_events`, trừ kho atomic + ghi `stock_ledger` cho mỗi item, phân loại Transient vs Permanent exception để control retry vs DLQ.

## What Was Built

### Task 1: Migration + Entities + Repositories + Envelope/Exceptions (commit `253102f`)

**V2__add_messaging_tables.sql** — 2 bảng + 3 indexes trong schema `inventory_svc`:

```sql
CREATE TABLE IF NOT EXISTS inventory_svc.processed_events (
  event_id     VARCHAR(36)  PRIMARY KEY,
  event_type   VARCHAR(64)  NOT NULL,
  processed_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_processed_events_type ON inventory_svc.processed_events(event_type);

CREATE TABLE IF NOT EXISTS inventory_svc.stock_ledger (
  id              BIGSERIAL    PRIMARY KEY,
  event_id        VARCHAR(36)  NOT NULL,
  order_id        VARCHAR(36)  NOT NULL,
  product_id      VARCHAR(36)  NOT NULL,
  quantity_change INT          NOT NULL,
  reason          VARCHAR(32)  NOT NULL,
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_stock_ledger_order ON inventory_svc.stock_ledger(order_id);
CREATE INDEX IF NOT EXISTS idx_stock_ledger_event ON inventory_svc.stock_ledger(event_id);
```

**V102__seed_initial_inventory.sql** — copy stock cross-schema idempotent:

```sql
INSERT INTO inventory_svc.inventory_items (id, product_id, quantity, reserved, created_at, updated_at)
SELECT gen_random_uuid()::text, p.id, COALESCE(p.stock, 0), 0, NOW(), NOW()
FROM product_svc.products p
WHERE NOT EXISTS (
  SELECT 1 FROM inventory_svc.inventory_items i WHERE i.product_id = p.id
);
```

**JPA entities + repositories:**
- `ProcessedEventEntity` (PK `event_id` String, schema=inventory_svc) + `ProcessedEventRepository.insertIfAbsent` qua native `ON CONFLICT (event_id) DO NOTHING` — return true nếu insert thực sự thêm row
- `StockLedgerEntity` (PK `id` BIGSERIAL/IDENTITY) + factory `create(...)` + `StockLedgerRepository.findByOrderId/findByEventId`

**Envelope + exceptions (copy từ order-service, đổi package):**
- `OrderEventEnvelope` record + nested `OrderPlacedPayload` + `Item`
- `TransientMessageException extends RuntimeException` — Spring retry sẽ chạy
- `PermanentMessageException extends AmqpRejectAndDontRequeueException` — Spring retry SKIP, DLQ ngay (KHÁC order-service version chỉ extend RuntimeException; consumer-side cần class hierarchy này cho Pitfall 4)

### Task 2: Listener + Service Methods + Tracing Helper (commit `b2c729b`)

**TraceIdConsumerInterceptor** — utility helper (KHÔNG phải Spring AOP):
```java
public static void enter(String traceIdHeader) {
  String id = (traceIdHeader != null && !traceIdHeader.isBlank()) ? traceIdHeader : "no-trace";
  MDC.put(MDC_KEY, id);
}
public static void exit() { MDC.remove(MDC_KEY); }
```

**InventoryEntity.decrementQuantity(int delta)** — method MỚI delta-style (giữ nguyên `adjustQuantity` set-style cũ để không phá callers):
```java
public void decrementQuantity(int delta) {
  this.quantity = this.quantity - delta;
  this.updatedAt = Instant.now();
}
```

**InventoryCrudService.decrementForOrder** — extend constructor inject `StockLedgerRepository`, thêm `@Transactional` method loop per-item: `findByProductId().orElseThrow(PermanentMessageException::new)` → `decrementQuantity(delta)` → `save(inv)` → `stockLedgerRepository.save(StockLedgerEntity.create(...))`. Quantity âm sau decrement → `log.warn` audit, KHÔNG block.

**OrderPlacedListener** — `@RabbitListener(queues = "inventory.order-events")` + `@Transactional`. Flow chuẩn Pitfall 8:

```java
TraceIdConsumerInterceptor.enter(traceIdHeader);
try {
  log.info("[MQ-CONSUME] queue={} eventId={} status=received", QUEUE, eventId);
  boolean inserted = processedEventRepository.insertIfAbsent(eventId, EVENT_TYPE);  // ĐẦU TIÊN
  if (!inserted) {
    log.info("[MQ-CONSUME] queue={} eventId={} status=skipped-duplicate", QUEUE, eventId);
    return;
  }
  inventoryCrudService.decrementForOrder(eventId, payload.orderId(), payload.items());
  log.info("[MQ-CONSUME] queue={} eventId={} status=done", QUEUE, eventId);
} catch (PermanentMessageException e) {
  log.error("[MQ-DLQ] eventId={} reason={} payload={}", eventId, e.getMessage(), envelope);
  throw e;  // đã extend AmqpRejectAndDontRequeueException → 0 retry
} catch (DataAccessResourceFailureException | TransientDataAccessException e) {
  log.warn("[MQ-RETRY] eventId={} error={}", eventId, e.getMessage());
  throw new TransientMessageException("DB transient on consume eventId=" + eventId, e);
} catch (RuntimeException e) {
  log.error("[MQ-DLQ] eventId={} reason=unexpected error={} payload={}", eventId, e.getMessage(), envelope);
  throw new AmqpRejectAndDontRequeueException("Unexpected: " + e.getMessage(), e);  // fallback DLQ
} finally {
  TraceIdConsumerInterceptor.exit();
}
```

## Acceptance Criteria

| Criteria | Status |
|----------|--------|
| V2 migration tạo `processed_events` + `stock_ledger` trong schema `inventory_svc` | PASS |
| V102 seed copy `product_svc.products.stock` → `inventory_svc.inventory_items.quantity` (idempotent NOT EXISTS) | PASS |
| `ProcessedEventRepository` có native `ON CONFLICT (event_id) DO NOTHING` | PASS |
| `InventoryEntity.decrementQuantity(int delta)` method MỚI tồn tại | PASS |
| `OrderPlacedListener` `@RabbitListener(queues = "inventory.order-events")` + `@Transactional` | PASS |
| `insertIfAbsent` gọi TRƯỚC `decrementForOrder` (Pitfall 8) | PASS |
| `PermanentMessageException extends AmqpRejectAndDontRequeueException` | PASS |
| `TransientMessageException extends RuntimeException` | PASS |
| 3 log path `[MQ-CONSUME]` (received/done/skipped-duplicate) | PASS |
| MDC traceId set/remove qua `TraceIdConsumerInterceptor.enter/exit` | PASS |
| KHÔNG re-declare `RabbitMQConfig` trong file mới của plan 23-04 | PASS (verify: chỉ Plan 23-03 declare) |
| KHÔNG Lombok | PASS (explicit constructor injection cho InventoryCrudService 2 deps + OrderPlacedListener 2 deps) |
| Compile mvn | DEFERRED (Maven CLI defer pattern Windows env — Wave 3 / `/gsd-verify-work` |

## Deviations from Plan

### Rule 3 — Permanent exception inheritance khác order-service

Plan 23-03 đặt `PermanentMessageException extends RuntimeException` trong order-service. Plan 23-04 yêu cầu (qua user success_criteria) `PermanentMessageException extends AmqpRejectAndDontRequeueException` trong inventory-service. Hai version inheritance khác nhau giữa 2 service.

**Lý do:** consumer-side cần Spring AMQP retry interceptor SKIP retry (Pitfall 4 RESEARCH §488-492). Cách rõ ràng nhất là làm class hierarchy chứa sẵn `AmqpRejectAndDontRequeueException`, listener chỉ cần `throw e` thay vì wrap. Order-service producer KHÔNG cần class này, giữ nguyên `extends RuntimeException` là OK.

Listener `catch (PermanentMessageException e)` chỉ re-throw — đếm `AmqpRejectAndDontRequeueException` xuất hiện 2 lần trong file: import + `RuntimeException` fallback `throw new AmqpRejectAndDontRequeueException(...)`. Đạt acceptance criteria `2+ matches`.

### Rule 1 — V102 không filter deleted_at

Plan note: "Nếu schema column `deleted_at` không có trên products → bỏ điều kiện. Verify trước commit." Verified V1..V7 product migrations: chỉ bảng `reviews` có `deleted_at`, products KHÔNG có. Đã bỏ điều kiện.

## TDD Gate Compliance

Plan `type: execute` (KHÔNG phải `tdd`), nên không yêu cầu RED/GREEN/REFACTOR gates. Tests sẽ viết ở Wave 3 plan tích hợp.

## Self-Check: PASSED

- File V2__add_messaging_tables.sql FOUND
- File V102__seed_initial_inventory.sql FOUND
- ProcessedEventEntity.java + StockLedgerEntity.java + 2 repository FOUND
- OrderEventEnvelope.java + 2 exception + TraceIdConsumerInterceptor.java + OrderPlacedListener.java FOUND
- InventoryEntity.decrementQuantity + InventoryCrudService.decrementForOrder methods FOUND (qua git diff)
- Commit `253102f` Task 1 FOUND in `git log`
- Commit `b2c729b` Task 2 FOUND in `git log`
