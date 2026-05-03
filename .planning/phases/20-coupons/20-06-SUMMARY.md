---
phase: 20
plan: 06
subsystem: frontend-admin-coupons-and-order-display
tags: [frontend, admin, coupon-crud, order-detail-display, vietnamese-ui, rhf, zod]
requirements: [COUP-02, COUP-05]
dependency-graph:
  requires: [20-03, 20-04]
  provides: [admin-coupon-crud-ui, order-detail-coupon-display]
  affects: [admin-sidebar-nav, types-Order, types-AdminCoupon]
tech-stack:
  added: []
  patterns: [rhf+zod-form, modal-overlay-pattern, conditional-summary-row, isApiError-error-mapping]
key-files:
  created:
    - sources/frontend/src/services/admin-coupons.ts
    - sources/frontend/src/app/admin/coupons/page.tsx
    - sources/frontend/src/app/admin/coupons/page.module.css
  modified:
    - sources/frontend/src/types/index.ts
    - sources/frontend/src/app/admin/layout.tsx
    - sources/frontend/src/app/profile/orders/[id]/page.tsx
    - sources/frontend/src/app/admin/orders/[id]/page.tsx
decisions: [D-21, D-22, D-23, D-14]
metrics:
  duration: ~25min
  tasks-completed: 3
  files-changed: 7
  commits: 3
  completed-date: 2026-05-03
---

# Phase 20 Plan 06: FE Admin Coupons + Order Detail Coupon Display Summary

One-liner: Triển khai trang admin /admin/coupons CRUD đầy đủ (rhf+zod form, list+filter, toggle active, delete với error map COUPON_HAS_REDEMPTIONS) cộng với hiển thị block coupon trên 2 trang order detail (user + admin) khi order.couponCode non-null.

## Tasks Completed

| # | Task | Commit | Files |
|---|------|--------|-------|
| 1 | Service + types + sidebar nav | `33db349` | services/admin-coupons.ts (new), types/index.ts (already touched by 20-05 — verified AdminCoupon + Order extension present), admin/layout.tsx |
| 2 | /admin/coupons CRUD page | `b27d335` | app/admin/coupons/page.tsx (new), page.module.css (new) |
| 3 | Order detail coupon block | `666d2ad` | profile/orders/[id]/page.tsx, admin/orders/[id]/page.tsx |

## Artifacts

### `sources/frontend/src/services/admin-coupons.ts`
5 typed fetchers gọi `/api/orders/admin/coupons` qua gateway (D-14):
- `listAdminCoupons(params)` → `Page<AdminCoupon>` với q + active filter
- `getAdminCoupon(id)` → single coupon
- `createCoupon(body)` → POST 201
- `updateCoupon(id, body)` → PUT
- `toggleCouponActive(id, active)` → PATCH /active
- `deleteCoupon(id)` → DELETE 204 (hoặc 409 COUPON_HAS_REDEMPTIONS)

`CouponUpsertBody` type: code, type ('PERCENT'|'FIXED'), value, minOrderAmount, maxTotalUses?, expiresAt?, active.

### `sources/frontend/src/types/index.ts`
- Confirmed `AdminCoupon` interface (mirror BE CouponDto record): id, code, type, value, minOrderAmount, maxTotalUses (number|null), usedCount, expiresAt (string|null ISO8601), active, createdAt, updatedAt.
- Confirmed `Order` extended: `discountAmount?: number`, `couponCode?: string | null` (từ Plan 20-03 OrderDto extension).
- Note: cả hai đã được commit trong cùng nhánh khi Plan 20-05 chạy song song. Verify post-commit grep confirmed presence.

### `sources/frontend/src/app/admin/layout.tsx`
Thêm `navItems` entry mới: `{ href: '/admin/coupons', label: 'Coupon', icon: <coupon SVG> }` đặt SAU 'Đơn hàng' và TRƯỚC 'Tài khoản' theo D-22. Stroke icon Lucide-style coupon/wallet glyph.

### `sources/frontend/src/app/admin/coupons/page.tsx` (488 lines)
- **List**: 8 cột — Mã / Loại / Giá trị / Đơn tối thiểu / Đã dùng-Tối đa / Hết hạn / Trạng thái / Hành động.
- **Toolbar**: Input search theo code + select active filter (Tất cả / Đang bật / Đã tắt) + count.
- **Skeleton + RetrySection + Empty state**: mirror admin/products pattern.
- **CRUD modal (rhf+zod)**:
  - `couponSchema = z.object({...}).refine(...)` với 3 refine: PERCENT cap 100%, noLimit XOR maxTotalUses, noExpiry XOR expiresAt.
  - Form fields theo D-21: code (regex `/^[A-Z0-9_-]{3,32}$/` auto-uppercase qua setValue), type radio, value với label suffix `%` hoặc `(đ)`, min order, max uses (checkbox 'Không giới hạn' toggle), expiry datetime-local (checkbox 'Không hết hạn' toggle), active checkbox.
  - `useForm<CouponFormInput, unknown, CouponFormData>` với `z.input` vs `z.output` để xử lý `z.coerce.number()` input=`unknown` output=`number` (rhf v7 + zod v4 type compat).
  - Submit: convert datetime-local → ISO8601 qua `new Date().toISOString()`; uppercase code; null nếu noLimit/noExpiry.
- **Toggle active**: PATCH /active với body `{ active: !current }` + toast "Đã bật/tắt coupon".
- **Delete confirm**: modal với error inline; nếu BE trả `COUPON_HAS_REDEMPTIONS` → message "Coupon đã có người dùng — vui lòng tắt thay vì xoá" verbatim (D-21). Reuse `formatCouponError` từ `lib/couponErrorMessages.ts` (Plan 20-05).
- **Error handling**: `isApiError(err)` typeguard → format theo `formatCouponError` hoặc fallback message.

### `sources/frontend/src/app/admin/coupons/page.module.css`
Mirror `/admin/products/page.module.css`: `tableWrapper`, `table`, `actions/actionBtn/deleteBtn`, `overlay/modal/closeBtn`, `modalForm/formRow/checkboxLine/radioGroup`, `confirmModal/confirmActions`, `emptyState`, `errorText`. CSS variables (`--space-*`, `--radius-*`, `--surface-container-lowest`) theo design system Phase 1.

### `sources/frontend/src/app/profile/orders/[id]/page.tsx`
Chèn block conditional ngay sau `(order.discount ?? 0) > 0` row và trước `.totalRow`:
```tsx
{order.couponCode && (
  <>
    <div className={styles.priceRow}>
      <span>Mã giảm giá</span>
      <span><strong>{order.couponCode}</strong></span>
    </div>
    <div className={styles.priceRow} style={{ color: 'var(--success, #10b981)' }}>
      <span>Giảm giá</span>
      <span>-{formatPrice(order.discountAmount ?? 0)}</span>
    </div>
  </>
)}
```

### `sources/frontend/src/app/admin/orders/[id]/page.tsx`
Chèn block conditional ngay trước div "Tổng cộng": tương tự nhưng dùng inline style (mirror admin pattern). Comment giải thích BE Plan 20-03 đã set totalAmount = subtotal - discount → FE không cần subtract lần nữa.

## Decisions Referenced

| ID | Source | Application |
|----|--------|-------------|
| D-14 | 20-CONTEXT.md | 5 admin endpoints + DELETE 409 COUPON_HAS_REDEMPTIONS contract |
| D-21 | 20-CONTEXT.md | Page columns + form fields (verbatim) + delete error message |
| D-22 | 20-CONTEXT.md | Sidebar nav 'Coupon' đặt sau 'Đơn hàng' |
| D-23 | 20-CONTEXT.md | Order detail block layout: 'Mã giảm giá: CODE' + 'Giảm giá: -X đ' trên Tổng cộng |

## Verification

```bash
# Typecheck
$ cd sources/frontend && npx tsc --noEmit
(no output — exit 0)

# Acceptance criteria grep checks
$ grep -c "listAdminCoupons|createCoupon|updateCoupon|toggleCouponActive|deleteCoupon" sources/frontend/src/services/admin-coupons.ts
→ 5 export functions

$ grep "interface AdminCoupon" sources/frontend/src/types/index.ts
→ line 256 ✓

$ grep "discountAmount\?:|couponCode\?:" sources/frontend/src/types/index.ts
→ Order interface lines 206, 208 ✓

$ grep "/admin/coupons" sources/frontend/src/app/admin/layout.tsx
→ line 15 ✓

$ grep "useForm\|zodResolver\|couponSchema\|COUPON_HAS_REDEMPTIONS" sources/frontend/src/app/admin/coupons/page.tsx
→ all present ✓

$ grep "Coupon đã có người dùng — vui lòng tắt thay vì xoá" sources/frontend/src/app/admin/coupons/page.tsx
→ line 213 ✓ (D-21 verbatim message)

$ grep -c "order\.couponCode\|order\.discountAmount" sources/frontend/src/app/profile/orders/\[id\]/page.tsx sources/frontend/src/app/admin/orders/\[id\]/page.tsx
→ profile: 3, admin: 3 ✓
```

## Manual UAT (sẽ thực hiện sau khi BE Plan 20-01..04 deploy)

| Bước | Hành động | Kỳ vọng |
|------|-----------|---------|
| 1 | Login admin → /admin → click sidebar 'Coupon' | Navigate /admin/coupons, table render |
| 2 | Click '+ Thêm coupon' → fill code=DEMO123, type=PERCENT, value=15, minOrder=100000, noLimit, noExpiry, active=true → Submit | Toast "Coupon đã được tạo" + row mới |
| 3 | Click ✏️ row | Modal pre-filled exact values |
| 4 | Đổi value=20 → Lưu | Row update, toast "Coupon đã được cập nhật" |
| 5 | Click ⏸ | Toast "Đã tắt coupon" + Badge "Đã tắt" |
| 6 | Click ▶️ | Toast "Đã bật coupon" + Badge "Đang bật" |
| 7 | Click 🗑 trên coupon used_count=0 → confirm | Toast "Coupon đã được xoá" + row biến mất |
| 8 | (Cần seed redemption) Click 🗑 trên coupon đã có redemption → confirm | Modal lỗi "Coupon đã có người dùng — vui lòng tắt thay vì xoá" |
| 9 | User đặt order với coupon DEMO123 → /profile/orders/{id} | Block "Mã giảm giá: DEMO123" + "Giảm giá: -X đ" trên Tổng cộng |
| 10 | Admin → /admin/orders/{id} của order trên | Cùng block coupon hiển thị |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] zod v4 + rhf v7 type incompat với `z.coerce.number()`**
- **Found during:** Task 2 typecheck
- **Issue:** `useForm<CouponFormData>` báo error vì `z.infer` trả `output` type (`value: number`) nhưng rhf cần `input` type (`value: unknown`).
- **Fix:** Tách `CouponFormInput = z.input<typeof couponSchema>` (cho default values + form state) vs `CouponFormData = z.output<typeof couponSchema>` (cho onSubmit handler). Dùng `useForm<CouponFormInput, unknown, CouponFormData>` 3-generic form.
- **Files modified:** sources/frontend/src/app/admin/coupons/page.tsx
- **Commit:** b27d335

**2. [Rule 3 - Blocking] `node_modules` chưa install trong worktree**
- **Found during:** Task 1 verify (`npx tsc` báo "not the tsc command")
- **Fix:** Chạy `npm install --prefer-offline --no-audit --no-fund` trước verify (16s, 408 packages).
- **Files modified:** none (chỉ install dependencies)

### Concurrent Plan Coordination Note

Plan 20-05 chạy song song và đã commit trước Task 1: `87cbcc6 feat(20-05): coupon service + error map + useApplyCoupon hook + types extension`. Plan 20-05 đã thêm vào `types/index.ts`:
- `AdminCoupon` interface (originally scoped cho 20-06 nhưng 20-05 đã thêm)
- `Order.discountAmount?` + `Order.couponCode?` extension

Vì vậy Task 1 commit `33db349` chỉ chứa `admin-coupons.ts` + `admin/layout.tsx` (types/index.ts đã không còn diff khi staging). Final state vẫn correct — verified bằng grep post-commit. Không có overlap thực sự — 2 plans đã coordinate đúng (20-05 owns checkout/coupons.ts/useApplyCoupon, 20-06 owns admin/coupons + admin-coupons.ts + order detail extends).

## Deferred Issues (Out of Scope)

Pre-existing lint errors trong các file KHÔNG thuộc plan 20-06:
- `sources/frontend/src/app/admin/page.tsx:145` — react-hooks/set-state-in-effect (pre-existing)
- `sources/frontend/src/components/ui/AddressPicker/AddressPicker.tsx:39` — react-hooks/set-state-in-effect (pre-existing)
- `sources/frontend/src/app/admin/products/page.tsx` — set-state-in-effect (pre-existing)

Logged here per scope-boundary rule. Lint output cho các file 20-06 (admin-coupons.ts, /admin/coupons/page.tsx, /admin/coupons/page.module.css, layout.tsx, profile/orders/[id]/page.tsx, admin/orders/[id]/page.tsx) đều CLEAN — không có lỗi mới nào do plan này gây ra.

## Authentication Gates

None — plan này thuần FE, không cần auth credentials trong dev. UAT bước 1 yêu cầu admin login (Phase 9 admin role JWT), nhưng đã coverage trước plan này.

## Threat Flags

None — plan ở trong threat_model đã định nghĩa, không introduce surface mới.

## Self-Check: PASSED

- [x] services/admin-coupons.ts exists ✓
- [x] /admin/coupons/page.tsx + page.module.css exist ✓
- [x] All 3 commits in `git log`: 33db349, b27d335, 666d2ad ✓
- [x] Grep all D-21/D-22/D-23 acceptance criteria match ✓
- [x] tsc --noEmit exit 0 ✓
- [x] No new lint errors in plan files ✓
