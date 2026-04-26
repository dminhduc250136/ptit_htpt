# Phase 6: Real Auth Flow — Research

**Researched:** 2026-04-26
**Domain:** JWT authentication wiring — Spring Boot backend + Next.js frontend
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Backend `/auth/register` nhận `{username, email, password}` — 3 fields. FE register form update: bỏ `fullName` và `phone`, thêm `username` field, giữ `confirmPassword` (validate FE-side trước khi gửi).
- **D-02:** Login key là **email** — `/auth/login` nhận `{email, password}`. FE form hiện đã dùng email field → không cần đổi label.
- **D-03:** Backend trả `{accessToken, user}` cả khi register lẫn login (cùng AuthResponse shape). FE `services/auth.ts` đã handle case này.
- **D-04:** Backend trả token + user ngay khi register thành công → FE auto-login (lưu token + set AuthProvider state) → redirect về `/`. Không redirect về /login.
- **D-05:** Client-side discard only. Backend `/auth/logout` trả `200 OK`, không blacklist. FE xóa `accessToken`, `refreshToken` khỏi localStorage và zero `auth_present` cookie.
- **D-06:** JWT expiration = 24 giờ. Claim: `sub=userId`, `username`, `roles`, `exp`. Algorithm: HS256. Secret qua env var `JWT_SECRET`.
- **D-07:** Middleware matcher update thêm `/account/*`: `matcher: ['/checkout/:path*', '/profile/:path*', '/admin/:path*', '/account/:path*']`
- **D-08:** Admin role check trong middleware: FE set cookie `user_role=<ROLE>` (non-httpOnly) khi login. Middleware đọc `user_role` cookie để check ADMIN cho `/admin/*`.
- **D-09:** Non-admin user truy cập `/admin/*` → redirect về `/403`. Cần tạo `/403` page.
- **D-10:** Unauthenticated user truy cập protected routes → redirect về `/login?returnTo=<path>`.
- **D-11:** JJWT library — researcher/planner chọn version compatible với Spring Boot 3.3.2.
- **D-12:** `AuthController` là controller mới trong user-service, path `/api/users/auth/{register,login,logout}`.

### Claude's Discretion

- Chi tiết JJWT version và config.
- BCrypt password encoder bean config.
- Error message text cho 409/401 responses.
- `/403` page design — minimal.
- UserDto shape trong AuthResponse — dùng `UserDto` hiện có.

### Deferred Ideas (OUT OF SCOPE)

- Refresh token flow
- Email verification sau register
- Password reset / forgot password
- Rate limiting / brute-force protection trên login
- Gateway JWT claim verification (D14 defer)
- `fullName` và `phone` fields (delete khỏi register form, không phải đưa vào backend)
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| AUTH-01 | Backend POST /auth/register — username+email+password, BCrypt hash, return JWT pair | JJWT 0.12.x pattern verified; BCrypt via spring-security-crypto (đã có trong pom.xml test scope, cần move to compile) |
| AUTH-02 | Backend POST /auth/login — email+password, BCrypt verify, return JWT pair | UserRepository.findByEmail() đã có; BCryptPasswordEncoder.matches() pattern confirmed |
| AUTH-03 | Frontend login page — wire to real backend (replace mock), email+password fields | services/auth.ts login() đã impl; chỉ cần gỡ mock submit block trong page.tsx |
| AUTH-04 | Frontend register page — remove fullName/phone, add username field, wire to backend | RegisterRequest type cần update; register form cần refactor fields |
| AUTH-05 | Update Next.js middleware — add /account/:path*, add user_role cookie check for /admin/* | Edge Runtime constraint confirmed — dùng cookie, không parse JWT |
| AUTH-06 | Create /403 page per UI-SPEC | Minimal centered layout, max-width 440px, CSS Modules pattern từ login/register |
</phase_requirements>

---

## Summary

Phase 6 nối FE mock auth với backend JWT thật. Backend cần 3 endpoints mới trong `AuthController` (register/login/logout) thuộc user-service, dùng JJWT 0.12.x để issue HS256 token và `BCryptPasswordEncoder` để hash/verify password. Frontend cần gỡ mock submit khỏi login/register pages và wire vào `services/auth.ts` đã có sẵn, update `token.ts` để thêm `user_role` cookie, update `middleware.ts` để check role và thêm `/account/*` matcher, plus tạo `/403` page.

Toàn bộ codebase prep từ Phase 5 đã đến đây sẵn sàng: `UserRepository.findByEmail()` đã có, `UserEntity.create()` factory method đã có, `ApiErrorResponse` pattern thống nhất trên toàn service, `services/auth.ts` FE đã impl đúng endpoint paths — chỉ cần backend ship. Lượng code mới thuần túy là nhỏ: 3-4 Java classes + sửa 4 FE files + 1 FE page mới.

**Primary recommendation:** Tạo `AuthService` (business logic: register/login/BCrypt) + `JwtUtils` (issue/parse) + `AuthController` (HTTP layer) + `PasswordEncoderConfig` (@Bean). FE: gỡ mock trong 2 pages, extend token.ts, update middleware.ts, tạo /403 page.

**Critical finding:** `ApiResponseAdvice` tự động wrap MỌI response không phải `ApiResponse<?>` hoặc `ApiErrorResponse`. `AuthController` phải trả `ApiResponse<AuthResponse>` (wrapped manually) thay vì plain `AuthResponse` — hoặc FE `services/auth.ts` phải unwrap từ `data.data`. Recommended: wrap manually để nhất quán với convention. [VERIFIED: ApiResponseAdvice.java]

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Credential hashing (BCrypt) | API / Backend | — | Hashing xảy ra server-side khi register; never client-side |
| JWT issuing | API / Backend | — | Secret key không expose ra client |
| JWT storage | Browser / Client | — | localStorage + auth_present cookie (D-11 Phase 5, Edge Runtime constraint) |
| Route protection (unauthenticated) | Frontend Server (SSR) | — | Next.js middleware chạy ở Edge, đọc cookie |
| Admin role check | Frontend Server (SSR) | — | user_role cookie đọc trong middleware, Edge Runtime không verify JWT |
| Session hydration | Browser / Client | — | AuthProvider.tsx đọc localStorage khi mount |
| Error responses (409, 401) | API / Backend | — | GlobalExceptionHandler đã handle ResponseStatusException |

---

## Standard Stack

### Core (đã xác minh trong codebase)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `io.jsonwebtoken:jjwt-api` | 0.12.7 | JWT API surface | Stable, widely adopted với Spring Boot 3; 0.12.x required cho Java 17 / Jakarta namespace [VERIFIED: Maven Central search 2026-04-26] |
| `io.jsonwebtoken:jjwt-impl` | 0.12.7 | JJWT implementation (runtime scope) | Phải runtime scope — jjwt guarantees: never add to compile scope [VERIFIED: jjwt README] |
| `io.jsonwebtoken:jjwt-jackson` | 0.12.7 | JSON serialization cho JWT claims | Cho phép Jackson ObjectMapper handle claims [VERIFIED: jjwt README] |
| `spring-security-crypto` | managed by Spring Boot BOM | BCryptPasswordEncoder | Đã có trong pom.xml (test scope) — cần move lên compile scope; không cần full Spring Security [VERIFIED: sources/backend/user-service/pom.xml] |

### Supporting (đã có sẵn, không cần install)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Spring Boot Validation | 3.3.2 (BOM) | `@NotBlank @Email @Size` trên request records | Luôn dùng cho input validation |
| GlobalExceptionHandler | existing | 409 CONFLICT + 401 UNAUTHORIZED qua ResponseStatusException | AuthController throw → tự động serialize thành ApiErrorResponse |
| UserRepository | existing | `findByEmail()` + `findByUsername()` đã có | Login verify + register duplicate check |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| JJWT 0.12.x | Auth0 java-jwt 4.x | Auth0 có higher benchmark score nhưng JJWT đã locked trong CONTEXT.md D-11 |
| `spring-security-crypto` standalone | Full Spring Security | Full Spring Security requires SecurityFilterChain config — over-engineered cho MVP visible-first |
| user_role cookie (Edge middleware) | `jose` library verify JWT | jose có thể verify ở Edge nhưng expose secret rotation risk; cookie approach đơn giản hơn và đủ cho MVP |

**Installation (pom.xml additions):**
```xml
<!-- JJWT — Phase 6 JWT issuance -->
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-api</artifactId>
  <version>0.12.7</version>
</dependency>
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-impl</artifactId>
  <version>0.12.7</version>
  <scope>runtime</scope>
</dependency>
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-jackson</artifactId>
  <version>0.12.7</version>
  <scope>runtime</scope>
</dependency>
<!-- Move spring-security-crypto from test to compile scope -->
<dependency>
  <groupId>org.springframework.security</groupId>
  <artifactId>spring-security-crypto</artifactId>
  <!-- version managed by Spring Boot BOM -->
</dependency>
```

**Version verification:** jjwt-api 0.12.7 confirmed on Maven Central (August 2023). Mốc 0.12.x là đúng cho Java 17 + Spring Boot 3.x. [VERIFIED: Maven Central + jjwt releases page]

---

## Architecture Patterns

### System Architecture Diagram

```
FE: Login/Register form
        │
        │ POST /api/users/auth/login
        │     {email, password}
        ▼
API Gateway (port 8080)
        │ rewrite → /auth/login
        ▼
user-service (port 8081)
        │
        ├─ AuthController.login()
        │       │
        │       ▼
        │  AuthService.login(email, password)
        │       │
        │       ├─ UserRepository.findByEmail(email)
        │       │       → 401 INVALID_CREDENTIALS nếu not found
        │       │
        │       ├─ BCryptPasswordEncoder.matches(raw, hash)
        │       │       → 401 INVALID_CREDENTIALS nếu không match
        │       │
        │       └─ JwtUtils.issueToken(userId, username, roles)
        │               → JWT HS256, claims: sub/username/roles/exp
        │
        └── ApiResponse<AuthResponse>  [wrapped by ApiResponseAdvice]
                { data: { accessToken, user: UserDto }, status, message }
                │
                ▼
        FE: services/auth.ts login()
                │  (reads data.data.accessToken nếu unwrap,
                │   hoặc AuthController trả ApiResponse<AuthResponse> manually
                │   và FE đọc từ response.data.accessToken)
                │
                ├─ setTokens(accessToken, refreshToken='')
                │       → localStorage accessToken
                │       → cookie auth_present=1
                │
                ├─ setUserRole(role)  [NEW in token.ts]
                │       → cookie user_role=ADMIN|USER
                │
                └─ authLogin(user)  [AuthProvider state]
                        → localStorage userProfile

        Subsequent page navigation:
        middleware.ts (Edge Runtime)
                │
                ├─ Read auth_present cookie → if absent: redirect /login?returnTo=...
                │
                └─ For /admin/* only:
                        Read user_role cookie → if not 'ADMIN': redirect /403
```

### Recommended Project Structure

**Backend (new files only):**
```
user-service/src/main/java/com/ptit/htpt/userservice/
├── config/
│   └── PasswordEncoderConfig.java   # @Bean BCryptPasswordEncoder
├── jwt/
│   └── JwtUtils.java                # issueToken() + parseToken()
├── service/
│   └── AuthService.java             # register() + login() business logic
└── web/
    └── AuthController.java          # POST /auth/register, /auth/login, /auth/logout
```

**Frontend (modified/new files):**
```
sources/frontend/src/
├── services/
│   ├── token.ts                     # +setUserRole() +clearUserRole()
│   └── auth.ts                      # update setUserRole call + handle unwrap
├── app/
│   ├── login/
│   │   └── page.tsx                 # gỡ mock submit, wire services/auth.login()
│   ├── register/
│   │   └── page.tsx                 # refactor fields (rm fullName/phone, add username), wire register()
│   └── 403/
│       ├── page.tsx                 # NEW — "Không có quyền truy cập"
│       └── page.module.css          # NEW — centered layout
├── middleware.ts                    # update matcher + admin role check
└── types/
    └── index.ts                     # update RegisterRequest (add username, rm fullName/phone)
                                     # update AuthResponse (refreshToken optional)
                                     # update User (add username, roles string)
```

### Pattern 1: JwtUtils — issue + parse (JJWT 0.12.x)

```java
// Source: JJWT 0.12.x README + Baeldung Spring Security JWT guide
@Component
public class JwtUtils {

  @Value("${app.jwt.secret}")
  private String jwtSecret;

  @Value("${app.jwt.expiration-ms:86400000}") // 24h default
  private long expirationMs;

  private SecretKey getSigningKey() {
    return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
  }

  public String issueToken(String userId, String username, String roles) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(userId)
        .claim("username", username)
        .claim("roles", roles)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusMillis(expirationMs)))
        .signWith(getSigningKey(), Jwts.SIG.HS256)
        .compact();
  }

  public Claims parseToken(String token) {
    return Jwts.parser()
        .verifyWith(getSigningKey())
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }
}
```
[CITED: https://github.com/jwtk/jjwt#creating-a-jwt]

### Pattern 2: PasswordEncoderConfig — @Bean standalone (không cần full Spring Security)

```java
// Source: Spring Security Crypto docs — BCryptPasswordEncoder standalone
@Configuration
public class PasswordEncoderConfig {
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
```
[CITED: https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html]

**Lý do không cần full Spring Security:** `spring-security-crypto` là sub-module độc lập. Chỉ cần khai báo `@Bean PasswordEncoder` — không trigger SecurityAutoConfiguration, không cần SecurityFilterChain, không intercept HTTP requests.

### Pattern 3: AuthController — register + login + logout

**CRITICAL:** `ApiResponseAdvice` tự động wrap mọi non-`ApiResponse<?>` body. Để nhất quán với convention hiện tại của các controllers khác, `AuthController` trả `ApiResponse<AuthResponse>` manually (như `UserProfileController` đang làm với `ApiResponse<Object>`). FE `services/auth.ts` đọc `response.data` để lấy `accessToken`.

```java
// Source: existing UserProfileController.java pattern (codebase verified)
// + ApiResponseAdvice.java behavior (VERIFIED: wraps all non-ApiResponse bodies)
@RestController
@RequestMapping("/auth")
public class AuthController {

  // register → 201 Created + ApiResponse<AuthResponse>
  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
    return ApiResponse.of(201, "Registered successfully", authService.register(request));
  }

  // login → 200 OK + ApiResponse<AuthResponse>
  @PostMapping("/login")
  public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
    return ApiResponse.of(200, "Login successful", authService.login(request));
  }

  // logout → 200 OK (client-side discard, D-05)
  @PostMapping("/logout")
  public ApiResponse<Void> logout() {
    return ApiResponse.of(200, "Logged out", null);
  }
}
```

**FE side:** `httpPost<ApiResponse<AuthResponse>>('/api/users/auth/login', body)` rồi đọc `data.data.accessToken` và `data.data.user`. Hoặc type-narrow `AuthResponse` từ `data` nếu `httpPost` đã unwrap.

**Kiểm tra `httpPost` helper:** Cần xem `services/http.ts` để biết nó có unwrap `data` field không. Nếu có: `services/auth.ts` nhận `AuthResponse` trực tiếp (không cần thay đổi). Nếu không: cần update type.

[VERIFIED: ApiResponseAdvice.java lines 49-51 — `if (body instanceof ApiResponse<?> || body instanceof ApiErrorResponse) return body;` → passing `ApiResponse<AuthResponse>` manually bypasses double-wrap]

### Pattern 4: AuthService — business logic

```java
@Service
@Transactional
public class AuthService {

  public AuthResponse register(RegisterRequest request) {
    // 1. Check duplicate username + email → 409 CONFLICT
    if (userRepo.findByUsername(request.username()).isPresent())
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
    if (userRepo.findByEmail(request.email()).isPresent())
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
    // 2. Hash password
    String hash = passwordEncoder.encode(request.password());
    // 3. Create + persist
    UserEntity entity = UserEntity.create(request.username(), request.email(), hash, "USER");
    userRepo.save(entity);
    // 4. Issue JWT + return
    String token = jwtUtils.issueToken(entity.id(), entity.username(), entity.roles());
    return new AuthResponse(token, UserMapper.toDto(entity));
  }

  public AuthResponse login(LoginRequest request) {
    UserEntity entity = userRepo.findByEmail(request.email())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
    if (!passwordEncoder.matches(request.password(), entity.passwordHash()))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    String token = jwtUtils.issueToken(entity.id(), entity.username(), entity.roles());
    return new AuthResponse(token, UserMapper.toDto(entity));
  }
}
```

### Pattern 5: token.ts additions (FE)

```typescript
// Source: existing token.ts pattern + D-08 cookie spec
const ROLE_COOKIE = 'user_role';

export function setUserRole(role: string): void {
  if (typeof window === 'undefined') return;
  // Non-httpOnly; middleware reads this to check ADMIN for /admin/*
  // Max-Age=2592000 matches auth_present (30 days)
  document.cookie = `${ROLE_COOKIE}=${role}; Path=/; SameSite=Lax; Max-Age=2592000`;
}

export function clearUserRole(): void {
  if (typeof window === 'undefined') return;
  document.cookie = `${ROLE_COOKIE}=; Path=/; SameSite=Lax; Max-Age=0`;
}
```

`clearTokens()` cần gọi `clearUserRole()` bên trong để đảm bảo xóa đồng thời. [VERIFIED: existing token.ts pattern]

### Pattern 6: middleware.ts update — admin role check

```typescript
// Source: Next.js middleware docs + D-07/D-08/D-09
export function middleware(req: NextRequest) {
  const authPresent = req.cookies.get('auth_present')?.value;
  if (!authPresent) {
    const returnTo = encodeURIComponent(req.nextUrl.pathname + req.nextUrl.search);
    return NextResponse.redirect(new URL(`/login?returnTo=${returnTo}`, req.url));
  }

  // Admin route check — Edge Runtime cannot verify JWT signature,
  // so rely on user_role cookie set at login (D-08).
  if (req.nextUrl.pathname.startsWith('/admin')) {
    const userRole = req.cookies.get('user_role')?.value;
    // Use includes() to handle potential comma-separated roles e.g. "ADMIN,USER"
    if (!userRole?.includes('ADMIN')) {
      return NextResponse.redirect(new URL('/403', req.url));
    }
  }

  return NextResponse.next();
}

export const config = {
  matcher: ['/checkout/:path*', '/profile/:path*', '/admin/:path*', '/account/:path*'],
};
```

[VERIFIED: existing middleware.ts pattern + CONTEXT.md D-07/D-08/D-09]

### Pattern 7: services/auth.ts — caller update

`services/auth.ts` cần gọi `setUserRole` sau khi nhận token. Hiện tại file không có import `setUserRole` vì function chưa tồn tại:

```typescript
// Sau khi thêm setUserRole vào token.ts:
import { setTokens, clearTokens, setUserRole } from './token';

export async function login(body: LoginRequest): Promise<AuthResponse> {
  const data = await httpPost<AuthResponse>('/api/users/auth/login', body);
  setTokens(data.accessToken, data.refreshToken ?? '');
  // Set user_role cookie so middleware can check admin access
  if (data.user?.roles) {
    setUserRole(data.user.roles);
  }
  return data;
}

export async function register(body: RegisterRequest): Promise<AuthResponse> {
  const data = await httpPost<AuthResponse>('/api/users/auth/register', body);
  if (data?.accessToken) {
    setTokens(data.accessToken, data.refreshToken ?? '');
    if (data.user?.roles) {
      setUserRole(data.user.roles);
    }
  }
  return data;
}
```

**Lưu ý:** `services/auth.ts` hiện gọi `setTokens(data.accessToken, data.refreshToken)` — nhưng `AuthResponse` backend Phase 6 không trả `refreshToken` (intentionally deferred). Phải update để chấp nhận `refreshToken` optional hoặc fallback `''`.

**Lưu ý về `httpPost` unwrapping:** Cần kiểm tra `services/http.ts` — nếu `httpPost` trả `response.data` (unwrapped từ `ApiResponse`), thì `services/auth.ts` nhận `AuthResponse` trực tiếp mà không cần thay đổi. [ASSUMED — chưa kiểm tra http.ts trong session này; đây là ưu tiên đọc đầu tiên khi code]

### Pattern 8: FE login page — gỡ mock

Thay block mock trong `handleSubmit`:
```typescript
// XÓA:
await new Promise((r) => setTimeout(r, 800));
const derivedName = email.split('@')[0] || 'Khách hàng';
setTokens('mock-access-token', 'mock-refresh-token');
authLogin({ id: 'mock-user', email, name: derivedName });

// THAY BẰNG:
try {
  const data = await login({ email, password });
  authLogin({ id: data.user.id, email: data.user.email, name: data.user.username });
  router.replace(returnTo);
} catch (err: unknown) {
  if (err instanceof ApiError && err.status === 401) {
    setApiError('Email hoặc mật khẩu không chính xác. Vui lòng thử lại');
  } else {
    setApiError('Có lỗi xảy ra, vui lòng thử lại');
  }
}
```

[VERIFIED: existing login page mock block + services/auth.ts interface]

### Anti-Patterns to Avoid

- **Không import `jjwt-impl` với compile scope** — luôn `runtime`. jjwt guarantees: public API chỉ qua `jjwt-api`; implementation có thể break binary compatibility giữa minor versions.
- **Không đưa JWT_SECRET vào application.yml plain text** — dùng `${JWT_SECRET}` env var reference; để fallback dev value rõ ràng là dev-only.
- **Không dùng `Jwts.parserBuilder()` (deprecated 0.11)** — 0.12.x dùng `Jwts.parser()` builder mới.
- **Không leak `passwordHash` trong response** — `UserDto` đã tách field này; `AuthResponse` dùng `UserDto`.
- **Không import Node.js `crypto` module trong middleware.ts** — Edge Runtime sẽ crash; dùng cookie approach như D-08.
- **Không dùng `@EnableWebSecurity`** — không cần Spring Security filter chain; chỉ dùng `spring-security-crypto` standalone.
- **Không trả plain `AuthResponse` từ AuthController** — `ApiResponseAdvice` sẽ wrap thêm một lớp nữa → `data.data.accessToken` thay vì `data.accessToken`. Dùng `ApiResponse.of(...)` manually để kiểm soát shape [VERIFIED: ApiResponseAdvice.java].

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Password hashing | Custom hash function | `BCryptPasswordEncoder` từ `spring-security-crypto` | BCrypt built-in salt rounds, constant-time compare — hand-roll dễ bị timing attack |
| JWT signing/verification | Manual HMAC + Base64 | JJWT 0.12.x | Signature padding, Base64URL encoding, claims expiry — nhiều edge case |
| Token storage pattern | Custom cookie manager | `token.ts` existing + thêm `setUserRole` | Đã handle SSR-safe, cross-tab sync, cookie clearing |
| Duplicate check | Manual SQL query | `UserRepository.findByUsername()` + `findByEmail()` — đã có | Spring Data JPA đã có |

**Key insight:** Toàn bộ "auth plumbing" đã có sẵn từ Phase 5 preparation — chỉ cần ghép đúng pieces, không build từ đầu.

---

## Common Pitfalls

### Pitfall 1: `spring-security-crypto` scope sai

**What goes wrong:** `spring-security-crypto` hiện khai báo `<scope>test</scope>` trong pom.xml. Nếu để nguyên, `BCryptPasswordEncoder` sẽ không available ở runtime → `ClassNotFoundException` khi `AuthService` gọi `passwordEncoder.encode()`.

**Why it happens:** Phase 5 chỉ dùng BCrypt để verify seed hash trong test, nên khai báo test scope.

**How to avoid:** Move `spring-security-crypto` dependency lên compile scope (bỏ `<scope>test</scope>` tag) khi thêm JJWT.

**Warning signs:** Spring context startup failure với `NoSuchBeanDefinitionException: PasswordEncoder`.

[VERIFIED: sources/backend/user-service/pom.xml line 49-52]

### Pitfall 2: `UserEntity.roles` là String, không phải `Set<String>`

**What goes wrong:** Code review thấy `roles` field là `String` (ví dụ `"USER"` hoặc `"ADMIN"`), không phải `Set<String>`. Middleware check cần so sánh đúng format.

**Why it happens:** Phase 5 thiết kế đơn giản: roles là comma-separated string hoặc single value trong DB column `varchar(200)`.

**How to avoid:**
- Backend `JwtUtils.issueToken()` truyền `entity.roles()` string trực tiếp vào JWT claim `roles`.
- FE nhận `data.user.roles` là string (không phải array).
- `token.ts` `setUserRole(role)` nhận string.
- Middleware check: `userRole?.includes('ADMIN')` (dùng includes để safe với "ADMIN,USER" format).
- `UserDto.roles` là `String`, không phải `String[]`.

[VERIFIED: sources/backend/user-service/src/main/java/.../domain/UserEntity.java line 42 + UserDto.java]

### Pitfall 3: `AuthResponse` type mismatch — `refreshToken` optional

**What goes wrong:** Frontend `services/auth.ts` gọi `setTokens(data.accessToken, data.refreshToken)` — nhưng Phase 6 backend **không trả refreshToken** (deferred). `data.refreshToken` sẽ là `undefined` → `setTokens(access, undefined)` → localStorage lưu `"undefined"` string.

**Why it happens:** `AuthResponse` type hiện tại khai báo `refreshToken: string` (required). Backend trả JSON không có field → TypeScript không catch vì runtime type erasure.

**How to avoid:**
- Update `types/index.ts` `AuthResponse`: `refreshToken?: string` (optional).
- Update `token.ts` `setTokens`: nhận `refresh: string | undefined | null`, chỉ lưu nếu có giá trị.
- `services/auth.ts`: `setTokens(data.accessToken, data.refreshToken ?? '')` hoặc skip refresh.

[VERIFIED: sources/frontend/src/services/auth.ts line 32 + token.ts setTokens signature]

### Pitfall 4: `RegisterRequest` type cũ còn `fullName` / `phone`

**What goes wrong:** `types/index.ts` `RegisterRequest` hiện có `fullName: string` và `phone?: string`. Nếu không update type, TypeScript sẽ compile nhưng FE form mới (username, không có fullName) sẽ gửi object thiếu `fullName` — có thể trigger validation error hoặc type error.

**Why it happens:** Type chưa được update đồng bộ với D-01.

**How to avoid:** Update `RegisterRequest` trong `types/index.ts`:
```typescript
export interface RegisterRequest {
  username: string;   // NEW
  email: string;
  password: string;
  // fullName và phone REMOVED (D-01)
}
```

[VERIFIED: sources/frontend/src/types/index.ts line 66-71]

### Pitfall 5: `User` type trong `AuthProvider` dùng `name`, backend trả `username`

**What goes wrong:** `AuthProvider.tsx` `AuthState` định nghĩa user có `{ id, email, name }`. Khi `login page` gọi `authLogin({ id: data.user.id, email: data.user.email, name: ??? })` — backend `UserDto` không có `name` field, chỉ có `username`.

**Why it happens:** `AuthState['user']` type trong AuthProvider dùng `name` (generic), nhưng backend `UserDto` dùng `username` (auth-model).

**How to avoid:** Map `username → name` khi gọi `authLogin`: `authLogin({ id: data.user.id, email: data.user.email, name: data.user.username })`. Không cần refactor `AuthProvider` type — đây là display name field, mapping là đúng.

[VERIFIED: sources/frontend/src/providers/AuthProvider.tsx line 17 + UserDto.java]

### Pitfall 6: JJWT 0.11.x vs 0.12.x API khác nhau

**What goes wrong:** Stack Overflow và nhiều blog cũ dùng `Jwts.parserBuilder()` (0.11.x). 0.12.x đã deprecated method này và dùng `Jwts.parser()` mới với khác biệt `verifyWith()` thay `setSigningKey()`.

**Why it happens:** Training data có nhiều code sample từ 0.11.x.

**How to avoid:** Dùng đúng 0.12.x API như Pattern 1 ở trên. Key methods:
- Build parser: `Jwts.parser().verifyWith(secretKey).build()`
- Build token: `.signWith(key, Jwts.SIG.HS256)` (không phải `.signWith(key, SignatureAlgorithm.HS256)`)

[CITED: https://github.com/jwtk/jjwt#reading-a-jwt]

### Pitfall 7: JWT_SECRET quá ngắn cho HS256

**What goes wrong:** JJWT 0.12.x enforce minimum key length. HS256 requires >= 256 bits (32 bytes). Nếu `JWT_SECRET` env var ngắn hơn, JJWT sẽ throw `WeakKeyException` khi tạo SecretKey.

**How to avoid:** Dev fallback trong `application.yml` phải đủ dài:
```yaml
app:
  jwt:
    secret: ${JWT_SECRET:dev-secret-key-at-least-32-chars-long-for-hs256}
    expiration-ms: ${JWT_EXPIRATION_MS:86400000}
```

[CITED: https://github.com/jwtk/jjwt#secret-keys]

### Pitfall 8: `ApiResponseAdvice` auto-wraps AuthController response — VERIFIED và đã giải quyết

**What goes wrong:** `ApiResponseAdvice` implements `ResponseBodyAdvice<Object>` và tự động wrap MỌI response không phải `ApiResponse<?>` hoặc `ApiErrorResponse` trong `ApiResponse<T>` envelope. Nếu `AuthController` trả plain `AuthResponse`, FE nhận `{ data: { data: { accessToken, user } }, status, message }` (double-wrapped) thay vì `{ data: { accessToken, user }, status, message }`.

**Why it happens:** `ApiResponseAdvice.beforeBodyWrite()` check: `if (body instanceof ApiResponse<?> || body instanceof ApiErrorResponse) return body;` — chỉ pass-through nếu đã là `ApiResponse`. Plain `AuthResponse` sẽ bị wrap.

**How to avoid (RESOLVED):** `AuthController` trả `ApiResponse<AuthResponse>` manually (gọi `ApiResponse.of(201, "message", authResponse)`) — giống pattern của `UserProfileController`. `ApiResponseAdvice` sẽ pass-through vì `instanceof ApiResponse<?>` check passes. FE `services/auth.ts` đọc response shape nhất quán với các endpoints khác.

[VERIFIED: sources/backend/user-service/src/main/java/.../api/ApiResponseAdvice.java — đã đọc và phân tích lines 43-70]

---

## Code Examples

### application.yml thêm JWT config (user-service)

```yaml
# Source: Phase 5 application.yml pattern + D-06 JWT config
app:
  jwt:
    secret: ${JWT_SECRET:dev-jwt-secret-key-minimum-32-characters-for-hs256-ok}
    expiration-ms: ${JWT_EXPIRATION_MS:86400000}   # 24 giờ = 86400000ms
```

### AuthResponse record (backend)

```java
// Source: D-03 — cùng shape cho register và login
public record AuthResponse(
    String accessToken,
    UserDto user
) {}
```

**Không có `refreshToken` field** — deferred per CONTEXT.md Deferred section.

### FE /403 page layout (per UI-SPEC)

```tsx
// Source: 06-UI-SPEC.md §/403 Page Layout Spec
'use client';

import Link from 'next/link';
import Button from '@/components/ui/Button/Button';
import styles from './page.module.css';

export default function ForbiddenPage() {
  return (
    <div className={styles.page}>
      <div className={styles.container}>
        <h1 className={styles.title}>Không có quyền truy cập</h1>
        <p className={styles.body}>Bạn không có quyền xem trang này.</p>
        <Link href="/">
          <Button size="lg" fullWidth>Về trang chủ</Button>
        </Link>
      </div>
    </div>
  );
}
```

CSS: centered, max-width 440px, background `--surface` (không dùng gradient — per UI-SPEC).

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `Jwts.parserBuilder()` | `Jwts.parser()` | JJWT 0.12.0 | Code phải dùng API mới; 0.11.x code sẽ compile nhưng deprecated |
| `signWith(key, SignatureAlgorithm.HS256)` | `signWith(key, Jwts.SIG.HS256)` | JJWT 0.12.0 | `SignatureAlgorithm` enum deprecated |
| Full Spring Security JWT filter | standalone JJWT + `spring-security-crypto` | Community preference cho minimal setup | Ít config hơn, không cần SecurityFilterChain |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | JJWT 0.12.7 là phiên bản stable mới nhất cho Spring Boot 3.3.2 (0.13.0 trên GitHub releases đánh dấu "last release supporting Java 7" — có thể 0.13.0 vẫn tương thích Java 17 nhưng chưa xác minh rõ) | Standard Stack | Dùng 0.12.7 an toàn hơn; nếu cần 0.13.0 thì upgrade sau mà không ảnh hưởng API |
| A2 | `UserEntity.roles` chứa single role string (như "USER" hoặc "ADMIN"), không phải comma-separated multiple roles | Pitfall 2 + middleware check | Nếu user có "USER,ADMIN" thì `userRole === 'ADMIN'` sẽ fail; Pattern 6 đã dùng `includes()` để safe |
| A3 | `services/http.ts` `httpPost<T>` trả `T` sau khi unwrap `data` field từ `ApiResponse<T>` envelope | Pattern 7 + services/auth.ts usage | Nếu `httpPost` không unwrap: `services/auth.ts` nhận `ApiResponse<AuthResponse>` và cần đọc `data.data.accessToken` thay vì `data.accessToken` |

**Note on A3:** Đọc `services/http.ts` là BƯỚC ĐẦU TIÊN khi bắt đầu code FE. Shape của response quyết định cách `services/auth.ts` đọc `accessToken`.

**Resolved (không còn là assumption):**
- ~~A1 (cũ): ApiResponseAdvice behavior~~ — VERIFIED: tự động wrap mọi non-`ApiResponse<?>` body. AuthController dùng `ApiResponse.of()` manually để bypass double-wrap.

---

## Open Questions

1. **`httpPost` helper — có unwrap `ApiResponse.data` không?**
   - What we know: `services/auth.ts` gọi `httpPost<AuthResponse>('/api/users/auth/login', body)` và đọc `data.accessToken` trực tiếp.
   - What's unclear: `httpPost` có tự động unwrap `response.data` field từ `ApiResponse<T>` envelope không? Hay nó trả raw `{ data, status, message }` object?
   - Recommendation: Đọc `sources/frontend/src/services/http.ts` ngay khi bắt đầu FE tasks. Nếu không unwrap: update `services/auth.ts` để đọc `data.data.accessToken`. Nếu unwrap: không cần thay đổi.

2. **`user_role` cookie — single role vs multiple roles trong seed data**
   - What we know: `UserEntity.roles` là String field. Seed data admin có `"ADMIN"`, demo_user có `"USER"`.
   - What's unclear: Liệu schema cho phép comma-separated như `"ADMIN,USER"` không?
   - Recommendation: Pattern 6 đã dùng `userRole?.includes('ADMIN')` để safe cho cả 2 cases.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Spring Boot 3.3.2 | All backend | ✓ | 3.3.2 | — |
| Java 17 | JWT crypto | ✓ | 17 (eclipse-temurin) | — |
| PostgreSQL (user_svc schema) | UserRepository | ✓ | Phase 5 verified PASS | — |
| JJWT 0.12.7 | JwtUtils | ✗ (not in pom.xml yet) | — | Phải add; không có fallback |
| `spring-security-crypto` compile scope | AuthService | ✗ (test scope only) | Managed by BOM | Move scope only |
| Next.js 16.2.3 | Middleware | ✓ | 16.2.3 | — |
| `jose` npm | Middleware JWT verify | Not needed | — | Không cần (dùng cookie approach) |

**Missing dependencies với no fallback:**
- `io.jsonwebtoken:jjwt-api/jjwt-impl/jjwt-jackson` 0.12.7 — phải add vào pom.xml

**Scope fix (không phải install mới):**
- `spring-security-crypto` — di chuyển từ test scope sang compile scope

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Spring Boot Test (JUnit 5) — backend; no FE test infra detected |
| Config file | `src/test/resources/` per service |
| Quick run command | `cd sources/backend/user-service && mvn test -pl . -Dtest=AuthControllerTest` |
| Full suite command | `cd sources/backend/user-service && mvn test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| AUTH-01 | POST /auth/register returns 201 + AuthResponse | Integration (MockMvc) | `mvn test -Dtest=AuthControllerTest#register_*` | ❌ Wave 0 |
| AUTH-01 | POST /auth/register 409 on duplicate email/username | Integration | same file | ❌ Wave 0 |
| AUTH-02 | POST /auth/login returns 200 + JWT | Integration | `mvn test -Dtest=AuthControllerTest#login_*` | ❌ Wave 0 |
| AUTH-02 | POST /auth/login 401 on wrong credentials | Integration | same file | ❌ Wave 0 |
| AUTH-03 | FE login page calls real endpoint | Manual smoke | docker compose up → navigate /login | N/A |
| AUTH-04 | FE register page calls real endpoint, auto-login | Manual smoke | docker compose up → navigate /register | N/A |
| AUTH-05 | /account/* redirect unauthenticated → /login | Manual smoke | navigate /account/orders without login | N/A |
| AUTH-05 | /admin/* non-ADMIN → redirect /403 | Manual smoke | login as USER, navigate /admin | N/A |
| AUTH-06 | /403 page renders correctly | Manual smoke | navigate /403 directly | N/A |

### Wave 0 Gaps

- [ ] `sources/backend/user-service/src/test/java/.../web/AuthControllerTest.java` — covers AUTH-01, AUTH-02
- [ ] Test requires Testcontainers PostgreSQL (đã có dependency từ Phase 5)

*(FE smoke tests: manual — không có FE test infra trong project)*

---

## Security Domain

> `security_enforcement` status: không rõ trong config.json — include section per default.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | YES | BCryptPasswordEncoder (spring-security-crypto) |
| V3 Session Management | Partial | JWT 24h + client-side clear (no server invalidation — deferred MVP) |
| V4 Access Control | YES | user_role cookie check in middleware for /admin/* |
| V5 Input Validation | YES | `@Valid @NotBlank @Email @Size` trên RegisterRequest/LoginRequest |
| V6 Cryptography | YES | HS256 + Keys.hmacShaKeyFor(); JWT_SECRET via env var |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Credential enumeration (login tells which field is wrong) | Information Disclosure | Dùng generic "Invalid credentials" cho cả email-not-found và password-wrong (D-02 confirmed) |
| JWT secret leakage | Tampering | `${JWT_SECRET}` env var — không hardcode in application.yml committed to git |
| user_role cookie tampering | Spoofing | Cookie không httpOnly → JS accessible, có thể bị XSS spoof. Chấp nhận cho MVP; backend luôn có thể verify roles từ DB nếu cần (deferred D14) |
| Password hash leak | Information Disclosure | `UserDto` không có `passwordHash` field; `GlobalExceptionHandler` mask sensitive field values [VERIFIED: ApiErrorResponse.java + UserDto.java] |
| Open redirect sau login | Spoofing | `returnTo` validation đã có trong login page: `startsWith('/') && !startsWith('//')` [VERIFIED: login/page.tsx line 27] |

---

## Sources

### Primary (HIGH confidence)
- [VERIFIED: sources/backend/user-service/pom.xml] — dependencies hiện tại, spring-security-crypto scope
- [VERIFIED: sources/backend/user-service/.../UserRepository.java] — findByEmail(), findByUsername() đã có
- [VERIFIED: sources/backend/user-service/.../UserEntity.java] — roles field là String, factory method create()
- [VERIFIED: sources/backend/user-service/.../UserDto.java] — wire format, no passwordHash
- [VERIFIED: sources/backend/user-service/.../api/ApiResponseAdvice.java] — auto-wrap behavior, pass-through logic
- [VERIFIED: sources/backend/user-service/.../api/GlobalExceptionHandler.java] — ResponseStatusException → ApiErrorResponse
- [VERIFIED: sources/frontend/src/services/auth.ts] — endpoints đang gọi, setTokens usage
- [VERIFIED: sources/frontend/src/services/token.ts] — cookie pattern, clearTokens()
- [VERIFIED: sources/frontend/middleware.ts] — hiện tại matcher + redirect logic
- [VERIFIED: sources/frontend/src/app/login/page.tsx] — mock block cần gỡ
- [VERIFIED: sources/frontend/src/app/register/page.tsx] — mock block + fields cần refactor
- [VERIFIED: sources/frontend/src/types/index.ts] — RegisterRequest/AuthResponse/User types

### Secondary (MEDIUM confidence)
- [CITED: https://github.com/jwtk/jjwt] — JJWT 0.12.x API patterns, scope requirement
- [CITED: Maven Central central.sonatype.com/artifact/io.jsonwebtoken/jjwt-api] — version 0.12.7/0.13.0 status
- [CITED: https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html] — BCryptPasswordEncoder standalone bean

### Tertiary (LOW confidence)
- [ASSUMED: A3] httpPost unwrapping behavior — cần đọc services/http.ts trước khi code

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — JJWT 0.12.x confirmed; dependencies verified trong pom.xml; version trên Maven Central confirmed
- Architecture: HIGH — toàn bộ derived từ existing codebase inspection; ApiResponseAdvice behavior VERIFIED
- Pitfalls: HIGH (tất cả 8 pitfalls — pitfall 8 đã resolve bằng cách đọc ApiResponseAdvice.java)

**Research date:** 2026-04-26
**Valid until:** 2026-05-26 (30 ngày — stack stable)
