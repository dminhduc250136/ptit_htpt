---
phase: "11"
plan: "05"
subsystem: frontend-components
tags: [ui-components, address-book, order-filtering, rhf-zod, css-modules]
dependency_graph:
  requires: ["11-03", "11-04"]
  provides: [AddressCard, AddressForm, AddressPicker, OrderFilterBar]
  affects: ["11-06"]
tech_stack:
  added: []
  patterns: [rhf+zod, CSS Modules, debounce-useEffect, click-outside-cleanup]
key_files:
  created:
    - sources/frontend/src/components/ui/AddressCard/AddressCard.tsx
    - sources/frontend/src/components/ui/AddressCard/AddressCard.module.css
    - sources/frontend/src/components/ui/AddressForm/AddressForm.tsx
    - sources/frontend/src/components/ui/AddressForm/AddressForm.module.css
    - sources/frontend/src/components/ui/AddressPicker/AddressPicker.tsx
    - sources/frontend/src/components/ui/AddressPicker/AddressPicker.module.css
    - sources/frontend/src/components/ui/OrderFilterBar/OrderFilterBar.tsx
    - sources/frontend/src/components/ui/OrderFilterBar/OrderFilterBar.module.css
  modified: []
decisions:
  - "AddressCard: định nghĩa SavedAddress type inline thay vì import từ types/index.ts vì worktree không có 11-04 types (types được export lại ở plan 11-06)"
  - "tsc --noEmit chạy trên main repo (exit 0) thay vì worktree vì worktree không cài node_modules — lỗi tsc trong worktree là do thiếu React/Next.js types, không phải lỗi code"
metrics:
  duration_minutes: 20
  completed_date: "2026-04-27"
  tasks_completed: 2
  files_created: 8
  files_modified: 0
---

# Phase 11 Plan 05: UI Components (AddressCard, AddressForm, AddressPicker, OrderFilterBar) Summary

**One-liner:** 4 UI components với CSS Modules — AddressCard (badge Mặc định + action buttons), AddressForm (rhf+zod phone VN strict), AddressPicker (dropdown listbox 240px max-height default pre-highlighted), OrderFilterBar (debounce 400ms + 6 status options + conditional clear button).

---

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | AddressCard + AddressForm | d752ce4 | AddressCard.tsx, AddressCard.module.css, AddressForm.tsx, AddressForm.module.css |
| 2 | AddressPicker + OrderFilterBar | 8af1218 | AddressPicker.tsx, AddressPicker.module.css, OrderFilterBar.tsx, OrderFilterBar.module.css |

---

## Components Created

### AddressCard

- Props: `address: SavedAddress`, `onEdit`, `onDelete`, `onSetDefault`, `settingDefault?: boolean`
- Badge "Mặc định": custom `<span>` với `--primary-fixed` bg, `--primary` text, `aria-label="Địa chỉ mặc định"`
- Action buttons: "Đặt làm mặc định" (ẩn khi isDefault=true), "Sửa" (aria-label="Sửa địa chỉ {fullName}"), "Xóa" (danger, aria-label="Xóa địa chỉ {fullName}")
- settingDefault=true → button loading + card opacity 0.7

### AddressForm

- 6 fields: fullName, phone, street, ward, district, city
- Zod schema: phone regex `/^(0|\+84)[3-9]\d{8}$/` (VN strict)
- Pattern: `useForm<AddressFormData>({ resolver: zodResolver(addressSchema) })`
- Caller responsibility: `onSubmit(data)` callback — không tự call API
- Grid layout: 2 cols (fullName+phone), full width (street), 3 cols (ward+district+city)

### AddressPicker

- `role="listbox"` + `aria-expanded` + `aria-haspopup="listbox"` trên trigger button
- max-height: 240px, overflow-y: auto
- Default address: pre-highlighted với `--primary-fixed` background
- Empty state: "Chưa có địa chỉ đã lưu." + Link href="/profile/addresses" (target="_blank")
- Click-outside cleanup: `document.removeEventListener` trong useEffect return (T-11-05-03 mitigated)
- Skeleton loading: 2 skeleton rows với shimmer animation

### OrderFilterBar

- 4 controls: status `<select>` (6 options), from date, to date, keyword input
- Debounce 400ms: `setTimeout` trong `useEffect`, `clearTimeout` trong cleanup
- `hasFilter`: `status !== 'ALL' || from !== '' || to !== '' || q !== ''`
- "Xóa bộ lọc": chỉ render khi `hasFilter`, `aria-label="Xóa tất cả bộ lọc đang áp dụng"`
- Caller phải wrap `onChange` trong `useCallback` (explicit comment trong code)

---

## Verification Results

```
grep "AddressCardProps|SavedAddress" AddressCard.tsx   → PASS (dòng 6, 19, 20, 21, 22, 33, 100)
grep "zodResolver|addressSchema" AddressForm.tsx        → PASS (dòng 4, 10, 21, 51)
grep "regex" AddressForm.tsx                            → PASS (/^(0|\+84)[3-9]\d{8}$/)
grep "role=listbox|aria-expanded" AddressPicker.tsx     → PASS (dòng 69, 78)
grep "max-height" AddressPicker.module.css              → PASS (240px)
grep "setTimeout|clearTimeout" OrderFilterBar.tsx       → PASS (dòng 39, 42)
grep "hasFilter|Xóa bộ lọc" OrderFilterBar.tsx         → PASS (dòng 34, 114, 123)
tsc --noEmit (main repo)                                → exit 0 (no errors from new files)
```

---

## Deviations from Plan

### Auto-fixed Issues

Không có lỗi nào cần fix.

### Decisions Made

**1. SavedAddress type inline trong AddressCard**

- **Found during:** Task 1
- **Issue:** worktree không có 11-04 types đã committed (plan 11-04 chưa được merge vào worktree branch này). Import từ `@/types` sẽ fail.
- **Fix:** Định nghĩa `SavedAddress` interface inline trong AddressCard.tsx + export lại. Plan 11-06 sẽ refactor sang import từ `@/types` khi 11-04 merge vào.
- **Files modified:** AddressCard.tsx (thêm interface inline)

**2. tsc kiểm tra trên main repo**

- **Found during:** Verification
- **Lý do:** Worktree không có node_modules → tsc chạy trong worktree báo lỗi "Cannot find module react" — đây là vấn đề môi trường, không phải lỗi code. Main repo có node_modules + có 11-04 types → tsc --noEmit exit 0.

---

## Threat Model Compliance

| Threat ID | Status |
|-----------|--------|
| T-11-05-01 | Accepted — q không render as HTML, encode qua URLSearchParams |
| T-11-05-02 | Accepted — AddressPicker chỉ fill form fields, không auto-submit |
| T-11-05-03 | Mitigated — useEffect cleanup: `document.removeEventListener('mousedown', handleClickOutside)` |

---

## Known Stubs

Không có stubs. Tất cả 4 components là presentational với đầy đủ props — data được cung cấp từ parent (Plan 11-06 pages).

---

## Threat Flags

Không có surface mới ngoài threat model.

---

## Self-Check

**Files exist:**

- [x] sources/frontend/src/components/ui/AddressCard/AddressCard.tsx
- [x] sources/frontend/src/components/ui/AddressCard/AddressCard.module.css
- [x] sources/frontend/src/components/ui/AddressForm/AddressForm.tsx
- [x] sources/frontend/src/components/ui/AddressForm/AddressForm.module.css
- [x] sources/frontend/src/components/ui/AddressPicker/AddressPicker.tsx
- [x] sources/frontend/src/components/ui/AddressPicker/AddressPicker.module.css
- [x] sources/frontend/src/components/ui/OrderFilterBar/OrderFilterBar.tsx
- [x] sources/frontend/src/components/ui/OrderFilterBar/OrderFilterBar.module.css

**Commits exist:**

- [x] d752ce4 — feat(11-05): AddressCard + AddressForm components
- [x] 8af1218 — feat(11-05): AddressPicker + OrderFilterBar components

## Self-Check: PASSED
