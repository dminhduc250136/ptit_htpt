# Tổng hợp 4 Phase của dự án PTIT HTPT E-Commerce

> Tài liệu này giải thích **đã làm gì trong từng phase** bằng ngôn ngữ tự nhiên, kèm ví dụ cụ thể để bạn hình dung dù chưa quen nghiệp vụ. Nguồn dữ liệu: các file `SUMMARY` và `UAT` trong `.planning/phases/`.

---

## Bối cảnh nhanh

Đây là một website bán laptop được tách thành **7 service nhỏ** (microservice) chạy song song:

| Service | Vai trò |
|---|---|
| `user-service` | Đăng ký/đăng nhập, hồ sơ người dùng, địa chỉ |
| `product-service` | Danh mục laptop, chi tiết sản phẩm, đánh giá |
| `order-service` | Giỏ hàng, đơn hàng |
| `payment-service` | Thanh toán (mock) |
| `inventory-service` | Tồn kho, đặt giữ hàng |
| `notification-service` | Gửi thông báo / email mẫu |
| `api-gateway` | "Cửa ngõ" duy nhất ở cổng `8080`, ai gọi gì cũng đi qua đây trước |
| `frontend` (Next.js) | Giao diện người dùng cuối |

Toàn bộ 4 phase đều xoay quanh **một mục tiêu lớn**: làm cho các service "nói chuyện" với nhau và với frontend theo **cùng một quy ước** (cùng cấu trúc response, cùng cách báo lỗi, cùng cách phân trang...).

---

## Phase 1 — Thiết lập "hợp đồng API" và Swagger ✅

**Mục tiêu:** Mọi service phải trả response theo **đúng một khuôn mẫu**, và phải có trang tài liệu Swagger để xem được.

### 1-01: Khuôn response và lỗi chuẩn

Trước phase này, mỗi service trả về kiểu khác nhau, frontend phải viết logic xử lý lỗi riêng cho từng service. Sau phase này:

- **Khi thành công** → bọc dữ liệu trong "phong bì" `ApiResponse`:
  ```json
  {
    "timestamp": "2026-04-22T10:00:00Z",
    "status": 200,
    "message": "OK",
    "data": { ... dữ liệu thật ở đây ... }
  }
  ```

- **Khi lỗi** → khuôn `ApiErrorResponse` cố định:
  ```json
  {
    "timestamp": "2026-04-22T10:00:00Z",
    "status": 400,
    "error": "Bad Request",
    "code": "VALIDATION_ERROR",
    "message": "Validation failed",
    "path": "/users/register",
    "traceId": "uat-abc-123",
    "fieldErrors": [
      { "field": "email", "rejectedValue": "abc", "message": "phải là email hợp lệ" }
    ]
  }
  ```

- **`X-Request-Id`**: mọi request đi vào sẽ được gắn 1 mã trace để debug. Nếu client tự gửi header `X-Request-Id: my-id-001` thì server giữ nguyên; nếu không thì server tự sinh.

- **Endpoint smoke `/__contract/ping` và `/__contract/validate`**: là 2 endpoint "test mẫu" có sẵn trong mọi service để kiểm thử nhanh khuôn response/lỗi mà không cần dữ liệu thật.

> **Ví dụ dễ hình dung:** Trước đây service A trả `{ok: true, items: []}`, service B trả `{success: 1, list: []}`. Sau phase 1-01, cả 2 đều trả `{timestamp, status, message, data: {...}}` — frontend chỉ cần đọc `.data` là xong.

### 1-02: Bật Swagger cho mọi service

Mỗi service được cài thư viện `springdoc-openapi` (version 2.6.0). Sau khi chạy:
- Truy cập `http://localhost:8081/swagger-ui.html` (user-service) thấy trang Swagger interactive.
- `http://localhost:8081/v3/api-docs` trả về JSON đặc tả toàn bộ API.

> **Ví dụ:** Bạn vào Swagger UI của product-service, click endpoint `GET /products`, bấm "Try it out" → xem ngay response thật, không cần Postman.

### 1-03: Gateway gom Swagger + chuyển tiếp request-id

API Gateway (cổng 8080) được nâng cấp:
- Trang Swagger gateway `http://localhost:8080/swagger-ui.html` liệt kê **một chỗ** cả 6 service docs.
- Header `X-Request-Id` được gateway truyền xuyên xuống service và echo lại trên response → tiện debug toàn chuỗi.
- Lỗi sinh tại gateway (vd: gọi route không tồn tại) cũng dùng đúng `ApiErrorResponse` schema.

> **Ví dụ:** Bạn gọi `GET http://localhost:8080/api/does-not-exist/foo` sẽ được gateway trả về 404 với `code: NOT_FOUND` đúng schema chứ không phải HTML lỗi mặc định.

---

## Phase 2 — Hoàn thiện CRUD trên mọi service ✅

**Mục tiêu:** Đảm bảo mọi service đều có đủ bộ **C**reate / **R**ead / **U**pdate / **D**elete và đều theo cùng quy ước phân trang, soft-delete, tách public/admin.

### 2-01: Inventory + Notification

- `inventory-service` có CRUD cho **mặt hàng tồn kho** và **đơn đặt giữ (reservation)**.
- `notification-service` có CRUD cho **template thông báo** và **lượt gửi (dispatch)**.
- Tách rõ route public vs admin: `/inventory/items` cho user thường, `/admin/inventory/items` cho quản trị.

### 2-02: User + Product

- `user-service`: CRUD hồ sơ + địa chỉ + admin có thể `block`/`unblock` user.
- `product-service`: CRUD sản phẩm + danh mục, cộng cờ kích hoạt/ngừng bán.

### 2-03: Order + Payment

- `order-service`: CRUD giỏ hàng + đơn hàng + admin update trạng thái đơn.
- `payment-service`: CRUD phiên thanh toán + giao dịch + admin cập nhật trạng thái.

### Quy ước chung được thiết lập

- **Phân trang** (`GET /products?page=0&size=20`) trả về:
  ```json
  {
    "content": [...],
    "totalElements": 134,
    "totalPages": 7,
    "currentPage": 0,
    "pageSize": 20,
    "isFirst": true,
    "isLast": false
  }
  ```
- **Soft delete**: `DELETE /products/123` không xoá thật mà chỉ đánh dấu `deletedAt`. Admin vẫn xem được nếu thêm `?includeDeleted=true`.
- **Smoke script PowerShell**: mỗi plan có file `.ps1` ở `.planning/phases/02-.../scripts/` để gọi nhanh các route gateway và xác nhận không bị 404.

> **Ví dụ:** Trước phase 2, có thể tạo product nhưng không có endpoint update category. Sau phase 2, mọi tài nguyên (user, product, order, cart, payment, inventory item, notification template) đều có đủ 4 thao tác CRUD theo cùng kiểu.

---

## Phase 3 — Chuẩn hoá Validation và xử lý lỗi ✅

**Mục tiêu:** Bất kể service nào lỗi gì, frontend chỉ cần **một logic** để xử lý.

### 3-01: Chuẩn hoá GlobalExceptionHandler trong mọi service

Mọi service có 1 file `GlobalExceptionHandler.java` xử lý đồng nhất:

| Tình huống | HTTP | code | Ví dụ |
|---|---|---|---|
| Body sai validation | 400 | `VALIDATION_ERROR` | `{"email": "abc"}` → field `email` báo "phải là email" |
| JSON gãy | 400 | `BAD_REQUEST` | Gửi `{"email":` (thiếu đóng) |
| Token sai/thiếu | 401 | `UNAUTHORIZED` | Gọi `/profile` không có Bearer |
| Không đủ quyền | 403 | `FORBIDDEN` | User thường gọi route admin |
| Không tìm thấy | 404 | `NOT_FOUND` | `GET /products/9999999` |
| Trùng/xung đột | 409 | `CONFLICT` | Đăng ký email đã tồn tại |
| Lỗi khác | 500 | `INTERNAL_ERROR` | Crash bất ngờ — KHÔNG để lộ stack trace |

**Bảo mật quan trọng**:
- Nếu field tên có chứa `password`, `token`, `secret` → `rejectedValue` được thay bằng `***` (không lộ giá trị).
- Nếu giá trị quá dài (>120 ký tự) → cắt còn 120 + `...`.
- Lỗi 500 chỉ trả message chung chung "Internal server error", không lộ message của exception.

> **Ví dụ:** Bạn POST `{"password": "abc"}` mà password yêu cầu ≥ 8 ký tự → response sẽ có `fieldErrors[0].rejectedValue: "***"` chứ không phải `"abc"`.

### 3-02: Gateway "pass-through" lỗi từ service

API Gateway cũng được hardening:
- **Nếu service phía dưới đã trả lỗi đúng `ApiErrorResponse`** → gateway giữ nguyên (cả `code`, `message`, `traceId`, `fieldErrors[]`). KHÔNG đè/làm méo.
- **Nếu service trả lỗi raw không đúng khuôn** → gateway tự bọc lại đúng khuôn.
- **Route không tồn tại** → 404 `NOT_FOUND` đúng khuôn.

**Bug được phát hiện và sửa trong phase này**: Spring Cloud Gateway có class `NotFoundException` extends từ `ResponseStatusException` với status mặc định 503 — code cũ chặn nhánh này nên route miss bị trả 503 thay vì 404. Đã đảo thứ tự kiểm tra trong handler để fix.

> **Ví dụ:** Frontend gọi `POST /api/users/register` qua gateway, user-service trả 400 + `VALIDATION_ERROR` + `fieldErrors`. Gateway forward y nguyên về frontend — frontend đọc được `fieldErrors` để hiển thị inline error trên form.

---

## Phase 4 — Frontend hợp đồng + Validate end-to-end (đang làm — 2/3 plan)

**Mục tiêu:** Frontend Next.js gọi đúng endpoint thật, hiển thị lỗi đúng khuôn, đi xuyên flow mua hàng.

### 4-01: Tầng HTTP có type + Codegen + Bảo vệ route ✅

Đây là phần "móng" của frontend.

**(1) Codegen từ OpenAPI** — `npm run gen:api` đọc `/v3/api-docs` của 6 service rồi sinh ra 6 file `.generated.ts` chứa toàn bộ kiểu TypeScript. Lợi ích: nếu backend đổi tên field, frontend compile lỗi luôn.

**(2) HTTP wrapper** — `src/services/http.ts` cung cấp `httpGet/httpPost/httpPut/httpPatch/httpDelete`. Mỗi function:
- Tự gắn `Authorization: Bearer <token>` từ localStorage.
- Tự bóc lớp `ApiResponse.data` trả về phẳng cho code gọi.
- Khi server trả lỗi → throw `ApiError` (có `code`, `status`, `fieldErrors`, `traceId`).
- Gặp `401` → tự xoá token + redirect `/login?returnTo=<đường-cũ>`. Có chặn open-redirect: chỉ nhận `returnTo` bắt đầu bằng `/` và KHÔNG bắt đầu `//`.

**(3) Domain modules** — bao bọc HTTP chuyên biệt cho từng nhóm: `services/auth.ts`, `products.ts`, `orders.ts`, `cart.ts`, `payments.ts`...

**(4) Middleware bảo vệ route** — `middleware.ts` ở gốc dự án chặn 3 nhóm route:
```
matcher: ['/checkout/:path*', '/profile/:path*', '/admin/:path*']
```
Nếu user chưa có cookie `auth_present=1` → tự redirect `/login?returnTo=<route>`.

**(5) AuthProvider + ToastProvider** đã mount trong `app/layout.tsx`.

> **Bug đáng nhớ ở 4-01**: Next.js 16 + Turbopack tự đoán "workspace root" sai (chọn nhầm thư mục cha có `package-lock.json`) → middleware bị bỏ qua trong dev mode. Phải pin `turbopack.root` trong `next.config.ts` mới chạy.

### 4-02: Component UI khôi phục lỗi + nối page vào service thật ✅

**(1) 3 component UI mới**:
- `Banner` — banner đỏ trên đầu form khi có lỗi validation. `role="alert"`, `aria-live="assertive"`. Dòng chữ mặc định: *"Vui lòng kiểm tra các trường bị lỗi"*.
- `Modal` — dialog có khoá scroll, đóng bằng Esc/click backdrop, `aria-modal="true"`.
- `RetrySection` — placeholder hiển thị khi tải data fail, có nút *"Thử lại"*. Dùng cho list sản phẩm, profile orders, home...

**(2) Toast được patch** thêm `aria-label="Đóng thông báo"` cho nút X.

**(3) 8 page được rewire** từ data giả (mock) sang gọi service thật:

| Page | Dùng service nào |
|---|---|
| `/` (home) | `listProducts` x2 (featured + latest) + `listCategories` |
| `/products` | `listProducts` + `listCategories`, có filter giá, sort |
| `/products/[slug]` | `getProductBySlug` (fallback query `?slug=...`); 404 → `not-found.tsx` |
| `/cart` | `services/cart.readCart()` (localStorage, có `cart:change` event) |
| `/checkout` | `createOrder` + xử lý lỗi đa dạng (xem dưới) |
| `/profile` | `useAuth()` + `listMyOrders` |
| `/login`, `/register` | **Vẫn mock submit** — backend chưa có `/auth/*`. Sau khi mock submit thành công vẫn set token + cookie để middleware cho qua. |

**(4) Error dispatcher trong `/checkout`** — tâm điểm của phase 4:
```
err.code === 'VALIDATION_ERROR'  → bật Banner + đặt fieldErrors inline
err.code === 'CONFLICT'
   ├─ nếu details.domainCode === 'STOCK_SHORTAGE' hoặc details.items[] tồn tại
   │  → mở Stock Modal "Một số sản phẩm không đủ hàng"
   │     ├─ nút "Cập nhật số lượng" → set qty từng item về availableQuantity
   │     └─ nút "Xóa khỏi giỏ" → remove các item lỗi
   └─ ngược lại
      → mở Payment Modal "Thanh toán thất bại"
         ├─ nút "Thử lại" → submit lại
         └─ nút "Đổi phương thức thanh toán" → đóng modal
err.code === 'UNAUTHORIZED' → http.ts đã tự redirect, không cần xử
err.code === 'FORBIDDEN'/'NOT_FOUND'/default → toast "Đã có lỗi, vui lòng thử lại"
```

> **Ví dụ flow B2 (stock conflict)**: Bạn có 5 cái laptop X trong giỏ, nhưng kho chỉ còn 3. Submit checkout → backend trả 409 + `details.items: [{productId, requestedQuantity:5, availableQuantity:3}]` → frontend mở modal kèm danh sách. Bấm "Cập nhật số lượng" → giỏ tự đổi 5 → 3, modal đóng, submit lại → đặt hàng thành công.

### 4-03: UAT walkthrough + dọn mock + README — ⏳ chưa chạy

Còn lại bộ checklist UAT (`04-UAT.md` đã có template) cần chạy thủ công trên docker-compose live, gồm 6 happy path A1–A6 + 5 failure case B1–B5. Sau đó dọn nốt các mock chưa thay (admin pages, search page, profile/orders/[id]) và cập nhật README phase 4.

---

## Trạng thái tổng quan (tính đến 2026-04-25)

| Phase | Mục tiêu | Plan hoàn thành | Trạng thái |
|-------|----------|-----------------|------------|
| 1 | Hợp đồng API + Swagger | 3/3 | ✅ Hoàn tất 22/04 |
| 2 | CRUD trên 6 service | 3/3 | ✅ Hoàn tất 22/04 |
| 3 | Validation + xử lý lỗi | 2/2 | ✅ Hoàn tất 23/04 |
| 4 | Frontend alignment + E2E | 2/3 | ⏳ Đang làm — wave 3 chưa chạy |

---

## Tóm 1 câu cho mỗi phase

1. **Phase 1** — *"Mọi service nói cùng một thứ tiếng"*: cùng response envelope, cùng error schema, cùng có Swagger.
2. **Phase 2** — *"Mọi service đều có đầy đủ tủ đồ CRUD"*: tạo/đọc/sửa/xoá nhất quán, soft-delete, phân trang chuẩn.
3. **Phase 3** — *"Mọi lỗi đều khoác đúng đồng phục"*: validation, auth, conflict, internal — đều bọc đúng khuôn, không lộ thông tin nhạy cảm.
4. **Phase 4** — *"Frontend mặc đúng size đồng phục đó"*: codegen ra type, HTTP wrapper bóc envelope, page hiện UI khôi phục lỗi rõ ràng.
