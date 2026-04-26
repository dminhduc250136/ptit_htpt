---
status: passed
phase: 07-search-admin-real-data
source: [07-VERIFICATION.md]
started: 2026-04-26T11:45:00.000Z
updated: 2026-04-26T15:00:00.000Z
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
    "timestamp": "2026-04-26T12:13:08.840135708Z",
    "status": 200,
    "message": "Products listed",
    "data": {
        "content": [
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
                "createdAt": "2026-04-26T11:25:43.458857Z",
                "updatedAt": "2026-04-26T11:25:43.458857Z"
            }
        ],
        "totalElements": 1,
        "totalPages": 1,
        "currentPage": 0,
        "pageSize": 20,
        "isFirst": true,
        "isLast": true
    }
}
Đã hoạt động

### 2. Admin create product — modal + category dropdown + persist
expected: Admin vào /admin/products → click "+ Thêm sản phẩm" → modal mở với empty form → category dropdown load từ /api/products/admin/categories → fill brand/thumbnailUrl/shortDescription → submit → POST /api/products/admin → toast success → list refresh với product mới; brand/thumbnail persist trong DB (Flyway V2 columns)
result: [pending]

-> đăng nhập admin vào được trang quản trị bình thường, nhưng phần sản phẩm ko thấy có sản phẩm nào cả các mục khác (đơn hàng, dashboard hay user) - lẽ ra nếu ko có ccũng nên để một dòng placeholder là không có sản phẩm nào, hoặc đại ý vậy. Về layout tranng admin vẫn còn header, footer cũ bên người dùng -> xấu

-> Bug: với tài khoản demo@tmdt.local sau khi đăng nhập -> trỏ url -> ttp://localhost:3000/admin/products thì vẫn vào thành công mà ko trỏ sang forbiden (hay tài khoản này cũng là admin?)

Giao diện có nhiều cái chưa dùng được, (ví dụ khi nhấn vào admin -> ko có popup logout hoặc xem thông tin tài khoản, với phía người dùng thì icon người dùng (header) thì bấm vào nó redirect thẳng sang trang accout -> ko biết logout ở đâu -> đáng lẽ phải là một popup có các option như: thông tin tài khoản, logout, dơn hàng của bạn, ... đại ý thế).


-> Không có category trong dropdown -> ko thêm mới được

### 3. Admin orders navigation + status update PATCH
expected: Admin vào /admin/orders → list orders thật từ API → click 📋 trên row → router.push('/admin/orders/{id}') → detail page load với getAdminOrderById → chọn status mới trong dropdown → click "Cập nhật trạng thái" → PATCH /api/orders/admin/{id}/state → toast success
result: [pending]

-> Đã vào được trang detail order, nhưng khi cập nhật state -> fail
{
    "timestamp": "2026-04-26T12:16:41.387835563Z",
    "status": 400,
    "error": "Bad Request",
    "message": "Validation failed",
    "code": "VALIDATION_ERROR",
    "path": "/admin/orders/ord-demo-002/state",
    "traceId": "e1343ba1-d3f5-4c74-b1d2-b11b0d91b1af",
    "fieldErrors": [
        {
            "field": "state",
            "rejectedValue": null,
            "message": "must not be blank"
        }
    ]
}

### 4. Admin edit user fullName — PATCH + list refresh + fallback
expected: Admin vào /admin/users → list users thật → cột Họ tên hiển thị fullName nếu có, fallback username → click ✏️ → UserEditModal pre-filled với fullName/phone/roles → submit → PATCH /api/users/admin/{id} → toast success → list refresh; user có roles=ADMIN không có nút 🗑️
result: [pending]
-> mở được modal -> update name thành công
{
    "timestamp": "2026-04-26T12:17:49.430236649Z",
    "status": 200,
    "message": "Admin users listed",
    "data": {
        "content": [
            {
                "id": "00000000-0000-0000-0000-000000000002",
                "username": "demo_user",
                "email": "demo@tmdt.local",
                "roles": "USER",
                "fullName": "demo name",
                "phone": null,
                "createdAt": "2026-04-26T11:25:42.719348Z",
                "updatedAt": "2026-04-26T12:17:49.369517Z"
            },
            {
                "id": "00000000-0000-0000-0000-000000000001",
                "username": "admin",
                "email": "admin@tmdt.local",
                "roles": "ADMIN",
                "fullName": null,
                "phone": null,
                "createdAt": "2026-04-26T11:25:42.719348Z",
                "updatedAt": "2026-04-26T11:25:42.719348Z"
            }
        ],
        "totalElements": 2,
        "totalPages": 1,
        "currentPage": 0,
        "pageSize": 20,
        "isFirst": true,
        "isLast": true
    }
}

## Summary

total: 4
passed: 1
issues: 3
pending: 0
skipped: 0
blocked: 0

## Gaps

- truth: "Login POST /api/users/auth/login → 200 + token"
  status: fixed
  reason: "Gateway route /api/users/** rewrite thành /users/* bao trùm /api/users/auth/login → /users/auth/login (không tồn tại). Fix: thêm route user-service-auth trước route chung."
  severity: blocker
  test: 1
  fix_applied: "api-gateway/src/main/resources/application.yml — thêm route user-service-auth-base + user-service-auth trước user-service-admin-base (2026-04-26)"
