---
status: partial
phase: 27-real-email-smtp
source: [27-VERIFICATION.md]
started: 2026-05-23T00:00:00Z
updated: 2026-05-23T00:00:00Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. Trang /forgot-password layout và UX
expected: Mở `http://localhost:3000/forgot-password`, nhập email hợp lệ, click "Gửi link đặt lại mật khẩu" → form ẩn đi và hiện panel "Kiểm tra hộp thư của bạn" với email in đậm. Layout giống login page, tiếng Việt đúng UI-SPEC.
result: [pending]

### 2. Trang /verify-email state machine
expected: (a) Mở `/verify-email` không token → status card "Link không hợp lệ" hiện ngay (icon đỏ, không spinner vô tận). (b) Mở `/verify-email?token=invalidtoken123` → spinner loading rồi chuyển "Link không hợp lệ".
result: [pending]

### 3. Trang /reset-password validation
expected: Mở `/reset-password?token=abc`, nhập mật khẩu mới < 6 ký tự + xác nhận không khớp → hiện "Mật khẩu ít nhất 6 ký tự" và "Mật khẩu không khớp" trước khi gọi API (client-side validation).
result: [pending]

### 4. End-to-end email flow (cần SMTP env)
expected: Cấu hình `.env` với Gmail App Password, start docker-compose, đăng ký tài khoản mới với email thật → email xác minh tới hộp thư trong vài giây với nút "Xác minh email"; click link → trang verify-email success. Admin đổi trạng thái đơn sang "shipped" → email cập nhật tới khách. Đặt đơn → email xác nhận đơn hàng.
result: [pending]

### 5. Graceful degradation khi thiếu SMTP env
expected: Start notification-service KHÔNG có biến môi trường `MAIL_SMTP_*` → service khởi động bình thường, log cảnh báo, `dispatch_log` ghi status=SKIPPED (không crash).
result: [pending]

## Summary

total: 5
passed: 0
issues: 0
pending: 5
skipped: 0
blocked: 0

## Gaps
