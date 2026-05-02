# Architecture Patterns — v1.3 Integration Research

**Domain:** E-commerce microservices (Spring Boot + Next.js)
**Researched:** 2026-05-02
**Milestone:** v1.3 — Catalog Realism & Commerce Intelligence

---

## Existing Architecture Snapshot (v1.2 baseline)

### Services & Gateway Routing

```
Browser → Next.js :3000
  └─► API Gateway :8080
        ├── /api/users/**          → user-service:8080
        ├── /api/users/me/**       → user-service:8080   (profile + addresses)
        ├── /api/users/admin/**    → user-service:8080   (admin user CRUD)
        ├── /api/products/**       → product-service:8080
        ├── /api/products/admin/** → product-service:8080
        ├── /api/orders/**         → order-service:8080
        ├── /api/orders/admin/**   → order-service:8080
        ├── /api/payments/**       → payment-service:8080
        ├── /api/inventory/**      → inventory-service:8080
        └── /api/notifications/**  → notification-service:8080
```

### Database State (all services share single Postgres 16 instance, separate schemas)

| Service | Schema | Flyway Version | Key Tables |
|---------|--------|---------------|------------|
| user-service | user_svc | V1, V2, V101 | users, addresses |
| product-service | product_svc | V1-V6 | categories, products, reviews |
| order-service | order_svc | V1, V2, V100(seed) | orders, order_items |
| payment-service | payment_svc | (separate) | payments |
| inventory-service | inventory_svc | (separate) | inventory |
| notification-service | notification_svc | (separate) | notifications |

### Cross-Service Calls (confirmed from codebase)

- `order-svc` → `product-svc`: GET /api/products/{id} để validate stock (D-04) + PATCH /api/products/admin/{id} để deduct stock (D-05)
- `product-svc` → `order-svc`: GET /api/orders/internal/check-buyer?userId=&productId= để verify-buyer eligibility (REV-03)
- Tất cả calls đi qua api-gateway:8080 (không direct service-to-service DNS)

### Cart State (CONFIRMED BUG)

Cart hiện tại là **100% client-only** qua `localStorage['cart']` (services/cart.ts). Backend có `InMemoryCartRepository` và `CartController` nhưng FE hoàn toàn không gọi các endpoint này — cart API backend là dead code từ v1.0. FE chỉ POST cart items trực tiếp lên `/api/orders` khi checkout.

### Order Detail Items Bug (ROOT CAUSE FOUND)

**Admin page** (`/admin/orders/[id]/page.tsx`): `AdminOrder` interface không có `items` field. Component render "Chi tiết sản phẩm sẽ khả dụng sau khi Phase 8 hoàn thiện" — placeholder text đã hardcode.

**User page** (`/profile/orders/[id]/page.tsx`): `Order` type có `items: OrderItem[]`. Backend `OrderMapper.toDto()` map đúng items từ `OrderItemEntity`. Backend `findByIdWithItems()` dùng LEFT JOIN FETCH. **Bug thực sự**: FE `getOrderById()` gọi `/api/orders/{id}` nhưng `OrderController` gọi `orderCrudService.getOrder(id, false)` — phương thức này dùng `findByIdWithItems()` nên items có mặt. Cần kiểm tra xem FE type `Order.items` có được serialize đúng từ response hay không. Khả năng cao là DTO wrapper `ApiResponse<OrderDto>` trả `data.items` nhưng FE parse `data` mà không unwrap — hoặc field name mismatch.

---

## v1.3 Integration Decisions

### 1. Coupon Table Placement: order-svc (RECOMMENDED)

**Quyết định:** Đặt coupon table trong `order-svc`, schema `order_svc`.

**Rationale:**
- Coupon được validate và apply tại thời điểm tạo order — coupon lifecycle gắn liền với order, không phải product
- `createOrderFromCommand()` đã là điểm tập trung của cart→order logic — coupon validation fit tự nhiên ở đây
- Tránh tạo cross-service call mới: nếu đặt coupon ở product-svc, order-svc phải gọi thêm product-svc để validate → thêm latency + failure point
- Coupon eligibility rule (min order amount, max usage) chỉ có thể check sau khi biết cart total — order-svc là nơi duy nhất có đủ context này

**Khả năng khác bị loại:**
- `product-svc`: coupon gắn với product/category eligibility nhưng checkout logic ở order-svc — cần cross-service call tại checkout
- `coupon-svc` riêng: overkill cho MVP, thêm service mới + gateway route + docker container; v1.3 scope không justify

**Schema đề xuất (order-svc V3 migration):**

```sql
-- V3__create_coupons.sql (order_svc schema)
CREATE TABLE order_svc.coupons (
  id            VARCHAR(36)   PRIMARY KEY,
  code          VARCHAR(50)   NOT NULL,
  type          VARCHAR(10)   NOT NULL,    -- 'PERCENT' | 'FIXED'
  value         NUMERIC(12,2) NOT NULL,    -- percent 0-100 hoặc fixed VND
  min_order     NUMERIC(12,2) NOT NULL DEFAULT 0,
  max_usage     INT           NOT NULL DEFAULT -1,  -- -1 = unlimited
  usage_count   INT           NOT NULL DEFAULT 0,
  expires_at    TIMESTAMP WITH TIME ZONE,
  active        BOOLEAN       NOT NULL DEFAULT TRUE,
  deleted       BOOLEAN       NOT NULL DEFAULT FALSE,
  created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT uq_coupons_code UNIQUE (code)
);

-- Track per-user usage để enforce max_usage/user
CREATE TABLE order_svc.coupon_usages (
  id         VARCHAR(36) PRIMARY KEY,
  coupon_id  VARCHAR(36) NOT NULL REFERENCES order_svc.coupons(id),
  user_id    VARCHAR(36) NOT NULL,
  order_id   VARCHAR(36) NOT NULL REFERENCES order_svc.orders(id),
  used_at    TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT uq_coupon_user_order UNIQUE (coupon_id, order_id)
);

CREATE INDEX idx_coupon_usages_coupon_id ON order_svc.coupon_usages(coupon_id);
CREATE INDEX idx_coupon_usages_user_id ON order_svc.coupon_usages(user_id);

-- Thêm coupon_code + discount_amount vào orders
ALTER TABLE order_svc.orders ADD COLUMN IF NOT EXISTS coupon_code VARCHAR(50);
ALTER TABLE order_svc.orders ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(12,2) DEFAULT 0;
```

**Gateway routes mới cần thêm:**
```yaml
# /api/orders/admin/coupons/** → order-service /admin/coupons/**
- id: order-service-admin-coupons
  uri: http://order-service:8080
  predicates:
    - Path=/api/orders/admin/coupons/**
  filters:
    - RewritePath=/api/orders/admin/coupons/(?<seg>.*), /admin/coupons/${seg}

# /api/orders/coupons/validate → order-service /coupons/validate (public validate)
- id: order-service-coupon-validate
  uri: http://order-service:8080
  predicates:
    - Path=/api/orders/coupons/**
  filters:
    - RewritePath=/api/orders/coupons/(?<seg>.*), /coupons/${seg}
```

**Data flow (checkout với coupon):**
```
FE cart page → POST /api/orders/coupons/validate {code, cartTotal}
  ← {valid: true, discountAmount: 50000, finalTotal: 450000}

FE checkout → POST /api/orders {items, shippingAddress, paymentMethod, couponCode}
  order-svc: validate coupon lại (idempotent) → apply discount → persist coupon_usage
  ← OrderDto {total: 450000, discountAmount: 50000, couponCode: "SALE10"}
```

### 2. Chat Persistence Placement: Next.js API Routes (RECOMMENDED)

**Quyết định:** Chat sessions persist qua **Next.js API Routes** (`/api/chat/*`) với database riêng hoặc shared Postgres schema `chat_svc` — KHÔNG tạo Spring Boot microservice mới.

**Rationale:**
- Claude API (Anthropic SDK) có Node.js SDK tốt hơn Java SDK — streaming response với `ReadableStream` native trong Next.js API Routes
- Tránh tạo thêm Spring Boot microservice (thêm Dockerfile, docker-compose entry, gateway route, Flyway) chỉ cho 1 tính năng
- Next.js API Routes có thể đọc JWT từ cookie/header để verify user — cùng pattern với FE auth hiện tại
- Streaming (SSE/text streaming) dễ implement hơn trong Next.js so với Spring WebFlux (project hiện dùng Spring MVC blocking)
- Chat history persist vào Postgres qua pg/postgres.js driver trong Next.js API route — không cần JPA overhead

**Chat DB schema (chat_svc schema, init qua separate SQL hoặc Next.js migration):**

```sql
-- Nếu dùng shared Postgres instance, tạo schema trong db/init/
CREATE SCHEMA IF NOT EXISTS chat_svc;

CREATE TABLE chat_svc.chat_sessions (
  id         VARCHAR(36) PRIMARY KEY,
  user_id    VARCHAR(36) NOT NULL,    -- nullable cho guest? MVP: require auth
  context    VARCHAR(30) NOT NULL DEFAULT 'customer',  -- 'customer' | 'admin'
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE chat_svc.chat_messages (
  id         VARCHAR(36) PRIMARY KEY,
  session_id VARCHAR(36) NOT NULL REFERENCES chat_svc.chat_sessions(id),
  role       VARCHAR(10) NOT NULL,   -- 'user' | 'assistant'
  content    TEXT        NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_chat_messages_session ON chat_svc.chat_messages(session_id);
CREATE INDEX idx_chat_sessions_user ON chat_svc.chat_sessions(user_id);
```

**API surface (Next.js API Routes):**

```
POST /api/chat/sessions          → tạo session mới
GET  /api/chat/sessions          → list sessions của user hiện tại
POST /api/chat/sessions/[id]/messages  → gửi message + stream response
GET  /api/chat/sessions/[id]/messages  → load history
```

**Streaming topology:**
```
FE ChatWidget
  → POST /api/chat/sessions/{id}/messages (Next.js API Route)
    → persist user message vào chat_svc.chat_messages
    → Anthropic SDK streamMessage() với system prompt + history context
    → pipe SSE stream → FE (ReadableStream)
    → persist assistant response sau khi stream complete
```

**Lý do KHÔNG dùng user-svc:**
- user-svc là Spring Boot JPA — streaming response phức tạp hơn (cần Spring WebFlux hoặc SseEmitter hack)
- Chat là cross-cutting concern (customer + admin) — không phải user-domain
- Tách biệt DB concern: chat messages có volume cao, index pattern khác

### 3. Cart → DB Migration: order-svc với Flyway V4 (RECOMMENDED)

**Quyết định:** Migrate cart từ localStorage vào `order-svc` DB, thêm `carts` và `cart_items` tables. Thay thế `InMemoryCartRepository` bằng JPA repository.

**Rationale:**
- `InMemoryCartRepository` đã tồn tại trong order-svc — pattern đúng, chỉ cần swap sang JPA
- `CartEntity` record đã có đủ fields (userId, productId, quantity) — extend thêm `productName`, `unitPrice`, `thumbnailUrl` cho UX
- Backend `CartController` đã có full CRUD — chỉ cần wire JPA + add gateway route
- FE `services/cart.ts` hiện tại hoàn toàn client-side — cần refactor để call backend + giữ optimistic update

**Flyway migration (order-svc V4):**

```sql
-- V4__create_cart_tables.sql (order_svc schema)
CREATE TABLE order_svc.carts (
  id         VARCHAR(36) PRIMARY KEY,
  user_id    VARCHAR(36) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT uq_carts_user UNIQUE (user_id)   -- 1 cart per user
);

CREATE TABLE order_svc.cart_items (
  id            VARCHAR(36)   PRIMARY KEY,
  cart_id       VARCHAR(36)   NOT NULL REFERENCES order_svc.carts(id) ON DELETE CASCADE,
  product_id    VARCHAR(36)   NOT NULL,
  product_name  VARCHAR(300)  NOT NULL,   -- snapshot tại thời điểm add
  thumbnail_url VARCHAR(500),
  unit_price    NUMERIC(12,2) NOT NULL,   -- snapshot
  quantity      INT           NOT NULL,
  stock_snap    INT           NOT NULL DEFAULT 0,  -- stock snapshot cho FE cap
  created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT uq_cart_items_cart_product UNIQUE (cart_id, product_id)
);

CREATE INDEX idx_cart_items_cart_id ON order_svc.cart_items(cart_id);
```

**Migration path:**
- CartController backend đã có — refactor `InMemoryCartRepository` → `CartRepository extends JpaRepository`
- Gateway route: `/api/orders/cart/**` → order-service `/cart/**` (đã tồn tại nhưng FE không dùng)
- FE: thay `localStorage.getItem('cart')` bằng API calls, giữ `cart:change` CustomEvent pattern cho Header badge

**Note:** Cart items snapshot `unit_price` + `product_name` tại thời điểm add — KHÔNG refetch từ product-svc mỗi lần load cart (tránh stale price confusion khi price thay đổi giữa add và checkout).

### 4. Admin Charts Data Source: Direct Per-Service Queries (RECOMMENDED)

**Quyết định:** 4 chart endpoints được implement trực tiếp trong từng service — KHÔNG tạo admin-svc hay aggregator gateway endpoint.

**Rationale:**
- Pattern đã có: `OrderStatsService` + `AdminStatsController` trong order-svc trả `{totalOrders, pendingOrders}` — chứng minh pattern hoạt động
- Admin dashboard hiện tại dùng `Promise.allSettled([loadProduct(), loadOrder(), loadUser()])` — đã resilient với per-service failure
- 4 charts cần thêm: revenue/time (order-svc), top products (order-svc join product-svc? hoặc order-svc chỉ), order status pie (order-svc), signups (user-svc) + low-stock (product-svc)
- Snapshot daily vs realtime: dùng realtime queries đơn giản (COUNT + GROUP BY) — đủ cho demo, không cần caching layer

**Chart endpoints cần thêm:**

| Chart | Service | Endpoint | Query |
|-------|---------|----------|-------|
| Revenue/time | order-svc | GET /admin/orders/stats/revenue?period=7d | SUM(total) GROUP BY DATE |
| Top products | order-svc | GET /admin/orders/stats/top-products?limit=5 | COUNT order_items GROUP BY product_id |
| Order status pie | order-svc | GET /admin/orders/stats/by-status | COUNT GROUP BY status |
| Signups trend | user-svc | GET /admin/users/stats/signups?period=7d | COUNT GROUP BY DATE |
| Low-stock alert | product-svc | GET /admin/products/stats/low-stock?threshold=5 | WHERE stock <= 5 |

**FE integration:** Thêm vào `services/stats.ts` các fetch functions mới, render charts bằng recharts hoặc chart.js (cần check package.json).

### 5. Order Detail Items Bug: FE DTO Mismatch (ROOT CAUSE)

**Admin page bug:** `AdminOrder` interface không có `items` field. Cần:
1. Mở rộng `AdminOrder` type để include `items?: OrderItem[]`
2. Thay placeholder text bằng real items table (dùng pattern từ `/profile/orders/[id]`)

**User page bug (cần verify):** Backend `getOrder()` đã dùng `findByIdWithItems()` — items được fetch. Nghi vấn: FE `getOrderById()` parse response `ApiResponse<OrderDto>` → nếu `httpGet` tự unwrap `data` field thì `order.items` phải có. Cần kiểm tra:
- `http.ts` unwrap logic: `response.data` hay full response
- BE `OrderController.getOrder()` vs `AdminOrderController.getOrder()` — cả hai đều call `getOrder(id, false/true)` với `findByIdWithItems()`
- Khả năng: `findByUserId()` không fetch join items (LAZY) nhưng `findByIdWithItems()` thì có — order list page không có items nhưng order detail page thì có

**Kết luận:** Bug thực sự ở admin page là hardcoded placeholder. User page cần verify có phải FE type mismatch hay không.

### 6. Storage Audit Classification

**localStorage items phát hiện:**

| Key | File | Classification | Action v1.3 |
|-----|------|---------------|-------------|
| `cart` | services/cart.ts | USER DATA — loses on logout/cross-device | MIGRATE → DB (cart-persist phase) |
| `accessToken` | services/token.ts | SECURITY — XSS risk (accepted tradeoff v1.1) | KEEP (documented decision) |
| `refreshToken` | services/token.ts | SECURITY — XSS risk | KEEP (documented, no refresh flow implemented) |
| `userProfile` | providers/AuthProvider.tsx | USER DATA — cache của /api/users/me | ACCEPTABLE (cache, re-fetched on mount) |

**Không có sessionStorage** — tất cả dùng localStorage.

**Auth token migration (accessToken → httpOnly cookie):** Scope rộng — cần thay đổi AuthProvider, middleware.ts pattern (`auth_present` cookie hiện dùng để detect login state), tất cả `httpGet/httpPost` headers. Nếu visible-first priority giữ nguyên: **defer auth-token migration**, chỉ migrate cart (visible user impact).

### 7. Seed Catalog: Flyway V7 cho product-svc

**Flyway state:** product-svc hiện có V1-V6. V7 available cho seed catalog.

**Migration approach:**
```sql
-- V7__seed_100_products.sql (product_svc schema, seed-dev folder)
-- 5 categories: phones/laptops/mice/keyboards/headphones
-- ~20 products/category, Unsplash CDN URLs, realistic brands
-- Insert categories trước, products sau (FK constraint)
```

**Lưu ý:** Nếu Flyway migration V7 đặt trong `db/migration/` sẽ chạy cả prod. Đặt trong `seed-dev/` như V100 hiện tại để chỉ chạy trong dev profile. Hoặc dùng `@Profile("dev")` Java Bean alternative.

**Conflict risk:** V100 hiện là seed data cũ — V7 seed mới có thể conflict với V100 products nếu dùng same IDs. Cần truncate + re-insert hoặc INSERT IGNORE pattern.

---

## Component Boundaries: New vs Modified

### NEW Components

| Component | Type | Location | Notes |
|-----------|------|----------|-------|
| CouponController | Backend | order-svc: `/coupons`, `/admin/coupons` | REST CRUD + validate endpoint |
| CouponRepository | Backend | order-svc | JPA repo cho coupons + coupon_usages |
| CartRepository (JPA) | Backend | order-svc | Thay InMemoryCartRepository |
| CartItemEntity | Backend | order-svc | JPA entity cho cart_items table |
| Next.js /api/chat/ routes | Frontend | `app/api/chat/*/route.ts` | Streaming handler + DB persist |
| ChatWidget | Frontend | `components/chat/ChatWidget.tsx` | Customer-facing chat UI |
| AdminRevenueChart | Frontend | `components/charts/` | recharts |
| AdminTopProductsChart | Frontend | `components/charts/` | recharts |
| AdminStatusPieChart | Frontend | `components/charts/` | recharts |
| AdminSignupsChart | Frontend | `components/charts/` | recharts |
| CouponInput | Frontend | `app/checkout/` | Coupon code input + validate |
| AdminCouponsPage | Frontend | `app/admin/coupons/` | Admin CRUD |

### MODIFIED Components

| Component | Change | Notes |
|-----------|--------|-------|
| `services/cart.ts` | localStorage → API calls | Giữ `cart:change` event pattern |
| `OrderController.createOrderFromCommand()` | Thêm coupon validate + discount | Validate coupon, apply discount, persist coupon_usage |
| `OrderEntity` | Thêm couponCode + discountAmount fields | Flyway V3 alter table |
| `AdminOrder` interface (FE) | Thêm `items` field | Bug fix |
| `/admin/orders/[id]/page.tsx` | Hiện thị real items | Bug fix |
| `OrderCrudService.createOrderFromCommand()` | Coupon handling | Apply discount trước khi tính total |
| `AdminStatsController` | Thêm revenue/top-products/status-pie endpoints | Chart data |
| `docker-compose.yml` | Thêm ANTHROPIC_API_KEY env (Next.js container hoặc separate) | Chat |
| `api-gateway/application.yml` | Thêm /api/orders/coupons/* route | Coupon validate public |
| Review controllers | Edit/delete + admin hide/approve | REV-04 |

---

## Data Flow Changes (Checkout)

### v1.2 Checkout Flow
```
FE cart (localStorage) → POST /api/orders {items, shippingAddress, paymentMethod}
  order-svc: validate stock → persist order + items → deduct stock
  ← OrderDto
```

### v1.3 Checkout Flow (with coupon + DB cart)
```
FE loads cart → GET /api/orders/cart (order-svc, user_id from header)
  ← CartDto {items: [{productId, productName, unitPrice, quantity, stock}]}

FE apply coupon → POST /api/orders/coupons/validate {code, cartTotal}
  order-svc: check active + not expired + min_order + usage_count
  ← {valid: bool, discountAmount: N, finalTotal: N}

FE checkout → POST /api/orders {items, shippingAddress, paymentMethod, couponCode?}
  order-svc:
    1. validate stock (existing D-04)
    2. validate + lock coupon (if couponCode provided)
    3. compute totalAmount - discountAmount
    4. persist order + order_items + coupon_usage
    5. deduct stock (existing D-05)
    6. clear cart items for user
  ← OrderDto {total, discountAmount, couponCode, items[...]}
```

---

## Suggested Build Order (Dependency-Minimizing)

### Phase 16 — Seed Catalog (FIRST: nhiều features cần realistic data)

**Why first:** Chart data vô nghĩa nếu chỉ có 5-6 products. Coupon demo cần đủ products. Admin charts top-products cần đủ order history với nhiều products khác nhau.

**Deliverables:**
- V7 Flyway seed migration (product-svc) với ~100 products / 5 categories
- Unsplash WebP CDN URLs trong thumbnailUrl + imageUrl fields
- Realistic brand names (Apple, Samsung, Dell, Logitech, Sony...)
- Verify existing FE product list + search + filters work với real data

**Dependencies:** None — standalone

### Phase 17 — ORDER-DETAIL Items Fix (SECOND: unblocks admin charts + user UX)

**Why second:** Nếu admin charts sau này hiện order detail, cần items hiển thị đúng. User experience improvement không phụ thuộc gì. Nhanh (1-2 plans).

**Deliverables:**
- Fix `AdminOrder` interface + admin order detail page để show items
- Verify user order detail page items render đúng
- Debug FE DTO parse nếu cần

**Dependencies:** None — pure FE fix

### Phase 18 — STORAGE Audit + Cart → DB Migration (THIRD: unblocks coupon)

**Why third:** Coupon applies to cart total — cần cart persist trên server để coupon validation có context. Nếu cart vẫn ở localStorage, coupon validation phải trust FE-provided total (security concern).

**Deliverables:**
- Flyway V4 order-svc: carts + cart_items tables
- Replace `InMemoryCartRepository` với JPA `CartRepository`
- Extend `CartEntity` → `CartItemEntity` JPA entity
- FE `services/cart.ts` refactor: API-first với localStorage fallback khi unauthenticated
- Storage audit doc: classify tất cả localStorage keys

**Dependencies:** Phase 16 (cần products đủ để add to cart và test)

### Phase 19 — ADMIN Completion: Charts + Order Detail (FOURTH)

**Why fourth:** Charts cần data từ Phase 16 seed. Admin order detail fix từ Phase 17 unblocks full admin UX.

**Deliverables:**
- Revenue/time chart (order-svc aggregate query)
- Top products chart (order-svc join)
- Order status pie (order-svc)
- Signups trend (user-svc)
- Low-stock alert (product-svc)
- FE chart components (recharts recommended)

**Dependencies:** Phase 16 (realistic data), Phase 17 (order detail items)

### Phase 20 — COUPON System (FIFTH: cart DB prerequisite)

**Why fifth:** Coupon validation cần cart total từ server — Phase 18 (cart DB) phải hoàn thành. Cũng cần đủ products để demo coupon trên các categories.

**Deliverables:**
- Flyway V3 order-svc: coupons + coupon_usages + alter orders
- `CouponEntity`, `CouponUsageEntity`, `CouponRepository`
- `CouponController`: POST /coupons/validate, GET+POST+PATCH+DELETE /admin/coupons/**
- `OrderCrudService.createOrderFromCommand()`: coupon validate + discount logic
- FE: CouponInput component trong checkout + AdminCouponsPage

**Dependencies:** Phase 18 (cart DB — coupon applies to cart)

### Phase 21 — REVIEW Polish: REV-04+ (SIXTH: independent)

**Why sixth:** Review edit/delete + admin moderation tương đối independent. Sort by helpful/newest/rating cần thêm BE sort params.

**Deliverables:**
- Author edit (PATCH /api/products/{id}/reviews/{reviewId}) + delete (DELETE)
- Admin moderation: PATCH /api/products/admin/reviews/{id}/status {hidden|approved}
- FE sort controls: dropdown newest/rating/helpful
- Phase 13 verified-buyer vẫn giữ nguyên

**Dependencies:** Phase 16 (cần real products để test reviews thực tế)

### Phase 22 — AI Chatbot Claude API MVP (LAST: most complex, needs full data)

**Why last:** Chatbot cần product catalog đầy đủ (Phase 16) để demo product Q&A. Chat history persist cần design riêng. Claude API streaming cần Next.js API Routes setup. Phức tạp nhất — để cuối tránh blocking phases khác.

**Deliverables:**
- Next.js API Routes `/api/chat/sessions` + `/api/chat/sessions/[id]/messages`
- Postgres schema `chat_svc` (sessions + messages) — via db/init SQL hoặc drizzle/kysely migration
- Anthropic SDK streaming integration
- Customer ChatWidget (floating button + drawer)
- Admin "suggest reply" UI
- System prompt với product catalog context (fetch top products từ product-svc lúc session create)

**Dependencies:** Phase 16 (product catalog cho context), tất cả phases trước (stable UX)

---

## Gateway Routing Plan: New Endpoints

```yaml
# Cần thêm vào api-gateway/application.yml:

# Coupon public validate: /api/orders/coupons/** → order-svc /coupons/**
# PHẢI đứng TRƯỚC order-service catch-all để không bị catch bởi /api/orders/**
- id: order-service-coupon-public
  uri: http://order-service:8080
  predicates:
    - Path=/api/orders/coupons/**
  filters:
    - RewritePath=/api/orders/coupons/(?<seg>.*), /coupons/${seg}

# Coupon admin: /api/orders/admin/coupons/** → order-svc /admin/coupons/**
# PHẢI đứng TRƯỚC order-service-admin catch-all
- id: order-service-admin-coupons
  uri: http://order-service:8080
  predicates:
    - Path=/api/orders/admin/coupons/**
  filters:
    - RewritePath=/api/orders/admin/coupons/(?<seg>.*), /admin/coupons/${seg}

# Cart per-user (by user context): /api/orders/cart/** → order-svc /cart/**
# Đã tồn tại trong catch-all /api/orders/**, NHƯNG tốt hơn là explicit route:
- id: order-service-cart
  uri: http://order-service:8080
  predicates:
    - Path=/api/orders/cart/**
  filters:
    - RewritePath=/api/orders/cart/(?<seg>.*), /cart/${seg}

# Admin stats extensions: /api/orders/admin/stats/** → order-svc /admin/orders/stats/**
# Đã covered bởi order-service-admin: /api/orders/admin/** → /admin/orders/**
# Không cần route mới — AdminStatsController @GetMapping("/stats/**") đã work

# Review admin moderation: /api/products/admin/reviews/** → product-svc /admin/reviews/**
# Đã covered bởi product-service-admin: /api/products/admin/** → /admin/products/**
# CẦN kiểm tra nếu ReviewController ở /admin/reviews/** hay /admin/products/{id}/reviews/**

# Chat: Next.js API routes — KHÔNG qua gateway (FE gọi /api/chat/* trực tiếp)
# Next.js App Router: app/api/chat/*/route.ts served tại localhost:3000/api/chat/*
```

---

## Scalability Considerations

| Concern | v1.3 Approach | v2.0+ Consideration |
|---------|--------------|---------------------|
| Cart DB load | Single Postgres, carts table per user | Redis cart store |
| Chat volume | Postgres chat_messages, no TTL | Partitioning by session date |
| Coupon race condition | Optimistic lock on usage_count | Pessimistic lock / Redis atomic counter |
| Chart queries | Direct COUNT queries, no cache | Materialized views / scheduled aggregates |
| Claude API cost | MVP: all messages sent to API | Context window trimming, semantic cache |

---

## Sources

- Codebase analysis: `sources/backend/order-service/` (CartEntity, InMemoryCartRepository, OrderCrudService, OrderRepository) — confirmed in-memory cart
- Codebase analysis: `sources/frontend/src/services/cart.ts` — confirmed localStorage-only cart
- Codebase analysis: `sources/frontend/src/services/token.ts` — localStorage token pattern documented + accepted v1.1
- Codebase analysis: `sources/frontend/src/app/admin/orders/[id]/page.tsx` — hardcoded placeholder for items
- Codebase analysis: `sources/backend/api-gateway/src/main/resources/application.yml` — full routing table
- Flyway migrations: `order-svc` V1-V2, `product-svc` V1-V6, `user-svc` V1-V2-V101
- Confidence: HIGH (direct codebase inspection, no training data reliance)
