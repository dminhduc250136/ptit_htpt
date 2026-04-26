# Phase 8: Cart → Order Persistence Visible - Context

**Gathered:** 2026-04-26
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 8 đưa persistence thật vào 3 luồng đang hardcode/stub:
1. `ProductEntity.stock` field persist trong DB (product-service); `GET /api/products/{id}` trả `stock` thật; FE product detail check stock khi add-to-cart; backend validate khi tạo order.
2. `OrderEntity` thêm per-item `OrderItemEntity` rows (separate table `order_svc.order_items`) + `shippingAddress` (JSON column) + `paymentMethod` (VARCHAR); `POST /api/orders` save full breakdown; `GET /api/orders/{id}` + `GET /api/orders/me` trả full payload.
3. FE `/account/orders/{id}` (order detail) render full breakdown thật từ backend payload; checkout thành công redirect thẳng sang `/account/orders/{id}`.
4. Fix UI product detail page: add-to-cart button + quantity +/- hiện bị disabled → sửa hoạt động; hiển thị stock thật + disable khi stock=0.

**Trong scope:** Backend: stock field vào ProductEntity + Flyway migration + deduct khi order; OrderItemEntity table + shippingAddress JSON column + paymentMethod column; GET endpoints trả full items array. FE: product detail fix + stock display; account orders/{id} wire real API; checkout redirect flow; stock=0 UX.

**Ngoài scope:** Inventory-service stock sync (phase sau); cross-service reservation pattern; real payment chain; refresh token; backend hardening invisible to user.

</domain>

<decisions>
## Implementation Decisions

### Stock Persistence + Validation (PERSIST-01)
- **D-01:** `ProductEntity` thêm field `stock: int` (default 0, nullable=false). Flyway migration `V3__add_product_stock.sql` trong product_svc schema: `ALTER TABLE product_svc.products ADD COLUMN IF NOT EXISTS stock INT NOT NULL DEFAULT 0`.
- **D-02:** `GET /api/products` và `GET /api/products/{id}` (và `/slug/{slug}`) trả `stock` trong payload. `ProductCrudService.toResponse()` hiện hardcode `stock: 0` → đọc từ `product.stock()`.
- **D-03:** FE check stock khi add-to-cart: gọi `GET /api/products/{id}` khi user click "Thêm vào giỏ" để lấy stock thật. Nếu `stock=0` → disable nút + badge "Hết hàng", không thêm vào giỏ.
- **D-04:** Backend validate stock khi `POST /api/orders`: kiểm tra từng item trong order request, nếu item nào có `quantity > stock` → trả `409 STOCK_SHORTAGE` qua `ApiErrorResponse`. FE dispatcher hiện thị modal lỗi.
- **D-05:** Stock decrement ngay khi order tạo thành công: order-service sau khi lưu `OrderEntity` thành công → gọi `PATCH /api/products/admin/{productId}` qua gateway để giảm stock (`stock -= quantity` mỗi item). Nếu deduct call fail → log error nhưng KHÔNG rollback order (MVP — acceptable).

### Order Items Persistence (PERSIST-02)
- **D-06:** Tạo bảng mới `order_svc.order_items` via Flyway `V3__add_order_items.sql`:
  ```sql
  CREATE TABLE order_svc.order_items (
    id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL,
    product_id VARCHAR(36) NOT NULL,
    product_name VARCHAR(300) NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    line_total DECIMAL(12,2) NOT NULL
  );
  ```
- **D-07:** `OrderEntity` thêm `@OneToMany(mappedBy="order", cascade=ALL, orphanRemoval=true) List<OrderItemEntity> items`. `OrderItemEntity` là entity riêng với `@ManyToOne OrderEntity order`.
- **D-08:** `shippingAddress` lưu dưới dạng JSON column trong bảng `orders`: `ADD COLUMN shipping_address JSONB`. JPA mapping dùng `@JdbcTypeCode(SqlTypes.JSON)` hoặc `@Column(columnDefinition="jsonb")` + Jackson serialization.
- **D-09:** `paymentMethod` thêm column `VARCHAR(30)`: `ADD COLUMN payment_method VARCHAR(30)`. Không validate enum backend — FE gửi 'COD' | 'BANK_TRANSFER' | 'E_WALLET', backend lưu as-is.
- **D-10:** `OrderDto` update: thêm `List<OrderItemDto> items`, `Map<String, Object> shippingAddress` (hoặc typed record), `String paymentMethod`. `OrderMapper.toDto` map từ entity. Admin detail page và user order detail đều nhận full payload.

### Order Confirmation Flow (PERSIST-03)
- **D-11:** Sau `POST /api/orders` thành công → FE redirect thẳng sang `/account/orders/{id}` (dùng `router.push`). Bỏ modal success inline hiện tại.
- **D-12:** `/account/orders/{id}` page: gọi `getOrderById(id)` qua `GET /api/orders/orders/{id}`, render full breakdown: line items table (product name, quantity, unit price, line total), shipping address, payment method, total. Bỏ placeholder TODO Phase 8.

### Product Detail UI Fix
- **D-13:** Fix add-to-cart button và +/- quantity trên product detail page (`/products/{slug}`). Bug: nút hiện bị disabled — sửa logic disabled condition.
- **D-14:** Product detail page fetch stock thật: sau khi load product (từ slug lookup), nếu `product.stock === 0` → disable nút add-to-cart + hiển thị badge/text "Hết hàng". Nếu `stock > 0` → nút hoạt động bình thường, có thể show "Còn {n} sản phẩm" (Claude's Discretion).
- **D-15:** Quantity selector: max quantity bị giới hạn bởi `product.stock`. User không chọn quantity > stock được.

### UAT Fixes đã áp dụng trước Phase 8 (ghi nhận)
- **Fixed (pre-Phase 8):** Header user dropdown popup (user icon → dropdown với Đăng xuất, Tài khoản)
- **Fixed (pre-Phase 8):** Admin role check — non-ADMIN user không vào được `/admin/*`
- **Fixed (pre-Phase 8):** Admin orders PATCH /state → 400 bug đã fix

### Claude's Discretion
- Stock display text khi `stock > 0` (VD: "Còn 5 sản phẩm" hay không hiển thị) — planner quyết.
- Chi tiết JPA `@OneToMany` fetch type (LAZY vs EAGER) — LAZY là default, dùng LAZY.
- Jackson/JPA mapping chi tiết cho `shippingAddress` JSONB — planner chọn approach phù hợp Spring Boot 3.3.
- `ProductUpsertRequest` có cần thêm field `stock` để admin update stock qua form không — planner đánh giá.
- Flyway version numbering: V3 cho cả product-service và order-service (riêng biệt, mỗi service schema riêng).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Roadmap & Requirements
- `.planning/ROADMAP.md` §"Phase 8: Cart → Order Persistence Visible" — goal, success criteria (5 SCs), REQ mapping (PERSIST-01..03)
- `.planning/REQUIREMENTS.md` §"C3. Cart → Order Persistence Visible" — PERSIST-01, PERSIST-02, PERSIST-03 behavioral spec

### Existing Entities (must read before extending)
- `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/domain/ProductEntity.java` — entity hiện tại, thêm `stock` field
- `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/service/ProductCrudService.java` — `toResponse()` line 178 hardcodes `stock: 0`; `ProductUpsertRequest` record cần thêm stock
- `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/OrderEntity.java` — entity hiện tại, thêm items/@OneToMany, shippingAddress, paymentMethod
- `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/OrderDto.java` — wire format, cần thêm items + address + paymentMethod
- `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderCrudService.java` — `createOrder()`, `OrderStateRequest`, `CreateOrderCommand` records

### FE Pages (must read before updating)
- `sources/frontend/src/app/profile/orders/[id]/page.tsx` — placeholder với TODO Phase 8 comment tại line 31-36; cần wire `getOrderById(id)`
- `sources/frontend/src/app/checkout/page.tsx` — checkout flow, đã gửi items/shippingAddress/paymentMethod; cần thay modal success bằng redirect
- `sources/frontend/src/services/orders.ts` — `createOrder`, `getOrderById` functions đã có
- `sources/frontend/src/types/index.ts` — `Order` type cần update thêm items array + address + paymentMethod

### Flyway Migrations (pattern từ Phase 7)
- `sources/backend/product-service/src/main/resources/db/migration/V2__add_product_extended_fields.sql` — pattern V2 migration cho product_svc
- `sources/backend/user-service/src/main/resources/db/migration/V2__add_fullname_phone.sql` — pattern ADD COLUMN IF NOT EXISTS

### Phase 7 Context (decisions đã lock)
- `.planning/phases/07-search-admin-real-data/07-CONTEXT.md` — D-01..D-10, gateway patterns, ProductUpsertRequest extension pattern

### Gateway Routes (đã có sẵn)
- `sources/backend/api-gateway/src/main/resources/application.yml` — `product-service-admin` route cho PATCH /api/products/admin/{id}; `order-service` route cho GET /api/orders/orders/{id}

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `OrderCrudService.CreateOrderCommand` — record đã có `items` List, `shippingAddress`, `paymentMethod` → backend đã nhận input nhưng bỏ qua khi lưu
- `services/orders.ts: createOrder()` — FE đã gửi đúng payload; `getOrderById()` đã có nhưng chưa được gọi từ account order page
- `services/cart.ts` — `readCart()`, `clearCart()` đã có; add-to-cart service đã viết
- Existing pattern `@SQLRestriction + @SQLDelete` — soft-delete pattern cho order_items nếu cần (Claude's Discretion)

### Established Patterns
- Flyway `V{n}__description.sql` per-schema migration — đã có từ Phase 5/7
- Entity getter style dạng record (`id()`, `name()`) — thống nhất toàn bộ codebase
- `@Column(length=36) String id` + UUID factory — dùng cho `OrderItemEntity.id`
- `ApiErrorResponse` + `GlobalExceptionHandler` — 409 STOCK_SHORTAGE cần thêm case
- `httpPatch` trong `http.ts` — dùng để update stock sau order

### Integration Points
- Order-service → Product-service: gọi `http://api-gateway:8080/api/products/admin/{id}` với PATCH body `{stock: newValue}` (hoặc delta endpoint riêng nếu planner chọn)
- FE product detail page: `getProductBySlug(slug)` → trả `Product` → check `product.stock`
- FE checkout: sau `createOrder()` thành công → `router.push('/account/orders/' + order.id)`

</code_context>

<specifics>
## Specific Ideas

- User xác nhận: stock decrement không rollback order nếu deduct call fail (MVP acceptable)
- User xác nhận: redirect sang `/account/orders/{id}` thay modal inline sau checkout
- Product detail: cả add-to-cart lẫn +/- quantity đều bị disabled cùng một bug — likely cùng một điều kiện disabled sai
- Quantity selector phải có `max={product.stock}` sau khi fix

</specifics>

<deferred>
## Deferred Ideas

- Inventory-service sync (stock reservation chain → D1 từ v1.0 audit, defer v1.2)
- Real payment chain integration (D2)
- Race condition handling (2 users mua cùng lúc stock cuối) — optimistic locking, defer v1.2
- Order cancellation → stock restore
- Account profile page `/profile` cho user tự edit thông tin (header dropdown point đến đây nhưng page chưa có)

</deferred>

---

*Phase: 08-cart-order-persistence*
*Context gathered: 2026-04-26*
