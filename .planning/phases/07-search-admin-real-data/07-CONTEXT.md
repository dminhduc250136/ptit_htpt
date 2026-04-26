# Phase 7: Search + Admin Real Data - Context

**Gathered:** 2026-04-26
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 7 migrate FE `/search` và toàn bộ `admin/*` pages khỏi stub/mock sang CRUD thật qua gateway. Bao gồm:
- Backend fix keyword search cho ProductController + ProductCrudService
- Thêm gateway routes cho admin endpoints (per-service prefix)
- FE admin pages wire real API calls: products (list/create/edit/delete), orders (list/detail page/status update), users (list/edit/soft-delete)
- Backend: mở rộng ProductUpsertRequest, thêm fullName+phone vào UserEntity, thêm PATCH /admin/users/{id} endpoint
- Flyway V3 migration: thêm fullName + phone columns vào user_svc.users table

**Trong scope:** Gateway admin routes; FE admin services (createProduct, updateProduct, deleteProduct, listAdminOrders, updateOrderState, listAdminUsers, patchAdminUser, deleteAdminUser); admin/products reuse modal (add/edit); admin/orders dedicated detail page; admin/users full edit modal; backend keyword search fix; ProductUpsertRequest extension; UserEntity fullName/phone; PATCH user endpoint.

**Ngoài scope:** Admin inventory management (deferred); product slug auto-generation logic (planner quyết); `/account` profile page cho user tự edit fullName/phone (Phase sau); refresh token; payment chain; backend JWT verification tại gateway (D14 defer).

</domain>

<decisions>
## Implementation Decisions

### Gateway Routing (admin endpoints)
- **D-01:** Per-service admin prefix — thêm 3 gateway routes MỚI đặt TRƯỚC các routes hiện tại trong `application.yml`:
  ```yaml
  # product-service admin (TRƯỚC product-service route hiện tại)
  - id: product-service-admin
    uri: http://product-service:8080
    predicates:
      - Path=/api/products/admin/**
    filters:
      - RewritePath=/api/products/admin/(?<seg>.*), /admin/products/${seg}

  # order-service admin (TRƯỚC order-service route hiện tại)
  - id: order-service-admin
    uri: http://order-service:8080
    predicates:
      - Path=/api/orders/admin/**
    filters:
      - RewritePath=/api/orders/admin/(?<seg>.*), /admin/orders/${seg}

  # user-service admin (TRƯỚC user-service route hiện tại)
  - id: user-service-admin
    uri: http://user-service:8080
    predicates:
      - Path=/api/users/admin/**
    filters:
      - RewritePath=/api/users/admin/(?<seg>.*), /admin/users/${seg}
  ```
  FE gọi: `/api/products/admin/products`, `/api/orders/admin/orders`, `/api/users/admin/users`.
  **Why:** Routes đặt trước để match trước general `/**` routes; tránh sai path stripping.

### Backend: Keyword Search Fix
- **D-02:** Thêm optional `keyword` param vào `ProductController.listProducts` và `ProductCrudService.listProducts`.
  - JPA query filter: `LOWER(e.name) LIKE LOWER(CONCAT('%', :keyword, '%'))` (hoặc JPA Specification — planner chọn approach).
  - Public endpoint `/api/products?keyword=` — FE search page đã gọi đúng, không cần thay đổi FE.
  - Admin endpoint `/api/products/admin/products` cũng có thể support keyword (Claude's Discretion).

### Backend: ProductUpsertRequest Extension
- **D-03:** Mở rộng `ProductCrudService.ProductUpsertRequest` thêm các fields:
  - `String brand` (optional/nullable)
  - `String thumbnailUrl` (optional/nullable)
  - `String shortDescription` (optional/nullable)
  - `BigDecimal originalPrice` (optional/nullable)
  - Các fields mới đều nullable — không break existing tests.
  - Mapping trong `createProduct` / `updateProduct` update accordingly.

### Backend: UserEntity — fullName + phone
- **D-04:** Flyway V3 migration trong user-service thêm 2 nullable columns vào `user_svc.users`:
  ```sql
  ALTER TABLE user_svc.users ADD COLUMN IF NOT EXISTS full_name VARCHAR(120);
  ALTER TABLE user_svc.users ADD COLUMN IF NOT EXISTS phone VARCHAR(20);
  ```
  - `UserEntity` thêm fields `fullName` + `phone` (nullable).
  - `UserDto` update include `fullName` + `phone` (null nếu chưa set).
  - `RegisterRequest` KHÔNG thay đổi — fullName/phone không required khi đăng ký.

### Backend: Admin User Edit Endpoint
- **D-05:** Thêm endpoint `PATCH /admin/users/{id}` với `AdminUserPatchRequest`:
  ```java
  public record AdminUserPatchRequest(
      String fullName,   // nullable — update nếu present
      String phone,      // nullable — update nếu present
      String roles       // nullable — update nếu present
  ) {}
  ```
  - Chỉ update fields được cung cấp (partial update).
  - KHÔNG require passwordHash (tránh admin biết hash).
  - `PUT /admin/users/{id}` (UserUpsertRequest cần passwordHash) KHÔNG dùng cho edit modal.

### FE: Admin Products Page
- **D-06:** Reuse modal component — 1 component với 2 mode:
  - Mode "Add": click nút "+ Thêm sản phẩm", gọi `createProduct` (POST `/api/products/admin/products`)
  - Mode "Edit": click nút ✏️ trên row, pre-fill form với data hiện tại, gọi `updateProduct` (PUT `/api/products/admin/products/{id}`)
  - Submit → success toast → refresh list
- **D-07:** Category field trong form: `<select>` dropdown load từ `GET /api/products/admin/products/categories`. Load khi modal open. Lưu `categoryId` (string UUID) để gửi backend.

### FE: Admin Orders Page
- **D-08:** Dedicated detail page tại `/admin/orders/[id]/page.tsx`.
  - Click row trên list → `router.push('/admin/orders/${order.id}')`.
  - Detail page gọi `GET /api/orders/admin/orders/{id}` để load fresh data.
  - Status update ở detail page: dropdown với đủ 5 options (PENDING, CONFIRMED, SHIPPING, DELIVERED, CANCELLED), gọi `PATCH /api/orders/admin/orders/{id}/state`.
  - Inline modal trong list page (`selectedOrder` state) có thể bị remove hoặc giữ lại tùy planner.

### FE: Admin Users Page
- **D-09:** Adapt UI theo real `UserDto` shape:
  - Cột "Họ tên": hiển `fullName` nếu có, fallback `username` nếu fullName null/empty.
  - Cột "Điện thoại": hiển `phone` nếu có, fallback `—`.
  - Cột "Vai trò": đọc từ `roles` string (format: `"ROLE_ADMIN"` hoặc `"ROLE_CUSTOMER"`) — Badge variant tùy.
- **D-10:** Edit modal admin users cho phép sửa: fullName, phone, roles. Gọi `PATCH /api/users/admin/users/{id}` với `AdminUserPatchRequest`.
  - Modal có fields: input fullName, input phone, select roles (CUSTOMER / ADMIN).
  - Pre-fill với current values từ UserDto.
  - Save → success toast → refresh list.

### Claude's Discretion
- Slug auto-generation khi tạo product (từ name) — planner quyết.
- JPA implementation detail cho keyword search (LIKE vs Specification) — planner quyết.
- Loading skeleton pattern cho admin pages — dùng pattern đã có từ search page.
- Error toast / RetrySection pattern cho admin pages — follow existing pattern trong codebase.
- Chi tiết FE service function signatures — planner quyết theo pattern `services/products.ts`, `services/orders.ts`.
- Product form validation rules (required vs optional fields) — planner quyết theo ProductUpsertRequest `@NotBlank` annotations.

### Folded Todos
Không có todos được fold vào phase này.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Roadmap & Requirements
- `.planning/ROADMAP.md` §"Phase 7: Search + Admin Real Data" — goal, success criteria (UI-01..04), depends-on
- `.planning/REQUIREMENTS.md` §"C2. Admin + Search Real Data" — 4 atomic requirements UI-01..UI-04

### Prior Phase Context
- `.planning/phases/05-database-foundation/05-CONTEXT.md` — D-04 (String UUID), D-03 (Entity/DTO separation), schema layout (user_svc, product_svc, order_svc)
- `.planning/phases/06-real-auth-flow/06-CONTEXT.md` — D-11 (JWT storage localStorage + auth_present cookie), D-08 (user_role cookie), UserDto shape hiện tại (id, username, email, roles, createdAt, updatedAt)

### Gateway Config (MUST read before adding routes)
- `sources/backend/api-gateway/src/main/resources/application.yml` — existing route order + RewritePath patterns (D-01 routes phải đặt trước general routes)

### Existing Admin Controllers (MUST read)
- `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/web/AdminProductController.java` — endpoints: GET/POST /admin/products, PUT/PATCH/DELETE /admin/products/{id}, GET/POST /admin/products/categories
- `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/AdminOrderController.java` — endpoints: GET /admin/orders, GET /admin/orders/{id}, PATCH /admin/orders/{id}/state
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/AdminUserController.java` — existing: GET/PUT/DELETE /admin/users, GET /admin/users/{id}; cần thêm PATCH /admin/users/{id}

### Backend Service Layer (MUST read)
- `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/service/ProductCrudService.java` — ProductUpsertRequest (5 fields hiện tại), listProducts signature (chưa có keyword)
- `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/web/ProductController.java` — listProducts endpoint (chưa pass keyword sang service)
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/service/UserCrudService.java` — UserUpsertRequest (requires passwordHash — KHÔNG dùng cho admin edit modal)
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/domain/UserEntity.java` — current fields (chưa có fullName/phone)

### Frontend (MUST read)
- `sources/frontend/src/services/products.ts` — listProducts, getProductById đã có; cần thêm admin CRUD functions
- `sources/frontend/src/services/orders.ts` — listMyOrders, createOrder đã có; cần thêm admin functions
- `sources/frontend/src/services/http.ts` — httpGet/httpPost/httpPut/httpPatch/httpDelete; auto-attach Bearer token từ localStorage (không cần modify)
- `sources/frontend/src/app/admin/products/page.tsx` — stub _stubProducts = []; add modal hiện có (cần wire + thêm edit mode)
- `sources/frontend/src/app/admin/orders/page.tsx` — stub _stubOrders = []; modal hiện có (cần wire + chuyển sang navigate to detail page)
- `sources/frontend/src/app/admin/users/page.tsx` — stub _stubUsers = []; cần thêm edit modal
- `sources/frontend/src/app/search/page.tsx` — đã wired đúng (chỉ cần backend fix keyword filter)
- `sources/frontend/src/types/index.ts` — User interface (có fullName? + phone? nhưng backend UserDto chưa trả — sẽ nhất quán sau D-04)

### Codebase Maps
- `.planning/codebase/STACK.md` — Spring Boot 3.3.2, Java 17, Next.js
- `.planning/codebase/CONVENTIONS.md` — ApiErrorResponse pattern, package layout

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`services/http.ts`**: `httpGet/httpPost/httpPut/httpPatch/httpDelete` — auto Bearer token, auto envelope unwrap, auto 401 redirect. Admin service functions dùng thẳng, không cần modify.
- **`services/products.ts`**: `listProducts({keyword, page, size})` đã có — search page dùng. Thêm admin CRUD functions vào cùng file.
- **`services/orders.ts`**: `listMyOrders`, `getOrderById` đã có. Thêm `listAdminOrders`, `getAdminOrderById`, `updateOrderState`.
- **`components/ui/Button`, `Input`, `Badge`**: Đã dùng trong admin pages — tiếp tục dùng.
- **`RetrySection`**: Error retry pattern từ search page — áp dụng cho admin pages khi fetch fail.
- **`AdminProductController`**: Full CRUD + categories đã có; gateway route là gap duy nhất.
- **`AdminOrderController`**: Full CRUD + PATCH state đã có.
- **`AdminUserController`**: CRUD đã có; chỉ thiếu PATCH partial update endpoint.

### Established Patterns
- **Admin page pattern**: `useState([])` + `useEffect(() => load())` + loading/error state — follow search page pattern.
- **Modal pattern**: overlay + modal div + stopPropagation — đã có trong admin/products, admin/users.
- **Toast**: Hiện dùng `alert()` trong mock — cần thay bằng real toast component hoặc simple state-based notification (planner quyết).
- **API envelope**: `ApiResponse.of(200, msg, data)` — tất cả admin controllers đều follow pattern này; `http.ts` unwrap `data` field tự động.
- **Flyway migration**: V1 (schema) + V2 (seed) đã có per service. V3 cho user-service thêm fullName/phone columns.

### Integration Points
- Gateway `application.yml` — thêm 3 admin routes mới (đặt trước general routes).
- `ProductCrudService.listProducts()` — thêm `String keyword` param + JPA WHERE clause.
- `ProductCrudService.ProductUpsertRequest` — thêm 4 fields nullable.
- `UserEntity` → `UserDto` chain — thêm fullName + phone với Flyway V3 migration.
- `AdminUserController` — thêm `@PatchMapping("/{id}")` method.
- Admin pages (`/admin/orders/[id]`) — tạo mới route + page component.

</code_context>

<specifics>
## Specific Ideas

- Admin inventory management — user hỏi nhưng ngoài scope Phase 7. Ghi nhận cho roadmap.
- User profile editing (`/account` page) — fullName + phone được set qua profile page của user (Phase sau, không phải Phase 7 admin).
- Product slug: khi admin tạo product, form có input slug hay tự generate từ name? — Claude's Discretion (suggest: auto-generate + allow override).
- Admin orders status update dùng endpoint `PATCH /admin/orders/{id}/state` với body `OrderStateRequest { status }` — xác nhận từ `AdminOrderController`.

</specifics>

<deferred>
## Deferred Ideas

- **Admin inventory management** — user hỏi về "admin tồn kho" (inventory-service admin CRUD). Ngoài scope UI-01..04. Có thể add vào backlog hoặc Phase 9+.
- **User profile page (`/account`)** — user tự edit fullName, phone, avatar sau khi login. Ngoài Phase 7 (chỉ admin mới edit qua modal Phase 7). Phase sau.
- **Product image upload** — hiện form dùng URL input. File upload là feature riêng (cần file storage — S3/MinIO). Defer.
- **Product slug auto-gen** — nếu admin không nhập slug, backend có thể tự generate từ name. Implementation detail — defer nếu phức tạp.

</deferred>

---

*Phase: 07-search-admin-real-data*
*Context gathered: 2026-04-26*
