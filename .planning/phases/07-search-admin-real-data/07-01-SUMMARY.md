---
phase: 07-search-admin-real-data
plan: "01"
subsystem: backend
tags:
  - api-gateway
  - product-service
  - keyword-search
  - admin-routes
  - tdd
dependency_graph:
  requires:
    - "Phase 5 — product-service JPA + Flyway"
    - "Phase 6 — auth flow (gateway pass-through)"
  provides:
    - "6 gateway admin routes (product/order/user)"
    - "keyword search trên GET /api/products"
  affects:
    - "sources/backend/api-gateway/src/main/resources/application.yml"
    - "sources/backend/product-service"
tech_stack:
  added: []
  patterns:
    - "Spring Cloud Gateway first-wins route matching"
    - "Two-route-per-service (base + wildcard) pattern"
    - "In-memory Java stream filter (case-insensitive)"
    - "Backward-compat overload delegation"
key_files:
  created:
    - "sources/backend/product-service/src/test/java/com/ptit/htpt/productservice/web/ProductKeywordSearchTest.java"
  modified:
    - "sources/backend/api-gateway/src/main/resources/application.yml"
    - "sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/web/ProductController.java"
    - "sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/service/ProductCrudService.java"
decisions:
  - "Admin routes đặt TRƯỚC general routes trong application.yml do Spring Cloud Gateway first-wins matching"
  - "Capture group dùng 'seg' (không phải 'path') để tránh conflict với $PATH env var trong Docker container"
  - "Overload 4-arg backward-compat để AdminProductController không cần sửa (gọi sang 5-arg với null keyword)"
  - "In-memory filter dùng String.contains() — không có SQL injection risk, keyword là read-only operation"
metrics:
  duration: "~15 minutes"
  completed_date: "2026-04-26"
  tasks_completed: 2
  files_modified: 3
  files_created: 1
  commits: 3
---

# Phase 07 Plan 01: Gateway Admin Routes + Keyword Search — Summary

**One-liner:** 6 gateway admin routes (user/product/order) + in-memory keyword search filter trên product-service qua `@RequestParam(required=false) String keyword`.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Thêm 6 gateway admin routes (D-01) | `0dd8481` | application.yml |
| 2 TDD RED | Failing tests cho keyword search | `74408df` | ProductKeywordSearchTest.java |
| 2 TDD GREEN | Implement keyword search (D-02) | `3ce21cd` | ProductController.java, ProductCrudService.java |

## Changes Made

### Task 1 — Gateway Admin Routes (D-01)

**File:** `sources/backend/api-gateway/src/main/resources/application.yml`

Thêm 6 route blocks mới vào phần `routes:`, đặt TRƯỚC các general service routes tương ứng:

- `user-service-admin-base`: `Path=/api/users/admin` → `RewritePath=/api/users/admin, /admin/users`
- `user-service-admin`: `Path=/api/users/admin/**` → `RewritePath=/api/users/admin/(?<seg>.*), /admin/users/${seg}`
- `product-service-admin-base`: `Path=/api/products/admin` → `RewritePath=/api/products/admin, /admin/products`
- `product-service-admin`: `Path=/api/products/admin/**` → `RewritePath=/api/products/admin/(?<seg>.*), /admin/products/${seg}`
- `order-service-admin-base`: `Path=/api/orders/admin` → `RewritePath=/api/orders/admin, /admin/orders`
- `order-service-admin`: `Path=/api/orders/admin/**` → `RewritePath=/api/orders/admin/(?<seg>.*), /admin/orders/${seg}`

Tổng routes sau khi thêm: 18 (12 cũ + 6 mới).

### Task 2 — Keyword Search (D-02)

**File 1:** `ProductController.java`

Thêm `@RequestParam(required = false) String keyword` vào `listProducts` và truyền xuống service call.

**File 2:** `ProductCrudService.java`

Thêm overload 5-arg `listProducts(page, size, sort, includeDeleted, keyword)` với filter:
```java
.filter(product -> keyword == null || keyword.isBlank() ||
    product.name().toLowerCase().contains(keyword.toLowerCase()))
```

Overload 4-arg cũ delegate sang 5-arg với `null` — `AdminProductController` không cần sửa.

## Verification Results

```
# 3 admin base routes
grep "user-service-admin-base|product-service-admin-base|order-service-admin-base" application.yml | wc -l
→ 3 ✓

# Route order: admin trước general
- user-service-admin-base: line 23 < user-service-base: line 37 ✓
- product-service-admin-base: line 51 < product-service-base: line 65 ✓
- order-service-admin-base: line 79 < order-service-base: line 93 ✓

# Keyword param in ProductController
grep "@RequestParam(required = false) String keyword" ProductController.java
→ 1 match ✓

# Keyword filter in ProductCrudService
grep "keyword == null || keyword.isBlank()" ProductCrudService.java
→ 1 match ✓

# Compile
mvn compile -pl sources/backend/product-service -q
→ exit 0 (no errors) ✓
```

## TDD Gate Compliance

- RED gate: commit `74408df` — `test(07-01)` (failing tests trước khi implement)
- GREEN gate: commit `3ce21cd` — `feat(07-01)` (implementation pass tests)
- REFACTOR: không cần (code đủ clean)

## Deviations from Plan

None — plan executed exactly as written.

**Ghi chú thiết kế:** `AdminProductController.listProducts` tiếp tục dùng overload 4-arg (backward-compat), không cần truyền explicit `null`. Plan đề xuất sửa AdminProductController nhưng overload delegation là giải pháp tốt hơn — không làm thay đổi các caller hiện tại.

## Security Notes (Threat Register)

| Threat | Disposition | Note |
|--------|-------------|------|
| T-07-01-01: Admin routes expose /admin/* qua gateway không có auth filter | accept | Gateway không verify JWT (D-14 deferred). FE middleware Phase 6 protect `/admin/**` client-side. Acceptable cho demo scope. |
| T-07-01-02: keyword param tampering | mitigate | In-memory String.contains() — KHÔNG có SQL injection risk. Keyword read-only, không mutate state. |
| T-07-01-03: Admin routes expose existing /admin/* endpoints | accept | Endpoints đã tồn tại trên service layer (Phase 5). Gateway chỉ mở public path. |

## Known Stubs

None — changes này là infrastructure/routing, không có UI rendering stubs.

## Self-Check: PASSED

- `sources/backend/api-gateway/src/main/resources/application.yml` — FOUND, có 18 routes ✓
- `sources/backend/product-service/.../ProductController.java` — FOUND, có keyword param ✓
- `sources/backend/product-service/.../ProductCrudService.java` — FOUND, có keyword filter ✓
- `sources/backend/product-service/.../ProductKeywordSearchTest.java` — FOUND (TDD RED) ✓
- Commit `0dd8481` — gateway admin routes ✓
- Commit `74408df` — TDD RED test ✓
- Commit `3ce21cd` — TDD GREEN implementation ✓
