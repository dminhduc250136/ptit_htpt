# Phase 6: Real Auth Flow - Context

**Gathered:** 2026-04-26
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 6 nối FE mock auth với backend JWT thật: backend ship `/api/users/auth/{register,login,logout}`, FE gỡ mock, JWT được lưu vào localStorage + `auth_present` cookie, session persist sau page reload, protected routes redirect đúng khi không có session, admin routes có role check.

**Trong scope:** Backend AuthController (register/login/logout) + JWT issue/verify; FE gỡ mock trong login/register pages; middleware update (thêm /account/*, role check ADMIN); session hydration từ localStorage khi mount; /403 page cho non-admin; error handling (409 CONFLICT, 401 INVALID_CREDENTIALS).

**Ngoài scope:** Refresh token flow (token.ts đã comment intentionally not exported — defer); OAuth/social login; email verification; password reset; rate limiting / brute-force protection; Admin pages có real data (Phase 7); backend JWT claim verification ở gateway (PROJECT.md defer D14).

</domain>

<decisions>
## Implementation Decisions

### Register Contract
- **D-01:** Backend `/auth/register` nhận `{username, email, password}` — 3 fields. FE register form cập nhật: bỏ `fullName` và `phone`, thêm `username` field, giữ `confirmPassword` (validate FE-side trước khi gửi).
- **D-02:** Login key là **email** — `/auth/login` nhận `{email, password}`. FE form hiện đã dùng email field → không cần đổi label.
- **D-03:** Backend trả `{accessToken, user}` cả khi register lẫn login (cùng AuthResponse shape). FE `services/auth.ts` đã handle case này (`if (data.accessToken) setTokens(...)`).

### Post-Register Flow
- **D-04:** Backend trả token + user ngay khi register thành công → FE auto-login (lưu token + set AuthProvider state) → redirect về `/` (trang chủ). Không redirect về /login.

### Logout Approach
- **D-05:** Client-side discard only. Backend `/auth/logout` trả `200 OK` (không cần body, không blacklist). FE xóa `accessToken`, `refreshToken` khỏi localStorage và zero `auth_present` cookie. Token vẫn valid đến khi hết hạn — chấp nhận cho MVP.
- **D-06:** JWT expiration = **24 giờ**. Claim: `sub=userId`, `username`, `roles`, `exp`. Algorithm: HS256. Secret qua env var `JWT_SECRET`.

### Middleware + Route Protection
- **D-07:** Middleware matcher update thêm `/account/*`:
  ```
  matcher: ['/checkout/:path*', '/profile/:path*', '/admin/:path*', '/account/:path*']
  ```
- **D-08:** Admin role check trong middleware: FE set cookie `user_role=<ROLE>` (non-httpOnly) khi login. Middleware đọc `user_role` cookie để check ADMIN cho `/admin/*`. Không parse JWT trong Edge runtime (không an toàn nếu không verify signature).
- **D-09:** Non-admin user truy cập `/admin/*` → redirect về `/403`. Cần tạo `/403` page (đơn giản, không cần elaborate).
- **D-10:** Unauthenticated user truy cập protected routes → redirect về `/login?returnTo=<path>` (behavior hiện tại giữ nguyên).

### JWT Implementation
- **D-11:** Thư viện JWT backend: `io.jsonwebtoken:jjwt` (JJWT) — standard cho Spring Boot, không cần Spring Security full setup (giữ minimal, visible-first).
- **D-12:** `AuthController` là controller mới trong user-service, path `/api/users/auth/{register,login,logout}`. Không touch `UserProfileController` hay `AdminUserController`.

### Claude's Discretion
- Chi tiết JJWT version và config — researcher/planner chọn compatible với Spring Boot 3.3.2.
- BCrypt password encoder bean config — standard `@Bean PasswordEncoder` trong user-service.
- Error message text cho 409/401 responses — planner theo `ApiErrorResponse` envelope pattern.
- `/403` page design — minimal, chỉ cần "Không có quyền truy cập" + link về trang chủ.
- UserDto shape trong AuthResponse — dùng `UserDto` hiện có (id, username, email, roles, createdAt, updatedAt).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Roadmap & Requirements
- `.planning/ROADMAP.md` §"Phase 6: Real Auth Flow" — goal, success criteria, REQ mapping (AUTH-01..06)
- `.planning/REQUIREMENTS.md` §"C1. Auth Flow Thật" — 6 atomic requirements với behavioral spec

### Codebase Maps
- `.planning/codebase/STACK.md` — Spring Boot 3.3.2, Java 17, Maven multi-module
- `.planning/codebase/CONVENTIONS.md` — package layout (api/domain/repository/service/web), ApiErrorResponse pattern

### Existing Code (MUST read)
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/domain/UserEntity.java` — entity Phase 5 (username/email/passwordHash/roles fields)
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/domain/UserDto.java` — wire format (không có passwordHash)
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/service/UserCrudService.java` — service layer hiện tại
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/api/` — ApiErrorResponse + GlobalExceptionHandler pattern
- `sources/frontend/src/services/auth.ts` — FE auth service (đã implement login/register/logout, chờ backend)
- `sources/frontend/src/services/token.ts` — token storage (localStorage + auth_present cookie)
- `sources/frontend/src/providers/AuthProvider.tsx` — React Context auth state
- `sources/frontend/src/app/login/page.tsx` — login page (mock submit cần gỡ)
- `sources/frontend/src/app/register/page.tsx` — register page (mock submit + form fields cần gỡ)
- `sources/frontend/middleware.ts` — route protection (cần update matcher + role check)
- `sources/backend/user-service/src/main/resources/application.yml` — cần thêm JWT config
- `sources/backend/user-service/pom.xml` — cần thêm JJWT dependency

### Phase 5 Context (prior decisions)
- `.planning/phases/05-database-foundation/05-CONTEXT.md` — D-11 (localStorage/cookie pattern), D-04 (UUID String ID), entity/DTO separation

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`services/auth.ts`**: Đã implement `login()`, `register()`, `logout()` — gọi đúng endpoint `/api/users/auth/*`. Chỉ cần gỡ mock trong pages, không cần viết lại service.
- **`services/token.ts`**: `setTokens()`, `clearTokens()`, `getAccessToken()` — hoạt động, SSR-safe. Cần thêm `setUserRole()` để set `user_role` cookie khi login (D-08).
- **`providers/AuthProvider.tsx`**: Hydrate từ localStorage khi mount, expose `useAuth()`. Đã xử lý cross-tab storage sync.
- **`ApiErrorResponse` + `GlobalExceptionHandler`**: 5 services đã có — `AuthController` dùng cùng pattern để trả 409/401.
- **`UserCrudService`**: Có `findByEmail()` hoặc dễ add — dùng để verify credentials khi login.
- **`UserEntity.create()`**: Factory method sẵn — dùng trong register để tạo entity mới.

### Established Patterns
- **Error envelope**: `ApiErrorResponse { message, errorCode, traceId, fieldErrors? }` — `AuthController` dùng pattern này.
- **Controller pattern**: `@RestController @RequestMapping` với `@Validated` — theo `UserProfileController.java`.
- **FE form validation**: Client-side validate trước khi submit (xem login/register pages hiện tại).

### Integration Points
- **user-service pom.xml**: Thêm `io.jsonwebtoken:jjwt-api`, `jjwt-impl`, `jjwt-jackson` (runtime scope).
- **application.yml user-service**: Thêm `app.jwt.secret` + `app.jwt.expiration-ms` config.
- **FE login/register pages**: Thay mock submit bằng `await login(...)` / `await register(...)` từ `services/auth.ts`.
- **FE token.ts**: Thêm `setUserRole(role: string)` + `clearUserRole()` để manage `user_role` cookie.
- **FE middleware.ts**: Update matcher + thêm logic đọc `user_role` cookie cho `/admin/*`.

### Constraints / Gotchas
- **Middleware Edge runtime**: Không thể import `jsonwebtoken` hay Node crypto modules. Dùng cookie `user_role` thay vì parse JWT.
- **JJWT với Spring Boot 3.3.2**: Cần jjwt >= 0.12.x (tương thích Java 17 + Jakarta). Researcher verify version.
- **UserRepository findByEmail**: Hiện `UserCrudService` có thể chỉ có `findById`. Cần thêm `findByEmail(String email)` vào `UserRepository` interface.
- **BCrypt PasswordEncoder**: Cần khai báo `@Bean` trong user-service (không có Spring Security auto-config nếu không dùng full Spring Security). Dùng `new BCryptPasswordEncoder()`.
- **`confirmPassword` FE-only**: Không gửi lên backend — validate FE rồi chỉ gửi `{username, email, password}`.

</code_context>

<specifics>
## Specific Ideas

- **Register form update**: Bỏ `fullName` và `phone` fields, thêm `username` field. Giữ `confirmPassword` để validate FE-side.
- **AuthResponse shape**: `{ accessToken: string, user: UserDto }` — không trả `refreshToken` (intentionally omitted per token.ts comment).
- **JWT secret**: `JWT_SECRET` env var trong docker-compose, fallback dev value trong application.yml (không hardcode trong prod).
- **`/403` page**: Đơn giản — "Bạn không có quyền truy cập trang này" + button "Về trang chủ".
- **`user_role` cookie**: `user_role=ADMIN` hoặc `user_role=USER`, non-httpOnly, `SameSite=Lax`, `Max-Age=2592000` (đồng bộ với `auth_present`). Clear cùng lúc với `auth_present` khi logout.

</specifics>

<deferred>
## Deferred Ideas

- **Refresh token flow** — token.ts đã comment "intentionally not exported". Khi JWT 24h hết hạn, user bị kick và phải login lại. Defer refresh flow sang v1.2 hardening.
- **Email verification sau register** — không cần cho MVP thử nghiệm.
- **Password reset / forgot password** — defer.
- **Rate limiting / brute-force protection** trên `/auth/login` — backend hardening invisible, defer.
- **Gateway JWT claim verification** (D14 từ v1.0 audit) — thay X-User-Id header trust bằng JWT verify — defer security hardening.
- **`fullName` và `phone` fields** — user muốn xóa khỏi register form. Nếu sau này cần user profile phong phú hơn, tạo "Edit Profile" phase riêng.

</deferred>

---

*Phase: 06-real-auth-flow*
*Context gathered: 2026-04-26*
