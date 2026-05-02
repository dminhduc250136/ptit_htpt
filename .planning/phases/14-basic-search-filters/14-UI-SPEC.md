---
phase: 14
slug: basic-search-filters
status: draft
shadcn_initialized: false
preset: none
created: 2026-05-02
---

# Phase 14 — UI Design Contract (Basic Search Filters)

> Hợp đồng visual + interaction cho FilterSidebar (Brand + Price) trên `/products`. Sinh bởi gsd-ui-researcher (2026-05-02). Verified bởi gsd-ui-checker.

**Phạm vi UI Phase 14:**
- Component mới: `components/ui/FilterSidebar/` — chứa BrandFacet + PriceFacet (Claude tự quyết split file).
- Wire vào `app/products/page.tsx` — đặt sibling DƯỚI khối Categories hiện có trong `<aside className={styles.sidebar}>`.
- Thay thế logic `priceRange` client-side cũ (in-memory `.filter()` tại `page.tsx:87-89`) bằng server-side params (D-06).
- KHÔNG đụng `/search` page (D-01).

---

## Design System

| Property | Value |
|----------|-------|
| Tool | none (custom CSS Modules + design tokens M3-style đã có) |
| Preset | not applicable |
| Component library | none (vanilla React) |
| Icon library | inline SVG (precedent codebase — xem `page.tsx:116-122`) |
| Font | font-family kế thừa từ globals (`--font-family-headline` cho heading, system body) |

**Design tokens nguồn:** `sources/frontend/src/app/globals.css` — TẤT CẢ giá trị spacing/typography/color trong spec này dùng CSS variables đã định nghĩa, KHÔNG hardcode hex/px trong CSS Module mới (precedent `OrderFilterBar.module.css`).

---

## Spacing Scale

Reuse tokens hiện có (multiples of 4):

| Token | Value | Usage trong Phase 14 |
|-------|-------|---------------------|
| `--space-1` | 4px | Khoảng cách label↔input bên trong filter group |
| `--space-2` | 8px | Gap giữa checkbox và brand label; gap giữa preset chips |
| `--space-3` | 12px | Padding bên trong preset chip; gap giữa min input ↔ separator ↔ max input |
| `--space-4` | 16px | Padding ngang sidebar section; gap giữa Brand block ↔ Price block |
| `--space-5` | 20px | Margin-top giữa filter group title và options |
| `--space-6` | 24px | Padding-block sidebar section break |

**Exceptions:** Brand checkbox list `max-height: 240px` (~6 hàng visible) + `overflow-y: auto` khi DB có >6 brands — tránh sidebar dài vô hạn. Scrollbar dùng default browser (không cần custom).

---

## Typography

Reuse tokens hiện có (KHÔNG khai báo size mới):

| Role | Token | Computed | Weight | Usage |
|------|-------|----------|--------|-------|
| Filter section title ("Thương hiệu", "Khoảng giá") | `--text-title-md` | 16px | `--weight-semibold` (600) | Heading mỗi facet block — match `page.module.css .filterTitle` |
| Brand checkbox label, price input text | `--text-body-md` | 14px | `--weight-regular` (400) | Body text mặc định |
| Preset chip label ("<5tr", "5-10tr"...) | `--text-label-md` | 13px | `--weight-medium` (500) | Match precedent `.presetBtn` hiện có |
| Inline error price ("Giá tối thiểu...") | `--text-label-sm` | 12px | `--weight-medium` (500) | Error caption dưới price section |
| Reset button "Xóa bộ lọc" | `--text-label-md` | 13px | `--weight-semibold` (600) | Header sidebar action — match `.clearBtn` hiện có |

Line-height kế thừa từ globals (`--leading-normal` ≈ 1.5 cho body, `--leading-tight` ≈ 1.2 cho heading).

---

## Color

60/30/10 split — reuse tokens M3:

| Role | Token | Value | Usage |
|------|-------|-------|-------|
| Dominant (60%) | `--surface` / `--surface-container-lowest` | `#ffffff` | Page background, sidebar background |
| Secondary (30%) | `--surface-container-low` | `#f2f4f6` | Filter section card BG (nếu cần highlight section), preset chip BG (idle) |
| Accent (10%) | `--primary` | `#0040a1` | Checkbox CHECKED state, preset chip ACTIVE state, focus ring trên price input, reset button text |
| Destructive | `--error` | `#ba1a1a` | Inline error text "Giá tối thiểu phải nhỏ hơn giá tối đa" + border-color price input khi invalid |

**Accent reserved for (CHỈ những element này — KHÔNG dùng accent cho idle button hay hover của brand row):**
1. Checkbox brand đã tick (`accent-color: var(--primary)` trên `<input type=checkbox>`).
2. Preset chip đang active (BG `var(--primary)`, text `var(--on-primary)`).
3. Focus ring price input khi user đang nhập (`border-color: var(--primary)` — match precedent `.input:focus`).
4. Reset button "Xóa bộ lọc" text color (variant tertiary `Button` đã có).

**Destructive reserved for:** chỉ inline price validation error (text + border màu `--error`). KHÔNG dùng `--error` cho hover destructive khác (Phase 14 không có destructive action).

---

## Copywriting Contract

Toàn bộ copy tiếng Việt — tuân thủ `feedback_language.md`.

| Element | Copy | Ghi chú |
|---------|------|---------|
| Section title — Brand facet | **Thương hiệu** | Match style `.filterTitle` |
| Section title — Price facet | **Khoảng giá** | Reuse copy hiện có |
| Brand search/empty fallback (nếu DB trả [] ) | "Chưa có thương hiệu nào" | Render plain text, không nút |
| Price input min — placeholder | **Từ** | Match precedent `placeholder="Từ"` |
| Price input max — placeholder | **Đến** | Match precedent `placeholder="Đến"` |
| Preset chip 1 | **<5tr** | < 5,000,000 VND (priceMax=4999999) |
| Preset chip 2 | **5-10tr** | 5,000,000 ≤ price ≤ 10,000,000 |
| Preset chip 3 | **10-20tr** | 10,000,000 ≤ price ≤ 20,000,000 |
| Preset chip 4 | **>20tr** | > 20,000,000 (priceMin=20000001) |
| Inline error price (D-05) | **Giá tối thiểu phải nhỏ hơn giá tối đa** | Hiện onBlur khi `priceMin > priceMax` |
| Reset button (D-10) | **Xóa bộ lọc** | aria-label: "Xóa bộ lọc thương hiệu và giá" |
| Empty state heading (D-12) — khi 0 results sau filter | **Không tìm thấy sản phẩm phù hợp với bộ lọc** | Match exact wording CONTEXT D-12 |
| Empty state body | **Thử bỏ bớt thương hiệu hoặc nới rộng khoảng giá** | Hint hành động cụ thể |
| Empty state CTA | **Xóa bộ lọc** | Cùng handler D-10 |
| Loading state aria | **Đang tải sản phẩm…** | aria-live="polite" trên grid |
| Filter applied count badge (optional, KHÔNG trong scope khắc khe) | "{N} bộ lọc đang áp dụng" — khi `brands.length + (price set ? 1 : 0) > 0` | Hiện cạnh "Xóa bộ lọc"; có thể skip nếu Claude thấy thừa |

**KHÔNG có CTA primary mới** trong phase này (auto-apply D-02 → không cần nút "Áp dụng"). KHÔNG có destructive confirmation (reset filter là idempotent, không cần confirm dialog).

**VND format:** giá trị min/max format `1.000.000` khi `onBlur` (thousand-separator dấu chấm chuẩn VN). Khi user `onFocus` → strip về số thuần để dễ edit. Helper format: tham khảo `lib/format.ts` nếu đã có (CONTEXT code_context note); nếu chưa có thì Claude tạo helper inline trong `FilterSidebar.tsx` hoặc bổ sung `lib/format.ts` cùng phase.

---

## Interaction Contract

| Trigger | Behavior | Loading state |
|---------|----------|---------------|
| Check/uncheck brand checkbox | Apply NGAY (D-02 — instant) → reset page=0 (D-11) → refetch | Skeleton card grid (reuse `.skeletonCard` 8 ô, height 360 — precedent `page.tsx:230`) |
| Type vào price min/max input | Debounce 400ms (D-02) → validate → nếu pass thì apply | Skeleton same |
| Click preset chip | Fill min+max input + apply NGAY → reset page=0 | Skeleton same |
| Click chip đang active | Toggle off (clear cả min+max về undefined) + apply | Skeleton same |
| onBlur price input | Format VND thousand-separator + chạy validate `min > max` | — |
| Validation fail (`min > max`) | Hiện inline error text + border `--error` trên cả 2 input + KHÔNG fire request | — |
| Click "Xóa bộ lọc" | Reset `brands=[]`, `priceMin=undefined`, `priceMax=undefined`, clear preset active state, clear inline error → apply → page=0. Categories KHÔNG bị reset (D-10). | Skeleton same |
| Mobile (<768px) | FilterSidebar collapse vào drawer hiện có (`.sidebarOpen` toggle precedent line 128); button "Bộ lọc" header hiện count đang active | — |
| Brand list dài (>6 brands) | `max-height: 240px` + scroll-y; brand search box KHÔNG có (defer) | — |

**Sticky behavior:** sidebar `position: sticky; top: var(--space-6)` (match OrderFilterBar precedent + existing categories sidebar). Brand+Price block scroll cùng sidebar, KHÔNG sticky riêng.

---

## Component Inventory

Tất cả tự build (không dùng lib mới):

| Component | Path | Reuse hay mới |
|-----------|------|--------------|
| `FilterSidebar` | `components/ui/FilterSidebar/FilterSidebar.tsx` (+ `.module.css`) | MỚI |
| `BrandFacet` (checkbox list) | inline trong FilterSidebar HOẶC tách `BrandFacet.tsx` | Claude quyết theo phức tạp |
| `PriceFacet` (2 input + 4 chip + error) | inline HOẶC tách `PriceFacet.tsx` | Claude quyết |
| `Button` (variant tertiary, size sm) cho "Xóa bộ lọc" | `components/ui/Button/Button` (đã có) | REUSE |
| `useDebouncedValue` hook (cho price 400ms) | `hooks/useDebouncedValue.ts` nếu chưa có | MỚI nếu thiếu — Claude check codebase trước |
| Skeleton state | reuse `.skeletonCard` + class `skeleton` global | REUSE |
| Empty state | reuse pattern `.emptyState` trong `page.module.css` | REUSE — chỉ thay copy |

---

## Accessibility Contract

- Mỗi brand checkbox bọc trong `<label>` với `htmlFor` link tới `<input id="brand-{slug}">` — click label tick được checkbox.
- Price input có `<label>` ẩn visually (`sr-only`) "Giá từ" / "Giá đến" — vì placeholder không đủ cho screen reader.
- Preset chip là `<button type="button">` (KHÔNG `<div onClick>`) — keyboard focusable; `aria-pressed={isActive}`.
- Inline error `<p role="alert">` để screen reader announce ngay khi xuất hiện.
- Reset button `aria-label="Xóa bộ lọc thương hiệu và giá"`.
- Skeleton wrapper grid `aria-busy="true" aria-live="polite"`.
- Brand list scrollable container `role="group" aria-label="Danh sách thương hiệu"`.

---

## Registry Safety

| Registry | Blocks Used | Safety Gate |
|----------|-------------|-------------|
| shadcn official | none | not applicable (project không init shadcn) |
| third-party | none | not applicable |

Phase 14 KHÔNG thêm dependency mới (decision ROADMAP — vanilla JPQL + React state). KHÔNG cần registry vetting gate.

---

## Visual Layout Sketch

```
┌─ <aside .sidebar> (sticky top, width ~280px desktop) ──┐
│  ┌─ .sidebarHeader ────────────────────────────────┐  │
│  │  Bộ lọc                              [Xóa bộ lọc]│  │ ← reset button (tertiary)
│  └─────────────────────────────────────────────────┘  │
│                                                        │
│  [Search input] (existing — keyword)                   │
│                                                        │
│  ─── Danh mục ─── (existing — categories chips)        │
│  [Tất cả] [Cat1] [Cat2] ...                           │
│                                                        │
│  ─── Thương hiệu ─── (NEW — brand facet)              │
│  ☑ Apple                                              │
│  ☐ Asus                                               │
│  ☑ Dell                                               │
│  ☐ HP                                                 │
│  ☐ Lenovo                                             │
│  (scroll nếu >6)                                      │
│                                                        │
│  ─── Khoảng giá ─── (NEW — price facet)               │
│  [  Từ  ]  —  [ Đến ]                                 │
│  [<5tr] [5-10tr] [10-20tr] [>20tr]   ← preset chips   │
│  ⚠ Giá tối thiểu phải nhỏ hơn giá tối đa  (nếu err)  │
│                                                        │
└────────────────────────────────────────────────────────┘
```

Brand block + Price block đặt SAU section Categories, TRƯỚC mobile filter close button (precedent `page.tsx:200-204`).

---

## Pre-Populated From

| Source | Decisions Used |
|--------|---------------|
| `14-CONTEXT.md` | D-01 (scope `/products` only), D-02 (auto-apply + 400ms debounce), D-03 (brand alphabetical, no count), D-04 (4 preset chips + VND format onBlur), D-05 (validate onBlur), D-09 (local state), D-10 (reset button copy), D-12 (empty state copy) |
| `ROADMAP.md` | Phase 14 success criteria 1-4 |
| `REQUIREMENTS.md` | SEARCH-01 (brand multi-select), SEARCH-02 (price min/max + validate) |
| `OrderFilterBar.module.css` + `.tsx` | Pattern: sticky sidebar, debounce 400ms, label/input styling tokens, "Xóa bộ lọc" copy + aria-label |
| `app/products/page.tsx` + `page.module.css` | Existing sidebar layout, `.filterTitle`, `.filterChip`, `.presetBtn`, `.priceInputs`, mobile drawer toggle, skeleton 8 cards |
| `globals.css` | All design tokens reused (zero new color/size declared) |

---

## Checker Sign-Off

- [ ] Dimension 1 Copywriting: PASS
- [ ] Dimension 2 Visuals: PASS
- [ ] Dimension 3 Color: PASS
- [ ] Dimension 4 Typography: PASS
- [ ] Dimension 5 Spacing: PASS
- [ ] Dimension 6 Registry Safety: PASS

**Approval:** pending
