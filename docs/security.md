# Bảo mật — Edge Authentication tại API Gateway

**Phiên bản:** Phase 25 (Gateway JWT Edge Authentication)
**Cập nhật:** 2026-05-21

Tài liệu này mô tả mô hình xác thực ở biên (edge authentication) của hệ thống:
API Gateway là nơi đặt trust boundary — verify JWT, loại bỏ header tin cậy do
client tự gửi, và inject lại danh tính người dùng đã xác thực cho các service nội bộ.

---

## 1. Trust boundary

```
                          trust boundary
                               │
  [Browser] ──HTTPS──> [API Gateway :8080] ──Docker network──> [Backend services]
                               ▲                                user / product / order /
                               │ JWT verify ở đây                payment / inventory /
                               │ Strip & inject X-User-Id ở đây   notification
                               │
```

- **Phía ngoài biên (untrusted):** trình duyệt / client. Mọi header do client gửi
  đều KHÔNG đáng tin.
- **Phía trong biên (trusted):** Docker network nội bộ. 6 backend service KHÔNG
  expose port ra host (Phase 25) — chỉ truy cập được qua API Gateway cổng 8080.
- API Gateway là **điểm kiểm soát duy nhất**: verify JWT HS256 (chia sẻ
  `JWT_SECRET` với user-service), rồi cấp danh tính tin cậy cho downstream.

---

## 2. Threat: client giả mạo `X-User-Id`

### Vector tấn công (trước Phase 25)

Backend service nhận `userId` từ header `X-User-Id`. Trước Phase 25, header này
do **frontend tự gắn** và gateway route thẳng xuống service mà không kiểm tra.
Hệ quả: một user đã đăng nhập (hoặc bất kỳ ai biết endpoint) có thể gọi:

```
GET /api/orders
Authorization: Bearer <token-cua-ke-tan-cong>
X-User-Id: <uuid-cua-nan-nhan>
```

và xem được đơn hàng của nạn nhân. Đây chính là sự cố `orders-cross-user-leak`.

### Cách Phase 25 vá

`JwtAuthenticationFilter` (Spring Cloud Gateway `GlobalFilter`, chạy sớm nhất)
thực hiện, theo thứ tự, cho **mọi** request:

1. **Strip vô điều kiện** các header tin cậy do client gửi:
   `X-User-Id`, `X-User-Roles`, `X-Username`, `X-Request-Id`.
2. Parse `Authorization: Bearer <token>` (nếu có) → verify chữ ký HS256 + hạn.
3. Endpoint protected mà không có token hợp lệ → trả `401`.
4. **Inject lại** header tin cậy lấy TỪ claim của token đã verify:
   - `X-User-Id` = claim `sub`
   - `X-User-Roles` = claim `roles`
   - `X-Username` = claim `username`

Vì bước 1 luôn xoá header client gửi, kẻ tấn công không thể giả mạo `X-User-Id`:
giá trị downstream nhận được luôn là `sub` của chính token họ trình ra.

---

## 3. Allow-list endpoint public

Một số endpoint cần truy cập khi chưa đăng nhập. Allow-list được cấu hình trong
`api-gateway/src/main/resources/application.yml` (`app.auth.public-endpoints`),
khớp bằng `AntPathMatcher` theo cặp (method, pattern):

| Method  | Pattern                    | Lý do public                                        |
| ------- | -------------------------- | --------------------------------------------------- |
| POST    | `/api/users/auth/login`    | Khởi tạo session — chưa có token thì mới phải login. |
| POST    | `/api/users/auth/register` | Tạo tài khoản mới — chưa có token.                   |
| GET     | `/api/products`            | Duyệt catalog ẩn danh (khách vãng lai xem hàng).     |
| GET     | `/api/products/**`         | Chi tiết sản phẩm, review — đọc công khai.           |
| OPTIONS | `/**`                      | CORS preflight — trình duyệt gửi tự động, không kèm token. |
| GET     | `/actuator/health`         | Health check cho orchestrator / load balancer.      |

**Quy tắc với endpoint public (D-05):** nếu request có `Bearer` hợp lệ, gateway
vẫn parse và inject `X-User-Id` (hữu ích khi user đã đăng nhập vẫn duyệt catalog).
Nếu `Bearer` sai/hết hạn thì vẫn trả `401` — không "im lặng bỏ qua" để tránh
nhầm lẫn khi debug.

---

## 4. Bảo vệ endpoint admin

Cấu hình `app.auth.admin-path-patterns`:

| Pattern             | Yêu cầu                       |
| ------------------- | ----------------------------- |
| `/api/*/admin/**`   | claim `roles` phải chứa `ADMIN` |
| `/api/admin/**`     | claim `roles` phải chứa `ADMIN` |

Token thiếu role `ADMIN` khi gọi endpoint admin → gateway trả `403` với
`code = AUTH_ROLE_DENIED`. Đây là kiểm tra **role coarse-grained** ở biên; backend
vẫn có thể kiểm tra quyền chi tiết hơn nếu cần.

### Hợp đồng lỗi (error contract)

Mọi phản hồi lỗi auth dùng format `ApiErrorResponse` chuẩn của gateway:

| HTTP | `code`               | Khi nào                                     |
| ---- | -------------------- | ------------------------------------------- |
| 401  | `AUTH_TOKEN_MISSING` | Endpoint protected, thiếu `Authorization`.  |
| 401  | `AUTH_TOKEN_INVALID` | Token malformed / sai chữ ký / thiếu `sub`. |
| 401  | `AUTH_TOKEN_EXPIRED` | Token hết hạn (tách riêng cho FE xử lý).    |
| 403  | `AUTH_ROLE_DENIED`   | Thiếu role `ADMIN` cho endpoint admin.      |

---

## 5. Cảnh báo `JWT_SECRET` cho production

`api-gateway` và `user-service` chia sẻ cùng biến môi trường `JWT_SECRET`
(user-service issue token, gateway verify).

```yaml
app:
  jwt:
    secret: ${JWT_SECRET:dev-jwt-secret-key-minimum-32-characters-for-hs256-ok}
```

Giá trị mặc định `dev-jwt-secret-...` **CHỈ dùng cho môi trường phát triển**.

> ⚠️ **Production BẮT BUỘC** đặt biến môi trường `JWT_SECRET` bằng một chuỗi
> ngẫu nhiên ≥ 32 ký tự (yêu cầu của HS256). KHÔNG dùng giá trị dev mặc định —
> nó nằm công khai trong source code, ai cũng có thể tự ký token hợp lệ.

---

## 6. Defense in depth — hai lớp bảo vệ

Lỗ hổng `orders-cross-user-leak` được vá ở **hai tầng độc lập**:

1. **Lớp 1 — Service-side ownership check** (commit `e7f72fb`): order-service
   khi đọc/sửa đơn hàng so khớp `userId` của đơn với `X-User-Id` nhận được; lệch
   → từ chối. Lớp này chặn "leak" kể cả khi `X-User-Id` bị sai.
2. **Lớp 2 — Gateway trust boundary** (Phase 25, tài liệu này): gateway strip
   `X-User-Id` client gửi và inject lại từ JWT `sub`. Lớp này vá tận gốc —
   client không còn khả năng cung cấp `X-User-Id` tùy ý.

Hai lớp bổ sung cho nhau: ngay cả khi một lớp bị cấu hình sai, lớp còn lại vẫn
giữ được tính đúng đắn.

---

## 7. Phạm vi để lại sau (deferred)

Các hạng mục dưới đây nằm ngoài phạm vi Phase 25, ghi nhận để cân nhắc về sau:

- **D-25 — Refresh token / sliding session:** hiện dùng HS256 stateless, hạn 24h,
  không có refresh. Token hết hạn → user đăng nhập lại.
- **D-26 — Rate limit theo user ở gateway:** chưa có; chống brute-force/DoS để sau.
- **D-27 — Audit log mọi request:** hiện chỉ log thất bại xác thực, chưa log đầy
  đủ "ai gọi gì lúc nào".
- **D-28 — Đưa `JWT_SECRET` vào Docker secrets / Vault:** hiện truyền qua biến
  môi trường; quản lý secret tập trung để sau.
- mTLS giữa gateway và service, OAuth2/OIDC, token revocation list, migrate
  RS256 — tất cả defer.
