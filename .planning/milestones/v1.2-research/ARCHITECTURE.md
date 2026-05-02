# Architecture Integration — v1.2 UI/UX Completion

**Milestone:** v1.2 (subsequent milestone — kế thừa v1.0 + v1.1 architecture)
**Researched:** 2026-04-26
**Mode:** Project research (integration, không re-research foundation)
**Confidence:** HIGH (verified against actual codebase files)

---

## 1. Existing Architecture — Recap (DO NOT re-investigate)

| Layer | State | Files of Truth |
|------|-------|----------------|
| Gateway | Spring Cloud Gateway, routes `/api/{service}/**` → service `:8080` với RewritePath. CORS allow `localhost:3000`, allowCredentials. | `sources/backend/api-gateway/src/main/resources/application.yml` |
| Auth | BCrypt + JWT HS256 24h. FE set 2 cookies: `auth_present` (presence flag), `user_role` (CSV roles). Backend validate JWT mỗi call qua `Authorization: Bearer`. | `sources/frontend/src/middleware.ts`, `services/token.ts` |
| Middleware | **Đã mở rộng** matchers: `/checkout|/profile|/admin|/account/:path*`. Admin role check cho `/admin/*`. | `sources/frontend/middleware.ts:41` |
| Persistence | Postgres 16, schema-per-service: `user_svc`, `product_svc`, `order_svc`. Flyway baseline V1 + cộng dồn V2/V3 per service. | `sources/backend/{svc}/src/main/resources/db/migration/` |
| FE codegen | `npm run gen:api` → 6 typed modules (`src/types/api/{svc}.generated.ts`) từ Springdoc OpenAPI. | `sources/frontend/scripts/gen-api.mjs` |
| FE service tier | 6 modules (`auth/users/products/orders/payments/inventory/notifications`) + `cart.ts` local + `http.ts` envelope unwrap + `errors.ts` 5-branch dispatcher | `sources/frontend/src/services/` |
| Error envelope | Unified `ApiErrorResponse{code,message,traceId,details}` cross-service | OpenAPI codegen |

**Critical observation:** middleware.ts hiện tại đã có `/account/:path*`, `/profile/:path*`, `/checkout/:path*` trong matcher. Carry-over AUTH-06 trong PROJECT.md có thể đã thực thi de-facto qua commit `346092b` hoặc chưa được cập nhật trong audit. **Phase đầu v1.2 cần verify state thực thay vì assume gap còn mở.**

---

## 2. Feature → Service Mapping (Where mỗi feature sống)

| Feature | Owner Service | Tables mới | Schema migrations | Gateway routes mới |
|---------|---------------|------------|-------------------|--------------------|
| Wishlist / Favorites | **user-service** | `user_svc.wishlists` | V5 | `/api/users/me/wishlist/**` (cần `user-service-me` route group, đặt trước `-base`) |
| Order history filtering | **order-service** (no schema change) | — | — (optional V3 index) | Tận dụng `/api/orders` + thêm query params `status`, `from`, `to`, `q` |
| User profile editing (fullName/phone/avatar/password) | **user-service** | `user_svc.users` (thêm `avatar_url`) | V3 | `PATCH /api/users/me` + `POST /api/users/me/password` (cùng `user-service-me` group) |
| Product reviews & ratings | **product-service** | `product_svc.reviews` | V4 | `/api/products/{id}/reviews` (đã match generic route) |
| Advanced search filters | **product-service** (extend existing) | — (chỉ index) | V6 (indexes) optional | Tận dụng `/api/products?keyword=…` thêm `brandIn`, `priceMin/Max`, `ratingMin`, `inStock` |
| Address book | **user-service** | `user_svc.addresses` | V4 | `/api/users/me/addresses/**` |
| Homepage redesign | FE only | — | — | Reuse `/api/products` (featured/new-arrivals via sort+limit) |
| Product detail enhancements | FE only + product-service nhỏ | — (gallery dùng `gallery_urls JSONB`; specs `specs JSONB`) | V5 | — |
| Admin dashboard KPI (UI-02) | FE composes + new aggregation endpoints | — | — | Add `GET /api/products/admin/stats`, `GET /api/orders/admin/stats`, `GET /api/users/admin/stats` (FE concurrent fetch) |
| Playwright E2E re-baseline | FE/test only | — | — | — |
| AUTH-06 verify/close | FE middleware (đã đủ) | — | — | — |

**Service placement rationale:**
- **Wishlist ở user-service**: liên kết 1-N với user_id, không cần product domain logic phức tạp; chỉ lưu `(user_id, product_id, added_at)`. Cross-schema FK to `product_svc.products(id)` KHÔNG enforce (theo precedent `order_svc.orders.user_id` ở Phase 5). Read path: wishlist list trả về product_ids → FE gọi `/api/products/{id}` batch, hoặc backend làm fan-out HTTP call sang product-service (anti-pattern cho v1.2 — defer). **Recommendation: trả product_ids + FE batch fetch** (2 round-trips OK, đơn giản, no inter-service coupling).
- **Reviews ở product-service**: review thuộc product domain, average rating cần aggregation cùng schema. Lưu `user_id` + denormalized snapshot (`reviewer_name`) để tránh cross-service join khi render review list.
- **Addresses ở user-service**: profile-bound entity (user có 0..N addresses), không phải order-bound. Order vẫn lưu `shipping_address JSONB` snapshot khi checkout (đã có Phase 8 V2) — **address book chỉ cung cấp UX chọn**, không thay đổi order schema.
- **Admin KPI**: KHÔNG dùng cross-service aggregation backend (vi phạm boundary, cần saga). Mỗi card lazy-fetch endpoint riêng từng service → FE `Promise.all` → render. Theo FE App Router: parallel `fetch()` trong `app/admin/page.tsx` server component hoặc client với `Promise.all` trong `useEffect`.

---

## 3. Schema Migrations — Detailed (Flyway V4+)

### 3.1 user-service (next: V3, V4, V5)

**V3__add_avatar.sql** (profile editing):
```sql
ALTER TABLE user_svc.users ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(500);
```

**V4__create_addresses.sql** (address book):
```sql
CREATE TABLE user_svc.addresses (
  id            VARCHAR(36) PRIMARY KEY,
  user_id       VARCHAR(36) NOT NULL,
  label         VARCHAR(80),                -- "Nhà", "Công ty"
  recipient     VARCHAR(120) NOT NULL,
  phone         VARCHAR(20)  NOT NULL,
  line1         VARCHAR(300) NOT NULL,
  ward          VARCHAR(120),
  district      VARCHAR(120),
  city          VARCHAR(120) NOT NULL,
  is_default    BOOLEAN     NOT NULL DEFAULT FALSE,
  deleted       BOOLEAN     NOT NULL DEFAULT FALSE,
  created_at    TIMESTAMPTZ NOT NULL,
  updated_at    TIMESTAMPTZ NOT NULL,
  CONSTRAINT fk_addresses_user FOREIGN KEY (user_id) REFERENCES user_svc.users(id)
);
CREATE INDEX idx_addresses_user_id ON user_svc.addresses(user_id) WHERE deleted = FALSE;
CREATE UNIQUE INDEX uq_addresses_user_default
  ON user_svc.addresses(user_id) WHERE is_default = TRUE AND deleted = FALSE;
```

**V5__create_wishlists.sql** (wishlist):
```sql
CREATE TABLE user_svc.wishlists (
  user_id     VARCHAR(36) NOT NULL,
  product_id  VARCHAR(36) NOT NULL,
  added_at    TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (user_id, product_id)
);
CREATE INDEX idx_wishlists_user_id ON user_svc.wishlists(user_id);
```
*Note: composite PK đảm bảo idempotent add (no duplicates). `product_id` cross-schema KHÔNG enforce FK — consistency check ở app layer (theo precedent).*

### 3.2 product-service (next: V4, V5, V6)

**V4__create_reviews.sql**:
```sql
CREATE TABLE product_svc.reviews (
  id             VARCHAR(36) PRIMARY KEY,
  product_id     VARCHAR(36) NOT NULL,
  user_id        VARCHAR(36) NOT NULL,
  reviewer_name  VARCHAR(120) NOT NULL,    -- denormalized snapshot
  rating         SMALLINT     NOT NULL CHECK (rating BETWEEN 1 AND 5),
  comment        TEXT,
  status         VARCHAR(20)  NOT NULL DEFAULT 'PUBLISHED', -- PUBLISHED|HIDDEN
  deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at     TIMESTAMPTZ  NOT NULL,
  updated_at     TIMESTAMPTZ  NOT NULL,
  CONSTRAINT fk_reviews_product FOREIGN KEY (product_id) REFERENCES product_svc.products(id)
);
CREATE INDEX idx_reviews_product ON product_svc.reviews(product_id) WHERE deleted = FALSE;
CREATE UNIQUE INDEX uq_reviews_user_product
  ON product_svc.reviews(user_id, product_id) WHERE deleted = FALSE;
```
*Note: 1 user / 1 product = 1 review. Aggregation `AVG(rating), COUNT(*)` có thể compute on-demand (10 reviews/product → fine), defer materialized view.*

**V5__add_product_specs_and_rating.sql** (cho product detail enhancements + search):
```sql
ALTER TABLE product_svc.products ADD COLUMN IF NOT EXISTS specs JSONB;
ALTER TABLE product_svc.products ADD COLUMN IF NOT EXISTS gallery_urls JSONB; -- ["url1","url2"]
ALTER TABLE product_svc.products ADD COLUMN IF NOT EXISTS avg_rating NUMERIC(3,2) NOT NULL DEFAULT 0;
ALTER TABLE product_svc.products ADD COLUMN IF NOT EXISTS review_count INT NOT NULL DEFAULT 0;
```

**V6__add_search_indexes.sql** (advanced filter perf, optional):
```sql
CREATE INDEX IF NOT EXISTS idx_products_brand ON product_svc.products(brand) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_products_price ON product_svc.products(price) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_products_avg_rating ON product_svc.products(avg_rating) WHERE deleted = FALSE;
```

### 3.3 order-service — KHÔNG thêm tables

Order history filtering chỉ thêm query params; không cần schema change. Nếu muốn fast date filter:
```sql
-- V3 (optional)
CREATE INDEX IF NOT EXISTS idx_orders_user_status_created
  ON order_svc.orders(user_id, status, created_at DESC) WHERE deleted = FALSE;
```

### 3.4 Flyway naming caveat (đã có precedent)

v1.1 đã hit collision V2 ở order-service → rename V100__seed. **Mỗi migration mới phải confirm số version chưa dùng** trước khi commit. Convention v1.2: dev seed dùng V1xx, V2xx; schema migration tiếp tục V3, V4, V5...

---

## 4. Gateway Routing — Additions

### 4.1 Routes mới cần thêm vào `application.yml`

```yaml
# user-service: /api/users/me, /api/users/me/** (authenticated current-user)
- id: user-service-me-base
  uri: http://user-service:8080
  predicates: [Path=/api/users/me]
  filters:    [RewritePath=/api/users/me, /users/me]
- id: user-service-me
  uri: http://user-service:8080
  predicates: [Path=/api/users/me/**]
  filters:    [RewritePath=/api/users/me/(?<seg>.*), /users/me/${seg}]
```

**Order matters:** `me` routes phải đặt **TRƯỚC** `/api/users/**` generic (giống precedent `auth` & `admin` đã làm). Nếu không, `/api/users/me/wishlist` sẽ match `/api/users/{id}` với id=`me` → controller 404.

**Suggested layout:**
```
1. user-service-auth-base / -auth          (đã có)
2. user-service-admin-base / -admin        (đã có)
3. user-service-me-base / -me              (NEW — phải trước -base)
4. user-service-base / -                   (đã có)
```

Endpoints sẽ có sẵn:
- `PATCH /api/users/me` → update fullName/phone/avatar
- `POST  /api/users/me/password` → change password (require old password)
- `GET   /api/users/me/addresses`, `POST/PATCH/DELETE /api/users/me/addresses/{id}`
- `GET   /api/users/me/wishlist`, `POST/DELETE /api/users/me/wishlist/{productId}`
- `GET   /api/users/me/wishlist/contains/{productId}` (optional UX badge)

### 4.2 Reviews — không cần route mới

`/api/products/{id}/reviews` đã match generic `product-service` route (`/api/products/(?<seg>.*) → /products/${seg}`). Backend chỉ cần thêm `ReviewController` ở product-service `/products/{id}/reviews`.

### 4.3 Admin KPI stats — sub-paths có sẵn

`/api/{products,orders,users}/admin/stats` đã match existing admin routes. Chỉ cần thêm `@GetMapping("/stats")` trong từng `Admin{X}Controller`.

### 4.4 Search filters — không cần route mới

Mở rộng `/api/products` query param trong `ProductController.list()`. JPA Specification hoặc `@Query` với optional params.

---

## 5. Frontend — Module & Route Additions

### 5.1 OpenAPI codegen

Sau khi backend ship endpoints mới + Springdoc tự update, chạy `npm run gen:api`:
- `users.generated.ts` → có thêm paths cho `/users/me`, `/users/me/addresses`, `/users/me/wishlist`
- `products.generated.ts` → có thêm `/products/{id}/reviews`, query params filter mới

**Không tạo file `.generated` mới** — tất cả endpoints mới fall vào 6 service docs hiện có. Module nào tạo endpoint thì codegen module đó mở rộng.

### 5.2 New service modules (`src/services/`)

| File mới | Lý do tách |
|----------|-----------|
| `wishlist.ts` | Domain riêng — list/add/remove/contains. Tách khỏi `users.ts` để tránh module phình. |
| `reviews.ts` | Domain riêng — list/post/delete by productId; aggregation helper. |
| `addresses.ts` | Domain riêng — CRUD address book. |
| (extend `users.ts`) | Thêm `getMe()`, `updateMe()`, `changePassword()`. |
| (extend `products.ts`) | Thêm `ListProductsParams.brandIn?, priceMin?, priceMax?, ratingMin?, inStock?`. |
| (extend `orders.ts`) | Thêm filter params cho `listMyOrders(status, from, to, q)`. |

### 5.3 New page routes (App Router)

```
src/app/
├── account/
│   ├── wishlist/page.tsx               # Wishlist list + move-to-cart
│   ├── addresses/page.tsx              # Address book CRUD UI
│   └── settings/page.tsx               # Profile editing (fullName/phone/avatar/password)
├── profile/
│   ├── page.tsx                        # (có sẵn) — link to settings
│   └── orders/
│       ├── page.tsx                    # (có sẵn) — extend với filters bar
│       └── [id]/page.tsx               # (có sẵn — Phase 8 PERSIST-03)
├── products/
│   └── [slug]/page.tsx                 # extend: gallery + specs + reviews section + breadcrumb
├── search/
│   └── page.tsx                        # extend: filter sidebar (brand/price/rating/stock)
├── admin/
│   └── page.tsx                        # rewire KPI cards với real fetch (UI-02 closure)
└── page.tsx                            # homepage redesign (hero + featured + categories + new arrivals)
```

**Note on `/profile/settings` vs `/account/settings`:** PROJECT.md mention `/profile/settings`. Hiện tại `/profile` đã exist, `/account` mới. Đề xuất: **dùng `/account/settings`, `/account/wishlist`, `/account/addresses`** (gom account-management dưới `/account`), giữ `/profile/orders` cho order history (đã exist). Cả 2 đều được middleware bảo vệ. Hoặc gom hết vào `/profile/*` — chọn 1 trong Phase 9 design.

### 5.4 Components mới (suggest under `src/components/`)

```
components/
├── wishlist/
│   ├── WishlistButton.tsx          # heart icon trên product card + detail
│   └── WishlistRow.tsx             # row trong /account/wishlist
├── reviews/
│   ├── ReviewList.tsx              # list reviews on product detail
│   ├── ReviewForm.tsx              # post review (auth required, hide if !logged-in)
│   └── RatingStars.tsx             # 1..5 sao + half-star display
├── address/
│   ├── AddressCard.tsx
│   ├── AddressForm.tsx             # create/edit
│   └── AddressPicker.tsx           # checkout selector
├── search/
│   └── FilterSidebar.tsx           # brand/price/rating/stock chips + range
├── product/
│   ├── ImageGallery.tsx            # thumbnails + main + zoom
│   ├── SpecsTable.tsx              # render JSONB specs
│   ├── StockBadge.tsx              # IN_STOCK / LOW_STOCK / OUT_OF_STOCK
│   └── Breadcrumb.tsx
└── home/
    ├── HeroBanner.tsx
    ├── FeaturedGrid.tsx
    └── CategoryTiles.tsx
```

---

## 6. Auth Integration

### 6.1 Middleware (AUTH-06)

`middleware.ts:41` matcher hiện tại: `['/checkout/:path*', '/profile/:path*', '/admin/:path*', '/account/:path*']` — **đã đủ cho v1.2**. Verify trong Phase 9 đầu thay vì plan thêm work.

### 6.2 Profile editing với password verify

**Sensitive operation** (đổi password) cần re-verify old password. Endpoint dedicated:
```
POST /api/users/me/password
Body: { oldPassword: string, newPassword: string }
```
Backend: BCrypt.matches(old, hashFromDB) trước khi update. Nếu sai → `ApiErrorResponse{code: "AUTH_INVALID_PASSWORD"}`.

**Email change**: KHÔNG nằm trong v1.2 scope (PROJECT.md chỉ list fullName/phone/avatar/password). Defer.

### 6.3 Wishlist/address/review require auth

- FE: dùng existing `AuthProvider` hydration. Component check `useAuth().user` trước khi render WishlistButton enabled / ReviewForm visible.
- BE: extract user_id từ JWT (existing pattern). Wishlist/address operations nếu thiếu JWT → 401 (gateway-level qua existing JWT verification chain).

### 6.4 Anonymous interactions

- **Wishlist anon**: không hỗ trợ trong v1.2 (đơn giản). Click heart khi chưa login → toast "Đăng nhập để lưu". Không sync localStorage → server.
- **Review anon**: không cho phép. ReviewForm hidden cho guest.

---

## 7. Image Upload Story

**Decision: defer real upload v1.3, dùng URL input cho avatar v1.2.**

| Asset | v1.2 approach | v1.3+ |
|-------|--------------|------|
| User avatar | URL string input (giống admin product `thumbnail_url`) | Multipart POST + filesystem volume |
| Product images (admin) | URL string input (đã có pattern) | Multipart |
| Review images | KHÔNG SUPPORT trong v1.2 | Multipart trong v1.3 nếu visible value cao |
| Product gallery | `gallery_urls JSONB` array of URL strings (admin nhập tay) | Multipart |

**Rationale:**
- File upload thật cần: multipart parser, FS volume (`./uploads:/app/uploads`), ResourceHandler endpoint hoặc gateway static route, server-side resize, validation (size/MIME), security (path traversal). Scope creep.
- URL input đã có precedent `thumbnail_url` ở admin product (Phase 7 V2). Pattern tương tự cho avatar = 0 new infra.
- v1.3 nếu visible-priority đẩy lên: implement filesystem store với `/uploads/{kind}/{id}.{ext}`, mount Docker volume per service.

**Endpoint v1.2:** `PATCH /api/users/me` body `{ avatarUrl: string }`. Validate URL syntax + length. UI: text input + preview img.

---

## 8. Search Filter Integration

### 8.1 Existing `/search` endpoint

Phase 7 đã ship `/api/products?keyword=…` (UI-01) cho keyword search. Page `/search?q=…` consume.

### 8.2 Extension cho v1.2

Thêm query params (cumulative, all optional):
- `brandIn=Apple,Dell,HP` (CSV)
- `priceMin=500`, `priceMax=2000` (numeric, theo currency convention hiện tại)
- `ratingMin=4` (1..5; cần `avg_rating` denormalized — V5 add)
- `inStock=true` (filter `stock > 0`)
- `categoryId=...` (đã có)

### 8.3 Backend implementation

`ProductRepository` dùng JPA Specification:
```java
public static Specification<ProductEntity> matchFilters(SearchCriteria c) {
  return (root, q, cb) -> {
    List<Predicate> p = new ArrayList<>();
    p.add(cb.isFalse(root.get("deleted")));
    if (c.keyword() != null) p.add(cb.like(cb.lower(root.get("name")), "%"+c.keyword().toLowerCase()+"%"));
    if (c.brandIn() != null) p.add(root.get("brand").in(c.brandIn()));
    if (c.priceMin() != null) p.add(cb.ge(root.get("price"), c.priceMin()));
    if (c.priceMax() != null) p.add(cb.le(root.get("price"), c.priceMax()));
    if (Boolean.TRUE.equals(c.inStock())) p.add(cb.gt(root.get("stock"), 0));
    if (c.ratingMin() != null) p.add(cb.ge(root.get("avgRating"), c.ratingMin()));
    return cb.and(p.toArray(new Predicate[0]));
  };
}
```

### 8.4 Facet aggregation (defer v1.3)

True faceted UI ("Apple (12), Dell (8)" counts) cần aggregation queries. **v1.2 keep simple**: hardcoded brand list trong sidebar (lấy từ `GET /api/products/brands` distinct query, hoặc hardcode top 6 brands). Không show counts.

### 8.5 avg_rating denormalization

**Trade-off:**
- **On-write** (recommend): mỗi POST review → ReviewService updates `products.avg_rating` & `review_count` cùng transaction. Đơn giản, eventual consistency 1 transaction.
- **On-read JOIN**: query phức tạp, slow ở scale.
- **Materialized view**: overkill cho v1.2.

→ Add columns trong **V5** (`avg_rating NUMERIC(3,2)`, `review_count INT NOT NULL DEFAULT 0`).

---

## 9. Admin Dashboard KPI (UI-02)

### 9.1 Architecture

```
FE: app/admin/page.tsx (Server Component or "use client")
 ├── Promise.all([
 │     fetch /api/products/admin/stats,
 │     fetch /api/orders/admin/stats,
 │     fetch /api/users/admin/stats
 │   ])
 └── render 4 KPI cards
```

**Mỗi service tự owner stats của mình** — không cross-service backend aggregation. Tránh saga complexity.

### 9.2 Stats endpoints

```
GET /api/products/admin/stats
→ { totalProducts: 42, lowStockCount: 3, outOfStockCount: 1 }

GET /api/orders/admin/stats
→ { totalOrders: 187, todayOrders: 5, revenue30d: 12345.67, pendingCount: 12 }

GET /api/users/admin/stats
→ { totalUsers: 56, newUsersToday: 2, adminCount: 1 }
```

### 9.3 Lazy load vs upfront

**Recommend: upfront `Promise.all`** (3 calls in parallel, ~100-300ms). Lazy load (intersection observer per card) overkill cho 3-4 cards. Dùng React Suspense boundary nếu Server Component.

---

## 10. Build Order — Suggested Phase Sequencing (Phase 9+)

Build order respect entity dependencies + risk-front-loading.

```
Phase 9 — Foundation & Closure (low risk, parallelizable)
├── 9.1 AUTH-06 verify (read middleware.ts → confirm closed → 5-min smoke)
├── 9.2 UI-02 admin KPI rewire — 3 stats endpoints + FE concurrent fetch
└── 9.3 Playwright E2E re-baseline cho v1.1 features

Phase 10 — Account schema & profile editing (foundation for wishlist/address)
├── 10.1 user-service V3 (avatar_url) + V4 (addresses) + V5 (wishlists)
├── 10.2 Gateway: add user-service-me routes (BEFORE user-service-base)
├── 10.3 Backend: UserMeController (GET/PATCH /users/me, POST /users/me/password)
├── 10.4 FE: services/users.ts extend (getMe/updateMe/changePassword) + /account/settings page
└── 10.5 Codegen + smoke test profile editing

Phase 11 — Address book (depends on Phase 10 schema)
├── 11.1 Backend: AddressController (CRUD /users/me/addresses)
├── 11.2 FE: services/addresses.ts + /account/addresses CRUD page
├── 11.3 FE: AddressPicker integration trong /checkout (replace free-text với chọn-từ-list + new-on-the-fly)
└── 11.4 E2E: address book happy path + checkout với saved address

Phase 12 — Wishlist (depends on Phase 10 schema)
├── 12.1 Backend: WishlistController (list/add/remove/contains)
├── 12.2 FE: services/wishlist.ts + WishlistButton component (product card + detail)
├── 12.3 FE: /account/wishlist page với move-to-cart
└── 12.4 E2E: add-to-wishlist → list → move to cart → remove

Phase 13 — Reviews schema + UI (independent của account features)
├── 13.1 product-service V4 (reviews) + V5 (specs/gallery + avg_rating/review_count)
├── 13.2 Backend: ReviewController + ProductService.recomputeRating() on POST review
├── 13.3 FE: services/reviews.ts + ReviewList/ReviewForm/RatingStars
├── 13.4 FE: integrate vào /products/[slug] (reviews section)
└── 13.5 E2E: post review → average updates → display

Phase 14 — Search filters (depends on Phase 13 if ratingMin needed)
├── 14.1 product-service V6 (indexes optional)
├── 14.2 Backend: ProductController.list mở rộng SearchCriteria + Specification
├── 14.3 FE: services/products.ts ListProductsParams extend + FilterSidebar component
├── 14.4 FE: integrate /search và /products (URL-state filters)
└── 14.5 E2E: filter combos

Phase 15 — Order history filtering (no schema; small)
├── 15.1 Backend: OrderController.listMine query params (status, from, to, q)
├── 15.2 FE: extend /profile/orders với filter bar + URL state
└── 15.3 E2E

Phase 16 — Public polish (FE-heavy, no schema risk)
├── 16.1 Homepage redesign (hero/featured/categories/new arrivals)
├── 16.2 Product detail enhancements (gallery/specs/stock badge/breadcrumb) — depends on Phase 13 V5 specs
└── 16.3 Final E2E re-baseline

Phase 17 — Milestone audit & v1.2 ship
```

### 10.1 Dependency summary

```
Phase 10 (account schema) ──┬→ Phase 11 (address book) ──→ checkout integration
                            └→ Phase 12 (wishlist)

Phase 13 (reviews schema) ──┬→ Phase 14 (search filters with ratingMin)
                            └→ Phase 16.2 (product detail with reviews)

Phase 15 independent
Phase 9 closure ahead of all
```

### 10.2 Why this order

- **Closure first (Phase 9)**: low risk, fast wins, validate audit assumptions before sinking work.
- **Schema-heavy phases early (10, 13)**: Flyway migrations need careful version coordination. Phase 10 + 13 produce all v1.2 schema. After that, only code.
- **Address book before wishlist** (arbitrary, both depend only on Phase 10) — chọn theo end-user impact: address ảnh hưởng checkout (existing flow), wishlist là greenfield.
- **Reviews before search filters**: ratingMin filter needs avg_rating column from Phase 13.
- **Public polish cuối**: depends on product schema (specs/gallery), low risk, đẹp để ship cuối.

---

## 11. Cross-cutting Concerns

### 11.1 OpenAPI codegen pipeline

Mỗi phase ship backend endpoints → **chạy `npm run gen:api`** trước khi viết FE consumer. Update `.generated.ts` files. Pattern hiện tại: typed accessors vẫn là `paths[…]` style; nhiều response bodies emit `never` do `ApiResponseAdvice` wrap (xem comment `services/products.ts:14-18`). Defaults vẫn dùng hand-narrowed types từ `@/types`.

### 11.2 Error envelope

Tất cả endpoints mới phải emit `ApiErrorResponse` với traceId. Reuse existing `@RestControllerAdvice`. New error codes nên thêm:
- `WISHLIST_ALREADY_EXISTS`, `WISHLIST_NOT_FOUND`
- `ADDRESS_NOT_FOUND`, `ADDRESS_LIMIT_EXCEEDED` (cap 10/user?)
- `REVIEW_DUPLICATE` (1-user-1-product), `REVIEW_RATING_OUT_OF_RANGE`
- `AUTH_INVALID_PASSWORD` (change password old wrong)

### 11.3 OpenAPI doc annotation

Mỗi controller mới cần `@Operation`, `@ApiResponse(content=@Content(schema=…))` để codegen ra typed responses không bị `never`. Theo Pitfall 7 của products.ts.

### 11.4 Smoke tests per phase

Theo precedent v1.1: mỗi phase có smoke (curl + log assert) trước E2E. Playwright re-baseline cuối phase / cuối milestone.

---

## 12. Anti-patterns to Avoid

| Anti-pattern | Why bad | Instead |
|---|---|---|
| Cross-service JOIN (review.user → user_svc.users) | Vi phạm boundary; broken khi service down | Denormalize `reviewer_name` snapshot tại lúc create |
| Cross-service backend aggregation cho admin KPI | Saga complexity | FE concurrent fetch riêng từng service |
| Wishlist trong product-service | Gắn user_id vào product domain → muddied boundary | user-service owns wishlist |
| Order shipping_address từ FK → user_svc.addresses | Address có thể edit/delete sau order → history lost | Snapshot JSONB tại checkout (already done Phase 8) |
| Facet counts ở v1.2 | Aggregation queries phức tạp | Hardcoded brand chips, defer counts v1.3 |
| File upload thật trong v1.2 | Multipart + volume + resize = scope creep | URL input cho avatar (precedent thumbnail_url) |
| Email change trong profile editing | OTP/verify flow phức tạp | Out of scope — chỉ fullName/phone/avatar/password |
| Edge runtime JWT signature verify | Edge không support crypto cần thiết | Cookie presence check (existing pattern) |
| `Specs JSONB` validation lỏng | Schema drift, FE crash | Define TS interface `ProductSpecs`, validate Zod ở FE |
| Backend fan-out call wishlist→product-service | Inter-service coupling, slow | FE batch fetch product details by ids |
| `me` route đặt sau `/api/users/**` generic | Predicate match sai → controller 404 | `me` routes phải định nghĩa TRƯỚC `-base` (giống `auth`/`admin` precedent) |

---

## 13. Confidence & Open Questions

| Area | Confidence | Source |
|------|-----------|--------|
| Gateway routing additions | HIGH | application.yml inspected; precedent admin/auth ordering verified |
| Schema migrations | HIGH | All existing V1-V3 migrations read; Flyway collision pattern known |
| FE module placement | HIGH | services/ directory inspected; codegen script read |
| Middleware state | MEDIUM | matcher đã extended trong file nhưng PROJECT.md/MILESTONES.md vẫn ghi AUTH-06 partial — **Phase 9.1 phải verify** |
| Image upload defer decision | MEDIUM | Đề xuất URL-input cho avatar; final call thuộc roadmap phase |
| `/account/*` vs `/profile/*` route layout | LOW | Cả 2 valid; cần roadmap quyết |

### Open questions cho roadmap

1. **Avatar upload scope**: URL input only (recommended) hay multipart upload? Ảnh hưởng Phase 10 size.
2. **Wishlist anonymous**: localStorage shadow + sync khi login? Recommend SKIP cho v1.2.
3. **Reviews moderation**: ai có quyền hide/delete review của user khác? Admin-only hay user own? Recommend: user own + admin override.
4. **Product `specs` schema**: free-form JSONB hay constrained shape? Recommend constrained TS interface (cpu/ram/storage/display/...) với Zod validate.
5. **`/account` vs `/profile` URL convention**: gom hết về 1 prefix, hay split (`/profile` = read-only, `/account` = manage)?
6. **Address limit per user**: 10 / 5 / unlimited? Recommend 10 with `ADDRESS_LIMIT_EXCEEDED` error.
7. **Order filter date range**: client-side filter hay server-side? Server-side recommended (existing pagination), defer date pickers UX choice.
8. **Reviews — eligibility constraint**: chỉ user đã mua sản phẩm (verified buyer) mới review, hay bất kỳ logged-in user? Recommend ANY logged-in cho v1.2 (đơn giản); verified-buyer defer.

---

*Prepared: 2026-04-26 — Architecture integration research cho v1.2 UI/UX Completion. Foundation từ codebase inspection, không re-research v1.0/v1.1 patterns.*
