---
phase: 07-search-admin-real-data
plan: "06"
subsystem: frontend-admin
tags: [admin, users, real-api, edit-modal, delete-confirm, D-09, D-10]
dependency_graph:
  requires: ["07-03", "07-04"]
  provides: ["admin/users real API", "UserEditModal D-10", "delete confirm wired"]
  affects: ["sources/frontend/src/app/admin/users/page.tsx"]
tech_stack:
  added: []
  patterns: ["useCallback/useEffect load pattern", "overlay modal pattern", "toast feedback pattern"]
key_files:
  modified:
    - sources/frontend/src/app/admin/users/page.tsx
decisions:
  - "Roles format USER/ADMIN (không có ROLE_ prefix) — match DB format từ V2 seed + AuthService"
  - "Ẩn nút xóa cho ADMIN users — T-07-06-02 mitigation"
  - "fullName fallback username khi fullName null/empty — D-09 column spec"
metrics:
  duration: "~8 phút"
  completed: "2026-04-26"
  tasks_completed: 1
  tasks_total: 1
  files_modified: 1
---

# Phase 07 Plan 06: Admin Users Real API + UserEditModal + Delete Confirm Summary

**One-liner:** Admin /admin/users page wire real API với fullName/phone fallback columns, UserEditModal PATCH D-10, và delete confirm modal.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Admin Users page — real API + UserEditModal + delete confirm | c763593 | sources/frontend/src/app/admin/users/page.tsx |

## What Was Built

### Task 1: Admin Users page (D-09, D-10)

Refactor hoàn toàn `admin/users/page.tsx` từ stub sang real API:

**Real API integration:**
- `listAdminUsers()` thay thế `_stubUsers` array
- Loading skeleton rows (5 rows) khi đang fetch
- `RetrySection` khi fetch fail
- Empty state khi không có users

**D-09 Column mapping:**
- Cột Họ tên: `u.fullName && u.fullName.trim() ? u.fullName : u.username` (fallback username)
- Cột Điện thoại: `u.phone && u.phone.trim() ? u.phone : '—'` (fallback dash)
- Cột Vai trò: `u.roles === 'ADMIN' ? 'hot' badge 'Admin' : 'default' badge 'Khách hàng'`
- Cột Ngày tạo: `u.createdAt` với fallback `'—'`

**D-10 UserEditModal:**
- Form pre-filled với fullName, phone, roles từ editTarget
- Input fullWidth cho Họ và tên + Số điện thoại
- `<select>` cho Vai trò (USER → Khách hàng, ADMIN → Quản trị viên)
- Submit → `patchAdminUser(editTarget.id, editForm)` → toast success → reload
- Error toast khi PATCH fail

**Delete confirm modal:**
- Chỉ hiện nút 🗑️ khi `u.roles !== 'ADMIN'` (T-07-06-02 mitigation)
- Confirm modal → `deleteAdminUser(deleteTarget)` → toast success → reload
- Error toast khi DELETE fail

**Toast messages theo UI-SPEC:**
- Edit success: "Thông tin tài khoản đã được cập nhật"
- Edit error: "Không thể cập nhật tài khoản. Vui lòng thử lại"
- Delete success: "Tài khoản đã được xóa"
- Delete error: "Không thể xóa tài khoản. Vui lòng thử lại"

## Key Decisions

1. **Roles format: `USER`/`ADMIN` không có `ROLE_` prefix** — Match thực tế DB format (V2 seed: `roles='ADMIN'`, AuthService.register() gọi `UserEntity.create(..., "USER")`). UI-SPEC dùng `ROLE_ADMIN`/`ROLE_CUSTOMER` nhưng thực tế cần khớp DB.

2. **Delete ẩn cho ADMIN** — Threat T-07-06-02: `u.roles !== 'ADMIN'` guard trên delete button. Không thể xóa admin account qua UI.

3. **empty string → undefined trong PATCH body** — `editForm.fullName || undefined` pattern để tránh gửi empty string update field thành rỗng khi user không muốn thay đổi.

## TypeScript Compile Status

Chạy `npx tsc --noEmit 2>&1 | grep "admin/users"` → 0 errors.

## Deviations from Plan

Không có — plan executed exactly as written.

## Known Stubs

Không có stubs — page wire real API hoàn toàn.

## Threat Flags

Không có surface mới ngoài plan's threat model.

## Self-Check: PASSED

- [x] `sources/frontend/src/app/admin/users/page.tsx` tồn tại và có 188 lines mới
- [x] Commit c763593 tồn tại
- [x] `_stubUsers` đã xóa (0 matches)
- [x] `listAdminUsers` + `patchAdminUser` + `deleteAdminUser` đều present (4 matches)
- [x] `editTarget` state + modal condition + handler (13 matches)
- [x] `u.fullName` fallback present (1 match)
- [x] `RetrySection` present
- [x] `roles !== 'ADMIN'` guard present
- [x] Toast messages đúng per UI-SPEC
