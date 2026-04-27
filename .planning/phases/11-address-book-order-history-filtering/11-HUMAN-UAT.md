---
status: partial
phase: 11-address-book-order-history-filtering
source: [11-VERIFICATION.md]
started: 2026-04-27T00:00:00.000Z
updated: 2026-04-27T00:00:00.000Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. Edit address + Set-default (ACCT-05)
expected: PUT request đến /api/users/me/addresses/{id} — không còn 405 trong browser devtools. Sửa địa chỉ thành công, toast hiển thị, list refresh.
result: [pending]

### 2. Create + Delete + Limit 10 (ACCT-05)
expected: Modal create mở khi click "Thêm địa chỉ mới". Submit → address mới xuất hiện. Delete confirm → address biến mất. Khi đủ 10: nút Thêm disabled + tooltip.
result: [pending]

### 3. Order filter debounce + URL state (ACCT-02)
expected: Thay đổi filter → URL update sau 400ms. "Xóa bộ lọc" hiện khi có filter active. Filter stack với pagination.
result: [pending]

### 4. Checkout AddressPicker snap-fill (ACCT-06)
expected: /checkout hiển thị "Địa chỉ đã lưu ▼" khi login. Chọn address → snap-fill 6 fields (fullName, phone, street, ward, district, city). Fetch fail → ẩn picker (silent).
result: [pending]

## Summary

total: 4
passed: 0
issues: 0
pending: 4
skipped: 0
blocked: 0

## Gaps
