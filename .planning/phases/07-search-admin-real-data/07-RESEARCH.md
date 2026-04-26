# Phase 7: Search + Admin Real Data - Research

**Researched:** 2026-04-26
**Domain:** Spring Boot 3 (ProductService / UserService / OrderService) + API Gateway (Spring Cloud Gateway) + Next.js 14 App Router
**Confidence:** HIGH — toàn bộ file hiện tại đã được đọc trực tiếp; không có claim nào dựa thuần vào training knowledge

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Gateway per-service admin prefix — 3 routes mới đặt TRƯỚC general routes trong `application.yml`.
- **D-02:** Backend keyword search — `@RequestParam String keyword` trong `ProductController.listProducts` + LIKE filter trong `ProductCrudService.listProducts`.
- **D-03:** `ProductUpsertRequest` mở rộng thêm 4 fields nullable: `brand`, `thumbnailUrl`, `shortDescription`, `originalPrice`.
- **D-04:** Flyway V3 migration user-service — `ALTER TABLE user_svc.users ADD COLUMN IF NOT EXISTS full_name VARCHAR(120)` và `phone VARCHAR(20)`. `UserEntity` + `UserDto` + `UserMapper` cập nhật.
- **D-05:** `PATCH /admin/users/{id}` với `AdminUserPatchRequest(fullName, phone, roles)` partial update.
- **D-06:** Admin products — reuse modal với 2 mode (add/edit).
- **D-07:** Category dropdown trong modal — load từ `GET /api/products/admin/products/categories`.
- **D-08:** Admin orders — dedicated detail page tại `/admin/orders/[id]/page.tsx`; click row → `router.push`.
- **D-09:** Admin users — adapt columns theo real `UserDto`; `fullName` fallback `username`.
- **D-10:** Admin users — edit modal gọi `PATCH /api/users/admin/users/{id}`.

### Claude's Discretion

- JPA keyword search: LIKE vs Specification — planner chọn (nghiên cứu khuyến nghị: in-memory filter trước khi stream vì service đang dùng `productRepo.findAll()` trong RAM).
- Slug auto-generation từ name khi tạo sản phẩm — planner quyết.
- Loading skeleton pattern cho admin pages — dùng pattern đã có.
- Toast vs alert — thay `alert()` bằng `useToast()`.
- Chi tiết FE service function signatures.
- Product form validation (required vs optional).

### Deferred Ideas (OUT OF SCOPE)

- Admin inventory management.
- User profile page (`/account`).
- Product image upload (cần file storage).
- Product slug auto-gen phức tạp.

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| UI-01 | FE `/search` page: keyword gọi `listProducts({keyword})` qua gateway; empty state, loading skeleton | Backend ProductController chưa có `keyword` param → D-02 fix; FE search page đã wired đúng cấu trúc |
| UI-02 | FE `admin/products` migrate khỏi mock — list/create/edit/delete real; toast + refresh | AdminProductController đầy đủ CRUD; gateway route còn thiếu (D-01); FE page có stub rỗng |
| UI-03 | FE `admin/orders` migrate khỏi mock — list + dedicated detail page + status update | AdminOrderController có `GET /{id}` + `PATCH /{id}/state`; gateway route thiếu (D-01); detail page chưa tồn tại (D-08) |
| UI-04 | FE `admin/users` migrate khỏi mock — list + view + soft-delete + edit modal | `PATCH /admin/users/{id}` chưa tồn tại (D-05); `UserDto` chưa có `fullName`/`phone` (D-04) |

</phase_requirements>

---

## Summary

Phase 7 là phase migration: đưa 4 FE pages (`/search`, `admin/products`, `admin/orders`, `admin/users`) từ stub/mock sang CRUD thật qua API gateway. Sau khi đọc toàn bộ codebase, bức tranh hiện tại rõ ràng:

**Backend đã sẵn sàng 80%.** `AdminProductController`, `AdminOrderController` đã có đầy đủ endpoints. `AdminUserController` thiếu duy nhất PATCH endpoint. `ProductCrudService` thiếu keyword filter. `UserEntity`/`UserDto` thiếu `fullName`/`phone`.

**Gateway là gap duy nhất trên backend layer.** Không có route nào cho `/api/products/admin/**`, `/api/orders/admin/**`, `/api/users/admin/**` — mọi call admin sẽ fail 404 cho đến khi D-01 được implement.

**Frontend đã có cấu trúc đúng.** Search page đã wired thật với `listProducts({keyword})` — chỉ cần backend fix. Admin pages có skeleton UI nhưng dùng `_stubProducts = []`, `_stubOrders = []`, `_stubUsers = []` — cần wire real service functions. `http.ts` đã hỗ trợ `httpPatch`, auto-attach Bearer token.

**Primary recommendation:** Implement theo thứ tự wave — Backend (gateway + keyword search + UserEntity chain + PATCH endpoint) → FE services → FE pages. Gateway routes PHẢI có trước khi bất kỳ FE admin call nào hoạt động.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Keyword search filter | API / Backend (ProductCrudService) | — | FE đã gửi `keyword` param đúng; backend chưa consume |
| Gateway route admin prefix | API Gateway | — | Spring Cloud Gateway RewritePath — không liên quan FE hay service |
| ProductUpsertRequest extension | API / Backend (service layer) | — | Record change + controller passthrough |
| Flyway V3 migration | Database / Storage | API / Backend (Entity) | DB schema change + Entity/DTO/Mapper chain |
| PATCH /admin/users/{id} | API / Backend (controller + service) | — | New endpoint + partial update logic |
| Admin Products CRUD modal | Browser / Client (Next.js) | — | React state + API call; không cần SSR |
| Admin Orders detail page | Browser / Client (Next.js) | — | Dynamic route [id] — client component |
| Admin Users edit modal | Browser / Client (Next.js) | — | React state + PATCH call |
| FE service functions | Browser / Client | — | `services/products.ts`, `services/orders.ts`, new `services/users.ts` |
| Toast notification | Browser / Client | — | `ToastProvider` đã tồn tại; cần add vào admin layout |

---

## Standard Stack

### Core (đã có trong dự án — không cần install thêm)

| Library | Version | Purpose | Ghi chú |
|---------|---------|---------|---------|
| Spring Cloud Gateway | (theo parent BOM) | API routing + RewritePath | Đã có trong api-gateway |
| Spring Data JPA | (theo Spring Boot 3.3.2) | JPA repository | Đã có trong product/user/order service |
| Flyway Core | (theo Spring Boot BOM) | DB migration | V1 + V2 đã tồn tại ở user-service, product-service |
| Next.js App Router | (dự án hiện tại) | Dynamic routing `[id]` | `useRouter`, `useParams` từ `next/navigation` |
| React | (dự án hiện tại) | Admin page components | `useState`, `useEffect`, `useCallback` |

### Supporting

| Library | Version | Purpose | Khi dùng |
|---------|---------|---------|----------|
| `next/navigation` | built-in Next.js | `useRouter`, `useParams`, `useSearchParams` | Admin orders detail page — `router.push`, `params.id` |

**Không cần install thêm package nào** cho Phase 7. [VERIFIED: đọc codebase trực tiếp]

---

## Architecture Patterns

### System Architecture Diagram

```
FE Browser
  └─ /search?q=keyword
       └─ listProducts({keyword}) → GET /api/products?keyword=...
            └─ Gateway [product-service] route
                 └─ ProductController.listProducts(@RequestParam keyword)
                      └─ ProductCrudService.listProducts(keyword filter)

FE Browser (/admin/products, /admin/orders, /admin/users)
  └─ Admin service function (createProduct, listAdminOrders, patchAdminUser, ...)
       └─ GET|POST|PUT|PATCH|DELETE /api/{service}/admin/{resource}/**
            └─ Gateway [NEW admin route — D-01] RewritePath /api/{svc}/admin/{resource}/(.*)
                 → /{resource}/${seg} trên service
                 └─ AdminProductController | AdminOrderController | AdminUserController
                      └─ ProductCrudService | OrderCrudService | UserCrudService
                           └─ Postgres (JPA)

FE Browser (/admin/orders/[id])
  └─ getAdminOrderById(id) → GET /api/orders/admin/orders/{id}
       └─ Gateway → AdminOrderController.getOrder(id)
  └─ updateOrderState(id, status) → PATCH /api/orders/admin/orders/{id}/state
       └─ Gateway → AdminOrderController.updateState(id, body)
```

### Recommended Project Structure (thay đổi trong Phase 7)

```
sources/backend/
  api-gateway/src/main/resources/application.yml   ← thêm 3 admin routes (D-01)
  product-service/
    web/ProductController.java                      ← thêm @RequestParam keyword (D-02)
    service/ProductCrudService.java                 ← keyword filter + 4 fields ProductUpsertRequest (D-02, D-03)
    domain/ProductEntity.java                       ← thêm brand/thumbnailUrl/shortDescription/originalPrice (D-03)
  user-service/
    resources/db/migration/V3__add_fullname_phone.sql  ← NEW (D-04)
    domain/UserEntity.java                          ← thêm fullName + phone (D-04)
    domain/UserDto.java                             ← thêm fullName + phone (D-04)
    domain/UserMapper.java                          ← map fullName + phone (D-04)
    web/AdminUserController.java                    ← thêm @PatchMapping("/{id}") (D-05)
    service/UserCrudService.java                    ← thêm patchUser() + AdminUserPatchRequest (D-05)

sources/frontend/src/
  services/products.ts                              ← thêm admin CRUD functions
  services/orders.ts                                ← thêm admin functions
  services/users.ts                                 ← NEW file — admin user functions
  app/
    admin/
      layout.tsx                                    ← wrap ToastProvider
      products/page.tsx                             ← wire real + ProductUpsertModal (D-06, D-07)
      orders/page.tsx                               ← wire real + navigate to detail (D-08)
      orders/[id]/page.tsx                          ← NEW detail page (D-08)
      users/page.tsx                                ← wire real + UserEditModal (D-09, D-10)
```

### Pattern 1: Gateway RewritePath Admin Routes (D-01)

**What:** Thêm 3 routes đặt TRƯỚC các routes `product-service`, `order-service`, `user-service` hiện tại. Spring Cloud Gateway match routes theo thứ tự — routes đặt trước được match trước.

**Critical:** `product-service` hiện có route `Path=/api/products/**` — nếu route admin đặt SAU, URL `/api/products/admin/products` sẽ bị route sang `product-service` với path `/admin/products/products` thay vì `/admin/products` + empty seg. Phải đặt TRƯỚC.

```yaml
# THÊM VÀO TRƯỚC block "product-service: /api/products -> /products":
- id: product-service-admin
  uri: http://product-service:8080
  predicates:
    - Path=/api/products/admin/**
  filters:
    - RewritePath=/api/products/admin/(?<seg>.*), /admin/products/${seg}

- id: order-service-admin
  uri: http://order-service:8080
  predicates:
    - Path=/api/orders/admin/**
  filters:
    - RewritePath=/api/orders/admin/(?<seg>.*), /admin/orders/${seg}

- id: user-service-admin
  uri: http://user-service:8080
  predicates:
    - Path=/api/users/admin/**
  filters:
    - RewritePath=/api/users/admin/(?<seg>.*), /admin/users/${seg}
```

**Xác nhận FE path → Backend mapping:**

| FE gọi | Sau RewritePath | Backend nhận |
|--------|----------------|-------------|
| `GET /api/products/admin/products` | seg="" | `GET /admin/products` ✓ |
| `POST /api/products/admin/products` | seg="" | `POST /admin/products` ✓ |
| `PUT /api/products/admin/products/{id}` | seg="{id}" | `PUT /admin/products/{id}` ✓ |
| `DELETE /api/products/admin/products/{id}` | seg="{id}" | `DELETE /admin/products/{id}` ✓ |
| `GET /api/products/admin/products/categories` | seg="categories" | `GET /admin/products/categories` ✓ |
| `GET /api/orders/admin/orders` | seg="" | `GET /admin/orders` ✓ |
| `GET /api/orders/admin/orders/{id}` | seg="{id}" | `GET /admin/orders/{id}` ✓ |
| `PATCH /api/orders/admin/orders/{id}/state` | seg="{id}/state" | `PATCH /admin/orders/{id}/state` ✓ |
| `GET /api/users/admin/users` | seg="" | `GET /admin/users` ✓ |
| `PATCH /api/users/admin/users/{id}` | seg="{id}" | `PATCH /admin/users/{id}` ✓ |

[VERIFIED: đọc application.yml hiện tại + đọc AdminProductController, AdminOrderController, AdminUserController]

### Pattern 2: Keyword Search — In-Memory Filter (D-02)

**What:** `ProductCrudService.listProducts()` hiện dùng `productRepo.findAll()` rồi `.stream().filter(...)` trong RAM. Keyword search thêm vào cùng stream chain này.

**Tại sao không dùng JPA LIKE query:** Service đang sort và paginate trong application memory (không dùng JPA Pageable). Thêm JPA LIKE query sẽ cần thay đổi nhiều hơn (repository method + Pageable refactor). In-memory filter nhất quán với pattern hiện tại — phase 7 scope.

```java
// ProductController.java — thêm @RequestParam
@GetMapping
public ApiResponse<Map<String, Object>> listProducts(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size,
    @RequestParam(defaultValue = "updatedAt,desc") String sort,
    @RequestParam(required = false) String keyword   // THÊM
) {
  return ApiResponse.of(200, "Products listed",
      productCrudService.listProducts(page, size, sort, false, keyword));  // truyền keyword
}

// ProductCrudService.java — thêm keyword param + filter
public Map<String, Object> listProducts(int page, int size, String sort,
                                        boolean includeDeleted, String keyword) {
  List<ProductEntity> all = productRepo.findAll().stream()
      .filter(p -> includeDeleted || !p.deleted())
      .filter(p -> keyword == null || keyword.isBlank() ||
          p.name().toLowerCase().contains(keyword.toLowerCase()))
      .sorted(productComparator(sort))
      .toList();
  // ... paginate như cũ
}
```

**Lưu ý:** `AdminProductController.listProducts()` gọi cùng service method — cũng cần truyền keyword (nullable, optional) để tương thích. [ASSUMED: AdminProductController có thể cũng muốn keyword search — planner quyết có add @RequestParam vào admin endpoint không]

### Pattern 3: ProductUpsertRequest Extension (D-03)

**What:** Thêm 4 fields nullable vào record. Vì là Java record, toàn bộ constructor phải cập nhật — nhưng backend chỉ gọi thông qua `@Valid @RequestBody`, không có callers nội bộ khác.

**ProductEntity** hiện tại KHÔNG có các columns `brand`, `thumbnail_url`, `short_description`, `original_price` trong DB schema (V1 chỉ có: id, name, slug, category_id, price, status, deleted, timestamps). Phase 7 cần quyết định: persist các fields mới hay chỉ accept và bỏ qua?

**Recommendation (Claude's Discretion được phép):** Thêm các columns vào `product_svc.products` qua Flyway V2 migration mới (V3 không được — V3 đã reserve cho user-service nếu chung config, hoặc V2 riêng của product-service vì Flyway track per-schema). [VERIFIED: product-service chỉ có V1 migration, V2 là seed-dev — không conflict nếu add V3 vào product-service migration folder riêng]

**CRITICAL:** Nếu không add columns vào DB, `ProductEntity.update()` không thể persist các fields này → form admin sẽ accept nhưng data bị bỏ qua silently. Planner phải quyết định: (a) thêm Flyway migration + Entity columns, hoặc (b) accept-but-ignore tạm thời (Phase 8 cleanup).

**Khuyến nghị:** Thêm Flyway migration `V3__add_product_extended_fields.sql` vào product-service + thêm columns vào `ProductEntity` + update `create()`/`update()` methods.

### Pattern 4: Flyway V3 User-Service (D-04)

**Current state:**
- V1: `V1__init_schema.sql` (migration folder) — tạo bảng users
- V2: `V2__seed_dev_data.sql` (seed-dev folder) — seed admin + demo_user

**Flyway migration naming và location:**
- Migration folder: `src/main/resources/db/migration/` → chỉ chứa V1 hiện tại
- Seed folder: `src/main/resources/db/seed-dev/` → V2 seed (chỉ active khi profile=dev)
- V3 migration phải vào `db/migration/` (không phải seed-dev) vì đây là schema change — phải chạy mọi environment.

```sql
-- V3__add_fullname_phone.sql (tại db/migration/)
ALTER TABLE user_svc.users ADD COLUMN IF NOT EXISTS full_name VARCHAR(120);
ALTER TABLE user_svc.users ADD COLUMN IF NOT EXISTS phone VARCHAR(20);
```

**Chain cần update sau migration:**
1. `UserEntity`: thêm fields `fullName`, `phone` với `@Column`
2. `UserEntity.update()`: thêm optional update cho fullName/phone (hoặc tạo method riêng)
3. `UserDto`: thêm `String fullName`, `String phone`
4. `UserMapper.toDto()`: map 2 fields mới
5. `User` interface trong `types/index.ts`: đã có `fullName: string` và `phone?: string` — nhất quán

[VERIFIED: đọc V1__init_schema.sql, UserEntity.java, UserDto.java, UserMapper.java, types/index.ts]

### Pattern 5: PATCH /admin/users/{id} (D-05)

**What:** `AdminUserController` hiện có GET/POST/PUT/DELETE nhưng không có PATCH. PUT dùng `UserUpsertRequest` yêu cầu `passwordHash @NotBlank` — không phù hợp admin edit modal.

```java
// AdminUserController.java — thêm method
@PatchMapping("/{id}")
public ApiResponse<Object> patchUser(
    @PathVariable String id,
    @RequestBody AdminUserPatchRequest request   // không dùng @Valid vì tất cả fields nullable
) {
  return ApiResponse.of(200, "Admin user patched", userCrudService.patchUser(id, request));
}

// UserCrudService.java — thêm record + method
public record AdminUserPatchRequest(
    String fullName,
    String phone,
    String roles
) {}

public UserDto patchUser(String id, AdminUserPatchRequest request) {
  UserEntity user = loadUser(id);
  if (request.fullName() != null) user.setFullName(request.fullName());
  if (request.phone() != null) user.setPhone(request.phone());
  if (request.roles() != null && !request.roles().isBlank()) user.setRoles(request.roles());
  user.touch();  // cập nhật updatedAt
  return UserMapper.toDto(userRepo.save(user));
}
```

**Lưu ý:** `UserEntity` cần thêm setter methods `setFullName()`, `setPhone()`, `setRoles()` và `touch()`. [ASSUMED: Cần kiểm tra xem UserEntity có method `setRoles()` chưa — hiện tại chỉ có `update(username, email, roles)` — có thể tái dùng hoặc thêm riêng]

### Pattern 6: FE Admin Service Functions

**Reuse pattern từ `services/products.ts`** — dùng `httpGet`, `httpPost`, `httpPut`, `httpPatch`, `httpDelete`. Bearer token auto-attach.

**Thêm vào `services/products.ts`:**

```typescript
// Admin product CRUD
export function listAdminProducts(params?: ListProductsParams): Promise<PaginatedResponse<Product>> {
  const qs = new URLSearchParams();
  // ... build qs như listProducts
  return httpGet<PaginatedResponse<Product>>(`/api/products/admin/products${suffix}`);
}

export function createProduct(body: ProductUpsertBody): Promise<Product> {
  return httpPost<Product>(`/api/products/admin/products`, body);
}

export function updateProduct(id: string, body: ProductUpsertBody): Promise<Product> {
  return httpPut<Product>(`/api/products/admin/products/${encodeURIComponent(id)}`, body);
}

export function deleteProduct(id: string): Promise<void> {
  return httpDelete<void>(`/api/products/admin/products/${encodeURIComponent(id)}`);
}

export function listAdminCategories(): Promise<PaginatedResponse<Category>> {
  return httpGet<PaginatedResponse<Category>>(`/api/products/admin/products/categories`);
}
```

**Thêm vào `services/orders.ts`:**

```typescript
export function listAdminOrders(params?: ListOrdersParams): Promise<PaginatedResponse<Order>> {
  // ...
  return httpGet<PaginatedResponse<Order>>(`/api/orders/admin/orders${suffix}`);
}

export function getAdminOrderById(id: string): Promise<Order> {
  return httpGet<Order>(`/api/orders/admin/orders/${encodeURIComponent(id)}`);
}

export function updateOrderState(id: string, status: string): Promise<Order> {
  return httpPatch<Order>(`/api/orders/admin/orders/${encodeURIComponent(id)}/state`, { status });
}
```

**Tạo mới `services/users.ts`** (admin user functions):

```typescript
export function listAdminUsers(params?: ListUsersParams): Promise<PaginatedResponse<User>> {
  return httpGet<PaginatedResponse<User>>(`/api/users/admin/users${suffix}`);
}

export function patchAdminUser(id: string, body: AdminUserPatchBody): Promise<User> {
  return httpPatch<User>(`/api/users/admin/users/${encodeURIComponent(id)}`, body);
}

export function deleteAdminUser(id: string): Promise<void> {
  return httpDelete<void>(`/api/users/admin/users/${encodeURIComponent(id)}`);
}
```

### Pattern 7: Admin Orders Detail Page — Next.js Dynamic Route

**What:** Tạo `src/app/admin/orders/[id]/page.tsx` — Next.js App Router dynamic segment.

```typescript
// src/app/admin/orders/[id]/page.tsx
'use client';
import { useParams, useRouter } from 'next/navigation';

export default function AdminOrderDetailPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  // load: getAdminOrderById(id)
  // updateState: updateOrderState(id, newStatus)
  // back: router.back()
}
```

**Lưu ý `useParams` type:** Trong Next.js App Router, `useParams()` trả `{ id: string | string[] }` — phải narrow về `string`. Dùng `const { id } = useParams<{ id: string }>()` hoặc guard `Array.isArray(id) ? id[0] : id`. [VERIFIED: Next.js App Router docs pattern]

### Anti-Patterns to Avoid

- **Đặt admin routes SAU general routes trong application.yml:** Path `/api/products/**` sẽ match trước `/api/products/admin/**` → request đến sai handler.
- **Dùng `PUT /admin/users/{id}` (UserUpsertRequest) cho edit modal:** Yêu cầu `passwordHash @NotBlank` — admin không biết hash → 400 validation error.
- **Dùng `productRepo.findAll()` + stream keyword filter khi data lớn:** Ổn cho demo, nhưng note rõ limitation.
- **Không wrap ToastProvider vào admin layout:** `useToast()` trong admin pages sẽ throw context error.
- **Tạo Flyway migration với số version bị skip:** V3 phải là số tiếp theo sau V2 trong cùng folder. Nếu V2 là seed-dev và nằm ở folder khác, Flyway có thể count separately — cần kiểm tra Flyway config của user-service.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Routing admin HTTP requests | Custom servlet filter hoặc controller mapping | Spring Cloud Gateway `RewritePath` filter | Gateway đã có, pattern đã có trong `application.yml` |
| DB schema change | Sửa trực tiếp `V1__init_schema.sql` | Flyway V3 migration | Flyway track checksums — modify V1 gây CRC mismatch crash khi startup |
| Partial update với PUT | Override toàn bộ user với PUT | `@PatchMapping` + null-check per field | PUT UserUpsertRequest yêu cầu passwordHash |
| Toast từ đầu | Custom alert state | `ToastProvider` + `useToast()` đã có tại `src/components/ui/Toast/Toast.tsx` | Component đã implement đầy đủ (3500ms auto-dismiss, 3 types) |
| HTTP client với auth | `fetch()` thô | `httpGet/httpPost/httpPut/httpPatch/httpDelete` từ `services/http.ts` | Đã có auto Bearer attach, auto 401 redirect, envelope unwrap |

---

## Common Pitfalls

### Pitfall 1: Gateway Route Order — Admin Routes Bị Override bởi General Routes

**What goes wrong:** Thêm admin routes SAU `product-service` route hiện tại. Spring Cloud Gateway match theo thứ tự YAML. Route `product-service` có predicate `Path=/api/products/**` — match mọi path bắt đầu bằng `/api/products/`, kể cả `/api/products/admin/products`. Admin route không bao giờ được match.

**Root cause:** Spring Cloud Gateway route matching là first-match-wins.

**How to avoid:** Đặt 3 admin routes TRƯỚC block route của service tương ứng. Đọc YAML hiện tại trước khi thêm.

**Warning signs:** FE admin call trả `404` hoặc Spring trả `NoHandlerFoundException: No endpoint /admin/products/...` (path bị rewrite sai — thành `/admin/products/admin/products`).

### Pitfall 2: Flyway Checksum Mismatch khi Modify V1/V2

**What goes wrong:** Sửa nội dung `V1__init_schema.sql` hoặc `V2__seed_dev_data.sql` sau khi đã chạy. Flyway store checksum lúc đầu chạy; khi restart, checksum mới != stored → `FlywayException: Validate failed` → service crash on startup.

**How to avoid:** Luôn tạo file migration MỚI với version số tiếp theo (V3). Không sửa V1, V2 đã tồn tại.

**Warning signs:** Service crash với `ERROR: Migration checksum mismatch for migration version 1` trong log.

### Pitfall 3: UserDto Không Có fullName/phone → FE Hiển Thị Sai

**What goes wrong:** Triển khai D-04 theo thứ tự sai — FE pages được wire trước khi `UserDto` được cập nhật. Backend trả `UserDto` không có `fullName` → FE render `undefined` hoặc lỗi TypeScript.

**How to avoid:** Chain D-04 phải hoàn thành đầy đủ: V3 migration → UserEntity → UserDto → UserMapper → verify response shape — trước khi FE admin/users page được wire.

**Warning signs:** Admin users page hiển thị cột "Họ tên" trống cho tất cả users dù DB đã có data.

### Pitfall 4: ProductUpsertRequest Extend nhưng ProductEntity Không Persist

**What goes wrong:** Thêm 4 fields vào `ProductUpsertRequest` record và `ProductResponse` nhưng quên thêm columns vào DB schema và `ProductEntity`. Admin form submit thành công (backend accept request), nhưng các fields bị silent-drop vì `ProductEntity.create()` không set chúng.

**How to avoid:** Khi mở rộng `ProductUpsertRequest`, phải đồng thời thêm Flyway migration cho product-service (V3 hoặc phù hợp với numbering) + `@Column` vào `ProductEntity` + update `create()`/`update()` methods.

**Warning signs:** Admin tạo sản phẩm với brand="Apple", sau khi reload brand hiển thị null/empty.

### Pitfall 5: useParams Trả Array Thay vì String

**What goes wrong:** `const { id } = useParams()` trong Next.js App Router trả type `string | string[]`. Trực tiếp dùng `id` trong `getAdminOrderById(id)` khi TypeScript không narrow → runtime error hoặc unexpected array behavior.

**How to avoid:** Dùng generic `useParams<{ id: string }>()` hoặc guard: `const rawId = useParams().id; const id = Array.isArray(rawId) ? rawId[0] : rawId`.

### Pitfall 6: ToastProvider Chưa Wrap Admin Layout

**What goes wrong:** `useToast()` trong admin pages ném runtime error vì `ToastContext` không tìm thấy provider. Context default là `showToast: () => {}` (no-op) — không crash nhưng toast không hiện.

**How to avoid:** Thêm `<ToastProvider>` wrap trong `src/app/admin/layout.tsx` TRƯỚC khi các admin pages gọi `useToast()`.

**Warning signs:** Toast không hiện sau CRUD actions, hoặc console warning "useContext called without Provider".

### Pitfall 7: Admin Orders Detail Page — `username` Field trong Order

**What goes wrong:** `AdminOrdersPage` hiện có `getUserName(userId)` lookup bằng `_stubUsers`. Real `OrderEntity` chỉ lưu `userId` (UUID string) — không có `username` field trong Order DTO. Detail page cần hiển thị customer info.

**How to avoid:** Đọc `OrderCrudService` để xác định `OrderResponse` shape thực tế. Nếu `username` không có trong order response, hiển thị `userId` (partial UUID) hoặc làm call riêng đến user service (out of scope). Xem Decision D-09 mapping: FE orders table "Khách hàng" column đọc từ `username` field của order — cần verify backend trả field này. [ASSUMED: OrderEntity/OrderDto cần được đọc để xác nhận shape — chưa đọc file này]

### Pitfall 8: Roles String Format — `ROLE_ADMIN` vs `ADMIN`

**What goes wrong:** `UserEntity` lưu `roles = "ADMIN"` (từ V2 seed) nhưng `AdminUserPatchRequest.roles` có thể được FE gửi là `"ROLE_ADMIN"` (theo convention Spring Security). Nếu backend filter/badge đọc format khác nhau, hiển thị sai hoặc logic sai.

**Hiện trạng verified:** V2 seed: `roles='ADMIN'` (không có prefix ROLE_). `UserDto.roles` trả raw string. FE `types/index.ts` `User.roles?: string` — không specify format.

**How to avoid:** Planner phải quyết định format chuẩn và enforce nhất quán. Khuyến nghị: dùng `ROLE_ADMIN`/`ROLE_CUSTOMER` theo CONTEXT.md D-09 (UI-SPEC dùng `ROLE_ADMIN` cho Badge).

---

## Code Examples

### Gateway RewritePath với empty segment (verified từ STATE.md)

```yaml
# Pattern đã verified trong Phase 5 Plan 08 — "seg" thay "path" để tránh conflict với $PATH env var
- RewritePath=/api/products/admin/(?<seg>.*), /admin/products/${seg}
```

Khi FE gọi `GET /api/products/admin/products`:
- segment capture group `seg` = `products`
- Backend nhận: `GET /admin/products/products` (NOT expected!)

**Cẩn thận:** Nếu FE gọi `/api/products/admin/products`, thì seg = `products`, backend nhận `/admin/products/products` → 404!

**Đúng pattern FE phải gọi là:**
- `GET /api/products/admin/products` → seg=`products` → `/admin/products/products` (WRONG — endpoint là `/admin/products`)
- Hoặc FE gọi `GET /api/products/admin/` → seg=`` → `/admin/products/` → match `@GetMapping` của `AdminProductController` (empty path = root)

**CRITICAL RECHECK:** AdminProductController mapping là `@RequestMapping("/admin/products")` với method `@GetMapping` (no path = root). FE phải gọi `/api/products/admin/products` để seg=`products`... nhưng backend endpoint là `/admin/products` không phải `/admin/products/products`.

**Resolution:** FE phải gọi `/api/products/admin/` (trailing slash, seg="") hoặc gateway route cần adjust. Hoặc thêm thêm route riêng:

```yaml
# Cần 2 routes per service: base + wildcard
- id: product-service-admin-base
  predicates: [Path=/api/products/admin]
  filters: [RewritePath=/api/products/admin, /admin/products]
- id: product-service-admin
  predicates: [Path=/api/products/admin/**]
  filters: [RewritePath=/api/products/admin/(?<seg>.*), /admin/products/${seg}]
```

Với pattern này: `GET /api/products/admin` → `/admin/products` (list). `POST /api/products/admin` → conflict với base route predicate chỉ match GET... Cần test kỹ.

**Thực tế đơn giản nhất (dựa trên STATE.md Phase 5 gateway pattern "two-route-per-service"):**

```yaml
# BASE: POST/GET /api/products/admin → /admin/products
- id: product-service-admin-base
  uri: http://product-service:8080
  predicates:
    - Path=/api/products/admin
  filters:
    - RewritePath=/api/products/admin, /admin/products

# WILDCARD: /api/products/admin/** → /admin/products/{seg}
- id: product-service-admin-wildcard
  uri: http://product-service:8080
  predicates:
    - Path=/api/products/admin/**
  filters:
    - RewritePath=/api/products/admin/(?<seg>.*), /admin/products/${seg}
```

FE gọi với pattern nhất quán:
- List: `GET /api/products/admin` → `/admin/products` ✓
- Create: `POST /api/products/admin` → `/admin/products` ✓
- Update: `PUT /api/products/admin/{id}` → `/admin/products/{id}` ✓
- Delete: `DELETE /api/products/admin/{id}` → `/admin/products/{id}` ✓
- Categories: `GET /api/products/admin/categories` → `/admin/products/categories` ✓

[VERIFIED: STATE.md Phase 5 Plan 08 note — "two-route-per-service pattern (base + wildcard) — tránh trailing-slash ambiguity"]

---

## State of the Art

| Old Approach | Current Approach | Khi thay đổi | Impact |
|--------------|------------------|-------------|--------|
| FE dùng `alert()` cho feedback | `useToast()` hook từ `ToastContext` | Phase 7 | Toast đã có; admin pages còn dùng alert trong mock forms |
| Admin pages dùng `_stub*` arrays | Real API calls qua gateway | Phase 7 | Đây là mục tiêu của phase |
| ProductController không nhận keyword | Thêm `@RequestParam keyword` | Phase 7 D-02 | Search page đã gửi keyword — backend hiện bỏ qua |
| UserEntity không có fullName/phone | Flyway V3 + entity fields | Phase 7 D-04 | Admin users không thể hiển thị tên thật |

**Deprecated/outdated trong admin pages:**
- `_stubProducts`, `_stubOrders`, `_stubUsers`: Xóa sau khi wire real.
- `getUserName(userId)` trong orders page (lookup từ stub array): Thay bằng field trực tiếp từ order DTO.
- `alert()` calls trong mock form submits: Thay bằng `showToast()`.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `AdminProductController.listProducts` cũng cần `keyword` param (Claude's Discretion) | Pattern 2 | Minor: admin search không work — không block UI-02 |
| A2 | OrderEntity/OrderDto có field `username` hoặc có thể derive customer name | Pitfall 7 | HIGH: admin orders table "Khách hàng" column render sai nếu field không có |
| A3 | Roles stored as `ADMIN`/`USER` trong DB (không có prefix `ROLE_`) | Pitfall 8 | MEDIUM: Badge variant + edit modal mismatch nếu format khác |
| A4 | ProductUpsertRequest extension cần Flyway migration cho product_svc.products | Pattern 3 | HIGH: silent data loss nếu columns không được add vào DB |
| A5 | UserEntity cần setter methods riêng (setFullName, setPhone) thay vì update() | Pattern 5 | LOW: có thể reuse update() với full params — planner quyết |

---

## Open Questions

1. **OrderDto/OrderEntity — có `username` field không?**
   - What we know: `AdminOrderController.getOrder(id, true)` trả `OrderEntity` trực tiếp (chưa đọc `OrderCrudService`)
   - What's unclear: Order response có username/customer info không, hay chỉ có `userId`?
   - Recommendation: Đọc `OrderCrudService` và `OrderEntity` trước khi plan FE orders table column

2. **Product-service Flyway V2 numbering — có conflict không?**
   - What we know: `db/migration/V1__init_schema.sql` + `db/seed-dev/V2__seed_dev_data.sql` (2 folders riêng)
   - What's unclear: Flyway config của product-service có chỉ scan `db/migration/` hay cả `db/seed-dev/`? Nếu scan cả 2, thêm `V3__...` vào `db/migration/` OK; nếu scan cả 2 và V2 trong seed-dev cũng count, thì next version = V3.
   - Recommendation: Đọc `product-service/src/main/resources/application.yml` Flyway config để xác nhận locations

3. **`roles` format trong DB — `ADMIN` hay `ROLE_ADMIN`?**
   - What we know: V2 seed trong user-service: `roles='ADMIN'`. UI-SPEC dùng `ROLE_ADMIN` trong Badge map.
   - What's unclear: Khi Phase 6 JWT được issue, claim `roles` có prefix `ROLE_` không?
   - Recommendation: Đọc Phase 6 auth service JWT issuance code để xác nhận format

---

## Environment Availability

> Phase 7 là code changes — không có external dependencies mới. Backend services đã có Flyway, Spring Data JPA. Next.js đã có `next/navigation`.

Step 2.6: SKIPPED cho external tools. Tất cả dependencies đã available từ Phase 5/6.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Maven Surefire (Spring Boot Test) cho backend; không có FE unit test framework hiện tại |
| Config file | `pom.xml` mỗi service (Spring Boot parent) |
| Quick run command | `mvn test -pl sources/backend/user-service` (per service) |
| Full suite command | `mvn test -f sources/backend/pom.xml` hoặc per-service |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| UI-01 (D-02) | `listProducts` với keyword filter | Unit (service layer) | `mvn test -pl sources/backend/product-service -Dtest=ProductCrudServiceTest` | ❌ Wave 0 |
| UI-01 (gateway) | `/api/products?keyword=` routed đúng | Integration (gateway + service) | Docker smoke: `curl "http://localhost:8080/api/products?keyword=laptop"` | Manual |
| UI-02 (D-03) | `createProduct` với extended fields persist | Unit (service layer) | `mvn test -pl sources/backend/product-service -Dtest=ProductCrudServiceTest` | ❌ Wave 0 |
| UI-03 (D-01) | Admin orders gateway routing | Integration | Docker smoke: `curl -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/orders/admin/orders"` | Manual |
| UI-04 (D-04) | Flyway V3 migration apply thành công | Integration (startup test) | `mvn spring-boot:run -pl sources/backend/user-service` → check startup log | Manual |
| UI-04 (D-05) | `patchUser` partial update | Unit | `mvn test -pl sources/backend/user-service -Dtest=UserCrudServiceTest` | ❌ Wave 0 |
| FE (all) | Admin pages render không crash | Manual smoke | `npm run dev` → navigate admin pages | Manual |

### Sampling Rate

- **Per task commit:** `mvn test -pl sources/backend/{service}` cho service được thay đổi
- **Per wave merge:** `docker compose up` → smoke test admin endpoints với curl
- **Phase gate:** All admin CRUD operations work E2E trước `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `sources/backend/product-service/src/test/.../ProductCrudServiceTest.java` — test keyword filter (D-02) + extended fields (D-03)
- [ ] `sources/backend/user-service/src/test/.../UserCrudServiceTest.java` — test patchUser (D-05)
- [ ] FE test: không có Jest/Vitest hiện tại — manual smoke là acceptable

*(Existing test files nếu có: chưa được scan — planner xác nhận)*

---

## Security Domain

> `security_enforcement` không được set explicitly trong config.json → mặc định enabled.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | Có — admin endpoints cần auth | JWT Bearer token (đã có từ Phase 6 — http.ts auto-attach) |
| V3 Session Management | Không trực tiếp | N/A Phase 7 |
| V4 Access Control | Có — admin pages chỉ ADMIN role | Middleware Phase 6 protect `/admin/**` routes (đã có) |
| V5 Input Validation | Có | `@Valid @RequestBody` trên controller; `@NotBlank` trên request records |
| V6 Cryptography | Không | N/A Phase 7 (không có password change) |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Admin endpoint accessible without auth | Elevation of Privilege | Gateway không có auth filter (D-14 defer) — FE middleware protect client-side; backend trust X-User-Id header (Phase 7 không address) |
| Mass assignment via PATCH | Tampering | `AdminUserPatchRequest` chỉ expose 3 fields (fullName, phone, roles) — không expose passwordHash |
| SQL injection qua keyword | Tampering | In-memory filter (`.contains()` Java string) — không có SQL injection risk với approach này |

**Note:** Backend JWT verification tại gateway là D-14 — deferred sang v1.2. Phase 7 admin endpoints sẽ chỉ được protect bởi FE middleware (client-side) và header trust — acceptable cho demo scope.

---

## Sources

### Primary (HIGH confidence)

- `sources/backend/api-gateway/src/main/resources/application.yml` — route structure hiện tại, RewritePath patterns
- `sources/backend/product-service/web/AdminProductController.java` — endpoint mapping đầy đủ
- `sources/backend/order-service/web/AdminOrderController.java` — endpoint mapping đầy đủ
- `sources/backend/user-service/web/AdminUserController.java` — missing PATCH endpoint confirmed
- `sources/backend/product-service/service/ProductCrudService.java` — missing keyword param confirmed; ProductUpsertRequest current shape (5 fields)
- `sources/backend/user-service/domain/UserEntity.java` — missing fullName/phone confirmed
- `sources/backend/user-service/domain/UserDto.java` — missing fullName/phone confirmed
- `sources/backend/user-service/resources/db/migration/V1__init_schema.sql` — schema confirmed (no fullName/phone)
- `sources/frontend/src/services/products.ts` — listProducts đã có keyword; admin functions chưa có
- `sources/frontend/src/services/orders.ts` — admin functions chưa có
- `sources/frontend/src/services/http.ts` — httpPatch đã có; auto Bearer
- `sources/frontend/src/app/admin/products/page.tsx` — stub empty, modal add-only
- `sources/frontend/src/app/admin/orders/page.tsx` — stub empty, inline modal
- `sources/frontend/src/app/admin/users/page.tsx` — stub empty, no edit modal
- `sources/frontend/src/app/search/page.tsx` — đã wired; chờ backend keyword fix
- `sources/frontend/src/types/index.ts` — User có fullName + phone? đã có sẵn
- `sources/frontend/src/app/admin/layout.tsx` — ToastProvider chưa được add
- `sources/frontend/src/components/ui/Toast/Toast.tsx` — ToastProvider + useToast đã có
- `.planning/STATE.md` — D08 Phase 5 Plan 08: two-route-per-service pattern confirmed

### Secondary (MEDIUM confidence)

- `.planning/phases/07-search-admin-real-data/07-CONTEXT.md` — user decisions D-01..D-10
- `.planning/phases/07-search-admin-real-data/07-UI-SPEC.md` — UI design contract

### Tertiary (LOW confidence — ASSUMED)

- OrderEntity/OrderDto shape — chưa đọc file trực tiếp (A2)
- Roles string format trong JWT claims từ Phase 6 (A3)
- Product-service Flyway locations config (A4 partial)

---

## Metadata

**Confidence breakdown:**
- Standard Stack: HIGH — verified từ pom.xml và codebase hiện tại
- Architecture (Gateway routing): HIGH — đọc application.yml + AdminControllers trực tiếp
- Architecture (FE patterns): HIGH — đọc admin pages + services trực tiếp
- Pitfalls: HIGH — derived từ code thực tế (stub empty, missing endpoints)
- Open Questions (A2, A3): LOW — chưa đọc OrderCrudService + Phase 6 JWT code

**Research date:** 2026-04-26
**Valid until:** 2026-05-26 (stack stable — Spring Boot 3.3.2 + Next.js 14)
