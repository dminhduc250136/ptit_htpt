---
phase: 06-real-auth-flow
reviewed: 2026-04-26T00:00:00Z
depth: standard
files_reviewed: 18
files_reviewed_list:
  - sources/backend/user-service/pom.xml
  - sources/backend/user-service/src/main/resources/application.yml
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/config/PasswordEncoderConfig.java
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/jwt/JwtUtils.java
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/service/AuthService.java
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/AuthController.java
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/AuthResponseDto.java
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/LoginRequest.java
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/RegisterRequest.java
  - sources/backend/user-service/src/test/java/com/ptit/htpt/userservice/web/AuthControllerTest.java
  - sources/frontend/middleware.ts
  - sources/frontend/src/app/403/page.module.css
  - sources/frontend/src/app/403/page.tsx
  - sources/frontend/src/app/login/page.tsx
  - sources/frontend/src/app/register/page.tsx
  - sources/frontend/src/services/auth.ts
  - sources/frontend/src/services/token.ts
  - sources/frontend/src/types/index.ts
findings:
  critical: 0
  warning: 4
  info: 4
  total: 8
status: issues_found
---

# Phase 06: Code Review Report

**Reviewed:** 2026-04-26T00:00:00Z
**Depth:** standard
**Files Reviewed:** 18
**Status:** issues_found

## Summary

Review bao gồm toàn bộ auth flow Phase 6: backend Java (JWT issue/verify, BCrypt, AuthService, AuthController, DTOs, integration tests) và frontend TypeScript (middleware, login/register pages, auth service, token storage, types).

Kiến trúc tổng thể đúng hướng: BCrypt standalone không trigger Spring Security filter chain, JJWT 0.12.x API được dùng đúng, generic "Invalid credentials" cho cả hai trường hợp 401 là tốt (T-06-01 mitigated), UserDto không expose passwordHash (T-06-03 mitigated), open-redirect hardening trên returnTo param là đúng.

Phát hiện **4 warnings** (logic/correctness) và **4 info** (code quality). Không có critical security issues.

## Warnings

### WR-01: `user_role` cookie không được encode — dấu phẩy trong roles có thể gây parse lỗi tại middleware

**File:** `sources/frontend/src/services/token.ts:49`

**Issue:** `setUserRole(role: string)` ghi giá trị `role` thẳng vào cookie value mà không encode. Nếu backend sau này trả multi-role string dạng `"ADMIN,USER"`, dấu phẩy trong cookie value là ký tự hợp lệ theo RFC 6265 nhưng một số browser agent hoặc cookie-parsing library sẽ cắt value tại dấu phẩy. `middleware.ts` dùng `userRole?.includes('ADMIN')` — substring check này sẽ vẫn hoạt động với "ADMIN,USER" nhưng **sẽ fail** nếu role là "SUPERADMIN" vì `"SUPERADMIN".includes('ADMIN')` trả `true` (false positive — bất kỳ role nào có chuỗi "ADMIN" đều được cấp quyền admin route).

**Fix:**
```typescript
// token.ts: encode value trước khi ghi cookie
export function setUserRole(role: string): void {
  if (typeof window === 'undefined') return;
  document.cookie = `${ROLE_COOKIE}=${encodeURIComponent(role)}; Path=/; SameSite=Lax; Max-Age=2592000`;
}

// middleware.ts: dùng exact match hoặc split check thay vì substring includes()
const userRoleDecoded = decodeURIComponent(userRole ?? '');
const roleList = userRoleDecoded.split(',').map(r => r.trim());
if (!roleList.includes('ADMIN')) {
  return NextResponse.redirect(new URL('/403', req.url));
}
```

---

### WR-02: `AuthService.register` có race condition giữa check trùng và insert — thiếu DB-level constraint protection handling

**File:** `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/service/AuthService.java:43-48`

**Issue:** Logic hiện tại thực hiện `findByUsername` → check → `findByEmail` → check → `save`. Trong môi trường concurrent (hai request đăng ký cùng username/email gần như cùng lúc), cả hai có thể vượt qua cả hai check rồi cùng gọi `save`, dẫn đến `DataIntegrityViolationException` từ DB unique constraint — exception này sẽ propagate như 500 Internal Server Error thay vì 409 Conflict.

DB schema có unique constraint trên `username` và `email` (dựa vào `UserEntity @Column(unique=true)`), đây là tuyến phòng thủ cuối cùng — nhưng exception cần được catch và chuyển về 409.

**Fix:**
```java
// AuthService.java: wrap save() trong try-catch DataIntegrityViolationException
import org.springframework.dao.DataIntegrityViolationException;

public AuthResponseDto register(RegisterRequest req) {
    if (userRepo.findByUsername(req.username()).isPresent()) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
    }
    if (userRepo.findByEmail(req.email()).isPresent()) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
    }
    String hash = passwordEncoder.encode(req.password());
    UserEntity entity = UserEntity.create(req.username(), req.email(), hash, "USER");
    try {
        userRepo.save(entity);
    } catch (DataIntegrityViolationException e) {
        // Race condition: một request khác đã insert cùng username/email
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Username or email already exists");
    }
    String token = jwtUtils.issueToken(entity.id(), entity.username(), entity.roles());
    return new AuthResponseDto(token, UserMapper.toDto(entity));
}
```

---

### WR-03: `JwtUtils.getSigningKey()` được gọi mỗi lần issue/parse thay vì cache — không phải crash nhưng là logic waste có thể gây WeakKeyException nếu secret thay đổi mid-runtime

**File:** `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/jwt/JwtUtils.java:32-34`

**Issue:** `getSigningKey()` gọi `Keys.hmacShaKeyFor(jwtSecret.getBytes(...))` mỗi lần `issueToken` và `parseToken` được gọi. `@Value` field `jwtSecret` là `String`, không phải `final` — nếu framework reload context (test context reuse, hot-reload), key được reconstruct nhưng giá trị có thể không khớp. Thực tế quan trọng hơn: với `@SpringBootTest` integration test, `JWT_SECRET` không được override qua `@DynamicPropertySource` — fallback dev secret `"dev-jwt-secret-key-minimum-32-characters-for-hs256-ok"` được dùng, điều này tốt nhưng cần đảm bảo không deploy fallback lên production.

Đây cũng là điểm nên cache `SecretKey` sau khi `@PostConstruct`:

**Fix:**
```java
import jakarta.annotation.PostConstruct;

@Component
public class JwtUtils {
    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms:86400000}")
    private long expirationMs;

    private SecretKey signingKey;

    @PostConstruct
    void init() {
        // Fail fast nếu secret quá ngắn (JJWT sẽ throw WeakKeyException ở đây thay vì mid-request)
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    private SecretKey getSigningKey() {
        return signingKey;
    }
    // ... rest unchanged
}
```

---

### WR-04: `login/page.tsx` render `<Banner count={errorCount} />` không có children khi chỉ có field errors — Banner có thể render trống/sai

**File:** `sources/frontend/src/app/login/page.tsx:73`

**Issue:** Dòng 73: `{errorCount > 0 && <Banner count={errorCount} />}` gọi `<Banner>` không có children prop. Nếu `Banner` component dùng `children` để hiển thị nội dung lỗi (pattern thường thấy), component này sẽ render một banner trống hoặc throw prop-type error. Trong `register/page.tsx` line 95 có cùng pattern. Cần kiểm tra `Banner` component API — nếu `Banner` tự generate nội dung từ `count` prop thì ổn, nhưng nếu nội dung đến từ children thì đây là bug.

So sánh: dòng 72 `{apiError && <Banner count={1}>{apiError}</Banner>}` truyền children đúng cách, nhưng dòng 73 không có children.

**Fix:**
```tsx
{/* Nếu Banner chỉ dùng count để hiển thị "N lỗi phía dưới" thì pattern hiện tại đúng */}
{/* Nếu Banner cần message text, cần truyền children: */}
{errorCount > 0 && (
  <Banner count={errorCount}>
    {`Có ${errorCount} lỗi cần sửa trước khi tiếp tục`}
  </Banner>
)}
```

Cần verify `Banner` component props API để xác nhận severity.

---

## Info

### IN-01: Fallback dev JWT secret hardcoded trong application.yml — cần warning comment rõ hơn

**File:** `sources/backend/user-service/src/main/resources/application.yml:41`

**Issue:** `secret: ${JWT_SECRET:dev-jwt-secret-key-minimum-32-characters-for-hs256-ok}` — fallback value được commit trong repository. Đây là pattern phổ biến cho dev và được chấp nhận trong dự án này (project convention ưu tiên dev convenience). Tuy nhiên nên đảm bảo production deployment luôn set `JWT_SECRET` env var.

**Fix:** Thêm comment rõ vào yml:
```yaml
app:
  jwt:
    # PRODUCTION: phải set JWT_SECRET env var (min 32 chars). Fallback chỉ dùng cho local dev.
    secret: ${JWT_SECRET:dev-jwt-secret-key-minimum-32-characters-for-hs256-ok}
    expiration-ms: ${JWT_EXPIRATION_MS:86400000}
```

---

### IN-02: `types/index.ts` — `User` interface có cả `role` (singular, enum) và `roles` (optional string) — hai fields có tên gần giống nhau gây confusion

**File:** `sources/frontend/src/types/index.ts:49,45`

**Issue:** `User` interface có:
- `roles?: string;` (dòng 45) — từ Phase 6, match UserDto backend, dùng cho D-08 middleware
- `role: 'CUSTOMER' | 'ADMIN';` (dòng 49) — legacy field từ phase trước, không có trong UserDto backend

Hai fields này có thể gây confuse khi code mới được viết — không rõ field nào là source of truth. `roles` là string từ backend, `role` là legacy enum không được backend trả về nữa. Code trong `login/page.tsx` và `register/page.tsx` không dùng `role` field này (dùng `roles` qua `setUserRole`), nhưng field legacy vẫn tồn tại trong type.

**Fix:** Mark `role` là deprecated hoặc optional với comment:
```typescript
export interface User {
  id: string;
  email: string;
  username?: string;
  roles?: string;      // Phase 6: từ backend UserDto — source of truth cho auth
  /** @deprecated Legacy field — không có trong UserDto Phase 6. Dùng `roles` thay thế. */
  role?: 'CUSTOMER' | 'ADMIN';
  // ...
}
```

---

### IN-03: Integration test `AuthControllerTest` thiếu isolation giữa các test — shared database state có thể gây flaky tests

**File:** `sources/backend/user-service/src/test/java/com/ptit/htpt/userservice/web/AuthControllerTest.java:38`

**Issue:** Tests dùng `@SpringBootTest` với một PostgreSQL container chung cho tất cả tests trong class (static `@Container`). Không có `@Transactional` trên test class, không có `@BeforeEach` cleanup. Các test như `register_withNewCredentials_returns201WithToken` (tạo "newuser") và `login_withCorrectCredentials_returns200WithToken` (tạo "loginuser") sẽ fail nếu chạy lần hai do username/email đã tồn tại trong DB.

Test order trong JUnit 5 không được đảm bảo mặc định, nhưng trong CI môi trường repeat runs sẽ gây fail.

**Fix:**
```java
// Option 1: @Transactional + @Rollback (dễ nhất cho MockMvc tests)
@Transactional
class AuthControllerTest { ... }

// Option 2: @BeforeEach cleanup specific users
// Option 3: dùng timestamp/UUID suffix trong test usernames để tránh collision
private static final String SUFFIX = String.valueOf(System.currentTimeMillis());
var body = new RegisterRequest("newuser" + SUFFIX, "newuser" + SUFFIX + "@test.com", "password123");
```

---

### IN-04: `register/page.tsx` import `page.module.css` từ `../login/` thay vì có file CSS riêng

**File:** `sources/frontend/src/app/register/page.tsx:6`

**Issue:** `import styles from '../login/page.module.css';` — register page dùng chung CSS module của login page. Đây là pattern sharing hợp lý nếu hai pages có cùng layout, nhưng tạo coupling: thay đổi CSS login có thể vô tình ảnh hưởng register. Nên extract shared styles vào một file dùng chung (ví dụ `@/styles/auth.module.css`) hoặc tạo `register/page.module.css` riêng.

**Fix:**
```
// Tạo: sources/frontend/src/styles/auth.module.css (hoặc src/app/auth.module.css)
// Cả login/page.tsx và register/page.tsx đều import từ đó:
import styles from '@/styles/auth.module.css';
```

---

_Reviewed: 2026-04-26T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
