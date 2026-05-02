# Phase 15: Public Polish + Milestone Audit — Pattern Map

**Mapped:** 2026-05-02
**Files analyzed:** 9 (6 source + 1 e2e + 2 binary asset)
**Analogs found:** 8 / 9 (binary assets không có code analog)

> Phase 15 = polish/refactor — phần lớn target files đã tồn tại và sẽ MODIFY chính nó. "Closest analog" trong nhiều case là **chính file đó** (existing pattern trong file là tham chiếu chính). E2E test mới + Badge variant extend có analog ngoài.

---

## File Classification

| File | Action | Role | Data Flow | Closest Analog | Match Quality |
|------|--------|------|-----------|----------------|---------------|
| `sources/frontend/src/app/page.tsx` | MODIFY | page component (`'use client'`) | request-response (fetch on mount) | self (lines 21-53 load pattern) + `next/image` precedent từ `[slug]/page.tsx:130-137` | exact (self-pattern) |
| `sources/frontend/src/app/page.module.css` | MODIFY (thêm `.featuredScroll`) | CSS Module | static styling | self (no scroll-snap precedent in codebase — first usage) + MDN scroll-snap reference | partial (no in-codebase analog cho carousel) |
| `sources/frontend/src/app/products/[slug]/page.tsx` | MODIFY (breadcrumb + stock + add-cart) | page component (`'use client'`) | request-response | self (existing breadcrumb 105-121 + stock 209-216 + actions 218-263) | exact (self-pattern) |
| `sources/frontend/src/app/products/[slug]/page.module.css` | MODIFY (thumbnail border + outOfStockMessage) | CSS Module | static styling | self (lines 124-126 thumbnailActive + design tokens) | exact (self-pattern) |
| `sources/frontend/src/components/ui/Badge/Badge.tsx` | MODIFY (extend variant union) | UI primitive component | presentational | self — variant union pattern line 4 | exact |
| `sources/frontend/src/components/ui/Badge/Badge.module.css` | MODIFY (3 class mới) | CSS Module | static styling | self — variant class pattern lines 21-44 (`.sale`, `.new`, `.hot`, `.out-of-stock`) | exact |
| `sources/frontend/e2e/smoke.spec.ts` | CREATE | Playwright E2E spec | scenario test | `e2e/auth.spec.ts` (anon flow) + `e2e/order-detail.spec.ts` (storageState user flow) + `e2e/password-change.spec.ts` (form submit + assertion) + `e2e/global-setup.ts` (login fixture) | exact (4 strong analogs) |
| `sources/frontend/public/hero/hero-primary.webp` | CREATE binary | static asset | n/a | none — first WebP in `public/` | none (asset only) |
| `sources/frontend/public/hero/hero-secondary.webp` | CREATE binary | static asset | n/a | none — first WebP in `public/` | none (asset only) |

---

## Pattern Assignments

### `app/page.tsx` (page component, request-response)

**Analog 1 (self):** Existing load pattern + `Promise.all` parallel fetch.

**Imports + state pattern** (lines 1-19):
```tsx
'use client';
import React, { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import styles from './page.module.css';
import ProductCard from '@/components/ui/ProductCard/ProductCard';
import Button from '@/components/ui/Button/Button';
import Badge from '@/components/ui/Badge/Badge';
import RetrySection from '@/components/ui/RetrySection/RetrySection';
import { listProducts, listCategories } from '@/services/products';
import type { Product, Category } from '@/types';

export default function Home() {
  const [featured, setFeatured] = useState<Product[]>([]);
  const [latest, setLatest] = useState<Product[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
```
**Action for Phase 15:** ADD `import Image from 'next/image';`. Giữ nguyên state shape.

**Existing dual-fetch pattern (REFACTOR target — lines 28-37):**
```tsx
const [featuredResp, latestResp] = await Promise.all([
  listProducts({ size: 8, sort: 'reviewCount,desc' }).catch(() =>
    listProducts({ size: 8 }),
  ),
  listProducts({ size: 8, sort: 'createdAt,desc' }).catch(() =>
    listProducts({ size: 8 }),
  ),
]);
setFeatured(featuredResp?.content ?? []);
setLatest(latestResp?.content ?? []);
```
**Action for Phase 15 (D-04 + D-09 + Pitfall 2):** REPLACE 2 fetches bằng 1 single-fetch:
```tsx
const resp = await listProducts({ size: 16, sort: 'createdAt,desc' }).catch(() =>
  listProducts({ size: 16 }),
);
const all = resp?.content ?? [];
setFeatured(all.slice(0, 8));
setLatest(all.slice(8, 16));
```
Lý do: deterministic dedupe (cùng dataset) + 1 round-trip thay vì 2.

**Loading/error/empty triad (REUSE — lines 152-168):**
```tsx
{loading ? (
  <div className={styles.productsGrid}>
    {[...Array(4)].map((_, i) => (
      <div key={i} className="skeleton" style={{ height: 360, borderRadius: 'var(--radius-lg)' }} />
    ))}
  </div>
) : failed ? (
  <RetrySection onRetry={() => load()} loading={loading} />
) : featured.length > 0 ? (
  <div className={styles.productsGrid}>
    {featured.map((product) => (
      <ProductCard key={product.id} product={product} />
    ))}
  </div>
) : (
  <p style={{ color: 'var(--on-surface-variant)' }}>Chưa có sản phẩm nổi bật.</p>
)}
```
**Action for Phase 15:** WRAP `featured.map(...)` trong `<div className={styles.featuredScroll} role="region" aria-label="Sản phẩm nổi bật — vuốt ngang để xem thêm" tabIndex={0}>` thay vì `<div className={styles.productsGrid}>`. Skeleton + RetrySection + empty state giữ nguyên (D-06). Cho New Arrivals (lines 234-250) giữ nguyên `.productsGrid` (D-10 — grid 4 cột).

**Analog 2 (cross-file): `next/image priority`** từ `app/products/[slug]/page.tsx:130-137`:
```tsx
<Image
  src={product.images?.[selectedImage] ?? product.thumbnailUrl}
  alt={product.name}
  fill
  sizes="(max-width: 768px) 100vw, 50vw"
  className={styles.mainImg}
  priority
/>
```
**Action for Phase 15 (D-01):** REPLACE 2 `<img>` Unsplash blocks (lines 81-97) bằng:
```tsx
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
```
KHÔNG đặt `priority` cho secondary (chỉ 1 LCP ảnh).

**Hero CTA copy refactor (D-02 — lines 73-78):**
```tsx
<div className={styles.heroActions}>
  <Button href="/products" size="lg">Khám phá ngay</Button>
  <Button href="/collections" variant="secondary" size="lg">
    Xem bộ sưu tập
  </Button>
</div>
```
**Action:** Đổi `href="/collections"` → `href="/products"` và copy → "Xem tất cả sản phẩm" (tránh 404).

---

### `app/page.module.css` (CSS Module, scroll-snap container)

**Analog (self):** CSS variable usage pattern + responsive breakpoints (lines 240-310).

**Existing token usage to copy** (lines 188-192):
```css
.productsGrid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-4);
}
```

**Existing responsive pattern** (lines 278-303):
```css
@media (max-width: 768px) {
  .heroTitle {
    font-size: var(--text-display-sm);
  }
  .productsGrid {
    grid-template-columns: repeat(2, 1fr);
  }
}
```

**Action for Phase 15 (D-05):** ADD new classes (no in-codebase scroll-snap analog — first usage; reference MDN pattern from RESEARCH.md):
```css
.featuredScroll {
  display: flex;
  gap: var(--space-4);
  overflow-x: auto;
  scroll-snap-type: x mandatory;
  scroll-padding: var(--space-4);
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;       /* Firefox */
  padding-bottom: var(--space-2); /* breathing room cho scroll cue */
}
.featuredScroll::-webkit-scrollbar { display: none; } /* Chrome/Safari */

.featuredCard {
  flex: 0 0 280px;
  scroll-snap-align: start;
}

@media (max-width: 768px) {
  .featuredCard { flex-basis: 240px; }
}
```
Strict reuse `var(--space-4)` token (KHÔNG hard-code `16px`).

---

### `app/products/[slug]/page.tsx` (page component, breadcrumb + stock + add-cart refactor)

**Analog (self):** Existing breadcrumb structure + Badge usage + button conditional.

**Breadcrumb pattern (REFACTOR target — lines 105-121):**
```tsx
<div className={styles.breadcrumb}>
  <div className={styles.container}>
    <Link href="/" className={styles.breadcrumbLink}>Trang chủ</Link>
    <span className={styles.breadcrumbSep}>/</span>
    <Link href="/products" className={styles.breadcrumbLink}>Sản phẩm</Link>
    {product.category && (
      <>
        <span className={styles.breadcrumbSep}>/</span>
        <Link href={`/products?category=${product.category.slug}`} className={styles.breadcrumbLink}>
          {product.category.name}
        </Link>
      </>
    )}
    <span className={styles.breadcrumbSep}>/</span>
    <span className={styles.breadcrumbCurrent}>{product.name}</span>
  </div>
</div>
```
**Action for Phase 15 (D-14):** REPLACE bằng brand-based breadcrumb:
```tsx
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
Reuse existing CSS classes — KHÔNG cần thay `.breadcrumbLink/.breadcrumbSep/.breadcrumbCurrent`.

**Existing Badge usage pattern (REFERENCE — lines 138-156):**
```tsx
{product.tags.map((tag) => (
  <Badge
    key={tag}
    variant={
      tag.toLowerCase().includes('sale') ? 'sale' :
      tag.toLowerCase().includes('new') ? 'new' :
      tag.toLowerCase().includes('best') ? 'hot' : 'default'
    }
  >
    {tag}
  </Badge>
))}
```
**Action for Phase 15 (D-15):** Apply same `<Badge variant="...">` pattern cho 3-tier stock — REPLACE lines 209-216:
```tsx
<div className={styles.stockInfo}>
  {(product.stock ?? 0) >= 10 && (
    <Badge variant="success">✓ Còn hàng</Badge>
  )}
  {(product.stock ?? 0) > 0 && (product.stock ?? 0) < 10 && (
    <Badge variant="warning">⚠ Sắp hết hàng (còn {product.stock})</Badge>
  )}
  {(product.stock ?? 0) === 0 && (
    <Badge variant="danger">✗ Hết hàng</Badge>
  )}
</div>
```

**Existing add-to-cart button pattern (REFACTOR target — lines 218-263):**
```tsx
<div className={styles.actions}>
  <div className={styles.quantitySelector}>
    <button className={styles.qtyBtn} onClick={() => setQuantity(...)} disabled={quantity <= 1}>−</button>
    <span className={styles.qtyValue}>{quantity}</span>
    <button className={styles.qtyBtn} onClick={() => setQuantity(...)} disabled={quantity >= (product.stock || 1)}>+</button>
  </div>
  <Button size="lg" fullWidth disabled={product.stock === 0} loading={addingToCart} onClick={async () => { ... addToCart(...) ... }}>
    {product.stock === 0 ? 'Hết hàng' : 'Thêm vào giỏ hàng'}
  </Button>
</div>
```
**Action for Phase 15 (D-16):** WRAP toàn bộ `.actions` block trong conditional `{(product.stock ?? 0) > 0 ? (...) : (<p className={styles.outOfStockMessage}>Sản phẩm tạm hết — vui lòng quay lại sau.</p>)}`. BỎ `disabled={product.stock === 0}` + label `'Hết hàng'` ternary trên Button vì branch stock=0 không bao giờ render. KHÔNG đụng "♡ Xem giỏ hàng" button (line 265-267) — vẫn render unconditionally.

**Thumbnail polish (lines 159-171):**
```tsx
{product.images.map((img, i) => (
  <button
    key={i}
    className={`${styles.thumbnail} ${i === selectedImage ? styles.thumbnailActive : ''}`}
    onClick={() => setSelectedImage(i)}
  >
    <Image src={img} alt={`${product.name} - ${i + 1}`} fill sizes="80px" className={styles.thumbImg} />
  </button>
))}
```
**Action for Phase 15 (D-11):** ADD a11y attrs lên `<button>`:
```tsx
<button
  key={i}
  type="button"
  aria-label={`Xem ảnh ${i + 1}`}
  aria-current={i === selectedImage ? 'true' : undefined}
  className={...}
  onClick={() => setSelectedImage(i)}
>
```
Optional keyboard arrow nav: SKIP nếu > 30 LOC (D-11 Claude's discretion).

---

### `app/products/[slug]/page.module.css` (CSS Module)

**Analog (self):** Token usage + thumbnail active state.

**Existing thumbnail active style (REFACTOR target — lines 124-126):**
```css
.thumbnailActive {
  border-color: var(--primary-container) !important;
}
```
**Action for Phase 15 (D-11):** TĂNG contrast — đổi `--primary-container` → `--primary` + tăng `border-width` từ `2px` (đã có baseline `.thumbnail` line 116) cũng giữ 2px nhưng đậm hơn. Final:
```css
.thumbnailActive {
  border-color: var(--primary) !important;
  border-width: 2px;
}
```

**Existing stock CSS (REMOVE — lines 217-229):** `.inStock` + `.outOfStock` không còn dùng (Badge component thay thế). Có thể xóa hoặc giữ làm dead code (recommend xóa cho clean).

**Action for Phase 15 (D-16):** ADD new `.outOfStockMessage` class:
```css
.outOfStockMessage {
  font-size: var(--text-body-md);
  font-weight: var(--weight-medium);
  color: var(--on-surface-variant);
  padding: var(--space-3) var(--space-4);
  background: var(--surface-container-low);
  border-radius: var(--radius-lg);
}
```

---

### `components/ui/Badge/Badge.tsx` (UI primitive — extend variant union)

**Analog (self):** Variant union type pattern.

**Existing variant union (line 4):**
```tsx
export type BadgeVariant = 'default' | 'sale' | 'new' | 'hot' | 'out-of-stock';
```
**Action for Phase 15 (D-15):** EXTEND union:
```tsx
export type BadgeVariant =
  | 'default'
  | 'sale'
  | 'new'
  | 'hot'
  | 'out-of-stock'
  | 'success'
  | 'warning'
  | 'danger';
```
KHÔNG đụng component body (lines 12-22) — class lookup `styles[variant]` đã generic.

---

### `components/ui/Badge/Badge.module.css` (CSS Module — extend 3 classes)

**Analog (self):** Existing variant class pattern (lines 21-44).

**Existing variant CSS pattern (lines 26-44):**
```css
.sale {
  background-color: var(--secondary-container);
  color: var(--on-secondary);
}
.new {
  background-color: var(--primary-fixed);
  color: var(--on-primary-fixed);
}
.hot {
  background-color: var(--secondary);
  color: var(--on-secondary);
}
.out-of-stock {
  background-color: var(--error-container);
  color: var(--on-error-container);
}
```
**Action for Phase 15 (D-15 + UI-SPEC color contract):** ADD 3 classes (verified `--success`/`--warning` KHÔNG tồn tại trong globals.css → MUST dùng fallback hex):
```css
.success {
  background-color: rgba(22, 163, 74, 0.15); /* fallback for color-mix(--success, 15%) */
  color: #15803d; /* green-700 — WCAG AA on light tint */
}

.warning {
  background-color: rgba(245, 158, 11, 0.18);
  color: #b45309; /* amber-700 — WCAG AA */
}

.danger {
  background-color: var(--error-container);
  color: var(--on-error-container);
}
```
**Note:** `.danger` reuse existing M3 error tokens (KHÔNG cần token mới). `.success` + `.warning` HARD-CODED hex theo UI-SPEC fallback contract — acceptable per RESEARCH A1 + UI-SPEC line 110. Recommend (Claude's discretion executor): consider `var(--success, #16a34a)` pattern nếu muốn future-proof khi tokens được add v1.3.

---

### `e2e/smoke.spec.ts` (CREATE — Playwright E2E)

**Analog 1: `e2e/auth.spec.ts`** — Anonymous flow pattern (lines 17-43).

**Pattern: anonymous test với cleared storageState** (auth.spec.ts:17-20):
```typescript
import { test, expect } from '@playwright/test';

// Anonymous tests — không reuse storageState (mỗi test tự clear state)
test.use({ storageState: { cookies: [], origins: [] } });
```
**Apply to SMOKE-1 (homepage navigation):** Use cleared storageState trong describe block.

**Analog 2: `e2e/order-detail.spec.ts`** — Authenticated user flow with storageState (lines 17-32).

**Pattern: storageState user fixture + navigation assertion** (order-detail.spec.ts:17-32):
```typescript
import { test, expect } from '@playwright/test';

test.use({ storageState: 'e2e/storageState/user.json' });

test('ORD-DTL-1: /profile render sau khi login qua storageState', async ({ page }) => {
  await page.goto('/profile');
  expect(page.url()).not.toContain('/login');
  await page.waitForLoadState('domcontentloaded');
  const pageTitle = await page.title();
  expect(pageTitle).not.toMatch(/404|error/i);
});
```
**Apply to SMOKE-2/3/4:** Reuse `'e2e/storageState/user.json'` cho 3 user flows.

**Pattern: skip-if-no-data degradation** (order-detail.spec.ts:50-53):
```typescript
const hasFromList = await orderLinkFromList.isVisible({ timeout: 5000 }).catch(() => false);
if (!hasFromList) {
  test.skip(true, 'User demo không có đơn hàng — cần đặt hàng trước khi chạy test này');
  return;
}
```
**Apply to SMOKE-3 (review submission):** If no DELIVERED order found cho user, `test.skip(true, 'No DELIVERED order — chạy test #2 trước hoặc seed DELIVERED order')` thay vì fail.

**Analog 3: `e2e/password-change.spec.ts`** — Form submit + assertion pattern (lines 22-40).

**Pattern: form fill + submit + assertion với confirmed selectors** (password-change.spec.ts:22-40):
```typescript
test.use({ storageState: 'e2e/storageState/user.json' });

test('PWD-1: ...', async ({ page }) => {
  await page.goto('/profile/settings');
  await page.waitForLoadState('domcontentloaded');
  await page.fill('input#oldPassword', 'SaiPasswordKhongDung999');
  await page.fill('input#newPassword', 'NewPass123');
  await page.fill('input#confirmPassword', 'NewPass123');
  await page.click('[data-testid="submitPassword"]');
});
```
**Apply to SMOKE-4 (profile editing):** Same pattern — `getByLabel` cho fullName/phone (Vietnamese labels), `data-testid` cho submit nếu có. **TODO:** Wave 0 grep `app/profile/settings/page.tsx` confirm exact selectors.

**Analog 4: `e2e/global-setup.ts`** — Login + storageState save (lines 30-53).

**Pattern: storageState reuse — KHÔNG cần re-implement login trong smoke.spec.ts.** Smoke tests chỉ `test.use({ storageState: ... })`.

**Header docstring convention** (auth.spec.ts:1-16, password-change.spec.ts:1-20):
```typescript
/**
 * Phase 15 / Plan 15-XX (TEST-02) — Smoke E2E (4 tests).
 * D-17/D-18/D-19: 4 critical paths cho v1.2 milestone closure.
 *
 * Tests:
 *   SMOKE-1 (anon): Homepage hero render + CTA → /products
 *   SMOKE-2 (user): Cart → /checkout → AddressPicker → submit order
 *   SMOKE-3 (user): PDP DELIVERED product → review submit → assertion
 *   SMOKE-4 (user): /profile/settings edit fullName + phone → toast persist
 *
 * Selectors confirmed từ ... (Wave 0 grep)
 * Dùng user storageState từ global-setup.ts (Phase 9 D-13).
 */
```
**Apply:** Follow same docstring convention với phase ref + test list + selector source.

**Final structure for `smoke.spec.ts`:**
```typescript
import { test, expect } from '@playwright/test';

test.describe('SMOKE-1: Homepage navigation (anonymous)', () => {
  test.use({ storageState: { cookies: [], origins: [] } });
  test('hero render + CTA "Khám phá ngay" → /products có ProductCard', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByRole('heading', { name: /chế tác thủ công/i })).toBeVisible();
    await page.getByRole('link', { name: 'Khám phá ngay' }).click();
    await page.waitForURL(/\/products/);
    await expect(page.locator('[class*="ProductCard"]').first()).toBeVisible({ timeout: 10000 });
  });
});

test.describe('SMOKE-2..4: authenticated flows', () => {
  test.use({ storageState: 'e2e/storageState/user.json' });
  test('SMOKE-2: address-at-checkout', async ({ page }) => { /* ... */ });
  test('SMOKE-3: review submission (skip nếu no DELIVERED)', async ({ page }) => { /* ... */ });
  test('SMOKE-4: profile editing persist', async ({ page }) => { /* ... */ });
});
```

---

### `public/hero/hero-primary.webp` + `hero-secondary.webp` (CREATE binary)

**No code analog.** Asset prep task:
- Source: download 2 ảnh hiện tại từ Unsplash URLs trong `app/page.tsx:84,92` (`?w=600` cho primary, `?w=400` cho secondary).
- Convert to WebP: `cwebp -q 80` hoặc squoosh.app.
- Target size: ~150-200KB primary, ~100-150KB secondary.
- Aspect ratio: bám parent container (primary ~600×800 portrait-ish, secondary ~400×500 portrait-ish — match `.heroImagePrimary` 75%×85% + `.heroImageSecondary` 45%×50%).
- Place: `sources/frontend/public/hero/` (mkdir mới).
- Verify path matches `<Image src="/hero/hero-primary.webp">` trong page.tsx refactor.

---

## Shared Patterns

### Authentication (E2E)
**Source:** `e2e/global-setup.ts:30-53` + Phase 9 D-13.
**Apply to:** SMOKE-2, SMOKE-3, SMOKE-4.
```typescript
test.use({ storageState: 'e2e/storageState/user.json' });
```
KHÔNG re-login trong smoke spec — global-setup đã handle.

### Loading/Error/Empty Triad (UI)
**Source:** `app/page.tsx:152-168` (Phase 5 standardization).
**Apply to:** Featured carousel + New Arrivals grid (giữ nguyên — D-06).
```tsx
{loading ? <Skeleton/> : failed ? <RetrySection/> : data.length > 0 ? <Render/> : <Empty/>}
```

### Design Token Reuse (CSS)
**Source:** `globals.css` (M3-style tokens) + `Badge.module.css`/`page.module.css` precedent.
**Apply to:** ALL CSS Modules trong phase 15.
- Spacing: `var(--space-1..7)` (8-point scale).
- Color: `var(--primary)`, `var(--error-container)`, `var(--surface-container-*)`, etc.
- Radius: `var(--radius-sm/lg/xl/2xl/full)`.
- Typography: `var(--text-*)`, `var(--weight-*)`, `var(--font-family-*)`.
- **Exception:** `.success` + `.warning` Badge — hard-code hex (tokens không tồn tại — RESEARCH A1 verified).

### Vietnamese-First Copy
**Source:** Toàn bộ codebase (per `feedback_language.md`).
**Apply to:** Stock badge text, breadcrumb fallback "Sản phẩm", out-of-stock message, smoke test docstring comments. Identifiers + commit prefixes giữ EN.

### Fetch Error Handling (`.catch` fallback)
**Source:** `app/page.tsx:29-34` existing `.catch(() => listProducts(...))` pattern.
**Apply to:** Single-fetch refactor — preserve `.catch` fallback nếu sort param bị backend reject.
```tsx
const resp = await listProducts({ size: 16, sort: 'createdAt,desc' }).catch(() =>
  listProducts({ size: 16 }),
);
```

### Conditional Render (Stock-Aware UI)
**Source:** `app/products/[slug]/page.tsx:138-156` (tag → variant lookup) + `app/page.tsx:101` (`{categories.length > 0 && ...}`).
**Apply to:** 3-tier badge render + add-to-cart hide (D-15, D-16). Inline JSX conditional, KHÔNG abstract hook (Phase 15 = polish, không add abstraction).

---

## No Analog Found

| File | Role | Reason |
|------|------|--------|
| `public/hero/hero-primary.webp` | static asset | First WebP trong `public/` — codebase chỉ có SVG + PNG. Asset prep task ngoài code pattern. |
| `public/hero/hero-secondary.webp` | static asset | Same as above. |
| CSS scroll-snap (`.featuredScroll`) | new CSS pattern | KHÔNG có scroll-snap precedent trong codebase (verified — `Grep "scroll-snap"` = empty). First usage. Reference MDN + RESEARCH.md Pattern 2. |

---

## Metadata

**Analog search scope:**
- `sources/frontend/src/app/` (page components)
- `sources/frontend/src/components/ui/` (UI primitives)
- `sources/frontend/e2e/` (Playwright specs)
- `sources/frontend/public/` (static assets)
- `sources/frontend/src/app/globals.css` (token availability check)

**Files scanned:** ~12 (page.tsx, page.module.css, [slug]/page.tsx, [slug]/page.module.css, Badge.tsx, Badge.module.css, globals.css grep, e2e/auth.spec.ts, e2e/order-detail.spec.ts, e2e/password-change.spec.ts, e2e/admin-products.spec.ts, e2e/global-setup.ts, playwright.config.ts).

**Verified facts:**
- `--success`/`--warning` tokens KHÔNG tồn tại trong globals.css (Grep result: "No matches found") → MUST hard-code hex cho Badge `.success`/`.warning`.
- `public/hero/` directory KHÔNG tồn tại (ls public/ shows: file.svg, globe.svg, next.svg, placeholder.png, vercel.svg, window.svg) → MUST mkdir.
- All e2e specs use `test.use({ storageState })` pattern + confirmed-selector docstring convention.
- `playwright.config.ts` đã có `globalSetup` registered — KHÔNG cần thay đổi config cho smoke tests (Claude's discretion: optional `@smoke` tag không cần thiết — file path filter `e2e/smoke.spec.ts` đã đủ).

**Pattern extraction date:** 2026-05-02
