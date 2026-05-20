# Phase 23: Message Queue Integration (RabbitMQ) - Context

**Gathered:** 2026-05-20
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase này tích hợp **RabbitMQ** làm message broker để các microservice giao tiếp **bất đồng bộ** trong luồng đặt hàng — đáp ứng yêu cầu BẮT BUỘC 3.3 của đề chủ đề 4 (Microservice + Message Queue).

**Trong scope:**
- Thêm RabbitMQ container vào docker-compose.yml (với Management UI)
- order-service: thêm vai trò **Producer** — publish event `OrderPlaced` sau khi tạo đơn thành công
- inventory-service: thêm vai trò **Consumer** — trừ `InventoryEntity.quantity` + ghi ledger entry
- notification-service: thêm vai trò **Consumer** — ghi vào `dispatch_log` table
- Cơ chế tin cậy: ACK manual, retry 3 lần exponential backoff, Dead Letter Queue
- Idempotency: bảng `processed_events` trong DB của từng consumer service
- Logging xuyên service qua `traceId` propagate trong message header
- Integration test (Testcontainers RabbitMQ) cho luồng end-to-end

**Ngoài scope (defer sang phase sau):**
- Các event khác (`OrderCancelled`, `PaymentSucceeded`, `OrderShipped`...) — phase này chỉ làm `OrderPlaced`
- Saga đầy đủ với compensating action — Phase 23 chỉ làm forward action
- Gửi email/SMS thật qua SMTP — notification consumer chỉ ghi `dispatch_log`
- Admin replay event từ DLQ — chỉ có DLQ exist, replay tool defer
- Tách DB hạ tầng (Phase B trong lộ trình) — sẽ là phase riêng
- Sửa lỗ hổng X-User-Id (Phase C trong lộ trình) — sẽ là phase riêng
- Transactional Outbox pattern — không cần thiết cho mức độ tin cậy đồ án

</domain>

<decisions>
## Implementation Decisions

### Topology

- **D-01:** Phase 23 publish **1 event duy nhất**: `OrderPlaced`. Routing key: `order.placed`. Các event khác (Cancelled, Payment*) defer.
- **D-02:** **Topic exchange** tên `order.events`, type `topic`, durable=true. 2 queue durable: `inventory.order-events` và `notification.order-events`. Cả 2 bind vào exchange với binding key `order.#` (mở rộng dễ về sau khi thêm `order.cancelled`, `order.shipped` mà không phải sửa topology).
- **D-15:** Message format JSON: `{ "eventId": "<uuid>", "eventType": "OrderPlaced", "occurredAt": "<ISO-8601>", "traceId": "<from MDC>", "payload": { orderId, userId, items: [{productId, quantity, priceAtPurchase}], totalAmount, currency } }`. Sử dụng `Jackson2JsonMessageConverter`.

### Publish (Producer Side)

- **D-03:** Publish event **SAU khi DB commit**, không trong cùng transaction. Sử dụng `TransactionSynchronizationManager.registerSynchronization` + `afterCommit()` callback để đảm bảo publish chỉ chạy nếu transaction commit thành công. Tránh anti-pattern "publish trước commit".
- **D-04:** **Publisher Confirms** bật. Config: `spring.rabbitmq.publisher-confirm-type=correlated` + `spring.rabbitmq.publisher-returns=true`. Producer log warning nếu không nhận confirm trong timeout (5s) hoặc nhận `nack`.
- **D-05:** Publish thất bại (broker down, confirm timeout, return) → **log error + alert + trả success cho user**. Order đã có trong DB — không rollback. Defer khả năng admin replay event sang phase sau (ghi vào "Deferred Ideas"). Đây là đánh đổi có chủ ý: nhất quán giữa "đơn đã tạo trong DB" và "phản hồi cho user" quan trọng hơn nhất quán event publishing.

### Reliability (Consumer Side)

- **D-06:** **Idempotency** qua bảng `processed_events(event_id VARCHAR(36) PK, event_type VARCHAR(64), processed_at TIMESTAMPTZ NOT NULL DEFAULT now())`. Mỗi consumer service có bảng riêng trong schema của mình (`inventory_svc.processed_events`, `notification_svc.processed_events`). Pattern xử lý: INSERT eventId trong cùng transaction với business logic; nếu conflict (eventId đã tồn tại) → consumer ACK và bỏ qua message.
- **D-07:** **Retry policy**: 3 lần, exponential backoff 1s → 2s → 4s. Config qua `spring.rabbitmq.listener.simple.retry.*` (`enabled=true`, `max-attempts=3`, `initial-interval=1000`, `multiplier=2.0`, `max-interval=10000`).
- **D-08:** **Phân biệt exception**: tạo 2 custom exception trong package `*.messaging.exception`:
  - `TransientMessageException` — DB timeout, network blip, broker tạm trục trặc → retry theo D-07.
  - `PermanentMessageException` — payload sai format, business invariant vi phạm (vd: product không tồn tại trong inventory) → reject NGAY (không retry), vào DLQ.
  - Logic trong consumer: catch domain exception, phân loại, throw lại exception phù hợp.
- **D-09:** **Dead Letter Exchange** `order.dlx` (type direct) + queue `order-events.dlq` (durable). Queue chính khai báo argument `x-dead-letter-exchange=order.dlx`. Message rơi vào DLQ khi: hết retry, hoặc reject với requeue=false. Verify được trong Management UI tại `http://localhost:15672`.

### Consumers (Business Logic)

- **D-10:** **inventory-service consumer:** consume `OrderPlaced` từ queue `inventory.order-events`. Cho từng item trong payload:
  1. Tìm InventoryEntity theo `productId`. Nếu không có → throw `PermanentMessageException` (data inconsistency, vào DLQ để admin xem).
  2. Giảm `quantity` đi `item.quantity`. Nếu kết quả < 0 → log warning nhưng vẫn cho phép (vì stock đã được validate đồng bộ ở D-11, đến đây là "kho âm" tức là có vấn đề concurrency cần audit).
  3. Ghi 1 record vào bảng mới `inventory_svc.stock_ledger(id, event_id, order_id, product_id, quantity_change, reason='order.placed', created_at)`.
  4. Insert vào `processed_events`.
  5. ACK message.
- **D-11:** **Stock validate GIỮ NGUYÊN** — code D-04 hiện tại trong `OrderCrudService.validateStock()` (REST sync GET product-service để check stock) **không động đến**. User vẫn nhận 409 STOCK_SHORTAGE ngay tại API tạo đơn nếu hết hàng. Lý do: trải nghiệm UX bắt buộc đồng bộ, không thể chấp nhận user biết hết hàng sau vài giây qua notification.
- **D-12:** **Stock deduct REST (D-05 cũ trong OrderCrudService.deductStock) BỎ** — thay bằng publish `OrderPlaced` event. inventory-service trừ kho qua consumer. **Code cũ cần xóa:** method `deductStock(...)` + helper `buildPatchBody(...)` trong OrderCrudService, và mọi call site liên quan. Lưu lại tham chiếu ở SUMMARY khi commit.
- **D-13:** **Seed initial inventory data**: hiện tại `inventory_svc.inventory_items` table có khả năng rỗng/không có record tương ứng với 100 sản phẩm seed (Phase 16). Cần thêm Flyway migration `V102__seed_initial_inventory.sql` ở seed-dev — copy `product_svc.products.stock` sang `inventory_svc.inventory_items.quantity` cho mỗi product. Migration phải idempotent (`ON CONFLICT DO NOTHING`).
- **D-14:** **notification-service consumer:** consume `OrderPlaced` từ queue `notification.order-events`. Logic:
  1. Render template "order-confirmation" với data từ payload (cần thêm template seed nếu chưa có).
  2. Insert 1 record vào `notification_svc.dispatch_log(id, event_id, recipient_user_id, channel='email', subject, body, status='SENT', sent_at)`. KHÔNG gửi SMTP thật.
  3. Insert vào `processed_events`.
  4. ACK message.

### Logging & Tracing

- **D-16:** **Trace propagation**: producer đọc `traceId` từ MDC (đã có sẵn qua `TraceIdFilter`), gắn vào message header `X-Trace-Id`. Consumer interceptor đọc header này set lại vào MDC trước khi xử lý. Demo được: chạy `docker compose logs` thấy cùng 1 `traceId` xuyên 3 service.
- **D-17:** **Logging contract**:
  - Producer: `log.info("[MQ-PUB] event={} routingKey={} eventId={} traceId={}", "OrderPlaced", "order.placed", eventId, traceId)`.
  - Consumer: `log.info("[MQ-CONSUME] queue={} eventId={} status=received|done|failed|skipped-duplicate", queueName, eventId, status)`.
  - Retry: `log.warn("[MQ-RETRY] eventId={} attempt={}/{} error={}", eventId, attempt, max, ex.getMessage())`.
  - DLQ: `log.error("[MQ-DLQ] eventId={} reason={} payload={}", eventId, reason, payloadJson)`.

### Testing

- **D-18:** **Integration test** dùng Testcontainers với image `rabbitmq:3-management`. Spawn 1 RabbitMQ container + 1 PostgreSQL container per test class. Scenario tối thiểu:
  1. Test happy path: order-service publish event → inventory consumer trừ kho → verify quantity giảm + processed_events có row.
  2. Test idempotency: gửi cùng eventId 2 lần → kho chỉ trừ 1 lần.
  3. Test DLQ: consumer throw `PermanentMessageException` → message vào DLQ sau 0 retry.
  4. Test retry: consumer throw `TransientMessageException` 2 lần rồi success → kho trừ đúng 1 lần.

### Claude's Discretion

Các quyết định kỹ thuật phụ tùy researcher/planner quyết định trong giới hạn của các D-* trên:
- Tên cụ thể của các `@Bean` config classes (vd: `RabbitMQConfig`, `OrderEventPublisher`, `OrderEventConsumer`)
- Chi tiết SQL của Flyway migration cho `processed_events`, `stock_ledger`, seed inventory
- Cấu hình prefetch count cho listener container (đề xuất default 10, planner xác minh)
- Tách hay không tách package `messaging/` thành module riêng trong từng service
- Lựa chọn giữa `@RabbitListener` annotation vs programmatic listener registration

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Architecture & Design (dự án)

- `architecture/02-sequence-diagrams.md` — sequence diagrams cho luồng đặt hàng đã có thiết kế Kafka (Phase 23 thay bằng RabbitMQ, các event tên giữ nguyên nhưng broker khác)
- `architecture/services/order-service.md` — ownership boundary của order
- `architecture/services/inventory-service.md` — ownership boundary của inventory
- `architecture/services/notification-service.md` — ownership boundary của notification

### Code patterns sẵn có để tận dụng

- `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/TraceIdFilter.java` — pattern propagate traceId qua MDC, cần extend cho messaging header
- `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderCrudService.java` §115-180 — chỗ cần chèn publisher (sau commit). §338-440 — code D-04 (validate, GIỮ) và D-05 (deduct, XÓA)
- `sources/backend/inventory-service/src/main/java/com/ptit/htpt/inventoryservice/domain/InventoryEntity.java` — entity để giảm quantity
- `sources/backend/inventory-service/src/main/java/com/ptit/htpt/inventoryservice/service/InventoryCrudService.java` — service layer, comment ghi "Phase 8 sẽ re-introduce reservation" — Phase 23 hoàn thành intent này
- `sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/web/NotificationController.java` — có sẵn dispatch CRUD, consumer sẽ tái dùng entity/repo

### Flyway migration pattern

- `sources/backend/order-service/src/main/resources/db/migration/` — đếm V-number hiện tại để chọn V mới
- `sources/backend/inventory-service/src/main/resources/db/migration/` — tương tự
- `sources/backend/notification-service/src/main/resources/db/migration/` — tương tự
- `sources/backend/product-service/src/main/resources/db/seed-dev/V101__seed_catalog_realistic.sql` — pattern seed dev profile (cho D-13 seed inventory)

### Đề bài & lý thuyết đã thảo luận

- Memory: `project_microservice-gaps.md` — lý do chọn RabbitMQ vs Kafka, lộ trình A→B→C→D
- Đề chủ đề 4 — yêu cầu 3.3 (MQ bắt buộc), 3.5 (retry + log lỗi message), 4 (log theo dõi message), mục 5 điểm cộng (event-driven workflow)

### Infrastructure

- `docker-compose.yml` — thêm RabbitMQ service ở root level. Tham khảo pattern healthcheck của postgres ở §11-15.
- `sources/backend/*/pom.xml` — pattern declare spring-boot-starter dependency. Thêm `spring-boot-starter-amqp` vào order-service, inventory-service, notification-service.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **TraceIdFilter** (3 service đã có): pattern OncePerRequestFilter đọc/gen `X-Request-Id` → MDC. Phase 23 mở rộng pattern cho messaging: producer đọc MDC ghi vào message header; consumer interceptor đọc header set MDC trước khi xử lý.
- **ApiResponse envelope + GlobalExceptionHandler**: pattern xử lý lỗi REST đã chuẩn — consumer logic có thể tái dùng các domain exception (vd: `StockShortageException` không apply ở đây nhưng pattern đặt tên/structure dùng được).
- **Testcontainers PostgreSQL**: setup IT hiện tại trong order-service (`AdminChartsControllerIT`, `OrderCouponRaceConditionIT`). Phase 23 mở rộng: thêm `RabbitMQContainer` cùng class.
- **Flyway pattern**: `db/migration` cho schema chính, `db/seed-dev` cho dev seed. Phase 23 thêm migration mới ở cả hai vị trí.
- **JJWT pattern**: hiện chỉ dùng cho HTTP, không cần cho messaging (consumer tin tưởng message từ broker nội bộ).

### Established Patterns

- **DB-per-service (schema-level)**: mỗi service có schema riêng (`order_svc`, `inventory_svc`, `notification_svc`). Bảng `processed_events` thêm vào đúng schema tương ứng.
- **`ApiResponse<T>` wrapper** trên REST response — không liên quan messaging.
- **Spring profile `dev`**: seed migration ở `db/seed-dev` chỉ chạy với profile=dev. D-13 seed inventory phải tuân theo pattern này.
- **Commit prefix tiếng Anh + commit body tiếng Việt**: pattern dự án — giữ nguyên.

### Integration Points

- **order-service tạo đơn**: `OrderCrudService.createOrder()` line ~115-180 — chèn publisher.publishOrderPlaced(...) trong `afterCommit` callback. KHÔNG động `validateStock()` (giữ REST sync). XÓA call `deductStock()` (line ~180 area).
- **inventory-service**: thêm package `messaging/` với listener class. Listener gọi `InventoryCrudService` (extend nếu cần method `decrementForOrder`) + ghi `stock_ledger`.
- **notification-service**: thêm package `messaging/` với listener class. Listener tái dùng entity/repo `DispatchLog` (đã có hoặc cần extend).
- **docker-compose.yml**: chèn service `rabbitmq` ở root, expose port `15672` (Management UI) và `5672` (AMQP). Tất cả backend service mới: thêm `depends_on: rabbitmq: condition: service_healthy` và env var `SPRING_RABBITMQ_HOST=rabbitmq`.

</code_context>

<specifics>
## Specific Ideas

- **Demo flow cho hội đồng**: tạo 1 đơn hàng qua FE → mở Management UI http://localhost:15672 → thấy message đi qua exchange `order.events` → 2 queue → 0 message còn lại (consumer đã ACK) → check DB inventory.quantity giảm + dispatch_log có row mới + processed_events có row mới. Đây là chuỗi demo trực quan, dễ chấm điểm.
- **Demo lỗi**: tạm thời cho 1 consumer throw exception → quan sát message retry 3 lần trong UI → rơi vào DLQ `order-events.dlq` → khôi phục consumer → demo replay thủ công (defer admin tool, dùng Management UI shovel hoặc CLI publish lại).
- **Lý do RabbitMQ thay Kafka (so với architecture/02-sequence-diagrams.md)**: đã ghi trong memory `project_microservice-gaps.md`. Cập nhật architecture doc trong plan.

</specifics>

<deferred>
## Deferred Ideas

Các ý tưởng xuất hiện trong discuss nhưng nằm ngoài scope Phase 23:

1. **Saga đầy đủ với compensating action**: `OrderCancelled` event + inventory hoàn kho + notification gửi mail hủy. Là điểm cộng mục 5 đề bài. → Phase riêng "Saga choreography" sau Phase 23.
2. **Event `PaymentSucceeded` / `PaymentFailed`**: payment-service publish, order + inventory consume. → Phase riêng khi triển khai luồng thanh toán thật.
3. **SMTP integration thật** (Mailtrap sandbox hoặc Gmail App Password): notification consumer gửi email thực sự thay vì chỉ ghi `dispatch_log`. → Phase riêng "Notification SMTP".
4. **Admin replay tool**: UI cho admin xem DLQ và replay message lỗi sau khi sửa bug. → Phase riêng "Admin MQ ops".
5. **Transactional Outbox pattern**: nâng cao độ tin cậy publish (zero message loss). → Defer indefinitely — không cần cho mức đồ án.
6. **Distributed tracing (Zipkin/Jaeger)**: thay vì chỉ traceId trong log, có UI trace xuyên service. → Điểm cộng mục 5 đề, phase riêng "Observability".
7. **Tách DB hạ tầng** (Phase B trong lộ trình lý thuyết): biến shared-postgres-different-schema thành database-per-service thật. → Phase 24.
8. **Sửa lỗ hổng X-User-Id** (Phase C): gateway verify JWT + inject trusted header. → Phase 25.

### Reviewed Todos (not folded)

Không quét todo system (gsd-sdk lỗi). Nếu có todo nào liên quan, user sẽ note.

</deferred>

---

*Phase: 23-message-queue-rabbitmq*
*Context gathered: 2026-05-20*
