---
phase: 26-vnpay-payment-integration
plan: "02"
subsystem: payments
tags: [flyway, migration, jpa, rabbitmq, idempotency, vnpay, order-service]

requires:
  - phase: 20-coupon-system
    provides: "Cột coupon_code/discount_amount + OrderEntity/OrderDto/OrderMapper pattern đã sử dụng"
  - phase: 23-rabbitmq
    provides: "ProcessedEventEntity/Repository idempotency pattern từ inventory-service"

provides:
  - "Flyway V6 migration: cột payment_status (PENDING) + vnp_transaction_no + bảng processed_events cho order-service DB"
  - "OrderEntity mở rộng: 2 field payment_status/vnp_transaction_no với getters + setters (bumps updatedAt)"
  - "OrderDto: 3 field mới paymentStatus, vnpTransactionNo, paymentUrl (transient nullable)"
  - "OrderMapper: ánh xạ 2 entity field mới; paymentUrl = null (Plan 03 set)"
  - "ProcessedEventEntity + ProcessedEventRepository (ON CONFLICT DO NOTHING) port từ inventory-service"
  - "TraceIdConsumerInterceptor port từ inventory-service với package orderservice"

affects:
  - 26-03-consumer-and-order-crud
  - 26-04-frontend-checkout

tech-stack:
  added: []
  patterns:
    - "Flyway ALTER TABLE IF NOT EXISTS — backward compat với order cũ (default PENDING)"
    - "JPA field pattern: default String field + setter bumps updatedAt (pattern setTotal)"
    - "OrderDto record extend: thêm field nullable + transient (paymentUrl set thủ công sau)"
    - "Idempotency infra: ProcessedEventRepository.insertIfAbsent ON CONFLICT DO NOTHING"

key-files:
  created:
    - "sources/backend/order-service/src/main/resources/db/migration/V6__add_payment_status.sql"
    - "sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/ProcessedEventEntity.java"
    - "sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/repository/ProcessedEventRepository.java"
    - "sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/messaging/tracing/TraceIdConsumerInterceptor.java"
  modified:
    - "sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/OrderEntity.java"
    - "sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/OrderDto.java"
    - "sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/OrderMapper.java"

key-decisions:
  - "paymentUrl là transient field trong OrderDto — KHÔNG map từ entity, Plan 03 set thủ công trong nhánh VNPAY"
  - "setter setPaymentStatus/setVnpTransactionNo bumps updatedAt — theo pattern setTotal Phase 20"
  - "processed_events gộp vào V6 migration cùng cột payment (không tạo V7 riêng)"
  - "COD orders cũ nhận payment_status=PENDING mặc định — không backfill theo order.status (planner chốt)"

patterns-established:
  - "ProcessedEventRepository.insertIfAbsent: native query ON CONFLICT (event_id) DO NOTHING trả boolean"
  - "TraceIdConsumerInterceptor.enter/exit: set/remove MDC traceId từ AMQP header X-Trace-Id"

requirements-completed: [PAY-04]

duration: 20min
completed: 2026-05-22
---

# Phase 26 Plan 02: VNPay Payment Data Foundation Summary

**Flyway V6 migration thêm cột payment_status/vnp_transaction_no + bảng processed_events vào order-service DB; OrderEntity/OrderDto/OrderMapper mở rộng; 3 file idempotency infra port từ inventory-service sẵn sàng cho consumer Plan 03**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-05-22T00:00:00Z
- **Completed:** 2026-05-22T00:20:00Z
- **Tasks:** 2/2
- **Files modified:** 7 (4 tạo mới, 3 sửa)

## Accomplishments

- Flyway V6 migration tạo 2 cột mới cho orders + bảng processed_events; backward compat với COD orders cũ (DEFAULT 'PENDING')
- OrderEntity/OrderDto/OrderMapper mở rộng đầy đủ cho VNPay: paymentStatus + vnpTransactionNo + paymentUrl (transient)
- Idempotency consumer infra (ProcessedEventEntity/Repository + TraceIdConsumerInterceptor) port từ inventory-service — sẵn cho PaymentEventListener Plan 03

## Task Commits

1. **Task 1: Flyway V6 migration** — `5ba37cc` (feat)
2. **Task 2: OrderEntity/OrderDto/OrderMapper + idempotency infra** — `7ab17e3` (feat)

**Plan metadata:** (xem commit docs bên dưới)

## Files Created/Modified

- `V6__add_payment_status.sql` — ALTER TABLE orders (2 cột) + CREATE TABLE processed_events
- `OrderEntity.java` — 2 field + 2 getter + 2 setter (bumps updatedAt)
- `OrderDto.java` — 3 field mới: paymentStatus, vnpTransactionNo, paymentUrl (nullable transient)
- `OrderMapper.java` — map paymentStatus/vnpTransactionNo từ entity; paymentUrl = null
- `ProcessedEventEntity.java` — port inventory-service, package orderservice
- `ProcessedEventRepository.java` — insertIfAbsent ON CONFLICT DO NOTHING
- `TraceIdConsumerInterceptor.java` — port inventory-service, package orderservice

## Decisions Made

- `paymentUrl` là transient field trong OrderDto, không có column trong DB — set thủ công trong OrderCrudService nhánh VNPAY (Plan 03). Plan 03 sẽ dùng field này để trả paymentUrl cho FE sau khi gọi payment-service.
- Setter setPaymentStatus/setVnpTransactionNo bump updatedAt theo pattern setTotal Phase 20.
- Migration V6 gộp cả cột payment + bảng processed_events vào 1 file (không tạo V7 riêng).

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

Maven không có trong PATH của môi trường bash. Không thể chạy `mvn compile -pl order-service` trực tiếp. Đã verify compile correctness bằng code review: (1) record constructor OrderDto chỉ có 1 caller (OrderMapper.java) đã cập nhật đúng; (2) không có test nào construct OrderDto trực tiếp; (3) OrderCrudService dùng OrderMapper.toDto() — tương thích.

## Known Stubs

- `paymentUrl` trong OrderDto: luôn null hiện tại — Plan 03 (OrderCrudService nhánh VNPAY) sẽ set sau khi gọi PaymentSessionClient. Đây là intentional transient field theo D-04.

## Next Phase Readiness

- Plan 03 có thể consume: ProcessedEventRepository.insertIfAbsent sẵn sàng cho PaymentEventListener
- Plan 03 có thể mở rộng OrderCrudService: paymentStatus/setPaymentStatus sẵn trên entity
- Plan 03 có thể set paymentUrl: OrderDto.paymentUrl field tồn tại, OrderMapper trả null (Plan 03 override sau mapper)
- V6 migration sẽ chạy khi order-service khởi động trong test/prod environment

---
*Phase: 26-vnpay-payment-integration*
*Completed: 2026-05-22*
