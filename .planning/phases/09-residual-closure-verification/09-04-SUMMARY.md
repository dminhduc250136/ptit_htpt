---
phase: 09-residual-closure-verification
plan: "04"
subsystem: frontend
tags:
  - admin-dashboard
  - kpi-cards
  - promise-allsettled
  - password-change
  - auth-07
  - ui-02
  - next-js
  - react-hooks

dependency_graph:
  requires:
    - phase: 09-02
      provides: "GET /api/{products,orders,users}/admin/stats endpoints với JwtRoleGuard"
    - phase: 09-03
      provides: "POST /api/users/me/password với BCrypt verify + 422 AUTH_INVALID_PASSWORD (field: code)"
  provides:
    - "services/stats.ts: fetchProductStats / fetchOrderStats / fetchUserStats typed wrappers"
    - "admin/page.tsx trimmed: 4 KPI cards + Promise.allSettled + per-card loading/error/retry"
    - "services/users.ts: changeMyPassword(body) → POST /api/users/me/password"
    - "profile/settings/page.tsx: form 3 fields đổi password + field-level error AUTH_INVALID_PASSWORD"
  affects:
    - "09-05 (Playwright E2E): dùng data-testid submitPassword / oldPasswordError / successMsg / formError"
    - "Phase 10 (profile editing): sẽ extend /profile/settings với fullName/phone/avatar"

tech-stack:
  added: []
  patterns:
    - "Promise.allSettled pattern: 1 endpoint fail không block N-1 cards còn lại (D-09)"
    - "Per-card CardState<T> generic type với status: loading|success|error + retry callback"
    - "isApiError() type guard để map errorCode → field-level error (thay vì cast unknown)"
    - "Inline success message thay toast lib (toast defer sang Phase 10)"
    - "Raw HTML form + manual validation (KHÔNG rhf+zod) — Phase 10 sẽ refactor"

key-files:
  created:
    - sources/frontend/src/services/stats.ts
    - sources/frontend/src/app/profile/settings/page.tsx
    - sources/frontend/src/app/profile/settings/page.module.css
  modified:
    - sources/frontend/src/app/admin/page.tsx
    - sources/frontend/src/app/admin/page.module.css
    - sources/frontend/src/services/users.ts

key-decisions:
  - "Toast pattern: dùng inline success message (p.success) thay vì toast lib — defer sang Phase 10 để tránh conflict khi Phase 10 setup foundation"
  - "Raw HTML form + manual validate trong onChange/onSubmit — KHÔNG cài rhf+zod sớm (Phase 10 sẽ refactor toàn bộ forms)"
  - "isApiError() type guard thay vì cast {status?, errorCode?} — đúng với actual ApiError shape (.code không phải .errorCode)"
  - "Backend trả field `code` (không phải `errorCode`) — confirmed từ 09-03-SUMMARY, mapping tại err.code === 'AUTH_INVALID_PASSWORD'"

patterns-established:
  - "Per-card state pattern: CardState<T> generic + useCallback loader + Promise.allSettled mount"
  - "Error code mapping: isApiError(err) && err.code === 'X' → field-level setError()"

requirements-completed:
  - UI-02
  - AUTH-07

duration: "~20 phút"
completed: "2026-04-27"
---

# Phase 9 Plan 04: Admin Dashboard KPI + Profile Settings Password Form Summary

**Admin dashboard wire 4 real KPI cards via Promise.allSettled với per-card retry; profile/settings page mới với form đổi password 3 fields và field-level error AUTH_INVALID_PASSWORD.**

## Performance

- **Duration:** ~20 phút
- **Started:** 2026-04-27
- **Completed:** 2026-04-27
- **Tasks:** 2/2 auto + 1 checkpoint (auto-approved)
- **Files modified:** 6

## Accomplishments

- Admin dashboard `/admin` không còn mock arrays — 4 KPI cards (Sản phẩm, Tổng đơn hàng, Khách hàng, Đơn chờ xử lý) với số thật từ backend stats endpoints (Plan 09-02)
- Promise.allSettled (D-09): 1 endpoint fail → 3 cards còn lại vẫn render; card lỗi hiển thị '--' + nút retry ⟳ re-fetch chỉ endpoint đó
- Profile settings page `/profile/settings` mới: form 3 fields + client-side validation + 422 AUTH_INVALID_PASSWORD → field-level error "Mật khẩu hiện tại không đúng"

## Task Commits

1. **Task 1: Service stats.ts + Trim admin/page.tsx về 4 KPI + Promise.allSettled** - `4c7ea43` (feat)
2. **Task 2: Profile settings page với password change form + service users.changeMyPassword** - `e0d12bb` (feat)
3. **Task 3: Manual verify (checkpoint:human-verify)** - auto-approved per `auto_advance=true` config

## Files Created/Modified

- `sources/frontend/src/services/stats.ts` — 3 typed wrappers: fetchProductStats/fetchOrderStats/fetchUserStats + interfaces ProductStats/OrderStats/UserStats
- `sources/frontend/src/app/admin/page.tsx` — Rewrite: 4 KPI cards, Promise.allSettled, per-card CardState<T>, KpiCard generic component, xóa mock arrays/formatPrice/totalRevenue/recent orders/quick stats
- `sources/frontend/src/app/admin/page.module.css` — Xóa grid2/card/table/statusBadge/quickStats; thêm statBody/skeleton/errorRow/retryBtn
- `sources/frontend/src/services/users.ts` — Thêm import httpPost + ChangePasswordBody interface + changeMyPassword()
- `sources/frontend/src/app/profile/settings/page.tsx` — Form 3 fields mới, validation, AUTH_INVALID_PASSWORD mapping, data-testid selectors cho Playwright
- `sources/frontend/src/app/profile/settings/page.module.css` — CSS module cho settings page

## Decisions Made

**Toast pattern (CONTEXT.md interfaces note):** Dùng inline success message (`<p role="status">Đã đổi mật khẩu</p>`) thay vì toast lib. Frontend chưa có ToastProvider (Phase 5/6 không setup). Defer sang Phase 10 khi setup profile editing foundation.

**Form pattern:** Raw HTML form + manual validate — KHÔNG cài rhf+zod sớm. Phase 9 context rõ: Phase 10 sẽ setup form foundation, Phase 9 dùng raw form tránh conflict.

**errorCode field name:** Backend Plan 09-03 trả `code: "AUTH_INVALID_PASSWORD"` (không phải `errorCode`). ApiError.code là field đúng. Dùng `isApiError(err) && err.code === 'AUTH_INVALID_PASSWORD'` — type-safe, đúng runtime shape.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Sửa field mapping errorCode → code trong settings page**
- **Found during:** Task 2 — đọc 09-03-SUMMARY
- **Issue:** Plan mô tả check `e.errorCode === 'AUTH_INVALID_PASSWORD'` nhưng backend thực tế trả field `code` (không phải `errorCode`). ApiError class cũng dùng `.code` (xem errors.ts). Cast `{errorCode?: string}` sẽ luôn undefined → không map được error.
- **Fix:** Dùng `isApiError(err) && err.code === 'AUTH_INVALID_PASSWORD'` thay vì cast shape `{errorCode?}`. Import `isApiError` từ `@/services/errors`.
- **Files modified:** `sources/frontend/src/app/profile/settings/page.tsx`
- **Verification:** grep `AUTH_INVALID_PASSWORD` + `err.code` trong file → đúng mapping
- **Committed in:** `e0d12bb`

---

**Total deviations:** 1 auto-fixed (Rule 1 — Bug: wrong field name in error mapping)
**Impact on plan:** Bắt buộc — nếu không fix, 422 response sẽ không map được thành field-level error, luôn hiển thị generic form error. Fix inline, không tăng scope.

## Issues Encountered

Không có vấn đề blocking. Build `npx next build` pass sau cả 2 tasks. Route `/profile/settings` xuất hiện trong build output.

## Toast Pattern Note

Dùng inline `<p role="status">Đã đổi mật khẩu</p>` thay toast lib (react-hot-toast / Sonner). Frontend chưa có ToastProvider. Behavior acceptable cho v1.2 demo scope. Phase 10 sẽ quyết định toast library khi setup profile editing foundation.

## Manual Verify (Task 3 — Auto-approved)

Task 3 là `checkpoint:human-verify` với `gate: blocking`. Với `auto_advance=true` trong `.planning/config.json`, checkpoint được auto-approve. Manual verify sẽ được thực hiện khi dev chạy stack:

**A. Admin dashboard `/admin`:**
- A.1: 4 cards "Sản phẩm", "Tổng đơn hàng", "Khách hàng", "Đơn chờ xử lý" với số thật từ DB
- A.2: 3 network calls tới `/api/{products,orders,users}/admin/stats` → 200
- A.3: `docker compose stop order-service` → 2 order cards hiển thị '--' + retry ⟳; 2 cards còn lại vẫn success

**B. Password form `/profile/settings`:**
- B.1..B.3: Client-side validation errors (min 8, letter, digit, confirm match)
- B.4: Sai oldPassword → 422 → field-level error "Mật khẩu hiện tại không đúng" tại oldPassword input
- B.5: Đúng oldPassword + newPassword hợp lệ → success "Đã đổi mật khẩu" + form reset, KHÔNG logout
- B.6: Re-login với password mới → thành công

Screenshots path: `e2e/screenshots/09-04-{1,2,3}.png` (dev tạo khi verify thủ công)

## Plan 09-05 Playwright Note

Selectors đã setup cho E2E tests:
- `data-testid="submitPassword"` — submit button
- `data-testid="oldPasswordError"` — field-level error tại oldPassword
- `data-testid="successMsg"` — success message
- `data-testid="formError"` — form-level error

## Known Stubs

Không có stubs — tất cả cards wire tới real endpoints (stats.ts gọi httpGet thật), form gọi changeMyPassword thật.

## Threat Flags

Không phát hiện surface mới ngoài threat model đã định nghĩa trong plan:
- T-09-04-01: KHÔNG `console.log(body)` trong service/form — verified
- T-09-04-03: JSX auto-escape, KHÔNG dùng dangerouslySetInnerHTML — verified
- T-09-04-04: Bearer token header-based auth, CSRF không áp dụng — confirmed

## Next Phase Readiness

- Plan 09-05 (Playwright): selectors đã có, có thể viết E2E tests cho admin dashboard + settings page
- Phase 10 (profile editing): `/profile/settings` sẵn sàng extend với fullName/phone/avatar fields; form sẽ được refactor sang rhf+zod khi Phase 10 setup foundation
- Admin dashboard: sẵn sàng cho bất kỳ KPI mới nào — chỉ cần thêm card vào array + backend endpoint tương ứng

## Self-Check: PASSED

Files exist:
- `sources/frontend/src/services/stats.ts` ✓
- `sources/frontend/src/app/admin/page.tsx` ✓ (rewritten)
- `sources/frontend/src/app/admin/page.module.css` ✓ (updated)
- `sources/frontend/src/services/users.ts` ✓ (changeMyPassword added)
- `sources/frontend/src/app/profile/settings/page.tsx` ✓
- `sources/frontend/src/app/profile/settings/page.module.css` ✓

Commits exist:
- `4c7ea43` ✓ (Task 1: admin dashboard KPI + stats.ts)
- `e0d12bb` ✓ (Task 2: settings page + changeMyPassword)

Build: `npx next build` exit 0 — `/profile/settings` route confirmed in build output ✓

---
*Phase: 09-residual-closure-verification*
*Completed: 2026-04-27*
