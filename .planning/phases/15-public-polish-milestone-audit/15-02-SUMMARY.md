---
phase: 15-public-polish-milestone-audit
plan: 02
subsystem: ui
tags: [phase-15, wave-1, pdp, breadcrumb, stock-badge, thumbnail, add-to-cart, a11y]

requires:
  - phase: 15-public-polish-milestone-audit
    provides: Badge variants success/warning/danger (Wave 0 — Plan 15-00)
provides:
  - PDP breadcrumb brand-based 'Trang chủ / {Brand} / {Name}' với link /products?brand={encoded} (D-14)
  - Breadcrumb fallback 'Trang chủ / Sản phẩm / {Name}' khi product.brand == null
  - Thumbnail strip a11y attrs (type=button, aria-label='Xem ảnh N', aria-current khi active) (D-11)
  - Thumbnail active emphasis: border 2px var(--primary) (D-11 visual cue)
  - Stock badge 3-tier success/warning/danger với icon prefix WCAG 1.4.1 (D-15)
  - Add-to-cart + quantity selector hide hoàn toàn khi stock=0, replace bằng inline message (D-16)
  - 'Xem giỏ hàng' secondary button render unconditionally bất kể stock value (D-16)
affects: [15-03-smoke-e2e (SMOKE-3 PDP DELIVERED product breadcrumb + stock badge), 14-search-brand-filter (continuity navigation breadcrumb -> FilterSidebar)]

tech-stack:
  added: []
  patterns:
    - "Stock badge 3-tier mutually exclusive conditional render với (product.stock ?? 0) defensive nullish"
    - "Conditional render section thay disabled state — ẩn hoàn toàn add-to-cart khi stock=0 (D-16 polish UX)"
    - "Icon prefix ✓⚠✗ + text label đảm bảo color KHÔNG là kênh thông tin duy nhất (WCAG 1.4.1)"
    - "Thumbnail a11y aria-current='true' | undefined (KHÔNG dùng false để tránh announce 'not current')"

key-files:
  created: []
  modified:
    - sources/frontend/src/app/products/[slug]/page.tsx
    - sources/frontend/src/app/products/[slug]/page.module.css

key-decisions:
  - "Breadcrumb refactor bỏ category segment hoàn toàn — đơn giản hóa hierarchy 3 level → 3 level brand-based (D-14)"
  - "Stock badge 3-tier dùng <Badge> component (Wave 0 extend) — KHÔNG inline span; cleanup .inStock/.outOfStock dead CSS"
  - "stock>=10 KHÔNG show count (giảm noise UI), stock<10 SHOW count (tạo urgency)"
  - "stock=0: hide entire .actions block (quantity + add-to-cart) thay disabled — UX rõ ràng hơn disabled button mơ hồ"
  - "Keyboard arrow Left/Right nav cho thumbnail SKIPPED (D-11 Claude's discretion) — default <button> tab+Enter/Space đã đủ; implement custom handler tốn >30 LOC + tăng test surface, defer v1.3 nếu UAT request"

requirements-completed: [PUB-03, PUB-04]

duration: 15min
completed: 2026-05-02
---

# Phase 15 Plan 02: Wave 1 PDP Polish Summary

**PDP `/products/[slug]` refactor 4 mặt: breadcrumb category-based → brand-based với fallback (D-14), thumbnail a11y attrs + active border 2px primary (D-11), stock badge 2-tier inline span → 3-tier `<Badge variant=success/warning/danger>` (D-15), add-to-cart hide hoàn toàn khi stock=0 thay disabled (D-16) — align ROADMAP Phase 15 SC-2 + SC-3 + continuity navigation tới Phase 14 brand filter.**

## Performance

- **Duration:** ~15 min (Task 1 commit `1abb61d` + Task 2 commit `6552fe2`)
- **Started:** 2026-05-02
- **Completed:** 2026-05-02

## Tasks

| Task | Name                                                            | Status | Commit  | Files                              |
| ---- | --------------------------------------------------------------- | ------ | ------- | ---------------------------------- |
| 1    | Refactor breadcrumb (brand-based) + thumbnail a11y polish       | Done   | 1abb61d | page.tsx, page.module.css          |
| 2    | Stock badge 3-tier + hide add-to-cart conditional               | Done   | 6552fe2 | page.tsx, page.module.css          |

## Lines Changed

**`sources/frontend/src/app/products/[slug]/page.tsx`:**

- **Breadcrumb block** (lines 104-122): category segment removed, brand-based với fallback link `/products` khi product.brand == null
- **Thumbnail strip** (lines 162-173): `type="button"` + `aria-label="Xem ảnh {i+1}"` + `aria-current={i === selectedImage ? 'true' : undefined}`
- **Stock display** (lines 213-223): 2-tier `<span className={inStock|outOfStock}>` → 3-tier `<Badge variant="success|warning|danger">` mutually exclusive với icon prefix ✓⚠✗
- **Actions block** (lines 225-280): wrap toàn bộ trong ternary `(product.stock ?? 0) > 0 ? <actions/> : <p.outOfStockMessage>`; quantity handlers chuyển sang functional setState `setQuantity((q) => ...)` để safer concurrent update
- **`Xem giỏ hàng` button** (line 282-284): giữ nguyên outside `.actions`, render unconditionally — confirmed visual bằng code trace (KHÔNG nằm trong stock-aware ternary)

**`sources/frontend/src/app/products/[slug]/page.module.css`:**

- **`.thumbnailActive`** (line 124-127): `border-color: var(--primary-container)` → `var(--primary)` + thêm `border-width: 2px`
- **`.inStock` + `.outOfStock` REMOVED** (cũ lines 218-230, ~13 LOC dead code) — replaced bằng `<Badge>` component
- **`.outOfStockMessage` ADDED** (lines 219-228): `var(--surface-container-low)` background + `var(--on-surface-variant)` color + `var(--radius-lg)` + padding/margin tokens

## Decisions Made

1. **Breadcrumb category segment bỏ hoàn toàn** (D-14) — Hierarchy 4 level (Home > Sản phẩm > Cat > Name) → 3 level (Home > Brand > Name). Continuity navigation ưu tiên brand filter (Phase 14 SEARCH-01) thay category vì brand link cụ thể hơn (1 brand << N categories per brand). Fallback `Trang chủ > Sản phẩm > Name` khi product.brand null đảm bảo KHÔNG có dangling segment.

2. **Stock badge tier breakpoint chọn `>=10` cho success** (D-15) — Industry pattern: green = "comfortable buffer", yellow = "act soon" (count creates urgency). Threshold 10 đủ rộng để không spam yellow nhưng đủ sớm để conversion lift via FOMO.

3. **stock=0 hide entire `.actions` thay disabled button** (D-16) — Disabled button gây hiểu nhầm (user click không phản hồi rõ). Inline message text rõ "Sản phẩm tạm hết — vui lòng quay lại sau" actionable hơn (set expectation về tương lai).

4. **Keyboard arrow nav cho thumbnail SKIPPED** (D-11 Claude's discretion) — Default `<button>` tab+Enter/Space đã đủ a11y baseline. Custom `onKeyDown` arrow Left/Right cycle tốn ~30+ LOC (handler + focus management + test). Defer v1.3 nếu UAT feedback yêu cầu, accept default behavior cho v1.2.

5. **Functional setState `setQuantity((q) => ...)` thay direct closure** (Task 2 cleanup) — Safer concurrent updates (React 18 batching). Mặc dù không strictly required cho click handler, pattern consistency tốt + future-proof.

## Cleanup Status

- ✅ `.inStock` CSS class **DELETED** (dead code, đã verify bằng grep — 0 references trong page.tsx)
- ✅ `.outOfStock` CSS class **DELETED** (dead code, đã verify bằng grep — 0 references; chỉ còn `.outOfStockMessage` mới)
- ✅ `disabled={product.stock === 0}` + label `'Hết hàng'` ternary trên Button **REMOVED** (branch stock=0 không bao giờ render trong nhánh `> 0`)

## Verify "Xem giỏ hàng" button render unconditionally khi stock=0

**Confirmed via code trace:**

```tsx
{(product.stock ?? 0) > 0 ? (
  <div className={styles.actions}>...</div>     // chỉ render khi stock > 0
) : (
  <p className={styles.outOfStockMessage}>...</p> // chỉ render khi stock = 0
)}

<Button variant="secondary" size="lg" fullWidth onClick={() => router.push('/cart')}>
  ♡ Xem giỏ hàng                                // ✅ NẰM NGOÀI ternary — render bất kể stock
</Button>
```

Visual confirmation defer cho 15-MANUAL-CHECKLIST M5 (cần seed product stock=0 trong DB để test) hoặc Wave 3 SMOKE-3.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] node_modules chưa install trong worktree**
- **Found during:** Task 2 verification (`npm run build` reported `'next' is not recognized`)
- **Issue:** Worktree fresh checkout, `sources/frontend/node_modules` không tồn tại
- **Fix:** `cd sources/frontend && npm install --no-audit --no-fund` — 368 packages installed in 19s
- **Files modified:** none (workspace setup only)
- **Commit:** none (build infrastructure)

### Deferred Issues

**1. Pre-existing build error `/profile/orders` Suspense boundary**
- **Phát hiện:** Task 2 `npm run build` failed at prerender step
- **Out-of-scope:** Phase 11 ProfileOrders page, KHÔNG do PDP changes
- **Verify alternative:** `tsc --noEmit` clean (zero TypeScript errors) → code correctness của PDP changes verified độc lập
- **Logged:** `.planning/phases/15-public-polish-milestone-audit/deferred-items.md`
- **Defer to:** Phase 11 hardening hoặc v1.3 polish (wrap orders page với `<Suspense>` boundary cho `useSearchParams()`)

## Authentication Gates

None — pure UI refactor.

## Verification Results

- ✅ TypeScript: `npx tsc --noEmit` exit 0, zero errors
- ⚠️ Next build: PDP code-correctness verified via tsc; build fail tại `/profile/orders` (Phase 11 pre-existing, deferred)
- ✅ Acceptance criteria Task 1: 6/6 grep matches verified
- ✅ Acceptance criteria Task 2: 8/8 grep matches verified (`variant="success/warning/danger"`, "Sản phẩm tạm hết", "outOfStockMessage", `(product.stock ?? 0) > 0 ?`, `var(--surface-container-low)`)
- ✅ `.inStock|.outOfStock` cleanup: 0 occurrences còn lại trong page.tsx (chỉ `.outOfStockMessage` mới)

## Next Steps

- **15-03 (Wave 2):** Smoke E2E 4 Playwright tests (homepage/checkout/review/profile) với skip-if-no-data degradation pattern
- **15-04 (Wave 3):** /gsd-audit-milestone v1.2 + MILESTONES update + git tag v1.2 annotated local

## Self-Check: PASSED

- ✅ `sources/frontend/src/app/products/[slug]/page.tsx` — modified, breadcrumb brand-based + thumbnail a11y + stock 3-tier + conditional add-to-cart
- ✅ `sources/frontend/src/app/products/[slug]/page.module.css` — modified, `.thumbnailActive` border emphasis + `.outOfStockMessage` added + dead CSS cleanup
- ✅ Commit `1abb61d` (Task 1) — `git log` confirmed
- ✅ Commit `6552fe2` (Task 2) — `git log` confirmed
- ✅ `.planning/phases/15-public-polish-milestone-audit/deferred-items.md` — created (logged out-of-scope build issue)
