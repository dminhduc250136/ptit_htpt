---
status: partial
phase: 07-search-admin-real-data
source: [07-VERIFICATION.md]
started: 2026-04-26T11:45:00.000Z
updated: 2026-04-26T11:45:00.000Z
---

## Current Test

[awaiting human testing — requires browser + Docker stack: `docker compose up`]

## Tests

### 1. Search keyword end-to-end với real data
expected: User nhập keyword vào /search → FE gọi `listProducts({keyword})` qua gateway → gateway route đến product-service → in-memory filter trả products có keyword trong tên → render kết quả; empty keyword → tất cả products; không có kết quả → empty state "Không tìm thấy sản phẩm"
result: [pending]

### 2. Admin create product — modal + category dropdown + persist
expected: Admin vào /admin/products → click "+ Thêm sản phẩm" → modal mở với empty form → category dropdown load từ /api/products/admin/categories → fill brand/thumbnailUrl/shortDescription → submit → POST /api/products/admin → toast success → list refresh với product mới; brand/thumbnail persist trong DB (Flyway V2 columns)
result: [pending]

### 3. Admin orders navigation + status update PATCH
expected: Admin vào /admin/orders → list orders thật từ API → click 📋 trên row → router.push('/admin/orders/{id}') → detail page load với getAdminOrderById → chọn status mới trong dropdown → click "Cập nhật trạng thái" → PATCH /api/orders/admin/{id}/state → toast success
result: [pending]

### 4. Admin edit user fullName — PATCH + list refresh + fallback
expected: Admin vào /admin/users → list users thật → cột Họ tên hiển thị fullName nếu có, fallback username → click ✏️ → UserEditModal pre-filled với fullName/phone/roles → submit → PATCH /api/users/admin/{id} → toast success → list refresh; user có roles=ADMIN không có nút 🗑️
result: [pending]

## Summary

total: 4
passed: 0
issues: 0
pending: 4
skipped: 0
blocked: 0

## Gaps
