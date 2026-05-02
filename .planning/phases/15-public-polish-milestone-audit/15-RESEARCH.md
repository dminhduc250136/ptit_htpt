# Phase 15: Public Polish + Milestone Audit — Research

**Researched:** 2026-05-02
**Domain:** Frontend polish (Next.js App Router, CSS scroll-snap, next/image LCP, Playwright smoke E2E) + milestone closure
**Confidence:** HIGH (toàn bộ stack đã có precedent trong codebase v1.0–v1.1)

---

## Summary

Phase 15 là phase **polish + alignment**, KHÔNG phải build từ zero. Phần lớn structural code (homepage hero/categories/featured/latest, PDP gallery/specs/breadcrumb/stock badge, Playwright config + global-setup) đã tồn tại từ Phase 5/7/9. Phase 15 thay 5 thứ chính:
1. Hero `<img>` Unsplash → `next/image priority` + WebP local trong `public/hero/` (PUB-01).
2. Featured sort `reviewCount,desc` → `createdAt,desc` + render dạng CSS scroll-snap carousel (PUB-01).
3. New Arrivals dedupe rule (skip first 8 — PUB-02).
4. PDP breadcrumb `Home > Sản phẩm > {Cat} > {Name}` → `Home > {Brand} > {Name}` + stock badge 2-tier → 3-tier + hide add-to-cart khi stock=0 (PUB-03, PUB-04).
5. 4 smoke E2E tests trong file mới `e2e/smoke.spec.ts` (TEST-02).

Đóng milestone v1.2: chạy `/gsd-audit-milestone v1.2` → tag `v1.2` annotated.

**Primary recommendation:** Triển khai theo thứ tự FE pages (homepage trước, PDP sau) → smoke E2E → milestone audit. Tận dụng tối đa pattern existing (`Promise.all`, `RetrySection`, `<Badge>`, design tokens, storageState fixture).

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Hero (PUB-01)**
- D-01: Thay `<img>` Unsplash → `next/image priority` + WebP local `public/hero/hero-primary.webp` (~600×800) + `hero-secondary.webp` (~400×500). KHÔNG remote loader.
- D-02: Hero copy + Badge + 2 CTA giữ nguyên. CTA thứ 2 đổi thành `/products` label "Xem tất cả sản phẩm" (tránh `/collections` 404).
- D-03: LCP target < 2.5s đo qua Lighthouse Chrome DevTools mobile preset trên `next build && next start`. KHÔNG thêm `<link rel="preload">` thủ công.

**Featured Products (PUB-01)**
- D-04: Sort `reviewCount,desc` → `createdAt,desc` (match ROADMAP SC-1).
- D-05: Render dạng horizontal CSS scroll-snap carousel — KHÔNG JS lib. Card width 280px desktop / 240px mobile, parent `overflow-x: auto; scroll-snap-type: x mandatory`, child `scroll-snap-align: start`. Hide scrollbar `scrollbar-width: none` + `::-webkit-scrollbar { display: none }`. KHÔNG prev/next arrow.
- D-06: Header "Sản phẩm nổi bật" + "Xem tất cả →" giữ nguyên. Loading skeleton + RetrySection giữ nguyên.

**Categories Grid (PUB-02)**
- D-07: Giữ link `/products?category={slug}` (KHÔNG đổi sang brand).
- D-08: Grid responsive 4 cột desktop / 2 cột mobile, design tokens `var(--space-*)`. 1 icon SVG dùng chung.

**New Arrivals (PUB-02)**
- D-09: Dedupe rule = `page=1, size=8, sort=createdAt,desc` (skip first 8 — Featured = top-8). Trade-off: catalog < 16 → empty hoặc < 8 items, acceptable.
- D-10: Render grid 4 cột (KHÔNG carousel) — emphasize "browse all".

**PDP Thumbnail Gallery (PUB-03)**
- D-11: Thumbnail strip + main swap đã có sẵn — giữ implementation. Polish: `aria-label="Xem ảnh {i+1}"` + `aria-current={i === selectedImage}` + active border 2px `var(--primary)`. Keyboard arrow nav: Claude's discretion (nếu < 30 LOC).

**PDP Specs Table (PUB-03)**
- D-12: Specs table giữ nguyên. Frontend dùng `product.specifications` (NOT `product.specs`).
- D-13: Empty state copy giữ "Chưa có thông số kỹ thuật."

**PDP Breadcrumb (PUB-03)**
- D-14: Refactor → `Home > {Brand} > {Name}`. Brand link `/products?brand={brand}` (Phase 14 contract). Fallback nếu không có brand: `Home > Sản phẩm > {Name}`.

**PDP Stock Badge (PUB-04)**
- D-15: 3-tier badge:
  - `stock >= 10` → green "✓ Còn hàng" (bỏ count detail)
  - `1 <= stock < 10` → yellow "⚠ Sắp hết hàng (còn {stock})" — show count tạo urgency
  - `stock === 0` → red "✗ Hết hàng"
  Dùng `<Badge>` component (cần extend variant `success/warning/danger`) hoặc inline span với token `var(--success/warning/error)`.
- D-16: Hide add-to-cart hoàn toàn khi `stock === 0` (return null), thay bằng inline message "Sản phẩm tạm hết — vui lòng quay lại sau." Quantity selector cũng hide. "Xem giỏ hàng" secondary button vẫn hiển thị.

**Smoke E2E (TEST-02)**
- D-17: 4 tests, 1 file mới `e2e/smoke.spec.ts`:
  1. Homepage navigation — load `/`, hero render, click "Khám phá ngay" → `/products` có ít nhất 1 ProductCard.
  2. Address-at-checkout — login (storageState), add to cart, `/checkout`, AddressPicker → chọn → submit → success page/toast.
  3. Review submission — login, mở PDP product DELIVERED, tab Reviews, submit rating=5 + content → review xuất hiện.
  4. Profile editing — login, `/profile/settings`, sửa fullName + phone, submit → toast success + reload persist.
- D-18: Test data setup dựa fixture/seed hiện tại (`db/init/`). KHÔNG seed mới. Nếu thiếu DELIVERED order: chạy test #2 trước → admin script update DELIVERED → test #3.
- D-19: PASS criteria — 4/4 PASS trên `docker compose up -d --build && npm run test:e2e -- smoke.spec.ts` từ fresh stack. Baseline (auth, admin-products/orders/users, order-detail, password-change) vẫn 100% PASS.
- D-20: KHÔNG add tests cho search filters Phase 14. KHÔNG visual regression.

**Milestone Audit**
- D-21: Sau 5 REQs ship xong, chạy `/gsd-audit-milestone v1.2`. Verify 17/17 active REQs satisfied OR documented gap.
- D-22: Tag `v1.2` annotated trên main: `git tag -a v1.2 -m "Milestone v1.2 — UI/UX Completion"`. KHÔNG push tự động.
- D-23: Update `.planning/MILESTONES.md` (mark v1.2 complete + completion date) + `.planning/PROJECT.md` (advance current milestone pointer).

### Claude's Discretion
- CSS class names cho `featuredScroll` carousel section.
- Exact dimension/aspect ratio cho hero WebP (khớp visual hiện tại là ưu tiên).
- Quantity selector hide vs disable khi stock=0 — Claude pick consistent với D-16.
- Keyboard arrow nav cho thumbnail (D-11) — implement nếu < 30 LOC.
- Order test execution + cách tạo DELIVERED order data cho smoke test #3.
- Inline copy chi tiết stock badge tiếng Việt natural.

### Deferred Ideas (OUT OF SCOPE)
- PDP fullscreen lightbox (`yet-another-react-lightbox`) — v1.3
- axe-core a11y gate — v1.3
- Playwright full E2E suite 8+ tests — v1.3
- `featured BOOLEAN` column trên ProductEntity
- Shop by Brand grid riêng trên homepage
- Visual regression (Percy/Chromatic/Playwright snapshots)
- Hero A/B testing slot
- Per-category icon SVG
- Lighthouse CI gate
- Hero CTA `/collections` route
- Stock badge animation/pulse
- JS carousel lib (slick/swiper/embla)
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PUB-01 | Homepage hero + featured products | next/image priority pattern; CSS scroll-snap carousel pattern; sort param `createdAt,desc` đã hỗ trợ qua `listProducts({ sort })` (services/products.ts:36) |
| PUB-02 | Categories grid + new arrivals dedupe | `listProducts` + `listCategories` đã có; dedupe rule = skip first 8 dùng `Array.slice(8)` hoặc fetch `page=1, size=16` rồi `.slice(8)` |
| PUB-03 | PDP thumbnail gallery + specs + breadcrumb | Existing structure tại `[slug]/page.tsx:159-171, 105-121, 321-337` — chỉ cần refactor breadcrumb + a11y polish |
| PUB-04 | Stock badge 3-tier + hide add-to-cart | Existing 2-tier tại `[slug]/page.tsx:209-216, 218-263` — refactor sang 3-tier + conditional render |
| TEST-02 | Smoke E2E 4 tests | Playwright config + storageState fixture pattern đã có (`global-setup.ts`, `auth.spec.ts` precedent) |
</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Hero LCP optimization | Browser (next/image client load) | Frontend Server (SSR markup + preload) | next/image `priority` injects `<link rel="preload">` server-side, browser fetches WebP early |
| Featured carousel (CSS scroll-snap) | Browser (CSS layout + native scroll) | — | Pure CSS, không JS runtime |
| Categories/New Arrivals fetch | Frontend Server (SSR shell) + Browser (client fetch via `'use client'`) | API/Backend (product-svc list endpoint) | Hiện tại `'use client'` pattern, có thể giữ hoặc convert RSC sau (out of scope) |
| PDP breadcrumb với brand link | Browser (client-side render) | API/Backend (Phase 14 brand filter contract) | Link target `/products?brand=X` → FilterSidebar parse |
| Stock badge color logic | Browser (conditional render từ `product.stock`) | — | Pure presentation, no API change |
| Smoke E2E | Playwright runner (Node) | Browser (Chromium) + Docker stack (BE services) | Black-box test toàn stack |
| Milestone audit | GSD orchestrator (audit doc + git tag) | — | Local workflow, không deploy |

**[VERIFIED: codebase grep `app/page.tsx`, `[slug]/page.tsx`, `services/products.ts`]**

---

## Standard Stack

### Core (đã có sẵn — KHÔNG install gì mới)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| next | 16.x (App Router) | Framework | Đã có. `next/image` core component cho LCP. **[VERIFIED: package.json + next.config.ts có `turbopack` config Next 16]** |
| react | 19.x | UI runtime | Đã có |
| @playwright/test | 1.x | E2E framework | Đã có (Phase 9 baseline) **[VERIFIED: playwright.config.ts]** |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `next/image` (built-in) | bundled | Image optimization + LCP preload | Hero images (D-01) + PDP gallery (đã dùng) |
| `next/link` (built-in) | bundled | Client-side nav | Breadcrumb segments + CTA |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| CSS scroll-snap | embla-carousel-react / swiper | Lib = +30-80KB JS, JS-driven scroll, accessibility tự build. CSS scroll-snap = native, 0 JS, browser-default a11y. CSS đủ cho v1.2 per D-05. |
| Inline stock badge span | Extend `<Badge>` variant `success/warning/danger` | Extending Badge consistent với design system; inline span ngắn hơn cho 1-off. **Recommendation:** Extend Badge (3 variant mới) — clean reuse cho future. |
| `next/image priority` preload tự động | `<link rel="preload">` thủ công trong `<head>` | Next.js auto-injects khi `priority`. Manual preload risk duplicate. D-03 confirm: KHÔNG manual. |

**Installation:** Không cần install gì mới.

**Version verification:** Skip (không thêm dependency).

---

## Architecture Patterns

### System Architecture Diagram

```
┌──────────────────────────────────────────────────────────────────┐
│  Browser (Chromium)                                              │
│  ┌─────────────────────────────────────────────────────────┐     │
│  │  / (Homepage)                                            │     │
│  │   ├─ Hero: <Image priority src="/hero/hero-primary..."> │     │
│  │   │    └─ next/image preload <link> in <head>           │     │
│  │   ├─ Categories grid → Link /products?category={slug}   │     │
│  │   ├─ Featured carousel (CSS scroll-snap) ──┐            │     │
│  │   └─ New Arrivals grid (skip first 8) ────┤            │     │
│  │                                              │            │     │
│  │  /products/[slug] (PDP)                     │            │     │
│  │   ├─ Breadcrumb: Home > {Brand} > {Name}    │            │     │
│  │   │    └─ Brand → Link /products?brand={X}  │            │     │
│  │   ├─ Gallery (thumbnail+main, existing)     │            │     │
│  │   ├─ Stock badge: 3-tier color              │            │     │
│  │   │    ├─ stock>=10 → green "Còn hàng"     │            │     │
│  │   │    ├─ stock 1-9 → yellow "Sắp hết..."  │            │     │
│  │   │    └─ stock=0  → red "Hết hàng" + hide  │            │     │
│  │   │                  add-to-cart            │            │     │
│  │   └─ Specs table (tab "Thông số")           │            │     │
│  └─────────────────────────────────────────────┼────────────┘     │
│                                                  │                  │
└──────────────────────────────────────────────────┼──────────────────┘
                          │                       │
                          ▼                       ▼
              ┌────────────────────┐    ┌──────────────────────┐
              │  /api/products?... │    │  Static assets       │
              │  (gateway → product│    │  /hero/*.webp        │
              │   -svc list)       │    │  (Next.js public/)   │
              └────────────────────┘    └──────────────────────┘
                                                  
┌──────────────────────────────────────────────────────────────────┐
│  Smoke E2E (Playwright + Docker Compose stack)                   │
│   global-setup.ts → login user/admin → save storageState         │
│   smoke.spec.ts:                                                  │
│     test 1 (anon)  → / hero + click CTA → /products              │
│     test 2 (user)  → cart → /checkout → AddressPicker → submit   │
│     test 3 (user)  → PDP DELIVERED prod → review form → list     │
│     test 4 (user)  → /profile/settings → form → toast + persist  │
└──────────────────────────────────────────────────────────────────┘
```

### Recommended Project Structure (file changes only — không cần new folders trừ public/hero/)

```
sources/frontend/
├── public/
│   └── hero/                       # MỚI
│       ├── hero-primary.webp       # MỚI ~600×800, ~150-200KB
│       └── hero-secondary.webp     # MỚI ~400×500, ~100-150KB
├── src/app/
│   ├── page.tsx                    # MODIFY: hero <img>→<Image>, sort, carousel, dedupe
│   ├── page.module.css             # MODIFY: thêm .featuredScroll + scrollbar hide
│   └── products/[slug]/
│       ├── page.tsx                # MODIFY: breadcrumb, stock badge, hide add-to-cart
│       └── page.module.css         # MODIFY: stock badge color tokens, thumbnail polish
├── src/components/ui/Badge/
│   ├── Badge.tsx                   # MODIFY: thêm variant 'success' | 'warning' | 'danger'
│   └── Badge.module.css            # MODIFY: 3 class CSS mới
└── e2e/
    └── smoke.spec.ts               # MỚI: 4 test cases
```

### Pattern 1: next/image với priority cho LCP hero

**What:** Inject `<link rel="preload" as="image">` server-side để browser fetch hero ảnh sớm.

**When to use:** Element above-the-fold là Largest Contentful Paint candidate (typically hero banner).

**Example:**
```tsx
// Source: Next.js docs https://nextjs.org/docs/app/api-reference/components/image#priority
import Image from 'next/image';

<div className={styles.heroImagePrimary}>
  <Image
    src="/hero/hero-primary.webp"
    alt="Sản phẩm nổi bật"
    fill
    sizes="(max-width: 768px) 100vw, 50vw"
    priority
    className={styles.heroImg}
  />
</div>
```
**[CITED: nextjs.org/docs/app/api-reference/components/image#priority]** — `priority` prop boolean, only one or two above-fold per page.

**Notes:**
- `fill` requires parent có `position: relative` (đã có trong `.heroImagePrimary`).
- `sizes` chính xác giúp Next pick smallest WebP variant trong srcset.
- KHÔNG cần `width/height` khi dùng `fill`.
- Local WebP trong `public/` → Next.js server WebP-as-is (không re-encode lần nữa, nhưng vẫn sinh srcset).

### Pattern 2: CSS scroll-snap horizontal carousel

**What:** Native browser scroll với snap-to-card behavior, 0 JS.

**When to use:** Horizontal browse flow, mobile-friendly swipe, không cần advanced controls (autoplay, infinite loop).

**Example:**
```css
/* Source: MDN https://developer.mozilla.org/en-US/docs/Web/CSS/scroll-snap-type */
.featuredScroll {
  display: flex;
  gap: var(--space-4);
  overflow-x: auto;
  scroll-snap-type: x mandatory;
  scroll-padding: var(--space-4);
  -webkit-overflow-scrolling: touch; /* iOS momentum */
  scrollbar-width: none;             /* Firefox */
}
.featuredScroll::-webkit-scrollbar { display: none; } /* Chrome/Safari */

.featuredScroll > * {
  flex: 0 0 280px;                   /* fixed card width desktop */
  scroll-snap-align: start;
}

@media (max-width: 768px) {
  .featuredScroll > * { flex-basis: 240px; }
}
```
**[CITED: developer.mozilla.org/en-US/docs/Web/CSS/CSS_scroll_snap]**

**Markup:**
```tsx
<div className={styles.featuredScroll} role="region" aria-label="Sản phẩm nổi bật">
  {featured.map((p) => <ProductCard key={p.id} product={p} />)}
</div>
```

### Pattern 3: Stock badge 3-tier conditional render

**What:** Pure presentation logic dựa `product.stock`, dùng existing Badge component.

**Example:**
```tsx
// Tại [slug]/page.tsx — replace lines 209-216
{product.stock >= 10 && (
  <Badge variant="success">✓ Còn hàng</Badge>
)}
{product.stock > 0 && product.stock < 10 && (
  <Badge variant="warning">⚠ Sắp hết hàng (còn {product.stock})</Badge>
)}
{product.stock === 0 && (
  <Badge variant="danger">✗ Hết hàng</Badge>
)}

// Replace lines 218-263 actions section:
{product.stock > 0 ? (
  <>
    <div className={styles.actions}>
      <div className={styles.quantitySelector}>...</div>
      <Button size="lg" fullWidth loading={addingToCart} onClick={...}>
        Thêm vào giỏ hàng
      </Button>
    </div>
  </>
) : (
  <p className={styles.outOfStockMessage}>
    Sản phẩm tạm hết — vui lòng quay lại sau.
  </p>
)}
```

### Pattern 4: Playwright smoke với storageState

**What:** Reuse login state từ `global-setup.ts` qua `test.use({ storageState: ... })` — tránh login lặp lại.

**Example:**
```typescript
// e2e/smoke.spec.ts
import { test, expect } from '@playwright/test';

// Test 1: anonymous
test.describe('SMOKE-1: Homepage navigation', () => {
  test.use({ storageState: { cookies: [], origins: [] } });
  test('hero render + CTA → /products', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByRole('heading', { name: /chế tác thủ công/i })).toBeVisible();
    await page.getByRole('link', { name: 'Khám phá ngay' }).click();
    await page.waitForURL(/\/products/);
    await expect(page.locator('[class*="ProductCard"]').first()).toBeVisible({ timeout: 10000 });
  });
});

// Test 2-4: authenticated
test.describe('SMOKE-2..4: authenticated flows', () => {
  test.use({ storageState: 'e2e/storageState/user.json' });
  test('SMOKE-2: address-at-checkout', async ({ page }) => { /* ... */ });
  test('SMOKE-3: review submission', async ({ page }) => { /* ... */ });
  test('SMOKE-4: profile editing', async ({ page }) => { /* ... */ });
});
```
**[VERIFIED: codebase pattern in `e2e/order-detail.spec.ts:20`, `e2e/auth.spec.ts:20`]**

### Anti-Patterns to Avoid
- **Manual `<link rel="preload">` cho hero image khi đã có `priority`:** Next.js inject duplicate → browser warning + double fetch overhead. **[ASSUMED — practical knowledge, not verified this session]**
- **Đặt `priority` trên nhiều ảnh trong cùng page:** Defeat purpose of preload prioritization. Chỉ 1-2 ảnh above-fold. **[CITED: nextjs.org/docs/app/api-reference/components/image#priority]**
- **JS-driven carousel khi CSS scroll-snap đủ:** Bloat bundle, accessibility regressions, mobile gesture conflicts. Per D-05.
- **Hardcode stock thresholds (10, 0) ở nhiều nơi:** Centralize qua const `STOCK_LOW_THRESHOLD = 10` trong file PDP hoặc `services/products.ts`.
- **Smoke test phụ thuộc test order ngầm:** Playwright default chạy theo file order; nếu test 3 cần data từ test 2, dùng `test.describe.serial` explicit hoặc setup fixture/API call riêng.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Image LCP preload | Manual `<link rel="preload">` + custom srcset | `next/image priority` | Next handles preload + responsive srcset + AVIF/WebP negotiation tự động |
| Horizontal carousel | JS event handlers cho swipe/wheel | CSS `scroll-snap-*` | Native browser support (95%+ browsers per caniuse), 0 JS overhead, accessible by default |
| Test login state | Login mỗi test | `globalSetup` + `storageState` JSON | Đã pattern ở Phase 9, giảm flakiness, tăng speed (login 1 lần thay vì N lần) |
| Stock-aware add-to-cart guard | Custom hook `useStockGuard` | Inline conditional render | 1 PDP page only — abstract premature; 5 dòng JSX đủ |

**Key insight:** Phase 15 = polish phase, **không phải lúc add abstractions**. Tận dụng tối đa existing components (Badge, Button, ProductCard, RetrySection) và browser primitives (next/image, CSS scroll-snap, storageState).

---

## Common Pitfalls

### Pitfall 1: Hydration mismatch khi dùng `next/image` trong `'use client'` page với hero
**What goes wrong:** Server render không có user agent hint → fallback srcset; client hydrate với responsive srcset → browser fetch lại ảnh khác.
**Why it happens:** `next/image` đôi khi sinh khác markup giữa SSR và CSR khi sizes prop edge case.
**How to avoid:** Đặt `sizes` prop chính xác và stable (string literal không thay đổi giữa renders). KHÔNG dùng `priority` cho ảnh con của component có `useState`-driven visibility.
**Warning signs:** React warning "Hydration failed" trong dev console; LCP fluctuate giữa loads.
**Confidence:** **[ASSUMED — based on training knowledge of next/image quirks; recommend testing in dev mode after refactor]**

### Pitfall 2: Featured + New Arrivals fetch race / dedupe drift
**What goes wrong:** Nếu Featured fetch và New Arrivals fetch là 2 query riêng (cùng `sort=createdAt,desc`), backend có thể trả overlapping results nếu pagination shifts giữa 2 calls.
**Why it happens:** 2 round-trips → race với product writes; dedupe `slice(8)` chỉ đúng nếu cùng dataset.
**How to avoid:** Dùng **1 fetch** `listProducts({ size: 16, sort: 'createdAt,desc' })` rồi `.slice(0, 8)` cho Featured + `.slice(8, 16)` cho New Arrivals. Hoặc Promise.all 2 calls riêng nhưng accept slight overlap (low traffic OK).
**Warning signs:** Cùng product xuất hiện ở cả Featured và New Arrivals.
**Recommendation:** Single fetch — đơn giản hơn, deterministic dedupe.
**Confidence:** HIGH (logic deterministic verifiable bằng test).

### Pitfall 3: CSS scroll-snap conflict với keyboard/screen reader nav
**What goes wrong:** Mặc định `overflow-x: auto` element không focusable bằng Tab; user phím không scroll được.
**Why it happens:** Browser require `tabindex="0"` để keyboard scroll, plus `role="region"` + `aria-label` cho screen reader.
**How to avoid:** Add `role="region" aria-label="Sản phẩm nổi bật" tabindex="0"` trên `.featuredScroll`. Test: Tab focus container, arrow keys scroll.
**Warning signs:** Lighthouse a11y warning "Scrollable element không focusable".
**Confidence:** **[CITED: w3.org/WAI/ARIA/apg/patterns scroll region]** + **[ASSUMED behavior detail]**

### Pitfall 4: Smoke test #3 thiếu DELIVERED order trong fresh stack
**What goes wrong:** Seed `V100__seed_dev_data.sql` có thể KHÔNG include order DELIVERED cho user `demo@tmdt.local` → test 3 fails REVIEW_NOT_ELIGIBLE.
**Why it happens:** Verified-buyer eligibility check (Phase 13 REV-01) gate review submission.
**How to avoid:** 2 chiến lược:
  1. **Test ordering:** test 2 place order → admin script (or direct SQL) update status DELIVERED → test 3 review. Yêu cầu `test.describe.serial`.
  2. **Seed addition:** thêm 1 DELIVERED order vào seed dev SQL — trái D-18 ("KHÔNG seed mới") nhưng chỉ thay đổi seed-dev không production.
**Recommendation:** Strategy 1 (serial test ordering) — match D-18 + Claude's discretion D-18.
**Confidence:** HIGH (bug đã thấy precedent trong order-detail.spec.ts trying to find existing order).

### Pitfall 5: Hero WebP file size > 200KB → defeat LCP gain
**What goes wrong:** WebP ảnh 1MB → next/image preload nhưng download time vẫn long → LCP > 2.5s.
**How to avoid:** Compress WebP target 80-150KB (primary) + 60-100KB (secondary). Tools: `cwebp -q 80 in.jpg -o out.webp`, hoặc squoosh.app online.
**Warning signs:** Lighthouse LCP > 2.5s desktop / > 4s mobile.
**Confidence:** HIGH (web.dev/lcp performance budget).

### Pitfall 6: `Badge` variant CSS class collision khi extend
**What goes wrong:** Thêm variant `success` vào Badge → CSS rule `.success` đã exist global hoặc trong other module.
**Why it happens:** CSS Modules scope OK, nhưng `Badge.module.css` đang dùng kebab `out-of-stock` → mới `success/warning/danger` cần consistent naming.
**How to avoid:** Dùng exact tên `success | warning | danger` (lowercase, no kebab) → match Badge.tsx union type. Verify tokens: `var(--success)`, `var(--warning)`, `var(--error)` exist trong theme; nếu không, fallback hex hoặc `var(--secondary-container)`/`var(--error-container)` (đã exist).
**Recommendation:** Check `globals.css` xem có `--success`/`--warning` token chưa trước khi viết Badge variant.

---

## Runtime State Inventory

> Phase 15 chủ yếu thêm/sửa file source + 2 file WebP mới trong `public/`. KHÔNG có rename, refactor scope rộng, hay migration data. Section này áp dụng minimal:

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None — verified by phase scope (FE-only + 2 WebP assets) | None |
| Live service config | None — không thay đổi gateway/service config | None |
| OS-registered state | None — không touch systemd/scheduled tasks | None |
| Secrets/env vars | None — smoke test reuse `E2E_ADMIN_EMAIL/PASSWORD` env vars đã có | None (verify env hiện tại) |
| Build artifacts | next.js `.next/` cache có thể stale sau khi đổi sang `next/image` priority — KHÔNG tự update đáng lo, build sạch via `rm -rf .next && npm run build` nếu LCP đo lệch | Optional: clean rebuild trước Lighthouse measure |

---

## Code Examples

### Verified pattern: Replace hero `<img>` → `<Image priority>`

```tsx
// app/page.tsx — replace lines 80-97
<div className={styles.heroVisual}>
  <div className={styles.heroImagePrimary}>
    <Image
      src="/hero/hero-primary.webp"
      alt="Sản phẩm nổi bật"
      fill
      sizes="(max-width: 1024px) 80vw, 45vw"
      priority
      className={styles.heroImg}
    />
  </div>
  <div className={styles.heroImageSecondary}>
    <Image
      src="/hero/hero-secondary.webp"
      alt="Phụ kiện cao cấp"
      fill
      sizes="(max-width: 1024px) 50vw, 25vw"
      className={styles.heroImg}
    />
  </div>
</div>
```
**Source:** `[CITED: nextjs.org/docs/app/api-reference/components/image]`

### Verified pattern: Featured carousel + dedupe single-fetch

```tsx
// app/page.tsx — replace lines 28-36
const resp = await listProducts({ size: 16, sort: 'createdAt,desc' }).catch(() =>
  listProducts({ size: 16 }),
);
const all = resp?.content ?? [];
setFeatured(all.slice(0, 8));
setLatest(all.slice(8, 16));

// JSX render featured as carousel
<div className={styles.featuredScroll} role="region" aria-label="Sản phẩm nổi bật" tabIndex={0}>
  {featured.map((p) => (
    <div key={p.id} className={styles.featuredCard}>
      <ProductCard product={p} />
    </div>
  ))}
</div>
```

### Verified pattern: PDP breadcrumb với brand fallback

```tsx
// [slug]/page.tsx — replace lines 105-121
<div className={styles.breadcrumb}>
  <div className={styles.container}>
    <Link href="/" className={styles.breadcrumbLink}>Trang chủ</Link>
    <span className={styles.breadcrumbSep}>/</span>
    {product.brand ? (
      <Link
        href={`/products?brand=${encodeURIComponent(product.brand)}`}
        className={styles.breadcrumbLink}
      >
        {product.brand}
      </Link>
    ) : (
      <Link href="/products" className={styles.breadcrumbLink}>Sản phẩm</Link>
    )}
    <span className={styles.breadcrumbSep}>/</span>
    <span className={styles.breadcrumbCurrent}>{product.name}</span>
  </div>
</div>
```

### Verified pattern: Smoke test #2 (address-at-checkout) skeleton

```typescript
// e2e/smoke.spec.ts
test('SMOKE-2: address-at-checkout flow', async ({ page }) => {
  // 1. Navigate to PDP & add to cart
  await page.goto('/products');
  const firstProductLink = page.locator('a[href^="/products/"]').first();
  await firstProductLink.click();
  await page.waitForURL(/\/products\/[^?]+/);
  await page.getByRole('button', { name: /thêm vào giỏ/i }).click();
  // Wait toast
  await expect(page.getByText(/đã thêm/i).first()).toBeVisible({ timeout: 5000 });

  // 2. Goto checkout
  await page.goto('/checkout');
  await page.waitForLoadState('networkidle');

  // 3. AddressPicker present (Phase 11)
  const addressOption = page.locator('[class*="addressItem"], [data-testid="address-option"]').first();
  await expect(addressOption).toBeVisible({ timeout: 10000 });
  await addressOption.click();

  // 4. Submit order
  await page.getByRole('button', { name: /đặt hàng|submit/i }).click();

  // 5. Assert success
  await page.waitForURL(/\/orders|\/profile|success/, { timeout: 15000 });
  // Hoặc toast success visible
  // expect.soft truthy assertion vì có thể redirect or toast
});
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `<img>` tag với external CDN URL | `next/image` priority + local WebP | Next.js 13+ App Router | LCP improvement, automatic preload, srcset |
| JS carousel libs (slick, owl) | CSS scroll-snap | CSS spec stable 2018+, browser support 95%+ caniuse | 0 JS overhead, native a11y |
| Login mỗi Playwright test | `globalSetup` + `storageState` | Playwright 1.x stable | Faster suites, less flakiness |

**Deprecated/outdated:**
- Featured sort `reviewCount,desc` proxy — legacy from pre-Phase-15 mock days. ROADMAP locked `createdAt,desc`.
- 2-tier stock badge — pre-PUB-04. Industry pattern là 3-tier (out-of-stock / low-stock urgency / in-stock).

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `--success`, `--warning`, `--error` design tokens exist trong globals.css | Pitfall 6 / D-15 | Need fallback hex `#16a34a / #f59e0b / #dc2626` per CONTEXT.md specifics §"Stock badge color semantic" |
| A2 | `priority` + `useState` in client component không cause hydration mismatch trong práctice | Pitfall 1 | Recommend dev-mode test; nếu fail → split hero thành RSC component (out of scope refactor) |
| A3 | Hero WebP ~150-200KB target đủ cho LCP < 2.5s trên `next start` | Pitfall 5 | Nếu vẫn slow → giảm dimension hoặc tăng quality compression. Verify Lighthouse trước commit. |
| A4 | `db/init/` seed có sẵn ít nhất 1 user `demo@tmdt.local` + product list active đủ cho smoke tests 1, 2, 4 | D-18 | Test #2 fails nếu seed empty products; test #4 fails nếu user không có saved address (nhưng test này check edit form, không require address) |
| A5 | Smoke test #3 sẽ cần test ordering (serial) hoặc admin script update DELIVERED status | D-18, Pitfall 4 | Risk: nếu admin endpoint không cho update status arbitrary, cần seed 1 DELIVERED order — trái D-18 |
| A6 | `next/image` với local file trong `public/` KHÔNG cần thêm `remotePatterns` config (chỉ cần cho external) | next.config.ts | LOW risk — Next docs xác nhận local public/ files automatic |

---

## Open Questions (RESOLVED)

1. **Test #3 DELIVERED order data strategy** — RESOLVED: Strategy A (skip-if-no-DELIVERED) per Plan 15-00 Task 3 + `15-SELECTOR-AUDIT.md`. Smoke test #3 sẽ skip gracefully nếu không có DELIVERED order trong seed; KHÔNG block phase.
2. **`--success`/`--warning` design tokens existence** — RESOLVED: Verified KHÔNG tồn tại trong `globals.css` (Plan 15-00 Task 1 grep). Badge `.success`/`.warning` dùng fallback hex `#15803d` text + rgba background tints; danger reuse `--error-container` đã có. Implementation in Plan 15-00 Task 2.
3. **`/checkout` page tồn tại và AddressPicker selector pattern** — RESOLVED: Verified per Plan 15-00 Task 3 + `15-SELECTOR-AUDIT.md` — selector `[role="option"]` ổn định cho AddressPicker; ProfileSettings selectors recorded trong audit doc. Smoke spec dùng các selectors này.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Node.js | Build, Playwright | ✓ | (detected via `next.config.ts` Next 16) | — |
| Docker Compose | Smoke E2E fresh stack | ? (host-dependent) | TBD | Smoke tests cần BE running — nếu host không Docker, run tests against existing dev stack |
| Chromium (Playwright) | Smoke E2E | ✓ via `@playwright/test` install | 1.x | — |
| `cwebp` / image compressor | Hero WebP creation | ? | TBD | Online tool squoosh.app — manual upload, không block automation |
| Lighthouse (Chrome DevTools) | LCP measurement (D-03) | ✓ via Chrome browser | — | — |

**Missing dependencies with no fallback:** None blocking — Docker stack assumption đã standard cho project Phase 9+.

**Missing dependencies with fallback:**
- WebP encoder không cài local → dùng squoosh.app web tool (manual step trong plan).

---

## Validation Architecture

> `workflow.nyquist_validation: true` trong `.planning/config.json` → section MANDATORY.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Playwright `@playwright/test` 1.x (đã có Phase 9) + Next.js `next build` (type check + lint compile) |
| Config file | `sources/frontend/playwright.config.ts` (existing) |
| Quick run command | `cd sources/frontend && npx playwright test e2e/smoke.spec.ts --reporter=list` |
| Full suite command | `cd sources/frontend && npm run test:e2e` (chạy toàn bộ baseline + smoke) |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| PUB-01 | Hero `<Image priority>` render + LCP measurable | manual visual + Lighthouse | Manual: `next build && next start` → Chrome DevTools Lighthouse mobile preset | ❌ Manual checklist (Wave 0) |
| PUB-01 | Featured carousel scroll-snap render top-8 createdAt,desc | E2E smoke + visual | `npx playwright test e2e/smoke.spec.ts -g "SMOKE-1"` | ❌ smoke.spec.ts Wave 0 |
| PUB-02 | Categories grid + New Arrivals dedupe (no overlap) | unit (logic) + visual | Manual: open `/`, inspect Featured + New Arrivals, verify no shared product IDs | ❌ Manual checklist (Wave 0) |
| PUB-03 | PDP breadcrumb `Home > {Brand} > {Name}` clickable | E2E smoke (covered indirectly trong test #3) | `npx playwright test e2e/smoke.spec.ts -g "SMOKE-3"` | ❌ smoke.spec.ts Wave 0 |
| PUB-03 | PDP thumbnail click → main swap (existing) | manual visual | Manual: open PDP product có >1 ảnh, click thumb, verify main đổi | ❌ Manual checklist |
| PUB-04 | Stock badge 3-tier color + hide add-to-cart | manual visual + unit conditional logic | Manual: tạo 3 product (stock=0, 5, 100), verify badge color + cart button visibility | ❌ Manual checklist (Wave 0) |
| TEST-02 | 4 smoke tests PASS fresh stack | E2E | `docker compose up -d --build && cd sources/frontend && npx playwright test e2e/smoke.spec.ts` | ❌ smoke.spec.ts Wave 0 |
| TEST-02 | Baseline tests vẫn 100% PASS | E2E regression | `cd sources/frontend && npm run test:e2e` | ✓ existing |
| (build gate) | TypeScript + Lint + Build success | compile | `cd sources/frontend && npm run build` | ✓ existing |

### Sampling Rate
- **Per task commit:** `cd sources/frontend && npm run build` (TypeScript compile + Next build = quick gate, ~30-60s)
- **Per wave merge:** `cd sources/frontend && npx playwright test e2e/smoke.spec.ts --reporter=list` (4 tests, ~30-60s với storageState reuse)
- **Phase gate:** Full suite `npm run test:e2e` green + manual visual checklist completed + Lighthouse LCP < 2.5s confirmed before `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `sources/frontend/e2e/smoke.spec.ts` — covers TEST-02 (4 tests)
- [ ] `sources/frontend/public/hero/hero-primary.webp` — asset cho PUB-01
- [ ] `sources/frontend/public/hero/hero-secondary.webp` — asset cho PUB-01
- [ ] Manual visual checklist doc (suggest: `15-MANUAL-CHECKLIST.md` trong phase folder) — covers PUB-01 LCP, PUB-02 dedupe, PUB-03 thumbnail swap, PUB-04 stock badge tiers
- [ ] Verify `--success`/`--warning` tokens exist trong `globals.css` — nếu không, plan addition trong `Badge.module.css` Wave 0
- [ ] Confirm DELIVERED order existence cho test #3 (grep V100__seed_dev_data.sql) — nếu không có, decide test ordering vs seed addition

---

## Project Constraints (from CLAUDE.md)

> `./CLAUDE.md` không tồn tại trong working directory (verified Step 1). Project-level constraints lấy từ memory files:
- **Vietnamese language:** Toàn bộ chat/docs/commits dùng tiếng Việt; identifiers + commit prefixes giữ EN.
- **Project nature:** Thử nghiệm GSD workflow, KHÔNG phải PTIT/HTPT student assignment.
- **End-user-visible priority:** Ưu tiên UI/UX visible features, defer backend hardening/security/observability — Phase 15 align hoàn hảo (FE polish + smoke E2E only).

---

## File-by-File Touch Map

| File | Action | Lines (approx) | Plan Mapping |
|------|--------|----------------|--------------|
| `sources/frontend/public/hero/hero-primary.webp` | CREATE (binary asset, ~150KB WebP) | — | PUB-01 |
| `sources/frontend/public/hero/hero-secondary.webp` | CREATE (binary asset, ~100KB WebP) | — | PUB-01 |
| `sources/frontend/src/app/page.tsx` | MODIFY | lines 28-36 (single-fetch + dedupe), 80-97 (hero `<img>` → `<Image>`), 138-170 (featured wrap trong scroll container), 220-252 (latest = `latest.slice(8)` if single-fetch model, hoặc giữ separate fetch) | PUB-01, PUB-02 |
| `sources/frontend/src/app/page.module.css` | MODIFY | thêm `.featuredScroll`, `.featuredCard`, scrollbar hide rules | PUB-01 |
| `sources/frontend/src/app/products/[slug]/page.tsx` | MODIFY | lines 105-121 (breadcrumb refactor), 159-171 (thumbnail a11y polish), 209-216 (stock badge 3-tier), 218-263 (hide add-to-cart conditional) | PUB-03, PUB-04 |
| `sources/frontend/src/app/products/[slug]/page.module.css` | MODIFY | stock badge color tokens (nếu inline), thumbnail active border 2px `var(--primary)`, `outOfStockMessage` style | PUB-03, PUB-04 |
| `sources/frontend/src/components/ui/Badge/Badge.tsx` | MODIFY | extend `BadgeVariant` union: `'success' \| 'warning' \| 'danger'` | PUB-04 |
| `sources/frontend/src/components/ui/Badge/Badge.module.css` | MODIFY | thêm 3 class CSS `.success`, `.warning`, `.danger` (verify `--success`/`--warning` tokens hoặc fallback hex) | PUB-04 |
| `sources/frontend/e2e/smoke.spec.ts` | CREATE | 4 tests (1 anon + 3 user storageState), ~150-200 LOC | TEST-02 |
| `sources/frontend/playwright.config.ts` | OPTIONAL MODIFY | thêm `@smoke` tag pattern nếu muốn `--grep @smoke` filter (Claude's discretion) | TEST-02 |
| `.planning/MILESTONES.md` | MODIFY | mark v1.2 complete + completion date | Milestone closure D-23 |
| `.planning/PROJECT.md` | MODIFY (nếu có pointer "current milestone") | advance pointer | Milestone closure D-23 |
| `.planning/milestones/v1.2-MILESTONE-AUDIT.md` | CREATE (via `/gsd-audit-milestone`) | audit doc generated | D-21 |
| Git tag `v1.2` | CREATE annotated | `git tag -a v1.2 -m "Milestone v1.2 — UI/UX Completion"` | D-22 |

**Total: ~12 file changes (8 modify + 4 create) + 1 git tag.**

---

## Sources

### Primary (HIGH confidence)
- `nextjs.org/docs/app/api-reference/components/image` — `priority` prop, `fill`, `sizes`, local public/ files **[CITED]**
- `developer.mozilla.org/en-US/docs/Web/CSS/CSS_scroll_snap` — scroll-snap-type, scroll-snap-align **[CITED]**
- Codebase grep — `app/page.tsx`, `[slug]/page.tsx`, `services/products.ts`, `playwright.config.ts`, `e2e/global-setup.ts`, `e2e/auth.spec.ts`, `e2e/order-detail.spec.ts`, `Badge.tsx`, `Badge.module.css`, `next.config.ts`, `.planning/config.json` **[VERIFIED this session]**
- `.planning/REQUIREMENTS.md`, `.planning/ROADMAP.md`, `.planning/phases/15-*/15-CONTEXT.md` **[VERIFIED]**

### Secondary (MEDIUM confidence)
- web.dev/lcp performance budget guidance (training data)
- Playwright docs `globalSetup` + `storageState` pattern (training + verified codebase precedent Phase 9)

### Tertiary (LOW confidence — flagged in Assumptions Log)
- A1: `--success`/`--warning` token existence — needs grep verify
- A2: hydration mismatch risk specific cho Next 16 — based on Next 13/14 prior knowledge
- A5: admin status update endpoint availability — needs codebase grep order-svc

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — toàn bộ existing, không add dependency mới
- Architecture: HIGH — pattern đã có precedent (FilterSidebar Phase 14, ReviewSection Phase 13, AddressPicker Phase 11, global-setup Phase 9)
- Pitfalls: MEDIUM — A1/A2/A5 cần verify trong implementation
- Smoke E2E: MEDIUM — test #3 DELIVERED order strategy chưa locked

**Research date:** 2026-05-02
**Valid until:** 2026-06-01 (~30 days — stack stable, không có fast-moving dependency)

---

## RESEARCH COMPLETE

**Phase:** 15 - Public Polish + Milestone Audit
**Confidence:** HIGH overall (MEDIUM for smoke test #3 data strategy)

### Key Findings
- Phase 15 = polish/alignment phase, KHÔNG cần install dependency mới (next/image, CSS scroll-snap, Playwright đều có sẵn).
- Single-fetch + `slice(0,8)` / `slice(8,16)` đơn giản hơn và deterministic hơn 2 separate fetches cho dedupe.
- Badge component cần extend 3 variant `success/warning/danger` — verify `--success`/`--warning` tokens trước (Wave 0 task).
- Smoke test #3 (review submission) cần test ordering strategy hoặc seed DELIVERED order — Open Question #1.
- LCP measurement phải chạy trên `next build && next start` (KHÔNG dev mode) — D-03 + Pitfall 5.

### File Created
`.planning/phases/15-public-polish-milestone-audit/15-RESEARCH.md`

### Confidence Assessment
| Area | Level | Reason |
|------|-------|--------|
| Standard Stack | HIGH | All existing in codebase, verified |
| Architecture | HIGH | Pattern precedent từ Phase 9-14 |
| Pitfalls | MEDIUM | A1/A2/A5 assumptions cần verify |
| Smoke E2E test #3 | MEDIUM | DELIVERED order data strategy chưa lock |

### Open Questions
1. Test #3 DELIVERED order strategy (test ordering vs seed addition vs admin status update endpoint).
2. `--success`/`--warning` design tokens existence trong globals.css (Wave 0 grep).
3. `/checkout` AddressPicker selector pattern (Wave 0 grep).

### Ready for Planning
Research complete. Planner có thể tạo PLAN.md files theo file-by-file touch map. Đề xuất plan structure: Wave 0 (asset prep + token verify + selector audit) → Wave 1 (homepage refactor PUB-01/02) → Wave 2 (PDP refactor PUB-03/04) → Wave 3 (smoke E2E TEST-02) → Wave 4 (milestone audit + tag v1.2).
