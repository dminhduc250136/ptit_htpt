---
phase: 11-address-book-order-history-filtering
plan: "04"
subsystem: ui
tags: [typescript, nextjs, address-book, order-filtering, services]

# Dependency graph
requires:
  - phase: 11-address-book-order-history-filtering/11-03
    provides: backend address CRUD endpoints + order filter query params trên order-service
provides:
  - "SavedAddress interface (10 fields) export từ src/types/index.ts"
  - "AddressBody interface (6+1 fields) export từ src/types/index.ts"
  - "5 address CRUD service functions trong services/users.ts (listAddresses, createAddress, updateAddress, deleteAddress, setDefaultAddress)"
  - "ListOrdersParams extended với 4 filter fields (status, from, to, q)"
  - "listMyOrders() backward-compat với filter params (status='ALL' skip, others set)"
affects:
  - "11-05 (AddressCard + AddressPicker components — import SavedAddress, AddressBody, listAddresses, createAddress, updateAddress, deleteAddress, setDefaultAddress)"
  - "11-06 (profile pages — import listMyOrders với filter params, listAddresses)"

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "encodeURIComponent(id) pattern cho address CRUD mutations (updateAddress, deleteAddress, setDefaultAddress)"
    - "status='ALL' skip pattern — không set status param khi 'ALL' để backend interpret là tất cả"
    - "URLSearchParams stacking pattern — filter params stack sau pagination params"

key-files:
  created: []
  modified:
    - "sources/frontend/src/types/index.ts"
    - "sources/frontend/src/services/users.ts"
    - "sources/frontend/src/services/orders.ts"

key-decisions:
  - "SavedAddress tách biệt khỏi Address — có id, userId, fullName, phone, isDefault; Address chỉ là checkout snapshot"
  - "status='ALL' → không set URLSearchParam (backend interpret vắng mặt = tất cả trạng thái)"
  - "encodeURIComponent(id) cho tất cả address mutations để handle UUIDs đặc biệt an toàn"
  - "import SavedAddress+AddressBody vào users.ts qua @/types (không duplicate import)"

patterns-established:
  - "Pattern: Address CRUD — 5 functions (list/create/update/delete/setDefault) theo endpoint /api/users/me/addresses/**"
  - "Pattern: Filter params stacking — optional params thêm vào sau pagination params trong URLSearchParams"

requirements-completed: [ACCT-02, ACCT-05, ACCT-06]

# Metrics
duration: 15min
completed: 2026-04-27
---

# Phase 11 Plan 04: Types + Services Foundation Summary

**SavedAddress type + 5 address CRUD service functions + listMyOrders() extended với status/from/to/q filter params — foundation type-level cho toàn bộ frontend Phase 11**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-04-27T07:17:00Z
- **Completed:** 2026-04-27T07:32:30Z
- **Tasks:** 2/2
- **Files modified:** 3

## Accomplishments

- Thêm `SavedAddress` (10 fields) + `AddressBody` (6+1 fields) vào `types/index.ts` — foundation type cho address book
- Export 5 address CRUD functions từ `services/users.ts`: listAddresses, createAddress, updateAddress, deleteAddress, setDefaultAddress — tất cả gọi đúng HTTP methods và paths `/api/users/me/addresses/**`
- Extend `ListOrdersParams` + `listMyOrders()` trong `services/orders.ts` với 4 filter params backward-compat: status (skip khi 'ALL'), from, to, q

## Task Commits

1. **Task 1: SavedAddress + AddressBody types + 5 address CRUD functions** - `cce42bb` (feat)
2. **Task 2: Extend ListOrdersParams + listMyOrders() với filter params** - `5ffc876` (feat)

## Files Created/Modified

- `sources/frontend/src/types/index.ts` - Thêm SavedAddress interface (10 fields) + AddressBody interface (6+1 fields) sau Address interface
- `sources/frontend/src/services/users.ts` - Cập nhật import thêm SavedAddress+AddressBody, thêm 5 address CRUD functions
- `sources/frontend/src/services/orders.ts` - Extend ListOrdersParams với 4 filter fields, extend listMyOrders() với 4 qs.set() calls

## Decisions Made

- `SavedAddress` tách biệt hoàn toàn khỏi `Address` interface — `Address` là checkout snapshot không có id/userId/isDefault; `SavedAddress` là persisted entry với đầy đủ metadata
- `status === 'ALL'` → không set URLSearchParam (vắng mặt param = tất cả, tránh backend phải handle 'ALL' string)
- `encodeURIComponent(id)` cho tất cả mutations address để an toàn với UUID format
- Import `SavedAddress`, `AddressBody` vào `users.ts` từ `@/types` (cùng import block) — không separate import statement

## Deviations from Plan

None - plan executed exactly as written. TypeScript compile check không thể chạy trong worktree (node_modules chưa được install) nhưng code syntax đã được verify thủ công theo patterns hiện có của codebase.

## Issues Encountered

- `npx tsc --noEmit` không khả dụng trong worktree environment (node_modules không install). Verify thủ công code syntax theo patterns từ `services/http.ts` và existing code — tất cả imports, types, function signatures đều consistent với codebase conventions.

## Known Stubs

None — tất cả service functions đều có implementation thực tế gọi API endpoints. Không có hardcoded data hay placeholder.

## Threat Flags

Không phát hiện surface mới ngoài threat model. T-11-04-01 (q param injection) được handle bởi `URLSearchParams.set()` — encode URL-safe tự động. T-11-04-02 (Bearer token) được handle bởi `http.ts` interceptor.

## Next Phase Readiness

- `SavedAddress` + `AddressBody` + 5 address functions ready để Plan 11-05 import (AddressCard, AddressPicker, AddressForm components)
- `ListOrdersParams` + `listMyOrders()` filter params ready để Plan 11-06 import (profile/orders page với filter bar)
- Không có blockers

---
*Phase: 11-address-book-order-history-filtering*
*Completed: 2026-04-27*
