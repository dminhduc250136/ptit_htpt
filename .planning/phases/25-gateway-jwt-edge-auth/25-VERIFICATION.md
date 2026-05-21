# Phase 25 — Verification

**Phase:** 25 Gateway JWT Edge Authentication
**Trạng thái:** Checklist thủ công — môi trường thực thi (Maven CLI / Docker
daemon / Playwright runtime) KHÔNG có sẵn khi viết code. Người dùng chạy các
bước dưới đây trên máy có Docker để xác nhận.

---

## A. Build & unit test (cần Maven hoặc build qua Docker)

| # | Lệnh                                                              | Kỳ vọng                                   |
| - | ----------------------------------------------------------------- | ----------------------------------------- |
| A1 | `cd sources/backend/api-gateway && mvn test`                     | `JwtVerifierTest` (4) + `JwtAuthenticationFilterTest` (8) đều xanh |
| A2 | `cd sources/backend/api-gateway && mvn package`                  | Build OK, sinh `api-gateway-*.jar`        |
| A3 | `docker compose build api-gateway`                               | Image build OK (JJWT 0.12.7 trên classpath) |

> Lưu ý: môi trường tạo code KHÔNG có `mvn`/`mvnw`. Code đã được kiểm tra thủ
> công về chữ ký + import. A1/A2 cần chạy trên máy có Maven, hoặc dựa vào A3
> (build qua Dockerfile).

## B. Network isolation — backend service không expose port (D-12, D-13)

Chạy `docker compose up -d --build`, rồi:

| # | Lệnh                                            | Kỳ vọng                                |
| - | ----------------------------------------------- | -------------------------------------- |
| B1 | `curl -v http://localhost:8081/users`          | Connection refused (user-service đã bỏ port) |
| B2 | `curl -v http://localhost:8082/products`       | Connection refused                     |
| B3 | `curl -v http://localhost:8083/orders`         | Connection refused                     |
| B4 | `curl -v http://localhost:8084/payments`       | Connection refused                     |
| B5 | `curl -v http://localhost:8085/inventory`      | Connection refused                     |
| B6 | `curl -v http://localhost:8086/notifications`  | Connection refused                     |
| B7 | `curl -s http://localhost:8080/actuator/health` | 200 — chỉ gateway expose port          |

## C. Allow-list — endpoint public (D-04, D-05)

| # | Lệnh                                                            | Kỳ vọng                          |
| - | --------------------------------------------------------------- | -------------------------------- |
| C1 | `curl -i http://localhost:8080/api/products`                   | 200 (không cần Bearer)           |
| C2 | `curl -i http://localhost:8080/api/products/<id>`              | 200 (không cần Bearer)           |
| C3 | `curl -i -X OPTIONS http://localhost:8080/api/orders`          | 200/204 (CORS preflight pass)    |

## D. Endpoint protected — verify JWT (D-09)

| # | Lệnh                                                                                       | Kỳ vọng                              |
| - | ------------------------------------------------------------------------------------------ | ------------------------------------ |
| D1 | `curl -i http://localhost:8080/api/orders`                                                | 401, body `code=AUTH_TOKEN_MISSING`  |
| D2 | `curl -i http://localhost:8080/api/orders -H "Authorization: Bearer rac"`                 | 401, body `code=AUTH_TOKEN_INVALID`  |
| D3 | Token hết hạn (xem ghi chú dưới) → `curl ... -H "Authorization: Bearer <expired>"`         | 401, body `code=AUTH_TOKEN_EXPIRED`  |
| D4 | Login lấy token hợp lệ → `curl -i http://localhost:8080/api/orders -H "Authorization: Bearer <token>"` | 200, trả đơn của chính user    |

> D3: token hết hạn khó tạo thủ công (hạn mặc định 24h). Trường hợp này đã được
> cover bởi unit test `JwtAuthenticationFilterTest.protectedEndpoint_expiredToken_returns401Expired`
> (token issue với `exp` quá khứ). Chấp nhận skip ở manual nếu không tiện.

## E. Chống giả mạo X-User-Id — vá `orders-cross-user-leak` (D-21)

Chuẩn bị: login 2 user (alice, bob) qua `POST /api/users/auth/login`, lấy token
và `sub` (decode JWT). Sau đó:

| # | Lệnh                                                                                                                  | Kỳ vọng                                       |
| - | --------------------------------------------------------------------------------------------------------------------- | --------------------------------------------- |
| E1 | `curl -s http://localhost:8080/api/orders -H "Authorization: Bearer <bob>" -H "X-User-Id: <alice-id>"`                | Chỉ trả đơn của **Bob** — gateway strip header giả |
| E2 | Kiểm tra mọi `order.userId` trong response E1                                                                         | Không phần tử nào bằng `<alice-id>`           |

## F. Bảo vệ endpoint admin (D-10)

| # | Lệnh                                                                                            | Kỳ vọng                            |
| - | ----------------------------------------------------------------------------------------------- | ---------------------------------- |
| F1 | `curl -i http://localhost:8080/api/users/admin/users -H "Authorization: Bearer <user-thuong>"` | 403, body `code=AUTH_ROLE_DENIED`  |
| F2 | `curl -i http://localhost:8080/api/users/admin/users -H "Authorization: Bearer <admin>"`       | 200                                |

## G. Playwright E2E (cần full stack chạy)

```
cd sources/frontend
npx playwright test cross-user-leak
```

| # | Test case                                                          | Kỳ vọng |
| - | ------------------------------------------------------------------ | ------- |
| G1 | Bob giả mạo X-User-Id của Alice → chỉ thấy đơn của Bob            | PASS    |
| G2 | Endpoint protected không Bearer → 401 AUTH_TOKEN_MISSING          | PASS    |
| G3 | Endpoint protected với Bearer rác → 401 AUTH_TOKEN_INVALID        | PASS    |

> Lưu ý: `playwright.config.ts` có `globalSetup` login UI ở `localhost:3000`.
> Phải chạy cả frontend (cổng 3000) lẫn gateway (8080) trước khi chạy test.
> Spec `cross-user-leak.spec.ts` tự đăng ký 2 user mới nên không phụ thuộc seed.

## H. Frontend — bỏ X-User-Id thủ công (D-14)

| # | Kiểm tra                                                                       | Kỳ vọng                          |
| - | ------------------------------------------------------------------------------ | -------------------------------- |
| H1 | `grep -rn "X-User-Id" sources/frontend/src/`                                  | Chỉ còn comment + type generated, không còn code set header |
| H2 | `grep -rn "_userHeaders\|getCurrentUserId" sources/frontend/src/services/`    | Rỗng (đã xoá)                    |
| H3 | `cd sources/frontend && npx tsc --noEmit`                                     | Không lỗi type (xem ghi chú)     |
| H4 | Smoke thủ công: login → thêm giỏ → checkout → đặt đơn                         | Đặt đơn thành công               |

> H3: môi trường tạo code KHÔNG có `node_modules` (chưa `npm install`) nên
> typecheck CHƯA chạy được khi viết code. Refactor chỉ xoá tham số `userId` và
> các hàm helper nội bộ — đã kiểm tra thủ công mọi call-site
> (`useApplyCoupon.ts`, `checkout/page.tsx`) đã cập nhật đồng bộ. Người dùng
> chạy `npm install && npx tsc --noEmit` để xác nhận lại.

---

## Đối chiếu Decision (CONTEXT)

| Decision | Mô tả ngắn                                  | Trạng thái |
| -------- | ------------------------------------------- | ---------- |
| D-01..03 | JwtAuthenticationFilter + JwtVerifier + secret | ✅ Code xong |
| D-04..05 | Allow-list config-driven, public parse token | ✅ Code xong |
| D-06..08 | Strip + inject header, anonymous pass        | ✅ Code xong |
| D-09..11 | Error contract AUTH_* dùng ApiErrorResponse  | ✅ Code xong |
| D-12..13 | docker-compose bỏ port 6 service             | ✅ Code xong — xác nhận runtime mục B |
| D-14..16 | FE bỏ X-User-Id, http.ts 401 handling        | ✅ Code xong — xác nhận mục H |
| D-17..19 | Backend giữ nguyên signature                 | ✅ Không đổi (đúng kế hoạch) |
| D-20     | JwtAuthenticationFilterTest 7+ case          | ✅ 8 case viết xong — chạy mục A1 |
| D-21     | Playwright cross-user-leak.spec.ts           | ✅ Viết xong — chạy mục G |
| D-22     | Integration test backend giữ nguyên          | ✅ Không đụng |
| D-23..24 | docs/security.md + summary note lớp 2         | ✅ Xong |
