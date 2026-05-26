# Phase 25 — Summary: Gateway JWT Edge Authentication

**Hoàn thành:** 2026-05-21
**Branch:** workspace/phase-24-25-microservice-hardening

## Mục tiêu đạt được

Chuyển **trust boundary** của hệ thống về API Gateway. Trước Phase 25, backend
service tin tuyệt đối header `X-User-Id` do client gửi — bất kỳ ai cũng có thể
giả mạo `X-User-Id` để impersonate (gốc rễ sự cố `orders-cross-user-leak`).

Phase 25 đặt một `GlobalFilter` ở gateway: verify JWT HS256, **strip** mọi header
tin cậy client gửi, **inject lại** `X-User-Id` / `X-User-Roles` / `X-Username` từ
claim của token đã verify. Đồng thời đóng port của 6 backend service — chúng chỉ
còn truy cập được qua gateway cổng 8080.

## Commits

| Plan  | Commit    | Nội dung                                                                                  |
| ----- | --------- | ----------------------------------------------------------------------------------------- |
| 25-01 | `46772a2` | JwtVerifier + JwtAuthenticationFilter + AuthProperties + 2 exception; JJWT 0.12.7 deps; allow-list `application.yml`; JwtVerifierTest (4 case) |
| 25-02 | `3a598db` | AuthErrorResponseWriter (error JSON `ApiErrorResponse`); docker-compose bỏ port 6 backend service; thêm `JWT_SECRET` env cho gateway + user-service |
| 25-03 | `5ca915e` | FE refactor: xoá `cartUserHeaders`/`orderUserHeaders`/khối `X-User-Id` coupons; bỏ param `userId` khỏi `createOrder`/`validateCoupon`/`useApplyCoupon`; checkout bỏ truyền `user?.id` |
| 25-04 | `987ef14` | JwtAuthenticationFilterTest (8 case); Playwright `cross-user-leak.spec.ts` (3 case)        |
| 25-05 | (commit này) | `docs/security.md`; `25-VERIFICATION.md`; `25-SUMMARY.md`; README link Security          |

## File thay đổi

**Mới (10 file):**

- `api-gateway/.../auth/JwtVerifier.java` — parse + verify HS256, record `VerifiedClaims`.
- `api-gateway/.../auth/JwtAuthenticationFilter.java` — `GlobalFilter`: strip + verify + inject.
- `api-gateway/.../auth/AuthProperties.java` — `@ConfigurationProperties` allow-list.
- `api-gateway/.../auth/AuthErrorResponseWriter.java` — ghi response lỗi auth JSON.
- `api-gateway/.../auth/TokenExpiredException.java`, `TokenInvalidException.java`.
- `api-gateway/.../test/auth/JwtVerifierTest.java`, `JwtAuthenticationFilterTest.java`.
- `frontend/e2e/cross-user-leak.spec.ts` — Playwright regression-guard.
- `docs/security.md` — tài liệu edge authentication (7 mục).

**Sửa (9 file):**

- `api-gateway/pom.xml` — 3 deps JJWT 0.12.7.
- `api-gateway/.../ApiGatewayApplication.java` — `@EnableConfigurationProperties`.
- `api-gateway/.../resources/application.yml` — block `app.jwt` + `app.auth`.
- `docker-compose.yml` — bỏ `ports` 6 service, thêm `JWT_SECRET` env.
- `frontend/src/services/{cart,orders,coupons,http}.ts` — bỏ X-User-Id thủ công.
- `frontend/src/hooks/useApplyCoupon.ts`, `app/checkout/page.tsx` — cập nhật call-site.

## Thay đổi breaking

- **Backend service không còn expose host port** (8081–8086). Mọi truy cập đi qua
  `localhost:8080` (API Gateway). Tích hợp/test cũ gọi thẳng service phải đổi.
- **Endpoint protected bắt buộc Bearer JWT.** Request không token (ngoài allow-list
  public) nhận `401`. Frontend đã tự đính `Authorization` qua `services/http.ts`.
- **Developer pull branch này** nên xoá token cũ trong `localStorage` nếu token đó
  được ký bằng `JWT_SECRET` khác — token sai chữ ký sẽ bị gateway từ chối `401`.

## Defense in depth — vá `orders-cross-user-leak` ở 2 lớp

- **Lớp 1** — service-side ownership check (commit `e7f72fb`, trước Phase 25):
  order-service so khớp `userId` của đơn với `X-User-Id`; lệch → từ chối.
- **Lớp 2** — gateway trust boundary (Phase 25): gateway strip `X-User-Id` client
  gửi và inject lại từ JWT `sub`; client không còn cấp được `userId` tùy ý.

Chi tiết threat model: `docs/security.md` mục 2 và 6.

## Verification

Xem `25-VERIFICATION.md`. Code-level checks PASS (kiểm tra thủ công chữ ký +
import + call-site). Runtime check (Docker / Maven / Playwright) defer cho user
chạy theo checklist — môi trường session không có Docker daemon, Maven CLI, hay
`node_modules` frontend.

## Deferred ideas (từ CONTEXT D-25..D-28)

- **D-25** — Refresh token / sliding session (hiện HS256 stateless 24h).
- **D-26** — Rate limit theo user ở gateway.
- **D-27** — Audit log đầy đủ mọi request (hiện chỉ log thất bại auth).
- **D-28** — Đưa `JWT_SECRET` vào Docker secrets / Vault.
- mTLS gateway↔service, OAuth2/OIDC, token revocation, migrate RS256 — defer.

## Known limitation

- `types/api/orders.generated.ts` vẫn còn field optional `"X-User-Id"?: string`
  (D-15) — không phá compile vì FE không gửi nữa; regenerate từ OpenAPI để sau.
- Unit test (`mvn test`) và E2E (`playwright test`) chưa chạy trong session tạo
  code do thiếu tooling — đã viết đầy đủ, người dùng chạy theo `25-VERIFICATION.md`.

## Self-Check: PASSED

- 10 file mới đều tồn tại trên đĩa (xác nhận qua công cụ Write).
- 5 commit (`46772a2`, `3a598db`, `5ca915e`, `987ef14` + commit docs này) có trong
  `git log` của branch `workspace/phase-24-25-microservice-hardening`.
