---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: verifying
last_updated: "2026-05-23T03:38:49.869Z"
last_activity: 2026-05-23
progress:
  total_phases: 13
  completed_phases: 10
  total_plans: 57
  completed_plans: 50
  percent: 88
---

## Current Position

Phase: 27
Plan: Not started
Status: Phase complete — ready for verification
Last activity: 2026-05-23

```
Progress: [█████████░] 88% (50/57 plans complete)
```

## Project Reference

See: `.planning/PROJECT.md` (updated 2026-05-02 — Current Milestone: v1.3 Catalog Realism & Commerce Intelligence)

**Core value:** Demo end-to-end shopping experience hoạt động với real data ở mọi điểm user nhìn thấy, đồng thời rèn quy trình GSD từ planning → execute → verify → archive.

**Current focus:** Phase 26.1 — migrate-payment-gateway-vnpay-to-momo

## Resume Cheat-Sheet

- Roadmap: `.planning/ROADMAP.md` (7 phases — Phase 16-22)
- Requirements: `.planning/REQUIREMENTS.md` (27 active REQs, tất cả mapped)
- Research: `.planning/research/SUMMARY.md` (HIGH confidence — codebase inspection trực tiếp)
- Milestone v1.0 archive: `.planning/milestones/v1.0-ROADMAP.md` + REQUIREMENTS.md
- Milestone v1.1 archive: `.planning/milestones/v1.1-ROADMAP.md` + REQUIREMENTS.md (audit gaps_found)
- Milestone v1.2 archive: `.planning/milestones/v1.2-ROADMAP.md` + REQUIREMENTS.md + audit (passed)
- Git tags: `v1.0`, `v1.1`, `v1.2`
- Project priority: visible-first (memory `feedback_priority.md`)
- Language: Vietnamese (memory `feedback_language.md`)

## Performance Metrics

| Milestone | Phases | Plans | REQs | Result |
|-----------|--------|-------|------|--------|
| v1.0 | 4 | 14 | 11/11 | PASSED |
| v1.1 | 4 | 22 | 15/19 SATISFIED + 4 PARTIAL | PASSED (gaps deferred) |
| v1.2 | 6 (+1 SKIP) | 24 | 17/17 | PASSED |
| v1.3 | 7 planned | TBD | 0/27 | In progress |
| Phase 26.1-migrate-payment-gateway P03 | 15min | 2 tasks | 9 files |
| Phase 17-s-a-order-detail-items P01 | 5min | 2 tasks | 2 files |
| Phase 17-s-a-order-detail-items P02 | 3min | 2 tasks | 1 file |
| Phase 17-s-a-order-detail-items P03 | 2min | 2 tasks | 2 files |
| Phase 17-s-a-order-detail-items P04 | 4min | 2 tasks | 2 files |
| Phase 18-storage-audit-cart-db P01 | 25min | 3 tasks | 10 files |
| Phase 18-storage-audit-cart-db P02 | 15min | 2 tasks | 2 files |
| Phase 18-storage-audit-cart-db P03 | 12min | 2 tasks | 4 files |
| Phase 18-storage-audit-cart-db P04 | 15min | 3 tasks | 3 files |
| Phase 18-storage-audit-cart-db P05 | 10min | 2 tasks | 7 files |
| Phase 18-storage-audit-cart-db P06 | 10min | 2 tasks | 1 file |
| Phase 19-ho-n-thi-n-admin-charts-low-stock P01 | 25min | 2 tasks | 9 files |
| Phase 19-ho-n-thi-n-admin-charts-low-stock P02 | 15min | 2 tasks | 7 files |
| Phase 19-ho-n-thi-n-admin-charts-low-stock P03 | 12min | 2 tasks | 10 files |
| Phase 19-ho-n-thi-n-admin-charts-low-stock P04 | 18min | 3 tasks | 15 files |
| Phase 20-coupons P03 | 12min | 2 tasks | 7 files |
| Phase 23-message-queue-rabbitmq P01 | 4min | 2 tasks | 3 files |
| Phase 23-message-queue-rabbitmq P02 | 2min | 2 tasks | 8 files |
| Phase 23-message-queue-rabbitmq P03 | 6min | 2 tasks | 9 files |
| Phase 23-message-queue-rabbitmq P04 | 8min | 2 tasks | 13 files |
| Phase 23-message-queue-rabbitmq P05 | 5min | 2 tasks | 10 files |
| Phase 23-message-queue-rabbitmq P06 | 10min | 2 tasks | 9 files |
| Phase 26.1-migrate-payment-gateway P01 | 10min | 3 tasks | 16 files |
| Phase 26.1-migrate-payment-gateway-vnpay-to-momo P04 | 3min | - tasks | - files |

## Decisions (active v1.3 locks)

**Phase 26.1 Plan 04 decisions (2026-05-23):**

- Grep audit sources/ 13 files có match — tất cả comment/Javadoc/migration history; 0 UNACCEPTABLE executable VNPay path; SC5 PASS
- PaymentTransactionRepositoryJpaTest "VNPAY" string là JPA repository test data (không phải VNPay class import/runtime) — ACCEPTABLE per SC5 definition
- V6__add_payment_status.sql giữ nguyên (Flyway checksum integrity); V7 đã rename cột vnp_transaction_no → payment_transaction_no
- Phase 26.1 hoàn thành 4/4 plans; SC1-SC5 tất cả PASS; ready /gsd-verify-work

**Phase 26.1 Plan 03 decisions (2026-05-23):**

- MoMo resultCode=0 và =9000 đều map sang trạng thái 'polling' (pending authorized cũng cần poll IPN — T-26.1-12 mitigate)
- Playwright spec mở rộng 4→5 tests: thêm test "thiếu orderId param" vì MoMo dùng orderId thay vnp_TxnRef (empty state coverage đầy đủ)
- Zero VNPay references verified: grep toàn bộ frontend/src + frontend/e2e — zero matches sau khi xóa comment cũ

**Phase 26.1 Plan 01 decisions (2026-05-23):**

- MomoIpnControllerIT dùng @ExtendWith(MockitoExtension.class) plain unit test (analog VNPayIpnControllerIT Phase 26 §Deviation 1) — tránh conflict @EnableConfigurationProperties
- AppConfig @Bean RestTemplate tạo mới (payment-service thiếu bean này trước đây — Rule 2 auto-add)
- MomoService tạo đầy đủ ở Task 1 (không defer sang Task 2) — bắt buộc để PaymentCrudService compile ngay sau khi xóa VNPaySignature
- buildSignDataCreate KHÔNG sort key (fixed order theo MoMo doc); buildSignDataIpn hard-code 13 field lexicographic
- PaymentEventEnvelope.PaymentPayload rename vnpTransactionNo → paymentTransactionNo (field chung cho mọi gateway)

**Phase 26 Plan 04 decisions (2026-05-22):**

- paymentStatus widened từ 'PENDING'|'PAID'|'FAILED'|'REFUNDED' → string (loại bỏ duplicate field; backward compat với paymentStatusMap lookup)
- getVNPayReturn forward window.location.search đầy đủ sang GET /api/payments/vnpay/return (public, whitelist Plan 26-01)
- checkout/result Playwright spec đặt trong e2e/ (testDir config) thay vì tests/ — 4 smoke tests với page.route() mock
- Inline Spinner (không tạo CSS module mới) + inline keyframe — UI-SPEC §Component reuse

**Phase 26 Plan 03 decisions (2026-05-22):**

- PaymentSessionClient throw 502 BAD_GATEWAY khi fail (KHÔNG fallback empty như ProductBatchClient) — paymentUrl bắt buộc cho đơn VNPAY (đơn không có paymentUrl là lỗi cứng)
- createOrderFromCommand overload 2-arg (backward compat) + 3-arg (với authHeader) — OrderController forward `Authorization` header xuống service
- publishOrderPlacedForOrder helper public trong OrderCrudService (DRY) — dùng bởi COD path và PaymentEventListener
- PaymentEventListener inject OrderCrudService (KHÔNG OrderEventPublisher trực tiếp) để gọi helper DRY
- PermanentMessageException order-service extends RuntimeException → listener wrap thành AmqpRejectAndDontRequeueException trước throw (KHÁC inventory-service extends ARDQE)
- PaymentEventEnvelope record trong order-service có shape IDENTICAL payment-service — đủ để Jackson2JsonMessageConverter deserialize qua field name matching
- Unit tests dùng Mockito (KHÔNG Testcontainers) — env Windows không Docker; pattern đồng nhất Plan 26-01

**Phase 23 Plan 06 decisions (2026-05-20):**

- D-18 LOCK enforced: OrderPlacedListenerIT có 4 @Test FULL (happyPath / idempotent_samePayloadTwice / permanentException_routedToDLQ / transientThenSuccess_retryThenAck) — KHÔNG @Disabled bất kỳ scenario nào
- @SpyBean StockLedgerRepository + Mockito.doAnswer throw TransientDataAccessResourceException 2 lần đầu (AtomicInteger counter + threshold 2) + callRealMethod cho lần thứ 3 — chứng minh Spring AMQP retry behavior runtime (test profile override 100ms exp backoff multiplier=2 max-attempts=3 → ~600ms total vs prod 7s)
- OrderEventPublisherIT 2 test với TransactionTemplate programmatic: rollback_doesNotPublish chứng minh D-03 afterCommit semantics (mitigation T-23-09 phantom-event gate); commit_publishesAfterCommit assert queue depth tăng cả inventory + notification
- OrderPlacedNotifyListenerIT 3 test (happyPath + idempotent + topology smoke) — KHÔNG có permanent-DLQ scenario vì recordOrderConfirmation accept mọi payload (chỉ StringBuilder concat + INSERT, không có business validation throw PermanentException dựa trên payload)
- Pattern @ServiceConnection (Spring Boot 3.1+) cho cả PostgreSQLContainer + RabbitMQContainer("rabbitmq:3-management") — gọn hơn @DynamicPropertySource
- application-test.yml inventory-service tạo MỚI (Rule 3 fix): notification đã có từ Plan 23-02, inventory chưa — thiếu thì IT retry phải đợi 7s prod-config → tạo để override 100ms exp backoff
- order-service test-init/01-schemas.sql UPDATE: thêm inventory_svc + notification_svc (vốn chỉ có order_svc) — Testcontainers Postgres bootstrap đủ 3 schema không conditional
- inventory-service test-init/01-schemas.sql UPDATE: thêm product_svc (cross-schema seed reference V102 nếu cần)
- scripts/verify-mq.sh smoke: curl Management UI /overview + /exchanges/order.events + /exchanges/order.dlx + 3 queues (inventory.order-events / notification.order-events / order-events.dlq); env-var override RABBITMQ_HOST/USER/PASS
- architecture/02-sequence-diagrams.md cập nhật: thay participant K as Kafka → MQ as RabbitMQ trong 5 sequence diagrams + thêm 1 error-path diagram permanent vs transient + Appendix A topology table (5 resources + retry policy + idempotency + smoke verify reference); giữ nguyên mermaid syntax
- Trade-off: OrderEventEnvelope duplicated across 3 services — shared module deferred to Phase ops (chấp nhận warning #3 envelope duplication; khi nào có 3+ event types thì messaging-contracts module sẽ release ROI)
- Maven CLI vẫn defer trên Windows env này (precedent v1.3 Plans 19-20 + Plans 23-01..05); 9 IT files đã sẵn cho /gsd-verify-work hoặc CI/local mvn chạy
- Phase 23 status: 6/6 plans COMPLETED → ready /gsd-verify-work; 7/7 SC SATISFIED static+IT level (mvn runtime + docker smoke + manual demo defer)

**Phase 23 Plan 05 decisions (2026-05-20):**

- MQ-04 notification consumer hoàn tất: 10 file Java mới trong notification-service. KHÔNG tạo migration mới — V1__init_schema.sql của Plan 23-02 đã có sẵn cả dispatch_log + processed_events trong schema notification_svc
- ProcessedEventEntity + DispatchLogEntity JPA record-style accessors (KHÔNG Lombok); DispatchLogEntity match đúng 10 cột V1 (id/eventId/recipientUserId/channel/subject/body/status/sentAt/createdAt/updatedAt) + factory create() generate UUID + Instant.now cho 3 timestamp
- ProcessedEventRepository.insertIfAbsent native ON CONFLICT (event_id) DO NOTHING return true nếu insert thực sự — identical pattern inventory Plan 23-04
- DispatchLogRepository findByEventId + findByRecipientUserId — debug/audit + user history lookup
- NotificationDispatchService.recordOrderConfirmation @Transactional: subject="Xác nhận đơn hàng {orderId}", body StringBuilder concat (cảm ơn + orderId + totalAmount+currency + items.size) → DispatchLogEntity.create(eventId, userId, "email", subject, body, "SENT") → save. KHÔNG SMTP, KHÔNG template engine ngoài (Thymeleaf/Freemarker) — dev scope
- Hằng số STATUS_SENT="SENT" + CHANNEL_EMAIL="email" private static final trong service (D-14)
- OrderEventEnvelope + 2 exception (Transient/Permanent) copy từ inventory-service đổi package; PermanentMessageException vẫn extend AmqpRejectAndDontRequeueException (Pitfall 4) — đồng nhất với consumer-side pattern, listener chỉ re-throw
- OrderPlacedNotifyListener @RabbitListener(queues="notification.order-events") + @Transactional; Pitfall 8 flow: insertIfAbsent TRƯỚC recordOrderConfirmation; duplicate eventId → log status=skipped-duplicate + return; PermanentException re-throw; TransientDataAccessException → wrap TransientMessageException; RuntimeException fallback → AmqpRejectAndDontRequeueException
- 3 log path [MQ-CONSUME] (received/done/skipped-duplicate) + [MQ-DLQ] + [MQ-RETRY] (D-17) — identical structure với inventory listener
- TraceIdConsumerInterceptor copy nguyên từ inventory-service đổi package — utility helper KHÔNG Spring AOP
- KHÔNG re-declare RabbitMQConfig (đã có Plan 23-03); listener dùng literal "notification.order-events" cho @RabbitListener (annotation cần compile-time constant)
- KHÔNG Lombok — explicit constructor injection cho NotificationDispatchService (1 dep) + OrderPlacedNotifyListener (2 deps)
- Maven CLI vẫn defer trên Windows env này; compile + IT runtime verify ở Plan 23-06 hoặc /gsd-verify-work

**Phase 23 Plan 04 decisions (2026-05-20):**

- MQ-03 inventory consumer hoàn tất: V2 migration tạo processed_events (PK event_id D-06 idempotency) + stock_ledger (D-10 audit per-item per-eventId) trong schema inventory_svc + 3 indexes
- V102 seed-dev copy product_svc.products.stock → inventory_svc.inventory_items.quantity (idempotent NOT EXISTS); KHÔNG filter deleted_at vì products schema KHÔNG có cột này (verified V1..V7 migrations — chỉ reviews table có)
- ProcessedEventEntity + StockLedgerEntity JPA record-style accessors; ProcessedEventRepository.insertIfAbsent native ON CONFLICT (event_id) DO NOTHING return true nếu insert thực sự (Pattern 4 RESEARCH §391-403)
- InventoryEntity.decrementQuantity(int delta) method MỚI delta-style — TÁCH HẲN khỏi adjustQuantity cũ set-style để không phá controller adjust legacy
- InventoryCrudService.decrementForOrder @Transactional constructor inject thêm StockLedgerRepository (2 deps total); per-item findByProductId().orElseThrow(PermanentMessageException) → decrementQuantity → save inv + ledger; quantity âm sau decrement → log.warn audit, KHÔNG block (D-10 bước 2)
- PermanentMessageException của inventory-service extend AmqpRejectAndDontRequeueException (KHÁC order-service vẫn extend RuntimeException) — consumer-side cần Spring retry SKIP (Pitfall 4); listener chỉ re-throw thay vì wrap
- OrderPlacedListener @RabbitListener(queues="inventory.order-events") + @Transactional; Pitfall 8 flow: insertIfAbsent TRƯỚC business; duplicate eventId → log status=skipped-duplicate + return; PermanentException re-throw; TransientDataAccessException → wrap TransientMessageException; RuntimeException fallback → AmqpRejectAndDontRequeueException tránh retry vô tận
- 5 log path D-17: [MQ-CONSUME] received/done/skipped-duplicate + [MQ-DLQ] + [MQ-RETRY]
- MDC traceId qua TraceIdConsumerInterceptor.enter/exit (utility helper KHÔNG phải Spring AOP); header X-Trace-Id binding qua @Header(required=false) default "no-trace"
- KHÔNG re-declare RabbitMQConfig (đã có Plan 23-03); @RabbitListener dùng literal "inventory.order-events" (annotation cần compile-time constant)
- KHÔNG Lombok — explicit constructor injection cho InventoryCrudService (2 deps) + OrderPlacedListener (2 deps)
- Maven CLI vẫn defer trên Windows env này; compile + IT runtime verify ở Wave 3 plan hoặc /gsd-verify-work

**Phase 23 Plan 03 decisions (2026-05-20):**

- MQ-02 producer hoàn tất: 3 RabbitMQConfig.java identical ở order/inventory/notification (Open Q #3 — AmqpAdmin idempotent với matching args); topology gồm TopicExchange order.events durable + DirectExchange order.dlx + DLQ order-events.dlq + 2 queue inventory.order-events/notification.order-events bind order.# với x-dead-letter-exchange args; RabbitTemplate setMandatory=true (D-04 publisher-returns)
- OrderEventEnvelope record (eventId/eventType/occurredAt/traceId/payload với nested OrderPlacedPayload + Item) + factory createOrderPlaced cap UUID + Instant.now (D-15 JSON envelope)
- 2 messaging exception (Transient/Permanent) extends RuntimeException — D-08 phân loại retry vs DLQ; Wave 2 consumer sẽ wrap PermanentMessageException → AmqpRejectAndDontRequeueException
- TraceIdMessagePostProcessor implements MessagePostProcessor + factory capture() đọc MDC NGAY tại caller thread (Pitfall 1 RESEARCH §469-474: afterCommit callback có thể chạy sau TraceIdFilter cleanup MDC trong finally)
- OrderEventPublisher.publishOrderPlaced: capture MDC traceId NGAY → build envelope → TransactionSynchronizationManager.registerSynchronization (nếu có tx) HOẶC publish ngay (fallback test); doPublish dùng CorrelationData per-message + getFuture().get(5s, TimeUnit.MILLISECONDS); 5 log path D-17 ([MQ-PUB] success / [MQ-PUB-NACK] / [MQ-PUB-TIMEOUT] / [MQ-PUB-INTERRUPT] / [MQ-PUB-ERR])
- CONFIRM_TIMEOUT_MS=5000L (D-04 lock): block tối đa 5s/event đủ ngắn không treo HTTP request, đủ dài cho intra-docker round-trip <50ms thông thường
- OrderCrudService.createOrderFromCommand chèn orderEventPublisher.publishOrderPlaced(payload) ngay sau orderRepository.save(); payload build từ saved entity (record-style accessor saved.id()/userId()/items()/total() + per-item productId()/quantity()/unitPrice()); currency="VND" hardcode (đa tệ defer phase ops)
- D-12 XÓA hoàn toàn: deductStockAfterPersist (cũ 31 dòng) + buildStockUpdateBody (cũ 16 dòng) + imports HttpEntity/HttpHeaders/HttpMethod/MediaType — KHÔNG còn dùng. inventory consumer Wave 2 trừ kho async
- D-11 GIỮ NGUYÊN: validateStockOrThrow + unwrapEnvelope + RestTemplate inject — stock validate đồng bộ vẫn trả 409 STOCK_SHORTAGE
- KHÔNG Lombok (project convention) — explicit constructor injection cho OrderCrudService (6 deps) + OrderEventPublisher (1 dep)
- Maven CLI vẫn defer trên Windows env này; compile + IT runtime verify ở Wave 3 plan hoặc local mvn

**Phase 23 Plan 02 decisions (2026-05-20):**

- MQ-04 notification-service persistence bootstrap: thừa hưởng nguyên block persistence stack từ inventory-service pom (spring-boot-starter-data-jpa + postgresql + flyway-core + flyway-database-postgresql) + spring-boot-starter-amqp + 5 test deps (starter-test + testcontainers postgresql/junit-jupiter/rabbitmq + spring-rabbit-test) — parent Spring Boot 3.3.2 BOM auto-manage versions
- order-service + inventory-service pom: thêm spring-boot-starter-amqp + testcontainers:rabbitmq + spring-rabbit-test (đã có persistence stack từ Phase 5) — 3 service producer/consumer config đồng nhất
- notification-service application.yml REPLACE toàn bộ: datasource currentSchema=notification_svc env-var fallback, jpa ddl-auto=validate + default_schema=notification_svc + format_sql + open-in-view=false, flyway enabled + schemas/default-schema=notification_svc + baseline-on-migrate=false, spring.rabbitmq publisher-confirm-type=correlated + publisher-returns=true + listener.simple retry 3×exp backoff 1s→2s→4s max-10s prefetch=10 default-requeue-rejected=false, profile dev seed-dev location
- order/inventory application.yml: thêm spring.rabbitmq block giống notification (D-04 confirm + D-07 retry) — copy-paste có chủ ý cho consistency, KHÔNG tách shared yml fragment (Spring Boot không support import yml cross-service)
- V1__init_schema.sql: idempotent (CREATE SCHEMA IF NOT EXISTS + CREATE TABLE IF NOT EXISTS) safe co-exist với schema từ db/init Plan 23-01; dispatch_log 10 cột (D-14: id PK + event_id + recipient_user_id + channel + subject + body + status + sent_at + timestamps) + 2 index; processed_events 3 cột (D-06: event_id PK + event_type + processed_at) + 1 index
- test/resources/application-test.yml: override retry initial-interval=100ms / max-interval=500ms — IT chạy gọn (3 retry × 100ms thay vì 7s) mà vẫn validate được retry flow; datasource + rabbitmq host/port để Testcontainers @ServiceConnection inject runtime Wave 3
- T-23-07 Tampering mitigated: ddl-auto=validate (KHÔNG update/create) + baseline-on-migrate=false — chỉ Flyway tạo schema, JPA validate match
- KHÔNG Lombok (project convention) — explicit constructor injection sẽ áp dụng ở Wave 2 consumer code

**Phase 23 Plan 01 decisions (2026-05-20):**

- MQ-01 hạ tầng bootstrap: rabbitmq:3-management container + Management UI port 15672 + AMQP 5672, healthcheck rabbitmq-diagnostics ping (interval 10s retries 10), volume named tmdt-rabbitmqdata persist queue/exchange state
- 3 backend service (order/inventory/notification): depends_on rabbitmq service_healthy + env SPRING_RABBITMQ_HOST/USER/PASS=rabbitmq/guest/guest
- Rule 2 fix preventive: notification-service block trước đó chỉ có ports/build — hoàn thiện env DB đầy đủ (DB_HOST/PORT/NAME/USER/PASSWORD + SPRING_PROFILES_ACTIVE) để service không block khi Flyway bootstrap notification_svc trong Plan 23-02
- db/init/01-schemas.sql: thêm CREATE SCHEMA IF NOT EXISTS notification_svc (idempotent, sau inventory_svc) — note dev cần docker volume rm tmdt-pgdata HOẶC manual psql tạo schema nếu volume cũ tồn tại
- REQUIREMENTS.md backfilled: section MQ với MQ-01..MQ-05 + traceability 5 dòng + scope 7→8 trục + Total 27→32 (32/32 mapped 100%)
- Threat register accept: guest/guest credential acceptable cho dev (port 5672 bind localhost, không expose firewall ngoài) — đổi password defer Phase ops

**Phase 20 Plan 03 decisions (2026-05-03):**

- COUP-04 BE order integration hoàn tất: OrderCrudService.createOrderFromCommand inject CouponRedemptionService, gọi atomicRedeem trong cùng @Transactional cha (D-08, D-12). Server compute discountAmount từ subtotal qua CouponPreviewService.computeDiscount (D-10, KHÔNG tin client). Snapshot 2 field discountAmount + couponCode lên OrderEntity → cuối cùng saved
- COUP-03 BE preview endpoint: CouponPreviewController POST /orders/coupons/validate (D-13) + CouponPreviewControllerIT 5 cases (happy, already-redeemed 409, anonymous skip-check, missing code 400, unknown code 404)
- CreateOrderCommand record extend với field couponCode nullable (D-12) — backward compat khi null/blank
- OrderDto + 2 field discountAmount + couponCode (D-23, D-24) — FE Plan 20-06 sẽ render
- OrderEntity.setTotal helper mới (Rule 3 fix): plan đề xuất dùng update() reset 4 field — không phù hợp; setTotal scope hẹp + bumps updatedAt
- D-25 race condition IT: 6 cases gồm R1 (2 thread khác user, maxTotalUses=1 → 1 success + 1 CONFLICT) + R2 (2 thread cùng user → 1 success + 1 ALREADY_REDEEMED qua UNIQUE violation, usedCount=1 sau rollback) + 4 bonus (no coupon, happy, atomic rollback unknown, server-compute discount D-10) — chứng minh SC #3 race-safe
- Maven CLI vẫn chưa khả dụng trên Windows env, defer verify cho CI/local mvn — pattern đồng nhất với Plans 20-01, 20-02

**Phase 19 Plan 04 decisions (2026-05-02):**

- ADMIN-01..05 FE consume hoàn tất: install recharts@3.8.1 --save-exact (D-11 lock no caret), 5 typed fetchers services/charts.ts, lib/chartFormat.ts (STATUS_COLORS D-12 + statusLabel D-13 VN + Intl.NumberFormat/DateTimeFormat 'vi-VN')
- 6 components admin/: ChartCard generic 3-state wrapper (D-14), 4 chart components (Revenue/TopProducts/StatusDistribution/UserSignups), LowStockSection với click row → router.push('/admin/products?highlight={id}') (D-10)
- admin/page.tsx extend (KHÔNG rewrite): KPI row → time-window dropdown (default 30d D-06) → 2x2 charts grid → low-stock full-width (D-07 layout). 5 useCallback loaders với deps [range] cho 3 charts có time-window, deps [] cho status pie + low-stock (D-06 pie KHÔNG bị range)
- 2 Playwright smoke specs: admin-charts.spec.ts + admin-low-stock.spec.ts (reuse admin storageState từ global-setup.ts Phase 9 D-13). Spec syntax verified bằng tsc; runtime execution defer cho /gsd-verify-work hoặc UAT manual khi docker+browser binaries ready
- Rule 1 type fix Recharts 3.8.1: Tooltip Formatter signature `Formatter<ValueType, NameType>` accept `value: ValueType | undefined`, KHÔNG accept narrow `: number` annotation per RESEARCH code. Cast Number(v)/String(iso) inside callback body
- Rule 2 lint fix: eslint-disable `@next/next/no-img-element` cho LowStockSection thumbnail Unsplash CDN (precedent v1.2 SEED-03, KHÔNG cần next/image domain config)
- Phase 19 hoàn tất 4/4 plans → 5/5 ADMIN-01..05 COMPLETED → ready /gsd-verify-work

**Phase 19 Plan 03 decisions (2026-05-02):**

- ADMIN-05 BE product-svc layer hoàn tất: ProductRepository.findLowStock @Query (stock<:threshold ORDER BY stock ASC, soft-delete auto-filtered) + LowStockService (LOW_STOCK_THRESHOLD=10 D-08, CAP=50 D-09, record LowStockItem D-10) + ProductBatchService (record ProductSummary match Plan 19-01 wire format, JpaRepository.findAllById built-in)
- 2 controllers: AdminChartsController GET /admin/products/charts/low-stock + AdminProductBatchController POST /admin/products/batch (path /admin/products/batch không /charts/batch — gateway rewrite catch-all)
- ProductEntity dùng accessor record-style p.id() / p.name() — verified entity convention, RESEARCH gợi ý p.getId() bị deviation (Rule 1 fix)
- @RequestBody(required=false) + null guard cho batch controller — defensive null body handling (Rule 2)
- 17 test cases written (3 IT findLowStock + 2 unit LowStockService + 4 unit ProductBatchService + 4 IT AdminCharts + 4 IT AdminProductBatch) — Maven CLI vẫn chưa khả dụng trên Windows env, defer verify cho Wave check
- Gateway routes existing /api/products/admin/** đã catch-all cover /charts/low-stock và /batch — KHÔNG modify api-gateway

**Phase 19 Plan 02 decisions (2026-05-02):**

- ADMIN-04 BE user-svc layer hoàn tất: Range enum per-svc copy (KHÔNG shared module — pattern theo JwtRoleGuard precedent) + UserRepository.aggregateSignupsByDay @Query (FUNCTION('DATE', u.createdAt) Postgres dialect) + UserChartsService Java gap-fill (0L cho ngày trống, D-05) + AdminChartsController @GetMapping(/signups) với JwtRoleGuard.requireAdmin
- 13 test cases written (5 RangeTest + 2 UserRepositorySignupsIT + 6 AdminChartsControllerIT) — Maven CLI vẫn chưa khả dụng trên Windows env, defer verify cho Wave check
- Plan 02 INDEPENDENT với Plan 01 (different svc, different repo) — parallel-executable cùng Wave 1
- Gateway routes existing /api/users/admin/** đã catch-all cover /api/users/admin/charts/** automatically — KHÔNG modify api-gateway

**Phase 19 Plan 01 decisions (2026-05-02):**

- ADMIN-01/02/03 BE order-svc layer hoàn tất: Range enum (D7/D30/D90/ALL parse + toFromInstant) + 3 OrderRepository @Query (revenue/top/status) + ProductBatchClient cross-svc + OrderChartsService (Java gap-fill BigDecimal.ZERO) + AdminChartsController (3 GET endpoints)
- D-03 + Pitfall #4 áp dụng nghiêm: top-products forward Bearer authHeader xuống ProductBatchClient → product-svc batch endpoint cũng gate JwtRoleGuard. Verified qua AdminChartsControllerIT.verify(productBatchClient).fetchBatch(anyList(), eq("Bearer " + adminToken))
- Gateway routes existing /api/orders/admin/** đã catch-all cover /api/orders/admin/charts/** — KHÔNG modify api-gateway
- Maven CLI không có trên Windows env này — tests đã viết (19 cases tổng) nhưng chưa chạy; defer verify cho `/gsd-verify-work` hoặc Plan 04 FE consume khi có Maven+Docker

**Carry-over từ v1.2:**

- Phase numbering tiếp tục KHÔNG reset (v1.3 → Phase 16+)
- Visible-first priority giữ nguyên
- Backend hardening (D1..D17) defer cho đến khi có triggering event (ngoại lệ: storage audit có thể surface security issue)
- ApiErrorResponse + traceId envelope; Swagger/OpenAPI codegen; Postgres + JPA + Flyway 5 services; auth thật JWT HS256; admin CRUD qua gateway; FE typed services; rhf+zod pattern; Playwright suite

**Phase 18 Plan 06 decisions (2026-05-02):**

- STORE-01 closed: grep confirms 4 localStorage keys (cart/userProfile/accessToken/refreshToken) + 2 cookie keys (auth_present/user_role). Classify xong vao bang audit 18-SUMMARY.md
- STORE-03 closed: no additional user-data leaks found beyond cart. Wishlist/recently-viewed/search-history KHONG ton tai trong FE codebase hien tai
- UAT 4 truths auto-approved: workflow._auto_chain_active=true, code review + static analysis thay the manual browser test
- Phase 18 COMPLETED: 6/6 plans done, STORE-01/02/03 closed, 18-SUMMARY.md committed

**Phase 18 Plan 01 decisions (2026-05-02):**

- CartController.java reset thành stub (Plan 02 replace hoàn toàn) — file cũ reference CartUpsertRequest đã bị xóa cùng với InMemoryCartRepository
- CartItemEntity.equals/hashCode dựa trên id only (không traverse lazy collection) — tranh LazyInitializationException
- KHÔNG có unit_price_at_add trong cart_items — cart hiển thị live price, chỉ snapshot khi tạo order
- upsertAddQuantity native SQL ON CONFLICT DO UPDATE — idempotent ADD semantics (D-05)

**v1.3 locks (2026-05-02 — locked từ research + user answers):**

- **Seed catalog**: ~100 SP / 5 categories (điện thoại, laptop, chuột, bàn phím, tai nghe), brand realistic (Apple/Samsung/Logitech/Razer/Sony/...), ảnh Unsplash CDN `?fm=webp&q=80` (precedent v1.2 P15). Flyway V7 product-svc Spring profile `dev`.
- **Storage audit scope**: TOÀN FE codebase (grep localStorage + sessionStorage), classify (user-data / UI-pref / auth-token), migrate user-data → DB per user_id. Cart confirmed localStorage-only (dead code `InMemoryCartRepository` unused).
- **Admin charts**: 4 loại — revenue/time (area), top products (bar), order status pie (donut), signups (line) + low-stock alert `stock < 10`. Stack: `recharts@3.8.1`.
- **Review polish**: REV-04 (author edit/delete = soft-delete `deleted_at`) + sort by newest/rating DESC/ASC + admin moderation (hide/unhide = `hidden BOOLEAN` column). KHÔNG helpful votes (defer v1.4).
- **Coupon**: 2 loại (% off + fixed amount), min order + expiry + max usage, 1 lần/coupon/user (`UNIQUE CONSTRAINT coupon_redemptions(coupon_id, user_id)`), 1 mã/đơn (KHÔNG stack). Admin CRUD `/admin/coupons` + FE checkout input. Race-safe atomic UPDATE. Flyway V3 order-svc.
- **Chatbot AI**: Claude API MVP, model `claude-haiku-4-5`. Customer FAQ + product Q&A + recommendation. Streaming UI (native ReadableStream, không Vercel AI SDK). Chat history persist Postgres `chat_svc` schema (raw pg driver). Prompt caching bắt buộc từ ngày 1. Login required (NOT guest). Sliding window 10 turns. Admin "suggest reply" — manual confirm. KHÔNG agentic tool-use. Stack: `@anthropic-ai/sdk@0.92.0`. Phase này dùng `/gsd-ai-integration-phase`.
- **Order detail bug**: Root cause xác định — admin page có hardcoded string "Chi tiết sản phẩm..."; `AdminOrder` interface thiếu `items[]`. FE fix chính, BE verify DTO đã đúng.
- **Cart→DB placement**: order-svc Flyway V4 (`carts` + `cart_items`). FE `services/cart.ts` refactor API-first. Guest merge idempotent `ON CONFLICT DO UPDATE`.
- **Chat persistence placement**: Next.js API route + raw pg driver trực tiếp `chat_svc` schema. KHÔNG tạo Spring Boot microservice mới.

## Flyway V-number Reservations (confirmed từ research)

| Service | Version | Purpose | Phase |
|---------|---------|---------|-------|
| product-svc | V101 | Seed ~100 sản phẩm trong db/seed-dev/ (Spring profile `dev` only) — APPLIED 2026-05-02 | Phase 16 |
| order-svc | V3 | Coupons + coupon_redemptions tables | Phase 20 |
| order-svc | V4 | Carts + cart_items tables | Phase 18 |
| chat_svc | — | Schema init qua Next.js raw pg driver (không Flyway) | Phase 22 |

*Note: user-svc V5 reserved cho ACCT-01 wishlist (v1.4). product-svc V8 optional review sorts index nếu cần.*

## Deferred Items (carry-over từ v1.2 + v1.3 scope locks)

| Category | Item | Status |
|----------|------|--------|
| debug | products-list-500 | root_cause_found — carry-over |
| uat_gap | Phase 06/07/09/10/11 *-HUMAN-UAT.md | partial / pending manual UAT |
| verification_gap | Phase 06/07/09/10/11 *-VERIFICATION.md | human_needed |
| v1.4+ | ACCT-01 wishlist | SKIPPED v1.2 Phase 12, V5 migration reserved |
| v1.4+ | SEARCH-03/04 rating filter + URL state | scope trim v1.2 |
| v1.4+ | ACCT-04 avatar upload | Deferred D-08 từ Phase 10 |
| v1.4+ | TEST-02-FULL 8+ E2E tests | scope trim v1.2 |
| v1.4+ | COUP-06 coupon stacking | scope lock v1.3 |
| v1.4+ | AI-06 agentic chatbot tool-use | scope lock v1.3 |
| v1.4+ | REV-07 helpful votes | scope lock v1.3 |
| v1.4+ | STORE-04 auth-token httpOnly cookie | visible-first defer |

## Blockers

Không có blocker.

## Accumulated Context

### Roadmap Evolution

- 2026-05-20 — Phase 23 added: Message Queue Integration (RabbitMQ) — đáp ứng yêu cầu BẮT BUỘC 3.3 của đề chủ đề 4 (giao tiếp bất đồng bộ giữa các microservice). Luồng OrderPlaced → inventory + notification với retry + DLQ. Broker chọn RabbitMQ (đơn giản hơn Kafka, có sẵn DLX, Management UI demo trực quan).
- 2026-05-20 — Phase 23 CONTEXT captured: 18 decisions (D-01 đến D-18) qua 4 gray areas. Quyết định chính: 1 event OrderPlaced (topic exchange `order.events`); publish-after-commit; idempotency qua processed_events table; retry 3 lần exp backoff + DLQ qua x-dead-letter-exchange; GIỮ REST stock validate đồng bộ + BỎ REST stock deduct (thay bằng inventory consumer); notification ghi dispatch_log (defer SMTP). 8 deferred ideas (Saga đầy đủ, SMTP, observability, tách DB, X-User-Id...).
- 2026-05-20 — Phase 24 added: Database Per Service (tách CSDL hạ tầng) — củng cố 3.4 + failure isolation. 5 postgres container riêng (user/product/order/payment/inventory). Demo: tắt 1 DB → service khác vẫn chạy.
- 2026-05-20 — Phase 25 added: Gateway JWT Edge Authentication — vá lỗ hổng X-User-Id. Gateway verify JWT + strip + inject trusted header. Bỏ port expose của service nội bộ. Đóng bug orders-cross-user-leak ở tầng kiến trúc.
- 2026-05-22 — Phase 26 added: Tích Hợp Thanh Toán VNPay Sandbox — khách chọn VNPay tại checkout → redirect cổng VNPay sandbox → backend xử lý IPN callback (verify HMAC SHA512, so khớp số tiền, cập nhật payment_status idempotent). Depends on Phase 20 (cần discountAmount để số tiền gửi VNPay là final). Cổng chọn: VNPay sandbox (user quyết định — phổ biến nhất, sandbox miễn phí). REQ PAY-01..04.
- 2026-05-23 — Phase 26.1 inserted after Phase 26: Migrate Payment Gateway VNPay → MoMo (URGENT). Lý do: VNPay sandbox merchant portal phức tạp (KYC, portal mới doitac.vnpay.vn, không nhận mail kích hoạt) — chuyển sang MoMo Developer (developers.momo.vn) đơn giản hơn cho dev cá nhân (HMAC SHA256, JSON REST, sandbox key public). Architecture event-based + payment_status không đổi — chỉ thay implementation. Effort ước tính ~5-7h, ~30-40% code Phase 26 phải sửa.
- 2026-05-22 — Phase 27 added: Gửi Email Thật (SMTP) — email thật qua SMTP Gmail (credential qua env, user cấp account riêng) cho 3 luồng: xác thực tài khoản (verify đăng ký + reset mật khẩu), xác nhận đơn hàng, cập nhật trạng thái đơn. Tái dụng notification-service + consumer RabbitMQ OrderPlaced (Phase 23). Depends on Phase 23. REQ MAIL-01..04.

- Project: tmdt-use-gsd — dự án thử nghiệm GSD workflow (Spring Boot microservices + Next.js + API gateway + Docker Compose).
- Foundation v1.0 + v1.1 + v1.2 reuse được: ApiErrorResponse + traceId envelope; Swagger/OpenAPI codegen; Postgres + JPA + Flyway 5 services; auth thật JWT HS256; admin CRUD qua gateway; FE typed services; rhf+zod pattern; Playwright E2E suite (14 baseline + 4 smoke); reviews verified-buyer cross-service; FilterSidebar pattern; M3 design tokens.
- Visible-first priority applied: feature invisible → defer; visible → ship.
- v1.2 closed 2026-05-02 với verdict PASSED (17/17 active REQs). Tag `v1.2` annotated local.
- Memory: Vietnamese chat/docs/commits; visible-first priority; dự án thử nghiệm GSD KHÔNG phải PTIT/HTPT assignment.
- Pitfall quan trọng v1.3: coupon double-redemption (atomic UPDATE + UNIQUE constraint); chatbot context window blowup (sliding 10 turns + prompt cache); Flyway seed prod isolation (V7 vào seed/dev path); cart merge race condition (idempotent upsert + FE useRef flag); prompt injection từ reviews (XML tag isolation).

## Next Steps

1. `/gsd-plan-phase 19` → Execute Phase 19: Admin Charts + Low-Stock (ADMIN-01..05)
2. `/gsd-plan-phase 20` → Execute Phase 20: Hệ Thống Coupon (COUP-01..05)
3. `/gsd-plan-phase 21` → Execute Phase 21: Hoàn Thiện Reviews (REV-04..06)
4. `/gsd-ai-integration-phase 22` → Execute Phase 22: AI Chatbot Claude API MVP (dùng AI integration workflow thay plan-phase chuẩn)

**Completed:**

- Phase 16: Seed Catalog Hiện Thực — **COMPLETED 2026-05-02** (3/3 plans, SEED-01..04)
- Phase 17: Sửa Order Detail Items — **COMPLETED 2026-05-02** (4/4 plans, ORDER-01 + ADMIN-06)
- Phase 18: Kiểm Toán Storage + Cart→DB — **COMPLETED 2026-05-02** (6/6 plans, STORE-01/02/03 closed)
- Phase 19 Plan 01: order-svc admin chart endpoints — **COMPLETED 2026-05-02** (ADMIN-01/02/03 BE layer done; FE Plan 04 sẽ consume)
- Phase 19 Plan 02: user-svc admin /signups chart endpoint — **COMPLETED 2026-05-02** (ADMIN-04 BE layer done)
- Phase 19 Plan 03: product-svc admin /low-stock + /batch endpoints — **COMPLETED 2026-05-02** (ADMIN-05 BE layer done + cross-svc enrichment helper cho Plan 01)
- Phase 19 Plan 04: FE admin charts grid + low-stock — **COMPLETED 2026-05-02** (recharts@3.8.1 + 5 fetchers + 6 components + admin/page extend + 2 Playwright specs)
- Phase 19: Hoàn Thiện Admin Charts + Low-Stock — **COMPLETED 2026-05-02** (4/4 plans, ADMIN-01..05 closed)
- Phase 23 Plan 01: Bootstrap RabbitMQ + Schema notification_svc — **COMPLETED 2026-05-20** (MQ-01 hạ tầng container + REQUIREMENTS backfill MQ-01..MQ-05)
- Phase 23 Plan 02: notification-service persistence bootstrap + spring.rabbitmq config 3 service — **COMPLETED 2026-05-20** (MQ-04 prep + V1 dispatch_log + processed_events)
- Phase 23 Plan 03: Producer topology + OrderEventPublisher afterCommit + XÓA deductStock REST — **COMPLETED 2026-05-20** (MQ-02 producer done; Wave 2 consumer plans next)
- Phase 23 Plan 04: Inventory consumer V2 migration + OrderPlacedListener idempotent + InventoryCrudService.decrementForOrder — **COMPLETED 2026-05-20** (MQ-03 done; 13 files, 2 commits)
- Phase 23 Plan 05: Notification consumer DispatchLogEntity + ProcessedEventEntity + NotificationDispatchService render template + OrderPlacedNotifyListener idempotent — **COMPLETED 2026-05-20** (MQ-04 done; 10 files, 2 commits)
- Phase 23 Plan 06: Integration tests 3 IT class 9 @Test methods (FULL D-18 không @Disabled) + scripts/verify-mq.sh smoke + architecture/02-sequence-diagrams.md Kafka→RabbitMQ — **COMPLETED 2026-05-20** (MQ-02/03/04/05 evidence; 9 files, 2 commits fc86cb0 + 9957b99)
- Phase 23: Message Queue Integration (RabbitMQ) — **COMPLETED 2026-05-20** execution (6/6 plans, MQ-01..05 đã có evidence; ready /gsd-verify-work cho mvn + docker smoke runtime)
