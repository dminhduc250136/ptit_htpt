---
phase: 07-search-admin-real-data
plan: "05"
subsystem: frontend-admin
tags: [admin, products, orders, real-api, crud, next-js]
dependency_graph:
  requires: ["07-01", "07-02", "07-04"]
  provides: ["admin-products-crud", "admin-orders-list", "admin-orders-detail"]
  affects: ["sources/frontend/src/app/admin/products/page.tsx", "sources/frontend/src/app/admin/orders/page.tsx", "sources/frontend/src/app/admin/orders/[id]/page.tsx"]
tech_stack:
  added: []
  patterns: ["useCallback+useEffect load pattern", "editTarget state for add/edit modal", "router.push navigate to detail page", "local AdminProduct/AdminOrder interface for DTO mismatch"]
key_files:
  created:
    - sources/frontend/src/app/admin/orders/[id]/page.tsx
  modified:
    - sources/frontend/src/app/admin/products/page.tsx
    - sources/frontend/src/app/admin/orders/page.tsx
decisions:
  - "Dùng local AdminProduct interface thay vì Product từ types/index.ts để tránh type mismatch (Product.category là nested object, backend trả categoryId string)"
  - "Dùng local AdminOrder interface thay vì Order từ types/index.ts vì Order type dùng orderStatus/items nhưng backend trả status/no-items"
  - "admin/orders/[id]/page.tsx reuse styles từ admin/products/page.module.css thay vì tạo CSS file mới"
metrics:
  duration: "~20 phút"
  completed: "2026-04-26"
  tasks_completed: 2
  tasks_total: 2
  files_modified: 3
---

# Phase 07 Plan 05: Admin Products CRUD + Orders Real Data Summary

Wire 3 admin pages với real API: products CRUD (add/edit modal + delete confirm + category dropdown) + orders list (router.push navigate) + orders detail page mới (status update).

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Admin Products — real API + add/edit modal + delete confirm | 8bdb3c0 | admin/products/page.tsx |
| 2 | Admin Orders list (router.push) + Orders detail page (new) | 075470b | admin/orders/page.tsx, admin/orders/[id]/page.tsx |

## Files Modified / Created

### Modified

**sources/frontend/src/app/admin/products/page.tsx**
- Xóa `_stubProducts` array
- Thêm `listAdminProducts`, `createProduct`, `updateProduct`, `deleteProduct`, `listAdminCategories` imports
- Thêm `editTarget` state để phân biệt add mode vs edit mode trong cùng một modal
- `openEditModal(product)` pre-fill form data từ product row
- Category dropdown load từ `listAdminCategories()` khi modal mở
- Submit handler: FE validation → auto-gen slug từ name nếu trống → create/update theo `editTarget`
- Delete handler: `deleteProduct(id)` → toast success → list refresh
- Loading skeleton (5 rows), RetrySection error state, empty state

**sources/frontend/src/app/admin/orders/page.tsx**
- Xóa `_stubOrders`, `_stubUsers`, toàn bộ inline modal code
- Wire `listAdminOrders()` với loading/error/empty/data states
- Thêm `router.push('/admin/orders/${o.id}')` trên nút 📋
- Badge variant mapping: PENDING=default, CONFIRMED=new, SHIPPING=hot, DELIVERED=sale, CANCELLED=out-of-stock

### Created

**sources/frontend/src/app/admin/orders/[id]/page.tsx** (file mới)
- `useParams<{ id: string }>()` để lấy order ID từ URL
- `getAdminOrderById(id)` load order data
- Loading skeleton (3 blocks), RetrySection error state
- Info grid 2 cột: thông tin đơn hàng + thông tin giao hàng (Phase 8 placeholder)
- Line items card (Phase 8 placeholder)
- Status update card: `<select>` 5 options → `updateOrderState(id, newStatus)` → toast success/error
- `router.back()` back navigation

## D-06/D-07/D-08 Implementation Details

### D-06: Edit mode (editTarget state)

Modal dùng `editTarget: AdminProduct | null` làm discriminant:
- `null` → add mode (title "Thêm sản phẩm mới", submit button "Thêm sản phẩm", gọi `createProduct`)
- non-null → edit mode (title "Chỉnh sửa sản phẩm", submit button "Lưu thay đổi", gọi `updateProduct`)

`openEditModal(product)` pre-fill tất cả 8 fields của `formData` từ product row trước khi mở modal.

### D-07: Category dropdown loading

`loadCategories()` được gọi mỗi khi modal mở (cả add và edit):
- `loadingCategories=true` → hiển thị "Đang tải danh mục..."
- `categories.length === 0` → hiển thị "Không thể tải danh mục — thử lại"
- Success → render `<option>` list từ API response

### D-08: Detail page navigation pattern

```
admin/orders/page.tsx
  └── router.push(`/admin/orders/${o.id}`)  [nút 📋 mỗi row]
        ↓
admin/orders/[id]/page.tsx
  └── useParams().id → getAdminOrderById(id) → render detail
  └── updateOrderState(id, newStatus) → toast → reload
  └── router.back() [nút ← Quay lại]
```

## TypeScript Compile Status

`npx tsc --noEmit` — 0 errors cho `admin/products` và `admin/orders` paths.

**Note:** Local `AdminProduct` và `AdminOrder` interfaces được khai báo trong từng file để tránh type mismatch với `types/index.ts` (legacy `Product` type có `category: Category` nested object; `Order` type có `orderStatus`/`items` không khớp với backend DTO trả về `categoryId`/`status`).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Type mismatch: Product.category vs backend categoryId**
- **Found during:** Task 1 — khi đọc `types/index.ts`
- **Issue:** `Product` interface có `category: Category` (nested object), nhưng backend `/api/products/admin` trả về `categoryId: string`. Nếu dùng `Product` type, TypeScript sẽ complain và runtime sẽ render undefined
- **Fix:** Khai báo local `AdminProduct` interface với `categoryId?: string` thay vì dùng `Product` từ types. Dùng `as any[]` cast khi set từ API response
- **Files modified:** admin/products/page.tsx

**2. [Rule 1 - Bug] Type mismatch: Order.orderStatus vs backend status**
- **Found during:** Task 2 — khi đọc `types/index.ts` và orders/page.tsx cũ
- **Issue:** `Order` interface có `orderStatus`, `items`, `shippingAddress`, v.v.; nhưng backend AdminOrderDto trả về `status`, không có `items`. Nếu dùng `Order` type, toàn bộ field access sẽ sai
- **Fix:** Khai báo local `AdminOrder` interface với `status: string` + `totalAmount?/total?`. Dùng `as any` cast khi set từ API response
- **Files modified:** admin/orders/page.tsx, admin/orders/[id]/page.tsx

**3. [Rule 2 - Missing] `_showToast` prefix trong orders/page.tsx**
- **Found during:** Task 2 — `useToast` import không dùng trong list page (toast chỉ cần ở detail page)
- **Fix:** Giữ import nhưng alias `showToast: _showToast` để tránh unused variable warning. List page không cần toast (chỉ navigate sang detail page)
- **Files modified:** admin/orders/page.tsx

## Known Stubs

| File | Stub | Reason |
|------|------|--------|
| admin/orders/[id]/page.tsx | "Chi tiết sản phẩm sẽ khả dụng sau khi Phase 8 hoàn thiện" | Backend OrderDto Phase 7 không có items array — Phase 8 implement PERSIST-02 |
| admin/orders/[id]/page.tsx | Địa chỉ: "—", Thanh toán: "—" | Backend AdminOrderDto không expose shippingAddress/paymentMethod — defer Phase 8 |

## Self-Check

### Created files exist
- [x] `sources/frontend/src/app/admin/products/page.tsx` — EXISTS (modified)
- [x] `sources/frontend/src/app/admin/orders/page.tsx` — EXISTS (modified)
- [x] `sources/frontend/src/app/admin/orders/[id]/page.tsx` — EXISTS (created)

### Commits exist
- [x] `8bdb3c0` — feat(07-05): admin products page
- [x] `075470b` — feat(07-05): admin orders list + orders detail page

## Self-Check: PASSED
