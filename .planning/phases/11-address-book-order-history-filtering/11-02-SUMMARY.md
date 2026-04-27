---
phase: 11-address-book-order-history-filtering
plan: "02"
subsystem: order-service-backend
tags: [order-filter, server-side-filtering, timezone, jpql, acct-02]
dependency_graph:
  requires: []
  provides: [listMyOrders-api, order-filter-backend]
  affects: [frontend-orders-page]
tech_stack:
  added: []
  patterns: [JPQL-@Query-with-@Param, ZoneOffset-UTC+7, inner-record-command-pattern]
key_files:
  created: []
  modified:
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/repository/OrderRepository.java
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderCrudService.java
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/OrderController.java
decisions:
  - "StatusFilter null khi 'ALL' — repository bỏ qua filter status, trả tất cả orders"
  - "to date → 23:59:59 UTC+7 (không phải 00:00:00 ngày hôm sau) — đảm bảo SC-5 timezone correctness"
  - "qFilter = null khi blank — không pass empty string vào LIKE query (tránh unnecessary LIKE %%)"
  - "userId = null → backward compat: fallback listOrders() cũ cho admin/test path"
metrics:
  duration: "~35 phút"
  completed: "2026-04-27"
  tasks_completed: 2
  tasks_total: 2
  files_modified: 3
requirements_addressed: [ACCT-02]
---

# Phase 11 Plan 02: Order Filter Backend — SUMMARY

**One-liner:** JPQL filter query cho order-service với UTC+7 timezone-correct date range (D-14) + server-side status/keyword filter (D-12, D-13).

## Objective

Extend order-service backend để `GET /orders` nhận thêm filter params (status, from, to, q), query DB server-side, trả paginated results đúng timezone. Phục vụ ACCT-02 — frontend `/profile/orders` page.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | ListMyOrdersQuery + filter logic + extend OrderRepository | 63ca14f | OrderRepository.java, OrderCrudService.java |
| 2 | Extend OrderController GET /orders với filter params | 82debcf | OrderController.java |

## What Was Built

### Task 1 — OrderRepository + OrderCrudService

**OrderRepository** — thêm JPQL `@Query` method:
```java
@Query("SELECT o FROM OrderEntity o WHERE o.userId = :userId "
     + "AND (:status IS NULL OR o.status = :status) "
     + "AND (:from IS NULL OR o.createdAt >= :from) "
     + "AND (:to IS NULL OR o.createdAt <= :to) "
     + "AND (:q IS NULL OR LOWER(o.id) LIKE LOWER(CONCAT('%', :q, '%'))) "
     + "ORDER BY o.createdAt DESC")
List<OrderEntity> findByUserIdWithFilters(userId, status, from, to, q);
```

- T-11-02-03 mitigated: JPQL `@Param` binding — không raw string concat, tránh injection.
- T-11-02-01 mitigated: filter theo `userId` từ X-User-Id header — không thể xem orders của user khác.

**OrderCrudService** — thêm `ListMyOrdersQuery` record (8 fields) + `listMyOrders()`:
- D-14 timezone: `ZoneOffset.of("+07:00")` — KHÔNG trust client timezone string.
- `from` → `atStartOfDay().toInstant(saigon)` (00:00:00 UTC+7).
- `to` → `atTime(23, 59, 59).toInstant(saigon)` — đảm bảo SC-5 (đơn 23:59 GMT+7 ngày 30/4 không bị miss).
- `statusFilter = null` khi `status = "ALL"` hoặc null.
- `qFilter = null` khi `q` blank.

### Task 2 — OrderController

`listOrders()` extend với:
- 4 filter `@RequestParam` (status, from, to, q) — tất cả `required=false`.
- `@RequestHeader("X-User-Id", required=false)` — consistent với `createOrder` pattern hiện tại.
- Routing: `userId != null` → `listMyOrders(ListMyOrdersQuery)`; `userId = null` → `listOrders()` cũ (backward compat cho admin/test).

## Deviations from Plan

Không có — plan executed exactly as written.

## Threat Surface Scan

Không có surface mới ngoài threat model trong plan:
- T-11-02-01 (Information Disclosure): mitigated bằng userId filter.
- T-11-02-02 (Tampering - timezone): mitigated bằng server-side ZoneOffset +07:00.
- T-11-02-03 (Injection): mitigated bằng JPQL @Param binding.
- T-11-02-04 (DoS - malformed input): accepted (demo scope).

## Known Stubs

Không có stub — backend filter logic hoàn chỉnh, frontend sẽ wire trong Plan 11-03 (order filter page).

## Self-Check: PASSED

- [x] OrderRepository.java có `findByUserIdWithFilters` với 5 @Param: `git show 63ca14f --name-only`
- [x] OrderCrudService.java có `ListMyOrdersQuery` record + `listMyOrders()` method
- [x] `23:59:59` end-of-day UTC+7 present (SC-5 timezone correctness)
- [x] OrderController.java có 4 filter @RequestParam + X-User-Id header routing
- [x] Commit 63ca14f và 82debcf tồn tại trong git log
