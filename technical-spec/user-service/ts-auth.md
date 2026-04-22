# TS-AUTH: Xác thực người dùng

## Tóm tắt
Implementation spec cho UC-AUTH. Service: **User Service** only. 7 endpoints (register, login, refresh, logout, password-reset request/confirm, get-me). Classes: `AuthController`, `AuthService`, `TokenService`, `PasswordResetService`. Bcrypt cost 12, JWT HS512. FE: pages `/register`, `/login`, `/reset-password`, `/reset-password/confirm`.

## Context Links
- BA Spec: [../ba/uc-auth.md](../ba/uc-auth.md)
- Services affected: ✅ User | ⬜ Product | ⬜ Order
- Architecture: [../architecture/services/user-service.md](../architecture/services/user-service.md)
- Sequence: [../architecture/02-sequence-diagrams.md#1-register-uc-auth](../architecture/02-sequence-diagrams.md#1-register-uc-auth)

## Services & Responsibilities
- **User Service (8081)**: toàn bộ logic
- **Gateway (8080)**: route public, rate limit IP
- **Email Provider**: welcome email, reset link (async qua Kafka consumer)

## API Contracts

### POST /api/v1/auth/register
Public endpoint. Rate limit: 3/min/IP.

**Request**
```json
{
  "email": "user@example.com",
  "password": "Passw0rd123",
  "fullName": "Nguyen Van A",
  "phone": "0901234567"
}
```

**Validation**
- `email`: required, RFC 5322, max 255
- `password`: required, min 8, regex `^(?=.*[A-Za-z])(?=.*\d).+$`
- `fullName`: required, 2-100
- `phone`: optional, regex `^(0|\+84)[0-9]{9,10}$`

**Response 201**
```json
{
  "user": {
    "id": "uuid",
    "email": "user@example.com",
    "fullName": "Nguyen Van A",
    "phone": "0901234567",
    "role": "CUSTOMER",
    "status": "ACTIVE",
    "createdAt": "2026-04-21T10:00:00Z"
  },
  "accessToken": "eyJhbGci...",
  "refreshToken": "eyJhbGci..."
}
```
Set-Cookie: `refresh_token=...; HttpOnly; Secure; SameSite=Lax; Max-Age=604800`

**Errors**
- 400 `EMAIL_EXISTS` — Email already registered
- 400 `INVALID_EMAIL`
- 400 `WEAK_PASSWORD`
- 400 `INVALID_NAME`
- 400 `INVALID_PHONE`
- 429 `TOO_MANY_REQUESTS`

---

### POST /api/v1/auth/login
Public endpoint. Rate limit: 10/min/IP.

**Request**
```json
{ "email": "...", "password": "..." }
```

**Response 200** (same shape as register)

**Errors**
- 401 `INVALID_CREDENTIALS` — generic, không leak user existence
- 401 `ACCOUNT_LOCKED_TRY_LATER` — 5 fails/15min → lock 30min
- 403 `USER_BLOCKED` — by admin

---

### POST /api/v1/auth/refresh
Gets new access token using refresh cookie.

**Request** — no body, uses `refresh_token` cookie

**Response 200**
```json
{ "accessToken": "..." }
```
Set-Cookie mới (rotation).

**Errors**
- 401 `INVALID_REFRESH_TOKEN` — revoked hoặc expired

---

### POST /api/v1/auth/logout
Requires auth.

**Request** — no body. Cookie refresh_token provided.

**Response 204** — clear cookie.

---

### POST /api/v1/auth/password-reset/request
Public. Rate limit: 3/min/email.

**Request**
```json
{ "email": "user@example.com" }
```

**Response 200** — always
```json
{ "message": "If email exists, reset link sent." }
```

(Internal: nếu email exist → gen token, publish event `PasswordResetRequested` với recipient email + plainToken → Email consumer send)

---

### POST /api/v1/auth/password-reset/confirm
Public. Rate limit: 10/min/IP.

**Request**
```json
{ "token": "plain-token-from-email", "newPassword": "NewPassw0rd" }
```

**Response 200** — `{ message: "Password reset successful" }`

**Errors**
- 400 `INVALID_RESET_TOKEN`
- 400 `EXPIRED_RESET_TOKEN`
- 400 `WEAK_PASSWORD`

---

### GET /api/v1/users/me
Requires auth.

**Response 200** — user object (same shape as register response minus tokens)

---

## Database Changes

### Migration V1__create_user_tables.sql
```sql
CREATE TABLE user (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    avatar_url VARCHAR(500),
    role VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    blocked_reason TEXT,
    blocked_by UUID,
    blocked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_user_email ON user(email);
CREATE INDEX idx_user_status ON user(status);

CREATE TABLE refresh_token (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user(id),
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_user ON refresh_token(user_id);

CREATE TABLE password_reset_token (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user(id),
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
```

## Event Contracts (Kafka)

### Publish: user.user.registered (topic `user.registered`)
```json
{
  "eventId": "uuid",
  "eventType": "UserRegistered",
  "version": "1.0",
  "occurredAt": "2026-04-21T10:00:00Z",
  "producer": "user-service",
  "data": {
    "userId": "uuid",
    "email": "user@example.com",
    "fullName": "...",
    "registeredAt": "2026-04-21T10:00:00Z"
  }
}
```

### Publish: user.password.reset_requested (internal topic — email consumer subscribe)
```json
{
  "data": {
    "userId": "uuid",
    "email": "user@example.com",
    "resetToken": "plain-token",
    "expiresAt": "2026-04-21T10:15:00Z"
  }
}
```

### Consume: (none for MVP)

## Sequence

Xem [architecture/02-sequence-diagrams.md sections 1, 2](../architecture/02-sequence-diagrams.md#1-register-uc-auth).

## Class/Component Design

### Backend — User Service

#### Controller
```java
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req);

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http);

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@CookieValue("refresh_token") String refreshToken);

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue("refresh_token") String refreshToken);

    @PostMapping("/password-reset/request")
    public ResponseEntity<MessageResponse> requestReset(@Valid @RequestBody PasswordResetRequest req);

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<MessageResponse> confirmReset(@Valid @RequestBody PasswordResetConfirmRequest req);
}

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    @GetMapping("/me")
    public UserResponse getMe(@AuthenticationPrincipal UserPrincipal principal);
}
```

#### Service
```java
@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final LoginFailService loginFailService;
    private final KafkaProducer kafkaProducer;

    public AuthResponse register(RegisterRequest req);
    public AuthResponse login(LoginRequest req, String clientIp);
    public TokenResponse refresh(String refreshToken);
    public void logout(String refreshToken);
}

@Service
public class TokenService {
    public String generateAccessToken(User user);
    public RefreshTokenInfo generateRefreshToken(UUID userId);
    public UUID validateAccessToken(String token);
    public RefreshToken validateRefreshToken(String token);
    public void revoke(String tokenHash);
    public void revokeAllForUser(UUID userId);
}

@Service
public class PasswordResetService {
    public void requestReset(String email);
    public void confirmReset(String token, String newPassword);
}

@Service
public class LoginFailService {
    // Redis-backed counter
    public boolean isLocked(String email);
    public void incrementFail(String email);
    public void resetFail(String email);
}
```

#### Repository
```java
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    List<RefreshToken> findByUserIdAndRevokedFalse(UUID userId);
}
```

#### Entity
```java
@Entity
@Table(name = "user")
public class User {
    @Id UUID id;
    String email;
    String passwordHash;
    String fullName;
    String phone;
    String avatarUrl;
    @Enumerated(EnumType.STRING) UserRole role;
    @Enumerated(EnumType.STRING) UserStatus status;
    String blockedReason;
    UUID blockedBy;
    Instant blockedAt;
    @CreationTimestamp Instant createdAt;
    @UpdateTimestamp Instant updatedAt;
}

public enum UserRole { CUSTOMER, ADMIN }
public enum UserStatus { ACTIVE, BLOCKED }
```

### Frontend

#### Pages
- `/register` → `app/(auth)/register/page.tsx`
- `/login` → `app/(auth)/login/page.tsx`
- `/reset-password` → `app/(auth)/reset-password/page.tsx`
- `/reset-password/confirm` → `app/(auth)/reset-password/confirm/page.tsx`

#### Components
- `components/auth/RegisterForm.tsx` — React Hook Form + Zod
- `components/auth/LoginForm.tsx`
- `components/auth/ResetRequestForm.tsx`
- `components/auth/ResetConfirmForm.tsx`

#### API Client
```typescript
// lib/api/auth.api.ts
export const authApi = {
  register: (data: RegisterInput) => apiCall<AuthResponse>('/auth/register', { method: 'POST', body: JSON.stringify(data) }),
  login: (data: LoginInput) => apiCall<AuthResponse>('/auth/login', { method: 'POST', body: JSON.stringify(data) }),
  refresh: () => apiCall<TokenResponse>('/auth/refresh', { method: 'POST', credentials: 'include' }),
  logout: () => apiCall<void>('/auth/logout', { method: 'POST', credentials: 'include' }),
  requestReset: (email: string) => apiCall('/auth/password-reset/request', { method: 'POST', body: JSON.stringify({ email }) }),
  confirmReset: (token: string, newPassword: string) => apiCall('/auth/password-reset/confirm', { method: 'POST', body: JSON.stringify({ token, newPassword }) }),
  getMe: () => apiCall<User>('/users/me'),
};
```

#### State
- `stores/auth.store.ts` (Zustand):
  - State: `user`, `accessToken`, `isAuthenticated`
  - Actions: `login`, `logout`, `setAccessToken`, `hydrate`
- React Query key: `['me']` → fetch getMe

## Implementation Steps

### Backend
1. [ ] Create Spring Boot project `user-service` with dependencies: web, security, data-jpa, postgresql, flyway, kafka, redis, jjwt, springdoc, testcontainers
2. [ ] Write migration `V1__create_user_tables.sql`
3. [ ] Create JPA entities: `User`, `RefreshToken`, `PasswordResetToken`
4. [ ] Create enums: `UserRole`, `UserStatus`
5. [ ] Create repositories
6. [ ] Configure JWT: `jwt.secret`, `jwt.access-ttl`, `jwt.refresh-ttl` in `application.yml`
7. [ ] Create `TokenService` with generateAccess/Refresh, validate, revoke
8. [ ] Create `LoginFailService` using Redis `StringRedisTemplate`
9. [ ] Create `AuthService.register()` — validate, hash, insert, publish UserRegistered, gen tokens
10. [ ] Create `AuthService.login()` — rate check, find, verify, gen tokens
11. [ ] Create `AuthService.refresh()` — validate, issue new pair, revoke old
12. [ ] Create `AuthService.logout()` — revoke refresh
13. [ ] Create `PasswordResetService` — request, confirm
14. [ ] Create DTOs: RegisterRequest, LoginRequest, AuthResponse, UserResponse, ...
15. [ ] Create `AuthController` with endpoints
16. [ ] Create `UserController.getMe()`
17. [ ] Configure Spring Security: stateless, JWT filter, permitAll for `/auth/**`, authenticated for others
18. [ ] Add `@ControllerAdvice` for `BusinessException` → JSON error response
19. [ ] Configure Kafka producer for `UserRegistered`, `PasswordResetRequested`
20. [ ] Write unit tests: AuthService, TokenService (Mockito)
21. [ ] Write integration tests: @SpringBootTest với Testcontainers (Postgres + Redis + Kafka)
22. [ ] Add OpenAPI annotations, export Swagger UI at `/swagger-ui.html`
23. [ ] Run `mvn spotless:apply && mvn test`

### Frontend
1. [ ] Create Zod schemas in `lib/validations/auth.schema.ts`
2. [ ] Create types `types/auth.ts` matching BE response
3. [ ] Create `lib/api/auth.api.ts` với fetch wrapper
4. [ ] Create `stores/auth.store.ts` (Zustand) với persist
5. [ ] Create `app/(auth)/layout.tsx` — centered card layout
6. [ ] Create `RegisterForm.tsx` (react-hook-form + zod) + page
7. [ ] Create `LoginForm.tsx` + page
8. [ ] Create `ResetRequestForm.tsx` + page
9. [ ] Create `ResetConfirmForm.tsx` + page (read token from URL)
10. [ ] Add middleware.ts check auth cho /account, /admin
11. [ ] Add Header dropdown (profile + logout)
12. [ ] Refresh token auto on 401
13. [ ] Write unit tests: form validation, API call mock
14. [ ] Write E2E test Playwright: full register → login → logout flow
15. [ ] Run `npm run lint && npm run typecheck && npm test`

## Test Strategy

### Unit Tests (BE)
- `AuthServiceTest`:
  - register success with valid input
  - register fail email exists
  - register fail weak password
  - login success
  - login fail invalid credentials
  - login lockout after 5 fails
  - login fail blocked user
  - refresh token rotation
  - logout revokes token
- `TokenServiceTest`:
  - generate & validate access token
  - generate & validate refresh token
  - expired token rejected
  - revoked token rejected

### Integration Tests (BE)
- `AuthControllerIntegrationTest` với Testcontainers:
  - POST /register end-to-end (DB insert + Kafka message + response)
  - POST /login end-to-end
  - Email unique enforced
  - Rate limit (mock 4 requests → 4th = 429)

### E2E Tests (FE — Playwright)
- `auth.spec.ts`:
  - Register new user → redirect home → logged in
  - Login with correct creds → redirect home
  - Login wrong password → error message
  - Logout → redirect login → cannot access /account

## Edge Cases & Gotchas

1. **Race condition register same email**: dùng DB UNIQUE constraint + catch `DataIntegrityViolationException` → return EMAIL_EXISTS.
2. **Clock skew JWT**: nhét 30s leeway khi verify `exp`.
3. **Refresh token rotation concurrent**: dùng optimistic lock (version column) hoặc accept race (user refresh 2 lần nhanh → 1 thành công, 1 fail với 401, FE retry login).
4. **Password reset timing attack**: always take ~200ms response time (even khi email not found) để không leak qua timing.
5. **Bcrypt cost 12 slow**: ~250ms per hash → acceptable cho register/login, nhưng không dùng trong loops hoặc batch operations.
6. **Password in logs**: tuyệt đối không log RegisterRequest/LoginRequest full body. Chỉ log `{ email, ... }`.
7. **Cookie flags**: production BẮT BUỘC `Secure` + `HttpOnly`. Dev có thể skip Secure.
8. **CORS cho credentials**: Gateway phải set `Access-Control-Allow-Credentials: true` + specific origin (không wildcard).
9. **Gateway trust headers**: downstream services trust `X-User-Id` → gateway phải strip headers từ client request (tránh injection).
10. **Email provider fail**: register success vẫn trả 200 dù welcome email fail (async). Password reset email fail → không retry auto (user có thể request lại).
