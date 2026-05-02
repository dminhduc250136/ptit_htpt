---
phase: 15
slug: public-polish-milestone-audit
status: draft
shadcn_initialized: false
preset: none
created: 2026-05-02
---

# Phase 15 — UI Design Contract (Public Polish)

> Hợp đồng visual + interaction cho 5 surface areas: Hero (next/image), Featured carousel (CSS scroll-snap), Categories grid + New Arrivals dedupe, PDP breadcrumb refactor, PDP stock badge 3-tier, PDP thumbnail gallery polish. Sinh bởi gsd-ui-researcher (2026-05-02). Verified bởi gsd-ui-checker.

**Phạm vi UI Phase 15:**
- `app/page.tsx` + `page.module.css` — hero img→`<Image priority>` + featured CSS scroll-snap carousel + categories grid responsive 4↔2 cột + new arrivals dedupe single-fetch.
- `app/products/[slug]/page.tsx` + `page.module.css` — breadcrumb refactor (Brand thay Category) + stock badge 3-tier color-coded + add-to-cart hide khi stock=0 + thumbnail polish (border + a11y).
- `components/ui/Badge/Badge.tsx` + `Badge.module.css` — extend variant `'success' | 'warning' | 'danger'` cho stock badge.
- `public/hero/hero-primary.webp` + `hero-secondary.webp` — assets mới (Claude's discretion dimension/quality).

**KHÔNG trong scope:** redesign hero layout, redesign navigation/header/footer, lightbox lib, axe-core gate, Lighthouse CI, per-category icon, JS carousel lib, visual regression, design tokens overhaul, smoke E2E (TEST-02 — separate plan, không thuộc visual contract).

---

## Design System

| Property | Value |
|----------|-------|
| Tool | none (custom CSS Modules + design tokens M3-style đã có) |
| Preset | not applicable |
| Component library | none (vanilla React + Next.js core: `next/image`, `next/link`) |
| Icon library | inline SVG (precedent codebase) + ký tự Unicode `✓` `⚠` `✗` cho stock badge |
| Font | `--font-be-vietnam-pro` (kế thừa từ globals — Be Vietnam Pro tối ưu diacritics tiếng Việt) |

**Design tokens nguồn:** `sources/frontend/src/app/globals.css` — TẤT CẢ giá trị spacing/typography/color reuse CSS variables. Gap duy nhất: `--success` + `--warning` semantic tokens KHÔNG tồn tại — see Color section cho fallback.

---

## Spacing Scale

Reuse tokens hiện có (multiples of 4 — `--space-*` đã 8-point scale):

| Token | Value | Usage trong Phase 15 |
|-------|-------|---------------------|
| `--space-1` | 4px | Gap giữa stock badge icon (✓/⚠/✗) và text; thumbnail border-width khi inactive |
| `--space-2` | 8px | Gap giữa breadcrumb separator (`/`) và link; gap giữa thumbnail strip items (đã có) |
| `--space-3` | 16px | Gap giữa featured carousel cards; padding inline `outOfStockMessage`; padding ngang categories card |
| `--space-4` | 24px | Gap giữa categories grid items; padding-block hero section; scroll-padding featured carousel |
| `--space-5` | 32px | Margin-bottom section header (đã có); gap giữa hero text block và visual block (mobile) |
| `--space-6` | 48px | Gap chính giữa hero columns (đã có); padding-block categories section (đã có) |
| `--space-7` | 64px | Padding-block products/value sections (đã có) |

**Exceptions:**
- Featured card `flex-basis: 280px` desktop / `240px` mobile (≤768px) — KHÔNG là multiple of 8 nhưng justified: mục tiêu hiển thị ~3.5 cards trên viewport 1024px (peek next card → cue scroll), trade-off với 8-grid acceptable.
- Hero WebP aspect ratio bám parent container (75% × 85% primary, 45% × 50% secondary đã có) — KHÔNG ép spacing token cho image dimension.
- Active thumbnail border `2px var(--primary)` — match precedent UI patterns (vẫn nằm dưới `--space-1` = 4px ngưỡng).

---

## Typography

Reuse tokens hiện có (KHÔNG khai báo size mới — phase 15 polish-only):

| Role | Token | Computed | Weight | Usage |
|------|-------|----------|--------|-------|
| Hero title | `--text-display-lg` (đã có) | 56px desktop / 36px mobile (`--text-display-sm`) | `--weight-bold` (700) | `.heroTitle` — KHÔNG đổi |
| Hero description | `--text-body-lg` | 16px | `--weight-regular` (400) | `.heroDescription` — KHÔNG đổi |
| Section title ("Sản phẩm nổi bật", "Sản phẩm mới", "Danh mục sản phẩm") | `--text-headline-lg` | 32px | `--weight-bold` (700) | `.sectionTitle` — KHÔNG đổi |
| Section subtitle | `--text-body-md` | 14px | `--weight-regular` (400) | `.sectionSubtitle` — KHÔNG đổi |
| Stock badge text (3-tier) | `--text-label-sm` | 11px | `--weight-semibold` (600) | Match precedent `.badge` — uppercase + tracking-wide |
| Out-of-stock inline message | `--text-body-md` | 14px | `--weight-medium` (500) | `.outOfStockMessage` — natural body, KHÔNG uppercase |
| Breadcrumb link | `--text-body-sm` | 12px | `--weight-regular` (400) | `.breadcrumbLink` — match precedent |
| Breadcrumb current segment | `--text-body-sm` | 12px | `--weight-medium` (500) | `.breadcrumbCurrent` — slight emphasis cho final segment |
| Featured carousel card content | (kế thừa `<ProductCard>`) | n/a | n/a | KHÔNG đổi typography ProductCard |

Line-height kế thừa từ globals (`--leading-tight` 1.2 cho heading, `--leading-normal` 1.5 cho body, `--leading-relaxed` 1.6 cho description Vietnamese).

---

## Color

60/30/10 split — reuse tokens M3 + 3 token MỚI cho stock badge:

| Role | Token | Value | Usage |
|------|-------|-------|-------|
| Dominant (60%) | `--surface` / `--surface-container-lowest` | `#f7f9fb` / `#ffffff` | Page background, hero background (`--gradient-hero`), product card surface |
| Secondary (30%) | `--surface-container-low` | `#f2f4f6` | Categories section BG, breadcrumb bar BG, value section BG, featured card hover, price block BG |
| Accent (10%) | `--primary` | `#0040a1` | CTA primary "Khám phá ngay", section title "Xem tất cả →" tertiary text, active thumbnail border 2px, breadcrumb link hover, focus ring (kế thừa `:focus-visible`) |
| Destructive | `--error` | `#ba1a1a` | Stock badge "Hết hàng" text, out-of-stock inline message KHÔNG dùng (giữ neutral `--on-surface-variant`) |
| **Success (NEW)** | `var(--success, #16a34a)` | `#16a34a` (green-600 Tailwind palette) | Stock badge "Còn hàng" background tint + text |
| **Warning (NEW)** | `var(--warning, #f59e0b)` | `#f59e0b` (amber-500 Tailwind palette) | Stock badge "Sắp hết hàng" background tint + text |
| **Danger (alias)** | `var(--error)` | `#ba1a1a` | Stock badge "Hết hàng" — reuse existing `--error` token, KHÔNG đặt token mới |

**Accent reserved for (CHỈ những element này — KHÔNG dùng accent cho idle button hay hover thông thường):**
1. Hero CTA primary "Khám phá ngay" (`<Button size="lg">` — kế thừa primary variant).
2. Section header "Xem tất cả →" (`<Button variant="tertiary">` — text color `--primary`).
3. Active thumbnail border 2px `var(--primary)` (replace precedent `--primary-container` để emphasize chọn rõ hơn).
4. Breadcrumb link hover state (`color: var(--primary)` — đã có precedent).
5. Focus ring trên brand link breadcrumb + featured carousel container `:focus-visible` (kế thừa global `--primary-container`).

**Destructive reserved for:** chỉ stock badge "Hết hàng" (red tint background + dark red text). KHÔNG dùng `--error` cho hover destructive khác trong phase này (KHÔNG có destructive action như delete/cancel).

**Stock badge color contract chi tiết:**

| Tier | Condition | Background | Text Color | Copy |
|------|-----------|------------|------------|------|
| Success | `stock >= 10` | `color-mix(in srgb, var(--success, #16a34a) 15%, transparent)` (light green tint) — fallback inline `rgba(22, 163, 74, 0.15)` | `var(--success, #16a34a)` darker variant `#15803d` (green-700) cho contrast | `✓ Còn hàng` |
| Warning | `1 <= stock <= 9` | `color-mix(in srgb, var(--warning, #f59e0b) 18%, transparent)` — fallback `rgba(245, 158, 11, 0.18)` | `#b45309` (amber-700) cho WCAG AA contrast trên light tint | `⚠ Sắp hết hàng (còn {N})` |
| Danger | `stock === 0` | `var(--error-container)` (`#ffdad6` — đã có token) | `var(--on-error-container)` (`#93000a` — đã có token) | `✗ Hết hàng` |

> **Note:** Success + Warning dùng fallback hex CSS variable pattern `var(--success, #16a34a)` để executor có thể add token vào `globals.css` v1.3 mà KHÔNG cần touch lại Badge.module.css. Recommend (Claude's discretion executor): add `--success: #16a34a;` + `--warning: #f59e0b;` vào `globals.css :root` cùng commit Badge extend — clean centralization. Danger reuse `--error-container` đã có (KHÔNG cần token mới).

---

## Copywriting Contract

Toàn bộ copy tiếng Việt — tuân thủ `feedback_language.md`. Identifiers + commit prefixes giữ EN.

### Hero (PUB-01)

| Element | Copy | Ghi chú |
|---------|------|---------|
| Hero badge | **Bộ sưu tập Thu Đông 2024** | Giữ nguyên (`<Badge variant="new">`) |
| Hero title (line 1) | **Nghệ thuật** | Giữ nguyên |
| Hero title accent (line 2) | **chế tác thủ công** | Giữ nguyên (`.heroAccent` gradient) |
| Hero description | **Khám phá bộ sưu tập thu đông mới nhất với chất liệu cao cấp và thiết kế tinh xảo từ những nghệ nhân hàng đầu.** | Giữ nguyên |
| CTA primary | **Khám phá ngay** | Giữ nguyên — link `/products` |
| CTA secondary (REPLACE) | **Xem tất cả sản phẩm** | THAY copy + link cũ "Xem bộ sưu tập" → `/collections` (404) bằng copy mới + link `/products` (D-02 CONTEXT). Tránh route 404. |
| Hero primary image alt | **Sản phẩm nổi bật** | Giữ nguyên |
| Hero secondary image alt | **Phụ kiện cao cấp** | Giữ nguyên |

### Featured Carousel (PUB-01)

| Element | Copy | Ghi chú |
|---------|------|---------|
| Section title | **Sản phẩm nổi bật** | Giữ nguyên |
| Section subtitle | **Những sản phẩm được yêu thích nhất bởi khách hàng** | Giữ nguyên |
| "View all" CTA | **Xem tất cả →** | Giữ nguyên (`<Button variant="tertiary">`) |
| Carousel container aria-label | **Sản phẩm nổi bật — vuốt ngang để xem thêm** | Hint mobile gesture cho screen reader |
| Empty state | **Chưa có sản phẩm nổi bật.** | Giữ nguyên (precedent line 167) |
| Loading aria | **Đang tải sản phẩm…** | Skeleton wrapper `aria-busy="true" aria-live="polite"` |

### Categories (PUB-02)

| Element | Copy | Ghi chú |
|---------|------|---------|
| Section title | **Danh mục sản phẩm** | Giữ nguyên |
| Section subtitle | **Khám phá các dòng sản phẩm được tuyển chọn kỹ lưỡng** | Giữ nguyên |
| Category card link | **{cat.name}** dynamic | Giữ nguyên — link `/products?category={slug}` (D-07: KHÔNG đổi sang brand) |

### New Arrivals (PUB-02)

| Element | Copy | Ghi chú |
|---------|------|---------|
| Section title | **Sản phẩm mới** | Giữ nguyên |
| Section subtitle | **Khám phá bộ sưu tập mới nhất của chúng tôi** | Giữ nguyên |
| "View all" CTA | **Xem tất cả →** | Giữ nguyên |
| Empty state | **Chưa có sản phẩm mới.** | Giữ nguyên — fired khi catalog < 16 products (single-fetch slice trả [] cho `slice(8,16)`) |

### PDP Breadcrumb (PUB-03)

| Element | Copy | Ghi chú |
|---------|------|---------|
| Home segment | **Trang chủ** | Giữ nguyên — link `/` |
| Brand segment (có brand) | **{product.brand}** dynamic | Link `/products?brand={encodeURIComponent(brand)}` — Phase 14 contract |
| Sản phẩm fallback (không brand) | **Sản phẩm** | Link `/products` — fallback khi `product.brand == null` |
| Current product segment | **{product.name}** dynamic | Non-link (`<span className={styles.breadcrumbCurrent}>`) |
| Breadcrumb separator | `/` literal character | `<span className={styles.breadcrumbSep}>` — color `--outline-variant` |

### PDP Thumbnail Gallery (PUB-03)

| Element | Copy | Ghi chú |
|---------|------|---------|
| Thumbnail button aria-label | **Xem ảnh {i+1}** | Index 1-based natural cho screen reader VN |
| Active thumbnail aria-current | `"true"` (boolean attribute) | Set khi `i === selectedImage` |
| Main image alt | **{product.name}** | Giữ nguyên (precedent line 132) |
| Thumbnail image alt | **{product.name} - {i+1}** | Giữ nguyên (precedent line 167) |

### PDP Stock Badge (PUB-04)

| Element | Copy | Ghi chú |
|---------|------|---------|
| Success badge (`stock >= 10`) | **✓ Còn hàng** | KHÔNG hiện count detail (D-15 — minimal). Bỏ `(N sản phẩm)` cũ. |
| Warning badge (`1 ≤ stock ≤ 9`) | **⚠ Sắp hết hàng (còn {N})** | HIỆN count để tạo urgency. `{N}` = `product.stock` raw integer. |
| Danger badge (`stock === 0`) | **✗ Hết hàng** | Đỏ, no count. |
| Out-of-stock inline message (replace add-to-cart UI) | **Sản phẩm tạm hết — vui lòng quay lại sau.** | Block element thay thế quantity selector + add-to-cart button khi `stock === 0`. Color `--on-surface-variant` (neutral, không alarming). |
| "Xem giỏ hàng" secondary button | **♡ Xem giỏ hàng** | Giữ nguyên — KHÔNG hide khi stock=0 (user vẫn vào giỏ xem item khác) |

### Empty / Error / Loading

| Element | Copy | Ghi chú |
|---------|------|---------|
| Featured/Latest fetch fail | (kế thừa `<RetrySection>` Phase 5 — không override copy) | "Tải lại" button — pattern đã chuẩn hóa |
| Specs empty (kế thừa) | **Chưa có thông số kỹ thuật.** | Giữ nguyên (D-13 CONTEXT) |

**KHÔNG có CTA primary mới** trong phase này (5 surface areas đều polish/refactor — không introduce primary action mới). **KHÔNG có destructive action** (no delete/cancel/remove confirmations).

**VND format:** không áp dụng phase 15 (price formatting đã chuẩn ở `formatPrice()`). Stock count `{N}` chỉ là integer raw, không format.

---

## Interaction Contract

### Hero (PUB-01)

| Trigger | Behavior | Loading state |
|---------|----------|---------------|
| Page load | `<Image priority>` injects `<link rel="preload">` server-side cho `/hero/hero-primary.webp` → browser fetch sớm → LCP < 2.5s target trên `next build && next start` mobile preset | Image placeholder color `var(--surface-container-low)` (kế thừa `.heroImagePrimary` BG) trong khi WebP load |
| Click CTA "Khám phá ngay" | Navigate `/products` (Next link, prefetch tự động) | — |
| Click CTA "Xem tất cả sản phẩm" | Navigate `/products` (cùng đích — 2 CTA cùng link, accept duplicate intent) | — |
| Hero image hover | Không hover effect (giữ minimal — image static) | — |

### Featured Carousel (PUB-01)

| Trigger | Behavior | Loading state |
|---------|----------|---------------|
| Page load | Single `listProducts({ size: 16, sort: 'createdAt,desc' })` fetch → `setFeatured(all.slice(0,8))` + `setLatest(all.slice(8,16))` (Pitfall 2 dedupe) | Skeleton 4 cards (kế thừa precedent `.skeleton` height 360 — 4 vì viewport ban đầu show ~4) |
| Touch swipe / wheel scroll horizontal | Native browser scroll với `scroll-snap-type: x mandatory` snap to next card | — |
| Tab focus carousel container | Container `tabindex="0"` → focus ring (kế thừa `:focus-visible` global) | — |
| Arrow Left/Right key khi focus container | Native browser scroll (1 card width per keystroke approx — browser default behavior) | — |
| Click product card | Navigate `/products/{slug}` (kế thừa `<ProductCard>` link) | — |
| Click "Xem tất cả →" | Navigate `/products` | — |
| Featured fetch fail | `<RetrySection>` render với "Tải lại" button (kế thừa Phase 5 pattern) | — |

**Carousel scroll behavior:** `overflow-x: auto` + `scrollbar-width: none` (Firefox) + `::-webkit-scrollbar { display: none }` (Chrome/Safari/Edge) — visually clean. Touch momentum `-webkit-overflow-scrolling: touch` cho iOS. KHÔNG có prev/next arrow buttons (D-05 — minimal).

### Categories Grid (PUB-02)

| Trigger | Behavior | Loading state |
|---------|----------|---------------|
| Click category card | Navigate `/products?category={slug}` (D-07 — KHÔNG đổi sang brand) | — |
| Hover card | `transform: translateY(-4px)` + `box-shadow: var(--shadow-md)` (giữ nguyên precedent) | — |
| Categories fetch fail | Render `null` cho section (best-effort — không block hero/featured render) | — |

**Responsive grid:**
- Desktop (>1024px): 4 cột (`grid-template-columns: repeat(4, 1fr)`) — REVIEW NOTE: precedent dùng `repeat(5, 1fr)` cho 5 categories; phase 15 align ROADMAP 4 cột cho consistency với products grid. Executor verify count categories → nếu count = 5 fixed, có thể giữ 5 cột (Claude's discretion executor).
- Tablet (≤1024px): 3 cột (kế thừa precedent)
- Mobile (≤768px): 2 cột (kế thừa precedent)

### New Arrivals Grid (PUB-02)

| Trigger | Behavior | Loading state |
|---------|----------|---------------|
| Page load | `setLatest(all.slice(8, 16))` từ single-fetch chung với Featured — dedupe deterministic | Skeleton 4 cards (kế thừa) |
| Click product card | Navigate `/products/{slug}` | — |
| Click "Xem tất cả →" | Navigate `/products` | — |
| Catalog < 16 products | `latest` array < 8 items hoặc empty → render whatever returned hoặc empty state copy | — |

**Render mode:** Grid 4 cột (kế thừa `.productsGrid`) — KHÔNG carousel (D-10 — chỉ Featured dùng carousel để differentiate visually).

### PDP Breadcrumb (PUB-03)

| Trigger | Behavior | Loading state |
|---------|----------|---------------|
| Click "Trang chủ" | Navigate `/` | — |
| Click brand segment | Navigate `/products?brand={encodeURIComponent(product.brand)}` → FilterSidebar Phase 14 pre-check brand (continuity navigation) | — |
| Click "Sản phẩm" fallback (no brand) | Navigate `/products` | — |
| Hover link segment | Color transition `--on-surface-variant` → `--primary` (kế thừa precedent) | — |
| Tab focus link | Focus ring `:focus-visible` (kế thừa global) | — |

### PDP Thumbnail Gallery (PUB-03)

| Trigger | Behavior | Loading state |
|---------|----------|---------------|
| Click thumbnail | `setSelectedImage(i)` → main image swap (kế thừa precedent) | Main `<Image priority>` đã preload (no skeleton needed) |
| Hover thumbnail | Border `--outline-variant` (kế thừa precedent `.thumbnail:hover`) | — |
| Active thumbnail | Border `2px solid var(--primary)` (replace precedent `var(--primary-container)` để contrast rõ hơn) + `aria-current="true"` | — |
| Tab focus thumbnail | Focus ring `:focus-visible` (kế thừa) | — |
| **Optional (Claude's discretion D-11)** Arrow Left/Right key khi focus trong thumbnail strip | Decrement/increment `selectedImage` (modulo length), focus next thumbnail. Implement nếu < 30 LOC; SKIP nếu phức tạp (acceptable degradation). | — |

### PDP Stock Badge + Add-to-Cart (PUB-04)

| Trigger | Behavior | Loading state |
|---------|----------|---------------|
| Render `stock >= 10` | `<Badge variant="success">✓ Còn hàng</Badge>` + show quantity selector + "Thêm vào giỏ hàng" button | — |
| Render `1 <= stock <= 9` | `<Badge variant="warning">⚠ Sắp hết hàng (còn {stock})</Badge>` + show quantity selector + "Thêm vào giỏ hàng" button (max quantity capped at `stock`) | — |
| Render `stock === 0` | `<Badge variant="danger">✗ Hết hàng</Badge>` + **HIDE hoàn toàn** (`return null`) cả `.actions` (quantity + add-to-cart) → render `<p className={styles.outOfStockMessage}>Sản phẩm tạm hết — vui lòng quay lại sau.</p>`. "♡ Xem giỏ hàng" secondary button vẫn hiển thị. | — |
| Click "Thêm vào giỏ hàng" | Add to cart logic (kế thừa precedent — không thay đổi handler) | `loading={addingToCart}` spinner (kế thừa) |
| Click "Xem giỏ hàng" | Navigate `/cart` (kế thừa) | — |

**KHÔNG dùng `disabled` button khi stock=0** — D-16 explicit: hide thay vì disable. Lý do UX: disabled button gây ambiguity (broken? loading?), inline message rõ ràng hơn cho VN user.

---

## Component Inventory

| Component | Path | Reuse hay mới |
|-----------|------|--------------|
| `next/image` (`<Image priority>`) | Next.js core | REUSE (đã dùng PDP gallery) — apply mới cho hero |
| `next/link` | Next.js core | REUSE |
| `<Badge>` | `components/ui/Badge/Badge.tsx` | **EXTEND** — thêm 3 variant `success | warning | danger` vào `BadgeVariant` union + 3 class CSS module |
| `<Button>` | `components/ui/Button/Button.tsx` | REUSE (primary/secondary/tertiary variants đã có) |
| `<ProductCard>` | `components/ui/ProductCard/ProductCard.tsx` | REUSE — wrap trong `.featuredCard` div cho carousel sizing |
| `<RetrySection>` | `components/ui/RetrySection/RetrySection.tsx` | REUSE |
| `.skeleton` global class | `globals.css` | REUSE |
| `<ReviewSection>` | `[slug]/ReviewSection/` | REUSE (PDP tabs — không touch) |
| `featuredScroll` CSS Module class | `app/page.module.css` (mới) | MỚI — scroll-snap container styles |
| `featuredCard` CSS Module class | `app/page.module.css` (mới) | MỚI — flex-basis fixed cho card sizing |
| `outOfStockMessage` CSS Module class | `app/products/[slug]/page.module.css` (mới) | MỚI — inline message style khi stock=0 |
| Hero WebP assets | `public/hero/hero-primary.webp`, `hero-secondary.webp` | MỚI — Claude's discretion source/dimension |

**KHÔNG add dependency mới:** không lightbox, không carousel lib, không icon lib (Unicode `✓ ⚠ ✗` đủ).

---

## Accessibility Contract

### Hero
- `<Image>` với `alt="Sản phẩm nổi bật"` + `alt="Phụ kiện cao cấp"` (descriptive, KHÔNG empty alt vì hero image là content not decoration).
- CTA buttons giữ semantic `<button>` qua `<Button>` component (kế thừa).

### Featured Carousel
- Container `<div role="region" aria-label="Sản phẩm nổi bật — vuốt ngang để xem thêm" tabindex="0">` — Pitfall 3: scrollable element MUST có `tabindex="0"` cho keyboard scroll + `role="region"` + `aria-label` cho screen reader.
- Skeleton wrapper: `aria-busy="true" aria-live="polite"` khi `loading === true`.
- Mỗi `<ProductCard>` link kế thừa semantic `<a>` từ ProductCard component.

### Categories Grid
- `<Link>` semantic anchor với category name visible text (KHÔNG icon-only — đã có name kế thừa).
- Hover state KHÔNG là chỉ baseline visibility (color contrast OK với `--on-surface` trên `--surface-container-lowest` BG).

### PDP Breadcrumb
- Toàn bộ segments là `<Link>` ngoại trừ current `<span>` (correct semantic).
- Brand link `aria-label="Xem sản phẩm thương hiệu {product.brand}"` cho clarity (optional — link text đã đủ trong context).
- Separator `/` là `<span>` decorative (KHÔNG `aria-hidden` cần thiết vì screen reader thường skip punctuation).

### PDP Thumbnail Gallery
- Mỗi thumbnail `<button type="button" aria-label="Xem ảnh {i+1}" aria-current={i === selectedImage ? "true" : undefined}>` — `aria-current` chỉ set khi active, undefined khi inactive (cleaner DOM).
- Active border `2px var(--primary)` cung cấp visual cue + `aria-current` cung cấp screen reader cue → dual-channel feedback.
- Focus visible kế thừa global `:focus-visible` 2px outline.
- Optional keyboard arrow nav (D-11): nếu implement, cập nhật `tabindex` + `focus()` programmatically — đảm bảo focus theo selected image.

### PDP Stock Badge
- Badge text PHẢI bao gồm semantic icon prefix (`✓ ⚠ ✗`) — color KHÔNG là kênh thông tin duy nhất (WCAG 1.4.1 Use of Color). Screen reader đọc text + icon naturally.
- "Hết hàng" badge `aria-label` mặc định = visible text (đủ — không cần override).
- Out-of-stock message `<p>` plain — screen reader đọc tự nhiên, không cần `role="alert"` (không phải transient notification, là persistent state).

### General
- Mọi interactive element keyboard accessible (button + link semantic, tabindex correct).
- Color contrast WCAG AA:
  - Stock badge success: text `#15803d` trên BG `rgba(22,163,74,0.15)` → contrast ratio ≥ 4.5:1 (verified visually green-on-light-green readable).
  - Stock badge warning: text `#b45309` trên BG `rgba(245,158,11,0.18)` → contrast ratio ≥ 4.5:1.
  - Stock badge danger: text `#93000a` trên BG `#ffdad6` → contrast ratio ≥ 4.5:1 (kế thừa M3 error palette designed cho AA).
- KHÔNG axe-core gate (D-deferred v1.3) — accessibility manual review đủ cho phase 15.

---

## Registry Safety

| Registry | Blocks Used | Safety Gate |
|----------|-------------|-------------|
| shadcn official | none | not applicable (project KHÔNG init shadcn — vanilla CSS Modules) |
| third-party | none | not applicable |

Phase 15 KHÔNG thêm dependency npm mới (next/image + CSS scroll-snap đã có). KHÔNG cần registry vetting gate.

---

## Visual Layout Sketches

### Homepage `/`

```
┌─ Header (existing — sticky glass) ────────────────────────┐
│                                                            │
├─ HERO (.hero, 2 cols, min-height 75vh, gradient BG) ──────┤
│  ┌─ .heroContent ──────┐  ┌─ .heroVisual (asymmetric) ──┐│
│  │ [Badge: Bộ sưu tập] │  │ ┌──────────────────────────┐ ││
│  │                     │  │ │ <Image priority>          │ ││
│  │ Nghệ thuật          │  │ │  hero-primary.webp        │ ││
│  │ chế tác thủ công    │  │ │  (75% × 85%, top-right)   │ ││
│  │ (gradient accent)   │  │ │                            │ ││
│  │                     │  │ └──────────────────────────┘ ││
│  │ Khám phá bộ sưu tập │  │     ┌─────────────────┐      ││
│  │ thu đông...         │  │     │ <Image>         │      ││
│  │                     │  │     │  hero-secondary │      ││
│  │ [Khám phá ngay]     │  │     │  (45%×50%, bot-l│      ││
│  │ [Xem tất cả sản phẩm│  │     └─────────────────┘      ││
│  └─────────────────────┘  └──────────────────────────────┘│
├─ CATEGORIES (.categoriesSection, BG container-low) ────────┤
│  Danh mục sản phẩm                                         │
│  Khám phá các dòng sản phẩm...                             │
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐                       │
│  │ Cat1 │ │ Cat2 │ │ Cat3 │ │ Cat4 │  (4 cột desktop)     │
│  └──────┘ └──────┘ └──────┘ └──────┘                       │
├─ FEATURED (.productsSection — CAROUSEL) ───────────────────┤
│  Sản phẩm nổi bật              [Xem tất cả →]              │
│  Những sản phẩm được yêu thích nhất...                     │
│  ┌─.featuredScroll (overflow-x: auto, scroll-snap) ──────┐│
│  │ ┌──────┐┌──────┐┌──────┐┌──────┐┌──→ scroll          ││
│  │ │Card1 ││Card2 ││Card3 ││Card4 ││ peek next         ││
│  │ │280px ││280px ││280px ││280px ││                    ││
│  │ └──────┘└──────┘└──────┘└──────┘                      ││
│  └────────────────────────────────────────────────────────┘│
├─ VALUE PROPS (existing — KHÔNG đổi) ──────────────────────┤
├─ NEW ARRIVALS (.productsSection — GRID) ───────────────────┤
│  Sản phẩm mới                  [Xem tất cả →]              │
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐                       │
│  │Card9 │ │Card10│ │Card11│ │Card12│  (slice(8,16))       │
│  └──────┘ └──────┘ └──────┘ └──────┘                       │
└────────────────────────────────────────────────────────────┘
```

### PDP `/products/[slug]`

```
┌─ Breadcrumb (BG container-low) ────────────────────────────┐
│  Trang chủ / [Brand link] / [Product Name (current)]       │
│  Trang chủ / Sản phẩm / [Product Name]   ← fallback no-brand│
├─ Product Section (2 cols) ─────────────────────────────────┤
│  ┌─ Gallery (sticky) ──┐  ┌─ Info ───────────────────────┐│
│  │  ┌─ Main Image ──┐  │  │ {Category}                    ││
│  │  │ <Image fill>  │  │  │ {Brand}                       ││
│  │  └───────────────┘  │  │ Product Name (h1)             ││
│  │  ┌──┐┌──┐┌──┐┌──┐   │  │ ★★★★☆ 4.5 (12 đánh giá)       ││
│  │  │T1││T2││T3││T4│   │  │ Short description             ││
│  │  └──┘└──┘└──┘└──┘   │  │ ┌─ Price block ────────┐     ││
│  │  ↑ active border 2px│  │  │ {price} VND          │     ││
│  │    var(--primary)   │  │  │ {original} [Sale 20%]│     ││
│  └─────────────────────┘  │  └──────────────────────┘     ││
│                           │ ┌─ STOCK BADGE (3-tier) ──┐  ││
│                           │  │ stock >= 10:           │  ││
│                           │  │  [✓ Còn hàng] (green)  │  ││
│                           │  │ stock 1-9:             │  ││
│                           │  │  [⚠ Sắp hết hàng (3)]  │  ││
│                           │  │     (yellow/amber)     │  ││
│                           │  │ stock 0:               │  ││
│                           │  │  [✗ Hết hàng] (red)    │  ││
│                           │  └────────────────────────┘  ││
│                           │ stock > 0:                    ││
│                           │  ┌─ Quantity ─┐ ┌─ Add cart ┐││
│                           │  │ [-] 1 [+]  │ │ Thêm vào..││
│                           │  └────────────┘ └───────────┘││
│                           │ stock = 0:                    ││
│                           │  Sản phẩm tạm hết — vui lòng  ││
│                           │  quay lại sau.                ││
│                           │                                ││
│                           │ [♡ Xem giỏ hàng] (always show)││
│                           │ ┌─ Guarantees (3 items) ──┐  ││
│                           └────────────────────────────┘  │
├─ Tabs Section (Mô tả / Thông số / Đánh giá) ──────────────┤
└─ Related Products (4 cols grid) ───────────────────────────┘
```

---

## Pre-Populated From

| Source | Decisions Used |
|--------|---------------|
| `15-CONTEXT.md` | D-01 (next/image priority + WebP local), D-02 (CTA secondary copy + link `/products`), D-03 (LCP target 2.5s), D-04 (sort createdAt,desc), D-05 (CSS scroll-snap carousel + card width 280/240), D-06 (header + RetrySection giữ nguyên), D-07 (categories link `?category=`), D-08 (responsive 4↔2), D-09 (single-fetch slice(8,16) dedupe), D-10 (new arrivals grid không carousel), D-11 (thumbnail polish a11y + active border 2px primary), D-12 (specs frontend `specifications`), D-13 (specs empty copy), D-14 (breadcrumb refactor + brand fallback), D-15 (3-tier stock badge color + count rule), D-16 (hide add-to-cart + inline message + secondary button stays) |
| `15-RESEARCH.md` | A1 success/warning token gap → fallback hex + CSS variable pattern, Pattern 1 (next/image priority sizes), Pattern 2 (scroll-snap CSS), Pattern 3 (3-tier conditional render), Pitfall 2 (single-fetch dedupe), Pitfall 3 (carousel a11y tabindex/role/aria-label), Pitfall 6 (Badge variant naming), file-by-file touch map |
| `ROADMAP.md §"Phase 15"` | SC-1 (hero LCP + carousel + dedupe), SC-2 (PDP breadcrumb brand), SC-3 (3-tier badge + hide add-cart) |
| `REQUIREMENTS.md §"PUB"` | PUB-01..04 acceptance criteria |
| `14-UI-SPEC.md` | Format precedent (table structure, accent reserved-for, registry safety pattern) + breadcrumb brand link contract `/products?brand={X}` (Phase 14 SEARCH-01) |
| `globals.css` (verified) | All tokens reused — `--primary #0040a1`, `--error #ba1a1a`, `--surface*`, `--text-*`, `--space-*` (8-point), `--radius-*`, `--font-be-vietnam-pro`. Confirmed gap: `--success` + `--warning` KHÔNG tồn tại → fallback hex spec'd. |
| `Badge.module.css` (verified) | Existing variants `default | sale | new | hot | out-of-stock` — Phase 15 add `success | warning | danger` (3 mới). |
| `[slug]/page.module.css` (verified) | Breadcrumb classes `.breadcrumbLink/.breadcrumbSep/.breadcrumbCurrent` — REUSE; thumbnail classes `.thumbnail/.thumbnailActive` — modify border color/width; stock classes `.inStock/.outOfStock` — remove (replace by Badge component). |
| User input (orchestrator prompt) | Vietnamese-first copy, 5-area scope clarification, 3-tier stock color semantics, hide-not-disable add-to-cart |

---

## Checker Sign-Off

- [ ] Dimension 1 Copywriting: PASS
- [ ] Dimension 2 Visuals: PASS
- [ ] Dimension 3 Color: PASS
- [ ] Dimension 4 Typography: PASS
- [ ] Dimension 5 Spacing: PASS
- [ ] Dimension 6 Registry Safety: PASS

**Approval:** pending
