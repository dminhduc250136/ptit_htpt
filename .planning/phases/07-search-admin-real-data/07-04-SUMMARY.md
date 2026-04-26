---
phase: "07-search-admin-real-data"
plan: "04"
subsystem: "frontend-services"
tags: ["fe", "services", "admin", "toast"]
dependency_graph:
  requires: ["07-01", "07-02", "07-03"]
  provides: ["listAdminProducts", "createProduct", "updateProduct", "deleteProduct", "listAdminCategories", "listAdminOrders", "getAdminOrderById", "updateOrderState", "listAdminUsers", "patchAdminUser", "deleteAdminUser", "ToastProvider-admin-layout"]
  affects: ["07-05", "07-06"]
tech_stack:
  added: []
  patterns: ["typed-http-service-layer", "react-context-toast"]
key_files:
  created:
    - sources/frontend/src/services/users.ts
  modified:
    - sources/frontend/src/services/products.ts
    - sources/frontend/src/services/orders.ts
    - sources/frontend/src/app/admin/layout.tsx
decisions:
  - "Reuse ListOrdersParams interface đã có trong orders.ts thay vì tạo mới (interface đã exist từ trước)"
  - "ToastProvider wrap toàn bộ AdminLayout JSX — cho phép bất kỳ admin page con nào gọi useToast() mà không lỗi context"
metrics:
  duration: "10 phút"
  completed: "2026-04-26T10:12:10Z"
  tasks_completed: 2
  files_modified: 4
---

# Phase 7 Plan 04: FE Services Foundation Summary

**One-liner:** Typed HTTP service layer cho admin CRUD (products/orders/users) + ToastProvider wrapping admin layout để enable useToast() trong tất cả admin pages.

## Kết quả

Tạo typed HTTP service layer cho các admin operations. Plans 07-05 (admin products page) và 07-06 (admin orders/users pages) có thể import trực tiếp các functions này.

## Files Modified/Created

### sources/frontend/src/services/products.ts (modified)

Thêm:
- Import: `httpPost`, `httpPut`, `httpDelete` từ `./http`
- Interface `ProductUpsertBody` (name, slug?, categoryId, price, status, stock?, brand?, thumbnailUrl?, shortDescription?, originalPrice?)
- 5 admin functions:

| Function | Method | Gateway URL |
|---|---|---|
| `listAdminProducts(params?)` | GET | `/api/products/admin?page=&size=&sort=&keyword=` |
| `createProduct(body)` | POST | `/api/products/admin` |
| `updateProduct(id, body)` | PUT | `/api/products/admin/{id}` |
| `deleteProduct(id)` | DELETE | `/api/products/admin/{id}` |
| `listAdminCategories()` | GET | `/api/products/admin/categories` |

Existing functions (`listProducts`, `getProductById`, `getProductBySlug`, `listCategories`) không thay đổi.

### sources/frontend/src/services/orders.ts (modified)

Thêm:
- Import: `httpPatch` từ `./http`
- 3 admin functions (dùng `ListOrdersParams` đã có sẵn trong file):

| Function | Method | Gateway URL |
|---|---|---|
| `listAdminOrders(params?)` | GET | `/api/orders/admin?page=&size=&sort=` |
| `getAdminOrderById(id)` | GET | `/api/orders/admin/{id}` |
| `updateOrderState(id, status)` | PATCH | `/api/orders/admin/{id}/state` |

Existing functions (`createOrder`, `listMyOrders`, `getOrderById`) không thay đổi.

### sources/frontend/src/services/users.ts (TẠO MỚI)

File mới hoàn toàn với:
- Interfaces: `ListUsersParams`, `AdminUserPatchBody`
- 3 admin functions:

| Function | Method | Gateway URL |
|---|---|---|
| `listAdminUsers(params?)` | GET | `/api/users/admin?page=&size=&sort=` |
| `patchAdminUser(id, body)` | PATCH | `/api/users/admin/{id}` |
| `deleteAdminUser(id)` | DELETE | `/api/users/admin/{id}` |

### sources/frontend/src/app/admin/layout.tsx (modified)

- Import `ToastProvider` từ `@/components/ui/Toast/Toast`
- Wrap toàn bộ JSX return trong `<ToastProvider>...</ToastProvider>`
- Tất cả admin pages (products, orders, users) có thể gọi `useToast()` mà không lỗi context

## TypeScript Compile Status

Không có lỗi TypeScript mới. `npx tsc --noEmit` — 0 errors liên quan đến các files đã sửa.

## Deviations from Plan

Không có deviation. Plan thực thi đúng theo spec.

`ListOrdersParams` interface đã tồn tại trong `orders.ts` từ trước — không tạo duplicate, dùng interface đã có.

## Commits

| Hash | Mô tả |
|------|--------|
| `a08cea1` | feat(07-04): thêm 5 admin functions vào services/products.ts |
| `f2f086e` | feat(07-04): admin orders/users services + ToastProvider trong admin layout |

## Self-Check

- [x] `sources/frontend/src/services/products.ts` — tồn tại, có 5 admin functions
- [x] `sources/frontend/src/services/orders.ts` — tồn tại, có 3 admin functions
- [x] `sources/frontend/src/services/users.ts` — tồn tại (mới tạo), có 3 admin functions
- [x] `sources/frontend/src/app/admin/layout.tsx` — có 3 occurrences của ToastProvider (import + open tag + close tag)
- [x] Commit `a08cea1` tồn tại trong git log
- [x] Commit `f2f086e` tồn tại trong git log

## Self-Check: PASSED
