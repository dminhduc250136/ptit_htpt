# Feature Research — v1.2 UI/UX Completion

**Domain:** B2C E-commerce (laptop catalog) — visible-first priority
**Researched:** 2026-04-26
**Confidence:** HIGH (industry patterns well-documented, Baymard/NN Group/Shopify references)
**Scope:** 11 target features for v1.2 milestone (3 residual + 8 new)

> Project nature: dự án thử nghiệm GSD workflow. Categorization dưới đây ưu tiên **visible-first** + **scope phù hợp dự án thử nghiệm** (không phải production e-commerce thật). "Table stakes" = user kỳ vọng có ở mọi e-commerce site; "Differentiator (skip)" = phần dễ over-engineer mà nên cắt.

## Feature Landscape

### Table Stakes (Users Expect These)

Missing = product feels incomplete. All 11 v1.2 features fall here OR have a table-stakes core subset.

| # | Feature | Why Expected | Complexity | Notes |
|---|---------|--------------|------------|-------|
| 1 | **AUTH-06 middleware mở rộng** | Bảo vệ `/account\|/profile\|/checkout` khỏi unauthenticated access (SSR-bounce) — hiện chỉ `/admin` có gate; compensating control http.ts 401 redirect đã hoạt động nhưng UX kém (flash content trước khi redirect) | **LOW** | Sửa matcher trong `middleware.ts`. Test: visit `/profile` khi logout → redirect `/login`. |
| 2 | **UI-02 admin landing dashboard (KPI thật)** | Admin login vào `/admin` thấy KPI = 0 → broken impression. KPI cơ bản: revenue today, orders pending, low-stock count, new users (last 7d) | **MEDIUM** | Cần aggregate endpoint (hoặc 4 calls song song). 4-8 widgets là sweet spot cho admin landing (quick glance). |
| 3 | **Playwright E2E re-baseline** | Regression net cho v1.1 features (auth real, admin CRUD, order detail full breakdown) — không visible cho end-user nhưng visible cho dev/QA | **MEDIUM** | Update existing 12 tests + thêm cho login real + admin orders + order detail. KHÔNG viết E2E mới cho v1.2 features chưa build (sẽ làm trong phase tương ứng). |
| 4 | **Wishlist / Favorites** | 60% sites yêu cầu account để dùng wishlist — Baymard flag "severe UX mistake". Kỳ vọng: heart icon trên product card + PDP, trang `/profile/wishlist`, "move to cart" button | **MEDIUM** | Persistent (DB) cho logged-in user. Single list (không multi-list — đó là differentiator skip). Icon ở: PDP, product card, navbar count badge. |
| 5 | **Order history filtering** | Standard pattern: filter status (5 STATUS_OPTIONS), date range (preset: 7d/30d/90d/1y/custom), search theo orderId. Hiện tại `/profile/orders` chỉ list flat | **MEDIUM** | Date range UX: dropdown preset + custom range. Status: multi-select pills. Search: orderId substring. Combined filters AND. |
| 6 | **User profile editing** | Settings page kỳ vọng: edit fullName, phone, avatar (upload), change password (with old-password verify) | **MEDIUM** | Old password verify = bắt buộc (security baseline). Avatar: 1MB max, JPG/PNG, backend resize 256x256. PATCH `/api/users/me`. |
| 7 | **Product reviews & ratings** | 5-star + comment trên PDP, average rating ở product card. UGC convert at 161% higher rate. | **HIGH** | Core: 1 review per user per product, edit/delete by author, average rating tính lại sau khi review. Verified-purchase gating = differentiator skip. Helpful votes = differentiator skip. |
| 8 | **Advanced search filters** | Faceted search: brand multi-select, price range, rating ≥X, in-stock toggle. Hiện tại `/search` chỉ keyword. | **HIGH** | Multi-select brand, price min/max input, rating chips (4★+/3★+ — only if Reviews shipped first), in-stock checkbox. Active filter chips + "Clear all" button. Counts per facet skip. |
| 9 | **Address book** | Save nhiều địa chỉ, default address concept, edit/delete, chọn từ saved list khi checkout | **MEDIUM** | CRUD: list, add, edit, delete, set-default. Default = checkbox → 1 address per user is default. Validation: required fields (recipient name, phone, line1, city). External validation API skip (VN không có ZIP chuẩn). |
| 10 | **Homepage redesign** | Hero banner + featured products (4-8) + categories (4-8 main) + new arrivals. Hiện tại "basic" homepage | **MEDIUM** | Sections: (1) Hero with CTA "Shop New Arrivals", (2) Categories grid, (3) Featured/New arrivals (8 products by createdAt DESC). "Featured" = "New arrivals" để giảm scope (no `featured` flag migration). |
| 11 | **Product detail enhancements** | Image gallery (thumbnails + main), specs table, stock badge ("In Stock" / "Only N left" / "Out of stock"), breadcrumb (Home > Category > Product) | **MEDIUM** | Gallery: click thumbnail → swap main image. Zoom skip. Specs: key-value table (CPU, RAM, GPU, Display, Storage). Cần `images_json` column migration. |

### Differentiators — Skip Sub-features (Ship Core)

Những gì có thể **cắt** khỏi feature scope để rút gọn delivery — vẫn hoạt động đầy đủ table-stakes UX.

| Skip Sub-feature | Parent Feature | Why Skip | Defer To |
|------------------|----------------|----------|----------|
| Multi-list wishlists ("Birthday list", "Work setup") | Wishlist | Single list đủ cho 95% users; multi-list complicates UI | v1.3+ |
| Verified-purchase gating cho reviews | Reviews | Cần join order_items + reviews; demo project không cần fraud prevention | v1.3+ optional |
| Helpful/not-helpful votes | Reviews | Cần thêm bảng review_votes + dedup theo user; low-value cho demo | v1.3+ |
| Review images/video upload | Reviews | Cần image upload pipeline + storage; chỉ value khi có user base thật | v1.3+ |
| Facet counts ("Apple (12)") | Advanced filters | Cần aggregate query mỗi lần filter change; performance complexity | v1.3+ nếu cần |
| Postcode autocomplete / address validation API | Address book | VN không có standard ZIP; Google Places API = external dependency | OUT |
| Save-for-later (cart-side, khác wishlist) | Wishlist | Wishlist + cart đã đủ; save-for-later overlaps | OUT |
| Image zoom (hover-magnifier hoặc lightbox) | PDP gallery | Click-to-swap thumbnails đủ table-stakes; zoom = polish | v1.3+ |
| Recently viewed products | PDP | Đã defer trong PROJECT.md "Deferred v1.3+" | v1.3+ |
| Related products | PDP | Đã defer trong PROJECT.md | v1.3+ |
| Conversion rate / RPV / CLV / CAC trên admin dashboard | UI-02 dashboard | Cần session tracking + customer cohorts; over-engineered cho demo | OUT |
| Dashboard time-series charts (revenue trend 30d) | UI-02 dashboard | Bonus visual; KPI cards đủ "fix broken impression" | v1.3+ |
| Email change flow (verify token) | Profile editing | Cần email service + token table; demo OK với fullName/phone/avatar/password only | v1.3+ |
| Avatar crop UI (drag/zoom) | Profile editing | Library cost; backend auto-resize đủ | v1.3+ |
| Rating filter trong Advanced filters | Advanced filters | Block dependency on Reviews; có thể skip nếu Reviews phase chưa xong | Add nếu Reviews ship trước |

### Anti-Features (Don't Build — Out of Scope)

| Anti-Feature | Why Requested | Why Problematic | Alternative |
|--------------|---------------|-----------------|-------------|
| **Real-time wishlist sync via WebSocket** | "Modern UX" | Polling/refresh đủ; WebSocket infra cho demo project = over-engineer | Refresh on page load |
| **Review moderation queue with admin approval** | "Prevent spam" | Demo không có user base spam; thêm 1 phase admin chỉ cho review approval | Auto-publish + soft-delete by admin retroactively |
| **Address validation against external API (Google/USPS)** | "Reduce delivery errors" | External dependency, API key, rate limits, không phù hợp VN | Required-field validation đủ |
| **Multi-step password change (email confirm + reset link)** | "Security best practice" | Cần email service + token table; old-password verify đủ cho demo | Old-password + new-password form, sync change |
| **Faceted search với Elasticsearch / Solr / OpenSearch** | "Performance at scale" | Catalog ~50 products; Postgres LIKE + indexed columns đủ | SQL filters với IN/BETWEEN |
| **Product comparison table (compare 3 laptops)** | "Laptop shopping needs comparison" | Ngoài scope 11 items; PDP đủ chi tiết | Defer v1.3+ |
| **Wishlist sharing via public link** | "Social/gift" | Cần public-token route + share UI; privacy concerns | Defer indefinitely |
| **Real-time low-stock indicator ("3 people viewing")** | "Urgency / FOMO" | Dark pattern, requires session tracking | Just show stock count |
| **Admin dashboard drill-down filter pre-applied từ KPI card click** | "Productivity" | Static link to admin/orders đủ; drill-down = extra | Static link to filtered list |
| **Multi-step checkout (Shipping → Payment → Review)** | "Standard flow" | Đã defer trong PROJECT.md; current single-page checkout đủ | Defer v1.3+ (đã ghi) |

## Feature Dependencies

```
[AUTH-06 middleware]
    └──blocks──> [Wishlist page /profile/wishlist (cần protected route)]
    └──blocks──> [Order history filtering (/profile/orders cần protected)]
    └──blocks──> [Profile editing (/profile/settings cần protected)]
    └──blocks──> [Address book (/profile/addresses cần protected)]
    └──blocks──> [Checkout (/checkout cần protected)]

[Address book CRUD]
    └──enhances──> [Checkout shipping address selection]
                       (existing checkout đã có address form;
                        v1.2 thêm "Choose from saved" dropdown)

[Product reviews backend (review entity + endpoints)]
    └──blocks──> [Average rating display trên product card]
    └──blocks──> [Rating filter trong Advanced search filters (rating ≥4★)]
    └──enhances──> [PDP enhancements (rating section dưới gallery)]

[Advanced search filters]
    └──requires──> [ProductEntity có brand field] (đã có v1.1 V2 migration)
    └──conditional──> [Rating filter requires Reviews shipped first]

[Homepage redesign]
    └──decision──> ["Featured" = top-N by createdAt DESC] (avoid `featured` flag migration)
    └──verify──> [Codebase có category field trên Product?] (nếu không, dùng static category list)

[PDP enhancements (image gallery)]
    └──requires──> [Migration thêm images_json column trên products table]
                   (current chỉ có thumbnail single)

[UI-02 admin dashboard KPIs]
    └──requires──> [Aggregate endpoint /api/admin/dashboard/summary HOẶC client-side aggregate]
    └──enhances──> [Existing admin/orders, admin/products, admin/users sub-routes]

[Playwright E2E re-baseline]
    └──depends-on──> [v1.1 features đã ship (auth real, admin CRUD)]
    └──validates──> [Tất cả features mới khi shipped (per-phase E2E sẽ thêm sau)]
```

### Critical Dependencies (Roadmap Implications)

- **AUTH-06 unblocks 4 protected routes** → Phase đầu tiên (residual closure cluster) phải làm AUTH-06.
- **Reviews backend blocks rating filter trong Advanced search** → Build Reviews trước Advanced filters, HOẶC skip rating filter.
- **Image gallery requires images_json migration** → Cần V4 Flyway migration. Block PDP enhancements.
- **Address book CRUD nên có TRƯỚC khi rewire checkout** → Checkout integration là 1 small task sau khi address book CRUD xong.
- **Featured flag decision**: Recommend dùng "top 8 by createdAt DESC" làm proxy thay vì thêm `featured BOOLEAN` column + admin toggle UI. Giảm scope đáng kể.
- **Categories source verification**: Check Product entity có category field chưa. Nếu không, dùng static list (Gaming / Văn phòng / Đồ họa / Macbook) link tới `?brand=` filtered URL.

## MVP Definition (within v1.2 scope)

### Must Ship (P1) — All 11 features in committed form

v1.2 charter đã commit 11 features; cắt feature = re-scope milestone. Cách giảm risk: **cắt sub-features** (Differentiators table) thay vì cắt parent features.

- [x] **AUTH-06 middleware** — matcher mở rộng `/account|/profile|/checkout`
- [x] **UI-02 admin dashboard** — 4 KPI cards (revenue today, orders pending, low-stock count, new users 7d)
- [x] **Playwright re-baseline** — update existing 12 tests cho v1.1 features
- [x] **Wishlist** — single list, persistent, heart icon trên PDP + product card, `/profile/wishlist` page với "Move to cart" + "Remove"
- [x] **Order history filtering** — status pills + date preset dropdown + orderId search
- [x] **Profile editing** — fullName + phone + avatar upload + password change with old-password verify
- [x] **Reviews & ratings** — 1 review per user/product, 5-star + comment, edit/delete by author, average rating trên product card + PDP (skip verified-purchase, skip helpful votes)
- [x] **Advanced search filters** — brand multi-select + price min/max + in-stock toggle (rating filter conditional on Reviews shipped first)
- [x] **Address book** — list/add/edit/delete + default + checkout integration
- [x] **Homepage redesign** — hero + categories grid + new arrivals (= "featured")
- [x] **PDP enhancements** — image gallery (thumbnails + main, click swap) + specs table + stock badge + breadcrumb

### Add After v1.2 (v1.3+ candidates)

- [ ] Verified-purchase badge cho reviews
- [ ] Helpful/not-helpful votes
- [ ] Image zoom trong PDP gallery
- [ ] Facet counts cho filters
- [ ] Recently viewed / Related products
- [ ] Multi-step checkout (Shipping → Payment → Review)
- [ ] Multi-list wishlists
- [ ] Featured flag + admin toggle UI cho homepage
- [ ] Time-series charts trên admin dashboard

### Future Consideration (v2+)

- [ ] Product comparison
- [ ] Wishlist sharing
- [ ] Review images/video
- [ ] Real CLV / CAC / cohort metrics
- [ ] Email change flow with verification token
- [ ] Real-time stock/cart sync

## Feature Prioritization Matrix

Priority **trong v1.2** (tất cả P1 vì đã commit) — sort theo dependency order + complexity:

| # | Feature | User Value | Implementation Cost | Dependency Order | Phase Suggestion |
|---|---------|------------|---------------------|------------------|------------------|
| 1 | AUTH-06 middleware | MEDIUM | LOW | First (unblocks 4 routes) | Phase 9 (residual) |
| 2 | UI-02 admin dashboard | LOW (admin only) | MEDIUM | Standalone | Phase 9 (residual) |
| 3 | Playwright re-baseline | LOW (dev/QA) | MEDIUM | After v1.1 stable | Phase 9 (residual) |
| 4 | Address book | MEDIUM | MEDIUM | Before checkout integration | Phase 10 (account) |
| 5 | Profile editing | MEDIUM | MEDIUM | Standalone (after AUTH-06) | Phase 10 (account) |
| 6 | Wishlist | HIGH | MEDIUM | Standalone (after AUTH-06) | Phase 10 (account) |
| 7 | Order history filtering | MEDIUM | MEDIUM | Standalone (after AUTH-06) | Phase 10 (account) |
| 8 | Reviews & ratings | HIGH | HIGH | Before rating filter (if any) | Phase 11 (discovery) |
| 9 | Advanced search filters | HIGH | HIGH | After Reviews (if rating filter included) | Phase 11 (discovery) |
| 10 | Homepage redesign | HIGH | MEDIUM | After "featured" decision | Phase 12 (public polish) |
| 11 | PDP enhancements | HIGH | MEDIUM | Need product images migration | Phase 12 (public polish) |

**Priority key:** All 11 are P1 (committed in milestone charter). Order is dependency-driven, not value-driven.

## Per-Feature Behavior Spec (concise reference)

### Wishlist
- **Persistence:** DB per logged-in user. KHÔNG support guest wishlist (compensating: prompt login khi click heart while logged out, redirect back sau login).
- **Single list:** 1 wishlist per user (no naming, no multi-list).
- **UX:** Heart icon (filled = in wishlist, outline = not). Click = toggle. PDP + product card + navbar count badge.
- **Move to cart:** Button trên `/profile/wishlist` row → adds to cart (respects stock). Option "Move" (remove from wishlist after add) vs "Add" (keep).
- **Stock-aware:** Out-of-stock items vẫn lưu trong wishlist (khác với cart); badge "Out of stock" + disabled "Move to cart".

### Reviews & Ratings
- **Gating:** Logged-in users only. NO purchase verification (skip — defer v1.3+).
- **Constraint:** 1 review per (user, product). Submitting again = update existing.
- **Fields:** rating (1-5 required), title (optional, max 100), body (required, 10-2000 chars).
- **Edit/delete:** Author can edit/delete own review. Admin can soft-delete (existing pattern).
- **Average:** Compute on read hoặc cached field on ProductEntity, recompute sau mỗi review write.
- **Display:** PDP có section "Reviews (N)" với average + distribution bars + paginated list. Product card hiện star + count.

### Address Book
- **CRUD:** List, add, edit, delete (soft-delete OK).
- **Default:** 1 address marked default. Setting another as default unmarks previous (transactional).
- **Validation:** Required = recipient name, phone, address line 1, city. Optional = line 2, district, country (default VN).
- **Checkout integration:** Checkout shipping section thêm dropdown "Choose from saved addresses" (default selected) + "Use new address" option (existing form). Submit checkout có option "Save this as new address".
- **Soft-delete safety:** Existing orders đã snapshot shippingAddress JSON (PERSIST-02 đã ship) → soft-delete address book entry không ảnh hưởng order history.

### Order History Filtering
- **Filter dimensions:** status (5 options multi-select pills) + date range (preset dropdown: 7d / 30d / 90d / 1y / All / Custom) + search box (orderId substring).
- **Date custom range:** Date picker pair (start ≤ end validation).
- **Combination:** AND across dimensions.
- **Backend:** Existing `/api/orders` GET cần thêm query params: `?status=PENDING,SHIPPED&from=2026-01-01&to=2026-04-30&search=ORD-123`.
- **Empty state:** "No orders match your filters" + "Clear filters" button.

### Advanced Search Filters
- **Facets:** brand (multi-select checkbox list), price (min/max number inputs), in-stock (checkbox), rating (≥4★ / ≥3★ pills — only if Reviews shipped).
- **Active filters bar:** Chips above results với X button per chip + "Clear all".
- **URL state:** Filters persist trong URL query string (shareable, back/forward works).
- **Counts:** Skip per-facet counts (defer); show only total result count.
- **Mobile:** Filter button opens drawer/modal (mobile = 66% e-commerce traffic).
- **Apply behavior:** Auto-apply on change (no Apply button) — better UX per Baymard.

### Profile Editing
- **Fields:** fullName, phone (VN format hint), avatar (file upload), email (read-only display — change email skip).
- **Password change:** Separate sub-form / accordion với fields: currentPassword + newPassword + confirmNewPassword. Old password verify mandatory.
- **Avatar:** Max 1MB, JPG/PNG/WebP. Backend resize tới 256x256 square. Storage: filesystem hoặc DB blob (demo OK).
- **Validation:** Frontend hint + backend enforce. Field-level errors.
- **Success UX:** Toast "Profile updated" + immediate avatar/name reflect trong navbar.

### Homepage Redesign
- **Section order:** (1) Hero banner full-width with CTA "Shop New Arrivals" (link tới `/products?sort=newest`), (2) Categories grid (icons + label, link tới filtered `/products?category=X` hoặc `?brand=X`), (3) New Arrivals/Featured grid 8 items (product cards, link "View all"), (4) Optional trust signals.
- **"Featured":** = New Arrivals (top 8 by createdAt DESC) — không thêm `featured` flag để giảm scope.
- **Categories source:** Verify codebase trong phase planning. Nếu không có category field, dùng static list (Gaming / Văn phòng / Đồ họa / Macbook) link tới brand-filtered URL.
- **Hero content:** Static placeholder (image + headline + CTA button) — không cần CMS.

### PDP Enhancements
- **Breadcrumb:** Home > [Category] > [Product Name] (category clickable nếu category nav exists; otherwise just Home > Product).
- **Image gallery:** Main image (large) + thumbnail strip (4-6 thumbs). Click thumb = swap main. NO zoom.
- **Specs table:** 2-column key-value (Brand, CPU, RAM, GPU, Display, Storage, OS, Weight). Source: ProductEntity fields (cần verify schema; có thể cần migration thêm specs_json column hoặc reuse existing fields).
- **Stock badge:** "In Stock" green nếu stock ≥10, "Only N left" amber nếu 1≤stock<10, "Out of stock" red disabled nếu stock=0. Reuse existing stock từ V3 migration.
- **Required schema change:** `images_json TEXT` column trên ProductEntity (current chỉ có thumbnail single). Recommend `images_json` để đơn giản (vs `product_images` 1-N table).

### UI-02 Admin Dashboard
- **4 KPI cards (top of `/admin`):**
  1. **Revenue Today** — sum total of orders WHERE created_at::date = today (exclude CANCELLED)
  2. **Orders Pending** — count WHERE status IN (PENDING, PROCESSING)
  3. **Low Stock** — count products WHERE stock < 5
  4. **New Users (7d)** — count users WHERE created_at >= now() - 7d
- **Implementation:** Recommend `/api/admin/dashboard/summary` aggregate endpoint (1 round trip) thay vì client-side aggregate từ list endpoints.
- **Below KPIs:** Quick links to admin/products, admin/orders (latest 5 pending), admin/users — existing sub-routes.
- **Skip:** Charts, time-series, conversion rate, CLV, drill-down filters.

## Competitor / Reference Analysis

| Feature | Shopify default | Tiki/Lazada VN | Our v1.2 Approach |
|---------|-----------------|----------------|-------------------|
| Wishlist | Plugin-based, persistent | Yes, single list, persistent, navbar count | Single persistent list, navbar count, move to cart |
| Reviews | Verified-purchase optional | Verified-purchase + helpful votes + photos | Basic 5-star + comment, NO verification (demo scope) |
| Faceted search | Built-in (Liquid theme) | Brand + price slider + rating + multi-select | Brand multi + price min/max + in-stock (skip rating if Reviews not yet) |
| Address book | Account section | Yes, default + many addresses | CRUD + default + checkout dropdown |
| Order filtering | Status + date | Status tabs (5) + date | Status pills + date preset + orderId search |
| Homepage | Theme-based | Hero + flash sale + categories + recommendations | Hero + categories + new arrivals (no flash sale, no recs) |
| PDP gallery | Theme-based, zoom common | Thumbnails + zoom + 360° common | Thumbnails + click swap (no zoom for v1.2) |
| Admin dashboard | KPI + charts + AI insights | KPI + charts + filters | 4 KPI cards only (fix broken impression) |

## Quality Gate Checklist

- [x] Categories clear: 11 features categorized as table-stakes (with sub-feature differentiators marked skip)
- [x] Complexity noted: LOW (1) / MEDIUM (8) / HIGH (2) per feature
- [x] Dependencies identified: AUTH-06 → 4 protected routes; Address book → checkout; Reviews → rating filter; PDP gallery → images_json migration; Homepage → featured/categories source decision
- [x] Anti-features list: 10 items explicitly out-of-scope với alternative
- [x] Per-feature behavior spec: short reference cho roadmap consumer

## Sources

### Wishlist UX
- [Wishlist or shopping cart? — Nielsen Norman Group](https://www.nngroup.com/articles/wishlist-or-cart/)
- [How to design Wishlists for E-Commerce — Agencja UX](https://thestory.is/en/journal/designing-wishlists-in-e-commerce/)
- [Shopping Cart UX Guide 2026 — Trafiki](https://www.trafiki-ecommerce.com/marketing-knowledge-hub/the-ultimate-guide-to-shopping-cart-ux/)

### Reviews & Ratings
- [Ecommerce Product Reviews Best Practices — Contentsquare](https://contentsquare.com/guides/ecommerce-ux/product-review-section/)
- [Definitive Guide to eCommerce Product Reviews — Vervaunt](https://vervaunt.com/the-definitive-guide-to-ecommerce-product-reviews)
- [Product Reviews Trends 2026 — Amanda Lauren](https://amanda-lauren.com/product-reviews-trends-2026/)

### Faceted Search Filters
- [Faceted Search Best Practices for E-commerce 2026 — BrokenRubik](https://www.brokenrubik.com/blog/faceted-search-best-practices)
- [Faceted search: 9 best practices — Fact-Finder](https://www.fact-finder.com/blog/faceted-search/)
- [Faceted filtering for ecommerce — LogRocket](https://blog.logrocket.com/ux-design/faceted-filtering-better-ecommerce-experiences/)

### Admin Dashboard KPIs
- [Ecommerce Dashboard 15 KPIs Templates 2026 — Databrain](https://www.usedatabrain.com/blog/ecommerce-dashboard)
- [15 Essential e-commerce KPIs 2026 — ThoughtSpot](https://www.thoughtspot.com/data-trends/ecommerce-kpis-metrics)
- [Essential Ecommerce KPIs — Shopify](https://www.shopify.com/blog/7365564-32-key-performance-indicators-kpis-for-ecommerce)

### Address Book / Validation
- [Address Validation for Ecommerce Checkout — Google Maps Platform](https://developers.google.com/maps/architecture/ecommerce-checkout-address-validation)
- [328 Address Validator Design Examples — Baymard](https://baymard.com/checkout-usability/benchmark/step-type/address-validator)
- [E-commerce Checkout Design Deep Dive — Address Collection](https://medium.com/@isaacy/e-commerce-checkout-design-deep-dive-part-ii-address-collection-3229b80bc709)

### Homepage Design
- [Hero Section Design Best Practices 2026 — Perfect Afternoon](https://www.perfectafternoon.com/2025/hero-section-design/)
- [10 Crucial Elements for E-commerce Homepage — iCreations Lab](https://icreationslab.com/10-crucial-elements-for-high-converting-ecommerce-homepage-design/)
- [Best Ecommerce Homepage Design 2026 — FuturMedia](https://futurmedia.co.uk/blog/best-ecommerce-homepage-design)

### Product Detail Page
- [Product Page UX Best Practices 2026 — Baymard Institute](https://baymard.com/blog/current-state-ecommerce-product-page-ux)
- [Product Page UX 15 Best Practices 2026 — Koanthic](https://koanthic.com/en/product-page-ux-15-best-practices-guide-2026/)
- [Best Practices for Product Detail Pages 2026 — Scandiweb](https://scandiweb.com/blog/best-practices-for-product-detail-pages/)

### Profile / Password
- [Designing profile, account, and setting pages for better UX — Bootcamp](https://medium.com/design-bootcamp/designing-profile-account-and-setting-pages-for-better-ux-345ef4ca1490)
- [8 UI/UX tips about password design — DEV Community](https://dev.to/indieklem/8-uiux-tips-about-password-design-5bbn)

### Order History
- [Order history with search and filters module — Microsoft Dynamics 365 Commerce](https://learn.microsoft.com/en-us/dynamics365/commerce/order-history-module)
- [Viewing and filtering orders — Shopify Help Center](https://help.shopify.com/en/manual/fulfillment/managing-orders/viewing-orders)

---
*Feature research for: B2C E-commerce v1.2 UI/UX Completion*
*Researched: 2026-04-26*
*Confidence: HIGH — well-documented industry patterns, multiple authoritative sources cross-verified*
