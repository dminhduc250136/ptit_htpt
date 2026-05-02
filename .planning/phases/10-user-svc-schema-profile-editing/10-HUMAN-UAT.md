---
status: partial
phase: 10-user-svc-schema-profile-editing
source: [10-VERIFICATION.md]
started: 2026-04-27T05:30:00Z
updated: 2026-04-27T05:30:00Z
---

## Current Test

[awaiting human testing — cần docker compose up + browser]

## Tests

### 1. Profile Info Form — Submit Happy Path
expected: Login, vào /profile/settings, sửa fullName thành "Nguyễn Văn Test" + phone "+84 901 234 567", click "Lưu thay đổi" → toast "Đã cập nhật" ~3s; navbar top-right hiển thị tên mới ngay (không reload)
result: [pending]

### 2. Client-side Validation (Zod)
expected: Nhập phone = "abc" → submit → field error "Số điện thoại không hợp lệ" hiển thị ngay dưới input; form không gửi request (no network call)
result: [pending]

### 3. Backend 400 fieldErrors Mapping
expected: Submit phone vượt regex → backend 400 fieldErrors[{field:'phone'}] → setError mapped đúng field, hiển thị field-level error
result: [pending]

### 4. Phase 9 Security Section Intact
expected: Section "Đổi mật khẩu" vẫn hiển thị 3 input fields + submit → đổi mật khẩu hoạt động bình thường (Phase 10 changes không break)
result: [pending]

### 5. Avatar Placeholder Visual
expected: Section "Ảnh đại diện" hiển thị initials circle (chữ cái đầu email) + text "Tính năng tải ảnh đại diện sẽ có trong bản cập nhật sau." — KHÔNG có file input
result: [pending]

## Summary

total: 5
passed: 0
issues: 0
pending: 5
skipped: 0
blocked: 0

## Gaps
