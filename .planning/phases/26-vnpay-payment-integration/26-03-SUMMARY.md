---
phase: 26-vnpay-payment-integration
plan: "03"
subsystem: order-service
tags: [vnpay, rabbitmq, payment-events, idempotency, order-placed-defer, payment-session-client]
dependency_graph:
  requires:
    - "26-01: PaymentEventEnvelope contract (payment.events exchange, PaymentSucceeded/Failed shape)"
    - "26-02: ProcessedEventRepository.insertIfAbsent, OrderEntity.paymentStatus/vnpTransactionNo, OrderDto.paymentUrl transient"
  provides:
    - "order-service/PaymentSessionClient: REST client tạo VNPAY session qua gateway"
    - "order-service/PaymentEventListener: consumer queue order.payment-events idempotent"
    - "order-service/RabbitMQConfig: thêm payment.events topology (exchange + DLX/DLQ + queue)"
    - "order-service/OrderCrudService: nhánh VNPAY defer OrderPlaced; COD publish ngay"
  affects:
    - "26-04-frontend-checkout: FE checkout redirect sang paymentUrl khi VNPAY"
tech-stack:
  added: []
  patterns:
    - "PaymentSessionClient: RestTemplate + forward Bearer JWT (T-26-10) + throw 502 khi fail (KHÔNG fallback empty)"
    - "PaymentEventListener: @RabbitListener + @Transactional + insertIfAbsent-first (T-26-09 Pitfall 8)"
    - "publishOrderPlacedForOrder: helper DRY trong OrderCrudService dùng bởi cả COD path lẫn listener"
    - "VNPAY branch: createOrderFromCommand rẽ nhánh theo paymentMethod — defer OrderPlaced đến PAID (D-09)"
key-files:
  created:
    - "sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/PaymentSessionClient.java"
    - "sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/messaging/event/PaymentEventEnvelope.java"
    - "sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/messaging/consumer/PaymentEventListener.java"
    - "sources/backend/order-service/src/test/java/com/ptit/htpt/orderservice/OrderCrudServiceVNPayIT.java"
    - "sources/backend/order-service/src/test/java/com/ptit/htpt/orderservice/PaymentEventListenerIT.java"
  modified:
    - "sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderCrudService.java"
    - "sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/messaging/config/RabbitMQConfig.java"
    - "sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/OrderController.java"
key-decisions:
  - "PaymentSessionClient throw 502 khi fail (KHÔNG fallback empty như ProductBatchClient) — paymentUrl bắt buộc cho đơn VNPAY"
  - "createOrderFromCommand overload 2-arg (backward compat) + 3-arg (với authHeader) — OrderController forward Authorization header"
  - "publishOrderPlacedForOrder helper trong OrderCrudService (KHÔNG inline trong listener) — DRY pattern"
  - "PaymentEventListener inject OrderCrudService (KHÔNG OrderEventPublisher trực tiếp) để dùng helper DRY"
  - "PermanentMessageException order-service extends RuntimeException → listener phải wrap thành AmqpRejectAndDontRequeueException (KHÁC inventory-service extends AmqpRejectAndDontRequeueException)"
  - "Unit tests dùng Mockito (KHÔNG Testcontainers) — env Windows không có Docker; pattern đồng nhất với Plan 26-01"
requirements-completed: [PAY-01, PAY-03, PAY-04]
duration: 5min
completed: 2026-05-22
---

# Phase 26 Plan 03: VNPAY order-service integration — PaymentSessionClient + consumer Summary

**PaymentSessionClient gọi REST sang payment-service tạo session VNPAY; OrderCrudService rẽ nhánh VNPAY (defer OrderPlaced); PaymentEventListener consume PaymentSucceeded/Failed idempotent qua processed_events.**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-05-22T16:30:32Z
- **Completed:** 2026-05-22T16:35:49Z
- **Tasks:** 2/2
- **Files modified:** 8 (5 tạo mới, 3 sửa)

## Accomplishments

- `PaymentSessionClient`: REST client forward Bearer JWT + throw 502 khi payment-service fail (lỗi cứng, KHÔNG fallback)
- `OrderCrudService.createOrderFromCommand`: nhánh VNPAY → `payment_status=PENDING` + gọi `PaymentSessionClient` + trả `paymentUrl` (D-09), KHÔNG publish `OrderPlaced`; nhánh COD → publish `OrderPlaced` ngay (D-10)
- Helper `publishOrderPlacedForOrder` DRY — dùng chung bởi COD path và `PaymentEventListener`
- `PaymentEventEnvelope` record trong order-service — shape IDENTICAL payment-service để Jackson2JsonMessageConverter deserialize
- `RabbitMQConfig` mở rộng: thêm `payment.events` topology (exchange + `payment.dlx` + `payment-events.dlq` + queue `order.payment-events` bind `payment.#`) mà KHÔNG phá topology `order.events` cũ
- `PaymentEventListener`: idempotency-first (T-26-09), PaymentSucceeded→PAID+publish OrderPlaced, PaymentFailed→FAILED, error classification copy OrderPlacedListener
- 6 @Test tổng: `OrderCrudServiceVNPayIT` (3) + `PaymentEventListenerIT` (3)

## Task Commits

1. **Task 1: PaymentSessionClient + OrderCrudService VNPAY branch** — `e278aff` (feat)
2. **Task 2: PaymentEventEnvelope + RabbitMQConfig + PaymentEventListener** — `0128def` (feat)

## Files Created/Modified

- `PaymentSessionClient.java` — NEW: REST POST `/api/payments/sessions` + forward JWT + 502 khi fail
- `PaymentEventEnvelope.java` — NEW: record 5 field + nested `PaymentPayload` + factory `of(...)`
- `PaymentEventListener.java` — NEW: consumer `order.payment-events` idempotent + PAID/FAILED logic
- `OrderCrudService.java` — MODIFY: inject `PaymentSessionClient`, overload `createOrderFromCommand` + VNPAY branch + helper `publishOrderPlacedForOrder`
- `RabbitMQConfig.java` — MODIFY: thêm 6 beans payment topology (KHÔNG đụng order.events beans)
- `OrderController.java` — MODIFY: forward `Authorization` header xuống `createOrderFromCommand`
- `OrderCrudServiceVNPayIT.java` — NEW: 3 @Test VNPAY/COD/error
- `PaymentEventListenerIT.java` — NEW: 3 @Test succeeded/failed/duplicate-idempotent

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing method] createOrderFromCommand thiếu cách nhận authHeader từ controller**
- **Found during:** Task 1
- **Issue:** Controller `OrderController.createOrder` không có `@RequestHeader Authorization` và service không có overload nhận authHeader → PaymentSessionClient không thể nhận JWT để forward
- **Fix:** Thêm overload 2-arg (backward compat) + 3-arg (với authHeader) trong OrderCrudService; cập nhật OrderController forward `Authorization` header
- **Files modified:** `OrderCrudService.java`, `OrderController.java`
- **Commit:** `e278aff`

**2. [Rule 1 - Bug] PermanentMessageException order-service không extends AmqpRejectAndDontRequeueException**
- **Found during:** Task 2
- **Issue:** inventory-service `PermanentMessageException` extends `AmqpRejectAndDontRequeueException` (Spring skip retry tự động); order-service `PermanentMessageException` chỉ extends `RuntimeException` (Phase 23 decision) → nếu listener re-throw thẳng, Spring sẽ retry thay vì DLQ
- **Fix:** `PaymentEventListener` wrap `PermanentMessageException` thành `new AmqpRejectAndDontRequeueException(...)` trước khi throw — đồng nhất với error contract DLQ, không cần thay đổi class exception gốc (tránh break Phase 23 code)
- **Files modified:** `PaymentEventListener.java`
- **Commit:** `0128def`

## Known Stubs

Không có stubs. `PaymentSessionClient.createVNPaySession` gọi HTTP thật và throw 502 khi fail. `PaymentEventListener` xử lý đầy đủ 2 event type. `orderCrudService.publishOrderPlacedForOrder` đã wired thật.

## Threat Flags

Không có threat surface mới ngoài threat model đã có trong plan (T-26-09, T-26-10, T-26-11, T-26-12 tất cả được mitigate trong code).

## Self-Check: PASSED

Files created:
- `PaymentSessionClient.java`: EXISTS
- `PaymentEventEnvelope.java`: EXISTS
- `PaymentEventListener.java`: EXISTS
- `OrderCrudServiceVNPayIT.java`: EXISTS
- `PaymentEventListenerIT.java`: EXISTS

Commits verified:
- `e278aff`: EXISTS
- `0128def`: EXISTS

Acceptance criteria:
- `grep "api-gateway:8080/api/payments/sessions" PaymentSessionClient.java`: PASS (line 32)
- `grep "VNPAY" OrderCrudService.java`: PASS
- `grep "ResponseStatusException" PaymentSessionClient.java`: PASS
- `grep "order.payment-events" PaymentEventListener.java`: PASS (line 43)
- `grep "payment.events" RabbitMQConfig.java`: PASS
- `grep "insertIfAbsent" PaymentEventListener.java`: PASS
- `grep "PAID" PaymentEventListener.java`: PASS
- `grep "FAILED" PaymentEventListener.java`: PASS
- `grep "publishOrderPlaced" PaymentEventListener.java`: PASS (nhánh PaymentSucceeded)
- `OrderCrudServiceVNPayIT.java` với ≥ 3 @Test: PASS (3 tests)
- `PaymentEventListenerIT.java` với ≥ 3 @Test: PASS (3 tests)
- Topology `order.events` cũ không bị phá: PASS (chỉ thêm beans mới)
