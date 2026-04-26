---
phase: 06-real-auth-flow
verified: 2026-04-26T10:00:00Z
status: human_needed
score: 17/17 must-haves verified
overrides_applied: 0
human_verification:
  - test: "Đăng ký user mới qua FE form (username/email/password) → submit → kiểm tra backend persist UserEntity, FE redirect về /"
    expected: "Backend trả 201 + accessToken non-null; FE redirect về trang chủ; user còn session sau F5 reload"
    why_human: "End-to-end flow cần docker compose up + real Postgres + browser — không thể verify programmatically"
  - test: "Đăng ký trùng username → kiểm tra FE hiện field error đúng copy"
    expected: "Field error: 'Tên đăng nhập này đã được sử dụng' bên dưới username field"
    why_human: "Cần browser render để kiểm tra Banner/field-error display"
  - test: "Đăng ký trùng email → kiểm tra FE hiện field error đúng copy"
    expected: "Field error: 'Email này đã được đăng ký. Đăng nhập' bên dưới email field"
    why_human: "Cần browser render"
  - test: "Login với credentials sai → FE hiện Banner lỗi form-level"
    expected: "Banner hiện 'Email hoặc mật khẩu không chính xác. Vui lòng thử lại' (không highlight field)"
    why_human: "Cần browser để verify Banner render behavior"
  - test: "Logout → truy cập /account/orders → kiểm tra redirect"
    expected: "Redirect về /login?returnTo=%2Faccount%2Forders"
    why_human: "Cookie/session behavior cần browser"
  - test: "Login với USER role → truy cập /admin → kiểm tra redirect"
    expected: "Redirect về /403 page"
    why_human: "user_role cookie và middleware redirect cần browser execution"
  - test: "F5 reload sau login → kiểm tra session persist"
    expected: "Vẫn còn session, không bị redirect về /login (AUTH-06)"
    why_human: "localStorage + cookie persistence cần browser"
---

# Phase 6: Real Auth Flow — Verification Report

**Phase Goal:** User đăng ký + đăng nhập + đăng xuất thật qua backend; JWT issued; FE form gỡ mock; session persist sau page reload; protected routes redirect đúng khi không có session.
**Verified:** 2026-04-26T10:00:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | POST /api/users/auth/register trả 201 + accessToken + UserDto khi credentials hợp lệ | ✓ VERIFIED | AuthController.register() @ResponseStatus(201); AuthService.register() gọi jwtUtils.issueToken() + UserMapper.toDto(); trả AuthResponseDto(token, user) |
| 2 | POST /api/users/auth/register trả 409 khi username hoặc email đã tồn tại | ✓ VERIFIED | AuthService throws ResponseStatusException(CONFLICT) cho cả findByUsername và findByEmail duplicate check |
| 3 | POST /api/users/auth/login trả 200 + accessToken + UserDto khi credentials đúng | ✓ VERIFIED | AuthController.login() trả ApiResponse.of(200,...); AuthService.login() verify BCrypt + issue token |
| 4 | POST /api/users/auth/login trả 401 khi email không tìm thấy hoặc password sai | ✓ VERIFIED | AuthService: orElseThrow → ResponseStatusException(UNAUTHORIZED); passwordEncoder.matches() false → ResponseStatusException(UNAUTHORIZED); cả hai generic "Invalid credentials" (T-06-01) |
| 5 | POST /api/users/auth/logout trả 200 OK | ✓ VERIFIED | AuthController.logout() trả ApiResponse.of(200, "Logged out", null) |
| 6 | accessToken là JWT HS256 24h, claim: sub=userId, username, roles | ✓ VERIFIED | JwtUtils.issueToken(): .subject(userId).claim("username",...).claim("roles",...).expiration(now+expirationMs=86400000).signWith(key, Jwts.SIG.HS256); JJWT 0.12.x API (Jwts.parser().verifyWith().build()) |
| 7 | RegisterRequest type có username, không có fullName/phone | ✓ VERIFIED | types/index.ts: RegisterRequest = {username, email, password}; comment "fullName và phone REMOVED per D-01" |
| 8 | AuthResponse.refreshToken là optional | ✓ VERIFIED | types/index.ts: AuthResponse.refreshToken?: string |
| 9 | User type có username? và roles? fields | ✓ VERIFIED | types/index.ts: User.username?: string; User.roles?: string; fullName giữ nguyên |
| 10 | token.ts export setUserRole() và clearUserRole() | ✓ VERIFIED | token.ts lines 47-55: export function setUserRole(role: string) và export function clearUserRole() |
| 11 | setTokens() chấp nhận refresh undefined/null mà không lưu chuỗi "undefined" | ✓ VERIFIED | token.ts: setTokens(access: string, refresh?: string \| null): if(refresh) guard trước localStorage.setItem |
| 12 | auth.ts login() gọi setUserRole(data.user.roles) sau khi nhận AuthResponse | ✓ VERIFIED | auth.ts lines 37-41: setTokens(); if(data.user?.roles) setUserRole(data.user.roles) |
| 13 | auth.ts register() auto-login khi backend trả accessToken (không check refreshToken) | ✓ VERIFIED | auth.ts: if(data?.accessToken) { setTokens(...); setUserRole(...) } — không check refreshToken |
| 14 | auth.ts logout() gọi clearUserRole() để xóa user_role cookie | ✓ VERIFIED | auth.ts: clearTokens() gọi nội bộ clearUserRole(); logout() gọi clearTokens() + fire-and-forget httpPost logout |
| 15 | Login page gọi real services/auth.login() thay vì mock | ✓ VERIFIED | login/page.tsx: import { login } from '@/services/auth'; handleSubmit: await login({email, password}); không còn setTimeout/mock |
| 16 | Register page có username field, không có fullName/phone; 409 discriminate đúng | ✓ VERIFIED | register/page.tsx: form state {username, email, password, confirmPassword}; JSX có Input label="Tên đăng nhập"; catch(409): includes('username') → field error; includes('email') → field error |
| 17 | middleware.ts: 4 matchers bao gồm /account/:path*; admin check → /403 khi không phải ADMIN | ✓ VERIFIED | middleware.ts: matcher = ['/checkout/:path*', '/profile/:path*', '/admin/:path*', '/account/:path*']; pathname.startsWith('/admin') + user_role cookie includes('ADMIN') → redirect /403 |

**Score:** 17/17 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `sources/backend/user-service/src/main/java/.../jwt/JwtUtils.java` | issueToken(userId, username, roles) → JWT string | ✓ VERIFIED | JJWT 0.12.x API; issueToken + parseToken; @Component; @Value injected secret/expiration |
| `sources/backend/user-service/src/main/java/.../service/AuthService.java` | register() + login() business logic với BCrypt | ✓ VERIFIED | Full implementation: PasswordEncoder, UserRepository, ResponseStatusException 409/401 |
| `sources/backend/user-service/src/main/java/.../web/AuthController.java` | HTTP layer: POST /auth/register, /auth/login, /auth/logout | ✓ VERIFIED | 3 endpoints; returns ApiResponse<AuthResponseDto> manually (không double-wrap) |
| `sources/backend/user-service/src/main/java/.../config/PasswordEncoderConfig.java` | @Bean PasswordEncoder (BCryptPasswordEncoder) | ✓ VERIFIED | @Configuration; @Bean BCryptPasswordEncoder; standalone, không enable SecurityFilterChain |
| `sources/backend/user-service/src/main/java/.../web/RegisterRequest.java` | Record với validation annotations | ✓ VERIFIED | record RegisterRequest(username, email, password); @NotBlank @Size @Email |
| `sources/backend/user-service/src/main/java/.../web/LoginRequest.java` | Record với validation annotations | ✓ VERIFIED | record LoginRequest(email, password); @NotBlank @Email |
| `sources/backend/user-service/src/main/java/.../web/AuthResponseDto.java` | Record (accessToken + UserDto, không có refreshToken) | ✓ VERIFIED | record AuthResponseDto(String accessToken, UserDto user); không có refreshToken field |
| `sources/frontend/src/types/index.ts` | RegisterRequest, AuthResponse (refreshToken optional), User (username/roles) | ✓ VERIFIED | Tất cả 3 types đã update đúng per plan |
| `sources/frontend/src/services/token.ts` | setUserRole(), clearUserRole(), setTokens() với refresh optional | ✓ VERIFIED | Tất cả exports có; clearUserRole defined trước clearTokens (tránh forward reference) |
| `sources/frontend/src/services/auth.ts` | login/register/logout wired với user_role cookie management | ✓ VERIFIED | Không còn mock; setUserRole wired sau setTokens; fire-and-forget logout |
| `sources/frontend/src/app/login/page.tsx` | Login page wired với real auth.login() | ✓ VERIFIED | import login từ @/services/auth; apiError state; Banner(children) pattern; 401 → Banner |
| `sources/frontend/src/app/register/page.tsx` | Register page: username field + real auth.register() + auto-login | ✓ VERIFIED | username field đầu tiên; import register; router.replace('/') sau success |
| `sources/frontend/src/app/403/page.tsx` | /403 page per UI-SPEC | ✓ VERIFIED | h1 "Không có quyền truy cập"; p "Bạn không có quyền xem trang này."; Button "Về trang chủ" |
| `sources/frontend/src/app/403/page.module.css` | 403 page styles: centered, max-width 440px | ✓ VERIFIED | min-height: 100vh; background: var(--surface); max-width: 440px; CSS tokens đúng |
| `sources/frontend/middleware.ts` | matcher + /account/:path* + admin role check → /403 | ✓ VERIFIED | 4 matcher entries; admin check với user_role cookie; redirect new URL('/403') |
| `sources/backend/user-service/pom.xml` | JJWT 0.12.7 (3 deps, đúng scope) + spring-security-crypto compile | ✓ VERIFIED | jjwt-api (compile, default), jjwt-impl (runtime), jjwt-jackson (runtime); spring-security-crypto không có scope=test |
| `sources/backend/user-service/src/main/resources/application.yml` | app.jwt.secret + app.jwt.expiration-ms | ✓ VERIFIED | app.jwt.secret: ${JWT_SECRET:dev-jwt-secret-key-minimum-32-characters-for-hs256-ok}; expiration-ms: 86400000 |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| AuthController.register() | AuthService.register() | Spring constructor injection | ✓ WIRED | Constructor injection: public AuthController(AuthService authService) |
| AuthService.register() | JwtUtils.issueToken() | gọi trực tiếp sau userRepo.save() | ✓ WIRED | jwtUtils.issueToken(entity.id(), entity.username(), entity.roles()) sau userRepo.save() |
| AuthController | ApiResponse<AuthResponseDto> | ApiResponse.of() manually | ✓ WIRED | return ApiResponse.of(201, "Registered successfully", authService.register(request)) |
| auth.ts login() | token.ts setUserRole() | import và gọi sau setTokens() | ✓ WIRED | import { setTokens, clearTokens, setUserRole } từ './token'; setUserRole(data.user.roles) |
| auth.ts logout() | token.ts clearUserRole() | clearTokens() gọi clearUserRole() nội bộ | ✓ WIRED | clearTokens() line 62: clearUserRole() |
| login/page.tsx handleSubmit | services/auth.login() | import { login } từ '@/services/auth' | ✓ WIRED | const data = await login({ email, password }) |
| register/page.tsx handleSubmit | services/auth.register() | import { register } từ '@/services/auth' | ✓ WIRED | const data = await register({ username, email, password }) |
| middleware.ts /admin check | user_role cookie | req.cookies.get('user_role')?.value | ✓ WIRED | if (!userRole?.includes('ADMIN')) return NextResponse.redirect(new URL('/403')) |
| middleware /403 redirect | app/403/page.tsx | NextResponse.redirect(new URL('/403', req.url)) | ✓ WIRED | Redirect target '/403' khớp với app/403/page.tsx route |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|-------------------|--------|
| AuthController.register() | AuthResponseDto | UserRepository.save() → JwtUtils.issueToken() → UserMapper.toDto() | Có — DB query + BCrypt + JWT issue | ✓ FLOWING |
| AuthController.login() | AuthResponseDto | UserRepository.findByEmail() → passwordEncoder.matches() → JwtUtils.issueToken() | Có — DB query + BCrypt verify + JWT issue | ✓ FLOWING |
| login/page.tsx | data (AuthResponse) | httpPost('/api/users/auth/login') → backend | Có — real API call (gỡ mock) | ✓ FLOWING |
| register/page.tsx | data (AuthResponse) | httpPost('/api/users/auth/register') → backend | Có — real API call (gỡ mock) | ✓ FLOWING |

### Behavioral Spot-Checks

Step 7b: SKIPPED — backend cần docker compose up + Postgres để chạy; FE cần Next.js dev server. Không thể smoke test programmatically trong môi trường verify (Windows worktree, Docker không accessible từ JVM như ghi nhận trong SUMMARY-01). Kiểm tra thủ công trong Human Verification Required section.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|------------|-------------|-------------|--------|----------|
| AUTH-01 | 06-01 | Backend /auth/register: persist UserEntity BCrypt, 201 Created, 409 duplicate | ✓ SATISFIED | AuthController.register() → AuthService → UserRepository.save() + ResponseStatusException 409 |
| AUTH-02 | 06-01 | Backend /auth/login: verify cred, issue JWT HS256, 401 wrong cred | ✓ SATISFIED | AuthService.login(): findByEmail → passwordEncoder.matches → jwtUtils.issueToken(); ResponseStatusException 401 |
| AUTH-03 | 06-01, 06-03 | Backend /auth/logout: invalidate token (client-side discard) | ✓ SATISFIED | AuthController.logout() trả 200; auth.ts logout() clearTokens() + fire-and-forget POST |
| AUTH-04 | 06-02, 06-03 | FE login form call real /auth/login, store token+cookie, redirect / | ✓ SATISFIED | login/page.tsx: await login(); authLogin(); router.replace(returnTo); setTokens + setUserRole trong auth.ts |
| AUTH-05 | 06-02, 06-03 | FE register form call real /auth/register, field errors từ ApiErrorResponse, auto-login | ✓ SATISFIED | register/page.tsx: await register(); 409 discriminate → field errors; auto-login → router.replace('/') |
| AUTH-06 | 06-02, 06-03 | Session persist sau reload; protected routes redirect đúng | ? NEEDS HUMAN | token.ts setTokens stores to localStorage; auth_present cookie; middleware matcher includes /account/:path*; F5 behavior cần browser verify |

**Orphaned requirements:** Không có — tất cả 6 AUTH requirements được cover bởi ít nhất 1 plan.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | — | — | — |

Không tìm thấy anti-patterns đáng kể:
- Không còn `setTimeout` / mock code trong login/register pages
- Không có `return null` / empty implementations trong AuthController/AuthService
- Không có `refreshToken` required check trong auth.ts (đã fix)
- JwtUtils dùng `Jwts.parser()` (0.12.x), không phải deprecated `Jwts.parserBuilder()` (0.11.x)
- `clearUserRole()` defined trước `clearTokens()` — không có forward reference issue
- `spring-security-crypto` không còn `scope=test`

**1 informational note:** Banner component trong login/register dùng children pattern (`<Banner count={1}>{apiError}</Banner>`) thay vì `message` prop — đây là deviation từ plan được auto-fixed trong Plan 03 vì Banner.tsx chỉ nhận children. Implementation đúng.

### Human Verification Required

### 1. End-to-End Register Flow

**Test:** docker compose up → truy cập http://localhost:3000/register → điền (username="testuser01", email="test01@test.com", password="pass123") → Submit
**Expected:** Backend persist UserEntity (verify bằng psql query SELECT * FROM user_svc.users); FE redirect về /; reload trang → vẫn còn session (không redirect về /login)
**Why human:** Full E2E flow cần Docker + Postgres + browser execution

### 2. Register Duplicate Errors (AUTH-01 FE side)

**Test:** Register với username đã tồn tại, sau đó với email đã tồn tại
**Expected:** Username dup → field error "Tên đăng nhập này đã được sử dụng" dưới username field; Email dup → field error "Email này đã được đăng ký. Đăng nhập" dưới email field; không có Banner, không redirect
**Why human:** Error rendering behavior cần browser

### 3. Login 401 Error Display (AUTH-02 FE side)

**Test:** Login với email đúng nhưng password sai
**Expected:** Banner hiển thị "Email hoặc mật khẩu không chính xác. Vui lòng thử lại" ở đầu form; không có field highlight; form không bị cleared
**Why human:** Banner render cần browser

### 4. Session Persist Sau Reload (AUTH-06)

**Test:** Login thành công → F5 reload trang bất kỳ (/, /account/orders)
**Expected:** Session còn nguyên; user vẫn logged in; `/account/orders` load bình thường (không redirect về /login)
**Why human:** localStorage + cookie persistence + AuthProvider init behavior cần browser

### 5. Middleware Protected Routes (AUTH-05, AUTH-06)

**Test a:** Logout (clear cookies/storage) → truy cập http://localhost:3000/account/orders
**Expected:** Redirect về /login?returnTo=%2Faccount%2Forders
**Test b:** Login với USER role (không phải ADMIN) → truy cập http://localhost:3000/admin
**Expected:** Redirect về /403 page với h1 "Không có quyền truy cập"
**Test c:** Login với ADMIN role → truy cập http://localhost:3000/admin
**Expected:** Không redirect, trang admin load
**Why human:** Cookie/middleware behavior cần browser execution; ADMIN user cần seed data từ Phase 5 (admin/admin123)

### 6. Logout Flow (AUTH-03)

**Test:** Login → logout (click logout button) → kiểm tra cookies/localStorage đã cleared → truy cập /account
**Expected:** auth_present cookie cleared; user_role cookie cleared; accessToken localStorage cleared; redirect về /login khi truy cập /account
**Why human:** Browser DevTools để verify cookie/storage state

### Gaps Summary

Không có gaps kỹ thuật — tất cả 17 must-haves verified programmatically. Status `human_needed` vì 6 behavioral checks cần browser + docker compose up để xác nhận end-to-end behavior.

---

_Verified: 2026-04-26T10:00:00Z_
_Verifier: Claude (gsd-verifier)_
