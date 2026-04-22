# TS-ADMIN-PRODUCT: Admin Product Management

## Tóm tắt
Impl spec cho UC-ADMIN-PRODUCT. Service: **Product** only. Admin endpoints prefix `/api/v1/admin/*`. Bao gồm: CRUD product, upload image (presigned), update stock (audit log), CRUD category, hide review. Kafka events publish for cache invalidation.

## Context Links
- BA Spec: [../ba/uc-admin-product.md](../ba/uc-admin-product.md)
- Services affected: ✅ Product | ⬜ User | ⬜ Order
- Architecture: [../architecture/services/product-service.md](../architecture/services/product-service.md)

## API Contracts

### GET /api/v1/admin/products
Admin only.

**Query**: page, size, status, categoryId, q (search), dateFrom, dateTo, sort

**Response 200** — `{ data: [...productAdminSummary], meta: {...} }` (include DRAFT/INACTIVE items unlike public)

### POST /api/v1/admin/products
Admin only.

**Request**
```json
{
  "sku": "APPLE-IP15PRO-256",
  "name": "iPhone 15 Pro 256GB",
  "slug": "iphone-15-pro-256gb",
  "brand": "Apple",
  "categoryId": "uuid",
  "description": "...",
  "price": 28990000,
  "salePrice": 26990000,
  "stock": 50,
  "images": ["users/.../1.jpg", "users/.../2.jpg"],
  "specs": { "ram": "8GB", "storage": "256GB" },
  "status": "DRAFT"
}
```

**Validation**
- `sku`: 3-50, unique, regex `^[A-Z0-9-]+$`
- `slug`: if empty → auto-gen; unique
- `price` > 0, `salePrice` < `price` if set
- `stock` >= 0
- `images`: min 1, max 10 keys (relative path)
- `categoryId`: must exist, must be leaf (no children)
- `status`: DRAFT | ACTIVE (cannot set INACTIVE/OUT_OF_STOCK via create)

**Response 201** — ProductAdminDetail

**Errors**: 400 SKU_EXISTS, SLUG_EXISTS, INVALID_SALE_PRICE, CATEGORY_NOT_LEAF, IMAGES_REQUIRED

### PATCH /api/v1/admin/products/{id}
All fields optional. SKU read-only (cannot change).

**Response 200** — Product

### PATCH /api/v1/admin/products/{id}/stock
**Request**: `{ "delta": +50, "reason": "Import batch #123" }`
**Response 200** — `{ product, stockLog }`
**Errors**: 400 STOCK_WOULD_BE_NEGATIVE, REASON_REQUIRED

### PATCH /api/v1/admin/products/{id}/status
**Request**: `{ "status": "ACTIVE", "reason": "..." }`
Allowed transitions: DRAFT↔ACTIVE, ACTIVE↔INACTIVE, INACTIVE↔ACTIVE. Not: manual → OUT_OF_STOCK (auto only).
**Response 200**

### POST /api/v1/admin/products/{id}/images
**Request**: `{ "images": [{ "key": "...", "order": 0 }, ...] }` (replace full list)
**Response 200**

### POST /api/v1/admin/upload/presign
Admin only. Generic upload presign.
**Request**: `{ "filename": "...", "contentType": "...", "size": ... }`
**Response 200** — `{ uploadUrl, key, publicUrl, expiresInSec }` (similar to user avatar)

### GET /api/v1/admin/products/{id}/stock-log
**Query**: page, size, dateFrom, dateTo
**Response 200** — paginated stock logs

### GET /api/v1/admin/categories
**Response 200** — `{ data: [...flat list] }`

### POST /api/v1/admin/categories
**Request**: `{ parentId?, name, slug?, icon?, sortOrder? }`
**Response 201**

### PATCH /api/v1/admin/categories/{id}
**Response 200**

### DELETE /api/v1/admin/categories/{id}
**Errors**: 400 CATEGORY_HAS_PRODUCTS, CATEGORY_HAS_CHILDREN

### PATCH /api/v1/admin/reviews/{id}/hide
**Request**: `{ "reason": "...", "hidden": true }`
**Response 200**

## Database Changes

### Migration V3__create_stock_log.sql (Product DB)
```sql
CREATE TABLE stock_log (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES product(id),
    delta INT NOT NULL,
    stock_after INT NOT NULL,
    reason TEXT,
    type VARCHAR(30) NOT NULL,
    actor_id UUID,
    actor_type VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_stock_log_product_date ON stock_log(product_id, created_at DESC);
```

## Event Contracts

### Publish: product.product.created
```json
{ "productId", "sku", "name", "categoryId", "price", "createdAt" }
```

### Publish: product.product.updated
```json
{ "productId", "changedFields": ["stock","price"], "updatedAt" }
```

### Publish: product.product.activated / deactivated
```json
{ "productId" }
```

### Publish: product.stock.admin_updated (internal audit)
```json
{ "productId", "delta", "stockAfter", "reason", "adminId", "at" }
```

### Consume
None from these admin endpoints.

## Sequence

Xem [architecture/02-sequence-diagrams.md section 6 (Admin Update Stock)](../architecture/02-sequence-diagrams.md#6-admin-update-stock-uc-admin-product).

## Class/Component Design

### Backend — Product Service (extend)
```java
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductController {
    @GetMapping("/products") public Page<ProductAdminSummary> list(...);
    @GetMapping("/products/{id}") public ProductAdminDetail get(...);
    @PostMapping("/products") public ProductAdminDetail create(...);
    @PatchMapping("/products/{id}") public ProductAdminDetail update(...);
    @PatchMapping("/products/{id}/stock") public StockUpdateResponse updateStock(...);
    @PatchMapping("/products/{id}/status") public ProductAdminDetail changeStatus(...);
    @PostMapping("/products/{id}/images") public ProductAdminDetail updateImages(...);
    @GetMapping("/products/{id}/stock-log") public Page<StockLogResponse> getStockLog(...);
}

@RestController
@RequestMapping("/api/v1/admin/categories")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCategoryController { ... }

@RestController
@RequestMapping("/api/v1/admin/reviews")
@PreAuthorize("hasRole('ADMIN')")
public class AdminReviewController {
    @PatchMapping("/{id}/hide") public ReviewResponse hide(...);
}

@RestController
@RequestMapping("/api/v1/admin/upload")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUploadController {
    @PostMapping("/presign") public PresignResponse presign(...);
}
```

```java
@Service
@Transactional
public class ProductAdminService {
    public Product create(CreateProductRequest req, UUID adminId);
    public Product update(UUID id, UpdateProductRequest req, UUID adminId);
    public Product changeStatus(UUID id, ProductStatus status, UUID adminId);
    public Product updateImages(UUID id, List<ImageRef> images);
    private String generateUniqueSlug(String baseName);
}

@Service
@Transactional
public class StockService {
    public StockUpdateResult updateStock(UUID productId, int delta, String reason, UUID adminId);
    // Used by admin + by Kafka listeners (reserve/commit/release)
    public void reserveStock(UUID productId, int quantity, UUID orderId);
    public void commitStock(UUID productId, int quantity, UUID orderId);
    public void releaseStock(UUID productId, int quantity, UUID orderId);
}

@Service
public class CategoryAdminService {
    public Category create(CreateCategoryRequest req);
    public Category update(UUID id, UpdateCategoryRequest req);
    public void delete(UUID id); // validate no products + no children
}
```

### Frontend (Admin)
- Pages:
  - `/admin/products` — list
  - `/admin/products/new` — wizard
  - `/admin/products/{id}` — edit
  - `/admin/categories`
- Components:
  - `AdminProductTable.tsx` with bulk select
  - `ProductWizard.tsx` (multi-step)
  - `ImageUploader.tsx` (drag-drop, reorder, set primary)
  - `SpecsEditor.tsx` (dynamic per category)
  - `StockUpdateModal.tsx`
  - `CategoryTree.tsx` (CRUD)
- API: `lib/api/admin.api.ts`
- Route guard: middleware check role=ADMIN

## Implementation Steps

### Backend
1. [ ] Migration V3 stock_log
2. [ ] Entities: StockLog
3. [ ] Repositories
4. [ ] `ProductAdminService` CRUD
5. [ ] `generateUniqueSlug` helper
6. [ ] `StockService.updateStock` transactional với stock_log insert
7. [ ] Auto-transition to OUT_OF_STOCK when stock=0, back to ACTIVE when stock>0 (trigger trong StockService)
8. [ ] `CategoryAdminService` CRUD với validation leaf + no products
9. [ ] `AdminReviewController.hide` với aggregate recompute
10. [ ] `S3PresignService` generic (shared with user avatar)
11. [ ] Controllers với `@PreAuthorize("hasRole('ADMIN')")`
12. [ ] Kafka producers for events
13. [ ] Cache eviction on update (`@CacheEvict`)
14. [ ] Unit tests
15. [ ] Integration test: create → publish → ACTIVE → stock update → OUT_OF_STOCK auto
16. [ ] OpenAPI docs

### Frontend
1. [ ] Admin API client
2. [ ] Admin layout với sidebar + role guard
3. [ ] `AdminProductTable` với filter + bulk
4. [ ] `ImageUploader` component (upload to presigned URL)
5. [ ] `SpecsEditor` dynamic per category
6. [ ] `ProductWizard` multi-step create
7. [ ] Product edit page
8. [ ] `StockUpdateModal`
9. [ ] `CategoryTree` CRUD view
10. [ ] Review moderation panel
11. [ ] E2E admin flows

## Test Strategy

### Unit
- `generateUniqueSlug` collision handling
- `StockService.updateStock` stock cannot go negative
- Status transition matrix

### Integration
- Full product lifecycle: create DRAFT → images upload → publish ACTIVE → update stock → auto OUT_OF_STOCK
- Category delete blocked if has products
- Concurrent stock update (2 admins): last-write wins với row lock

### E2E (Admin)
- Create product wizard end-to-end
- Stock update + verify log
- Category CRUD
- Hide review → disappear from public

## Edge Cases

1. **Slug collision**: auto-gen `-2`, `-3`; if admin input manual → 400 if exists.
2. **SKU uppercase**: normalize to uppercase before insert.
3. **Image order**: `images` array index = order. Re-upload changes order.
4. **Stock update via admin AND via order events concurrent**: use `SELECT ... FOR UPDATE` in StockService, all changes serialize per product.
5. **Delete category with orphan products** (in INACTIVE/DRAFT): still blocked — enforce clean tree.
6. **Status OUT_OF_STOCK auto-set vs admin manual ACTIVE**: admin can override ACTIVE even nếu stock=0 (show "Còn hàng" — admin trách nhiệm). MVP: auto OUT_OF_STOCK; admin can't override (simpler).
7. **Specs JSON schema different versions**: backlog — version field. MVP: free-form JSON.
8. **Images không tồn tại ở S3**: admin có thể nhập invalid key. BE không verify (admin trust). Rendering FE sẽ broken — monitoring alert via 404 from CDN.
9. **Bulk activate 100 items**: wrap in transaction? MVP: loop single, best-effort, return success count + failure count. Phase 2: batch.
10. **Review aggregate consistency**: hide review → recompute. Race with new review post → transaction serialize per product.
