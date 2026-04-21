# UC-ADMIN-PRODUCT: Admin — Product, Stock, Category Management

## Tóm tắt
Admin quản lý catalog: CRUD product (với status DRAFT/ACTIVE/INACTIVE), upload images (S3 presigned), update stock (với audit log), CRUD category tree (2 levels). Bulk actions + export CSV.

## Context Links
- Strategy: [../strategy/services/product-business.md](../strategy/services/product-business.md)
- Technical Spec: [../technical-spec/ts-admin-product.md](../technical-spec/ts-admin-product.md)
- Architecture: [../architecture/services/product-service.md](../architecture/services/product-service.md)

## Actors
- **Primary**: Admin (role=ADMIN)

## Preconditions
- User logged in với role=ADMIN
- Gateway đã verify và allow `/api/v1/admin/**`

---

## Flow A — Product List (Admin)

### Main Flow
1. Admin vào `/admin/products`
2. FE gọi GET /api/v1/admin/products?page=0&size=20&status=&category=&q=
3. BE trả products (all statuses, không filter status ACTIVE như public API)
4. FE render:
   - Filter bar: status (DRAFT/ACTIVE/INACTIVE/OUT_OF_STOCK), category, search keyword
   - Table với columns: Image, SKU, Name, Category, Brand, Price, Stock, Status, Last updated, Actions
   - Bulk select checkbox column
   - Pagination
5. Bulk actions: Activate, Deactivate, Export CSV

### Acceptance Criteria
- [ ] AC-A1: Table pagination 20 rows/page
- [ ] AC-A2: Filter combine (AND): status + category + keyword
- [ ] AC-A3: Search on name/SKU/brand
- [ ] AC-A4: Bulk activate/deactivate: confirm dialog, success toast

---

## Flow B — Create Product

### Main Flow
1. Admin click "Tạo sản phẩm" → `/admin/products/new`
2. Multi-step form:
   - **Step 1 — Basic info**: name (required), SKU (required, unique), slug (auto-gen, editable), brand, category, description (rich text MVP: markdown textarea)
   - **Step 2 — Pricing & Stock**: price, salePrice, stock
   - **Step 3 — Images**: upload, reorder, set primary
   - **Step 4 — Specs**: dynamic form based on category
     - Phone: ram, storage, screen_size, battery, camera_main, chip, os
     - Laptop: cpu, ram, ssd, screen_size, gpu, os, weight
     - Smartwatch: display, battery_life, water_resistance, connectivity
     - Accessory: type, compatibility, color
   - **Step 5 — Review & Save**: preview + buttons "Lưu DRAFT" / "Publish (ACTIVE)"
3. Image upload flow:
   - Click upload → request presigned URL: POST /api/v1/admin/upload/presign `{ filename, contentType }` → `{ url, key }`
   - PUT file to `url` (S3 directly)
   - Save `key` vào image array
4. Submit save:
   - FE gửi POST /api/v1/admin/products với body
   - BE validate:
     - SKU unique
     - Slug unique (auto-gen với suffix -2, -3 nếu trùng)
     - Category exists
     - `salePrice < price` nếu có
     - Images không empty
   - BE insert product
   - BE publish `ProductCreated`
   - BE trả 201
5. FE redirect `/admin/products/{id}` (edit page)

### Draft vs Active
- Save as DRAFT: không publish, không hiển thị public
- Publish (ACTIVE): hiển thị public, publish event

### Exception Flows
- **EF-B1: SKU trùng** → 400 `SKU_EXISTS`
- **EF-B2: salePrice >= price** → 400 `INVALID_SALE_PRICE`
- **EF-B3: No images** → 400 `IMAGES_REQUIRED` (at least 1)
- **EF-B4: Image > 2MB** → FE block trước upload
- **EF-B5: Category không tồn tại** → 400

### Acceptance Criteria
- [ ] AC-B1: Tạo product với đầy đủ info
- [ ] AC-B2: Auto-gen slug từ name
- [ ] AC-B3: Upload images direct-to-S3 (presigned URL)
- [ ] AC-B4: Specs form dynamic theo category

---

## Flow C — Edit Product

### Main Flow
1. Admin click row hoặc "Edit" icon → `/admin/products/{id}`
2. FE gọi GET /api/v1/admin/products/{id}
3. FE render form prefilled
4. Admin edit → click "Lưu"
5. FE gửi PATCH /api/v1/admin/products/{id} { changed fields }
6. BE:
   - Validate
   - Update
   - Publish `ProductUpdated` với `changedFields`
   - Invalidate cache `product:{id}`, `product:slug:{slug}`
7. BE trả 200
8. FE toast "Cập nhật thành công"

### Status Transitions
| From | To | Action |
|---|---|---|
| DRAFT | ACTIVE | "Publish" button |
| ACTIVE | INACTIVE | "Unpublish" button |
| INACTIVE | ACTIVE | "Publish" button |
| Any | OUT_OF_STOCK | Auto khi stock=0 (backend trigger) |

### Acceptance Criteria
- [ ] AC-C1: Edit không đổi SKU (readonly sau create)
- [ ] AC-C2: Update stock qua endpoint riêng (không qua edit form — tránh race)
- [ ] AC-C3: Publish/Unpublish với separate endpoint hoặc PATCH status field

---

## Flow D — Update Stock

### Main Flow
1. Admin vào product detail hoặc list, click "Cập nhật stock"
2. Modal:
   - Current stock: (readonly)
   - Delta: input (có thể âm hoặc dương)
   - Reason: text (required)
3. Admin submit
4. FE gửi PATCH /api/v1/admin/products/{id}/stock { delta, reason }
5. BE:
   - Transaction:
     - UPDATE product SET stock = stock + delta WHERE id AND stock + delta >= 0
     - Check rows affected = 1 (else fail — stock có thể bị âm)
     - INSERT stock_log { productId, delta, stockAfter, reason, actorId=adminId, actorType=ADMIN, type=ADMIN_UPDATE }
   - Commit
   - Invalidate cache
   - Publish `ProductUpdated` (changedFields=[stock])
   - Nếu stock = 0 → auto set status=OUT_OF_STOCK (via trigger hoặc service logic)
6. BE trả 200 { product, stockLog }
7. FE close modal, refresh

### Exception Flows
- **EF-D1: Delta tạo stock âm** → 400 `STOCK_WOULD_BE_NEGATIVE`
- **EF-D2: Reason empty** → 400 `REASON_REQUIRED`

### Acceptance Criteria
- [ ] AC-D1: Stock update atomic (transaction)
- [ ] AC-D2: Audit log mọi thay đổi
- [ ] AC-D3: Low stock alert (stock <= 5) flag in list UI

---

## Flow E — Category Management

### Main Flow (E1: List)
1. Admin vào `/admin/categories`
2. FE gọi GET /api/v1/admin/categories
3. FE render tree view với CRUD actions per node

### Main Flow (E2: Create)
1. Admin click "Thêm category" hoặc "+ Sub-category"
2. Form:
   - Parent (dropdown nếu sub, null nếu root)
   - Name (required)
   - Slug (auto-gen, editable)
   - Icon URL (optional)
   - Sort order
3. Submit POST /api/v1/admin/categories
4. BE validate:
   - Parent != null chỉ nếu tạo sub (không chaining 3+ levels)
   - Slug unique
5. BE insert, publish cache invalidation

### Main Flow (E3: Edit/Delete)
- Edit: PATCH /api/v1/admin/categories/{id}
- Delete: DELETE /api/v1/admin/categories/{id}
  - BE check: category không có products + không có sub-categories → else 400 `CATEGORY_NOT_EMPTY`

### Exception Flows
- **EF-E1: Delete category có product** → 400 `CATEGORY_HAS_PRODUCTS`
- **EF-E2: Delete parent có sub-categories** → 400 `CATEGORY_HAS_CHILDREN`
- **EF-E3: Slug trùng** → 400

### Acceptance Criteria
- [ ] AC-E1: Tree view 2 levels
- [ ] AC-E2: Delete blocked nếu còn products/sub
- [ ] AC-E3: Cache `category:tree` invalidate ngay khi CRUD

---

## Flow F — Hide Review (admin)

### Main Flow
1. Admin vào product detail, scroll reviews section
2. Hover review vi phạm → click "Hide"
3. Confirm với reason
4. FE gửi PATCH /api/v1/admin/reviews/{id}/hide { reason }
5. BE: set isHidden=true, recompute product rating/reviewCount
6. BE trả 200

### Acceptance Criteria
- [ ] AC-F1: Hidden review không show public
- [ ] AC-F2: Aggregate exclude hidden

---

## Business Rules (references)
- BR-PRODUCT-01 đến -07
- BR-INVENTORY-08: Audit log stock
- BR-REVIEW-04: Hide review

## Non-functional Requirements
- **Performance**: List admin 20 rows < 500ms (có join category)
- **Security**: Chỉ ADMIN role access, all endpoints prefix `/api/v1/admin/*`, Gateway enforce
- **Audit**: Stock changes + status changes logged
- **Bulk actions**: Transactional, rollback nếu 1 item fail

## UI Screens
- `/admin/products` — product list
- `/admin/products/new` — create wizard
- `/admin/products/{id}` — edit + stock + reviews panel
- `/admin/categories` — tree view with CRUD
- Modals: Update stock, Hide review, Delete confirm
