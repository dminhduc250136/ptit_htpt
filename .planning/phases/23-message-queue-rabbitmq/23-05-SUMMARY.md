---
phase: 23
plan: 05
subsystem: notification-service / messaging
tags: [notification-service, consumer, dispatch-log, idempotency, template-render, rabbitmq]
requires:
  - notification-service spring-amqp dep + spring.rabbitmq config + V1__init_schema.sql (Plan 23-02 — dispatch_log + processed_events đã sẵn)
  - RabbitMQConfig topology + queue notification.order-events bind order.# (Plan 23-03)
  - OrderEventPublisher publish OrderPlaced afterCommit (Plan 23-03)
provides:
  - OrderPlacedNotifyListener consume notification.order-events
  - Idempotency qua processed_events (PK event_id + ON CONFLICT DO NOTHING)
  - dispatch_log audit per-eventId với status=SENT, channel=email
  - NotificationDispatchService.recordOrderConfirmation render template subject+body
affects:
  - (không sửa file cũ — chỉ thêm 10 file mới)
tech-stack:
  patterns:
    - "Insertion-Order BEFORE business (Pitfall 8): insertIfAbsent processed_events đầu tiên trong tx"
    - "PermanentException extends AmqpRejectAndDontRequeueException → 0 retry, DLQ ngay (Pitfall 4)"
    - "TransientDataAccessException → wrap TransientMessageException → Spring retry interceptor"
    - "MDC traceId từ AMQP header X-Trace-Id (helper TraceIdConsumerInterceptor.enter/exit)"
    - "Template render = StringBuilder concat (KHÔNG Thymeleaf/Freemarker) — dev scope"
key-files:
  created:
    - sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/messaging/event/OrderEventEnvelope.java
    - sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/messaging/exception/TransientMessageException.java
    - sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/messaging/exception/PermanentMessageException.java
    - sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/domain/ProcessedEventEntity.java
    - sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/domain/DispatchLogEntity.java
    - sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/repository/ProcessedEventRepository.java
    - sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/repository/DispatchLogRepository.java
    - sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/service/NotificationDispatchService.java
    - sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/messaging/tracing/TraceIdConsumerInterceptor.java
    - sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/messaging/consumer/OrderPlacedNotifyListener.java
  modified: []
decisions:
  - "KHÔNG tạo migration mới — V1__init_schema.sql của Plan 23-02 đã có sẵn cả dispatch_log + processed_events trong schema notification_svc (verified read file trước khi viết)."
  - "DispatchLogEntity match đúng 10 cột V1 (id/eventId/recipientUserId/channel/subject/body/status/sentAt/createdAt/updatedAt) + factory create() generate UUID + 3 timestamp now()."
  - "PermanentMessageException của notification-service extend AmqpRejectAndDontRequeueException — đồng nhất với inventory-service Plan 23-04 (KHÁC order-service producer-side vẫn RuntimeException). Listener chỉ re-throw."
  - "NotificationDispatchService render template = StringBuilder string concat — scope dev demo, KHÔNG dùng template engine ngoài (Thymeleaf/Freemarker). Subject='Xác nhận đơn hàng {orderId}', body Vietnamese 4 dòng (lời cảm ơn + mã đơn + tổng tiền + số sản phẩm)."
  - "Hằng số STATUS_SENT='SENT' + CHANNEL_EMAIL='email' private static final trong service — match V1 column types VARCHAR(16) cho cả 2 cột."
  - "@RabbitListener(queues = QUEUE_LITERAL) dùng `private static final String QUEUE = \"notification.order-events\"` — annotation cần compile-time constant; KHÔNG import RabbitMQConfig.NOTIFICATION_QUEUE để giữ listener self-contained (decision đồng nhất Plan 23-04 inventory)."
  - "KHÔNG re-declare RabbitMQConfig — đã declare ở Plan 23-03. Verified grep 'class RabbitMQConfig' trong OrderPlacedNotifyListener + TraceIdConsumerInterceptor = 0 match."
  - "Build chưa verify trên Windows env (Maven CLI defer pattern); compile + IT runtime check sẽ thực hiện ở Plan 23-06 hoặc /gsd-verify-work."
metrics:
  duration: 5min
  tasks: 2
  files: 10 (10 created + 0 modified)
  completed: 2026-05-20
---

# Phase 23 Plan 05: Notification Consumer — DispatchLog + OrderPlacedNotifyListener Summary

Triển khai consumer notification-service đáp ứng MQ-04: lắng nghe OrderPlaced từ queue `notification.order-events`, idempotent qua `processed_events`, render template "order-confirmation" đơn giản + insert `dispatch_log` với status=SENT/channel=email, **KHÔNG** gửi SMTP thật. Pattern mirror với inventory-service consumer (Plan 23-04) — chỉ khác queue name + service call.

## What Was Built

### Task 1: Entities + Repositories + Envelope/Exceptions + Service Render (commit `68e5ce8`)

**KHÔNG migration mới** — V1__init_schema.sql của Plan 23-02 đã có sẵn:
- `notification_svc.dispatch_log` (10 cột: id/event_id/recipient_user_id/channel/subject/body/status/sent_at/created_at/updated_at + 2 index)
- `notification_svc.processed_events` (3 cột: event_id PK / event_type / processed_at + 1 index)

**JPA entities + repositories:**

- `ProcessedEventEntity` (PK `event_id` String, schema=notification_svc, record-style accessors)
- `ProcessedEventRepository.insertIfAbsent(eventId, eventType)` qua native `ON CONFLICT (event_id) DO NOTHING` — return true nếu insert thực sự thêm row
- `DispatchLogEntity` match đúng 10 cột V1 + factory `create(eventId, recipientUserId, channel, subject, body, status)` generate UUID + `Instant.now()` cho 3 timestamp
- `DispatchLogRepository.findByEventId / findByRecipientUserId` — debug/audit + user history

**Envelope + exceptions (copy từ inventory-service Plan 23-04, đổi package):**

- `OrderEventEnvelope` record + nested `OrderPlacedPayload` + `Item`
- `TransientMessageException extends RuntimeException`
- `PermanentMessageException extends AmqpRejectAndDontRequeueException` (Pitfall 4 — 0 retry, DLQ ngay)

**NotificationDispatchService.recordOrderConfirmation** — render + persist:

```java
@Transactional
public DispatchLogEntity recordOrderConfirmation(String eventId, OrderPlacedPayload payload) {
  String subject = "Xác nhận đơn hàng " + payload.orderId();
  StringBuilder body = new StringBuilder();
  body.append("Cảm ơn bạn đã đặt hàng!\n");
  body.append("Mã đơn: ").append(payload.orderId()).append("\n");
  body.append("Tổng tiền: ").append(payload.totalAmount()).append(" ").append(payload.currency()).append("\n");
  body.append("Số sản phẩm: ").append(payload.items().size()).append("\n");
  DispatchLogEntity log = DispatchLogEntity.create(eventId, payload.userId(), CHANNEL_EMAIL,
      subject, body.toString(), STATUS_SENT);
  return dispatchLogRepository.save(log);
}
```

Hằng số `CHANNEL_EMAIL="email"` + `STATUS_SENT="SENT"`. KHÔNG SMTP, KHÔNG template engine ngoài.

### Task 2: TraceIdConsumerInterceptor + OrderPlacedNotifyListener (commit `26d45ec`)

**TraceIdConsumerInterceptor** — copy nguyên từ inventory-service đổi package, utility helper KHÔNG Spring AOP.

**OrderPlacedNotifyListener** — `@RabbitListener(queues = "notification.order-events")` + `@Transactional`. Pattern Pitfall 8 identical với inventory listener — chỉ khác queue + service call:

```java
TraceIdConsumerInterceptor.enter(traceIdHeader);
try {
  log.info("[MQ-CONSUME] queue={} eventId={} status=received", QUEUE, eventId);
  boolean inserted = processedEventRepository.insertIfAbsent(eventId, EVENT_TYPE);  // ĐẦU TIÊN
  if (!inserted) {
    log.info("[MQ-CONSUME] queue={} eventId={} status=skipped-duplicate", QUEUE, eventId);
    return;
  }
  notificationDispatchService.recordOrderConfirmation(eventId, envelope.payload());  // DIFF: service call mới
  log.info("[MQ-CONSUME] queue={} eventId={} status=done", QUEUE, eventId);
} catch (PermanentMessageException e) {
  log.error("[MQ-DLQ] eventId={} reason={} payload={}", eventId, e.getMessage(), envelope);
  throw e;
} catch (DataAccessResourceFailureException | TransientDataAccessException e) {
  log.warn("[MQ-RETRY] eventId={} error={}", eventId, e.getMessage());
  throw new TransientMessageException("DB transient on consume eventId=" + eventId, e);
} catch (RuntimeException e) {
  log.error("[MQ-DLQ] eventId={} reason=unexpected error={} payload={}", eventId, e.getMessage(), envelope);
  throw new AmqpRejectAndDontRequeueException("Unexpected: " + e.getMessage(), e);
} finally {
  TraceIdConsumerInterceptor.exit();
}
```

## Diff vs Inventory Listener (chứng minh mirror)

| Aspect | Inventory (Plan 04) | Notification (Plan 05) |
|--------|---------------------|------------------------|
| Class name | `OrderPlacedListener` | `OrderPlacedNotifyListener` |
| QUEUE constant | `"inventory.order-events"` | `"notification.order-events"` |
| Service call | `inventoryCrudService.decrementForOrder(eventId, orderId, items)` | `notificationDispatchService.recordOrderConfirmation(eventId, payload)` |
| Constructor deps | ProcessedEventRepository + InventoryCrudService (2) | ProcessedEventRepository + NotificationDispatchService (2) |
| Exception handling | identical (Permanent re-throw / Transient wrap / RuntimeException fallback) | identical |
| MDC trace setup | TraceIdConsumerInterceptor.enter/exit | identical (cùng package class, copy) |
| Log path | [MQ-CONSUME] received/done/skipped-duplicate + [MQ-DLQ] + [MQ-RETRY] | identical |

Mirror 100% trừ 3 điểm DIFF logic-specific (queue, service, deps). Code review xác minh dễ.

## Acceptance Criteria

| Criteria | Status |
|----------|--------|
| 8 file Java mới Task 1 (envelope + 2 exception + 2 entity + 2 repo + service) tồn tại | PASS |
| DispatchLogEntity `@Table(schema = "notification_svc")` | PASS |
| ProcessedEventRepository native `notification_svc.processed_events` + ON CONFLICT DO NOTHING | PASS |
| NotificationDispatchService có hằng số STATUS_SENT="SENT" + CHANNEL_EMAIL="email" | PASS |
| DispatchLogEntity 10 fields match V1 migration | PASS |
| KHÔNG Lombok | PASS (grep `@RequiredArgsConstructor` notification-service = 0) |
| OrderPlacedNotifyListener `@RabbitListener(queues = "notification.order-events")` + `@Transactional` | PASS |
| `recordOrderConfirmation` được gọi 1 lần trong listener | PASS |
| `insertIfAbsent` gọi TRƯỚC `recordOrderConfirmation` (Pitfall 8) | PASS |
| AmqpRejectAndDontRequeueException grep ≥2 matches (import + RuntimeException fallback throw) | PASS (4 matches) |
| 3 log path `[MQ-CONSUME]` (received/done/skipped-duplicate) | PASS |
| TraceIdConsumerInterceptor đúng package + utility helper | PASS |
| KHÔNG re-declare `class RabbitMQConfig` trong 2 file mới Task 2 | PASS (grep = 0) |
| Compile mvn | DEFERRED (Maven CLI defer pattern Windows env — Plan 23-06 / `/gsd-verify-work`) |

## Deviations from Plan

None — plan executed exactly as written. Plan đã anticipate đúng:
- V1__init_schema.sql của Plan 23-02 đã có sẵn cả 2 bảng → KHÔNG tạo migration mới (plan note "Nếu V1__init_schema.sql của Plan 23-02 đã tạo... thì plan 23-05 KHÔNG tạo migration mới")
- Mirror inventory listener pattern → copy nguyên TraceIdConsumerInterceptor + exception classes, đổi package + đổi service call

## Flyway Apply Log

Defer cho Plan 23-06 IT hoặc `docker compose up notification-service` runtime. Schema notification_svc + 2 bảng đã được tạo từ Plan 23-02 Flyway V1 — JPA entities chỉ cần `ddl-auto=validate` match column names (verified manually cross-reference V1 migration vs DispatchLogEntity 10 fields).

## TDD Gate Compliance

Plan `type: execute` (KHÔNG phải `tdd`), nên không yêu cầu RED/GREEN/REFACTOR gates. Tests sẽ viết ở Plan 23-06 integration tests.

## Self-Check: PASSED

- 10 file Java mới FOUND (verified by Write success + git commit log shows 8+2=10 files created)
- Commit `68e5ce8` Task 1 FOUND (`git log` shows feat(23-05): entities + repos + service render template)
- Commit `26d45ec` Task 2 FOUND (`git log` shows feat(23-05): TraceIdConsumerInterceptor + OrderPlacedNotifyListener)
- ROADMAP.md updated (23-05 marked [x] với commit hashes)
- REQUIREMENTS.md updated (MQ-04 marked [x] + Satisfied 23-05 + Completed 2026-05-20)
- STATE.md updated (Current Position → 5 of 6 / 23-05 COMPLETED; decisions block prepended; completed_plans 19→20)
