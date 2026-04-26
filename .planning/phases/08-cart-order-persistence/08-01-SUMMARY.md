---
phase: 08-cart-order-persistence
plan: "01"
subsystem: product-service
tags: [flyway, jpa, stock, persistence, backend]
dependency_graph:
  requires: []
  provides: [ProductEntity.stock, V3-migration, ProductUpsertRequest.stock]
  affects: [08-02-order-stock-validation, 08-04-fe-product-detail]
tech_stack:
  added: []
  patterns: [Flyway-migration-idempotent, JPA-accessor-style, Math.max-guard]
key_files:
  created:
    - sources/backend/product-service/src/main/resources/db/migration/V3__add_product_stock.sql
  modified:
    - sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/domain/ProductEntity.java
    - sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/service/ProductCrudService.java
decisions:
  - "setStock() dùng Math.max(0, stock) để chặn giá trị âm — threat T-08-01-01 mitigation"
  - "update() nhận int stock làm param cuối — giữ nguyên factory create() signature để không phá call sites cũ"
  - "createProduct() set stock sau create() qua setStock() — không thay đổi protected constructor"
metrics:
  duration: "12m"
  completed: "2026-04-26T13:42:31Z"
  tasks_completed: 3
  tasks_total: 3
  files_changed: 3
---

# Phase 8 Plan 01: Stock Persistence Foundation Summary

**One-liner:** Flyway V3 migration + JPA entity field + service wire để ProductEntity.stock persist thật từ DB thay vì hardcode 0.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Flyway V3 migration — column stock + seed defaults | `1f67b94` | V3__add_product_stock.sql (created) |
| 2 | ProductEntity — field stock + getter + setStock | `d57ce74` | ProductEntity.java |
| 3 | ProductCrudService — wire stock thật + request field | `cee1cba` | ProductCrudService.java |

## What Was Built

### V3 migration (Task 1)
`V3__add_product_stock.sql` thêm column `stock INT NOT NULL DEFAULT 0` vào bảng `product_svc.products` qua `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` (idempotent). Sau đó seed `stock = 50` cho toàn bộ 10 products Phase 5 đang active (`deleted = false`).

### ProductEntity (Task 2)
- Field `private int stock = 0` với `@Column(nullable = false)` — map đúng sang DB column
- `stock()` getter — accessor style nhất quán với codebase
- `setStock(int)` — dùng `Math.max(0, stock)` để chặn giá trị âm (threat T-08-01-01)
- `update(... int stock)` — thêm param cuối để persist stock khi admin update product

### ProductCrudService (Task 3)
- `toResponse()` line 181: `product.stock()` thay vì hardcode `0`
- `ProductUpsertRequest` record: thêm `@Min(0) int stock` field cuối
- `createProduct()`: gọi `product.setStock(request.stock())` trước `productRepo.save()`
- `updateProduct()`: truyền `request.stock()` vào `current.update()` param cuối
- Import `jakarta.validation.constraints.Min` thêm cho `@Min` annotation

## Verification

```
V3__add_product_stock.sql: ALTER TABLE product_svc.products ADD COLUMN IF NOT EXISTS stock INT NOT NULL DEFAULT 0  ✓
ProductEntity.java: private int stock = 0  ✓
ProductEntity.java: public int stock()  ✓
ProductEntity.java: public void setStock(int stock)  ✓
ProductEntity.java: update(... int stock)  ✓
ProductCrudService.java: product.stock()  ✓
ProductCrudService.java: @Min(0) int stock  ✓
ProductCrudService.java: product.setStock(request.stock())  ✓
ProductCrudService.java: request.stock() (updateProduct)  ✓
```

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None — stock field is fully wired end-to-end from DB to API response.

## Threat Flags

None — T-08-01-01 mitigated via `@Min(0)` on request + `Math.max(0, stock)` in setStock. T-08-01-02 accepted (public info). T-08-01-03 accepted (MVP scope, no new surface introduced).

## Self-Check: PASSED

- FOUND: V3__add_product_stock.sql
- FOUND: ProductEntity.java
- FOUND: ProductCrudService.java
- FOUND: commit 1f67b94 (Task 1)
- FOUND: commit d57ce74 (Task 2)
- FOUND: commit cee1cba (Task 3)
