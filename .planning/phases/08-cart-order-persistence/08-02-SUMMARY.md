---
phase: 08-cart-order-persistence
plan: "02"
subsystem: order-service
tags: [persistence, jpa, flyway, order-items, stock-validate, stock-deduct]
dependency_graph:
  requires: [08-01-SUMMARY]
  provides: [order-items-schema, OrderItemEntity, OrderItemRepository, createOrderFromCommand-full, stock-validate-D04, stock-deduct-D05]
  affects: [order-service, frontend-order-detail]
tech_stack:
  added: [RestTemplate, StockShortageException, OrderItemRepository, Flyway V2 migration]
  patterns: [JPA @OneToMany cascade ALL orphanRemoval, @JdbcTypeCode(JSON) JSONB mapping, best-effort stock validate/deduct via RestTemplate]
key_files:
  created:
    - sources/backend/order-service/src/main/resources/db/migration/V2__add_order_items.sql
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/OrderItemEntity.java
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/repository/OrderItemRepository.java
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/OrderItemDto.java
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/AppConfig.java
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/exception/StockShortageException.java
  modified:
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/OrderEntity.java
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/OrderDto.java
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/OrderMapper.java
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderCrudService.java
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/api/GlobalExceptionHandler.java
decisions:
  - "D-04 best-effort: nếu product-service timeout/unavailable khi validate stock → log.warn + bỏ qua item (không block order). MVP acceptable."
  - "D-05 no-rollback: nếu PATCH stock deduct fail sau order persist → log.error, KHÔNG rollback order. User confirmed MVP acceptable."
  - "productName snapshot từ FE cart state (D-06): không verify với product-service — lưu as-is đủ cho order history display."
  - "shippingAddress lưu JSONB nullable (orders cũ từ Phase 5 seed không có địa chỉ)."
  - "buildStockUpdateBody() copy toàn bộ GET response fields + override stock → tránh null-out name/price khi PATCH ProductUpsertRequest."
metrics:
  duration_seconds: 263
  completed_date: "2026-04-26"
  tasks_completed: 4
  files_changed: 11
---

# Phase 8 Plan 02: Order Items Persistence + Stock Validate/Deduct — Summary

**One-liner:** Flyway V2 migration + OrderItemEntity @OneToMany cascade + per-item persist với productName snapshot + D-04 stock validate (409 STOCK_SHORTAGE) + D-05 stock deduct (PATCH product-service).

## Completed Tasks

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Flyway V2 migration | `2abe9b1` | V2__add_order_items.sql |
| 2 | OrderItemEntity + Repository + mở rộng OrderEntity | `fdb9b20` | OrderItemEntity.java, OrderItemRepository.java, OrderEntity.java |
| 3 | OrderItemDto + OrderDto + OrderMapper + AppConfig + createOrderFromCommand | `08500fd` | OrderItemDto.java, OrderDto.java, OrderMapper.java, AppConfig.java, OrderCrudService.java |
| 4 | StockShortageException + GlobalExceptionHandler + D-04/D-05 | `d4ab3d5` | StockShortageException.java, GlobalExceptionHandler.java, OrderCrudService.java |

## What Was Built

### Flyway V2 Migration (Task 1)
- Tạo bảng `order_svc.order_items` với các cột: id, order_id, product_id, product_name (VARCHAR 300), quantity, unit_price, line_total
- Thêm index `idx_order_items_order_id` trên `order_items.order_id`
- Thêm cột `shipping_address JSONB` vào `orders` table (D-08)
- Thêm cột `payment_method VARCHAR(30)` vào `orders` table (D-09)

### JPA Entities (Task 2)
- `OrderItemEntity`: @Entity @ManyToOne(LAZY) → OrderEntity, factory method `create()`, accessor naming style
- `OrderItemRepository`: extends JpaRepository<OrderItemEntity, String>
- `OrderEntity` mở rộng: @OneToMany(cascade ALL, orphanRemoval, LAZY) items, @JdbcTypeCode(JSON) shippingAddress, paymentMethod; thêm `addItem()`, `setShippingAddress()`, `setPaymentMethod()`

### DTO + Mapper + AppConfig (Task 3)
- `OrderItemDto` record: id, productId, productName, quantity, unitPrice, lineTotal
- `OrderDto` mở rộng: thêm `List<OrderItemDto> items`, `Map<String,Object> shippingAddress`, `String paymentMethod` (giữ `totalAmount` alias)
- `OrderMapper.toDto()`: map items stream + parse shippingAddress JSON → Map (với fallback emptyMap nếu malformed)
- `AppConfig.java`: @Bean RestTemplate tại package root
- `OrderItemRequest` thêm `@NotBlank String productName` (D-06 snapshot)
- `createOrderFromCommand()` mới: serialize shippingAddress → JSON, tạo OrderItemEntity per item với productName thật, cascade save

### D-04/D-05 + Exception Handler (Task 4)
- `StockShortageException`: RuntimeException với `List<StockShortageItem>` (productId, productName, requested, available)
- `GlobalExceptionHandler.handleStockShortage()`: 409 CONFLICT với body `{code:"CONFLICT", domainCode:"STOCK_SHORTAGE", items:[...]}`
- `validateStockOrThrow()`: GET stock từ api-gateway:8080 per item → collect shortages → throw StockShortageException
- `deductStockAfterPersist()`: GET current stock → PATCH /api/products/admin/{id} với newStock = max(0, current - qty)
- `buildStockUpdateBody()`: copy GET response fields + override stock, remove read-only fields (id, createdAt, updatedAt, deleted)
- `createOrderFromCommand()` final order: userId check → validateStock (D-04) → totalAmount → shippingAddress JSON → OrderEntity → addItems → save → deductStock (D-05) → return dto

## Deviations from Plan

Không có deviation — plan executed exactly as written.

## Known Stubs

Không có stubs. Tất cả data flows thật từ FE request body đến DB.

## Threat Flags

Không phát hiện threat surface mới ngoài threat model đã định nghĩa trong plan.

## Self-Check: PASSED

- `V2__add_order_items.sql` — tồn tại ✓
- `OrderItemEntity.java` — tồn tại ✓
- `OrderItemRepository.java` — tồn tại ✓
- `OrderDto.java` — có `List<OrderItemDto> items` ✓
- `OrderMapper.java` — map items + shippingAddress ✓
- `AppConfig.java` — @Bean RestTemplate ✓
- `StockShortageException.java` — tồn tại ✓
- `GlobalExceptionHandler.java` — có STOCK_SHORTAGE handler ✓
- `OrderCrudService.java` — có validateStockOrThrow + deductStockAfterPersist + wire vào createOrderFromCommand ✓
- Commits: `2abe9b1`, `fdb9b20`, `08500fd`, `d4ab3d5` — tất cả tồn tại ✓
