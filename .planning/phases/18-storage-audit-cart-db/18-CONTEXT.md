# Phase 18: Kiểm Toán Storage + Cart→DB - Context

**Gathered:** 2026-05-02
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 18 đưa cart từ client-only localStorage sang persist server-side per-user, và audit toàn bộ FE storage để classify mọi key thành 3 nhóm (DB-migrate / UI-keep / auth-deferred).

**Trong scope:**
1. **Backend:** order-svc V4 migration tạo bảng `carts` (id, user_id UNIQUE) + `cart_items` (id, cart_id FK, product_id, quantity, UNIQUE(cart_id, product_id)). Endpoints: GET/POST/PATCH/DELETE cart cho user hiện tại; idempotent merge endpoint cho guest→user login flow.
2. **Frontend:** `services/cart.ts` rewrite — guest dùng localStorage (giữ nguyên), user đã login dùng API write-through mỗi mutation. AuthProvider hook merge guest cart vào DB khi login thành công, sau đó `clearCart()` localStorage.
3. **Storage audit:** Grep `localStorage`/`sessionStorage` toàn FE, viết SUMMARY.md classify từng key: (a) migrated to DB, (b) UI preference kept, (c) auth-token reviewed but deferred to STORE-04. Nếu phát hiện wishlist/recently-viewed/search-history → migrate trong phase này.
4. **Logout cleanup:** Cart localStorage không còn data user sau khi logout (guest cart key cleared trên logout, không "leak" sang next user).

**Ngoài scope:**
- Auth token migration sang httpOnly cookie (STORE-04, deferred — đã accept tradeoff Phase 6 D-11).
- Anonymous server-side cart (guest cart vẫn ở localStorage cho MVP).
- Coupon validation server-side cart total (Phase 20 depends on this phase).
- Offline cart sync, conflict resolution multi-device (defer).
- CartEntity stub cũ (`InMemoryCartRepository`, schema 1-row-per-add) — sẽ replace hoàn toàn, không backward-compatible.

</domain>

<decisions>
## Implementation Decisions

### Cart DB Schema (STORE-02)
- **D-01:** Tạo Flyway `V4__add_cart_tables.sql` trong order_svc:
  ```sql
  CREATE TABLE order_svc.carts (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
  );
  CREATE TABLE order_svc.cart_items (
    id VARCHAR(36) PRIMARY KEY,
    cart_id VARCHAR(36) NOT NULL REFERENCES order_svc.carts(id) ON DELETE CASCADE,
    product_id VARCHAR(36) NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(cart_id, product_id)
  );
  ```
- **D-02:** Replace `CartEntity` (record stub cũ trong `domain/CartEntity.java` + `InMemoryCartRepository`) bằng `CartEntity` mới (`@OneToMany(mappedBy="cart", cascade=ALL, orphanRemoval=true) List<CartItemEntity> items`) + `CartItemEntity` JPA entity. Repo dùng JPA `JpaRepository<CartEntity, String>` + `findByUserId(String userId)`.
- **D-03:** Cart `user_id` lookup: lấy từ JWT `sub` claim (cùng pattern Phase 8 `OrderCrudService`) qua gateway header forwarding. Không có cart guest server-side.

### Cart API Endpoints (STORE-02)
- **D-04:** `GET /api/orders/cart` → trả `CartDto { items: CartItemDto[] }` cho user hiện tại. Nếu chưa có cart row → tạo lazy + trả empty items.
- **D-05:** `POST /api/orders/cart/items` body `{productId, quantity}` → `INSERT ... ON CONFLICT (cart_id, product_id) DO UPDATE SET quantity = cart_items.quantity + EXCLUDED.quantity` (idempotent add). Validate `quantity <= product.stock` qua gateway call `GET /api/products/{id}` — fail → `409 STOCK_SHORTAGE` (reuse error code Phase 8).
- **D-06:** `PATCH /api/orders/cart/items/{productId}` body `{quantity}` → set absolute quantity. Validate stock, fail → 409. `quantity <= 0` → DELETE item (alias).
- **D-07:** `DELETE /api/orders/cart/items/{productId}` → remove single item. `DELETE /api/orders/cart` → clear all (giữ cart row, xóa items).
- **D-08:** `POST /api/orders/cart/merge` body `{items: [{productId, quantity}]}` → idempotent merge: cho mỗi item, sum quantity với DB cart hiện tại, clamp by stock. Endpoint dành riêng cho login flow. Trả `CartDto` mới.

### Frontend Cart Service Rewrite (STORE-02)
- **D-09:** `services/cart.ts` chia 2 backend impl theo auth state:
  - Guest (no token): giữ nguyên localStorage logic hiện tại (đã có `readCart`, `writeCart`, `addToCart`, `updateQuantity`, `removeFromCart`, `clearCart`).
  - User logged-in: gọi API tương ứng. Mỗi function async, return updated cart state.
- **D-10:** Pattern routing: hàm wrapper kiểm tra `getAccessToken()` — có token → API path, không → localStorage path. Caller (cart page, header badge, product detail) không cần biết.
- **D-11:** Write-through mỗi mutation: không debounce, không optimistic UI cho MVP. UX trade-off acceptable (mỗi click +/- ~100-300ms latency).
- **D-12:** Cart page (`/cart`) khi user đã login: dùng React Query `useQuery(['cart'])` + `useMutation` với `invalidateQueries(['cart'])`. Header badge subscribe cùng query key (existing `cart:change` event vẫn fire cho guest path để giữ compat).

### Guest → User Merge Flow (STORE-02)
- **D-13:** `AuthProvider.login(user)` (sau khi token được set thành công, trước khi `router.push`) → đọc `readCart()` từ localStorage. Nếu non-empty: `POST /api/orders/cart/merge` với items array. Sau khi merge thành công → `clearCart()` localStorage để tránh duplicate sync ở mutation tiếp theo.
- **D-14:** Merge fail (network, 500): log error, KHÔNG block login flow, KHÔNG clear localStorage (user thử lại lần sau khi cart sync). Toast warning "Không đồng bộ được giỏ hàng cũ — vui lòng kiểm tra lại".
- **D-15:** Logout (`AuthProvider.logout`): `clearCart()` localStorage để cart user A không leak sang guest session tiếp theo cùng browser.

### Stock Validation
- **D-16:** Validate stock cả ở mutation cart và checkout:
  - Cart mutation (POST/PATCH cart items): backend gọi product-svc qua gateway lấy stock, fail → 409 STOCK_SHORTAGE (FE toast + clamp UI).
  - Checkout (POST /api/orders): re-validate đã có sẵn từ Phase 8 D-04, giữ nguyên.
- **D-17:** Cart không lưu `stock` snapshot ở DB — luôn fetch live từ product-svc khi validate. Trade-off: thêm 1 service call/mutation, đổi lấy không có stale stock.

### Storage Audit (STORE-01, STORE-03)
- **D-18:** Audit script: grep `localStorage|sessionStorage` toàn `sources/frontend/src` (đã chạy preview — kết quả: `cart`, `userProfile`, `accessToken`, `refreshToken`, `auth_present` cookie). Viết `18-SUMMARY.md` table classify từng key: source file, purpose, classification (DB-migrated / UI-kept / auth-deferred), reason.
- **D-19:** STORE-03 fold-in policy: trong quá trình audit nếu phát hiện key user-data leak chưa biết (wishlist, recently-viewed, search-history, hoặc khác) → migrate luôn trong phase này. Nếu không có → STORE-03 đóng với note "no additional leaks found beyond cart". (User confirmed: audit kỹ, fold-in if found, OK skip if not.)
- **D-20:** Auth token (`accessToken`, `refreshToken`) trong audit report ghi rõ "deferred to STORE-04 (security hardening, visible-first defer per REQUIREMENTS.md §carry-over)" — KHÔNG migrate trong phase này, không sửa logic.
- **D-21:** `userProfile` localStorage giữ lại với note "UI session cache — DB là source of truth via user-svc Phase 10, cache để hydrate AuthProvider tránh flash unauth state". Không phải data leak.

### Claude's Discretion
- JPA mapping chi tiết (cascade, fetch type) cho `CartEntity ↔ CartItemEntity` — planner chọn (LAZY default).
- React Query `staleTime` cho cart query — planner chọn (suggest 0 hoặc Infinity với manual invalidate).
- Error message vi text cho 409 STOCK_SHORTAGE từ cart endpoint — planner soạn.
- Có cần `cart_items.unit_price_at_add` snapshot (như order_items) hay không — planner đánh giá; default KHÔNG (cart hiển thị live price từ product-svc, chỉ snapshot khi tạo order).
- Endpoint URL chính xác sau gateway prefix (`/api/orders/cart` vs `/api/cart`) — planner align với gateway routes hiện có.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Roadmap & Requirements
- `.planning/ROADMAP.md` §"Phase 18: Kiểm Toán Storage + Cart→DB" — goal, 4 success criteria, REQ mapping (STORE-01..03), depends on Phase 16
- `.planning/REQUIREMENTS.md` §"S2. Storage Audit + Cart→DB" lines 45-47 — STORE-01..03 spec; lines 94 — STORE-04 deferred note; lines 114 — visible-first carry-over policy

### Existing Cart Code (must read before replacing)
- `sources/frontend/src/services/cart.ts` — current localStorage impl, hàm signatures cần preserve cho guest path
- `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/CartEntity.java` — stub cũ sẽ replace
- `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/repository/InMemoryCartRepository.java` — sẽ delete sau khi JPA repo lên
- `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderCrudService.java` — pattern JWT user_id extraction + product-svc validation call

### Auth/Storage Code (must read for merge flow + audit)
- `sources/frontend/src/providers/AuthProvider.tsx` — login()/logout() hooks, hydration pattern (`userProfile` localStorage cache); merge call sẽ inject vào login()
- `sources/frontend/src/services/auth.ts` — login/register/logout flows
- `sources/frontend/src/services/token.ts` — token storage helpers (review-only, KHÔNG sửa)
- `sources/frontend/src/services/http.ts` — typed HTTP wrapper, dùng cho cart API calls

### FE Cart Consumers (must update)
- `sources/frontend/src/app/cart/page.tsx` — cart page UI (cần switch từ readCart sync → React Query)
- `sources/frontend/src/components/layout/Header.tsx` (hoặc tương đương) — cart badge subscriber
- `sources/frontend/src/app/products/[slug]/page.tsx` — add-to-cart button (Phase 8 đã wire stock check)
- `sources/frontend/src/app/checkout/page.tsx` — checkout đọc cart, post order; cart cần được fetch async khi user logged-in

### Flyway Migration Pattern
- `sources/backend/order-service/src/main/resources/db/migration/V2__add_order_items.sql` — pattern V{n} migration order_svc, FK + UNIQUE constraints

### Phase 8 Decisions (locked, carry forward)
- `.planning/phases/08-cart-order-persistence/08-CONTEXT.md` — D-04 (409 STOCK_SHORTAGE error code reuse), D-06..D-10 (order_items pattern, similar to cart_items), gateway routes pattern

### Phase 6 Decisions (auth tradeoff)
- `.planning/phases/06-real-auth-flow/06-CONTEXT.md` — D-11/D-12 (accessToken in localStorage XSS tradeoff accepted, defer hardening). Phase 18 audit ghi nhận, không revert.

### Gateway Routes
- `sources/backend/api-gateway/src/main/resources/application.yml` — order-service route config; cần thêm route mapping cho `/api/orders/cart/**` (hoặc dùng existing wildcard order-service route — planner verify)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `services/cart.ts` localStorage path — toàn bộ guest logic giữ nguyên, chỉ extract sang internal `_localCart()` namespace
- `OrderCrudService` JWT user_id extraction pattern — reuse cho `CartCrudService`
- `OrderItemEntity` JPA mapping (Phase 8 D-07) — pattern `@ManyToOne` parent + `@OneToMany cascade=ALL` áp dụng y hệt cho `CartEntity ↔ CartItemEntity`
- `ApiErrorResponse` + 409 STOCK_SHORTAGE từ Phase 8 — reuse error code, không tạo mới
- React Query đã có sẵn trong frontend (Phase 7+) — dùng cho cart queries

### Established Patterns
- Flyway `V{n}__description.sql` per-schema (order_svc) — V4 cho phase này
- Entity record-style getters (`id()`, `userId()`) — thống nhất codebase
- `@Column(length=36) String id` + UUID — primary key pattern
- `httpGet`, `httpPost`, `httpPatch`, `httpDelete` từ `http.ts` — auth header tự động

### Integration Points
- Order-svc → Product-svc (qua gateway): stock validation call cho mỗi cart mutation; gateway route `product-service` đã có
- AuthProvider.login → cart merge endpoint: inject sau set token, trước router.push
- AuthProvider.logout → clearCart(): inject ngay sau clearTokens
- Cart page + header badge → React Query `['cart']` key (subscribe cùng cache)

</code_context>

<specifics>
## Specific Ideas

- User chấp nhận latency write-through (không cần optimistic UI cho MVP).
- User chấp nhận guest đóng browser → mất cart (giữ behavior hiện tại, không làm anonymous server-side cart).
- User confirmed: audit kỹ toàn bộ storage, NẾU phát hiện wishlist/recently-viewed/search-history thì migrate luôn trong phase này; nếu không có thì STORE-03 đóng với note.
- Auth token KHÔNG migrate trong phase này — chỉ ghi nhận trong audit report với reference STORE-04 deferred.
- CartEntity stub cũ + InMemoryCartRepository delete hoàn toàn (chưa được dùng ở đâu — không có backward-compat concern).
- Logout phải clear cart localStorage để tránh leak sang guest session tiếp theo cùng browser.

</specifics>

<deferred>
## Deferred Ideas

- **STORE-04 — Auth-token migration localStorage → httpOnly cookie** (đã ở REQUIREMENTS.md §carry-over deferred, security hardening visible-first defer)
- Anonymous server-side cart với session_id cookie (không cần cho MVP — guest localStorage đủ dùng)
- Optimistic UI + debounced sync cho cart mutations (defer nếu UX feedback xấu)
- Multi-device cart conflict resolution (last-write-wins acceptable cho MVP)
- Offline cart support (PWA scope, không phải v1.3)
- Cart cleanup job cho stale cart_items (e.g., product bị soft-delete) — chấp nhận defer, planner có thể đề xuất `JOIN` filter ở GET endpoint

</deferred>

---

*Phase: 18-storage-audit-cart-db*
*Context gathered: 2026-05-02*
