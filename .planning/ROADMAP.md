# Roadmap — Milestone v1.2 UI/UX Completion

**Milestone:** v1.2 — UI/UX Completion
**Started:** 2026-04-26
**Granularity:** standard (config.json)
**Total phases:** 7 (Phase 9 → Phase 15) — tiếp tục từ v1.1, KHÔNG reset
**Total v1.2 requirements:** 23 REQs across 7 categories
**Coverage:** 23/23 mapped ✓ (no orphans, no duplicates)
**Priority rule:** Visible-first — defer backend hardening invisible

---

## Pre-Phase Setup (BEFORE Phase 9)

Setup tasks roadmap PHẢI chốt trước khi spawn plan-phase agents — tránh collision/rework giữa phases.

### Flyway V-number Reservation Table

Reserve **explicit V-numbers per service** trong file này — plan-phase agents tham chiếu, KHÔNG tự pick (precedent v1.1 đã hit DB-05 collision).

| Service | Version | File / Purpose | Phase | Status |
|---------|---------|----------------|-------|--------|
| user-service | V3 | `V3__add_avatar.sql` — ALTER users ADD avatar BYTEA + content_type + updated_at | Phase 10 | Reserved |
| user-service | V4 | `V4__create_addresses.sql` — addresses table + partial unique idx `WHERE is_default` | Phase 11 | Reserved |
| user-service | V5 | `V5__create_wishlists.sql` — wishlists table + UNIQUE (user_id, product_id) | Phase 12 | Reserved |
| product-service | V4 | `V4__create_reviews.sql` — reviews table + UNIQUE (product_id, user_id) | Phase 13 | Reserved |
| product-service | V5 | `V5__add_avg_rating_review_count.sql` — ALTER products ADD avg_rating + review_count | Phase 13 | Reserved |
| product-service | V6 | `V6__add_search_indexes.sql` — idx brand/price/avg_rating (optional) | Phase 14 | Reserved (optional) |
| order-service | V3 | `V3__add_order_filter_index.sql` — composite idx (user_id, status, created_at DESC) (optional) | Phase 11 hoặc skip | Reserved (optional) |

**Naming convention v1.2:** schema migrations dùng `V3, V4, V5, V6`. Dev seed dùng `V1xx, V2xx` (precedent v1.1 V100__seed). KHÔNG share namespace giữa `db/migration` và `db/seed-dev`.

### Locked Decisions (từ research synthesis + user confirmation 2026-04-26)

| Decision | Lock | Rationale |
|----------|------|-----------|
| Avatar upload mechanism | **Multipart upload** + Thumbnailator 0.4.20 resize 256×256 + Postgres BYTEA inline (max 2MB, magic byte verify) | NOT URL input fallback — chọn multipart cho real UX (PITFALLS §10) |
| Route prefix consolidation | **`/profile/*`** cho mọi account-management page (settings/wishlist/addresses) | `/profile/orders` đã có v1.1 → consolidate KHÔNG split sang `/account/*` |
| Reviews eligibility | **Verified buyer only** (cross-service check qua order-svc — order DELIVERED chứa product_id) | NOT any-logged-in-user — đảm bảo review chất lượng (REV-01) |
| AUTH-06 status | **Chưa đóng** (codebase verified `middleware.ts:29` matcher = `/admin/:path*`) | Plan đầy đủ trong Phase 9, KHÔNG verify-only |
| Featured products | **Top-8 by `createdAt DESC`** | KHÔNG thêm `featured BOOLEAN` column — giảm scope |
| Address limit/user | **10 addresses** với error code `ADDRESS_LIMIT_EXCEEDED` (422) | Industry-standard cap, prevent abuse |
| Visible-first priority | **Giữ nguyên** từ v1.1 | Defer backend hardening invisible (D1..D17 carry-over) |
| Phase numbering | **Tiếp tục từ Phase 9** (KHÔNG reset) | v1.1 ended at Phase 8 |

---

## Phases

- [x] **Phase 9: Residual Closure & Verification** — Đóng AUTH-06 + AUTH-07 password change + UI-02 admin dashboard + Playwright E2E re-baseline cho v1.1. *(Complete 2026-04-27 — 5 plans, 4/4 automated checks PASS, 5 UAT items pending docker stack)*
- [x] **Phase 10: User-Svc Schema Cluster + Profile Editing** — V3 avatar BYTEA, gateway `/users/me/*` routes, profile editing UI (rhf+zod foundation). *(Complete 2026-04-27 — 3 plans, tsc+build PASS, ACCT-03 shipped; ACCT-04 avatar deferred per D-08)*
- [ ] **Phase 11: Address Book + Order History Filtering** — V4 addresses, address CRUD + checkout integration, order filter bar trên `/profile/orders`.
- [ ] **Phase 12: Wishlist** — V5 wishlists, heart icon trên product card/PDP, `/profile/wishlist` page với move-to-cart.
- [ ] **Phase 13: Reviews & Ratings** — V4 reviews + V5 avg_rating cached cols, recompute from scratch, verified-buyer eligibility, PDP review section.
- [ ] **Phase 14: Advanced Search Filters** — JPA Specification (brand/price/rating/inStock), FilterSidebar component, URL state encoding.
- [ ] **Phase 15: Public Polish + Milestone Audit** — Homepage redesign, PDP enhancements (gallery+specs+stock badge+breadcrumb), Playwright v1.2 E2E expansion, milestone audit + tag.

---

## Phase Details

### Phase 9: Residual Closure & Verification

**Goal:** Đóng 4 carry-over residual gaps từ v1.1 + verify infrastructure ổn định trước khi mở phases mới.
**Depends on:** Nothing (mở đầu v1.2)
**Requirements:** AUTH-06, AUTH-07, UI-02, TEST-01
**Success Criteria** (what must be TRUE):
  1. User direct visit `/profile/orders` (chưa login) → middleware redirect 307 sang `/login?returnTo=/profile/orders` (KHÔNG flash content trước khi redirect như v1.1).
  2. User logged-in submit form đổi password ở `/profile/settings` với 3 fields (current/new/confirm) → backend BCrypt verify oldPassword đúng → success toast; sai oldPassword → error `AUTH_INVALID_PASSWORD` hiển thị field-level.
  3. Admin login vào `/admin` thấy 4 KPI cards với số thật (Total products, Total orders, Total users, Pending orders) thay vì 0; Promise.all 3 endpoints `/api/{products,orders,users}/stats` parallel; loading skeleton + error fallback per card.
  4. Playwright E2E suite (existing 12 tests v1.1 features) pass 100% trên fresh `docker compose up` stack — selectors stable, no flaky.
**Plans:** 5 plans
- [ ] 09-01-PLAN.md — AUTH-06 middleware consolidation (xóa root duplicate, mở matcher 4 routes)
- [ ] 09-02-PLAN.md — UI-02 backend stats endpoints (3 services /admin/stats + manual JWT role check, D-05 REVISED)
- [ ] 09-03-PLAN.md — AUTH-07 backend POST /api/users/me/password (BCrypt verify + AUTH_INVALID_PASSWORD)
- [ ] 09-04-PLAN.md — UI-02 + AUTH-07 frontend (4 KPI cards + retry, password form 3 fields)
- [ ] 09-05-PLAN.md — TEST-01 Playwright re-baseline (6 specs ~14 tests + global storageState)
**UI hint:** yes

### Phase 10: User-Svc Schema Cluster + Profile Editing

**Goal:** Lay foundation cho 3 user-svc features (profile/address/wishlist) — V3 avatar migration + gateway `/users/me/*` route group + profile editing UI thiết lập rhf+zod pattern cho phases sau.
**Depends on:** Phase 9 (AUTH-06 closure → `/profile/*` được middleware bảo vệ)
**Requirements:** ACCT-03 (fullName/phone), ACCT-04 (avatar upload)
**Success Criteria** (what must be TRUE):
  1. User logged-in vào `/profile/settings` → form (rhf + zod) hiển thị fullName/phone editable + email read-only; submit `PATCH /api/users/me` → toast "Đã cập nhật" + navbar reflect tên mới ngay.
  2. User upload avatar JPEG/PNG/WebP ≤ 2MB qua `/profile/settings` Avatar section → backend magic-byte verify + Thumbnailator resize 256×256 → lưu BYTEA → `GET /api/users/{id}/avatar` serve binary với Cache-Control; navbar avatar update sau reload.
  3. User upload `.exe` hoặc file > 2MB → backend reject 422 với error code rõ ràng; FE hiển thị field-level error (không crash).
  4. Gateway route order verified: `user-service-me` ĐỨNG TRƯỚC `user-service-base` — `PATCH /api/users/me` không match `/api/users/{id}` với id="me".
**Plans:** 3 plans
- [x] 10-01-PLAN.md — Backend GET/PATCH /users/me + UserDto.hasAvatar (ACCT-03)
- [x] 10-02-PLAN.md — Frontend deps install (react-hook-form + zod + @hookform/resolvers — set rhf+zod foundation cho v1.2)
- [x] 10-03-PLAN.md — Frontend services + Profile Info section + Avatar placeholder (ACCT-03; ACCT-04 deferred per CONTEXT D-08)

**Note:** ACCT-04 (avatar upload) DEFERRED to backlog per CONTEXT D-08 — Phase 10 ship Profile Editing only. Success Criteria 2 & 3 (avatar upload flows) deferred together. SC-1 và SC-4 vẫn trong scope.
**UI hint:** yes

### Phase 11: Address Book + Order History Filtering

**Goal:** Ship address book CRUD + checkout integration + order filter bar — gom 2 features cùng `/profile/*` route family với size phù hợp 1 phase.
**Depends on:** Phase 10 (user-svc schema cluster + form pattern foundation)
**Requirements:** ACCT-02 (order filter), ACCT-05 (address CRUD), ACCT-06 (address checkout integration)
**Success Criteria** (what must be TRUE):
  1. User logged-in vào `/profile/addresses` → list addresses + create/edit/delete + radio chọn default; cap 10 — address thứ 11 trả 422 `ADDRESS_LIMIT_EXCEEDED` với error message rõ ràng.
  2. User checkout → AddressPicker component hiển thị saved addresses với default selected; chọn 1 address → snapshot full vào `OrderEntity.shippingAddress` JSONB (KHÔNG FK); hard-delete address sau đó KHÔNG ảnh hưởng order history (snapshot intact).
  3. Concurrent set-default 2 addresses (2 tab) → DB chỉ giữ 1 row `is_default=true` per user (partial unique index enforced).
  4. User vào `/profile/orders` → filter bar (status dropdown + date range native input + keyword search) → URL state encode (`?status=PAID&from=2026-04-01&to=2026-04-30&q=ORD-123`); back/forward browser preserve filter.
  5. Date range timezone correct: đơn lúc 23:59 GMT+7 ngày 30/4 hiển thị khi filter "tháng 4" (KHÔNG miss do UTC offset).
**Plans:** 6 plans
Plans:
- [ ] 11-01-PLAN.md — Backend user-service: V4 Flyway migration + AddressEntity/Repository/Service/Controller (ACCT-05)
- [ ] 11-02-PLAN.md — Backend order-service: extend listMyOrders() voi filter params (status/from/to/q) + UTC+7 timezone (ACCT-02)
- [ ] 11-03-PLAN.md — [BLOCKING] Gateway route verify + user-service restart -> Flyway V4 apply
- [ ] 11-04-PLAN.md — Frontend types (SavedAddress) + service functions (5 address CRUD + listMyOrders filter)
- [ ] 11-05-PLAN.md — Frontend components: AddressCard + AddressForm + AddressPicker + OrderFilterBar
- [ ] 11-06-PLAN.md — Frontend pages: /profile/addresses + /profile/orders + profile tab redirects + checkout AddressPicker
**UI hint:** yes

### Phase 12: Wishlist

**Goal:** Ship wishlist feature standalone — heart icon toggle + dedicated page + move-to-cart workflow.
**Depends on:** Phase 10 (user-svc schema cluster — V5 wishlists migration sequenced sau V3/V4)
**Requirements:** ACCT-01 (wishlist)
**Success Criteria** (what must be TRUE):
  1. User logged-in click heart icon trên product card / PDP → product save vào wishlist; refresh page heart vẫn filled (persistent); navbar wishlist count badge update.
  2. User vào `/profile/wishlist` → list items với realtime stock display (JOIN qua `/api/products?ids=...` batch); item out-of-stock vẫn hiển thị nhưng "Move to cart" button disabled với badge "Hết hàng".
  3. Click "Move to cart" → product add vào cart (respect stock) + remove khỏi wishlist; double-click không tạo duplicate (UNIQUE `(user_id, product_id)` enforced).
  4. Click heart khi chưa login → toast "Đăng nhập để lưu wishlist" + redirect `/login?returnTo=...` (KHÔNG localStorage shadow).
**Plans:** TBD
**UI hint:** yes

### Phase 13: Reviews & Ratings

**Goal:** Ship reviews end-to-end với verified-buyer eligibility + XSS-safe rendering + average rating recompute from scratch.
**Depends on:** Phase 9 (auth foundation), order-svc DELIVERED orders existing
**Requirements:** REV-01 (submission), REV-02 (list), REV-03 (avg rating cached), REV-04 (author edit/delete)
**Success Criteria** (what must be TRUE):
  1. User logged-in đã có order DELIVERED chứa product → PDP hiển thị review form (rating 1-5 + content textarea); submit → POST `/api/products/{id}/reviews` → backend OWASP sanitize content → review appear in list.
  2. User CHƯA mua product (no order DELIVERED) submit review → backend trả 422 `REVIEW_NOT_ELIGIBLE`; FE hide form với hint "Chỉ user đã mua mới review được".
  3. PDP review list hiển thị paginated 10/page, sort newest first, mỗi item show reviewer displayName (snapshot) + rating sao + content (rendered as plain text — XSS payload `<script>` hiển thị literal text, KHÔNG execute).
  4. Product card + PDP header hiển thị `avg_rating` (1 decimal) + `review_count` từ cached cols; sau khi user edit/delete review → recompute from scratch (`SELECT AVG, COUNT FROM reviews WHERE product_id=?`) — KHÔNG drift sau 10 edit cycles.
  5. Review author có thể edit content/rating hoặc delete review của mình (PATCH/DELETE `/api/reviews/{id}` ownership check); admin edit/delete review user khác defer v1.3.
**Plans:** TBD
**UI hint:** yes

### Phase 14: Advanced Search Filters

**Goal:** Ship faceted filters trên `/search` và `/products` — JPA Specification cho 4 facets + URL state encoding + clear-all.
**Depends on:** Phase 13 (rating filter cần `avg_rating` cached col từ V5)
**Requirements:** SEARCH-01 (brand), SEARCH-02 (price), SEARCH-03 (rating), SEARCH-04 (in-stock + URL + clear-all)
**Success Criteria** (what must be TRUE):
  1. User vào `/search?q=laptop` → FilterSidebar component hiển thị brand checkboxes (distinct brands), price range min/max input, rating radio (≥4★/≥3★/≥2★/Tất cả), in-stock toggle.
  2. User check 2 brands (Dell + HP) + price 10-20M + rating ≥4 + in-stock → backend JPA Specification combine `brand IN (...) AND price BETWEEN ... AND avg_rating >= 4 AND stock > 0` → results match; same-facet OR (Dell hoặc HP), cross-facet AND.
  3. URL state encoded: `/search?q=laptop&brand=Dell,HP&priceMin=10000000&priceMax=20000000&ratingMin=4&inStock=true`; share link → bạn mở thấy cùng filter state; back/forward browser preserve.
  4. URL với 10 filters active < 1KB (CSV format `brand=Dell,HP,Asus`); price min > max → client + server reject 400.
  5. Clear-all button reset state về default; empty result → empty state với CTA "Thử bỏ filter X".
**Plans:** TBD
**UI hint:** yes

### Phase 15: Public Polish + Milestone Audit

**Goal:** Ship homepage redesign + PDP enhancements + Playwright v1.2 E2E expansion + milestone audit + tag — close v1.2.
**Depends on:** Phase 13 (PDP rating display), Phase 14 (search filters tested)
**Requirements:** PUB-01 (hero + featured), PUB-02 (categories + new arrivals), PUB-03 (gallery + specs + breadcrumb), PUB-04 (stock badge), TEST-02 (E2E expansion)
**Success Criteria** (what must be TRUE):
  1. Homepage `/` load với hero banner (CTA + WebP image, `next/image` priority cho LCP < 2.5s) + Featured products carousel (top-8 by createdAt DESC, CSS scroll-snap, no JS lib) + Categories grid (link đến brand-filtered `/products?brand=X`) + New Arrivals strip (top-8 newest, KHÔNG duplicate featured).
  2. PDP `/products/[slug]` hiển thị image gallery với yet-another-react-lightbox (click thumbnail → swap main → click main → fullscreen lightbox; ESC close, arrow keys nav, axe-core a11y pass) + specs table render từ `product.specs` JSONB constrained schema + breadcrumb `Home > {brand} > {product.name}` (clickable segments).
  3. PDP stock badge color-coded: green "Còn hàng" (stock ≥10), yellow "Sắp hết hàng" (1≤stock<10), red "Hết hàng" (stock=0); add-to-cart button hidden khi stock=0.
  4. Lighthouse CI gate pass: LCP < 2.5s trên homepage, hero image < 500KB; gallery a11y axe-core 0 violations.
  5. Playwright v1.2 E2E suite: 8+ new tests (wishlist, address book at checkout, profile editing, review submission, search filters, homepage navigation, PDP gallery, password change) PASS trên fresh docker stack; toàn bộ v1.1 + v1.2 = 100% pass.
  6. Milestone audit completed (audit doc + verifier scan + tag `v1.2`); 23/23 REQs satisfied hoặc gaps documented.
**Plans:** TBD
**UI hint:** yes

---

## Progress Tracking

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 9. Residual Closure & Verification | 0/5 | Planned | - |
| 10. User-Svc Schema + Profile Editing | 0/3 | Planned | - |
| 11. Address Book + Order Filtering | 0/? | Not started | - |
| 12. Wishlist | 0/? | Not started | - |
| 13. Reviews & Ratings | 0/? | Not started | - |
| 14. Advanced Search Filters | 0/? | Not started | - |
| 15. Public Polish + Milestone Audit | 0/? | Not started | - |

---

## Coverage Validation

**Total v1.2 REQs:** 23
**Mapped:** 23 ✓
**Orphans:** 0 ✓
**Duplicates:** 0 ✓

| REQ-ID | Phase | Category |
|--------|-------|----------|
| AUTH-06 | Phase 9 | AUTH (residual) |
| AUTH-07 | Phase 9 | AUTH (residual) |
| UI-02 | Phase 9 | ADMIN (residual) |
| TEST-01 | Phase 9 | TEST (residual) |
| ACCT-03 | Phase 10 | ACCT (profile fullName/phone) |
| ACCT-04 | Phase 10 | ACCT (avatar upload) |
| ACCT-02 | Phase 11 | ACCT (order filter) |
| ACCT-05 | Phase 11 | ACCT (address CRUD) |
| ACCT-06 | Phase 11 | ACCT (address checkout integration) |
| ACCT-01 | Phase 12 | ACCT (wishlist) |
| REV-01 | Phase 13 | REV (submission) |
| REV-02 | Phase 13 | REV (list) |
| REV-03 | Phase 13 | REV (avg rating cached) |
| REV-04 | Phase 13 | REV (author edit/delete) |
| SEARCH-01 | Phase 14 | SEARCH (brand) |
| SEARCH-02 | Phase 14 | SEARCH (price) |
| SEARCH-03 | Phase 14 | SEARCH (rating) |
| SEARCH-04 | Phase 14 | SEARCH (inStock + URL state) |
| PUB-01 | Phase 15 | PUB (hero + featured) |
| PUB-02 | Phase 15 | PUB (categories + new arrivals) |
| PUB-03 | Phase 15 | PUB (gallery + specs + breadcrumb) |
| PUB-04 | Phase 15 | PUB (stock badge) |
| TEST-02 | Phase 15 | TEST (E2E expansion) |

---

*Roadmap created: 2026-04-26 by gsd-roadmapper. Granularity: standard. Synthesizer recommendation 7 phases adopted. Phase numbering tiếp tục từ Phase 9 (v1.1 ended at Phase 8). Visible-first priority giữ nguyên.*
