# Phase 15: Public Polish + Milestone Audit — Context

**Gathered:** 2026-05-02
**Status:** Ready for planning

<domain>
## Phase Boundary

Polish trang public-facing (Homepage hero/featured/categories/new arrivals, PDP gallery/specs/breadcrumb/stock badge) + viết Playwright smoke E2E (3-4 critical paths) + chạy milestone audit + tag `v1.2`. Đóng milestone v1.2.

**Requirements active:** PUB-01 (hero + featured), PUB-02 (categories + new arrivals), PUB-03 (PDP thumbnail gallery + specs + breadcrumb), PUB-04 (stock badge 3-tier), TEST-02 (smoke E2E 3-4 tests).

**KHÔNG trong scope:** PDP fullscreen lightbox (defer v1.3), axe-core a11y gate (defer v1.3), Playwright full suite 8+ (defer v1.3), thêm `featured BOOLEAN` column, redesign navigation/header/footer, performance audit toàn trang (chỉ LCP cho hero), thay design system / typography / color tokens.

**Trạng thái codebase hiện tại (quan trọng):**
- `app/page.tsx` — Đã có hero, categories grid, featured products, latest products, value props. Hero dùng `<img>` tag với Unsplash URL (KHÔNG `next/image priority`). Featured đang sort `reviewCount,desc` (không match ROADMAP `createdAt,desc`).
- `app/products/[slug]/page.tsx` — Đã có thumbnail strip + main image swap, breadcrumb (Home > Sản phẩm > Category > Name), specs table tab, stock display 2-tier (green/red).
- Type `Product` đã có `images: string[]`, `specifications: Specification[]`, `brand?`, `stock`.

→ Phase 15 = **polish + alignment với ROADMAP success criteria**, không phải build từ zero. Phần lớn structural code đã có sẵn từ các phase trước.

</domain>

<decisions>
## Implementation Decisions

### Hero (PUB-01)
- **D-01:** Thay `<img>` Unsplash bằng `next/image` với `priority` prop để cải thiện LCP. Source ảnh: download 2 ảnh hero hiện tại từ Unsplash về `sources/frontend/public/hero/hero-primary.webp` + `hero-secondary.webp` (WebP format, dimension primary ~600×800, secondary ~400×500). KHÔNG dùng remote loader (tránh phụ thuộc Unsplash CDN khi demo offline).
- **D-02:** Hero copy + Badge + 2 CTA giữ nguyên ("Khám phá ngay" → `/products`, "Xem bộ sưu tập" → `/collections`). KHÔNG redesign hero layout (2 cột text + visual đã ổn). `/collections` chưa tồn tại — **defer**: thay link CTA thứ 2 thành `/products?sort=newest` hoặc giữ link `/collections` và để 404 sau (Decision: thay thành `/products` với label "Xem tất cả sản phẩm" để tránh 404).
- **D-03:** LCP target < 2.5s đo qua Lighthouse local (Chrome DevTools, mobile preset, dev mode KHÔNG đại diện — đo trên `next build && next start`). KHÔNG thêm preload `<link rel="preload">` thủ công (next/image priority đã handle preload tự động).

### Featured Products (PUB-01)
- **D-04:** Đổi sort param từ `reviewCount,desc` sang `createdAt,desc` để match ROADMAP SC-1. Backend đã support sort param (Phase 5 + Phase 14 ProductRepository).
- **D-05:** Featured render dạng **horizontal carousel CSS scroll-snap** (KHÔNG dùng JS lib slick/swiper). Markup: `<div class="featuredScroll">` chứa 8 `<ProductCard>` mỗi card width fixed (e.g. 280px desktop, 240px mobile), parent `overflow-x: auto; scroll-snap-type: x mandatory`, child `scroll-snap-align: start`. Hide scrollbar bằng `scrollbar-width: none` + `::-webkit-scrollbar { display: none }`. KHÔNG show prev/next arrow (giữ minimal — user dùng touch/wheel scroll).
- **D-06:** Featured giữ section header "Sản phẩm nổi bật" + nút "Xem tất cả →". Loading skeleton + RetrySection hiện có giữ nguyên (đã pattern Phase 5).

### Categories Grid (PUB-02)
- **D-07:** Categories grid hiện tại link `/products?category={slug}` — **giữ nguyên** (categories là dimension navigation độc lập với brand). ROADMAP wording "brand-filtered /products?brand=X" được hiểu như **gợi ý** chứ không phải hard contract — categories ≠ brands trong data model. KHÔNG build "Shop by Brand" section riêng cho v1.2 (defer).
- **D-08:** Categories grid responsive (4 cột desktop, 2 cột mobile, gap consistent với `var(--space-*)` tokens). Icon SVG hiện tại giữ nguyên (1 icon dùng chung — chưa cần per-category icon trong v1.2).

### New Arrivals (PUB-02)
- **D-09:** New Arrivals = section "Sản phẩm mới" hiện có. **Dedupe rule:** Featured = top-8 by `createdAt DESC`; New Arrivals = **next 8** (tức là `page=1, size=8, sort=createdAt,desc`). Cách này đảm bảo KHÔNG trùng product nào (skip first 8). Trade-off: nếu catalog < 16 products, New Arrivals có thể empty hoặc < 8 items — acceptable, hiển thị whatever returned.
- **D-10:** Render dạng **grid 4 cột** (KHÔNG carousel) — giữ pattern hiện tại. Lý do: chỉ Featured dùng carousel để tạo visual differentiation; New Arrivals dùng grid để emphasize "browse all".

### PDP Thumbnail Gallery (PUB-03)
- **D-11:** Thumbnail strip + main swap **đã có sẵn** trong `app/products/[slug]/page.tsx:159-171` — giữ nguyên implementation (CSS + React state, KHÔNG lightbox lib). Polish:
  - Add `aria-label="Xem ảnh {i+1}"` + `aria-current={i === selectedImage}` cho accessibility cơ bản.
  - Active thumbnail border highlight rõ hơn (border 2px var(--primary) thay vì subtle).
  - Keyboard nav: arrow Left/Right để switch image (optional, **Claude's discretion**).

### PDP Specs Table (PUB-03)
- **D-12:** Specs table đã có ở tab "Thông số" trong `[slug]/page.tsx:321-337` — giữ nguyên. Type frontend dùng `product.specifications: Specification[]` (KHÔNG `product.specs`). ROADMAP wording "product.specs JSONB" refer đến backend column name; frontend dùng `specifications` đã đúng.
- **D-13:** Empty state khi không có specs: hiện đã có "Chưa có thông số kỹ thuật." — giữ nguyên copy. KHÔNG block tab nếu empty (user vẫn click vào được).

### PDP Breadcrumb (PUB-03)
- **D-14:** Refactor breadcrumb từ `Home > Sản phẩm > {Category} > {Name}` thành `Home > {Brand} > {Name}` per ROADMAP SC-2.
  - `Home` → `/`
  - `{Brand}` → link `/products?brand={brand}` (clickable, dùng filter brand từ Phase 14)
  - `{Name}` → current page (non-clickable)
  - **Fallback nếu product không có brand:** Hiển thị `Home > Sản phẩm > {Name}` (link `Sản phẩm` → `/products`). Tránh breadcrumb gãy khi brand=null.

### PDP Stock Badge (PUB-04)
- **D-15:** 3-tier color-coded badge (replace 2-tier hiện tại):
  - `stock >= 10` → green badge "✓ Còn hàng" (giữ format hiện tại, có thể bỏ count detail "(N sản phẩm)" để match minimal badge style)
  - `1 <= stock < 10` → yellow badge "⚠ Sắp hết hàng (còn {stock})" — **show count** để tạo urgency
  - `stock === 0` → red badge "✗ Hết hàng"
  - Badge style: dùng `<Badge>` component nếu có variant phù hợp, hoặc `<span>` với class CSS module + token `var(--success/warning/error)`.
- **D-16:** Add-to-cart button: ROADMAP nói "hidden khi stock=0". Hiện tại button đang `disabled + label "Hết hàng"`. **Decision:** Hide button hoàn toàn (return null) khi `stock === 0`, thay bằng inline message "Sản phẩm tạm hết — vui lòng quay lại sau." Quantity selector cũng hide. "Xem giỏ hàng" secondary button vẫn hiển thị.

### Playwright Smoke E2E (TEST-02)
- **D-17:** 4 smoke tests, 1 file mới `sources/frontend/e2e/smoke.spec.ts` (KHÔNG split — gọn hơn cho CI). Test list:
  1. **Homepage navigation** — Load `/`, assert hero render, click "Khám phá ngay" → land `/products`, assert có ít nhất 1 ProductCard.
  2. **Address-at-checkout** — Login (reuse `global-setup.ts` storageState), add product to cart, vào `/checkout`, AddressPicker hiển thị danh sách address, chọn 1 address, submit order → success page hoặc toast.
  3. **Review submission** — Login, mở PDP của product đã DELIVERED, mở tab Reviews, submit form rating=5 + content text, assert review xuất hiện trong list (REV-01 acceptance).
  4. **Profile editing** — Login, vào `/profile/settings`, sửa fullName + phone, submit, assert toast success + reload trang field giá trị mới persist (ACCT-03).
- **D-18:** Test data setup: dựa vào fixture/seed hiện có ở `db/init/`. KHÔNG thêm seed mới cho phase 15 — nếu thiếu DELIVERED order cho test #3, dùng test #2 trước (place order) → chạy admin script update status=DELIVERED → test #3. **Claude's discretion:** ordering tests hoặc dùng API fixtures.
- **D-19:** Smoke test PASS criteria: 4/4 PASS trên `docker compose up -d --build && npm run test:e2e -- smoke.spec.ts` từ fresh stack. Toàn bộ existing baseline (auth, admin-products/orders/users, order-detail, password-change) vẫn 100% PASS.
- **D-20:** **KHÔNG** add Playwright tests cho search filters Phase 14 (defer v1.3 cùng SEARCH-04 full URL state). KHÔNG add visual regression / screenshot diff (out of scope).

### Milestone Audit (Phase 15 closing)
- **D-21:** Sau khi 5 REQs PUB-01..04 + TEST-02 ship xong, chạy `/gsd-audit-milestone v1.2` để generate audit doc. Audit verify 17/17 active REQs satisfied OR documented gap.
- **D-22:** Tag `v1.2` annotated trên main branch sau audit PASS: `git tag -a v1.2 -m "Milestone v1.2 — UI/UX Completion"`. KHÔNG push tag automatically — user push thủ công sau review.
- **D-23:** Update `.planning/MILESTONES.md` đánh dấu v1.2 complete + populate completion date. Update `.planning/PROJECT.md` nếu có "current milestone" pointer cần advance.

### Claude's Discretion
- CSS class names + module organization cho `featuredScroll` (carousel) section.
- Exact dimension/aspect ratio cho hero WebP images (download để khớp visual hiện tại là ưu tiên).
- Quantity selector hide vs disable khi stock=0 — Claude pick consistent với D-16.
- Keyboard arrow navigation cho thumbnail (D-11) — implement nếu < 30 LOC, skip nếu phức tạp.
- Order test execution + cách tạo DELIVERED order data cho smoke test #3.
- Inline copy chi tiết cho stock badge ("Sắp hết hàng (còn 3)" hay "Còn 3 sản phẩm" — Claude pick natural Vietnamese).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Roadmap & Requirements
- `.planning/ROADMAP.md` §"Phase 15: Public Polish + Milestone Audit" — goal, success criteria 1-5, dependencies (Phase 13, 14)
- `.planning/REQUIREMENTS.md` §"PUB" — PUB-01 đến PUB-04 acceptance criteria
- `.planning/REQUIREMENTS.md` §"TEST" — TEST-02 (smoke 3-4 tests, KHÔNG full 8+)
- `.planning/REQUIREMENTS.md` §"Locked Decisions" — PDP gallery (no lightbox), TEST-02 smoke scope, Featured (top-8 createdAt DESC, no `featured` column)
- `.planning/MILESTONES.md` — milestone v1.2 closure tracking

### Prior Phase Patterns (reuse)
- `.planning/phases/14-basic-search-filters/14-CONTEXT.md` §"Brand filter" (D-03, D-08) — `/products?brand=X` query param contract used by D-14 breadcrumb + D-07 categories grid
- `.planning/phases/13-reviews-ratings/` — REV-01 review submission flow (smoke test #3 phụ thuộc)
- `.planning/phases/11-address-book-order-history-filtering/` — ACCT-06 AddressPicker tại checkout (smoke test #2 phụ thuộc)
- `.planning/phases/10-user-svc-schema-profile-editing/` — ACCT-03 profile editing form (smoke test #4 phụ thuộc)

### Existing Code Touchpoints
- `sources/frontend/src/app/page.tsx` — Homepage refactor (D-01..D-10): hero img→next/image, featured sort+carousel, dedupe new arrivals
- `sources/frontend/src/app/page.module.css` — Add `.featuredScroll` styles (D-05)
- `sources/frontend/src/app/products/[slug]/page.tsx` — PDP refactor (D-11..D-16): breadcrumb (lines 104-121), stock display (lines 209-216), add-to-cart hide (lines 218-263)
- `sources/frontend/src/app/products/[slug]/page.module.css` — stock badge color tokens (D-15)
- `sources/frontend/src/types/index.ts:113-135` — Product type (đã có `specifications`, `brand`, `stock` — không cần thay đổi)
- `sources/frontend/public/hero/` — **mới** thư mục: chứa `hero-primary.webp`, `hero-secondary.webp` (D-01)
- `sources/frontend/e2e/smoke.spec.ts` — **mới** file (D-17..D-20)
- `sources/frontend/e2e/global-setup.ts` — Auth storageState reuse cho smoke tests
- `sources/frontend/playwright.config.ts` — Có thể cần tag `@smoke` nếu muốn run subset

### State (no separate ADR docs)
Project KHÔNG có `docs/adr/` directory — toàn bộ decisions track qua `.planning/phases/*/CONTEXT.md`. Không có external library docs cần reference (CSS scroll-snap = web standard, next/image = Next.js core, Playwright = đã setup từ Phase 9 TEST-01).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`<ProductCard>`** (`components/ui/ProductCard/`) — render featured + new arrivals + related (đã consistent).
- **`<Button>`** với variant primary/secondary/tertiary — dùng cho hero CTA + section "Xem tất cả".
- **`<Badge>`** với variant new/sale/hot — có thể extend variant `success/warning/danger` cho stock badge (hoặc dùng inline span).
- **`<RetrySection>`** + skeleton class `.skeleton` — pattern loading/error đã chuẩn hóa.
- **`<AddressPicker>`** + `addToCart()` service + Toast — smoke test #2 reuse trực tiếp.
- **`<ReviewSection>`** trong `[slug]/ReviewSection/` — smoke test #3 reuse.
- **Global-setup auth fixture** ở `e2e/global-setup.ts` — smoke test login state reuse.

### Established Patterns
- **`'use client'` page components** với `useState` + `useEffect` + `useCallback` load pattern — homepage + PDP đều đang dùng.
- **`Promise.all` parallel fetch** cho independent data (featured + latest, đã có).
- **CSS Modules + design tokens** (`var(--space-*)`, `var(--radius-*)`, `var(--primary)`, `var(--on-surface-variant)`) — strict reuse, KHÔNG hard-code colors/spacing.
- **VND format** qua `formatPrice()` từ `services/api` — reuse cho stock badge nếu cần.
- **Vietnamese-first UI copy** — toàn bộ button/badge/empty state đều tiếng Việt.
- **Playwright spec convention**: `e2e/*.spec.ts` + storageState từ global-setup.

### Integration Points
- Hero ↔ `/products` route (CTA primary).
- Categories grid ↔ `/products?category={slug}` (existing dimension, **không** đổi sang brand).
- Featured/New Arrivals ↔ `services/products.ts listProducts({ sort, page, size })` — backend đã hỗ trợ.
- PDP Breadcrumb brand link ↔ `/products?brand={brand}` (Phase 14 SEARCH-01 contract).
- Smoke test stack ↔ `docker-compose.yml` fresh up (postgres + 4 services + gateway + nextjs).

### Anti-patterns to avoid
- KHÔNG dùng JS carousel lib (slick/swiper/embla) — CSS scroll-snap đủ cho v1.2 (D-05).
- KHÔNG dùng lightbox lib (yet-another-react-lightbox) — defer v1.3 per locked decision.
- KHÔNG thay đổi data model (`Product` type, backend entity) — chỉ FE/UI work + 1 thư mục public assets.
- KHÔNG add visual regression / Percy / Chromatic — defer.
- KHÔNG run accessibility audit blocking gate (axe-core) — defer v1.3.
- KHÔNG redirect Unsplash CDN qua next/image remote loader (tránh runtime dependency offline demo).

</code_context>

<specifics>
## Specific Ideas

- **Featured carousel touch behavior**: trên mobile, scroll-snap cho cảm giác "swipe between cards" tự nhiên — match UX e-commerce phổ thông (Tiki, Shopee).
- **Stock badge color semantic**: Map green/yellow/red tương ứng `--success/--warning/--error` tokens. Nếu chưa có sẵn 3 tokens này, fallback hex `#16a34a / #f59e0b / #dc2626`.
- **Breadcrumb brand link**: tận dụng filter Phase 14 — click "Apple" trong breadcrumb → `/products?brand=Apple` → FilterSidebar tự pre-check Apple. Tạo continuity navigation.
- **Smoke test scope philosophy**: 4 tests cover **business-critical paths**, không cover edge cases (validation errors, permission denied, 404s) — đó là job của full E2E suite v1.3.
- **Hero ảnh local WebP**: chọn 2 ảnh phù hợp tone shop tmđt VN (sản phẩm consumer/electronics/fashion), lưu kích thước reasonable (~150-200KB mỗi ảnh).

</specifics>

<deferred>
## Deferred Ideas

- **PDP fullscreen lightbox** (PUB-03 lightbox split) — `yet-another-react-lightbox` lib + axe-core a11y gate → v1.3.
- **Playwright full E2E suite** (TEST-02 full) — 8+ tests bao gồm wishlist, search filters, PDP gallery edge cases → v1.3.
- **`featured BOOLEAN` column trên ProductEntity** — sort proxy `createdAt DESC` đủ cho v1.2; add column khi cần admin curation manual.
- **Shop by Brand grid trên homepage** — discovery dimension thứ 2 ngoài Categories; chờ user feedback có cần không.
- **Visual regression testing** (Percy / Chromatic / Playwright snapshots) — v1.3+ nếu UI churn cao.
- **Hero A/B testing slot** — chờ analytics + experimentation framework.
- **Per-category icon SVG** trong Categories grid — hiện tại 1 icon chung; UX feedback sẽ quyết.
- **Lighthouse CI gate** — đo LCP/CLS/TBT trong pipeline; v1.3+ khi performance budget locked.
- **Keyboard arrow nav cho thumbnail gallery** — Claude's discretion D-11; nếu skip thì add backlog item.
- **Hero CTA "Xem bộ sưu tập" link `/collections`** — defer route `/collections` collection landing page.
- **Stock badge animation/pulse cho yellow tier** — micro-interaction defer.

</deferred>

---

*Phase: 15-public-polish-milestone-audit*
*Context gathered: 2026-05-02*
