# Milestone v1.2 Requirements — UI/UX Completion

**Milestone:** v1.2 — UI/UX Completion
**Started:** 2026-04-26
**Goal:** Hoàn thiện trải nghiệm UI/UX end-user — close residual gaps từ v1.1 + thêm 8 visible features (account / discovery / checkout address book / public polish), nâng demo flow lên "trải nghiệm UX gần production-ready".

**Priority rule:** Visible-first — defer backend hardening invisible.

**Phase numbering:** tiếp tục từ Phase 9 (KHÔNG reset).

**Locked decisions (từ research synthesis + user confirmation 2026-04-26):**
- Avatar: multipart upload + Thumbnailator 0.4.20 resize 256×256 + Postgres BYTEA inline (max 2MB, magic byte verify)
- Route prefix: `/profile/*` consolidation (`/profile/orders` v1.1 + `/profile/settings|wishlist|addresses` v1.2)
- Reviews eligibility: verified buyer only (cross-service check — order-svc delivered orders)
- AUTH-06 status: **chưa đóng** (codebase verified `middleware.ts:29` matcher = `/admin/:path*`) → plan đầy đủ
- Featured products: top-8 by `createdAt DESC` (KHÔNG thêm `featured BOOLEAN` column)
- Address limit: 10/user với `ADDRESS_LIMIT_EXCEEDED` error code

---

## v1.2 Requirements (Active)

### AUTH (Residual closure — Phase 9)

- [ ] **AUTH-06**: middleware.ts matcher mở rộng — extend từ `/admin/:path*` thành cover `/admin/:path*`, `/account/:path*`, `/profile/:path*`, `/checkout/:path*`. Skip `/api/users/auth/*` (login/register/logout). Kết quả: direct visit `/profile/orders` chưa login → 307 redirect sang `/login?returnTo=/profile/orders`.
- [ ] **AUTH-07**: Password change flow — endpoint dedicated `POST /api/users/me/password` nhận `{oldPassword, newPassword}`, BCrypt verify oldPassword trước khi update. KHÔNG cho phép password trong PATCH chung. FE form trong `/profile/settings` với 3 fields (current/new/confirm) + zod validate.

### ADMIN (Residual closure — Phase 9)

- [ ] **UI-02**: Admin landing dashboard (`/admin`) hiển thị 4 KPI thật — Total products, Total orders, Total users, Pending orders. FE dùng `Promise.all` gọi 3 endpoints `/api/products/stats`, `/api/orders/stats`, `/api/users/stats` (KHÔNG cross-service backend aggregation). Loading skeleton + error fallback per card.

### TEST (Residual + new — Phase 9 + Phase 15)

- [ ] **TEST-01**: Playwright E2E re-baseline cho v1.1 features — update selectors cho auth flow, admin CRUD (products/orders/users), order detail page. Baseline 100% pass trên fresh docker stack.
- [ ] **TEST-02**: Playwright E2E mở rộng cho v1.2 features — smoke happy paths cho wishlist, address book at checkout, profile editing, review submission, search filters, homepage navigation, PDP gallery. Min 8 new tests passing.

### ACCT (Account features — Phase 10, 11, 12)

- [ ] **ACCT-01**: Wishlist — user logged-in click heart icon trên product card / PDP để save; `/profile/wishlist` page hiển thị danh sách với move-to-cart button per item. Backend: user-svc V5 wishlists table, unique constraint (user_id, product_id). Stock display realtime qua JOIN `/api/products?ids=`.
- [ ] **ACCT-02**: Order history filtering — `/profile/orders` thêm filter bar: status dropdown (all / pending / paid / shipped / delivered / cancelled), date range (from/to native input), keyword search (order ID hoặc product name). URL state encoding. Pagination offset (low volume v1.2).
- [ ] **ACCT-03**: Profile editing fullName/phone — `/profile/settings` form (rhf + zod) cho phép edit fullName + phone. Endpoint `PATCH /api/users/me` (gateway route `user-service-me` ĐỨNG TRƯỚC `user-service-base` để tránh match `/api/users/{id}` với id="me"). Email change disabled v1.2 (display-only).
- [ ] **ACCT-04**: Avatar upload — `/profile/settings` Avatar section. Upload multipart (max 2MB), backend validate magic byte (JPEG/PNG/WebP), Thumbnailator resize 256×256 JPEG, lưu BYTEA cột `users.avatar`. GET `/api/users/{id}/avatar` serve binary. FE fallback initials avatar khi null.
- [ ] **ACCT-05**: Address book CRUD — `/profile/addresses` với list + create/edit/delete + set-default. Backend user-svc V4 addresses table với `is_default BOOLEAN` + partial unique index `WHERE is_default = true` per user. Cap 10 addresses/user (return 422 `ADDRESS_LIMIT_EXCEEDED`).
- [ ] **ACCT-06**: Address book integrate checkout — checkout page hiển thị AddressPicker component (chọn từ saved list, default selected). Snapshot full address vào `OrderEntity.shippingAddress` JSONB (KHÔNG foreign key — preserve hard-delete safety). Vẫn cho phép manual entry option.

### REV (Reviews & ratings — Phase 13)

- [ ] **REV-01**: Review submission — user logged-in đã mua product (cross-service check qua order-svc: tồn tại order với status=DELIVERED chứa product_id) submit form `{rating: 1-5, content: plain text}` trong PDP. Backend: product-svc V4 reviews table, BE OWASP HTML sanitize content. 422 `REVIEW_NOT_ELIGIBLE` nếu không thoả điều kiện.
- [ ] **REV-02**: Review list trên PDP — hiển thị danh sách reviews với reviewer displayName + rating sao + content + createdAt, pagination 10/page. Default sort: newest first.
- [ ] **REV-03**: Average rating + review count cached — ProductEntity thêm `avg_rating DECIMAL(2,1)` + `review_count INT`. Recompute from scratch trong `@PostPersist/Update/Remove` của ReviewEntity (KHÔNG dùng formula incremental — tránh drift). Hiển thị trên product card + PDP header.
- [ ] **REV-04**: Review author edit/delete — author của review có thể edit content/rating hoặc delete review của mình; trigger recompute REV-03. Admin soft-delete defer (out of scope v1.2).

### SEARCH (Advanced filters — Phase 14)

- [ ] **SEARCH-01**: Brand filter — multi-select trên `/search` và `/products`, OR within facet (brand IN [...]). FilterSidebar component với checkboxes liệt kê brands distinct.
- [ ] **SEARCH-02**: Price range filter — min/max numeric input, kết hợp AND với brand/keyword. Validate min ≤ max client + server.
- [ ] **SEARCH-03**: Rating filter — radio "≥ 4 sao", "≥ 3 sao", "≥ 2 sao", "Tất cả". Dùng cached `avg_rating` từ REV-03. Backend JPA Specification `cb.greaterThanOrEqualTo(root.get("avgRating"), threshold)`.
- [ ] **SEARCH-04**: In-stock filter + URL state + clear-all — boolean toggle "Chỉ hiện còn hàng" (`stock > 0`). Toàn bộ filter state encode trong URL query (`?brand=Dell,HP&priceMin=10000000&ratingMin=4&inStock=true`). Clear-all button reset state về default.

### PUB (Public polish — Phase 15)

- [ ] **PUB-01**: Homepage hero + featured products — hero banner section (CTA + image, `next/image` priority + WebP cho LCP), Featured products carousel (top-8 by `createdAt DESC`, CSS scroll-snap KHÔNG cần lib).
- [ ] **PUB-02**: Homepage categories spotlight + new arrivals — Categories grid (link đến brand-filtered `/products?brand=X`) + New arrivals strip (top-8 newest, khác section featured để tránh duplicate).
- [ ] **PUB-03**: PDP image gallery + specs table + breadcrumb — yet-another-react-lightbox cho gallery (ESC close, arrow keys nav, axe-core a11y pass), specs table render từ `product.specs` JSONB constrained schema, breadcrumb `Home > {brand} > {product.name}`.
- [ ] **PUB-04**: PDP stock badge — badge color-coded: green "Còn hàng" (stock ≥ 10), yellow "Sắp hết hàng" (1 ≤ stock < 10), red "Hết hàng" (stock = 0). Hide add-to-cart khi stock = 0.

---

## Future Requirements (Deferred v1.3+)

- [ ] Multi-step checkout (Shipping → Payment → Review)
- [ ] Recently viewed products + Related products rule-based
- [ ] Email change với verification flow
- [ ] Verified-purchase reviews → public moderation queue
- [ ] Review images attachment + helpful votes
- [ ] Wishlist multi-list + sharing
- [ ] Product comparison
- [ ] Facet counts cho search filters (cần caching strategy)
- [ ] Admin dashboard charts (revenue trends, conversion funnel)
- [ ] Cursor pagination cho order history (replace offset)
- [ ] Address validation API integration

## Out of Scope (Explicit exclusions)

- WebSocket / real-time sync (polling đủ cho v1.2)
- Elasticsearch / OpenSearch (Postgres + JPA Specifications đủ ở scale demo)
- Redis caching layer (no-cache acceptable, recompute < 200ms ở scale hiện tại)
- MinIO/S3 object store (BYTEA inline đủ cho avatar)
- Review moderation queue / admin approval flow (any logged-in verified buyer → immediately visible)
- Image zoom on hover, advanced gallery interactions
- Address validation regional-aware (cap 10 + basic format đủ)
- Backend hardening invisible (D1..D17 carry-over từ v1.1) — vẫn defer theo project priority

---

## Traceability (Phase Mapping)

_Filled by gsd-roadmapper 2026-04-26. Final split: 7 phases (Phase 9-15). 23/23 REQs mapped — no orphans, no duplicates._

| REQ-ID | Phase | Status | Plan |
|--------|-------|--------|------|
| AUTH-06 | Phase 9 (Residual Closure & Verification) | Pending | TBD |
| AUTH-07 | Phase 9 (Residual Closure & Verification) | Pending | TBD |
| UI-02 | Phase 9 (Residual Closure & Verification) | Pending | TBD |
| TEST-01 | Phase 9 (Residual Closure & Verification) | Pending | TBD |
| ACCT-03 | Phase 10 (User-Svc Schema + Profile Editing) | Pending | TBD |
| ACCT-04 | Phase 10 (User-Svc Schema + Profile Editing) | Pending | TBD |
| ACCT-02 | Phase 11 (Address Book + Order Filtering) | Pending | TBD |
| ACCT-05 | Phase 11 (Address Book + Order Filtering) | Pending | TBD |
| ACCT-06 | Phase 11 (Address Book + Order Filtering) | Pending | TBD |
| ACCT-01 | Phase 12 (Wishlist) | Pending | TBD |
| REV-01 | Phase 13 (Reviews & Ratings) | Pending | TBD |
| REV-02 | Phase 13 (Reviews & Ratings) | Pending | TBD |
| REV-03 | Phase 13 (Reviews & Ratings) | Pending | TBD |
| REV-04 | Phase 13 (Reviews & Ratings) | Pending | TBD |
| SEARCH-01 | Phase 14 (Advanced Search Filters) | Pending | TBD |
| SEARCH-02 | Phase 14 (Advanced Search Filters) | Pending | TBD |
| SEARCH-03 | Phase 14 (Advanced Search Filters) | Pending | TBD |
| SEARCH-04 | Phase 14 (Advanced Search Filters) | Pending | TBD |
| PUB-01 | Phase 15 (Public Polish + Milestone Audit) | Pending | TBD |
| PUB-02 | Phase 15 (Public Polish + Milestone Audit) | Pending | TBD |
| PUB-03 | Phase 15 (Public Polish + Milestone Audit) | Pending | TBD |
| PUB-04 | Phase 15 (Public Polish + Milestone Audit) | Pending | TBD |
| TEST-02 | Phase 15 (Public Polish + Milestone Audit) | Pending | TBD |

**Coverage check:** 23/23 REQs mapped ✓ · 0 orphans ✓ · 0 duplicates ✓

---

*Tổng: 23 REQs across 7 categories. Final: 7 phases (Phase 9-15). Research artifacts: `.planning/research/` (STACK, FEATURES, ARCHITECTURE, PITFALLS, SUMMARY). Roadmap: `.planning/ROADMAP.md`.*
