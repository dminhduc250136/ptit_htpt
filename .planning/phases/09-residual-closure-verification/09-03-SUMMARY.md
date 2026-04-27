---
phase: 09-residual-closure-verification
plan: "03"
subsystem: user-service + api-gateway
tags: [auth, password-change, bcrypt, jwt, spring-boot, gateway-routing]
dependency_graph:
  requires: []
  provides: [POST /api/users/me/password endpoint, gateway route user-service-me]
  affects: [user-service, api-gateway]
tech_stack:
  added: [InvalidPasswordException (custom), UserPasswordService, UserMeController, ChangePasswordRequest]
  patterns: [BCrypt re-authentication, custom exception + GlobalExceptionHandler @ExceptionHandler, JWT subject extraction, gateway route precedence ordering]
key_files:
  created:
    - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/ChangePasswordRequest.java
    - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/service/UserPasswordService.java
    - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/UserMeController.java
    - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/exception/InvalidPasswordException.java
  modified:
    - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/api/GlobalExceptionHandler.java
    - sources/backend/api-gateway/src/main/resources/application.yml
decisions:
  - "Option A cho errorCode binding: tạo InvalidPasswordException (RuntimeException subclass) + @ExceptionHandler riêng trong GlobalExceptionHandler thay vì sửa mapCommonCode(). Lý do: mapCommonCode() không có case UNPROCESSABLE_ENTITY — fall-through trả BAD_REQUEST, không đúng AUTH_INVALID_PASSWORD. Option A sạch hơn, không ảnh hưởng handler đang dùng."
  - "UserEntity dùng entity.changePasswordHash(newHash) (JPA mutable setter, Phase 7 Plan 03) — không cần withPasswordHash immutable pattern."
  - "D-10 honored: UserPasswordService không invalidate token, không issue token mới — chỉ update hash trong DB."
metrics:
  duration: "~25 phút"
  completed: "2026-04-27"
  tasks_completed: 2
  files_changed: 6
---

# Phase 9 Plan 03: Endpoint POST /api/users/me/password — BCrypt Verify + AUTH_INVALID_PASSWORD Summary

**One-liner:** Backend endpoint đổi password với BCrypt re-authentication, 422 AUTH_INVALID_PASSWORD khi sai oldPassword, gateway route user-service-me đứng trước catch-all.

## Tasks Completed

| Task | Mô tả | Commit | Files |
|------|-------|--------|-------|
| 1 | Backend POST /users/me/password + BCrypt verify + AUTH_INVALID_PASSWORD | `c9211a7` | ChangePasswordRequest, UserPasswordService, UserMeController, InvalidPasswordException, GlobalExceptionHandler |
| 2 | Gateway route user-service-me + user-service-me-base | `7dace41` | application.yml |

## Task 1: Backend Endpoint Chi Tiết

### Decision Branch: errorCode Binding (Option A)

**Phân tích GlobalExceptionHandler hiện tại:**
- `handleResponseStatus()` dùng `mapCommonCode(status)` để bind errorCode.
- `mapCommonCode()` không có case `UNPROCESSABLE_ENTITY` (422) → fall-through trả `"BAD_REQUEST"`.
- Nếu dùng `ResponseStatusException(422, "AUTH_INVALID_PASSWORD")`, frontend nhận `code = "BAD_REQUEST"` — sai yêu cầu.

**Lựa chọn: Option A — Custom Exception**
- Tạo `InvalidPasswordException extends RuntimeException` (không có `@ResponseStatus`, xử lý tại handler)
- Thêm `@ExceptionHandler(InvalidPasswordException.class)` riêng trong `GlobalExceptionHandler`
- Handler trả `ApiErrorResponse` với `code = "AUTH_INVALID_PASSWORD"`, `status = 422`
- `UserPasswordService` throw `InvalidPasswordException()` thay vì `ResponseStatusException`

**Kết quả:** Frontend nhận `{status: 422, code: "AUTH_INVALID_PASSWORD", message: "Mật khẩu hiện tại không đúng", ...}` — đúng spec D-11.

### UserEntity Pattern

`UserEntity` là JPA mutable class (không phải record). Có method `changePasswordHash(String newPasswordHash)` từ Phase 7 Plan 03. Dùng pattern:
```java
entity.changePasswordHash(newHash);
userRepo.save(entity);
```
Không cần `withPasswordHash()` (immutable) hay `setPasswordHash()` (generic setter).

### Validation Trên ChangePasswordRequest

```java
@NotBlank String oldPassword
@NotBlank @Size(min=8) @Pattern(letter) @Pattern(digit) String newPassword
```
- `newPassword < 8 ký tự` → `@Size(min=8)` → `MethodArgumentNotValidException` → 400 (GlobalExceptionHandler `handleValidation`)
- `newPassword` thiếu letter/digit → 400 tương tự

### Threat Mitigations (T-09-03-01, T-09-03-02, T-09-03-03)

- **T-09-03-01** mitigated: `passwordEncoder.matches(req.oldPassword(), entity.passwordHash())` BẮT BUỘC trước `changePasswordHash()`. Nếu sai → throw `InvalidPasswordException` ngay, không reach save().
- **T-09-03-02** mitigated: Controller extract userId từ `jwtUtils.parseToken(token).getSubject()` — không nhận userId từ path/body param.
- **T-09-03-03** mitigated: Không log request body trong controller. `InvalidPasswordException` message chỉ là "Mật khẩu hiện tại không đúng" — không leak password value.

## Task 2: Gateway Route Chi Tiết

### YAML Diff

Insert 2 routes sau `user-service-admin` (line 48) và trước `user-service-base` (line 67 sau insert):

```yaml
# user-service me: /api/users/me/** → /users/me/**
- id: user-service-me-base
  uri: http://user-service:8080
  predicates:
    - Path=/api/users/me
  filters:
    - RewritePath=/api/users/me, /users/me
- id: user-service-me
  uri: http://user-service:8080
  predicates:
    - Path=/api/users/me/**
  filters:
    - RewritePath=/api/users/me/(?<seg>.*), /users/me/${seg}
```

### Route Order Verification

```
user-service-me    line 59
user-service-base  line 67
→ 59 < 67: ROUTE ORDER OK
```

Thứ tự final cho user-service routes:
1. `user-service-auth-base` + `user-service-auth` (lines 23, 29)
2. `user-service-admin-base` + `user-service-admin` (lines 37, 43)
3. `user-service-me-base` + `user-service-me` (lines 53, 59) ← MỚI
4. `user-service-base` + `user-service` (lines 67, 73) — catch-all

## Curl Smoke Cases (Documented — chưa chạy end-to-end do Docker không available trong CI)

| Case | Request | Expected | Mô tả |
|------|---------|----------|-------|
| 1 | POST /api/users/me/password với valid token + đúng oldPassword + newPassword="NewPass123" | 200 `{message:"Đã đổi mật khẩu", data:{changed:true}}` | Happy path — BCrypt verify pass, hash update |
| 2 | POST /api/users/me/password với valid token + SAI oldPassword | 422 `{code:"AUTH_INVALID_PASSWORD", message:"Mật khẩu hiện tại không đúng"}` | InvalidPasswordException → handler |
| 3 | POST /api/users/me/password thiếu Authorization header | 401 `{code:"UNAUTHORIZED"}` | extractUserIdFromBearer() check null |
| 4 | POST /api/users/me/password với newPassword="short" (< 8 ký tự) | 400 `{code:"VALIDATION_ERROR", fieldErrors:[{field:"newPassword"}]}` | @Size(min=8) validation fail |

**Note:** Curl smoke 4 case được document theo spec plan. Môi trường Docker không available trong execution environment (CI context), do đó chỉ verify qua `mvn package -DskipTests` pass + code path analysis. End-to-end test sẽ chạy khi `docker compose up` local.

## Build Verification

```
user-service: mvn clean compile -q → exit 0 (no output)
user-service: mvn package -DskipTests -q → exit 0 (no output)
api-gateway:  mvn clean package -DskipTests -q → exit 0 (no output)
```

## Deviations from Plan

### Auto-added

**1. [Rule 2 - Missing critical functionality] Tạo InvalidPasswordException class**
- **Found during:** Task 1 — đọc GlobalExceptionHandler
- **Issue:** `mapCommonCode()` không cover `UNPROCESSABLE_ENTITY` (422). Dùng `ResponseStatusException(422, "AUTH_INVALID_PASSWORD")` sẽ trả `code = "BAD_REQUEST"` — sai D-11 spec.
- **Fix:** Tạo `InvalidPasswordException extends RuntimeException` + thêm `@ExceptionHandler(InvalidPasswordException.class)` trong GlobalExceptionHandler với `code = "AUTH_INVALID_PASSWORD"` (Option A từ interfaces spec).
- **Files modified:** `exception/InvalidPasswordException.java` (mới), `api/GlobalExceptionHandler.java` (thêm handler)
- **Commit:** `c9211a7`

## Known Stubs

Không có stubs. Endpoint thật: BCrypt verify thật, DB save thật, JWT parse thật.

## Threat Flags

Không có threat surface mới ngoài `<threat_model>` trong plan.

## Note cho Plan 09-04

Plan 09-04 frontend password change form cần:
- `POST /api/users/me/password` với body `{oldPassword, newPassword}`
- Authorization: Bearer token (từ localStorage/session)
- Xử lý response:
  - 200 → toast "Đã đổi mật khẩu", giữ session (D-10)
  - 422 + `code = "AUTH_INVALID_PASSWORD"` → field-level error tại "Mật khẩu hiện tại": "Mật khẩu hiện tại không đúng" (D-11)
  - 400 + `fieldErrors` → validation error per field
  - 401 → redirect to login

## Self-Check: PASSED

```
ChangePasswordRequest.java   FOUND ✓
UserPasswordService.java     FOUND ✓
UserMeController.java        FOUND ✓
InvalidPasswordException.java FOUND ✓
GlobalExceptionHandler.java  modified ✓ (AUTH_INVALID_PASSWORD handler added)
application.yml              modified ✓ (2 routes inserted)

Commits:
c9211a7 FOUND ✓ (feat(09-03): backend POST /users/me/password...)
7dace41 FOUND ✓ (feat(09-03): gateway route user-service-me...)

Route order: user-service-me (59) < user-service-base (67) ✓
```
