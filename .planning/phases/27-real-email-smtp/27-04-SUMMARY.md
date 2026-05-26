---
phase: 27-real-email-smtp
plan: "04"
subsystem: user-service
tags: [auth, verification-token, email-flow, tdd, anti-enumeration]
dependency_graph:
  requires:
    - "27-02: VerificationTokenService + AccountEventPublisher"
  provides:
    - "AuthService.register hook: sinh EMAIL_VERIFY token 24h + publishUserRegistered sau save"
    - "AuthService.verifyEmail: verifyAndConsume EMAIL_VERIFY + lật emailVerified=true"
    - "AuthService.forgotPassword: anti-enumeration, ifPresent sinh PASSWORD_RESET token 1h + publishPasswordReset"
    - "AuthService.resetPassword: verifyAndConsume PASSWORD_RESET + changePasswordHash"
    - "GET /auth/verify-email?token=... endpoint public"
    - "POST /auth/password/forgot endpoint public (luôn 200)"
    - "POST /auth/password/reset endpoint public"
  affects:
    - "user-service: AuthService + AuthController đã wire toàn luồng MAIL-02"
    - "notification-service (Wave 3 Plan 27-03): nhận user events từ user.events exchange"
tech_stack:
  added: []
  patterns:
    - "ifPresent anti-enumeration pattern — forgotPassword không throw dù email không tồn tại (D-08, T-27-10)"
    - "verifyAndConsume single-use token — replay → 410 GONE (T-27-04)"
    - "ApiResponse.of() tránh double-wrap (Pattern 4 từ 27-PATTERNS.md)"
    - "TDD RED/GREEN — AuthControllerIT 5 cases"
    - "D-07: register phát JWT ngay, không hard-gate email_verified"
key_files:
  created:
    - "sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/dto/ForgotPasswordRequest.java"
    - "sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/dto/ResetPasswordRequest.java"
    - "sources/backend/user-service/src/test/java/com/ptit/htpt/userservice/web/AuthControllerIT.java"
  modified:
    - "sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/service/AuthService.java"
    - "sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/AuthController.java"
decisions:
  - "D-07 LOCK: register vẫn phát JWT ngay — email_verified KHÔNG hard-gate login"
  - "D-08 + T-27-10: forgotPassword dùng ifPresent pattern không throw → anti-enumeration"
  - "resetPassword dùng changePasswordHash() (tên method thực tế của UserEntity) thay vì setPasswordHash"
  - "AuthControllerIT dùng @DynamicPropertySource exclude RabbitAutoConfiguration để không cần broker trong test"
  - "3 endpoint verify-email/forgot/reset PUBLIC — user-service không có Spring Security filter (auth do API Gateway handle)"
metrics:
  duration: "10 phút"
  completed_date: "2026-05-22"
  tasks_completed: 2
  files_created: 3
  files_modified: 2
---

# Phase 27 Plan 04: Wire Auth Token Flow — AuthService + AuthController Summary

**One-liner:** AuthService register hook + 3 method verifyEmail/forgotPassword/resetPassword + 3 endpoint public trong AuthController hoàn tất luồng MAIL-02 phía user-service.

## Completed Tasks

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | AuthService register hook + verifyEmail/forgotPassword/resetPassword + 2 DTO | 43d158c | AuthService.java, ForgotPasswordRequest.java, ResetPasswordRequest.java |
| 2 RED | AuthControllerIT (TDD RED) | 70f09ab | AuthControllerIT.java |
| 2 GREEN | 3 endpoint mới AuthController | 145567f | AuthController.java |

## What Was Built

### Request DTOs (web/dto/)

- **ForgotPasswordRequest**: record `(@Email @NotBlank String email)` — validation chỉ format email.
- **ResetPasswordRequest**: record `(@NotBlank String token, @NotBlank @Size(min=6) String newPassword)` — nhất quán với RegisterRequest constraint.

### AuthService — 3 method mới + register hook

- **register() hook (MAIL-02)**: sau `userRepo.save(entity)` (giữ JWT ngay — D-07), sinh EMAIL_VERIFY token 24h, dựng verifyUrl từ APP_BASE_URL, gọi `accountEventPublisher.publishUserRegistered()`.
- **verifyEmail(String token)**: `verifyAndConsume("EMAIL_VERIFY")` → tìm user → `setEmailVerified(true)` → save.
- **forgotPassword(String email)**: `findByEmail().ifPresent(user -> ...)` — KHÔNG throw nếu email không tồn tại (anti-enumeration T-27-10, D-08); nếu user tồn tại: sinh PASSWORD_RESET token 1h + `publishPasswordReset()`.
- **resetPassword(String token, String newPassword)**: `verifyAndConsume("PASSWORD_RESET")` → tìm user → `changePasswordHash(encoder.encode(newPassword))` → save.

### AuthController — 3 endpoint mới

- `GET /auth/verify-email?token=...` → `ApiResponse.of(200, "Email xác minh thành công", null)`
- `POST /auth/password/forgot` `@Valid ForgotPasswordRequest` → `ApiResponse.of(200, "Nếu email tồn tại...", null)` (luôn 200)
- `POST /auth/password/reset` `@Valid ResetPasswordRequest` → `ApiResponse.of(200, "Mật khẩu đã được đặt lại", null)`

Tất cả 3 endpoint public, không cần JWT, trả `ApiResponse.of()` để tránh double-wrap.

### AuthControllerIT (TDD)

5 test case:
1. verify-email valid token → 200 + `emailVerified=true` (DB check)
2. forgot email không tồn tại → 200 (anti-enumeration)
3. forgot email tồn tại → 200
4. reset password token hợp lệ → 200; login bằng pass mới thành công (end-to-end)
5. verify-email token replay → 410 GONE

## TDD Gate Compliance

- RED gate: commit `70f09ab` — `test(27-04): add failing tests for AuthController 3 endpoint mới (TDD RED)` — 5 test cases.
- GREEN gate: commit `145567f` — `feat(27-04): 3 endpoint mới AuthController — verify-email + password/forgot + password/reset`.
- REFACTOR: không cần — code đã clean.

## Deviations from Plan

### Auto-fix Issues

**1. [Rule 1 - Bug fix] resetPassword dùng changePasswordHash() thay setPasswordHash()**

- **Found during:** Task 1 - đọc UserEntity.java
- **Issue:** Plan đề xuất `user.setPasswordHash(passwordEncoder.encode(newPassword))` nhưng UserEntity không có method `setPasswordHash()`. Method thực tế là `changePasswordHash(newPasswordHash)` (lines 95-98 UserEntity.java).
- **Fix:** Dùng `user.changePasswordHash(passwordEncoder.encode(newPassword))` đúng API.
- **Files modified:** AuthService.java
- **Commit:** 43d158c

### Deviations Không Phát Sinh Vấn Đề

- Plan đề xuất tạo DTO trong `web/dto/` — đã tạo đúng package.
- user-service không có Spring Security filter riêng — 3 endpoint tự nhiên public (auth do API Gateway handle). Không cần thêm whitelist.
- AuthControllerIT cần exclude RabbitAutoConfiguration để test không crash khi không có RabbitMQ broker — thêm `@DynamicPropertySource` override (Rule 2 auto-fix missing infra).

## Known Stubs

Không có stub. Toàn bộ luồng đã wire:
- `AuthService.register()` → `VerificationTokenService.createToken()` → `AccountEventPublisher.publishUserRegistered()` → `user.events` exchange → `notification.user-events` queue → `UserEventListener` (Plan 27-03) → SMTP email.
- `AuthService.forgotPassword()` → `publishPasswordReset()` → tương tự.

## Threat Surface Scan

Không có surface mới ngoài threat model của plan:
- `GET /auth/verify-email` và `POST /auth/password/forgot + reset`: đã trong T-27-10 + T-27-11 + T-27-12.
- Anti-enumeration (T-27-10) đã mitigate: `forgotPassword` dùng `ifPresent` pattern, không throw.
- Token replay (T-27-11): `verifyAndConsume` kiểm tra `usedAt TRƯỚC expiresAt` (pattern từ Plan 27-02).

## Self-Check: PASSED

Files created:
- [x] ForgotPasswordRequest.java — tồn tại tại web/dto/
- [x] ResetPasswordRequest.java — tồn tại tại web/dto/
- [x] AuthControllerIT.java — tồn tại tại test/web/

Files modified:
- [x] AuthService.java — có publishUserRegistered + verifyEmail + forgotPassword + resetPassword
- [x] AuthController.java — có verify-email + password/forgot + password/reset endpoints

Commits:
- [x] 43d158c — feat(27-04): AuthService register hook + verifyEmail/forgotPassword/resetPassword + 2 request DTO
- [x] 70f09ab — test(27-04): add failing tests for AuthController 3 endpoint mới (TDD RED)
- [x] 145567f — feat(27-04): 3 endpoint mới AuthController — verify-email + password/forgot + password/reset
