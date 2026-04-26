---
status: partial
phase: 06-real-auth-flow
source: [06-VERIFICATION.md]
started: 2026-04-26T00:00:00.000Z
updated: 2026-04-26T00:00:00.000Z
---

## Current Test

[awaiting human testing — cần `docker compose up` với Phase 5 stack running]

## Tests

### 1. Register E2E flow
expected: Điền username/email/password → submit → backend persist UserEntity thật vào Postgres → FE redirect về / (trang chủ) → F5 reload → vẫn logged in (localStorage + cookie persist)
result: [pending]

### 2. Register duplicate errors
expected: Register username đã tồn tại → field error "Tên đăng nhập này đã được sử dụng"; Register email đã tồn tại → field error "Email này đã được đăng ký. Đăng nhập"
result: [pending]

### 3. Login 401 Banner
expected: Login với email/password sai → Banner hiển thị form-level "Email hoặc mật khẩu không chính xác. Vui lòng thử lại" (không highlight input field)
result: [pending]

### 4. Session persist sau reload (AUTH-06)
expected: Login → F5 reload trang → vẫn logged in, không bị redirect về /login; localStorage có access_token, cookie auth_present=1 và user_role tồn tại
result: [pending]

### 5. Middleware protected routes
expected: Logout → truy cập /account/orders → redirect /login?returnTo=%2Faccount%2Forders; Login USER role → truy cập /admin → redirect /403; Login ADMIN role → truy cập /admin → pass through
result: [pending]

### 6. Logout flow
expected: Click logout → localStorage access_token xóa, cookie auth_present và user_role xóa, redirect về / hoặc /login
result: [pending]

## Summary

total: 6
passed: 0
issues: 0
pending: 6
skipped: 0
blocked: 0

## Gaps
