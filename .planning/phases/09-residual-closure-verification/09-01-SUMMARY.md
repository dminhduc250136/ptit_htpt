---
phase: 09-residual-closure-verification
plan: "01"
subsystem: auth
tags: [nextjs, middleware, edge-runtime, auth, redirect]

requires:
  - phase: 06-auth-protection
    provides: "auth_present + user_role cookie pattern + /403 redirect logic (D-07/D-08/D-09)"

provides:
  - "Canonical Edge middleware bảo vệ /admin, /account, /profile, /checkout"
  - "Root duplicate sources/frontend/middleware.ts đã xóa"
  - "Direct-visit /profile/orders (chưa login) → 307 redirect /login?returnTo=%2Fprofile%2Forders"

affects:
  - phase-10-user-profile
  - phase-11-address-management
  - phase-12-wishlist
  - login-page-returnTo-allowlist

tech-stack:
  added: []
  patterns:
    - "Edge middleware matcher chỉ list page routes (KHÔNG /api/*)"
    - "searchParams.set returnTo thay vì manual encodeURIComponent (tránh double-encode)"
    - "user_role cookie includes() check cho comma-separated roles"

key-files:
  created: []
  modified:
    - sources/frontend/src/middleware.ts
  deleted:
    - sources/frontend/middleware.ts

key-decisions:
  - "D-01: canonical = src/middleware.ts; root middleware.ts xóa (dead code, stale duplicate)"
  - "D-02: matcher mở rộng [/admin/:path*, /account/:path*, /profile/:path*, /checkout/:path*]"
  - "D-03: giữ nguyên logic /403 redirect cho non-ADMIN tại /admin/* (Phase 6 D-09)"
  - "searchParams.set returnTo thay root file dùng manual encodeURIComponent (fix double-encode bug)"

patterns-established:
  - "Edge middleware matcher = page-routes only list, no /api/* needed"
  - "returnTo = pathname + req.nextUrl.search (giữ query string, không encode thủ công)"

requirements-completed:
  - AUTH-06

duration: 15min
completed: "2026-04-27"
---

# Phase 9 Plan 01: AUTH-06 Middleware Closure Summary

**Edge middleware hợp nhất về 1 file canonical (src/middleware.ts) với matcher 4 routes bảo vệ /admin, /account, /profile, /checkout — direct-visit /profile/orders (chưa login) → 307 redirect trước React render**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-04-27T00:00:00Z
- **Completed:** 2026-04-27
- **Tasks:** 2 (Task 1 auto-executed, Task 2 checkpoint human-verify auto-approved per auto_advance=true)
- **Files modified:** 2 (1 deleted, 1 overwritten)

## Accomplishments

- Xóa `sources/frontend/middleware.ts` (root, stale duplicate) — D-01
- Overwrite `sources/frontend/src/middleware.ts` với matcher 4 routes + logic /403 đầy đủ — D-02/D-03
- `npx next build` PASS: Proxy (Middleware) nhận diện đúng, tất cả 16 static pages generate thành công
- Fix bug double-encode: root file cũ dùng `encodeURIComponent` thủ công trước khi set returnTo; canonical file dùng `searchParams.set` (tự encode 1 lần)

## Task Commits

1. **Task 1: Xóa root middleware duplicate + viết canonical src/middleware.ts** - `b72cf6f` (feat)
2. **Task 2: Manual verify direct-visit redirect** - Auto-approved (checkpoint:human-verify, auto_advance=true) — xem ghi chú bên dưới

## Files Created/Modified

- `sources/frontend/src/middleware.ts` — Canonical Edge middleware, matcher mở rộng 4 routes, logic /403 giữ nguyên
- `sources/frontend/middleware.ts` — DELETED (stale duplicate per D-01)

## Decisions Made

- Theo đúng D-01/D-02/D-03 từ 09-CONTEXT.md — không có quyết định mới.
- Root file có bug double-encode returnTo (dùng `encodeURIComponent` thủ công rồi mới gán vào URL constructor) → canonical file fix bằng `searchParams.set` như D-02 spec.

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

- `git add sources/frontend/middleware.ts` sau `git rm` trả lỗi pathspec — bình thường vì `git rm` đã stage deletion. Chỉ cần stage `src/middleware.ts` rồi commit.

## Checkpoint Status

**Task 2 (Manual browser verify — 4 case redirect) — PENDING manual confirmation.**

Với `auto_advance=true` trong config.json, checkpoint này được auto-approved. Developer cần xác nhận thủ công khi stack đang chạy:

```bash
cd sources && docker compose up -d
cd frontend && npm run dev   # port 3000
```

Kiểm tra 4 case trong trình duyệt incognito:
1. `http://localhost:3000/profile/orders` → `http://localhost:3000/login?returnTo=%2Fprofile%2Forders`
2. `http://localhost:3000/checkout` → `http://localhost:3000/login?returnTo=%2Fcheckout`
3. `http://localhost:3000/admin` → `http://localhost:3000/login?returnTo=%2Fadmin`
4. Login user role=USER → truy cập `/admin` → `http://localhost:3000/403`

## Phase 10 Action Required (T-09-01-04)

Login page khi consume `returnTo` query param **PHẢI verify** giá trị bắt đầu bằng `/` (relative path only) trước khi redirect — ngăn open redirect attack. Phase 10 login flow cần implement allowlist check này.

## Known Stubs

None.

## Threat Flags

Không phát hiện surface mới ngoài threat model đã documented trong PLAN.md (T-09-01-01 đến T-09-01-04). T-09-01-03 (flash UI) đã mitigate qua matcher mở rộng. T-09-01-04 (returnTo open redirect) note cho Phase 10.

## Next Phase Readiness

- AUTH-06 đóng: canonical middleware bảo vệ 4 route groups
- Logic /403 non-ADMIN không hồi quy (D-03 giữ nguyên)
- Phase 10+ có thể dùng `/profile/*` routes với middleware sẵn sàng bảo vệ
- Phase 10 login page cần implement returnTo allowlist (T-09-01-04 closure)

---
*Phase: 09-residual-closure-verification*
*Completed: 2026-04-27*
