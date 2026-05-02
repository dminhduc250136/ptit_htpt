# Roadmap — Milestone v1.2 UI/UX Completion

**Milestone:** v1.2 — UI/UX Completion
**Started:** 2026-04-26
**Granularity:** standard (config.json)
**Total phases:** 6 active (Phase 9 → Phase 15, Phase 12 SKIPPED) — tiếp tục từ v1.1, KHÔNG reset
**Total v1.2 requirements:** 17 REQs active (6 deferred v1.3 — ACCT-01, REV-04, SEARCH-03, SEARCH-04, lightbox gallery, E2E full suite)
**Coverage:** 17/17 active mapped ✓
**Priority rule:** Visible-first — defer backend hardening invisible
**Revised:** 2026-04-27 — scope trim để ship nhanh hơn

---

## Pre-Phase Setup (BEFORE Phase 9)

Setup tasks roadmap PHẢI chốt trước khi spawn plan-phase agents — tránh collision/rework giữa phases.

### Flyway V-number Reservation Table

Reserve **explicit V-numbers per service** trong file này — plan-phase agents tham chiếu, KHÔNG tự pick (precedent v1.1 đã hit DB-05 collision).

| Service | Version | File / Purpose | Phase | Status |
|---------|---------|----------------|-------|--------|
| user-service | V3 | `V3__add_avatar.sql` — ALTER users ADD avatar BYTEA + content_type + updated_at | Phase 10 | Done |
| user-service | V4 | `V4__create_addresses.sql` — addresses table + partial unique idx `WHERE is_default` | Phase 11 | Done |
| user-service | V5 | `V5__create_wishlists.sql` — wishlists table + UNIQUE (user_id, product_id) | Phase 12 SKIPPED | Reserved (defer v1.3) |
| product-service | V4 | `V4__create_reviews.sql` — reviews table + UNIQUE (product_id, user_id) | Phase 13 | Reserved |
| product-service | V5 | `V5__add_avg_rating_review_count.sql` — ALTER products ADD avg_rating + review_count | Phase 13 | Reserved |
| product-service | V6 | `V6__add_search_indexes.sql` — idx brand/price (optional) | Phase 14 | Reserved (optional) |
| order-service | V3 | `V3__add_order_filter_index.sql` — composite idx (user_id, status, created_at DESC) (optional) | Phase 11 hoặc skip | Done (skipped — low volume) |

**Naming convention v1.2:** schema migrations dùng `V3, V4, V5, V6`. Dev seed dùng `V1xx, V2xx` (precedent v1.1 V100__seed). KHÔNG share namespace giữa `db/migration` và `db/seed-dev`.

### Locked Decisions (từ research synthesis + user confirmation 2026-04-26)

| Decision | Lock | Rationale |
|----------|------|-----------|
| Avatar upload mechanism | **Multipart upload** + Thumbnailator 0.4.20 resize 256×256 + Postgres BYTEA inline (max 2MB, magic byte verify) | NOT URL input fallback — chọn multipart cho real UX (PITFALLS §10) |
| Route prefix consolidation | **`/profile/*`** cho mọi account-management page (settings/wishlist/addresses) | `/profile/orders` đã có v1.1 → consolidate KHÔNG split sang `/account/*` |
| Reviews eligibility | **Verified buyer only** (cross-service check qua order-svc — order DELIVERED chứa product_id) | NOT any-logged-in-user — đảm bảo review chất lượng (REV-01) |
| AUTH-06 status | **Đã đóng** (Phase 9 complete) | middleware.ts matcher mở rộng 4 routes |
| Featured products | **Top-8 by `createdAt DESC`** | KHÔNG thêm `featured BOOLEAN` column — giảm scope |
| Address limit/user | **10 addresses** với error code `ADDRESS_LIMIT_EXCEEDED` (422) | Industry-standard cap, prevent abuse |
| Visible-first priority | **Giữ nguyên** từ v1.1 | Defer backend hardening invisible (D1..D17 carry-over) |
| Phase numbering | **Tiếp tục từ Phase 9** (KHÔNG reset) | v1.1 ended at Phase 8 |
| PDP gallery | **Thumbnail strip + main image swap** (CSS + state) | Bỏ yet-another-react-lightbox — giảm dependency, ship nhanh |
| Search filters | **Brand + Price chỉ** (JPQL optional params) | Bỏ JPA Specification + rating filter + URL state encoding — defer v1.3 |
| Phase 12 Wishlist | **SKIP** (defer v1.3) | Không unblock core shopping flow, tiết kiệm 1 phase |
| REV-04 author edit/delete | **SKIP** (defer v1.3) | Không visible với majority users, giảm ownership check complexity |

---

## Phases

- [x] **Phase 9: Residual Closure & Verification** — Đóng AUTH-06 + AUTH-07 password change + UI-02 admin dashboard + Playwright E2E re-baseline cho v1.1. *(Complete 2026-04-27 — 5 plans, 4/4 automated checks PASS, 5 UAT items pending docker stack)*
- [x] **Phase 10: User-Svc Schema Cluster + Profile Editing** — V3 avatar BYTEA, gateway `/users/me/*` routes, profile editing UI (rhf+zod foundation). *(Complete 2026-04-27 — 3 plans, tsc+build PASS, ACCT-03 shipped; ACCT-04 avatar deferred per D-08)*
- [x] **Phase 11: Address Book + Order History Filtering** — V4 addresses, address CRUD + checkout integration, order filter bar trên `/profile/orders`. *(Complete 2026-04-27 — 6 plans, 17/17 verified, 4 UAT items human_needed)*
- [~~] **Phase 12: Wishlist** — ~~SKIPPED~~ (deferred v1.3 — không unblock core shopping flow; V5 wishlists migration number reserved)
- [ ] **Phase 13: Reviews & Ratings** — V4 reviews + V5 avg_rating cached cols, verified-buyer eligibility, PDP review section. *(REV-04 author edit/delete deferred v1.3)*
- [ ] **Phase 14: Basic Search Filters** — Brand + Price filter với JPQL optional params, FilterSidebar component. *(SEARCH-03 rating + SEARCH-04 URL state deferred v1.3)*
- [ ] **Phase 15: Public Polish + Milestone Audit** — Homepage hero+featured+categories, PDP thumbnail gallery+specs+breadcrumb+stock badge, smoke E2E, milestone audit + tag v1.2.

---

## Phase Details

### Phase 9: Residual Closure & Verification ✓ COMPLETE

**Goal:** Đóng 4 carry-over residual gaps từ v1.1 + verify infrastructure ổn định trước khi mở phases mới.
**Depends on:** Nothing (mở đầu v1.2)
**Requirements:** AUTH-06, AUTH-07, UI-02, TEST-01
**Status:** Complete 2026-04-27 — 5 plans done, verification PASSED
**UI hint:** yes

### Phase 10: User-Svc Schema Cluster + Profile Editing ✓ COMPLETE

**Goal:** Lay foundation cho user-svc features — V3 avatar migration + gateway `/users/me/*` route group + profile editing UI thiết lập rhf+zod pattern cho phases sau.
**Depends on:** Phase 9 (AUTH-06 closure → `/profile/*` được middleware bảo vệ)
**Requirements:** ACCT-03 (fullName/phone), ACCT-04 (avatar — deferred D-08)
**Status:** Complete 2026-04-27 — 3 plans done, ACCT-03 shipped; ACCT-04 deferred
**UI hint:** yes

### Phase 11: Address Book + Order History Filtering ✓ COMPLETE

**Goal:** Ship address book CRUD + checkout integration + order filter bar.
**Depends on:** Phase 10 (user-svc schema cluster + form pattern foundation)
**Requirements:** ACCT-02 (order filter), ACCT-05 (address CRUD), ACCT-06 (address checkout integration)
**Status:** Complete 2026-04-27 — 6 plans done, 17/17 verified, 4 UAT items human_needed (11-HUMAN-UAT.md)
**UI hint:** yes

### Phase 12: Wishlist — SKIPPED (defer v1.3)

**Decision:** Defer toàn bộ Phase 12 sang v1.3.
**Rationale:** Wishlist (heart icon) là nice-to-have, không ảnh hưởng core shopping flow (browse → cart → checkout). Không unblock phase nào khác. Tiết kiệm ~1 phase execution time.
**Flyway note:** user-svc V5 migration number reserved — KHÔNG dùng cho purpose khác trong v1.2.
**Deferred REQs:** ACCT-01

### Phase 13: Reviews & Ratings

**Goal:** Ship reviews end-to-end với verified-buyer eligibility + XSS-safe rendering + avg_rating cached.
**Depends on:** Phase 9 (auth foundation), order-svc DELIVERED orders existing
**Requirements:** REV-01 (submission), REV-02 (list), REV-03 (avg rating cached)
**Deferred:** REV-04 (author edit/delete) → v1.3
**Success Criteria** (what must be TRUE):
  1. User logged-in đã có order DELIVERED chứa product → PDP hiển thị review form (rating 1-5 + content textarea); submit → POST `/api/products/{id}/reviews` → backend OWASP sanitize content → review appear in list.
  2. User CHƯA mua product (no order DELIVERED) submit review → backend trả 422 `REVIEW_NOT_ELIGIBLE`; FE hide form với hint "Chỉ user đã mua mới review được".
  3. PDP review list hiển thị paginated 10/page, sort newest first, mỗi item show reviewer displayName (snapshot) + rating sao + content (rendered as plain text — XSS payload `<script>` hiển thị literal text, KHÔNG execute).
  4. Product card + PDP header hiển thị `avg_rating` (1 decimal) + `review_count` từ cached cols; recompute from scratch (`SELECT AVG, COUNT FROM reviews WHERE product_id=?`) sau mỗi insert/delete — KHÔNG drift.
**Plans:** 4 plans
- [x] 13-01-PLAN.md — user-svc JwtUtils + AuthService thêm claim 'name' (fullName) *(2026-04-27)*
- [x] 13-02-PLAN.md — order-svc internal eligibility endpoint (/internal/orders/eligibility) + OrderRepository query *(2026-04-27)*
- [x] 13-03-PLAN.md — product-svc V4 reviews + V5 avg_rating + ReviewEntity/Repo/Service/Controller + Jsoup sanitize + RestTemplate eligibility re-check + tests *(2026-04-27)*
- [ ] 13-04-PLAN.md — FE ReviewSection (StarWidget, Form, List) + types align + ProductCard/PDP avgRating + UAT checkpoint
**UI hint:** yes

### Phase 14: Basic Search Filters

**Goal:** Ship brand + price filter trên `/search` và `/products` — JPQL optional params, FilterSidebar component với 2 facets.
**Depends on:** Phase 11 (search page đã có keyword — KHÔNG cần Phase 13 avg_rating vì bỏ rating filter)
**Requirements:** SEARCH-01 (brand), SEARCH-02 (price)
**Deferred:** SEARCH-03 (rating filter), SEARCH-04 (in-stock + URL state + clear-all) → v1.3
**Success Criteria** (what must be TRUE):
  1. User vào `/search?q=laptop` → FilterSidebar component hiển thị brand checkboxes (distinct brands từ DB) + price range min/max input.
  2. User check 2 brands (Dell + HP) + price 10-20M → backend JPQL `WHERE (:brands IS NULL OR brand IN :brands) AND (:priceMin IS NULL OR price >= :priceMin) AND (:priceMax IS NULL OR price <= :priceMax)` → results match; same-facet OR, cross-facet AND.
  3. Validate price min > max → client reject inline error, KHÔNG gọi server.
  4. Filter reset button xóa toàn bộ facet selection về default (không cần URL encode).
**Plans:** 3 plans
- [ ] 14-01-backend-jpql-brands-PLAN.md — product-svc ProductRepository JPQL findWithFilters + findDistinctBrands + ProductCrudService refactor 8-arg overload + ProductController extend params + new GET /products/brands
- [ ] 14-02-frontend-filter-sidebar-PLAN.md — FilterSidebar component (Brand checkboxes + 2 price input + 4 preset chip + Reset + validate min>max + a11y label/aria-pressed/role=alert)
- [ ] 14-03-wire-products-page-PLAN.md — services/products.ts extend ListProductsParams + listBrands() + wire FilterSidebar vào /products/page.tsx + remove client-side .filter() + empty state copy D-12 + UAT checkpoint
**UI hint:** yes

### Phase 15: Public Polish + Milestone Audit

**Goal:** Ship homepage redesign + PDP enhancements + smoke E2E + milestone audit + tag v1.2 — close milestone.
**Depends on:** Phase 13 (PDP avg_rating display), Phase 14 (search filters)
**Requirements:** PUB-01 (hero + featured), PUB-02 (categories + new arrivals), PUB-03 (thumbnail gallery + specs + breadcrumb), PUB-04 (stock badge), TEST-02 (smoke E2E)
**Success Criteria** (what must be TRUE):
  1. Homepage `/` load với hero banner (CTA + WebP image, `next/image` priority cho LCP < 2.5s) + Featured products carousel (top-8 by createdAt DESC, CSS scroll-snap, no JS lib) + Categories grid (link đến brand-filtered `/products?brand=X`) + New Arrivals strip (top-8 newest, KHÔNG duplicate featured).
  2. PDP `/products/[slug]` hiển thị thumbnail strip + main image swap (click thumbnail → swap main image, CSS + React state — KHÔNG dùng lightbox lib) + specs table render từ `product.specs` JSONB + breadcrumb `Home > {brand} > {product.name}` (clickable segments).
  3. PDP stock badge color-coded: green "Còn hàng" (stock ≥10), yellow "Sắp hết hàng" (1≤stock<10), red "Hết hàng" (stock=0); add-to-cart button hidden khi stock=0.
  4. Playwright smoke E2E: 3-4 critical path tests (address-at-checkout, review submission, profile editing, homepage navigation) PASS trên fresh docker stack; toàn bộ v1.1 baseline vẫn PASS.
  5. Milestone audit completed (audit doc + verifier scan + tag `v1.2`); 17/17 active REQs satisfied hoặc gaps documented.
**Plans:** 5 plans (4 waves)
- [x] 15-00-PLAN.md — Wave 0 prep: hero WebP assets + Badge variants extend + selector audit + manual checklist *(Complete 2026-05-02 — 3 tasks, 6 files; SUMMARY: 15-00-SUMMARY.md)*
- [x] 15-01-PLAN.md — Wave 1 homepage: hero next/image + Featured carousel scroll-snap + New Arrivals dedupe *(Complete 2026-05-02 — 2 tasks, 2 files; SUMMARY: 15-01-SUMMARY.md)*
- [x] 15-02-PLAN.md — Wave 1 PDP: breadcrumb brand refactor + 3-tier stock badge + hide add-to-cart + thumbnail a11y *(Complete 2026-05-02 — 2 tasks, 2 files; SUMMARY: 15-02-SUMMARY.md)*
- [x] 15-03-PLAN.md — Wave 2 smoke E2E: 4 Playwright tests (homepage/checkout/review/profile) + skip-if-no-data degradation *(Complete 2026-05-02 — 2 tasks, 1 file 251 LOC; auto-approved checkpoint; SUMMARY: 15-03-SUMMARY.md)*
- [ ] 15-04-PLAN.md — Wave 3 milestone closure: /gsd-audit-milestone v1.2 + MILESTONES update + git tag v1.2 annotated local
**UI hint:** yes

---

## Progress Tracking

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 9. Residual Closure & Verification | 5/5 | ✓ Complete | 2026-04-27 |
| 10. User-Svc Schema + Profile Editing | 3/3 | ✓ Complete | 2026-04-27 |
| 11. Address Book + Order Filtering | 6/6 | ✓ Complete | 2026-04-27 |
| 12. Wishlist | — | ~~SKIPPED~~ (defer v1.3) | — |
| 13. Reviews & Ratings | 0/4 | Not started | — |
| 14. Basic Search Filters | 0/3 | Not started | — |
| 15. Public Polish + Milestone Audit | 4/5 | Wave 2 complete (15-03 smoke E2E ✓) | — |

---

## Coverage Validation

**Total v1.2 REQs active:** 17
**Mapped:** 17 ✓
**Deferred v1.3:** 6 (ACCT-01, REV-04, SEARCH-03, SEARCH-04, lightbox/axe-core, E2E full suite)

| REQ-ID | Phase | Category | Status |
|--------|-------|----------|--------|
| AUTH-06 | Phase 9 | AUTH (residual) | ✓ Done |
| AUTH-07 | Phase 9 | AUTH (residual) | ✓ Done |
| UI-02 | Phase 9 | ADMIN (residual) | ✓ Done |
| TEST-01 | Phase 9 | TEST (residual) | ✓ Done |
| ACCT-03 | Phase 10 | ACCT (profile fullName/phone) | ✓ Done |
| ACCT-04 | Phase 10 | ACCT (avatar upload) | Deferred D-08 |
| ACCT-02 | Phase 11 | ACCT (order filter) | ✓ Done |
| ACCT-05 | Phase 11 | ACCT (address CRUD) | ✓ Done |
| ACCT-06 | Phase 11 | ACCT (address checkout integration) | ✓ Done |
| ACCT-01 | Phase 12 SKIPPED | ACCT (wishlist) | Deferred v1.3 |
| REV-01 | Phase 13 | REV (submission) | Pending |
| REV-02 | Phase 13 | REV (list) | Pending |
| REV-03 | Phase 13 | REV (avg rating cached) | Pending |
| REV-04 | Phase 13 TRIMMED | REV (author edit/delete) | Deferred v1.3 |
| SEARCH-01 | Phase 14 | SEARCH (brand) | Pending |
| SEARCH-02 | Phase 14 | SEARCH (price) | Pending |
| SEARCH-03 | Phase 14 TRIMMED | SEARCH (rating) | Deferred v1.3 |
| SEARCH-04 | Phase 14 TRIMMED | SEARCH (inStock + URL state) | Deferred v1.3 |
| PUB-01 | Phase 15 | PUB (hero + featured) | Complete (15-01) |
| PUB-02 | Phase 15 | PUB (categories + new arrivals) | Complete (15-01) |
| PUB-03 | Phase 15 | PUB (thumbnail gallery + specs + breadcrumb) | Complete (15-02) |
| PUB-04 | Phase 15 | PUB (stock badge) | Complete (15-02) |
| TEST-02 | Phase 15 | TEST (smoke E2E 3-4 tests) | Pending |

---

*Roadmap created: 2026-04-26 by gsd-roadmapper. Revised: 2026-04-27 — scope trim (Phase 12 skip, REV-04/SEARCH-03/SEARCH-04/lightbox deferred v1.3) để ship v1.2 nhanh hơn. Phase numbering tiếp tục từ Phase 9 (v1.1 ended at Phase 8). Visible-first priority giữ nguyên.*
