---
phase: 09-residual-closure-verification
reviewed: 2026-04-27T00:00:00Z
depth: standard
files_reviewed: 24
files_reviewed_list:
  - sources/backend/api-gateway/src/main/resources/application.yml
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/repository/OrderRepository.java
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderStatsService.java
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/AdminStatsController.java
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/JwtRoleGuard.java
  - sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/service/ProductStatsService.java
  - sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/web/AdminStatsController.java
  - sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/web/JwtRoleGuard.java
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/api/GlobalExceptionHandler.java
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/exception/InvalidPasswordException.java
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/service/UserPasswordService.java
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/service/UserStatsService.java
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/AdminStatsController.java
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/ChangePasswordRequest.java
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/JwtRoleGuard.java
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/UserMeController.java
  - sources/frontend/src/middleware.ts
  - sources/frontend/src/app/admin/page.tsx
  - sources/frontend/src/app/profile/settings/page.tsx
  - sources/frontend/src/services/stats.ts
  - sources/frontend/src/services/users.ts
  - sources/frontend/e2e/global-setup.ts
  - sources/frontend/e2e/auth.spec.ts
  - sources/frontend/e2e/password-change.spec.ts
findings:
  critical: 1
  warning: 4
  info: 3
  total: 8
status: issues_found
---

# Phase 9: Code Review Report

**Reviewed:** 2026-04-27T00:00:00Z
**Depth:** standard
**Files Reviewed:** 24
**Status:** issues_found

## Summary

Phase 9 mang đến hai tính năng chính: (1) Admin dashboard KPI stats với manual JWT role check trên 3 microservices và (2) self-service password change flow với client-side + server-side validation. Các file được review bao gồm backend Java (Spring Boot), frontend TypeScript/React, và E2E Playwright tests.

Kiến trúc tổng thể hợp lý. Luồng JWT role guard được implement nhất quán qua 3 service. Password change đúng pattern: verify oldPassword trước khi encode new, lấy userId từ JWT subject, không log request body.

Tuy nhiên có một vấn đề bảo mật đáng chú ý liên quan đến thông tin xác thực hardcode trong test setup, ba warning về logic có thể gây lỗi runtime, và một số info items về code quality.

---

## Critical Issues

### CR-01: Hardcoded production-grade credentials trong global-setup.ts

**File:** `sources/frontend/e2e/global-setup.ts:26-28`
**Issue:** Fallback credentials `admin123` được hardcode trực tiếp trong source code cho cả admin và user account. Mặc dù file comment "KHÔNG commit storageState", bản thân các credential defaults vẫn được commit vào repo. Nếu môi trường CI/CD không set env vars, pipeline sẽ chạy với password mặc định này — và nếu seed data giống production (hoặc staging dùng chung schema), điều này tạo ra attack surface thực.

Rủi ro cụ thể: bất kỳ ai đọc source có thể thử `admin@tmdt.local` / `admin123` trên staging/production endpoint.

```typescript
// Thay thế hardcode fallback bằng fail-fast khi chạy trong CI:
const ADMIN_EMAIL = process.env.E2E_ADMIN_EMAIL;
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD;
const USER_EMAIL = process.env.E2E_USER_EMAIL;
const USER_PASSWORD = process.env.E2E_USER_PASSWORD;

if (!ADMIN_EMAIL || !ADMIN_PASSWORD || !USER_EMAIL || !USER_PASSWORD) {
  throw new Error(
    '[global-setup] E2E credentials phải được set qua env vars: ' +
    'E2E_ADMIN_EMAIL, E2E_ADMIN_PASSWORD, E2E_USER_EMAIL, E2E_USER_PASSWORD'
  );
}
```

Nếu muốn giữ fallback cho local dev, tách rõ ràng bằng `NODE_ENV !== 'production'` check hoặc document trong `.env.example` thay vì inline trong source.

---

## Warnings

### WR-01: `claims.getSubject()` có thể trả null — NullPointerException trong UserMeController

**File:** `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/UserMeController.java:73`
**Issue:** `claims.getSubject()` trả `null` nếu JWT không có claim `sub`. Giá trị này được truyền thẳng vào `passwordService.changePassword(userId, body)` → `userRepo.findById(null)` — hành vi phụ thuộc JPA provider nhưng thường ném `IllegalArgumentException` hoặc trả `Optional.empty()` → `ResponseStatusException(404)`. Dù 404 không phải crash thảm khốc, message "User not found" với userId=null misleading và khó debug.

```java
private String extractUserIdFromBearer(String authHeader) {
    // ... existing null/Bearer check ...
    String token = authHeader.substring("Bearer ".length()).trim();
    try {
        Claims claims = jwtUtils.parseToken(token);
        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token: missing subject");
        }
        return subject;
    } catch (ResponseStatusException e) {
        throw e;
    } catch (Exception e) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
    }
}
```

---

### WR-02: JwtRoleGuard trùng lặp hoàn toàn 3 lần — drift risk khi fix security bug

**File:** `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/JwtRoleGuard.java`, `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/web/JwtRoleGuard.java`, `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/JwtRoleGuard.java`
**Issue:** Ba file này là bản sao hoàn toàn giống nhau (65 dòng, logic byte-by-byte identical). Nếu sau này cần fix một security bug trong logic parse roles (ví dụ thêm `ExpiredJwtException` handling riêng, hoặc thay đổi roles claim format), developer phải nhớ update cả 3 nơi. Lịch sử cho thấy pattern copy-paste như này thường dẫn đến 1-2 bản bị bỏ sót.

**Fix:** Extract sang một shared module (ví dụ `common-security` Maven module hoặc ít nhất một `shared` package nếu deploy chung JAR). Nếu không thể shared module ngay, ít nhất đặt comment rõ ràng "SYNC: file này phải giống hệt [path]/JwtRoleGuard.java" trong cả 3 file để reviewer nhận diện.

---

### WR-03: Frontend middleware chỉ check UX cookie `user_role` — dễ bị bypass bằng tay

**File:** `sources/frontend/src/middleware.ts:27-33`
**Issue:** Admin route guard ở frontend check `user_role` cookie — đây là plain-text cookie mà user có thể set tùy ý bằng DevTools. Comment trong code có ghi rõ "Edge runtime KHÔNG verify JWT signature", tuy nhiên đây vẫn là warning vì logic sẽ cho phép bất kỳ ai set `user_role=ADMIN` cookie để vượt qua redirect về `/403`. Họ sẽ thấy admin UI nhưng các API call thực sẽ fail với 403 từ backend — vậy không phải data breach. Tuy nhiên nếu có bất kỳ client-side rendered secret nào trong `/admin` page (ví dụ API keys, internal URLs) trước khi fetch, chúng sẽ bị lộ.

**Fix:** Đây là known trade-off với Edge middleware. Ít nhất cần đảm bảo `/admin` page không render bất kỳ thông tin nhạy cảm nào trước khi API response về. Hiện tại `admin/page.tsx` chỉ fetch stats sau mount nên ổn — nhưng cần document constraint này để Phase 10 không vô tình render sensitive data server-side trong admin layout.

---

### WR-04: `Promise.allSettled` result bị discard hoàn toàn trong useEffect

**File:** `sources/frontend/src/app/admin/page.tsx:59`
**Issue:** `Promise.allSettled([...])` được gọi nhưng result không được `.then()` hay `await` — nếu một trong 3 load functions throw một error không được catch bên trong (ví dụ do `setState` sau unmount trong React strict mode), nó sẽ trở thành unhandled promise rejection silently. Trong React 18 strict mode, component mount 2 lần → `loadProduct/loadOrder/loadUser` chạy 2 lần và race condition với `setXxxCard` có thể xảy ra nếu request thứ nhất trả về sau khi request thứ hai đã set state.

```typescript
useEffect(() => {
  let cancelled = false;
  const load = async () => {
    await Promise.allSettled([loadProduct(), loadOrder(), loadUser()]);
  };
  if (!cancelled) load();
  return () => { cancelled = true; };
}, [loadProduct, loadOrder, loadUser]);
```

Lưu ý: cancelled flag không tự cancel fetch, nhưng ngăn setState sau unmount nếu các load functions được update để check flag. Đây là pattern chuẩn cho React async effects.

---

## Info

### IN-01: `validate()` được gọi 2 lần mỗi render trong settings/page.tsx

**File:** `sources/frontend/src/app/profile/settings/page.tsx:42`
**Issue:** `const isValid = validate() === null` được gọi ở component body (mỗi render), và `validate()` cũng được gọi lại trong `handleSubmit`. Không phải bug nhưng là unnecessary double call. Với React state updates triggering re-renders, `validate()` có thể chạy nhiều lần không cần thiết.

**Fix:** `useMemo` hoặc inline điều kiện trong button `disabled={submitting || validate() !== null}` để nhất quán pattern (đọc 1 lần trong render).

---

### IN-02: auth.spec.ts dùng `page.waitForTimeout(1000)` — unreliable trong CI

**File:** `sources/frontend/e2e/auth.spec.ts:75`
**Issue:** `await page.waitForTimeout(1000)` là arbitrary sleep sau logout click — không đáng tin cậy trên CI với load khác nhau. Nếu logout redirect chậm hơn 1s, test vẫn pass nhưng kiểm tra cookie trên trang sai.

**Fix:**
```typescript
// Thay bằng waitForURL hoặc waitForCondition
await page.waitForURL(/\/(login|$)/, { timeout: 10000 });
```

---

### IN-03: `ChangePasswordRequest` không validate `oldPassword` length

**File:** `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/ChangePasswordRequest.java:16-17`
**Issue:** `oldPassword` chỉ có `@NotBlank` — không có `@Size` max limit. Một request với `oldPassword` có độ dài vài MB sẽ khiến BCrypt `matches()` tốn nhiều CPU (BCrypt cost là O(N) trên input). Không phải DoS nguy hiểm vì gateway thường có body size limit, nhưng nên thêm `@Size(max = 128)` để nhất quán với `newPassword` policy.

**Fix:**
```java
@NotBlank(message = "oldPassword required")
@Size(max = 128, message = "oldPassword too long")
String oldPassword,
```

---

_Reviewed: 2026-04-27T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
