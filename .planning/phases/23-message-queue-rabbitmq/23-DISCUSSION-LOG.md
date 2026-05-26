# Phase 23: Message Queue Integration (RabbitMQ) - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-20
**Phase:** 23-message-queue-rabbitmq
**Areas discussed:** Phạm vi event & topology, Thời điểm publish & độ tin cậy, Idempotency/retry/DLQ, Phạm vi consumer & quan hệ với code hiện tại

---

## Gray Area Selection

| Option | Description | Selected |
|--------|-------------|----------|
| Phạm vi event & topology | Số lượng event, loại exchange, routing key | ✓ |
| Thời điểm publish & độ tin cậy | Publish timing, Publisher Confirms, publish-fail handling | ✓ |
| Idempotency, retry, DLQ | EventId storage, retry strategy, exception classification | ✓ |
| Phạm vi consumer & quan hệ với code hiện tại | Consumer logic, quan hệ với REST stock flow hiện tại | ✓ |

---

## Phạm vi event & topology

### Q1: Phase 23 publish bao nhiêu event?

| Option | Description | Selected |
|--------|-------------|----------|
| Chỉ OrderPlaced (đề xuất) | 1 event duy nhất, đủ đáp ứng 3.3, scope nhỏ | ✓ |
| OrderPlaced + OrderCancelled | Thêm compensating action, minh họa Saga | |
| OrderPlaced + Cancelled + PaymentSucceeded/Failed | Saga choreography đầy đủ, vượt scope | |

**User's choice:** Chỉ OrderPlaced

### Q2: Loại exchange RabbitMQ?

| Option | Description | Selected |
|--------|-------------|----------|
| Topic exchange (đề xuất) | Linh hoạt, mở rộng dễ về sau | ✓ |
| Fanout exchange | Đơn giản nhất, khó mở rộng filter | |
| Direct exchange | Routing key khớp chính xác, ít linh hoạt | |

**User's choice:** Topic exchange

---

## Thời điểm publish & độ tin cậy

### Q1: Thời điểm publish event so với DB commit?

| Option | Description | Selected |
|--------|-------------|----------|
| Sau DB commit, trong code (đề xuất) | TransactionSynchronization afterCommit | ✓ |
| Transactional Outbox pattern | An toàn nhất, phức tạp hơn | |
| Trước DB commit | Anti-pattern, có thể publish event cho đơn rollback | |

**User's choice:** Sau DB commit, trong code

### Q2: Publisher Confirms (broker xác nhận nhận message)?

| Option | Description | Selected |
|--------|-------------|----------|
| Bật (đề xuất) | publisher-confirm-type=correlated, demo độ tin cậy | ✓ |
| Tắt | Fire-and-forget, không phát hiện message thất bại | |

**User's choice:** Bật

### Q3: Xử lý khi publish thất bại (broker xuống)?

| Option | Description | Selected |
|--------|-------------|----------|
| Log error, tiếp tục trả success cho user (đề xuất) | Order vẫn có trong DB, defer admin replay | ✓ |
| Throw exception, trả 500 cho user | User tưởng đơn fail nhưng DB có đơn — không nhất quán | |
| Throw exception + rollback order | Coupling ngược, mất ưu thế decoupling | |

**User's choice:** Log error, tiếp tục trả success cho user

---

## Idempotency, retry, DLQ

### Q1: Lưu eventId đã xử lý ở đâu?

| Option | Description | Selected |
|--------|-------------|----------|
| Bảng processed_events trong DB của từng consumer (đề xuất) | Giữ DB-per-service, transactional cùng business logic | ✓ |
| Redis chia sẻ | Nhanh hơn, thêm dependency mới, vi phạm DB-per-service | |
| Dựa vào UNIQUE constraint nghiệp vụ | Khó mở rộng, khó debug | |

**User's choice:** Bảng processed_events trong DB của từng consumer

### Q2: Chiến lược retry?

| Option | Description | Selected |
|--------|-------------|----------|
| 3 lần, exponential backoff 1s/2s/4s (đề xuất) | Cấu hình chuẩn Spring AMQP, đủ cho lỗi transient | ✓ |
| 5 lần, backoff 1s/5s/15s/30s/60s | Retry lâu hơn, khó demo | |
| Không retry, DLQ ngay | Không đáp ứng yêu cầu 3.5 "retry" | |

**User's choice:** 3 lần, exponential backoff 1s/2s/4s

### Q3: Phân biệt transient vs permanent exception?

| Option | Description | Selected |
|--------|-------------|----------|
| Có, qua 2 exception class (đề xuất) | TransientMessageException retry, PermanentMessageException DLQ ngay | ✓ |
| Không, retry tất cả rồi vào DLQ | Đơn giản hơn nhưng lãng phí retry cho lỗi format | |

**User's choice:** Có, qua 2 exception class

---

## Phạm vi consumer & quan hệ với code hiện tại

### Q1 (vòng 1): Inventory consumer sẽ làm gì khi nhận OrderPlaced?

| Option | Description | Selected |
|--------|-------------|----------|
| Trừ kho qua inventory-service (đề xuất) | Đúng microservice boundary | ✓ |
| Trừ kho qua product-service | Vi phạm service boundary | |
| Cả hai consumer tại product + inventory | Coupling cao, effort gấp đôi | |

**User's choice:** Trừ kho qua inventory-service

### Q2 (vòng 1): REST trừ stock hiện tại (order → product-service PATCH) xử lý sao?

| Option | Description | Selected (vòng 1) | Final (sau cảnh báo) |
|--------|-------------|----------|----------|
| Giữ nguyên, MQ THÊM ledger inventory (đề xuất ban đầu) | Rủi ro thấp nhất, không động code order | | |
| Bỏ REST hiện tại, MQ THAY hoàn toàn | Đúng microservice hơn, rủi ro cao (Saga đầy đủ) | ✓ | |
| Giữ REST, MQ chỉ là audit log | An toàn nhưng hội đồng có thể hỏi "tại sao MQ chỉ để log" | | |

**Notes:** Claude cảnh báo phương án "MQ THAY hoàn toàn" sẽ phá luồng stock validate đồng bộ, sinh Saga đầy đủ, vượt scope. User được hỏi lại vòng 2.

### Q3 (vòng 1): Notification consumer làm gì?

| Option | Description | Selected (vòng 1) | Final (sau cảnh báo) |
|--------|-------------|----------|----------|
| Ghi vào dispatch_log (đề xuất ban đầu) | An toàn, demo end-to-end MQ flow | | |
| Gửi email thật qua SMTP (Mailtrap/Gmail) | Demo ấn tượng, thêm dependency SMTP | ✓ | |
| Webhook + FE toast realtime | FE chưa có WebSocket, effort cao | | |

**Notes:** Claude cảnh báo phương án SMTP thật cần Mailtrap/Gmail credential, có rate limit/bounce làm consumer fail vì lý do không liên quan MQ. User được hỏi lại vòng 2.

### Q1 (vòng 2): Stock flow — sau khi biết rủi ro, muốn làm sao?

| Option | Description | Selected |
|--------|-------------|----------|
| Giữ REST validate, MQ chỉ thay REST deduct (đề xuất lại) | Validate đồng bộ giữ UX, deduct chuyển event-driven | ✓ |
| Giữ nguyên REST, MQ THÊM ledger (an toàn nhất) | Không động code order, ít ý nghĩa kỹ thuật hơn | |
| Giữ quyết định cũ: MQ THAY hoàn toàn | Chấp nhận scope lớn, thêm Saga | |

**User's choice (final):** Giữ REST validate, MQ chỉ thay REST deduct

### Q2 (vòng 2): Notification — sau khi biết rủi ro, muốn làm sao?

| Option | Description | Selected |
|--------|-------------|----------|
| Ghi dispatch_log thôi (đề xuất lại) | An toàn, defer SMTP cho phase riêng | ✓ |
| Gửi email thật qua Mailtrap (sandbox) | Demo hấp dẫn hơn, vẫn cần credential | |
| Gửi email thật qua Gmail/SMTP thật | Effort cao | |

**User's choice (final):** Ghi dispatch_log thôi

---

## Claude's Discretion

- Tên cụ thể của các `@Bean` config classes
- Chi tiết SQL của Flyway migration cho `processed_events`, `stock_ledger`, seed inventory
- Cấu hình prefetch count cho listener container (đề xuất default 10)
- Tách hay không tách package `messaging/` thành module riêng
- Lựa chọn giữa `@RabbitListener` annotation vs programmatic registration

## Deferred Ideas

1. Saga đầy đủ với compensating action (`OrderCancelled` event flow)
2. Event `PaymentSucceeded` / `PaymentFailed`
3. SMTP integration thật (Mailtrap/Gmail)
4. Admin replay tool cho DLQ
5. Transactional Outbox pattern
6. Distributed tracing (Zipkin/Jaeger)
7. Tách DB hạ tầng (Phase 24 trong lộ trình)
8. Sửa lỗ hổng X-User-Id (Phase 25 trong lộ trình)
