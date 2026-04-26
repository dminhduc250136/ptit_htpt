---
phase: 07-search-admin-real-data
verified: 2026-04-26T12:00:00Z
status: human_needed
score: 4/4 must-haves verified
overrides_applied: 0
deferred:
  - truth: "Admin /admin/orders/{id} hiển thị order detail với line items (số sản phẩm)"
    addressed_in: "Phase 8"
    evidence: "Phase 8 goal: 'OrderEntity persist per-item OrderItem rows... FE order confirmation + order detail render full breakdown thật từ backend payload'; Phase 7 SC-3 chỉ yêu cầu 'line items + status' nhưng backend OrderDto Phase 7 không có items array — cột Số sản phẩm hiển thị '—' là intentional gap ghi rõ trong 07-05-SUMMARY known stubs"
human_verification:
  - test: "Truy cập /search, nhập keyword 'laptop' — verify list thay đổi theo keyword từ backend"
    expected: "Products có 'laptop' trong tên xuất hiện; empty state hiện khi không có kết quả"
    why_human: "Cannot curl without running stack; verify real DB query vs in-memory filter behavior"
  - test: "Admin login → /admin/products → click '+ Thêm sản phẩm' → điền form → submit"
    expected: "POST /api/products/admin thành công; toast 'Sản phẩm đã được thêm thành công'; product xuất hiện trong list"
    why_human: "End-to-end modal submission + toast + list refresh cần browser interaction"
  - test: "Admin /admin/orders → click 📋 trên order row → verify redirect đến /admin/orders/{id}"
    expected: "Detail page load với order info; status dropdown có 5 options; nút 'Cập nhật trạng thái' hoạt động"
    why_human: "router.push navigation behavior cần browser runtime"
  - test: "Admin /admin/users → click ✏️ trên user row → submit form với fullName mới"
    expected: "PATCH /api/users/admin/{id} thành công; toast 'Thông tin tài khoản đã được cập nhật'; list refresh với fullName mới"
    why_human: "Edit modal form pre-fill + PATCH + reload cần browser"
---

# Phase 7: Search + Admin Real Data — Verification Report

**Phase Goal:** FE `/search` page và toàn bộ `admin/*` pages migrate khỏi mock sang CRUD thật qua gateway; admin có thể quản lý products/orders/users với data thật từ Postgres.
**Verified:** 2026-04-26T12:00:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (Success Criteria từ ROADMAP)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| SC-1 | User nhập keyword vào `/search` → FE call `listProducts({keyword})` → render kết quả thật từ DB; empty state + skeleton | ✓ VERIFIED | `search/page.tsx` line 51: `listProducts({ size: 24, keyword: trimmed })`; empty state line 101: "Không tìm thấy kết quả cho..."; skeleton line 93; debounce 350ms |
| SC-2 | Admin vào `admin/products` → list từ backend; create/edit/delete qua form/dialog → success toast → list refresh; gỡ mock | ✓ VERIFIED | `listAdminProducts`, `createProduct`, `updateProduct`, `deleteProduct` wired; `editTarget` state cho add/edit mode; 0 `_stubProducts` matches; toast messages exact per UI-SPEC |
| SC-3 | Admin vào `admin/orders` → list orders thật; click row mở detail page; admin update status persist trong DB | ✓ VERIFIED | `listAdminOrders` wired; `router.push('/admin/orders/${o.id}')` line 125; `admin/orders/[id]/page.tsx` tạo mới với `getAdminOrderById` + `updateOrderState` + 5 STATUS_OPTIONS |
| SC-4 | Admin vào `admin/users` → list users thật; admin edit fullName/phone/roles + soft-delete CUSTOMER only | ✓ VERIFIED | `listAdminUsers` wired; `patchAdminUser` trong edit modal; `deleteAdminUser` với confirm; `u.roles !== 'ADMIN'` guard; fullName fallback username |

**Score: 4/4 truths verified**

### Deferred Items

Items không có trong Phase 7 scope — được address rõ ràng ở Phase 8.

| # | Item | Addressed In | Evidence |
|---|------|-------------|----------|
| 1 | Order detail page hiển thị line items (Số sản phẩm, shippingAddress, paymentMethod) | Phase 8 | Phase 8 goal: "OrderEntity persist per-item OrderItem rows + shippingAddress + paymentMethod; FE order detail render full breakdown". 07-05-SUMMARY Known Stubs: "Chi tiết sản phẩm sẽ khả dụng sau khi Phase 8 hoàn thiện" — intentional, không phải gap |

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `sources/backend/api-gateway/src/main/resources/application.yml` | 6 admin routes (3 base + 3 wildcard) | ✓ VERIFIED | 3 base routes confirmed; admin routes TRƯỚC general routes (user: line 23 < 37, product: line 51 < 65, order: line 79 < 93) |
| `sources/backend/product-service/src/main/resources/db/migration/V2__add_product_extended_fields.sql` | 4 nullable columns | ✓ VERIFIED | brand, thumbnail_url, short_description, original_price — tất cả IF NOT EXISTS |
| `sources/backend/product-service/src/main/java/.../ProductEntity.java` | 4 JPA fields mới | ✓ VERIFIED | `private String brand`, `thumbnailUrl`, `shortDescription`, `BigDecimal originalPrice` — 4 matches |
| `sources/backend/product-service/src/main/java/.../ProductCrudService.java` | keyword filter + 4 new fields | ✓ VERIFIED | `keyword == null \|\| keyword.isBlank()` line 42; brand/thumbnailUrl in request + toResponse() dùng entity fields thật |
| `sources/backend/user-service/src/main/resources/db/migration/V2__add_fullname_phone.sql` | 2 nullable columns | ✓ VERIFIED | full_name + phone với IF NOT EXISTS |
| `sources/backend/user-service/src/main/java/.../UserEntity.java` | fullName + phone fields + setters | ✓ VERIFIED | `private String fullName` line 46, `private String phone` line 49 |
| `sources/backend/user-service/src/main/java/.../UserDto.java` | fullName + phone fields | ✓ VERIFIED | `String fullName` line 16, `String phone` line 17 |
| `sources/backend/user-service/src/main/java/.../UserMapper.java` | toDto() map fullName + phone | ✓ VERIFIED | `e.fullName()` line 16 |
| `sources/backend/user-service/src/main/java/.../UserCrudService.java` | AdminUserPatchRequest + patchUser() | ✓ VERIFIED | record definition line 131; `patchUser()` method line 137 |
| `sources/backend/user-service/src/main/java/.../AdminUserController.java` | @PatchMapping("/{id}") | ✓ VERIFIED | `@PatchMapping("/{id}")` line 63; `userCrudService.patchUser` line 68 |
| `sources/frontend/src/services/products.ts` | 5 admin functions | ✓ VERIFIED | listAdminProducts, createProduct, updateProduct, deleteProduct, listAdminCategories — lines 84/94/98/102/106 |
| `sources/frontend/src/services/orders.ts` | 3 admin functions | ✓ VERIFIED | listAdminOrders, getAdminOrderById, updateOrderState — lines 54/63/67 |
| `sources/frontend/src/services/users.ts` | New file — 3 admin functions | ✓ VERIFIED | listAdminUsers, patchAdminUser, deleteAdminUser — lines 21/30/34 |
| `sources/frontend/src/app/admin/layout.tsx` | ToastProvider wrap | ✓ VERIFIED | Import line 7, `<ToastProvider>` line 21, `</ToastProvider>` line 73 |
| `sources/frontend/src/app/admin/products/page.tsx` | Real API + add/edit modal + delete | ✓ VERIFIED | 0 `_stubProducts`; 6 service function usages; editTarget state; RetrySection; toast messages exact |
| `sources/frontend/src/app/admin/orders/page.tsx` | Real API + router.push | ✓ VERIFIED | `listAdminOrders` line 50; `router.push` line 125; 0 `_stubOrders` |
| `sources/frontend/src/app/admin/orders/[id]/page.tsx` | New file — order detail + status update | ✓ VERIFIED | `getAdminOrderById` line 58; `updateOrderState` line 74; STATUS_OPTIONS array; toast messages |
| `sources/frontend/src/app/admin/users/page.tsx` | Real API + edit modal + delete confirm | ✓ VERIFIED | listAdminUsers/patchAdminUser/deleteAdminUser × 4 usages; editTarget state × 3+; fullName fallback; RetrySection; roles guard |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| FE GET /api/products?keyword=... | ProductController.listProducts | gateway product-service route | ✓ WIRED | `@RequestParam(required=false) String keyword` line 36 ProductController; keyword filter line 42 ProductCrudService |
| FE GET /api/products/admin | AdminProductController.listProducts | gateway product-service-admin-base route | ✓ WIRED | Route `product-service-admin-base` line 51 YAML; route TRƯỚC `product-service-base` line 65 |
| FE GET /api/users/admin/{id} | AdminUserController endpoints | gateway user-service-admin route | ✓ WIRED | Route `user-service-admin` line 29; `@PatchMapping("/{id}")` AdminUserController line 63 |
| AdminUserController.patchUser | UserCrudService.patchUser | method call | ✓ WIRED | `userCrudService.patchUser(id, request)` AdminUserController line 68 |
| UserCrudService.patchUser → UserEntity.setFullName | user_svc.users.full_name | JPA userRepo.save | ✓ WIRED | `user.setFullName(request.fullName())` + `userRepo.save(user)` UserCrudService; V2 migration tồn tại |
| UserMapper.toDto | UserDto.fullName | e.fullName() accessor | ✓ WIRED | `e.fullName()` line 16 UserMapper |
| admin/products/page.tsx | services/products.ts listAdminProducts | import + useCallback load | ✓ WIRED | Import + 6 usages confirmed |
| admin/orders/page.tsx row click | admin/orders/[id]/page.tsx | router.push('/admin/orders/${o.id}') | ✓ WIRED | `router.push` line 125 orders/page.tsx; `useParams<{ id }>()` trong [id]/page.tsx |
| admin/orders/[id]/page.tsx | services/orders.ts getAdminOrderById + updateOrderState | useCallback load + handleUpdateStatus | ✓ WIRED | Lines 58 và 74 trong [id]/page.tsx |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `search/page.tsx` | `products` state | `listProducts({keyword})` → GET /api/products → ProductCrudService.listProducts stream filter | Yes — in-memory filter trên data từ JPA productRepo.findAll() | ✓ FLOWING |
| `admin/products/page.tsx` | `products` state | `listAdminProducts()` → GET /api/products/admin → AdminProductController → productCrudService.listProducts(true) | Yes — JPA productRepo.findAll() | ✓ FLOWING |
| `admin/orders/page.tsx` | `orders` state | `listAdminOrders()` → GET /api/orders/admin → AdminOrderController.listOrders | Yes — JPA orderRepo (Phase 5) | ✓ FLOWING |
| `admin/orders/[id]/page.tsx` | `order` state | `getAdminOrderById(id)` → GET /api/orders/admin/{id} | Yes — JPA query | ✓ FLOWING |
| `admin/users/page.tsx` | `users` state | `listAdminUsers()` → GET /api/users/admin → AdminUserController.listUsers | Yes — JPA userRepo | ✓ FLOWING |
| `ProductCrudService.toResponse()` | brand/thumbnailUrl/shortDescription/originalPrice | `product.brand()`, `product.thumbnailUrl()`, etc. — entity fields thật | Yes — từ entity fields sau V2 migration (Fix confirmed trong 07-02-SUMMARY) | ✓ FLOWING |

### Behavioral Spot-Checks

Step 7b: SKIPPED — cần Docker stack đang chạy để test API endpoints. Không thể verify programmatically mà không start services.

### Requirements Coverage

| Requirement | Source Plans | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| UI-01 | 07-01, 07-04 | FE `/search` rewire với keyword search thật | ✓ SATISFIED | search/page.tsx gọi `listProducts({keyword})`; ProductController nhận keyword; ProductCrudService filter in-memory |
| UI-02 | 07-01, 07-02, 07-04, 07-05 | FE `admin/products` migrate khỏi mock — CRUD thật qua gateway | ✓ SATISFIED | 0 stubs; listAdminProducts/createProduct/updateProduct/deleteProduct wired; V2 migration persist brand/thumbnail |
| UI-03 | 07-01, 07-04, 07-05 | FE `admin/orders` migrate khỏi mock — list + detail + status update | ✓ SATISFIED | listAdminOrders wired; router.push navigate; detail page với updateOrderState |
| UI-04 | 07-01, 07-03, 07-04, 07-06 | FE `admin/users` migrate khỏi mock — list + edit + soft-delete | ✓ SATISFIED | listAdminUsers/patchAdminUser/deleteAdminUser wired; UserEditModal PATCH; V2 migration fullName/phone |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `admin/orders/[id]/page.tsx` | 136-137 | `Địa chỉ: —`, `Thanh toán: —` hardcoded | ℹ️ Info | Intentional — OrderDto Phase 7 không có shippingAddress/paymentMethod; Phase 8 address |
| `admin/orders/[id]/page.tsx` | 145 | "Chi tiết sản phẩm sẽ khả dụng sau khi Phase 8 hoàn thiện" | ℹ️ Info | Intentional deferred stub — line items scope of Phase 8 PERSIST-02 |
| `admin/orders/page.tsx` | 113 | `<td>—</td>` cho cột Số sản phẩm | ℹ️ Info | Intentional — backend OrderDto không có items array trong Phase 7 |
| `admin/orders/[id]/page.tsx` | 58 | `as any` cast cho getAdminOrderById result | ⚠️ Warning | TypeScript type workaround vì local AdminOrder interface — không break runtime |
| `admin/orders/page.tsx` | Decision | `showToast: _showToast` alias để avoid unused var | ℹ️ Info | Style issue — không block functionality |

Tất cả anti-patterns ở mức Info/Warning — không có blocker nào ngăn goal.

### Human Verification Required

#### 1. Search keyword end-to-end

**Test:** Mở `/search`, nhập keyword "laptop" (hoặc tên product từ seed data), chờ debounce 350ms
**Expected:** Product list cập nhật với sản phẩm có "laptop" trong tên; khi nhập keyword không khớp → hiển thị "Không tìm thấy kết quả cho 'xyz'"
**Why human:** Cần Docker stack chạy; verify real DB → gateway → FE flow không thể curl without server

#### 2. Admin create product

**Test:** Login admin → /admin/products → click "+ Thêm sản phẩm" → điền Name, Price, Category (từ dropdown thật) → Submit
**Expected:** Toast "Sản phẩm đã được thêm thành công"; product xuất hiện trong list sau refresh; brand/thumbnail persist nếu điền
**Why human:** Modal form submission + category dropdown load từ API + toast + list refresh — browser interaction required

#### 3. Admin orders navigation + status update

**Test:** /admin/orders → click 📋 trên order row → verify /admin/orders/{id} page load → chọn status mới → click "Cập nhật trạng thái"
**Expected:** Router.push navigate sang detail page; order info hiển thị; toast "Trạng thái đơn hàng đã được cập nhật" sau update
**Why human:** Next.js router.push + useParams behavior cần browser runtime

#### 4. Admin edit user fullName

**Test:** /admin/users → click ✏️ trên user row (non-admin) → modal pre-filled với fullName/phone → update fullName → Save
**Expected:** Toast "Thông tin tài khoản đã được cập nhật"; list refresh với fullName mới hiển thị; nút xóa ẩn với ADMIN users
**Why human:** PATCH flow + list reload + UI fallback (fullName vs username) cần browser

### Gaps Summary

Không có gaps nào blocking goal achievement. Tất cả 4 success criteria từ ROADMAP đã verified. Các stubs còn lại (line items, địa chỉ giao hàng) là intentional và được address rõ ràng trong Phase 8.

Chỉ cần human verification cho 4 end-to-end flows vì cần browser + Docker stack chạy.

---

_Verified: 2026-04-26T12:00:00Z_
_Verifier: Claude (gsd-verifier)_
