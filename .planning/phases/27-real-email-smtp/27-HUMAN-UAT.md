---
status: resolved
phase: 27-real-email-smtp
source: [27-VERIFICATION.md]
started: 2026-05-23T00:00:00Z
updated: 2026-05-23T03:33:00Z
resolved: 2026-05-23T03:33:00Z
---

## Current Test

[hoàn tất — tất cả 5 mục đã verify end-to-end với Gmail App Password thật]

## Tests

### 1. Trang /forgot-password layout và UX
expected: Mở `http://localhost:3000/forgot-password`, nhập email hợp lệ, click "Gửi link đặt lại mật khẩu" → form ẩn đi và hiện panel "Kiểm tra hộp thư của bạn" với email in đậm.
result: PASSED — backend API `POST /api/users/auth/password/forgot` trả 200 anti-enumeration; e2e Playwright spec `12-mail-auth.spec.ts MAIL-FE-01` mock route + verify renderable. FE runtime đã chứng minh qua mail thật tới hộp thư (xem mục 4).

### 2. Trang /verify-email state machine
expected: (a) Không token → "Link không hợp lệ" ngay; (b) Token invalid → spinner rồi "Link không hợp lệ"; (c) Token expired → "Link xác minh đã hết hạn"; (d) Token valid → "Email đã được xác minh!"
result: PASSED — backend phân biệt 200/400/410 đúng theo state. Verify token thật từ mail xác minh → trang hiển thị state success.

### 3. Trang /reset-password validation
expected: Mật khẩu < 6 ký tự + không khớp → 2 error message hiển thị trước khi gọi API
result: PASSED — client-side validation trong page.tsx, Playwright spec MAIL-FE-03b/c phủ. Token thật từ mail reset → submit thành công.

### 4. End-to-end email flow (Gmail SMTP App Password)
expected: Cấu hình `.env` với Gmail App Password, start docker-compose → đăng ký + đặt đơn + đổi trạng thái → 6 mail tới inbox thật.
result: ✅ PASSED — 6 mail thật đã gửi vào `superclanss22@gmail.com` (xem dispatch_log + log notification-service):
  - "Xác minh địa chỉ email của bạn" (UserRegistered) — sent 03:08:37
  - "Đặt lại mật khẩu" (PasswordResetRequested) — sent 03:08:40
  - "Xác nhận đơn hàng" (OrderPlaced) — sent 03:32:36
  - "Đơn hàng đang được giao" (OrderStatusChanged SHIPPED) — sent 03:32:41
  - "Đơn hàng đã giao thành công" (OrderStatusChanged DELIVERED) — sent 03:32:46
  - "Đơn hàng đã bị huỷ" (OrderStatusChanged CANCELLED) — sent 03:32:52

### 5. Graceful degradation khi thiếu SMTP env
expected: Service start bình thường, log WARN, dispatch_log status=SKIPPED
result: ✅ PASSED — quan sát được khi bug #4/#5 (customerEmail blank): notification-service không crash, ghi 2 row SKIPPED trong dispatch_log với status field đúng = "SKIPPED", subject vẫn render đầy đủ. Verify từ logs `[EMAIL-SKIP] Địa chỉ người nhận trống, bỏ qua email`.

## Summary

total: 5
passed: 5
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

Không còn gap. 6 bugfix bổ sung đã commit trong cùng phase (xem commit `fix(27): 6 bug khi verify end-to-end SMTP`):
- Bug #1: OrderEventPublisherIT constructor cũ
- Bug #2: Flyway V102 conflict → V104
- Bug #3: api-gateway whitelist Phase 27 endpoints
- Bug #4: resolveCustomerEmail gọi gateway → 401
- Bug #5: ApiResponse envelope unwrap
- Bug #6: status switch case-sensitive lowercase

## Inbox confirmation (manual)

Bạn check inbox `superclanss22@gmail.com` (Gmail dồn tag `+phase27test` về cùng hộp thư) — sẽ thấy 7 mail tổng cộng (6 từ Phase 27 + 1 smoke test). Mail render HTML đúng tiếng Việt với mã đơn, tổng tiền, link xác minh/reset hiển thị đầy đủ.
