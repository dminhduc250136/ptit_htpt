# Phase 25: Gateway JWT Edge Authentication - Context

**Gathered:** 2026-05-20
**Status:** Ready for planning
**Depends on:** Không cứng. Có thể làm song song Phase 23/24. Nếu làm sau Phase 24 thì docker-compose merge sạch hơn (vì cùng động `ports` mapping).

<domain>
## Phase Boundary

Phase này vá lỗ hổng kiến trúc bảo mật: hiện tại các backend service **tin tưởng tuyệt đối** header `X-User-Id` đến từ client. Client có thể gọi `api-gateway:8080/api/orders` với `X-User-Id: <bất-kỳ-UUID-nào>` để impersonate. Lỗ hổng này đã từng gây ra incident `orders-cross-user-leak` (commit e7f72fb) — ownership check ở service-side mới chỉ vá phần "leak" chứ chưa vá nguồn gốc "trust client".

Phase 25 chuyển trust boundary về **API Gateway**: gateway parse + verify JWT (HS256, dùng JWT_SECRET đã chia sẻ với user-service), strip mọi `X-User-Id` từ client, rồi inject `X-User-Id` đáng tin cậy (từ `sub` claim) vào downstream request. Đồng thời đóng port 8081–8086 (không expose service nội bộ ra host).

**Trong scope:**
- Thêm Spring Cloud Gateway global filter `JwtAuthenticationFilter` ở api-gateway:
  - Parse Bearer token → verify với JWT_SECRET → extract `sub` (userId) + `roles` claim.
  - Token thiếu/invalid trên endpoint protected → 401 với `ApiErrorResponse` format chuẩn.
  - Token hợp lệ → strip mọi header `X-User-Id` từ request gốc + inject `X-User-Id: <sub>` + `X-User-Roles: <roles csv>`.
- **Allow-list endpoint public** (bypass JWT): `POST /api/users/auth/login`, `POST /api/users/auth/register`, `GET /api/products/**` (catalog browse), `GET /api/products/*/reviews` (read), CORS preflight `OPTIONS *`. Mọi endpoint khác bắt buộc JWT.
- **Allow-list role-protected**: `/api/*/admin/**` yêu cầu `roles` contain `ADMIN` — gateway reject 403 nếu không có.
- Bỏ port mapping của 6 backend service trong docker-compose (`user-service:8081`, ... `notification-service:8086` → bỏ `ports:` block). Chỉ giữ `api-gateway:8080` (+ frontend `3000`, postgres tùy Phase 24).
- Update FE: bỏ logic gửi `X-User-Id` manual (services/cart.ts, services/orders.ts, services/coupons.ts) — chỉ cần `Authorization: Bearer`. Backend lấy user từ header gateway inject.
- Update integration tests + Playwright: gọi qua gateway (`8080`), không gọi service trực tiếp; tests Bearer token issue qua AuthService.
- Verify lỗ hổng `orders-cross-user-leak` đóng được ở tầng kiến trúc: gọi `GET /api/orders` với `X-User-Id: <victim>` từ client → gateway strip header đó → service nhận `X-User-Id` từ token của attacker → trả đơn của attacker chứ không phải victim. Document trong VERIFICATION.

**Ngoài scope (defer):**
- OAuth2/OIDC, refresh token rotation, token revocation list — defer; phase 25 vẫn dùng HS256 stateless 24h.
- mTLS giữa gateway và service — defer.
- Rate limiting / WAF ở gateway — defer (đã có rate limit ở FE Next.js cho chat MVP).
- Token introspection endpoint — defer; gateway verify local với shared secret.
- Service-to-service authentication (vd RabbitMQ consumer khi gọi REST nội bộ) — defer; Phase 23 consumer chỉ touch DB, không call REST.
- Migrate sang RS256 (asymmetric) — defer; vẫn HS256 vì user-svc + gateway có thể share secret qua env.

</domain>

<decisions>
## Implementation Decisions

### Filter Architecture

- **D-01:** Implement `JwtAuthenticationFilter implements GlobalFilter, Ordered` ở api-gateway. **Order < RequestIdFilter** để JWT verify chạy sớm nhất; sau verify, RequestIdFilter vẫn append `X-Request-Id`. Lý do GlobalFilter (không phải GatewayFilterFactory per-route): áp dụng đồng nhất, không phải khai báo trên từng route trong yaml.
- **D-02:** **Library**: dùng cùng `io.jsonwebtoken:jjwt-api:0.12.x` (đã có trong user-svc) — copy `JwtUtils` pattern sang gateway hoặc đưa thành module shared. Phase 25 chọn **copy** (đơn giản, tránh phụ thuộc Maven multi-module trong giới hạn đề tài). File: `apigateway/jwt/JwtVerifier.java` — chỉ giữ verify path, KHÔNG có issue path.
- **D-03:** **JWT_SECRET**: cùng env var `${JWT_SECRET}` cho cả gateway + user-svc. Gateway `application.yml`: `app.jwt.secret: ${JWT_SECRET:dev-jwt-secret-key-minimum-32-characters-for-hs256-ok}`. Cảnh báo: production phải override env, KHÔNG dùng default.

### Allow-List Pattern Matching

- **D-04:** Allow-list config trong `application.yml` (không hardcode):
  ```yaml
  app:
    auth:
      public-paths:
        - { method: POST,    pattern: "/api/users/auth/login" }
        - { method: POST,    pattern: "/api/users/auth/register" }
        - { method: POST,    pattern: "/api/users/auth/forgot-password" }   # nếu có
        - { method: GET,     pattern: "/api/products" }
        - { method: GET,     pattern: "/api/products/**" }
        - { method: GET,     pattern: "/api/products/*/reviews" }
        - { method: GET,     pattern: "/api/products/*/reviews/**" }
        - { method: OPTIONS, pattern: "/**" }
      admin-paths:
        - "/api/*/admin/**"
        - "/api/admin/**"
  ```
  Filter dùng `AntPathMatcher` (đã có trong Spring) để match. Lý do config-driven: dễ audit + dễ chỉnh khi thêm endpoint mới.
- **D-05:** **Public endpoint = không cần JWT NHƯNG nếu có JWT vẫn parse**: nếu request có Bearer hợp lệ → vẫn inject `X-User-Id` (vd để track logged-in user xem catalog). Nếu Bearer invalid trên public endpoint → 401 (không "silently pass" — tránh nhầm lẫn debug). Nếu không có Bearer → pass through (anonymous).

### Header Sanitization

- **D-06:** **Strip rule**: trước khi route, filter **xóa unconditionally** các header sau khỏi request từ client: `X-User-Id`, `X-User-Roles`, `X-Request-Id` (RequestIdFilter sẽ thêm lại). Lý do strip cả `X-Request-Id`: client không được set trace ID giả.
- **D-07:** **Inject rule**: sau JWT verify hợp lệ, mutate request thêm:
  - `X-User-Id: <jwt.sub>`
  - `X-User-Roles: <roles csv, vd "USER" hoặc "USER,ADMIN">`
  - `X-User-Username: <jwt.username>` (optional, hữu ích cho logging downstream)
- **D-08:** **Anonymous request** trên public endpoint: KHÔNG inject `X-User-Id`. Downstream service phải handle null userId (như hiện tại cho `GET /api/products/**`).

### Error Contract

- **D-09:** **401 Missing/Invalid token**: gateway trả `ApiErrorResponse(code, message, traceId, fieldErrors=[])` với HTTP 401.
  - `code=AUTH_TOKEN_MISSING` nếu thiếu Authorization header
  - `code=AUTH_TOKEN_INVALID` nếu malformed / signature fail / expired
  - `code=AUTH_TOKEN_EXPIRED` nếu chỉ expired (tách ra cho FE handle refresh)
- **D-10:** **403 Forbidden role**: `code=AUTH_ROLE_DENIED`, message "User does not have required role for this resource", HTTP 403.
- **D-11:** **Format**: reuse `ApiErrorResponse` class hiện có trong gateway package. Add `ApiErrorResponseBuilder.forAuth(code, message, exchange)` helper.

### Docker / Network Changes

- **D-12:** **Bỏ port mapping** của 6 backend service trong `docker-compose.yml`:
  - `user-service`, `product-service`, `order-service`, `payment-service`, `inventory-service`, `notification-service` — XÓA toàn bộ block `ports:`.
  - Giữ `api-gateway: 8080:8080` và `frontend: 3000:3000` (postgres tùy Phase 24).
- **D-13:** **Verify isolation** sau commit: `curl http://localhost:8081/users` (port user-svc cũ) → connection refused. Document trong VERIFICATION.

### Frontend Changes

- **D-14:** **Loại bỏ thủ công X-User-Id**: refactor 3 file:
  - `services/cart.ts`: xóa `cartUserHeaders()`, mọi call dùng `httpGet/Post` (đã auto-attach Bearer).
  - `services/orders.ts`: xóa `orderUserHeaders()`. (Comment trong file đã ghi "Phase 5 will move to JWT-claim derivation" — chính là phase này.)
  - `services/coupons.ts`: xóa block `if (userId) headers['X-User-Id'] = userId`.
  - `app/checkout/page.tsx`: bỏ truyền `user?.id` vào `createOrder(...)` — chỉ pass body.
- **D-15:** **Generated types**: `types/api/orders.generated.ts` có `"X-User-Id"?: string` — regenerate từ OpenAPI sau khi backend OpenAPI doc cập nhật. Nếu Phase 25 không regenerate kịp, để nguyên (FE không pass header → field optional → không phá compile).
- **D-16:** **Token expiry handling**: FE đã có pattern? Check `services/http.ts` — nếu chưa, thêm: khi nhận 401 với `code=AUTH_TOKEN_EXPIRED`, FE clear localStorage token + redirect `/login?next=<current>`. Defer detailed UX nếu thời gian gấp — chỉ làm clear+redirect basic.

### Backend Changes

- **D-17:** **Backend service**: giữ nguyên `@RequestHeader("X-User-Id")` — KHÔNG đổi controller signature. Trust source thay đổi (từ "client" → "gateway"), nhưng signature giữ. Lý do: zero refactor, ít risk break.
- **D-18:** **Validate `X-User-Id` ở service phía xa**: KHÔNG cần verify lại JWT ở mỗi service (gateway đã verify). Nhưng cần verify format UUID để đề phòng tấn công internal/SSRF. Pattern hiện đã có (`requireUserId()` → throw 401 nếu thiếu). Phase 25 không sửa.
- **D-19:** **Loại bỏ endpoint test hardcode**: nếu có test/dev endpoint nào skip auth bằng `X-User-Id` thẳng (không qua login flow), xóa hoặc gate sau `app.dev-mode=true` flag.

### Testing

- **D-20:** **Gateway unit test**: `JwtAuthenticationFilterTest` — test 6 case bằng `WebTestClient.bindToRouterFunction`:
  1. Public endpoint không Bearer → pass + không có `X-User-Id` downstream.
  2. Public endpoint Bearer hợp lệ → pass + có `X-User-Id`.
  3. Protected endpoint không Bearer → 401 `AUTH_TOKEN_MISSING`.
  4. Protected endpoint Bearer invalid → 401 `AUTH_TOKEN_INVALID`.
  5. Protected endpoint Bearer expired → 401 `AUTH_TOKEN_EXPIRED`.
  6. Admin endpoint Bearer user role → 403 `AUTH_ROLE_DENIED`.
  Plus: client gửi `X-User-Id: forged` cùng Bearer hợp lệ → downstream nhận `X-User-Id` của Bearer, không phải forged.
- **D-21:** **E2E Playwright `cross-user-leak.spec.ts`**:
  - Login as Alice → grab token. Login as Bob → grab token.
  - Bob's browser thử fetch `/api/orders` với header `X-User-Id: <alice-id>` (manual override) — expect chỉ thấy orders của Bob.
  - Bob không có token → expect 401.
  Regression-guard cho `orders-cross-user-leak`.
- **D-22:** **Integration test backend** giữ nguyên (vẫn pass `X-User-Id` trực tiếp vì test gọi service trực tiếp, không qua gateway). Test gateway riêng theo D-20.

### Documentation

- **D-23:** **`docs/security.md`** (tạo mới hoặc extend) — section "Edge Authentication" giải thích trust boundary, threat model, allow-list rationale. Mention `orders-cross-user-leak` đã fix ở 2 tầng.
- **D-24:** **Update commit e7f72fb message reference** — không cần rewrite, chỉ note trong 25-SUMMARY.md là fix tầng 2.

### Scope Boundaries (deferred ideas)

- **D-25:** Refresh token / sliding session — defer.
- **D-26:** Per-user rate limit ở gateway — defer.
- **D-27:** Audit log mỗi request (who called what when) — defer; chỉ log auth failure.
- **D-28:** Move JWT secret sang Docker secrets / Vault — defer.

</decisions>

<reusable_assets>
## Reusable Assets

- **user-svc `JwtUtils.java`**: parse + verify logic dùng JJWT 0.12.x — copy `parseToken()` sang gateway. Bỏ `issueToken()`.
- **user-svc `application.yml` jwt config**: copy `app.jwt.secret` + `app.jwt.expiration-ms` block sang gateway `application.yml`.
- **gateway `ApiErrorResponse` + `GlobalGatewayErrorHandler`**: đã có error contract — chỉ cần thêm 3-4 error code mới (AUTH_TOKEN_*).
- **gateway `RequestIdFilter`**: pattern `GlobalFilter implements Ordered` — copy structure cho `JwtAuthenticationFilter`.
- **FE `services/http.ts`**: đã có Bearer auto-attach (line 80). Đã có error handling 401 — chỉ cần verify ở token-expired code mapping.
- **AntPathMatcher**: Spring built-in, không phải dependency mới.

</reusable_assets>

<open_questions>
## Open Questions (for research phase)

- **Q-01:** Spring Cloud Gateway WebFlux: làm thế nào để mutate request headers (strip + inject) trong `GlobalFilter`? Pattern là `exchange.mutate().request(builder -> builder.headers(...))` — researcher tìm doc chính thức + ví dụ chính xác cho JJWT integration.
- **Q-02:** `AntPathMatcher` cho method-specific matching: Spring native không hỗ trợ "method+pattern" trong 1 object — cần custom record `PublicEndpoint(HttpMethod, String pattern)` + Iterable check. Researcher confirm.
- **Q-03:** JJWT parse trong reactive context (WebFlux) — JJWT block thread. Performance impact đáng kể không với 1 service nhỏ? Researcher khảo sát + đề xuất nếu cần `Mono.fromCallable + subscribeOn(Schedulers.boundedElastic)`.
- **Q-04:** OpenAPI generated types FE: regenerate workflow đã có không? (Phase nào sinh `orders.generated.ts`?) Researcher trace + đề xuất hoặc skip regenerate ở Phase 25.
- **Q-05:** Test forging `X-User-Id` từ Playwright: cần custom fetch hoặc override `Authorization` header? Researcher kiểm tra Playwright API `page.route()` để inject header gốc trước khi gửi.

</open_questions>
