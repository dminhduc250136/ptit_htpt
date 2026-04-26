---
phase: 06-real-auth-flow
plan: "03"
subsystem: auth
tags: [typescript, nextjs, middleware, login, register, 403, route-protection]

requires:
  - phase: 06-real-auth-flow
    plan: "01"
    provides: POST /auth/register + POST /auth/login backend endpoints
  - phase: 06-real-auth-flow
    plan: "02"
    provides: services/auth.login() + services/auth.register() + token.ts setUserRole() + user_role cookie

provides:
  - login/page.tsx wired với real auth.login() — 401 → Banner form-level
  - register/page.tsx refactored (username field, không có fullName/phone) + wire auth.register() + auto-login
  - middleware.ts matcher thêm /account/:path* + admin role check → redirect /403
  - app/403/page.tsx — Forbidden page per UI-SPEC (copy chính xác)
  - app/403/page.module.css — centered layout, max-width 440px, --surface background

affects:
  - Phase 7 admin pages (middleware admin check đã active)
  - Toàn bộ /account/* routes (protected từ plan này)

tech-stack:
  added: []
  patterns:
    - "Wire pattern: import { login } from '@/services/auth' + catch ApiError → Banner children (không message prop)"
    - "409 conflict discriminate: err.message.includes('username') vs 'email' → field error vs Banner"
    - "middleware admin check: req.nextUrl.pathname.startsWith('/admin') + user_role cookie includes('ADMIN')"

key-files:
  created:
    - sources/frontend/src/app/403/page.tsx
    - sources/frontend/src/app/403/page.module.css
  modified:
    - sources/frontend/src/app/login/page.tsx
    - sources/frontend/src/app/register/page.tsx
    - sources/frontend/middleware.ts

key-decisions:
  - "Banner dùng children (không có message prop) — interface hiện tại của Banner.tsx chỉ nhận children + count + tone"
  - "login 401 → Banner count=1 form-level (không highlight field) per UI-SPEC"
  - "register 409: discriminate bằng err.message.toLowerCase().includes('username'/'email') → set field error"
  - "middleware admin check dùng user_role cookie (non-httpOnly) — Edge Runtime không verify JWT (D-08)"

duration: 12min
completed: "2026-04-26"
---

# Phase 06 Plan 03: FE Pages Wire + Middleware + /403 Page Summary

**Login/register pages gỡ mock và wire real auth services; middleware thêm /account/* + admin role check → /403; tạo mới Forbidden page per UI-SPEC**

## Performance

- **Duration:** ~12 min
- **Started:** 2026-04-26
- **Completed:** 2026-04-26
- **Tasks:** 2/2
- **Files modified:** 3 (modified) + 2 (created)

## Accomplishments

- `login/page.tsx`: xóa mock setTimeout + setTokens mock, wire `login()` từ services/auth, 401 → Banner form-level với copy "Email hoặc mật khẩu không chính xác. Vui lòng thử lại"
- `register/page.tsx`: refactor form state từ `{fullName, email, phone, ...}` → `{username, email, ...}`, wire `register()` + auto-login D-04, redirect về `/` sau success, 409 → field error cụ thể per UI-SPEC
- `middleware.ts`: thêm `/account/:path*` vào matcher (D-07), thêm admin check đọc `user_role` cookie → redirect `/403` cho non-ADMIN (D-08/D-09)
- `app/403/page.tsx`: ForbiddenPage với h1 "Không có quyền truy cập", p "Bạn không có quyền xem trang này.", Button "Về trang chủ"
- `app/403/page.module.css`: centered full viewport, max-width 440px, `--surface` background (không gradient), dùng đúng CSS tokens từ globals.css

## Task Commits

1. **Task 1: Wire login/register pages** - `dff2be7` (feat)
2. **Task 2: middleware + /403 page** - `3761675` (feat)

## Files Created/Modified

- `sources/frontend/src/app/login/page.tsx` — xóa mock, import login/ApiError, apiError state, Banner(children)
- `sources/frontend/src/app/register/page.tsx` — form state refactor (username), xóa fullName/phone JSX, wire register(), 409 discriminate
- `sources/frontend/middleware.ts` — matcher 4 entries, admin role check
- `sources/frontend/src/app/403/page.tsx` — tạo mới, copy per UI-SPEC
- `sources/frontend/src/app/403/page.module.css` — tạo mới, CSS tokens validated

## Decisions Made

- **Banner interface**: `Banner.tsx` chỉ nhận `children` (không có `message` prop) → dùng `<Banner count={1}>{apiError}</Banner>` thay vì `<Banner message={apiError} />`
- **returnTo hardening**: giữ nguyên guard `startsWith('/') && !startsWith('//')` trong login page (T-04-03 không được xóa khi refactor)
- **409 discriminate logic**: dùng `err.message.toLowerCase().includes('username'/'email')` vì backend trả message string chứa field name trong 409 response

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Banner không có message prop**
- **Found during:** Task 1
- **Issue:** Plan chỉ định `<Banner count={1} message={apiError} />` nhưng Banner component chỉ nhận `children`, `count`, `tone` — không có `message` prop
- **Fix:** Dùng `<Banner count={1}>{apiError}</Banner>` (children pattern)
- **Files modified:** `login/page.tsx`, `register/page.tsx`
- **Commit:** `dff2be7`

## Known Stubs

Không có stubs — tất cả call sites wire real services từ Plan 01/02.

## Threat Flags

Không có surface mới ngoài plan's threat_model.

- T-06-10 (user_role cookie forgeable): Accepted MVP — middleware chỉ làm UX redirect, backend validate JWT mọi API call
- T-06-11 (returnTo open redirect): Giữ nguyên guard trong login page khi refactor
- T-06-12 (admin elevation): middleware redirect non-ADMIN → /403 implemented

## Self-Check

- [x] `sources/frontend/src/app/login/page.tsx` — modified, committed `dff2be7`
- [x] `sources/frontend/src/app/register/page.tsx` — modified, committed `dff2be7`
- [x] `sources/frontend/middleware.ts` — modified, committed `3761675`
- [x] `sources/frontend/src/app/403/page.tsx` — created, committed `3761675`
- [x] `sources/frontend/src/app/403/page.module.css` — created, committed `3761675`
- [x] `npx tsc --noEmit` — chỉ có pre-existing e2e/uat.spec.ts errors (ghi nhận từ Phase 4), không có lỗi mới

## Self-Check: PASSED

---
*Phase: 06-real-auth-flow*
*Completed: 2026-04-26*
