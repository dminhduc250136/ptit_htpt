---
phase: 27-real-email-smtp
plan: 01
subsystem: messaging
tags: [rabbitmq, order-service, event-driven, email, notification]

# Dependency graph
requires:
  - phase: 23-message-queue-rabbitmq
    provides: OrderEventPublisher afterCommit pattern, RabbitMQConfig topology, doPublish() Publisher Confirms

provides:
  - OrderEventEnvelope mở rộng với customerEmail + productName + OrderStatusChangedPayload
  - OrderEventPublisher.publishOrderStatusChanged() với afterCommit + routing key order.status-changed
  - OrderCrudService.updateOrderState() publish OrderStatusChanged sau commit
  - resolveCustomerEmail() helper gọi user-service REST để lấy email

affects: [27-02, 27-03, 27-04, 27-05, notification-service consumer]

# Tech tracking
tech-stack:
  added: [com.fasterxml.jackson.annotation.JsonTypeInfo, com.fasterxml.jackson.annotation.JsonSubTypes]
  patterns:
    - Jackson polymorphic deserialization với Object payload + @JsonTypeInfo/@JsonSubTypes
    - afterCommit TransactionSynchronization pattern cho OrderStatusChanged event
    - resolveCustomerEmail REST fallback — empty string + log WARN nếu user-service không available

key-files:
  created: []
  modified:
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/messaging/event/OrderEventEnvelope.java
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/messaging/config/RabbitMQConfig.java
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/messaging/publisher/OrderEventPublisher.java
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderCrudService.java

key-decisions:
  - "Giữ 1 record OrderEventEnvelope, đổi payload sang Object + Jackson @JsonTypeInfo/@JsonSubTypes (Pitfall 3 RESEARCH — tránh refactor publisher generic + listener 2 phía)"
  - "ROUTING_KEY_ORDER_STATUS_CHANGED = order.status-changed — queue notification.order-events bind order.# nên tự route, KHÔNG cần exchange/queue mới"
  - "resolveCustomerEmail(): gọi GET /api/users/{userId} qua api-gateway; fallback '' nếu fail để notification-service ghi SKIPPED (KHÔNG NPE)"
  - "doPublish() refactor thêm routingKey param — dùng chung cho OrderPlaced và OrderStatusChanged"

patterns-established:
  - "Pattern: OrderEventEnvelope polymorphic — 1 envelope record, payload Object, Jackson discriminator theo eventType"
  - "Pattern: resolveCustomerEmail — REST helper với fallback empty string + WARN log"

requirements-completed: [MAIL-03, MAIL-04]

# Metrics
duration: 15min
completed: 2026-05-22
---

# Phase 27 Plan 01: Mở Rộng Order-Service Producer Summary

**OrderEventEnvelope mở rộng với customerEmail + productName + OrderStatusChangedPayload; updateOrderState publish event afterCommit để notification-service gửi email đổi trạng thái.**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-05-22
- **Completed:** 2026-05-22
- **Tasks:** 3 / 3
- **Files modified:** 4

## Accomplishments

### Task 1: Mở rộng OrderEventEnvelope
- Đổi `payload` field từ `OrderPlacedPayload` sang `Object` với Jackson `@JsonTypeInfo/@JsonSubTypes` polymorphic deserialization theo `eventType`
- Thêm `customerEmail` vào `OrderPlacedPayload` — notification-service không cần gọi REST để lấy email người đặt
- Thêm `productName` vào `Item` record — snapshot tên sản phẩm tại thời điểm đặt
- Thêm `OrderStatusChangedPayload` record (orderId, userId, customerEmail, newStatus, customerName)
- Thêm factory `createOrderStatusChanged()` cạnh `createOrderPlaced()`
- Rule 3 fix inline: cập nhật OrderCrudService caller `Item` constructor (3 arg → 4 arg) để không vỡ compile

### Task 2: publishOrderStatusChanged + routing key
- `RabbitMQConfig`: thêm `ROUTING_KEY_ORDER_STATUS_CHANGED = "order.status-changed"` — không cần exchange/queue mới vì `notification.order-events` bind `order.#`
- `OrderEventPublisher.doPublish()`: refactor thêm `routingKey` tham số — dùng chung cho cả 2 event type
- Thêm `publishOrderStatusChanged()` với afterCommit pattern đồng nhất với `publishOrderPlaced()`

### Task 3: Wire OrderCrudService
- `updateOrderState()`: thêm `@Transactional` + build `OrderStatusChangedPayload` + gọi `publishOrderStatusChanged()` sau `orderRepository.save()`
- `createOrderFromCommand()`: thay customerEmail tạm `""` bằng `resolveCustomerEmail(saved)` thật
- Thêm helper `resolveCustomerEmail()`: GET `/api/users/{userId}` qua api-gateway, unwrap envelope, fallback `""` + log WARN nếu fail — KHÔNG NPE

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| Task 1 | 862b992 | feat(27-01): mở rộng OrderEventEnvelope |
| Task 2 | b03bc65 | feat(27-01): thêm publishOrderStatusChanged + routing key |
| Task 3 | 57399a8 | feat(27-01): wire OrderCrudService |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Fix OrderCrudService Item constructor trong Task 1**
- **Found during:** Task 1 — sau khi thêm `productName` vào `Item` record, caller ở OrderCrudService dòng 186 vỡ compile (3 arg → 4 arg)
- **Fix:** Cập nhật ngay trong Task 1: `new OrderEventEnvelope.Item(it.productId(), it.productName(), it.quantity(), it.unitPrice())`
- **Files modified:** OrderCrudService.java
- **Commit:** 862b992 (bundled với Task 1)

## Known Stubs

- `customerEmail` trong `OrderPlacedPayload` sẽ là `""` (empty) nếu `resolveCustomerEmail()` không lấy được email từ user-service (user-service có thể chưa expose GET /users/{id} qua gateway hoặc schema thiếu `email` field). Notification-service Plan 27-03 sẽ xử lý SKIPPED status khi email blank.

## Threat Flags

Không phát hiện threat surface mới ngoài threat model đã đăng ký (T-27-01 customerEmail PII trên broker nội bộ — accepted).

## Self-Check: PASSED

Files created/modified:
- `sources/backend/order-service/.../messaging/event/OrderEventEnvelope.java` — FOUND
- `sources/backend/order-service/.../messaging/config/RabbitMQConfig.java` — FOUND
- `sources/backend/order-service/.../messaging/publisher/OrderEventPublisher.java` — FOUND
- `sources/backend/order-service/.../service/OrderCrudService.java` — FOUND

Commits:
- 862b992 — FOUND
- b03bc65 — FOUND
- 57399a8 — FOUND
