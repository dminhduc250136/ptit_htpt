---
phase: 10-user-svc-schema-profile-editing
plan: "03"
subsystem: ui
tags: [frontend, profile, react-hook-form, zod, hookform-resolvers, profile-editing, ACCT-03]

# Dependency graph
requires:
  - phase: 10-user-svc-schema-profile-editing
    plan: "01"
    provides: "GET/PATCH /api/users/me backend endpoints + UserDto.hasAvatar"
  - phase: 10-user-svc-schema-profile-editing
    plan: "02"
    provides: "react-hook-form + zod + @hookform/resolvers installed"

provides:
  - "getMe() + patchMe() service functions trong services/users.ts"
  - "UpdateMeBody interface trong services/users.ts"
  - "User type extended với hasAvatar?: boolean trong types/index.ts"
  - "/profile/settings page với 3 sections: Profile Info / Avatar / Security"
  - "rhf+zod form pattern — first instance trong codebase (ACCT-03)"
  - "Navbar fullName sync sau save via useAuth().login()"

affects:
  - "future-phases-using-rhf-zod-pattern"
  - "future-phases-wiring-avatar-upload (ACCT-04)"

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "rhf+zod: useForm<T> + zodResolver(schema) — first usage instance trong codebase"
    - "Refinement D-07: useAuth().login(updatedUser) thay manual localStorage + router.refresh() — AuthProvider đã handle write + cross-tab"
    - "useEffect cleanup pattern: alive flag để tránh setState sau unmount"
    - "patchMe phone-clear contract: empty string → undefined → backend skip field"

key-files:
  created: []
  modified:
    - "sources/frontend/src/types/index.ts — User interface thêm hasAvatar?: boolean"
    - "sources/frontend/src/services/users.ts — thêm UpdateMeBody, getMe(), patchMe()"
    - "sources/frontend/src/app/profile/settings/page.tsx — extend với Profile Info + Avatar sections, giữ Security (Phase 9)"
    - "sources/frontend/src/app/profile/settings/page.module.css — thêm .avatarPlaceholder, .comingSoon, .readonly"

key-decisions:
  - "Refinement D-07: useAuth().login() thay manual localStorage+router.refresh() — đơn giản hơn, tái dụng AuthProvider đã có sẵn"
  - "handleSubmit rename → rhfHandleSubmit để tránh duplicate identifier với password form handler (Phase 9)"
  - "Avatar initials dùng profileEmail.charAt(0) thay vì fullName — email luôn có sau getMe(), fullName có thể empty"
  - "Phone regex /^\\+?[0-9\\s-]{7,20}$/ (VN-loose) đồng bộ với backend Plan 10-01 @Pattern"
  - "ACCT-04 (avatar upload) defer per D-08 — Avatar section là static placeholder không có file input"

# Metrics
duration: 20min
completed: "2026-04-27"
---

# Phase 10 Plan 03: Frontend Profile Settings Summary

**Profile Info form dùng rhf+zod tại /profile/settings — getMe/patchMe services, navbar sync qua useAuth().login(), Avatar placeholder, Security section (Phase 9) intact**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-04-27T04:10:00Z
- **Completed:** 2026-04-27T04:30:00Z
- **Tasks:** 2/2
- **Files modified:** 4

## Accomplishments

- Thêm `getMe()` + `patchMe()` + `UpdateMeBody` vào `services/users.ts` theo pattern analog `changeMyPassword`
- Extend `User` interface trong `types/index.ts` với `hasAvatar?: boolean` (optional để compat callers hiện tại)
- Build Profile Info section dùng `useForm<ProfileFormData>` + `zodResolver(profileSchema)` — **first rhf+zod usage trong codebase**
- Zod schema: `fullName` trim/min1/max120 + `phone` regex `^\+?[0-9\s-]{7,20}$` or empty string
- `useEffect` mount hydrate form via `getMe()`, cleanup alive flag
- `onSubmitProfile`: `patchMe()` → `useAuth().login({ ...user, name: updated.fullName })` → `showToast('Đã cập nhật')` → `reset()`
- 400 fieldErrors từ backend → `setError(fe.field, { message })` per field
- Avatar section: initials circle (chữ đầu email) + "Tính năng tải ảnh đại diện sẽ có trong bản cập nhật sau." (ACCT-04 defer per D-08)
- Security section (Phase 9): oldPassword/newPassword/changeMyPassword intact
- `tsc --noEmit` exit 0, `npm run build` 17/17 pages pass

## Task Commits

1. **Task 1: Thêm getMe/patchMe service + extend User type với hasAvatar** - `851e835` (feat)
2. **Task 2: Build Profile Info section (rhf+zod) + Avatar placeholder + navbar sync** - `cfc116c` (feat)

## Files Created/Modified

- `src/types/index.ts` — Thêm `hasAvatar?: boolean` vào User interface (Phase 10 stub, luôn false từ backend)
- `src/services/users.ts` — Thêm `UpdateMeBody`, `getMe()`, `patchMe()` sau `changeMyPassword` block
- `src/app/profile/settings/page.tsx` — Extend với 3 sections (Profile Info / Avatar / Security), rhf+zod form, navbar sync
- `src/app/profile/settings/page.module.css` — Thêm `.avatarPlaceholder`, `.comingSoon`, `.readonly`

## Decisions Made

- **Refinement D-07**: `useAuth().login(updatedUser)` thay `manual localStorage + router.refresh()` — AuthProvider đã viết `localStorage 'userProfile'` + cross-tab storage event listener, tái dụng tốt hơn. Spirit D-07 không vi phạm (vẫn không tạo AuthContext mới).
- **handleSubmit rename**: rhf destructure `handleSubmit` trùng với password form handler — đổi thành `rhfHandleSubmit` để tránh duplicate identifier TS error.
- **Phone-clear contract**: `data.phone || undefined` — empty string → `undefined` → backend skip field (không overwrite với empty).
- **Avatar initials từ email** thay fullName: email luôn available sau `getMe()`, fullName có thể empty string.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Duplicate identifier `handleSubmit`**
- **Found during:** Task 2 — tsc compile
- **Issue:** rhf `useForm` destructure `handleSubmit` trùng tên với Phase 9 password form `async function handleSubmit(e: FormEvent)` → TS2300 duplicate identifier
- **Fix:** Rename rhf destructure thành `rhfHandleSubmit`, update `onSubmitProfile = rhfHandleSubmit(...)` với explicit type `data: ProfileFormData`
- **Files modified:** `src/app/profile/settings/page.tsx`
- **Commit:** `cfc116c`

## Known Stubs

- `hasAvatar` trong `User` type là `optional boolean` — backend Plan 10-01 luôn trả `false` (UserMapper.toDto stub). Phase 10 hiển thị initials placeholder thay avatar image. Phase 12+ wire real value khi ACCT-04 implement.
- Avatar section hoàn toàn static — không có upload form, không có img src. Stub có chủ ý per D-08.

## Threat Flags

Không có surface mới — page.tsx là 'use client' component gọi services đã có, không expose endpoint mới.

## Self-Check: PASSED

- FOUND: sources/frontend/src/types/index.ts (hasAvatar field)
- FOUND: sources/frontend/src/services/users.ts (getMe, patchMe, UpdateMeBody)
- FOUND: sources/frontend/src/app/profile/settings/page.tsx (Profile Info + Avatar + Security)
- FOUND: sources/frontend/src/app/profile/settings/page.module.css (.avatarPlaceholder, .comingSoon, .readonly)
- FOUND: commit 851e835 (feat(10-03): thêm getMe/patchMe service + hasAvatar vào User type)
- FOUND: commit cfc116c (feat(10-03): build Profile Info form (rhf+zod) + Avatar placeholder + navbar sync)

---
*Phase: 10-user-svc-schema-profile-editing*
*Completed: 2026-04-27*
