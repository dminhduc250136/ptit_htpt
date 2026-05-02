---
phase: 10-user-svc-schema-profile-editing
reviewed: 2026-04-27T07:00:00Z
depth: standard
files_reviewed: 9
files_reviewed_list:
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/domain/UserDto.java
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/domain/UserMapper.java
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/service/UserProfileService.java
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/UpdateMeRequest.java
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/UserMeController.java
  - sources/frontend/src/app/profile/settings/page.module.css
  - sources/frontend/src/app/profile/settings/page.tsx
  - sources/frontend/src/services/users.ts
  - sources/frontend/src/types/index.ts
findings:
  critical: 0
  warning: 3
  info: 3
  total: 6
status: issues_found
---

# Phase 10: Code Review Report

**Reviewed:** 2026-04-27T07:00:00Z
**Depth:** standard
**Files Reviewed:** 9
**Status:** issues_found

## Summary

Review phase 10 — ACCT-03 profile editing: 5 file backend (UserDto, UserMapper, UserProfileService, UpdateMeRequest, UserMeController) và 4 file frontend (page.tsx, page.module.css, services/users.ts, types/index.ts).

Không có lỗi Critical. Phát hiện 3 Warning (logic/contract risks) và 3 Info (code smell, dead code).

Điểm đáng chú ý nhất: `updateMe` trong service có double-write `updatedAt` do mỗi setter trong entity tự gọi `this.updatedAt = Instant.now()`, sau đó `touch()` gọi thêm một lần nữa — không sai về correctness nhưng gây extra mutation không cần thiết. Quan trọng hơn là `@Size` annotation trên `phone` field trong `UpdateMeRequest` bị thiếu — chỉ có `@Pattern`, không có giới hạn độ dài, trong khi DB column là `length = 20`.

---

## Warnings

### WR-01: `phone` thiếu `@Size` constraint — DB column giới hạn 20 ký tự nhưng validation không bắt

**File:** `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/UpdateMeRequest.java:18-19`

**Issue:** `@Pattern` chỉ kiểm tra định dạng (7–20 ký tự raw, nhưng pattern cho phép spaces và dashes). `UserEntity.phone` có `@Column(length = 20)`. Nếu caller gửi phone hợp lệ theo regex nhưng sau khi DB lưu sẽ bị truncate hoặc throw `DataException` — lỗi 500 thay vì 400. Ví dụ: `+1 234 567 890 123` (18 ký tự) khớp regex nhưng `phone` field trong DB chỉ 20 chars, edge case này hiện không bị chặn rõ ràng ở validation layer.

**Fix:**
```java
@Size(max = 20, message = "Số điện thoại không được vượt quá 20 ký tự")
@Pattern(regexp = "^\\+?[0-9\\s-]{7,20}$",
         message = "Số điện thoại không hợp lệ")
String phone
```

---

### WR-02: `updateMe` — `setFullName` / `setPhone` đều tự cập nhật `updatedAt`, sau đó `touch()` ghi đè lần nữa — 3 lần write `updatedAt` trong 1 transaction

**File:** `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/service/UserProfileService.java:54-56`

**Issue:** `UserEntity.setFullName()` gọi `this.updatedAt = Instant.now()`, `setPhone()` cũng gọi `this.updatedAt = Instant.now()`, sau đó `touch()` gọi lần thứ 3. Nếu clock có độ phân giải thấp hoặc hai setter được gọi trong cùng nanosecond, `updatedAt` sẽ phản ánh thời điểm của `touch()` (lần cuối), không phải thời điểm thực sự của cả batch. Không phải data corruption nhưng là logic không nhất quán: mỗi setter trong entity không nên tự cập nhật `updatedAt` nếu service layer sẽ gọi `touch()` ở cuối.

**Fix (ngắn hạn):** Bỏ `touch()` vì `setFullName` / `setPhone` đã set `updatedAt`:
```java
if (req.fullName() != null) entity.setFullName(req.fullName());
if (req.phone() != null)    entity.setPhone(req.phone());
// Bỏ dòng entity.touch(); — setters đã cập nhật updatedAt
return UserMapper.toDto(userRepo.save(entity));
```

Hoặc (dài hạn): Bỏ `updatedAt` assignment khỏi từng setter, chỉ giữ trong `touch()`, gọi `touch()` sau khi set xong.

---

### WR-03: `getMe` trong `useEffect` không bắt lỗi network riêng — `alive` guard có thể che giấu lỗi sau unmount

**File:** `sources/frontend/src/app/profile/settings/page.tsx:62-69`

**Issue:** Khi `getMe()` thất bại sau component unmount (ví dụ: user navigate đi trong khi request đang pending), `catch` block chạy `if (alive) showToast(...)`. Điều này đúng — không crash. Tuy nhiên `reset` không được gọi trong case thất bại, nên form giữ `defaultValues: { fullName: '', phone: '' }`. Nếu user quay lại trang ngay sau (fast navigation), form sẽ hiển thị rỗng và không có toast báo lỗi (vì `alive` đã false từ cleanup lần trước). Đây là race condition nhỏ: lần mount thứ hai sẽ trigger `getMe()` mới nên thường tự phục hồi, nhưng trường hợp hai requests chạy song song (strict mode) có thể gây reset form về rỗng rồi lại fill đúng.

**Fix:** Thêm `AbortController` để cancel request khi unmount, thay vì chỉ dùng `alive` flag:
```typescript
useEffect(() => {
  const controller = new AbortController();
  getMe(controller.signal)
    .then(me => {
      reset({ fullName: me.fullName ?? '', phone: me.phone ?? '' });
      setProfileEmail(me.email ?? '');
    })
    .catch(err => {
      if (!controller.signal.aborted) showToast('Không tải được thông tin', 'error');
    });
  return () => controller.abort();
}, [reset, showToast]);
```
(Cần update `getMe` trong `services/users.ts` để nhận optional `signal` parameter.)

---

## Info

### IN-01: `User` interface trong `types/index.ts` có `fullName: string` (required) nhưng backend `UserDto` trả `fullName` nullable

**File:** `sources/frontend/src/types/index.ts:46`

**Issue:** `UserDto.fullName` là `String` nullable trong Java (record field không có `@NotNull`). Tuy nhiên `User` interface ở frontend khai báo `fullName: string` (required, non-nullable). Điều này có thể gây lỗi runtime nếu user chưa có `fullName` (NULL trong DB) và code nào đó dùng `user.fullName.trim()` mà không null-check.

**Fix:**
```typescript
export interface User {
  // ...
  fullName?: string;   // nullable — backend UserDto có thể trả null
  // ...
}
```
Sau đó update các điểm sử dụng (page.tsx line 76 đã dùng `user.name` không phải `user.fullName` nên ít bị ảnh hưởng).

---

### IN-02: Avatar placeholder dùng `profileEmail` thay vì `fullName` để lấy initial — logic không nhất quán với tên section

**File:** `sources/frontend/src/app/profile/settings/page.tsx:190`

**Issue:** `{(profileEmail || 'U').charAt(0).toUpperCase()}` — avatar initial lấy từ email (ví dụ: email `john@example.com` → initial `j`). Thông thường avatar initial nên lấy từ `fullName` (ví dụ: `Nguyễn Văn A` → `N`). Hiện tại `fullName` đã có trong form state (react-hook-form `watch` hoặc `getValues`).

**Fix:**
```tsx
// Lấy initial từ fullName nếu có, fallback về email, fallback về 'U'
const displayInitial = (
  (form.getValues('fullName') || profileEmail || 'U').charAt(0).toUpperCase()
);
// Trong JSX:
{displayInitial}
```

---

### IN-03: `UserMeController` Javadoc comment ở line 29 còn nói "Phase 9 sẽ extend với..." — outdated sau khi Phase 10 đã implement

**File:** `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/UserMeController.java:27-29`

**Issue:** Comment `"Phase 10 sẽ extend với GET/PATCH /me cho profile editing — controller này là foundation. Trong Phase 9 chỉ map password change."` là outdated — Phase 10 đã implement xong GET/PATCH /me trong chính file này. Comment gây nhầm lẫn về state hiện tại của controller.

**Fix:**
```java
/**
 * Phase 9 / Plan 09-03 (AUTH-07). Endpoint user-self /me/* group.
 * Phase 10 / Plan 10-01 (ACCT-03): Thêm GET /me + PATCH /me cho profile editing.
 * ...
 */
```

---

_Reviewed: 2026-04-27T07:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
