---
status: partial
phase: 17-s-a-order-detail-items
source: [17-VERIFICATION.md]
started: 2026-05-02T14:53:33Z
updated: 2026-05-02T14:53:33Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. /profile/orders/{id} render thumbnail + brand
expected: Mỗi row hiển thị Image (hoặc placeholder 📦) + brand (hoặc '—') — KHÔNG còn placeholder, KHÔNG empty rows
result: [pending]

### 2. /admin/orders/{id} render full items table + shipping/payment Vietnamese
expected: Items table 4 cột render đúng + card 'Thông tin giao hàng' render địa chỉ + payment method tiếng Việt — KHÔNG còn 'khả dụng sau khi Phase 8'
result: [pending]

### 3. Empty items state
expected: Cả 2 page render '<p>Đơn hàng không có sản phẩm</p>' thay vì table rỗng
result: [pending]

### 4. Soft-deleted product fallback
expected: Thumbnail = placeholder div 📦, brand subtitle = '—' (Promise.allSettled không kill render)
result: [pending]

### 5. Playwright E2E suite full run sau khi backend stack up
expected: `npx playwright test e2e/admin-orders.spec.ts e2e/order-detail.spec.ts` → 5 tests pass hoặc skip-with-reason, 0 failed
result: [pending]

## Summary

total: 5
passed: 0
issues: 0
pending: 5
skipped: 0
blocked: 0

## Gaps
