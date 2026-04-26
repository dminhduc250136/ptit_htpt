---
phase: 06-real-auth-flow
plan: "02"
subsystem: auth
tags: [typescript, jwt, cookie, user_role, token-storage, auth-service]

requires:
  - phase: 05-database-foundation
    provides: UserEntity.roles field (String), UserDto.username field, seed data với admin/USER roles

provides:
  - RegisterRequest type với username (không có fullName/phone)
  - AuthResponse.refreshToken optional (không crash khi backend không trả)
  - User type có username? và roles? fields
  - token.ts exports setUserRole/clearUserRole cho user_role cookie management
  - setTokens() chấp nhận refresh optional (tránh localStorage "undefined")
  - clearTokens() gọi clearUserRole() nội bộ
  - auth.ts login/register gọi setUserRole sau khi nhận token
  - auth.ts register auto-login check dùng accessToken (không phụ thuộc refreshToken)
  - auth.ts logout fire-and-forget POST /api/users/auth/logout

affects:
  - 06-03 (Plan 03 middleware.ts đọc user_role cookie được set bởi token.ts)
  - 06-01 (Plan 01 backend AuthController trả AuthResponse shape — optional refreshToken)
  - Phase 7 admin pages (user_role cookie cho middleware ADMIN check)

tech-stack:
  added: []
  patterns:
    - "user_role cookie: non-httpOnly, set/clear qua token.ts setUserRole/clearUserRole, đọc bởi middleware Edge Runtime (D-08)"
    - "setTokens optional refresh: chỉ lưu refreshToken nếu có giá trị thật (tránh Pitfall 3)"
    - "auto-login sau register: check data.accessToken only, không check refreshToken (D-04)"

key-files:
  created: []
  modified:
    - sources/frontend/src/types/index.ts
    - sources/frontend/src/services/token.ts
    - sources/frontend/src/services/auth.ts

key-decisions:
  - "RegisterRequest: bỏ fullName/phone, thêm username — đồng bộ với backend AuthController D-01"
  - "AuthResponse.refreshToken: optional — backend Phase 6 không trả field này (deferred refresh flow)"
  - "User.roles: String field (không phải array) — match với UserEntity.roles từ Phase 5"
  - "clearTokens gọi clearUserRole nội bộ — đảm bảo user_role cookie luôn bị xóa khi logout (D-05)"

patterns-established:
  - "Pattern: user_role cookie lifecycle — set sau login/register, clear trong clearTokens"
  - "Pattern: setTokens với refresh optional — guard if(refresh) trước khi lưu vào localStorage"

requirements-completed:
  - AUTH-04
  - AUTH-05
  - AUTH-06

duration: 15min
completed: "2026-04-26"
---

# Phase 06 Plan 02: FE Types + Token + Auth Service Summary

**FE service layer sẵn sàng nhận real AuthResponse: RegisterRequest chuẩn hóa (username), token.ts có user_role cookie management, auth.ts fix auto-login register + setUserRole wired**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-04-26T08:12:00Z
- **Completed:** 2026-04-26T08:27:27Z
- **Tasks:** 2/2
- **Files modified:** 3

## Accomplishments

- `RegisterRequest` đồng bộ với backend D-01: có `username`, không có `fullName`/`phone`
- `token.ts` export `setUserRole`/`clearUserRole` — middleware.ts Plan 03 sẽ đọc cookie `user_role` để check ADMIN cho `/admin/*`
- `auth.ts` fix 3 bugs: refreshToken undefined ghi vào localStorage, auto-login register phụ thuộc refreshToken, thiếu setUserRole call sau login

## Task Commits

1. **Task 1: Cập nhật types/index.ts — RegisterRequest, AuthResponse, User** - `ab55ef4` (feat)
2. **Task 2: token.ts — setUserRole/clearUserRole + fix setTokens; auth.ts — wire setUserRole + fix auto-login** - `6008455` (feat)

## Files Created/Modified

- `sources/frontend/src/types/index.ts` — RegisterRequest (username, no fullName/phone); AuthResponse.refreshToken optional; User thêm username?/roles?
- `sources/frontend/src/services/token.ts` — thêm setUserRole/clearUserRole; fix setTokens optional refresh; clearTokens gọi clearUserRole
- `sources/frontend/src/services/auth.ts` — login/register gọi setUserRole; register auto-login fix; logout fire-and-forget backend call

## Decisions Made

- `User.fullName` giữ nguyên (không xóa) vì profile pages phase khác có thể dùng — chỉ thêm `username?` và `roles?` fields
- `clearUserRole()` định nghĩa trước `clearTokens()` trong file để tránh forward reference issue
- `auth.ts logout()` gọi `httpPost('/api/users/auth/logout').catch(() => {})` — fire-and-forget, không await (D-05)

## Deviations from Plan

Không có — plan thực thi đúng như viết.

## Issues Encountered

- `node_modules` không có trong worktree (worktree chỉ share source files, không install dependencies). TypeScript check chạy từ main repo `/d/SYP_PROJECT/gsd-learning/tmdt-use-gsd/sources/frontend/node_modules/.bin/tsc`. Pre-existing errors ở `e2e/uat.spec.ts` (type `never` từ springdoc — documented trong types/index.ts header comment từ Phase 4). Không có lỗi mới từ các files ta sửa.

## User Setup Required

None - không cần external service configuration.

## Next Phase Readiness

- Plan 03 (FE pages: login/register wire + middleware + /403 page) có thể proceed
- Plan 01 (Backend AuthController) chạy song song wave 1 — không block Plan 02
- `user_role` cookie infrastructure sẵn sàng cho middleware.ts check `/admin/*`
- TypeScript types aligned với backend AuthResponse shape Plan 01 sẽ trả về

## Self-Check

- [x] `sources/frontend/src/types/index.ts` — modified, committed `ab55ef4`
- [x] `sources/frontend/src/services/token.ts` — modified, committed `6008455`
- [x] `sources/frontend/src/services/auth.ts` — modified, committed `6008455`
- [x] Commit `ab55ef4` exists: `feat(06-02): update FE types — RegisterRequest/AuthResponse/User`
- [x] Commit `6008455` exists: `feat(06-02): update token.ts + auth.ts — user_role cookie + auto-login fix`

## Self-Check: PASSED

---
*Phase: 06-real-auth-flow*
*Completed: 2026-04-26*
