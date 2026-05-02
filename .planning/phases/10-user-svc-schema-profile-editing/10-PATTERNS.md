# Phase 10: User-Svc Schema + Profile Editing — Pattern Map

**Mapped:** 2026-04-27
**Files analyzed:** 7 (4 backend, 3 frontend)
**Analogs found:** 7 / 7 (đều có analog mạnh trong codebase)

---

## File Classification

| File (new/modified) | Status | Role | Data flow | Analog gần nhất | Match |
|---|---|---|---|---|---|
| `user-service/.../web/UserMeController.java` | MODIFY | controller | request-response | `UserMeController.java` (chính nó — extend) + `AdminUserController.java` (PATCH) | exact (self+sibling) |
| `user-service/.../service/UserProfileService.java` | NEW | service | CRUD | `UserPasswordService.java` + `UserCrudService.patchUser()` | exact (role+flow) |
| `user-service/.../web/UpdateMeRequest.java` | NEW | DTO/validation | request-response | `ChangePasswordRequest.java` + `UserCrudService.AdminUserPatchRequest` (record) | exact |
| `user-service/.../domain/UserDto.java` | MODIFY | DTO | — | self (extend record với `hasAvatar`) — UserMapper cần update | exact |
| `frontend/src/services/users.ts` | MODIFY | service (HTTP) | request-response | `changeMyPassword()` (cùng file) | exact |
| `frontend/src/app/profile/settings/page.tsx` | MODIFY | page (form) | request-response | self (Phase 9 password section, cùng file) | exact (extend) |
| `frontend/src/app/profile/settings/page.module.css` | MODIFY | stylesheet | — | self | exact |

**Lưu ý không có analog hoàn hảo cho:** rhf+zod (lần đầu trong codebase). Phase 10 thiết lập pattern mới — xem section "rhf + zod — Pattern Mới" cuối tài liệu.

---

## Pattern Assignments

### 1. `UserMeController.java` (MODIFY) — controller, request-response

**Analog A:** `UserMeController.java` (existing — Phase 9) — extend tại chỗ.
**Analog B:** `AdminUserController.java` lines 22-69 — pattern `@PatchMapping` với DTO record, `@Valid` ở controller layer.

**Imports & class structure** (UserMeController.java lines 1-40):
```java
package com.ptit.htpt.userservice.web;

import com.ptit.htpt.userservice.api.ApiResponse;
import com.ptit.htpt.userservice.jwt.JwtUtils;
// ADD: UserProfileService, UserDto
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/users/me")
public class UserMeController {
    private final UserPasswordService passwordService;
    private final JwtUtils jwtUtils;
    // ADD: private final UserProfileService profileService;
}
```

**Auth pattern — TÁI DỤNG NGUYÊN VẸN** (lines 65-77, hàm `extractUserIdFromBearer`):
```java
private String extractUserIdFromBearer(String authHeader) {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Missing or invalid Authorization header");
    }
    String token = authHeader.substring("Bearer ".length()).trim();
    try {
        Claims claims = jwtUtils.parseToken(token);
        return claims.getSubject();
    } catch (Exception e) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
    }
}
```
Cả `GET /users/me` và `PATCH /users/me` mới đều phải gọi `extractUserIdFromBearer(authHeader)` ngay đầu method (D-05).

**Endpoint pattern — copy từ `changePassword()` (lines 51-59)** + adapt cho GET/PATCH:
```java
@GetMapping
public ApiResponse<UserDto> getMe(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
) {
    String userId = extractUserIdFromBearer(authHeader);
    return ApiResponse.of(200, "Profile loaded", profileService.getMe(userId));
}

@PatchMapping
public ApiResponse<UserDto> updateMe(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
        @Valid @RequestBody UpdateMeRequest body
) {
    String userId = extractUserIdFromBearer(authHeader);
    return ApiResponse.of(200, "Đã cập nhật", profileService.updateMe(userId, body));
}
```

**Pattern note (so với `AdminUserController.patchUser`):** AdminUserController KHÔNG dùng `@Valid` cho PATCH (line 67 comment: "không dùng @Valid — tất cả fields nullable"). Phase 10 vẫn dùng `@Valid` vì có @Pattern cho phone format — Bean Validation `@Pattern` chỉ chạy khi value not-null, nullable optional fields tương thích.

---

### 2. `UpdateMeRequest.java` (NEW) — DTO record, validation

**Analog A:** `ChangePasswordRequest.java` (lines 15-24) — pattern record + jakarta validation.
**Analog B:** `UserCrudService.AdminUserPatchRequest` (UserCrudService.java lines 131-135) — pattern nullable optional fields.

**Excerpt — ChangePasswordRequest pattern** (lines 1-24):
```java
package com.ptit.htpt.userservice.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
    @NotBlank(message = "oldPassword required")
    String oldPassword,
    @NotBlank(message = "newPassword required")
    @Size(min = 8, message = "newPassword must be at least 8 characters")
    @Pattern(regexp = ".*[A-Za-z].*", message = "newPassword must contain at least 1 letter")
    @Pattern(regexp = ".*\\d.*", message = "newPassword must contain at least 1 number")
    String newPassword
) {}
```

**Excerpt — AdminUserPatchRequest nullable pattern** (UserCrudService.java lines 131-135):
```java
public record AdminUserPatchRequest(
    String fullName,   // nullable — update nếu not null
    String phone,      // nullable — update nếu not null
    String roles       // nullable — update nếu not null và not blank
) {}
```

**Phase 10 hybrid (KHÔNG @NotBlank vì nullable):**
```java
public record UpdateMeRequest(
    @Size(max = 120, message = "fullName too long") String fullName,   // nullable
    @Pattern(regexp = "^\\+?[\\d\\s-]{7,20}$",
             message = "Số điện thoại không hợp lệ") String phone     // nullable
) {}
```
(Phone regex theo CONTEXT.md Claude's Discretion — loose VN/international format.)

---

### 3. `UserProfileService.java` (NEW) — service, CRUD

**Analog A:** `UserPasswordService.java` (lines 22-57) — pattern `@Service @Transactional` + load-mutate-save.
**Analog B:** `UserCrudService.patchUser()` (lines 137-144) — pattern conditional update + `touch()`.

**Class skeleton — copy từ UserPasswordService** (lines 22-32):
```java
@Service
@Transactional
public class UserProfileService {
    private final UserRepository userRepo;

    public UserProfileService(UserRepository userRepo) {
        this.userRepo = userRepo;
    }
    // ...
}
```

**Load-mutate-save pattern — copy từ UserPasswordService.changePassword (lines 42-54) + UserCrudService.patchUser (lines 137-144):**
```java
@Transactional(readOnly = true)
public UserDto getMe(String userId) {
    UserEntity entity = userRepo.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    return UserMapper.toDto(entity);  // hasAvatar set bởi mapper (D-06)
}

public UserDto updateMe(String userId, UpdateMeRequest req) {
    UserEntity entity = userRepo.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    if (req.fullName() != null) entity.setFullName(req.fullName());
    if (req.phone() != null) entity.setPhone(req.phone());
    entity.touch();
    return UserMapper.toDto(userRepo.save(entity));
}
```

**Setter pattern đã có sẵn** (UserEntity.java lines 96-113):
```java
public void setFullName(String fullName) { this.fullName = fullName; this.updatedAt = Instant.now(); }
public void setPhone(String phone) { this.phone = phone; this.updatedAt = Instant.now(); }
public void touch() { this.updatedAt = Instant.now(); }
```
→ Không cần thêm setter mới trên UserEntity.

---

### 4. `UserDto.java` + `UserMapper.java` (MODIFY) — DTO + mapper

**Analog:** chính nó (extend record).

**Existing UserDto** (lines 11-20):
```java
public record UserDto(
    String id, String username, String email, String roles,
    String fullName, String phone,
    Instant createdAt, Instant updatedAt
) {}
```

**Phase 10 extension (D-06):**
```java
public record UserDto(
    String id, String username, String email, String roles,
    String fullName, String phone,
    boolean hasAvatar,                  // ADD — Phase 10 luôn false
    Instant createdAt, Instant updatedAt
) {}
```

**UserMapper.toDto update** (lines 10-21):
```java
public static UserDto toDto(UserEntity e) {
    return new UserDto(
        e.id(), e.username(), e.email(), e.roles(),
        e.fullName(), e.phone(),
        false,                          // D-06: hasAvatar luôn false Phase 10
        e.createdAt(), e.updatedAt()
    );
}
```
**Lưu ý ripple effect:** UserMapper được dùng bởi `UserCrudService` (admin endpoints) + `AuthService` (login response). Mọi caller hiện tại sẽ tự nhận `hasAvatar:false` — KHÔNG break wire format vì chỉ thêm field. Frontend type `User` cần thêm `hasAvatar?: boolean` (optional để giữ tương thích).

---

### 5. `frontend/src/services/users.ts` (MODIFY) — HTTP service

**Analog:** `changeMyPassword()` cùng file (lines 44-51).

**Existing pattern** (lines 38-51):
```typescript
// ============================================================
// Phase 9 / Plan 09-04 (AUTH-07). Self-service password change.
// Endpoint backend: POST /api/users/me/password (Plan 09-03).
// ============================================================

export interface ChangePasswordBody {
  oldPassword: string;
  newPassword: string;
}

export function changeMyPassword(body: ChangePasswordBody): Promise<{ changed: true }> {
  return httpPost<{ changed: true }>('/api/users/me/password', body);
}
```

**Phase 10 addition — copy pattern, dùng httpGet/httpPatch:**
```typescript
// ============================================================
// Phase 10 / ACCT-03. Self-service profile read & update.
// Endpoints backend: GET /api/users/me, PATCH /api/users/me.
// ============================================================

export interface UpdateMeBody {
  fullName?: string;
  phone?: string;
}

// User type đã có fullName/phone — Phase 10 thêm hasAvatar (optional)
export function getMe(): Promise<User> {
  return httpGet<User>('/api/users/me');
}

export function patchMe(body: UpdateMeBody): Promise<User> {
  return httpPatch<User>('/api/users/me', body);
}
```

**Imports sẵn có** (lines 1-7) — `httpPatch` đã có trong import; chỉ cần đảm bảo `httpGet, httpPatch` có trong destructure (đã có).

---

### 6. `frontend/src/app/profile/settings/page.tsx` (MODIFY) — page section

**Analog:** chính nó (Phase 9 password section, lines 24-135).

**Strategy:** giữ nguyên password section (D-01: Section 3 — Security), thêm Section 1 "Profile Info" lên trên + Section 2 "Avatar" placeholder.

**Existing imports** (lines 1-6) — extend:
```typescript
'use client';
import { useState, type FormEvent } from 'react';
import styles from './page.module.css';
import { changeMyPassword } from '@/services/users';
import { isApiError } from '@/services/errors';
```

**Phase 10 imports cần thêm:**
```typescript
import { useEffect } from 'react';
import { useForm } from 'react-hook-form';            // NEW DEP
import { zodResolver } from '@hookform/resolvers/zod';// NEW DEP
import { z } from 'zod';                              // NEW DEP
import { getMe, patchMe } from '@/services/users';
import { useToast } from '@/components/ui/Toast/Toast';
import { useAuth } from '@/providers/AuthProvider';
```

**Toast usage pattern — copy từ ToastProvider** (Toast.tsx lines 12, 17-21):
```typescript
const { showToast } = useToast();
// trong handler thành công:
showToast('Đã cập nhật', 'success');  // 3.5s tự ẩn
```

**Auth state update pattern — copy từ AuthProvider** (AuthProvider.tsx lines 75-81):
```typescript
const { user, login } = useAuth();
// sau patchMe success:
if (user) {
  login({ ...user, name: updated.fullName ?? user.name });  // updates context + localStorage 'userProfile'
}
// → Header tự re-render với fullName mới (KHÔNG cần router.refresh)
```

**LƯU Ý PATTERN UPDATE vs CONTEXT.md D-07:**
> CONTEXT D-07 nói "localStorage + router.refresh()". Codebase hiện tại đã có AuthProvider context (`useAuth().login()` đã ghi localStorage 'userProfile' nội bộ — line 78-80). Khuyến nghị dùng `login(updatedUser)` thay vì manual localStorage write + router.refresh() — đơn giản hơn, không cần soft reload, đồng bộ cross-tab nhờ storage event listener (lines 54-73). KHÔNG vi phạm spirit D-07 (vẫn không tạo AuthContext mới — đã có sẵn).

**Error handling pattern — copy từ Phase 9 password handler** (lines 61-70):
```typescript
try {
  const updated = await patchMe({ fullName, phone });
  showToast('Đã cập nhật', 'success');
  // sync auth context...
} catch (err) {
  if (isApiError(err) && err.status === 400 && err.fieldErrors?.length) {
    // map fieldErrors to rhf setError per field
    err.fieldErrors.forEach(fe => setError(fe.field as 'fullName' | 'phone', { message: fe.message }));
  } else if (isApiError(err)) {
    showToast(err.message ?? 'Có lỗi xảy ra', 'error');
  } else {
    showToast('Có lỗi xảy ra, vui lòng thử lại', 'error');
  }
}
```

**Form/Field/Section CSS classes — TÁI DỤNG NGUYÊN VẸN** từ page.module.css:
- `.section` (border + padding) — line 3
- `.sectionTitle` — line 4
- `.form`, `.field`, `.label`, `.input` — lines 5-9
- `.fieldError` (đỏ) — line 11
- `.submit` (primary button + disabled state) — lines 14-15

Có thể cần thêm:
- `.avatarPlaceholder` (Section 2 initials circle) — chưa có; thêm CSS mới.
- `.readonly` (cho email read-only) — có thể tái dụng `.input` + thêm `disabled` attribute.

---

### 7. `frontend/src/app/profile/settings/page.module.css` (MODIFY)

**Analog:** chính nó (Phase 9 styles).

**Existing reusable classes** (đã list ở trên).

**Cần thêm (Claude's Discretion):**
```css
.avatarPlaceholder {
  width: 80px; height: 80px; border-radius: 50%;
  background: var(--primary); color: white;
  display: flex; align-items: center; justify-content: center;
  font-size: 1.75rem; font-weight: 600;
}
.comingSoon { font-size: 0.875rem; color: var(--on-surface-variant); margin-top: var(--space-2); }
.readonly { background: var(--surface-variant); cursor: not-allowed; }
```

---

## Shared Patterns

### Authentication / userId extraction (backend)
**Source:** `UserMeController.extractUserIdFromBearer()` (lines 65-77)
**Apply to:** mọi endpoint mới trong UserMeController (GET /me, PATCH /me)
- Bearer token bắt buộc → 401 nếu thiếu/invalid
- userId LẤY TỪ JWT subject — KHÔNG nhận từ path/body/header X-User-Id (T-09-03-02 carry-over)

### ApiResponse envelope (backend)
**Source:** `ApiResponse.of(status, message, data)` — `api/ApiResponse.java` lines 11-13
**Apply to:** tất cả response của controller mới
```java
return ApiResponse.of(200, "Profile loaded", profileService.getMe(userId));
return ApiResponse.of(200, "Đã cập nhật", profileService.updateMe(userId, body));
```

### Error envelope (backend)
**Source:** `GlobalExceptionHandler` (api/GlobalExceptionHandler.java lines 25-53, 76-100)
- `MethodArgumentNotValidException` → 400 `VALIDATION_ERROR` với fieldErrors
- `ResponseStatusException` → status được resolve, code map qua `mapCommonCode` (lines 176-185)
- Phase 10 KHÔNG cần exception class mới (PATCH chỉ thấy NOT_FOUND nếu user không tồn tại — ResponseStatusException đủ)

### HTTP wrapper (frontend)
**Source:** `services/http.ts` (lines 49-148)
- Auto-attach Bearer token từ `getAccessToken()` (line 60)
- Auto-unwrap `ApiResponse.data` envelope (line 108)
- 401 → clear tokens + redirect /login (lines 118-131) — **TRỪ** auth endpoints (`/api/users/auth/login|register`)
- Throw `ApiError` với `code, status, message, fieldErrors` (lines 133-141)

### Toast (frontend)
**Source:** `components/ui/Toast/Toast.tsx` (lines 14-39)
- `useToast().showToast(message, 'success'|'error'|'info')` — auto-dismiss 3.5s
- ToastProvider đã được mount global (kiểm tra ở app layout)

### Auth state update (frontend)
**Source:** `providers/AuthProvider.tsx` (lines 75-81)
- `useAuth().login(user)` — set state + ghi localStorage `userProfile` + propagate cross-tab via storage event
- Đảm bảo Header (Header.tsx line 14) re-render tức thì với name mới — không cần router.refresh()

---

## rhf + zod — Pattern Mới (Lần Đầu Trong Codebase)

**Bối cảnh:** Phase 10 SET PATTERN cho v1.2 (CONTEXT §code_context). Chưa có file nào dùng rhf+zod. Phase 9 password form dùng inline `useState` + manual validate function.

**Đề xuất pattern (no analog — base on rhf+zod canonical):**

```typescript
// trong /profile/settings/page.tsx — Profile Info section

const profileSchema = z.object({
  fullName: z.string().min(1, 'Vui lòng nhập họ tên').max(120, 'Họ tên quá dài'),
  phone: z.string().regex(/^\+?[\d\s-]{7,20}$/, 'Số điện thoại không hợp lệ').or(z.literal('')),
});
type ProfileFormData = z.infer<typeof profileSchema>;

const {
  register, handleSubmit, formState: { errors, isSubmitting, isDirty },
  reset, setError,
} = useForm<ProfileFormData>({
  resolver: zodResolver(profileSchema),
  defaultValues: { fullName: '', phone: '' },
});

useEffect(() => {
  getMe().then(me => reset({ fullName: me.fullName ?? '', phone: me.phone ?? '' }))
         .catch(() => showToast('Không tải được thông tin', 'error'));
}, [reset, showToast]);

const onSubmit = handleSubmit(async (data) => {
  try {
    const updated = await patchMe(data);
    if (user) login({ ...user, name: updated.fullName ?? user.name });
    showToast('Đã cập nhật', 'success');
    reset(data);  // mark form clean
  } catch (err) {
    if (isApiError(err) && err.fieldErrors?.length) {
      err.fieldErrors.forEach(fe => setError(fe.field as keyof ProfileFormData, { message: fe.message }));
    } else if (isApiError(err)) {
      showToast(err.message ?? 'Có lỗi xảy ra', 'error');
    }
  }
});

// JSX:
<form onSubmit={onSubmit} className={styles.form} noValidate>
  <div className={styles.field}>
    <label htmlFor="fullName" className={styles.label}>Họ và tên</label>
    <input id="fullName" className={styles.input} {...register('fullName')} />
    {errors.fullName && <p className={styles.fieldError}>{errors.fullName.message}</p>}
  </div>
  {/* phone field tương tự */}
  <button type="submit" className={styles.submit} disabled={isSubmitting || !isDirty}>
    {isSubmitting ? 'Đang lưu...' : 'Lưu thay đổi'}
  </button>
</form>
```

**Dependencies cần install (NEW):**
- `react-hook-form` (peer-free, ~25KB)
- `zod` (~50KB)
- `@hookform/resolvers` (cầu nối, <5KB)

Tổng ~80KB — chấp nhận được cho project demo. Đăng ký vào `package.json` (Plan riêng cho deps install).

---

## No Analog Found

Không có file nào không tìm được analog — toàn bộ Phase 10 patterns đã có precedent trong codebase (riêng rhf+zod là pattern external chuẩn, không cần codebase analog).

---

## Metadata

**Analog search scope:**
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/`
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/service/`
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/domain/`
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/api/`
- `sources/frontend/src/app/profile/settings/`
- `sources/frontend/src/services/`
- `sources/frontend/src/providers/`
- `sources/frontend/src/components/ui/Toast/`
- `sources/frontend/src/components/layout/Header/`

**Files scanned:** 14 (mọi file đụng tới scope Phase 10 + analog candidates)
**Pattern extraction date:** 2026-04-27
**Key insight:** AuthProvider đã có sẵn — D-07 (CONTEXT.md) có thể đơn giản hóa từ "localStorage + router.refresh()" → `useAuth().login(updatedUser)` (vẫn không vi phạm "không tạo AuthContext mới" vì context đã tồn tại).
