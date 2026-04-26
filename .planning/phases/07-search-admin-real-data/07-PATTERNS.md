# Phase 7: Search + Admin Real Data - Pattern Map

**Mapped:** 2026-04-26
**Files analyzed:** 16 (files mới/sửa đổi)
**Analogs found:** 16 / 16

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `sources/backend/api-gateway/src/main/resources/application.yml` | config | request-response | chính file này (thêm 3 blocks mới) | exact |
| `sources/backend/product-service/web/ProductController.java` | controller | request-response | `AdminProductController.java` (có `@RequestParam sort`) | exact |
| `sources/backend/product-service/service/ProductCrudService.java` | service | CRUD | chính file này + `UserCrudService.java` | exact |
| `sources/backend/product-service/domain/ProductEntity.java` | model | CRUD | `UserEntity.java` (cùng pattern @Column nullable) | exact |
| `sources/backend/product-service/resources/db/migration/V3__add_product_extended_fields.sql` | migration | batch | `user-service/V1__init_schema.sql` (ALTER TABLE pattern) | role-match |
| `sources/backend/user-service/resources/db/migration/V3__add_fullname_phone.sql` | migration | batch | `user-service/V1__init_schema.sql` | exact |
| `sources/backend/user-service/domain/UserEntity.java` | model | CRUD | `ProductEntity.java` (@Column, accessor pattern) | exact |
| `sources/backend/user-service/domain/UserDto.java` | model | transform | chính file này | exact |
| `sources/backend/user-service/domain/UserMapper.java` | utility | transform | chính file này | exact |
| `sources/backend/user-service/web/AdminUserController.java` | controller | CRUD | `AdminOrderController.java` (@PatchMapping pattern) | exact |
| `sources/backend/user-service/service/UserCrudService.java` | service | CRUD | chính file này + `ProductCrudService.java` | exact |
| `sources/frontend/src/services/products.ts` | service | request-response | chính file này (listProducts pattern) | exact |
| `sources/frontend/src/services/orders.ts` | service | request-response | `services/products.ts` | exact |
| `sources/frontend/src/services/users.ts` | service | request-response | `services/orders.ts` | exact |
| `sources/frontend/src/app/admin/products/page.tsx` | component | CRUD | chính file này (add modal đã có, wire real) | exact |
| `sources/frontend/src/app/admin/orders/page.tsx` | component | CRUD | chính file này (replace inline modal với router.push) | exact |
| `sources/frontend/src/app/admin/orders/[id]/page.tsx` | component | request-response | `app/search/page.tsx` (useCallback load, loading/failed state) | role-match |
| `sources/frontend/src/app/admin/users/page.tsx` | component | CRUD | `app/admin/products/page.tsx` (modal pattern) | exact |
| `sources/frontend/src/app/admin/layout.tsx` | config | request-response | chính file này (thêm ToastProvider wrap) | exact |

---

## Pattern Assignments

### `application.yml` — 3 admin routes mới (D-01)

**Analog:** Chính file `sources/backend/api-gateway/src/main/resources/application.yml`

**Existing two-route-per-service pattern** (lines 23–34 — user-service làm mẫu):
```yaml
# user-service: /api/users -> /users, /api/users/{id} -> /users/{id}
- id: user-service-base
  uri: http://user-service:8080
  predicates:
    - Path=/api/users
  filters:
    - RewritePath=/api/users, /users
- id: user-service
  uri: http://user-service:8080
  predicates:
    - Path=/api/users/**
  filters:
    - RewritePath=/api/users/(?<seg>.*), /users/${seg}
```

**Pattern mới cần copy (áp dụng cho 3 services):**
```yaml
# ĐẶTTRƯỚC block user-service hiện tại (line 22)
- id: user-service-admin-base
  uri: http://user-service:8080
  predicates:
    - Path=/api/users/admin
  filters:
    - RewritePath=/api/users/admin, /admin/users
- id: user-service-admin
  uri: http://user-service:8080
  predicates:
    - Path=/api/users/admin/**
  filters:
    - RewritePath=/api/users/admin/(?<seg>.*), /admin/users/${seg}
```

**Lý do two-route:** Route base `Path=/api/users/admin` match `GET/POST /api/users/admin` (không có trailing slash). Route wildcard `Path=/api/users/admin/**` match `GET /api/users/admin/{id}`, `PATCH /api/users/admin/{id}`, v.v. Nếu chỉ dùng 1 route wildcard, `GET /api/users/admin` (không có `/**`) sẽ không match → 404.

**Route order critical:** 3 cặp admin route PHẢI đặt TRƯỚC cặp general route của service tương ứng. Spring Cloud Gateway match first-wins. Route `Path=/api/users/**` (general) sẽ match `/api/users/admin/**` nếu đặt trước.

---

### `ProductController.java` — thêm @RequestParam keyword (D-02)

**Analog:** `sources/backend/product-service/web/AdminProductController.java`

**Existing @GetMapping với @RequestParam pattern** (lines 33–39):
```java
@GetMapping
public ApiResponse<Map<String, Object>> listProducts(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size,
    @RequestParam(defaultValue = "updatedAt,desc") String sort,
    @RequestParam(defaultValue = "true") boolean includeDeleted
) {
  return ApiResponse.of(200, "Admin products listed", productCrudService.listProducts(page, size, sort, includeDeleted));
}
```

**Thay đổi cho `ProductController.java`** — thêm `@RequestParam(required = false) String keyword` và truyền sang service:
```java
// ProductController.java — lines 32–37 hiện tại (thêm keyword param)
@GetMapping
public ApiResponse<Map<String, Object>> listProducts(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size,
    @RequestParam(defaultValue = "updatedAt,desc") String sort,
    @RequestParam(required = false) String keyword          // THÊM
) {
  return ApiResponse.of(200, "Products listed",
      productCrudService.listProducts(page, size, sort, false, keyword));
}
```

---

### `ProductCrudService.java` — keyword filter + ProductUpsertRequest extension (D-02, D-03)

**Analog:** Chính file `sources/backend/product-service/service/ProductCrudService.java`

**Existing stream filter pattern** (lines 31–44):
```java
public Map<String, Object> listProducts(int page, int size, String sort, boolean includeDeleted) {
  List<ProductEntity> all = productRepo.findAll().stream()
      .filter(product -> includeDeleted || !product.deleted())
      .sorted(productComparator(sort))
      .toList();
  Map<String, Object> page0 = paginate(all, page, size);
  // ...
}
```

**Thêm keyword filter vào stream chain:**
```java
public Map<String, Object> listProducts(int page, int size, String sort,
                                        boolean includeDeleted, String keyword) {
  List<ProductEntity> all = productRepo.findAll().stream()
      .filter(product -> includeDeleted || !product.deleted())
      .filter(product -> keyword == null || keyword.isBlank() ||
          product.name().toLowerCase().contains(keyword.toLowerCase()))
      .sorted(productComparator(sort))
      .toList();
  // paginate như cũ
}
```

**Existing ProductUpsertRequest record** (lines 208–214):
```java
public record ProductUpsertRequest(
    @NotBlank String name,
    @NotBlank String slug,
    @NotBlank String categoryId,
    @DecimalMin("0.0") BigDecimal price,
    @NotBlank String status
) {}
```

**Extended record (D-03) — thêm 4 nullable fields:**
```java
public record ProductUpsertRequest(
    @NotBlank String name,
    @NotBlank String slug,
    @NotBlank String categoryId,
    @DecimalMin("0.0") BigDecimal price,
    @NotBlank String status,
    String brand,                // nullable — D-03
    String thumbnailUrl,         // nullable — D-03
    String shortDescription,     // nullable — D-03
    BigDecimal originalPrice     // nullable — D-03
) {}
```

**Cập nhật createProduct / updateProduct:** Sau khi thêm columns vào ProductEntity (qua Flyway V3), các method `create()` và `update()` nhận thêm 4 fields. Pattern lấy từ `UserEntity.update()` (lines 73–78):
```java
public void update(String username, String email, String roles) {
  this.username = username;
  this.email = email;
  this.roles = roles;
  this.updatedAt = Instant.now();
}
```

---

### `ProductEntity.java` — thêm 4 @Column nullable (D-03)

**Analog:** `sources/backend/user-service/domain/UserEntity.java`

**Existing nullable @Column pattern** (lines 28–29, 37–38):
```java
@Column(length = 36, nullable = false, updatable = false)
private String id;

@Column(name = "password_hash", nullable = false, length = 120)
private String passwordHash;
```

**Pattern cho 4 fields mới (nullable = không set nullable=false):**
```java
@Column(length = 200)
private String brand;

@Column(name = "thumbnail_url", length = 500)
private String thumbnailUrl;

@Column(name = "short_description", length = 500)
private String shortDescription;

@Column(name = "original_price", precision = 12, scale = 2)
private BigDecimal originalPrice;
```

**Accessor pattern** (lines 101–109 của ProductEntity):
```java
public String brand() { return brand; }
public String thumbnailUrl() { return thumbnailUrl; }
public String shortDescription() { return shortDescription; }
public BigDecimal originalPrice() { return originalPrice; }
```

---

### `V3__add_product_extended_fields.sql` (product-service migration)

**Analog:** `sources/backend/product-service/src/main/resources/db/migration/V1__init_schema.sql`

**Location:** `sources/backend/product-service/src/main/resources/db/migration/V3__add_product_extended_fields.sql`

**Note Flyway numbering:** product-service có V1 ở `db/migration/` và V2 ở `db/seed-dev/`. Vì đây là schema change (không phải seed), đặt vào `db/migration/`. Next version sau V1 trong folder này là V2 — nhưng **kiểm tra Flyway config** xem có scan `db/seed-dev/` không trước khi quyết định số version. Nếu Flyway scan cả 2 folder, version tiếp theo phải là V3 (sau V2 seed-dev).

**SQL pattern** (từ V1 user-service):
```sql
-- Thêm columns mở rộng cho product_svc.products (D-03)
ALTER TABLE product_svc.products ADD COLUMN IF NOT EXISTS brand VARCHAR(200);
ALTER TABLE product_svc.products ADD COLUMN IF NOT EXISTS thumbnail_url VARCHAR(500);
ALTER TABLE product_svc.products ADD COLUMN IF NOT EXISTS short_description VARCHAR(500);
ALTER TABLE product_svc.products ADD COLUMN IF NOT EXISTS original_price NUMERIC(12,2);
```

---

### `V3__add_fullname_phone.sql` (user-service migration) (D-04)

**Analog:** `sources/backend/user-service/src/main/resources/db/migration/V1__init_schema.sql`

**Location:** `sources/backend/user-service/src/main/resources/db/migration/V3__add_fullname_phone.sql`

**Note Flyway numbering:** user-service có V1 ở `db/migration/`, V2 ở `db/seed-dev/`. Tương tự product-service, kiểm tra scan config — nếu chỉ scan `db/migration/`, next là V2; nếu scan cả 2, next là V3. Tên `V3__` per CONTEXT.md D-04 là an toàn giả sử Flyway scan cả 2.

**SQL pattern:**
```sql
-- V3__add_fullname_phone.sql
ALTER TABLE user_svc.users ADD COLUMN IF NOT EXISTS full_name VARCHAR(120);
ALTER TABLE user_svc.users ADD COLUMN IF NOT EXISTS phone VARCHAR(20);
```

---

### `UserEntity.java` — thêm fullName + phone (D-04)

**Analog:** `sources/backend/product-service/domain/ProductEntity.java` (cùng pattern @Column, accessor)

**Existing field + accessor pattern** (ProductEntity lines 30–49, 101–109):
```java
@Column(nullable = false, length = 300)
private String name;
// ...
public String name() { return name; }
```

**Thêm 2 fields vào UserEntity** (sau `roles` field, trước `deleted`):
```java
@Column(name = "full_name", length = 120)
private String fullName;

@Column(length = 20)
private String phone;
```

**Cập nhật constructor** (UserEntity lines 55–65):
```java
protected UserEntity(String id, String username, String email, String passwordHash,
                     String roles, String fullName, String phone,
                     boolean deleted, Instant createdAt, Instant updatedAt) {
  // ... existing assignments
  this.fullName = fullName;
  this.phone = phone;
}
```

**Thêm setter methods** (pattern từ `changePasswordHash` line 80–83):
```java
public void setFullName(String fullName) {
  this.fullName = fullName;
  this.updatedAt = Instant.now();
}

public void setPhone(String phone) {
  this.phone = phone;
  this.updatedAt = Instant.now();
}

public void touch() {
  this.updatedAt = Instant.now();
}
```

**Thêm accessors:**
```java
public String fullName() { return fullName; }
public String phone() { return phone; }
```

---

### `UserDto.java` — thêm fullName + phone (D-04)

**Analog:** Chính file `sources/backend/user-service/domain/UserDto.java`

**Existing record** (lines 9–16):
```java
public record UserDto(
    String id,
    String username,
    String email,
    String roles,
    Instant createdAt,
    Instant updatedAt
) {}
```

**Extended record:**
```java
public record UserDto(
    String id,
    String username,
    String email,
    String roles,
    String fullName,      // THÊM — nullable
    String phone,         // THÊM — nullable
    Instant createdAt,
    Instant updatedAt
) {}
```

---

### `UserMapper.java` — map fullName + phone (D-04)

**Analog:** Chính file `sources/backend/user-service/domain/UserMapper.java`

**Existing toDto** (lines 7–16):
```java
public static UserDto toDto(UserEntity e) {
  return new UserDto(
      e.id(),
      e.username(),
      e.email(),
      e.roles(),
      e.createdAt(),
      e.updatedAt()
  );
}
```

**Updated toDto:**
```java
public static UserDto toDto(UserEntity e) {
  return new UserDto(
      e.id(),
      e.username(),
      e.email(),
      e.roles(),
      e.fullName(),     // THÊM
      e.phone(),        // THÊM
      e.createdAt(),
      e.updatedAt()
  );
}
```

---

### `AdminUserController.java` — thêm @PatchMapping (D-05)

**Analog:** `sources/backend/order-service/web/AdminOrderController.java`

**Existing @PatchMapping pattern** (AdminOrderController lines 57–60):
```java
@PatchMapping("/{id}/state")
public ApiResponse<Object> updateState(@PathVariable String id, @Valid @RequestBody OrderStateRequest request) {
  return ApiResponse.of(200, "Admin order state updated", orderCrudService.updateOrderState(id, request));
}
```

**Thêm vào AdminUserController** (không dùng @Valid vì tất cả fields nullable):
```java
import com.ptit.htpt.userservice.service.UserCrudService.AdminUserPatchRequest;
import org.springframework.web.bind.annotation.PatchMapping;

// Thêm import PatchMapping (đã có trong AdminOrderController)

@PatchMapping("/{id}")
public ApiResponse<Object> patchUser(
    @PathVariable String id,
    @RequestBody AdminUserPatchRequest request
) {
  return ApiResponse.of(200, "Admin user patched", userCrudService.patchUser(id, request));
}
```

**Existing DELETE pattern** (AdminUserController lines 54–58) để tham khảo ApiResponse.of:
```java
@DeleteMapping("/{id}")
public ApiResponse<Map<String, Object>> deleteUser(@PathVariable String id) {
  userCrudService.deleteUser(id);
  return ApiResponse.of(200, "Admin user soft deleted", Map.of("id", id, "deleted", true));
}
```

---

### `UserCrudService.java` — thêm patchUser + AdminUserPatchRequest (D-05)

**Analog:** Chính file `sources/backend/user-service/service/UserCrudService.java`

**Existing loadUser + updateUser pattern** (lines 71–79 + 87–90):
```java
public UserDto updateUser(String id, UserUpsertRequest request) {
  UserEntity current = loadUser(id);
  current.update(request.username(), request.email(),
      request.roles() == null || request.roles().isBlank() ? current.roles() : request.roles());
  if (request.passwordHash() != null && !request.passwordHash().isBlank()) {
    current.changePasswordHash(request.passwordHash());
  }
  return UserMapper.toDto(userRepo.save(current));
}

private UserEntity loadUser(String id) {
  return userRepo.findById(id)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
}
```

**Thêm AdminUserPatchRequest record + patchUser method:**
```java
public record AdminUserPatchRequest(
    String fullName,   // nullable
    String phone,      // nullable
    String roles       // nullable
) {}

public UserDto patchUser(String id, AdminUserPatchRequest request) {
  UserEntity user = loadUser(id);
  if (request.fullName() != null) user.setFullName(request.fullName());
  if (request.phone() != null) user.setPhone(request.phone());
  if (request.roles() != null && !request.roles().isBlank()) user.setRoles(request.roles());
  user.touch();
  return UserMapper.toDto(userRepo.save(user));
}
```

**Note:** UserEntity cần thêm `setRoles()` method. Pattern tương tự `setFullName()` / `setPhone()` (xem UserEntity pattern assignment trên). Khác với `update()` (thay đổi nhiều fields cùng lúc), `setRoles()` chỉ update 1 field.

---

### `services/products.ts` — thêm admin CRUD functions (D-06, D-07)

**Analog:** Chính file `sources/frontend/src/services/products.ts`

**Existing listProducts import + qs pattern** (lines 1–44):
```typescript
import type { paths as _ProductsPaths } from '@/types/api/products.generated';
import type { Product, Category, PaginatedResponse } from '@/types';
import { httpGet } from './http';

export interface ListProductsParams {
  page?: number;
  size?: number;
  sort?: string;
  categoryId?: string;
  keyword?: string;
}

export function listProducts(params?: ListProductsParams): Promise<PaginatedResponse<Product>> {
  const qs = new URLSearchParams();
  if (params?.page !== undefined) qs.set('page', String(params.page));
  if (params?.size !== undefined) qs.set('size', String(params.size));
  if (params?.sort)               qs.set('sort', params.sort);
  if (params?.categoryId)         qs.set('categoryId', params.categoryId);
  if (params?.keyword)            qs.set('keyword', params.keyword);
  const suffix = qs.toString() ? `?${qs}` : '';
  return httpGet<PaginatedResponse<Product>>(`/api/products${suffix}`);
}
```

**Thêm imports + admin functions vào cùng file:**
```typescript
import { httpGet, httpPost, httpPut, httpDelete } from './http';

// Admin product CRUD — gateway path: /api/products/admin → /admin/products
export function listAdminProducts(params?: ListProductsParams): Promise<PaginatedResponse<Product>> {
  const qs = new URLSearchParams();
  if (params?.page !== undefined) qs.set('page', String(params.page));
  if (params?.size !== undefined) qs.set('size', String(params.size));
  if (params?.sort)               qs.set('sort', params.sort);
  if (params?.keyword)            qs.set('keyword', params.keyword);
  const suffix = qs.toString() ? `?${qs}` : '';
  return httpGet<PaginatedResponse<Product>>(`/api/products/admin${suffix}`);
}

export interface ProductUpsertBody {
  name: string;
  slug?: string;          // optional — planner quyết auto-gen
  categoryId: string;
  price: number;
  status: string;
  brand?: string;
  thumbnailUrl?: string;
  shortDescription?: string;
  originalPrice?: number;
  stock?: number;
}

export function createProduct(body: ProductUpsertBody): Promise<Product> {
  return httpPost<Product>(`/api/products/admin`, body);
}

export function updateProduct(id: string, body: ProductUpsertBody): Promise<Product> {
  return httpPut<Product>(`/api/products/admin/${encodeURIComponent(id)}`, body);
}

export function deleteProduct(id: string): Promise<void> {
  return httpDelete<void>(`/api/products/admin/${encodeURIComponent(id)}`);
}

export function listAdminCategories(): Promise<PaginatedResponse<Category>> {
  return httpGet<PaginatedResponse<Category>>(`/api/products/admin/categories`);
}
```

**Gateway path mapping:**
- `POST /api/products/admin` → seg="" → `/admin/products` ✓
- `PUT /api/products/admin/{id}` → seg="{id}" → `/admin/products/{id}` ✓
- `GET /api/products/admin/categories` → seg="categories" → `/admin/products/categories` ✓

---

### `services/orders.ts` — thêm admin functions (D-08)

**Analog:** Chính file `sources/frontend/src/services/orders.ts`

**Existing pattern** (lines 1–50):
```typescript
import { httpGet, httpPost } from './http';

export function getOrderById(id: string): Promise<Order> {
  return httpGet<Order>(`/api/orders/orders/${encodeURIComponent(id)}`);
}
```

**Thêm vào file:**
```typescript
import { httpGet, httpPost, httpPatch } from './http';

// Admin order functions — gateway path: /api/orders/admin → /admin/orders
export function listAdminOrders(params?: ListOrdersParams): Promise<PaginatedResponse<Order>> {
  const qs = new URLSearchParams();
  if (params?.page !== undefined) qs.set('page', String(params.page));
  if (params?.size !== undefined) qs.set('size', String(params.size));
  if (params?.sort)               qs.set('sort', params.sort);
  const suffix = qs.toString() ? `?${qs}` : '';
  return httpGet<PaginatedResponse<Order>>(`/api/orders/admin${suffix}`);
}

export function getAdminOrderById(id: string): Promise<Order> {
  return httpGet<Order>(`/api/orders/admin/${encodeURIComponent(id)}`);
}

export function updateOrderState(id: string, status: string): Promise<Order> {
  return httpPatch<Order>(`/api/orders/admin/${encodeURIComponent(id)}/state`, { status });
}
```

---

### `services/users.ts` — NEW file (admin user functions) (D-09, D-10)

**Analog:** `sources/frontend/src/services/orders.ts` (cùng pattern httpGet/httpPatch/httpDelete)

**Full file pattern:**
```typescript
/**
 * User service API (admin scope) — listAdminUsers, patchAdminUser, deleteAdminUser.
 * Gateway path: /api/users/admin → /admin/users
 * Reuses http.ts Bearer auto-attach.
 */
import type { User, PaginatedResponse } from '@/types';
import { httpGet, httpPatch, httpDelete } from './http';

export interface ListUsersParams {
  page?: number;
  size?: number;
  sort?: string;
}

export interface AdminUserPatchBody {
  fullName?: string;
  phone?: string;
  roles?: string;
}

export function listAdminUsers(params?: ListUsersParams): Promise<PaginatedResponse<User>> {
  const qs = new URLSearchParams();
  if (params?.page !== undefined) qs.set('page', String(params.page));
  if (params?.size !== undefined) qs.set('size', String(params.size));
  if (params?.sort)               qs.set('sort', params.sort);
  const suffix = qs.toString() ? `?${qs}` : '';
  return httpGet<PaginatedResponse<User>>(`/api/users/admin${suffix}`);
}

export function patchAdminUser(id: string, body: AdminUserPatchBody): Promise<User> {
  return httpPatch<User>(`/api/users/admin/${encodeURIComponent(id)}`, body);
}

export function deleteAdminUser(id: string): Promise<void> {
  return httpDelete<void>(`/api/users/admin/${encodeURIComponent(id)}`);
}
```

---

### `admin/products/page.tsx` — wire real API + ProductUpsertModal (D-06, D-07)

**Analog:** Chính file `sources/frontend/src/app/admin/products/page.tsx` (xem toàn bộ file đã đọc)

**Existing import + state pattern** (lines 1–19):
```typescript
'use client';
import React, { useState } from 'react';
import styles from './page.module.css';
import Button from '@/components/ui/Button/Button';
import Input from '@/components/ui/Input/Input';
import Badge from '@/components/ui/Badge/Badge';
import type { Product } from '@/types';

export default function AdminProductsPage() {
  const [products, setProducts] = useState(_stubProducts);
  const [showAddModal, setShowAddModal] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<string | null>(null);
  const [search, setSearch] = useState('');
```

**Wire pattern — thêm useEffect + useCallback (từ search/page.tsx lines 40–60):**
```typescript
import { useCallback, useEffect } from 'react';
import { useToast } from '@/components/ui/Toast/Toast';
import { listAdminProducts, createProduct, updateProduct, deleteProduct, listAdminCategories } from '@/services/products';

const { showToast } = useToast();
const [loading, setLoading] = useState(false);
const [failed, setFailed] = useState(false);
const [editTarget, setEditTarget] = useState<Product | null>(null);  // null = add mode

const load = useCallback(async () => {
  setLoading(true);
  setFailed(false);
  try {
    const resp = await listAdminProducts();
    setProducts(resp?.content ?? []);
  } catch {
    setFailed(true);
  } finally {
    setLoading(false);
  }
}, []);

useEffect(() => { load(); }, [load]);
```

**Modal: reuse existing `.overlay` + `.modal` pattern** (lines 83–110):
```typescript
// Add/Edit mode — dùng editTarget state:
// editTarget === null → mode add; editTarget !== null → mode edit với pre-fill
{(showAddModal || editTarget !== null) && (
  <div className={styles.overlay} onClick={closeModal}>
    <div className={styles.modal} onClick={e => e.stopPropagation()}>
      <div className={styles.modalHeader}>
        <h3>{editTarget ? 'Chỉnh sửa sản phẩm' : 'Thêm sản phẩm mới'}</h3>
        <button className={styles.closeBtn} onClick={closeModal}>✕</button>
      </div>
      {/* form với các fields per UI-SPEC */}
    </div>
  </div>
)}
```

**Category select — inline style pattern** (từ admin/orders/page.tsx lines 96–99):
```typescript
<select
  value={formData.categoryId}
  onChange={e => setFormData(prev => ({ ...prev, categoryId: e.target.value }))}
  style={{
    width: '100%',
    padding: 'var(--space-3)',
    borderRadius: 'var(--radius-lg)',
    border: '1.5px solid rgba(195,198,214,0.2)',
    fontSize: 'var(--text-body-md)',
    fontFamily: 'var(--font-family-body)',
    background: 'var(--surface-container-lowest)',
    cursor: 'pointer'
  }}
>
  {loadingCategories ? <option disabled>Đang tải danh mục...</option> : null}
  {categories.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
</select>
```

**Toast pattern:**
```typescript
// Sau createProduct thành công:
showToast('Sản phẩm đã được thêm thành công', 'success');
// Sau createProduct thất bại:
showToast('Không thể thêm sản phẩm. Vui lòng thử lại', 'error');
```

---

### `admin/orders/page.tsx` — wire real API + router.push (D-08)

**Analog:** Chính file `sources/frontend/src/app/admin/orders/page.tsx`

**Thay đổi chính:** (1) replace `_stubOrders` bằng real fetch, (2) replace inline modal bằng `router.push`.

**useRouter pattern** (từ Next.js App Router):
```typescript
import { useRouter } from 'next/navigation';
const router = useRouter();

// Click row → navigate
<button className={styles.actionBtn} onClick={() => router.push(`/admin/orders/${o.id}`)}>📋</button>
```

**useEffect + load pattern** (copy từ search/page.tsx):
```typescript
import { useCallback, useEffect } from 'react';
import { useToast } from '@/components/ui/Toast/Toast';
import { listAdminOrders } from '@/services/orders';

const load = useCallback(async () => {
  setLoading(true);
  setFailed(false);
  try {
    const resp = await listAdminOrders();
    setOrders(resp?.content ?? []);
  } catch {
    setFailed(true);
  } finally {
    setLoading(false);
  }
}, []);

useEffect(() => { load(); }, [load]);
```

**Loading skeleton pattern** (từ search/page.tsx lines 91–94):
```typescript
{loading ? (
  [...Array(5)].map((_, i) => (
    <tr key={i}><td colSpan={7}><div className="skeleton" style={{ height: 60, borderRadius: 'var(--radius-md)' }} /></td></tr>
  ))
) : failed ? (
  <tr><td colSpan={7}><RetrySection onRetry={load} loading={loading} /></td></tr>
) : orders.length === 0 ? (
  // empty state
) : orders.map(...)}
```

---

### `admin/orders/[id]/page.tsx` — NEW detail page (D-08)

**Analog:** `sources/frontend/src/app/search/page.tsx` (useCallback load, loading/failed state pattern)

**Full file pattern:**
```typescript
'use client';
import React, { useCallback, useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import styles from '../../../products/page.module.css';  // reuse admin CSS
import Button from '@/components/ui/Button/Button';
import Badge from '@/components/ui/Badge/Badge';
import RetrySection from '@/components/ui/RetrySection/RetrySection';
import { useToast } from '@/components/ui/Toast/Toast';
import { getAdminOrderById, updateOrderState } from '@/services/orders';
import { formatPrice } from '@/services/api';
import type { Order } from '@/types';

export default function AdminOrderDetailPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const { showToast } = useToast();

  const [order, setOrder] = useState<Order | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [newStatus, setNewStatus] = useState('');
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setFailed(false);
    try {
      const data = await getAdminOrderById(id);
      setOrder(data);
      setNewStatus(data.orderStatus);
    } catch {
      setFailed(true);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => { load(); }, [load]);

  // ... render với layout per UI-SPEC
}
```

**useParams narrow pattern** (per RESEARCH.md Pitfall 5):
```typescript
// Narrow về string để tránh string | string[] TypeScript error
const { id } = useParams<{ id: string }>();
```

**Status update handler** (copy pattern từ updateProduct trong products page):
```typescript
const handleUpdateStatus = async () => {
  if (!order || newStatus === order.orderStatus) return;
  setSaving(true);
  try {
    await updateOrderState(order.id, newStatus);
    showToast('Trạng thái đơn hàng đã được cập nhật', 'success');
    await load();
  } catch {
    showToast('Không thể cập nhật trạng thái. Vui lòng thử lại', 'error');
  } finally {
    setSaving(false);
  }
};
```

**Status select style** (copy từ admin/orders/page.tsx lines 96–99):
```typescript
<select
  value={newStatus}
  onChange={e => setNewStatus(e.target.value)}
  style={{ width: '100%', padding: 'var(--space-3)', borderRadius: 'var(--radius-lg)', border: '1.5px solid rgba(195,198,214,0.2)', fontSize: 'var(--text-body-md)', fontFamily: 'var(--font-family-body)', background: 'var(--surface-container-lowest)' }}
>
  {statusOptions.map(s => <option key={s} value={s}>{statusLabels[s]}</option>)}
</select>
```

---

### `admin/users/page.tsx` — wire real API + UserEditModal (D-09, D-10)

**Analog:** `sources/frontend/src/app/admin/products/page.tsx` (modal pattern) + `sources/frontend/src/app/admin/users/page.tsx` (hiện tại — cấu trúc bảng)

**Wire pattern:**
```typescript
import { useCallback, useEffect } from 'react';
import { useToast } from '@/components/ui/Toast/Toast';
import { listAdminUsers, patchAdminUser, deleteAdminUser } from '@/services/users';
import type { User } from '@/types';

const [editTarget, setEditTarget] = useState<User | null>(null);

const load = useCallback(async () => {
  setLoading(true);
  setFailed(false);
  try {
    const resp = await listAdminUsers();
    setUsers(resp?.content ?? []);
  } catch {
    setFailed(true);
  } finally {
    setLoading(false);
  }
}, []);
```

**fullName fallback pattern** (per D-09):
```typescript
// Column "Họ tên": fullName nếu có, fallback username
<td className={styles.tdBold}>
  {u.fullName && u.fullName.trim() ? u.fullName : u.username}
</td>
```

**Role badge pattern** (roles string "ROLE_ADMIN" / "ROLE_CUSTOMER"):
```typescript
// Thay u.role === 'ADMIN' bằng u.roles check
<Badge variant={u.roles === 'ROLE_ADMIN' ? 'hot' : 'default'}>
  {u.roles === 'ROLE_ADMIN' ? 'Admin' : 'Khách hàng'}
</Badge>
```

**UserEditModal — inline trong file, pattern từ products page overlay:**
```typescript
{editTarget && (
  <div className={styles.overlay} onClick={() => setEditTarget(null)}>
    <div className={styles.modal} onClick={e => e.stopPropagation()}>
      <div className={styles.modalHeader}>
        <h3>Chỉnh sửa tài khoản</h3>
        <button className={styles.closeBtn} onClick={() => setEditTarget(null)}>✕</button>
      </div>
      <form className={styles.modalForm} onSubmit={handleSaveUser}>
        <Input label="Họ và tên" placeholder="Nguyễn Văn A" value={editForm.fullName ?? ''} onChange={...} fullWidth />
        <Input label="Số điện thoại" placeholder="0901 234 567" value={editForm.phone ?? ''} onChange={...} fullWidth />
        <div>
          <label>Vai trò</label>
          <select value={editForm.roles} onChange={...} style={...inline style từ orders page...}>
            <option value="ROLE_CUSTOMER">Khách hàng</option>
            <option value="ROLE_ADMIN">Quản trị viên</option>
          </select>
        </div>
        <div className={styles.modalActions}>
          <Button variant="secondary" type="button" onClick={() => setEditTarget(null)}>Hủy</Button>
          <Button type="submit" loading={saving}>Lưu thay đổi</Button>
        </div>
      </form>
    </div>
  </div>
)}
```

---

### `admin/layout.tsx` — thêm ToastProvider wrap

**Analog:** `sources/frontend/src/components/ui/Toast/Toast.tsx` (ToastProvider export)

**Existing layout pattern** (lines 1–72 — hiện CHƯA có ToastProvider):
```typescript
export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className={styles.adminLayout}>
      {/* sidebar + main */}
      <main className={styles.mainContent}>{children}</main>
    </div>
  );
}
```

**Thêm ToastProvider wrap:**
```typescript
import { ToastProvider } from '@/components/ui/Toast/Toast';

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return (
    <ToastProvider>
      <div className={styles.adminLayout}>
        {/* sidebar + main — không thay đổi */}
        <main className={styles.mainContent}>{children}</main>
      </div>
    </ToastProvider>
  );
}
```

---

## Shared Patterns

### Pattern: `ApiResponse.of(status, message, data)` — tất cả controllers
**Source:** `sources/backend/product-service/web/AdminProductController.java` (lines 43–45) + `sources/backend/order-service/web/AdminOrderController.java` (lines 43–44)
```java
// Pattern nhất quán — 3 args
return ApiResponse.of(200, "Admin user patched", userCrudService.patchUser(id, request));
return ApiResponse.of(201, "Admin product created", productCrudService.createProduct(request));
return ApiResponse.of(200, "Admin product soft deleted", Map.of("id", id, "deleted", true));
```
**Apply to:** `AdminUserController.java` (method patchUser mới)

### Pattern: Stream filter + paginate (in-memory)
**Source:** `sources/backend/product-service/service/ProductCrudService.java` (lines 31–43) + `sources/backend/user-service/service/UserCrudService.java` (lines 40–44)
```java
List<EntityType> all = repo.findAll().stream()
    .filter(e -> includeDeleted || !e.deleted())
    .sorted(comparator(sort))
    .toList();
return paginate(all, page, size);
```
**Apply to:** `ProductCrudService.listProducts()` (thêm keyword filter vào chain này)

### Pattern: loadEntity helper private method
**Source:** `sources/backend/user-service/service/UserCrudService.java` (lines 87–90)
```java
private UserEntity loadUser(String id) {
  return userRepo.findById(id)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
}
```
**Apply to:** `UserCrudService.patchUser()` (dùng `loadUser(id)` đã có)

### Pattern: useCallback + useEffect async load
**Source:** `sources/frontend/src/app/search/page.tsx` (lines 40–64)
```typescript
const load = useCallback(async (/* params */) => {
  setLoading(true);
  setFailed(false);
  try {
    const resp = await serviceCall(params);
    setData(resp?.content ?? []);
  } catch {
    setFailed(true);
  } finally {
    setLoading(false);
  }
}, [/* deps */]);

useEffect(() => { load(); }, [load]);
```
**Apply to:** tất cả admin pages (products, orders, users) và orders detail page

### Pattern: Modal overlay + stopPropagation
**Source:** `sources/frontend/src/app/admin/products/page.tsx` (lines 83–110)
```typescript
{showModal && (
  <div className={styles.overlay} onClick={closeModal}>
    <div className={styles.modal} onClick={e => e.stopPropagation()}>
      {/* content */}
    </div>
  </div>
)}
```
**Apply to:** ProductUpsertModal (edit mode mới), UserEditModal (mới)

### Pattern: Confirm delete modal
**Source:** `sources/frontend/src/app/admin/products/page.tsx` (lines 112–129) + `sources/frontend/src/app/admin/users/page.tsx` (lines 52–68)
```typescript
{deleteTarget && (
  <div className={styles.overlay} onClick={() => setDeleteTarget(null)}>
    <div className={styles.confirmModal} onClick={e => e.stopPropagation()}>
      <div className={styles.confirmIcon}>{/* error SVG */}</div>
      <h3 className={styles.confirmTitle}>Xác nhận xóa</h3>
      <p className={styles.confirmDesc}>... không thể hoàn tác.</p>
      <div className={styles.confirmActions}>
        <Button variant="secondary" onClick={() => setDeleteTarget(null)}>Hủy</Button>
        <Button variant="danger" onClick={handleDelete}>Xóa</Button>
      </div>
    </div>
  </div>
)}
```
**Apply to:** products page (giữ nguyên), users page (giữ nguyên pattern)

### Pattern: useToast — thay alert()
**Source:** `sources/frontend/src/components/ui/Toast/Toast.tsx` (lines 12–18)
```typescript
export const useToast = () => useContext(ToastContext);
// Trong component:
const { showToast } = useToast();
showToast('message', 'success' | 'error' | 'info');
```
**Apply to:** Tất cả admin pages — thay `alert()` trong mock form submits

### Pattern: skeleton loading row
**Source:** `sources/frontend/src/app/search/page.tsx` (lines 91–94)
```typescript
[...Array(5)].map((_, i) => (
  <div key={i} className="skeleton" style={{ height: 60, borderRadius: 'var(--radius-md)' }} />
))
```
**Apply to:** Loading state trong admin list pages (products, orders, users table)

### Pattern: RetrySection on fetch failure
**Source:** `sources/frontend/src/app/search/page.tsx` (line 97) + `sources/frontend/src/components/ui/RetrySection/RetrySection.tsx`
```typescript
<RetrySection onRetry={() => load()} loading={loading} />
```
**Apply to:** Error state trong admin list pages + orders detail page

---

## No Analog Found

Không có file nào trong Phase 7 không có analog trong codebase. Tất cả patterns đều có thể copy từ code hiện tại.

---

## Critical Implementation Notes

### Note 1: Gateway route path mapping — FE call convention

**Hiện tại:** `services/products.ts` gọi `GET /api/products` (không có trailing `/products` suffix sau prefix). Tương tự, admin functions phải gọi `GET /api/products/admin` (không phải `/api/products/admin/products`).

Gateway route với two-route pattern:
- `GET /api/products/admin` → route `product-service-admin-base` → `/admin/products` ✓
- `POST /api/products/admin` → route `product-service-admin-base` → `/admin/products` ✓
- `PUT /api/products/admin/{id}` → route `product-service-admin` → seg=`{id}` → `/admin/products/{id}` ✓
- `GET /api/products/admin/categories` → route `product-service-admin` → seg=`categories` → `/admin/products/categories` ✓

### Note 2: Product-service Flyway version numbering

`db/migration/` có V1; `db/seed-dev/` có V2. Cần kiểm tra `product-service/application.yml` Flyway `locations` config để xác định version tiếp theo. Nếu locations chỉ scan `db/migration/`, next là V2; nếu scan cả 2, next là V3. Planner phải đọc file config trước khi đặt tên migration file.

### Note 3: `username` field trong Order response

`Order` interface trong `types/index.ts` có `userId` (string UUID) nhưng không có `username`. Admin orders table cần hiển thị "Khách hàng" — nếu backend `OrderDto` chỉ trả `userId`, FE sẽ hiển thị partial UUID (fallback). Planner phải đọc `OrderCrudService.java` để xác nhận shape thực tế của `OrderResponse`.

### Note 4: roles string format

Backend V2 seed lưu `roles='ADMIN'` (không có prefix `ROLE_`). CONTEXT.md D-09 và UI-SPEC dùng `ROLE_ADMIN`. Admin PATCH endpoint sẽ set `roles` theo format FE gửi. Planner phải quyết định format chuẩn và enforce nhất quán — khuyến nghị migrate về `ROLE_ADMIN`/`ROLE_CUSTOMER` khi patchUser set roles.

---

## Metadata

**Analog search scope:** `sources/backend/` và `sources/frontend/src/`
**Files scanned:** 19 files đọc trực tiếp
**Pattern extraction date:** 2026-04-26
