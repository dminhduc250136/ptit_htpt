---
phase: 07-search-admin-real-data
reviewed: 2026-04-26T07:00:00Z
depth: standard
files_reviewed: 20
files_reviewed_list:
  - sources/backend/api-gateway/src/main/resources/application.yml
  - sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/domain/ProductEntity.java
  - sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/service/ProductCrudService.java
  - sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/web/ProductController.java
  - sources/backend/product-service/src/main/resources/db/migration/V2__add_product_extended_fields.sql
  - sources/backend/product-service/src/test/java/com/ptit/htpt/productservice/web/ProductKeywordSearchTest.java
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/domain/UserDto.java
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/domain/UserEntity.java
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/domain/UserMapper.java
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/service/UserCrudService.java
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/AdminUserController.java
  - sources/backend/user-service/src/main/resources/db/migration/V2__add_fullname_phone.sql
  - sources/frontend/src/app/admin/layout.tsx
  - sources/frontend/src/app/admin/orders/[id]/page.tsx
  - sources/frontend/src/app/admin/orders/page.tsx
  - sources/frontend/src/app/admin/products/page.tsx
  - sources/frontend/src/app/admin/users/page.tsx
  - sources/frontend/src/services/orders.ts
  - sources/frontend/src/services/products.ts
  - sources/frontend/src/services/users.ts
findings:
  critical: 0
  warning: 6
  info: 5
  total: 11
status: issues_found
---

# Phase 07: Báo cáo Code Review

**Reviewed:** 2026-04-26T07:00:00Z
**Depth:** standard
**Files Reviewed:** 20
**Status:** issues_found

## Tóm tắt

Phase 7 triển khai keyword search cho product-service (D-02), mở rộng schema product/user với các field nullable (D-03/D-04), và xây dựng admin UI (products, orders, users) kết nối dữ liệu thật qua API gateway.

Codebase nhìn chung có cấu trúc tốt, boundary rõ ràng (entity → DTO → controller), convention nhất quán. Không phát hiện lỗ hổng bảo mật nghiêm trọng. Có **6 warning** cần sửa trước khi merge — đáng chú ý nhất là:
- Keyword search thực hiện `findAll()` rồi filter in-memory (không scale khi dataset lớn, và có thể bị `@SQLRestriction` che mất deleted records cần xem với `includeDeleted=true`).
- `getProductBySlug` ở frontend fetch 50 bản ghi rồi filter client-side, trong khi backend đã có endpoint `/products/slug/{slug}`.
- `patchUser` gọi `user.touch()` ngay sau khi đã set từng field (mỗi setter đã gọi `updatedAt = Instant.now()`), gây thêm một lần update thừa không cần thiết.

---

## Warnings

### WR-01: Keyword search `findAll()` in-memory không hiệu quả và `includeDeleted=true` không có tác dụng

**File:** `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/service/ProductCrudService.java:40-45`

**Issue:** `listProducts` gọi `productRepo.findAll()` để load toàn bộ bảng rồi filter bằng Java stream. Với `includeDeleted=true` (ví dụ từ admin endpoint), `@SQLRestriction("deleted = false")` ở entity vẫn được áp dụng ở SQL layer — tức là bản ghi đã xóa mềm luôn bị loại trừ, tham số `includeDeleted` không có tác dụng thực tế. Ngoài ra khi dữ liệu lớn, load toàn bộ bảng vào RAM rồi filter là antipattern.

**Fix:** Đưa filter vào repository query. Thêm method vào `ProductRepository`:
```java
// Tìm kiếm keyword — @SQLRestriction áp dụng tự động (chỉ non-deleted)
List<ProductEntity> findByNameContainingIgnoreCase(String keyword);

// Hoặc dùng @Query cho trường hợp admin cần deleted records:
@Query("SELECT p FROM ProductEntity p WHERE " +
       "(:includeDeleted = true OR p.deleted = false) AND " +
       "(:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')))")
List<ProductEntity> search(@Param("keyword") String keyword,
                            @Param("includeDeleted") boolean includeDeleted);
```
Sau đó `listProducts` gọi `productRepo.search(keyword, includeDeleted)` thay vì `findAll()`.

---

### WR-02: `getProductBySlug` ở frontend bỏ qua endpoint có sẵn của backend

**File:** `sources/frontend/src/services/products.ts:57-60`

**Issue:** Frontend fetch `/api/products?size=50` và filter client-side theo `slug`. Backend (`ProductController`) đã có endpoint `GET /products/slug/{slug}` trỏ về `getProductBySlug`. Gateway rule `product-service` (`Path=/api/products/**`) sẽ forward `/api/products/slug/{slug}` đến `/products/slug/{slug}` đúng chuẩn. Cách hiện tại tốn băng thông, có thể trả kết quả sai nếu sản phẩm không nằm trong 50 bản ghi đầu.

**Fix:**
```typescript
export async function getProductBySlug(slug: string): Promise<Product | null> {
  try {
    return await httpGet<Product>(`/api/products/slug/${encodeURIComponent(slug)}`);
  } catch {
    return null;
  }
}
```
Bỏ hoàn toàn logic fetch + client-side filter.

---

### WR-03: `patchUser` gọi `user.touch()` thừa sau khi setter đã cập nhật `updatedAt`

**File:** `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/service/UserCrudService.java:137-143`

**Issue:** `setFullName`, `setPhone`, `setRoles` mỗi method đều đã thực hiện `this.updatedAt = Instant.now()`. Dòng `user.touch()` ở cuối `patchUser` ghi đè `updatedAt` một lần nữa bằng một timestamp khác sau khi các setter đã set — không gây lỗi nghiêm trọng nhưng tạo ra timestamp không chính xác (thời điểm sau cùng, không phải thời điểm mutation thực sự xảy ra) và là dead call nếu không có field nào được set.

**Fix:** Xóa dòng `user.touch()` trong `patchUser`:
```java
public UserDto patchUser(String id, AdminUserPatchRequest request) {
    UserEntity user = loadUser(id);
    if (request.fullName() != null) user.setFullName(request.fullName());
    if (request.phone()    != null) user.setPhone(request.phone());
    if (request.roles() != null && !request.roles().isBlank()) user.setRoles(request.roles());
    // Bỏ user.touch() — các setter đã cập nhật updatedAt
    return UserMapper.toDto(userRepo.save(user));
}
```

---

### WR-04: Route collision tiềm ẩn — `/api/products/admin` bị `product-service` catch trước `product-service-admin`

**File:** `sources/backend/api-gateway/src/main/resources/application.yml:58-76`

**Issue:** Route `product-service` có predicate `Path=/api/products/**` — pattern này match cả `/api/products/admin` và `/api/products/admin/**`. Trong Spring Cloud Gateway, route được đánh giá theo thứ tự khai báo. Hiện tại `product-service-admin-base` (dòng 53) và `product-service-admin` (dòng 58) được khai báo **trước** `product-service-base` và `product-service` (dòng 64-76), nên thứ tự này đúng. Tuy nhiên không có order tường minh — nếu thứ tự routes bị hoán đổi khi refactor, admin traffic sẽ bị forward nhầm sang `/products/admin` thay vì `/admin/products`.

**Fix:** Thêm thuộc tính `order` tường minh để đảm bảo admin routes được ưu tiên:
```yaml
- id: product-service-admin-base
  uri: http://product-service:8080
  order: 1
  predicates:
    - Path=/api/products/admin
  ...
- id: product-service-admin
  uri: http://product-service:8080
  order: 2
  predicates:
    - Path=/api/products/admin/**
  ...
- id: product-service
  uri: http://product-service:8080
  order: 10
  predicates:
    - Path=/api/products/**
  ...
```
Áp dụng pattern tương tự cho user-service và order-service routes.

---

### WR-05: `AdminProduct` interface ở frontend thiếu field `category` — hiển thị `categoryId` thay vì tên danh mục

**File:** `sources/frontend/src/app/admin/products/page.tsx:17-31` và dòng `248`

**Issue:** `listAdminProducts` gọi `/api/products/admin` — backend trả về `ProductResponse` (từ `ProductCrudService.toResponse`) có field `category: CategoryRef { id, name, slug }`. Nhưng `AdminProduct` interface ở FE chỉ map `categoryId?: string` (không map `category`). Kết quả: cột "Danh mục" trong bảng hiển thị raw `categoryId` UUID thay vì tên danh mục có nghĩa.

**Fix:** Cập nhật interface và render:
```typescript
interface AdminProduct {
  // ... existing fields
  category?: { id: string; name: string; slug: string }; // thêm field này
  categoryId?: string; // giữ lại cho backward compat
}
// Trong render:
<td>{p.category?.name ?? p.categoryId ?? '—'}</td>
```

---

### WR-06: `ProductKeywordSearchTest` — `@BeforeAll` gọi `postgres.start()` thủ công nhưng `@Container` đã tự quản lý lifecycle

**File:** `sources/backend/product-service/src/test/java/com/ptit/htpt/productservice/web/ProductKeywordSearchTest.java:51-57`

**Issue:** Khi dùng `@Testcontainers` + `@Container` trên static field, Testcontainers JUnit 5 extension tự động gọi `start()` trước `@BeforeAll`. Gọi `postgres.start()` thủ công trong `@BeforeAll` là idempotent (container đã chạy rồi nên lần gọi này là no-op), nhưng nếu schema creation trong `@BeforeAll` chạy trước container thực sự khởi động (timing edge case với JUnit extension ordering), `initSchema` sẽ throw `IllegalStateException`. Đảm bảo thứ tự đúng bằng cách loại bỏ lời gọi `start()` thủ công.

**Fix:**
```java
@BeforeAll
static void initSchema() throws Exception {
    // Bỏ postgres.start() — @Testcontainers/@Container đã handle lifecycle
    try (Connection conn = postgres.createConnection("");
         Statement stmt = conn.createStatement()) {
        stmt.execute("CREATE SCHEMA IF NOT EXISTS product_svc");
    }
}
```

---

## Info

### IN-01: `AdminProduct.categoryId` trong `listAdminProducts` phụ thuộc vào field không có trong `ProductResponse`

**File:** `sources/frontend/src/app/admin/products/page.tsx:22`

**Issue:** `ProductResponse` (backend) không có field `categoryId` — chỉ có object `category: CategoryRef`. `AdminProduct.categoryId` sẽ luôn là `undefined` khi nhận từ admin endpoint. Đây là info đi kèm WR-05 — cần xem xét khi fix WR-05.

---

### IN-02: `_showToast` trong `AdminOrdersPage` là unused variable

**File:** `sources/frontend/src/app/admin/orders/page.tsx:44`

**Issue:** `const { showToast: _showToast } = useToast()` — biến được alias bắt đầu bằng `_` để báo hiệu "intentionally unused", nhưng không có use case rõ ràng. Nếu page thực sự không cần toast, nên xóa destructuring này.

**Fix:** Xóa dòng 44 nếu không có kế hoạch dùng toast trong page này. Nếu có kế hoạch, rename thành `showToast` khi implement.

---

### IN-03: Magic string `"ACTIVE"`, `"INACTIVE"`, `"OUT_OF_STOCK"` lặp lại nhiều nơi không có type constraint

**File:** `sources/frontend/src/app/admin/products/page.tsx:254`, `sources/frontend/src/services/products.ts:74`

**Issue:** Status string literals xuất hiện rải rác ở cả FE service layer, page component, và backend entity. Không có shared enum/union type đảm bảo consistency.

**Fix:** Khai báo union type ở `@/types`:
```typescript
export type ProductStatus = 'ACTIVE' | 'INACTIVE' | 'OUT_OF_STOCK';
```
Dùng trong `ProductUpsertBody.status` và `AdminProduct.status`.

---

### IN-04: `AdminUserController.patchUser` không validate request body

**File:** `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/AdminUserController.java:66`

**Issue:** Comment giải thích "không dùng `@Valid` — tất cả fields nullable" là hợp lý. Nhưng nếu `roles` nhận string tùy ý (ví dụ `"SUPERADMIN"` hay SQL fragment), không có validation nào chặn. Đây là info vì project đã có quyết định defer security hardening, nhưng nên ghi nhận.

**Fix (defer Phase 8):** Thêm whitelist check trong `patchUser` service:
```java
private static final Set<String> VALID_ROLES = Set.of("USER", "ADMIN");
if (request.roles() != null && !VALID_ROLES.contains(request.roles())) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role");
}
```

---

### IN-05: `softDelete()` trong `ProductEntity` cập nhật `deleted=true` nhưng `@SQLDelete` cũng làm điều tương tự — double write khi gọi `repository.save()` sau `softDelete()`

**File:** `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/domain/ProductEntity.java:121-124`

**Issue:** `deleteProduct` trong service gọi `current.softDelete()` rồi `productRepo.save(current)`. `save()` sẽ trigger `UPDATE` thông thường (set tất cả fields). Nếu thay vào đó gọi `productRepo.delete(current)`, `@SQLDelete` sẽ fire câu UPDATE đúng của nó — nhất quán hơn và không cần method `softDelete()` trên entity. Hiện tại logic vẫn đúng nhưng có hai code path làm cùng việc.

**Fix (low priority):** Chọn một trong hai pattern và áp dụng nhất quán:
- **Pattern A:** Giữ `softDelete()` + `save()` (pattern hiện tại) — xóa `@SQLDelete`.
- **Pattern B:** Dùng `repository.delete(entity)` để `@SQLDelete` tự xử lý — xóa method `softDelete()`.

---

_Reviewed: 2026-04-26T07:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
