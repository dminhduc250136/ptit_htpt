---
phase: 11-address-book-order-history-filtering
plan: "06"
subsystem: ui
tags: [nextjs, react, address-book, order-filter, url-state, snap-fill]

# Dependency graph
requires:
  - phase: 11-address-book-order-history-filtering
    plan: "04"
    provides: SavedAddress type, listAddresses/createAddress/updateAddress/deleteAddress/setDefaultAddress services, listMyOrders filter params
  - phase: 11-address-book-order-history-filtering
    plan: "05"
    provides: AddressCard, AddressForm, AddressPicker, OrderFilterBar UI components

provides:
  - /profile/addresses page — full address book CRUD (list, create modal, edit modal, delete confirm, set-default)
  - /profile/orders page — standalone order history page với OrderFilterBar + URL-encoded filter state
  - profile/page.tsx tab redirects — tab 'orders'/'addresses' → router.push dedicated pages
  - checkout/page.tsx AddressPicker integration — snap-fill 6 fields khi chọn saved address

affects:
  - phase-12-checkout-enhancements
  - phase-10-profile-settings (profile hub routing)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - URL filter state pattern: useSearchParams() read + router.push URLSearchParams write
    - Silent fail pattern: fetch error → hide UI element (không toast), alive flag cleanup
    - Custom overlay modal cho form components (tránh double-submit với Modal component)
    - Snap-fill pattern: onSelect callback → setForm merge với address fields

key-files:
  created:
    - sources/frontend/src/app/profile/addresses/page.tsx
    - sources/frontend/src/app/profile/addresses/page.module.css
    - sources/frontend/src/app/profile/orders/page.tsx
    - sources/frontend/src/app/profile/orders/page.module.css
  modified:
    - sources/frontend/src/app/profile/page.tsx
    - sources/frontend/src/app/checkout/page.tsx
    - sources/frontend/src/app/checkout/page.module.css

key-decisions:
  - "Dùng custom overlay modal (không dùng Modal component) cho create/edit address vì AddressForm tự quản lý submit button — tránh double nút submit"
  - "Silent fail cho AddressPicker fetch: catch → setPickerVisible(false), không toast — per plan spec"
  - "Giữ lại activeTab render blocks trong profile/page.tsx — tab redirect không xóa chúng để tránh rework lớn"
  - "useCallback cho handleFilterChange trong orders page để OrderFilterBar debounce effect không re-run vô hạn"

requirements-completed: [ACCT-02, ACCT-05, ACCT-06]

# Metrics
duration: 25min
completed: 2026-04-27
---

# Phase 11 Plan 06: Wire Pages — Address Book CRUD + Order History Filter + Checkout AddressPicker

**4 pages/files wire UI components vào API thật: /profile/addresses CRUD modal flow, /profile/orders với URL filter state, profile hub redirects, và checkout snap-fill từ saved addresses**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-04-27T10:00:00Z
- **Completed:** 2026-04-27T10:25:00Z
- **Tasks:** 2 (Task 3 là checkpoint:human-verify — dừng tại đây)
- **Files modified:** 7

## Accomplishments

- /profile/addresses/page.tsx: full CRUD — list addresses, create/edit custom overlay modal với AddressForm, delete confirm dùng Modal component, set-default với loading state per card, disabled button khi >= 10 địa chỉ
- /profile/orders/page.tsx: standalone page với OrderFilterBar sticky, URL state qua useSearchParams + router.push, fetch re-triggers khi searchParams thay đổi
- profile/page.tsx: tab 'orders' + 'addresses' onClick thay bằng router.push đến dedicated pages
- checkout/page.tsx: AddressPicker fetch khi user login, alive flag cleanup, silent fail (pickerVisible=false), snap-fill 6 fields qua handleAddressSelect

## Task Commits

1. **Task 1: /profile/addresses CRUD page + /profile/orders standalone filter page** - `5eac6c5` (feat)
2. **Task 2: Profile tab redirects + checkout AddressPicker snap-fill** - `35dfcdc` (feat)
3. **Fix: Xóa import useToast unused trong orders page** - `35eb736` (fix)

## Files Created/Modified

- `sources/frontend/src/app/profile/addresses/page.tsx` — Address book CRUD page với 3 modal states, set-default, limit 10
- `sources/frontend/src/app/profile/addresses/page.module.css` — Layout sidebar+main, custom modal overlay, empty state
- `sources/frontend/src/app/profile/orders/page.tsx` — Standalone orders page với OrderFilterBar, useSearchParams URL state
- `sources/frontend/src/app/profile/orders/page.module.css` — Sticky filter bar, order card list, empty state
- `sources/frontend/src/app/profile/page.tsx` — Tab 'orders'/'addresses' onClick → router.push (D-09)
- `sources/frontend/src/app/checkout/page.tsx` — AddressPicker integration, silent fail fetch, snap-fill 6 fields
- `sources/frontend/src/app/checkout/page.module.css` — Thêm .pickerDivider style

## Decisions Made

- **Custom overlay modal cho address create/edit:** Modal component luôn render primaryAction button; AddressForm cũng có nút submit riêng → sẽ có 2 nút. Giải pháp: dùng custom `.modalOverlay/.modal` overlay (pattern sẵn trong profile/page.module.css) cho create/edit. Modal component chỉ dùng cho delete confirm (không có form).
- **Silent fail cho AddressPicker:** Khi listAddresses() fail, pickerVisible=false — ẩn picker hoàn toàn, không hiện toast lỗi. User vẫn có thể điền form thủ công.
- **Giữ activeTab render blocks:** Không xóa `{activeTab === 'orders' && ...}` và `{activeTab === 'addresses' && ...}` trong profile/page.tsx — chúng sẽ không được trigger (tab redirect rồi) nhưng giữ lại tránh rework.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Xóa import useToast unused trong /profile/orders/page.tsx**
- **Found during:** Task 1 (kiểm tra TypeScript lint)
- **Issue:** Import `useToast` được thêm vào nhưng không dùng (orders page không cần toast — errors chỉ trigger RetrySection)
- **Fix:** Xóa dòng `import { useToast } from '@/components/ui/Toast/Toast'` và destructure `_showToast`
- **Files modified:** sources/frontend/src/app/profile/orders/page.tsx
- **Committed in:** `35eb736`

---

**Total deviations:** 1 auto-fixed (1 unused import cleanup)
**Impact on plan:** Không ảnh hưởng logic, chỉ clean up để tránh TypeScript unused warning.

## Issues Encountered

- node_modules chưa được cài trong worktree nên không thể chạy `tsc --noEmit` locally. TypeScript check sẽ được thực hiện tại checkpoint Task 3 (human-verify).

## Known Stubs

Không có stubs. Tất cả data wiring đều gọi API thật:
- listAddresses() → /api/users/me/addresses
- createAddress/updateAddress/deleteAddress/setDefaultAddress → /api/users/me/addresses/*
- listMyOrders() với filter params → /api/orders/orders
- AddressPicker dùng savedAddresses từ listAddresses()

## Threat Flags

Không có surface mới ngoài threat model đã định nghĩa trong plan (T-11-06-01 đến T-11-06-04). Tất cả /profile/* đã được bảo vệ bởi middleware.ts từ Phase 9.

## User Setup Required

None - không cần cấu hình external service.

## Next Phase Readiness

- Phase 11 hoàn thành 6/6 plans. Tất cả 5 Success Criteria (SC-1..SC-5) đã được deliver.
- Checkpoint Task 3 cần: `tsc --noEmit` + `npm run build` pass + visual spot-check 8 bước.
- Sau khi user approve checkpoint, Phase 11 kết thúc.

---
*Phase: 11-address-book-order-history-filtering*
*Completed: 2026-04-27*
