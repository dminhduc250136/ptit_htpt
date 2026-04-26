---
status: partial
phase: 07-search-admin-real-data
source: [07-VERIFICATION.md]
started: 2026-04-26T11:45:00.000Z
updated: 2026-04-26T11:45:00.000Z
---

## Current Test

[awaiting human testing — requires browser + Docker stack: `docker compose up`]

## Tests

### 1. Search keyword end-to-end với real data
expected: User nhập keyword vào /search → FE gọi `listProducts({keyword})` qua gateway → gateway route đến product-service → in-memory filter trả products có keyword trong tên → render kết quả; empty keyword → tất cả products; không có kết quả → empty state "Không tìm thấy sản phẩm"
result: issue
severity: blocker
reported: "search sony hay sach đều ra 10 kết quả (không filter). Backend trả đúng 10 products không lọc. FE chưa pass keyword param. Đồng thời không login được do lỗi 500 — gateway route /api/users/** rewrite thành /users/** sai cho AuthController (/auth/login). Fix: thêm route user-service-auth trước route chung trong application.yml (đã áp dụng 2026-04-26)."

với url http://localhost:3000/search
-> kết quả: search sony hay sach đều ra 10 kết quả, và nó vẫn ở giao diện của người dùng thì phải khi header và footer như user view
** Kể cả xyz cũng cho ra 10 kết quả
chạy thử trên postman với api http://localhost:8080/api/products?keyword=sony

{
    "timestamp": "2026-04-26T11:07:19.896993648Z",
    "status": 200,
    "message": "Products listed",
    "data": {
        "content": [
            {
                "id": "prod-010",
                "name": "Son môi MAC Ruby Woo",
                "slug": "son-moi-mac-ruby-woo",
                "description": "",
                "shortDescription": "",
                "price": 690000.00,
                "originalPrice": null,
                "discount": null,
                "images": [],
                "thumbnailUrl": "",
                "category": {
                    "id": "cat-cosmetics",
                    "name": "Mỹ phẩm",
                    "slug": "my-pham"
                },
                "brand": null,
                "rating": 0,
                "reviewCount": 0,
                "stock": 0,
                "status": "ACTIVE",
                "tags": [],
                "createdAt": "2026-04-26T04:06:54.364446Z",
                "updatedAt": "2026-04-26T04:06:54.364446Z"
            },
            {
                "id": "prod-009",
                "name": "Kem chống nắng Anessa SPF50",
                "slug": "kem-chong-nang-anessa",
                "description": "",
                "shortDescription": "",
                "price": 489000.00,
                "originalPrice": null,
                "discount": null,
                "images": [],
                "thumbnailUrl": "",
                "category": {
                    "id": "cat-cosmetics",
                    "name": "Mỹ phẩm",
                    "slug": "my-pham"
                },
                "brand": null,
                "rating": 0,
                "reviewCount": 0,
                "stock": 0,
                "status": "ACTIVE",
                "tags": [],
                "createdAt": "2026-04-26T04:06:54.364446Z",
                "updatedAt": "2026-04-26T04:06:54.364446Z"
            },
            {
                "id": "prod-008",
                "name": "Sách Atomic Habits - James Clear",
                "slug": "sach-atomic-habits",
                "description": "",
                "shortDescription": "",
                "price": 180000.00,
                "originalPrice": null,
                "discount": null,
                "images": [],
                "thumbnailUrl": "",
                "category": {
                    "id": "cat-books",
                    "name": "Sách",
                    "slug": "sach"
                },
                "brand": null,
                "rating": 0,
                "reviewCount": 0,
                "stock": 0,
                "status": "ACTIVE",
                "tags": [],
                "createdAt": "2026-04-26T04:06:54.364446Z",
                "updatedAt": "2026-04-26T04:06:54.364446Z"
            },
            {
                "id": "prod-007",
                "name": "Sách Clean Code - Robert C. Martin",
                "slug": "sach-clean-code",
                "description": "",
                "shortDescription": "",
                "price": 320000.00,
                "originalPrice": null,
                "discount": null,
                "images": [],
                "thumbnailUrl": "",
                "category": {
                    "id": "cat-books",
                    "name": "Sách",
                    "slug": "sach"
                },
                "brand": null,
                "rating": 0,
                "reviewCount": 0,
                "stock": 0,
                "status": "ACTIVE",
                "tags": [],
                "createdAt": "2026-04-26T04:06:54.364446Z",
                "updatedAt": "2026-04-26T04:06:54.364446Z"
            },
            {
                "id": "prod-006",
                "name": "Bộ chăn ga gối cotton",
                "slug": "bo-chan-ga-goi-cotton",
                "description": "",
                "shortDescription": "",
                "price": 890000.00,
                "originalPrice": null,
                "discount": null,
                "images": [],
                "thumbnailUrl": "",
                "category": {
                    "id": "cat-household",
                    "name": "Gia dụng",
                    "slug": "gia-dung"
                },
                "brand": null,
                "rating": 0,
                "reviewCount": 0,
                "stock": 0,
                "status": "ACTIVE",
                "tags": [],
                "createdAt": "2026-04-26T04:06:54.364446Z",
                "updatedAt": "2026-04-26T04:06:54.364446Z"
            },
            {
                "id": "prod-005",
                "name": "Nồi cơm điện Cuckoo 1.8L",
                "slug": "noi-com-dien-cuckoo-1-8l",
                "description": "",
                "shortDescription": "",
                "price": 3290000.00,
                "originalPrice": null,
                "discount": null,
                "images": [],
                "thumbnailUrl": "",
                "category": {
                    "id": "cat-household",
                    "name": "Gia dụng",
                    "slug": "gia-dung"
                },
                "brand": null,
                "rating": 0,
                "reviewCount": 0,
                "stock": 0,
                "status": "ACTIVE",
                "tags": [],
                "createdAt": "2026-04-26T04:06:54.364446Z",
                "updatedAt": "2026-04-26T04:06:54.364446Z"
            },
            {
                "id": "prod-004",
                "name": "Quần jean slim-fit",
                "slug": "quan-jean-slim-fit",
                "description": "",
                "shortDescription": "",
                "price": 549000.00,
                "originalPrice": null,
                "discount": null,
                "images": [],
                "thumbnailUrl": "",
                "category": {
                    "id": "cat-fashion",
                    "name": "Thời trang",
                    "slug": "thoi-trang"
                },
                "brand": null,
                "rating": 0,
                "reviewCount": 0,
                "stock": 0,
                "status": "ACTIVE",
                "tags": [],
                "createdAt": "2026-04-26T04:06:54.364446Z",
                "updatedAt": "2026-04-26T04:06:54.364446Z"
            },
            {
                "id": "prod-003",
                "name": "Áo thun cotton basic",
                "slug": "ao-thun-cotton-basic",
                "description": "",
                "shortDescription": "",
                "price": 199000.00,
                "originalPrice": null,
                "discount": null,
                "images": [],
                "thumbnailUrl": "",
                "category": {
                    "id": "cat-fashion",
                    "name": "Thời trang",
                    "slug": "thoi-trang"
                },
                "brand": null,
                "rating": 0,
                "reviewCount": 0,
                "stock": 0,
                "status": "ACTIVE",
                "tags": [],
                "createdAt": "2026-04-26T04:06:54.364446Z",
                "updatedAt": "2026-04-26T04:06:54.364446Z"
            },
            {
                "id": "prod-002",
                "name": "Bàn phím cơ Keychron K2",
                "slug": "ban-phim-co-keychron-k2",
                "description": "",
                "shortDescription": "",
                "price": 2490000.00,
                "originalPrice": null,
                "discount": null,
                "images": [],
                "thumbnailUrl": "",
                "category": {
                    "id": "cat-electronics",
                    "name": "Điện tử",
                    "slug": "dien-tu"
                },
                "brand": null,
                "rating": 0,
                "reviewCount": 0,
                "stock": 0,
                "status": "ACTIVE",
                "tags": [],
                "createdAt": "2026-04-26T04:06:54.364446Z",
                "updatedAt": "2026-04-26T04:06:54.364446Z"
            },
            {
                "id": "prod-001",
                "name": "Tai nghe bluetooth Sony WH-1000XM5",
                "slug": "tai-nghe-sony-wh-1000xm5",
                "description": "",
                "shortDescription": "",
                "price": 7990000.00,
                "originalPrice": null,
                "discount": null,
                "images": [],
                "thumbnailUrl": "",
                "category": {
                    "id": "cat-electronics",
                    "name": "Điện tử",
                    "slug": "dien-tu"
                },
                "brand": null,
                "rating": 0,
                "reviewCount": 0,
                "stock": 0,
                "status": "ACTIVE",
                "tags": [],
                "createdAt": "2026-04-26T04:06:54.364446Z",
                "updatedAt": "2026-04-26T04:06:54.364446Z"
            }
        ],
        "totalElements": 10,
        "totalPages": 1,
        "currentPage": 0,
        "pageSize": 20,
        "isFirst": true,
        "isLast": true
    }
}

** Vấn đề mới: Tìm ra rồi. User-service có 2 nhóm endpoint:

AuthController → prefix /auth (login, register, logout)
UserProfileController → prefix /users
Gateway route /api/users/** → rewrite thành /users/** — đúng cho UserProfileController, nhưng sai cho AuthController vì login là /auth/login không phải /users/auth/login.

Frontend gọi /api/users/auth/login → gateway rewrite thành /users/auth/login → user-service không có endpoint này → 500.

Cần thêm route riêng trong gateway cho /api/users/auth/** → /auth/**:

Đây là issue cần sửa trong application.yml của api-gateway — thêm route cho auth trước route chung /api/users/**. Bạn muốn tôi fix không?
-> không login được

### 2. Admin create product — modal + category dropdown + persist
expected: Admin vào /admin/products → click "+ Thêm sản phẩm" → modal mở với empty form → category dropdown load từ /api/products/admin/categories → fill brand/thumbnailUrl/shortDescription → submit → POST /api/products/admin → toast success → list refresh với product mới; brand/thumbnail persist trong DB (Flyway V2 columns)
result: [pending]

-> đăng nhập admin vào được trang quản trị bình thường, nhưng phần sản phẩm ko thấy có sản phẩm nào cả các mục khác (đơn hàng, dashboard hay user) - lẽ ra nếu ko có ccũng nên để một dòng placeholder là không có sản phẩm nào, hoặc đại ý vậy. Về layout tranng admin vẫn còn header, footer cũ bên người dùng -> xấu

-> Bug: với tài khoản demo@tmdt.local sau khi đăng nhập -> trỏ url -> ttp://localhost:3000/admin/products thì vẫn vào thành công mà ko trỏ sang forbiden (hay tài khoản này cũng là admin?)

Giao diện có nhiều cái chưa dùng được, (ví dụ khi nhấn vào admin -> ko có popup logout hoặc xem thông tin tài khoản, với phía người dùng thì icon người dùng (header) thì bấm vào nó redirect thẳng sang trang accout -> ko biết logout ở đâu -> đáng lẽ phải là một popup có các option như: thông tin tài khoản, logout, dơn hàng của bạn, ... đại ý thế).

### 3. Admin orders navigation + status update PATCH
expected: Admin vào /admin/orders → list orders thật từ API → click 📋 trên row → router.push('/admin/orders/{id}') → detail page load với getAdminOrderById → chọn status mới trong dropdown → click "Cập nhật trạng thái" → PATCH /api/orders/admin/{id}/state → toast success
result: [pending]

-> Chưa thấy data trên giao diện -> không thao tác được

### 4. Admin edit user fullName — PATCH + list refresh + fallback
expected: Admin vào /admin/users → list users thật → cột Họ tên hiển thị fullName nếu có, fallback username → click ✏️ → UserEditModal pre-filled với fullName/phone/roles → submit → PATCH /api/users/admin/{id} → toast success → list refresh; user có roles=ADMIN không có nút 🗑️
result: [pending]
-> chưa có giao diện ko thao tác được

## Summary

total: 4
passed: 0
issues: 1
pending: 3
skipped: 0
blocked: 0

## Gaps

- truth: "Login POST /api/users/auth/login → 200 + token"
  status: fixed
  reason: "Gateway route /api/users/** rewrite thành /users/* bao trùm /api/users/auth/login → /users/auth/login (không tồn tại). Fix: thêm route user-service-auth trước route chung."
  severity: blocker
  test: 1
  fix_applied: "api-gateway/src/main/resources/application.yml — thêm route user-service-auth-base + user-service-auth trước user-service-admin-base (2026-04-26)"
