---
phase: 13-reviews-ratings
plan: "01"
subsystem: auth
tags: [jwt, jjwt, user-service, claim, fullName]

# Dependency graph
requires:
  - phase: 06-auth
    provides: JwtUtils.issueToken + AuthService register/login — nền tảng JWT HS256 hiện tại
provides:
  - JwtUtils.issueToken(userId, username, fullName, roles) phát claim 'name' trong JWT
  - AuthService truyền entity.fullName() cho cả register lẫn login
affects:
  - 13-reviews-ratings/13-03 (ReviewController dùng claim 'name' để snapshot reviewerName)
  - downstream services dùng JWT (api-gateway, product-service, order-service)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "JWT claim 'name' fallback: (fullName != null && !fullName.isBlank()) ? fullName : username — D-10 defense in depth"

key-files:
  created: []
  modified:
    - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/jwt/JwtUtils.java
    - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/service/AuthService.java

key-decisions:
  - "Fallback về username (không về empty string) khi fullName null/blank — đảm bảo claim 'name' luôn có giá trị non-blank trong token"
  - "Defense in depth: JwtUtils set claim, ReviewController vẫn cần kiểm tra null cho token cũ chưa có claim 'name'"

patterns-established:
  - "Pattern D-10: JWT consumer fallback — luôn kiểm tra null trước khi dùng optional claim từ token"

requirements-completed: [REV-01, REV-02]

# Metrics
duration: 15min
completed: 2026-04-27
---

# Phase 13 Plan 01: JWT 'name' Claim Summary

**JwtUtils.issueToken mở rộng lên 4 tham số với claim 'name' = fullName||username; AuthService register/login cập nhật 2 call site — prerequisite cho ReviewController snapshot reviewerName (Plan 03)**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-04-27T16:01:00Z
- **Completed:** 2026-04-27T16:10:00Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- Mở rộng `JwtUtils.issueToken` từ 3-arg sang 4-arg với claim `'name'` và fallback `username` khi `fullName` null/blank
- Cập nhật 2 call site trong `AuthService` (register + login) để truyền `entity.fullName()`
- Compile PASS; 10/12 tests xanh (2 fail do Docker không khởi động — pre-existing infrastructure issue)

## Task Commits

Mỗi task được commit riêng biệt:

1. **Task 1: Mở rộng JwtUtils.issueToken thêm fullName + claim 'name'** - `4bbf19a` (feat)
2. **Task 2: Cập nhật AuthService register+login truyền fullName** - `61df947` (feat)

**Plan metadata:** (sẽ được thêm sau khi commit SUMMARY)

## Files Created/Modified
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/jwt/JwtUtils.java` — Đổi signature sang 4-arg, thêm `.claim("name", ...)` với fallback
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/service/AuthService.java` — Cập nhật 2 call site truyền `entity.fullName()`

## Decisions Made
- **Fallback về `username` (không `""`):** Đảm bảo claim `'name'` luôn non-blank. ReviewController Phase 03 vẫn kiểm tra null cho token cũ (backward compatibility).
- **Không thêm import mới:** `String` đã có sẵn trong cả 2 file.

## Deviations from Plan

### Auto-fixed Issues

Không có auto-fix cần thiết. Tuy nhiên có 1 quan sát về execution order:

**[Observation] Task 1 verify compile không thể PASS độc lập trước Task 2**
- **Found during:** Task 1 verify
- **Issue:** Plan yêu cầu `./mvnw -q -DskipTests compile` PASS sau Task 1, nhưng AuthService vẫn dùng 3-arg call → compile fail là expected cho đến khi Task 2 hoàn thành
- **Fix:** Thực hiện Task 2 trước khi chạy compile verify (không phải bug — chỉ là inter-task dependency)
- **Impact:** Không ảnh hưởng outcome. Cả 2 task commit riêng, compile PASS sau Task 2.

---

**Total deviations:** 0 auto-fix. 1 observation về execution order (không ảnh hưởng kết quả).
**Impact on plan:** Không có scope creep. Code thay đổi chính xác như plan mô tả.

## Issues Encountered

**Docker not running — Testcontainers fail:**
- `UserRepositoryJpaTest` và `AuthControllerTest` dùng Testcontainers (PostgreSQL container)
- Docker daemon không khởi động được trong môi trường CI worktree
- Đây là **pre-existing infrastructure issue**, không do thay đổi code của plan này
- 10 tests không dùng Docker (GlobalExceptionHandlerTest: 8 PASS, BCryptSeedHashTest: 2 PASS) đều xanh
- Pitfall đã handle: fallback `username` khi `fullName` null — claim `'name'` luôn có giá trị non-blank

## Known Stubs

None — không có stub hay placeholder trong code thay đổi.

## Threat Flags

Không phát hiện surface mới ngoài threat model đã đăng ký (T-13-01-01 đến T-13-01-04).

## Next Phase Readiness
- JWT token mới phát hành sau register/login chứa claim `'name'` = fullName (hoặc fallback username)
- Token cũ (chưa có claim `'name'`) vẫn tương thích — consumer cần fallback (Plan 03 ReviewController)
- **Sẵn sàng cho Plan 03:** ReviewController có thể đọc `claims.get("name")` để snapshot `reviewerName`
- **Blocker nhỏ:** Docker cần chạy để Testcontainers-based tests pass (UserRepositoryJpaTest, AuthControllerTest)

---
*Phase: 13-reviews-ratings*
*Completed: 2026-04-27*
