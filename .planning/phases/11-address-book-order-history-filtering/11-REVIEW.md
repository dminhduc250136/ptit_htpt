---
phase: 11-address-book-order-history-filtering
reviewed: 2026-04-27T10:00:00Z
depth: standard
files_reviewed: 23
files_reviewed_list:
  - sources/backend/user-service/src/main/resources/db/migration/V101__create_addresses.sql
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/domain/AddressEntity.java
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/domain/AddressDto.java
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/repository/AddressRepository.java
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/exception/AddressLimitExceededException.java
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/service/AddressRequest.java
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/service/AddressService.java
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/AddressController.java
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/api/GlobalExceptionHandler.java
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/repository/OrderRepository.java
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderCrudService.java
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/OrderController.java
  - sources/frontend/src/components/ui/AddressCard/AddressCard.tsx
  - sources/frontend/src/components/ui/AddressForm/AddressForm.tsx
  - sources/frontend/src/components/ui/AddressPicker/AddressPicker.tsx
  - sources/frontend/src/components/ui/OrderFilterBar/OrderFilterBar.tsx
  - sources/frontend/src/app/profile/addresses/page.tsx
  - sources/frontend/src/app/profile/orders/page.tsx
  - sources/frontend/src/app/profile/page.tsx
  - sources/frontend/src/app/checkout/page.tsx
  - sources/frontend/src/services/users.ts
  - sources/frontend/src/services/orders.ts
  - sources/frontend/src/types/index.ts
findings:
  critical: 0
  warning: 5
  info: 6
  total: 11
status: issues_found
---

# Phase 11: Code Review Report

**Reviewed:** 2026-04-27T10:00:00Z
**Depth:** standard
**Files Reviewed:** 23
**Status:** issues_found

## Summary

Phase 11 bổ sung hai tính năng chính: (1) Address Book — CRUD đầy đủ với ownership check, giới hạn 10 địa chỉ/user, partial unique index đảm bảo chỉ 1 default/user; (2) Order History Filtering — filter theo status / date range / keyword trên order ID. Kiến trúc tổng thể rõ ràng, ownership được enforce đúng ở service layer, JWT subject dùng nhất quán từ phía user-service.

Phát hiện **5 Warning** (không có Critical) và **6 Info**. Không có lỗ hổng bảo mật nghiêm trọng. Vấn đề chính cần chú ý:

- `updateAddress` bỏ qua field `isDefault` trong request — user không thể cập nhật default flag khi edit (hành vi không nhất quán với `createAddress`).
- Race condition nhỏ giữa `clearDefaultByUserId` và `save` trong `setDefault`: nếu @Modifying query không flush kịp, partial unique index của DB sẽ chặn, nhưng lỗi 500 sẽ trả về thay vì 422.
- `OrderController.listOrders` không có ownership check khi `userId` vắng mặt — admin path fallback trả toàn bộ orders.
- `loadOrders` trong `orders/page.tsx` không wrapped trong `useCallback` — tạo dependency instability khi pass vào `RetrySection`.
- `updateAddress` trong `users.ts` dùng `httpPatch` nhưng backend endpoint là `PUT` — mismatch HTTP method.

---

## Warnings

### WR-01: `updateAddress` bỏ qua field `isDefault` — không nhất quán với `createAddress`

**File:** `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/service/AddressService.java:86-95`

**Issue:** `updateAddress` chỉ update các text fields (fullName, phone, street, ward, district, city) mà không xử lý `req.isDefault()`. Trong khi `createAddress` (dòng 69-72) có logic `if (req.isDefault()) clearDefaultByUserId + setDefault`. Kết quả: user gửi `PUT` với `isDefault: true` nhưng address không được đặt làm default — hành vi im lặng, không có lỗi báo về client.

**Fix:**
```java
public AddressDto updateAddress(String userId, String addressId, AddressRequest req) {
    AddressEntity entity = findAndCheckOwner(userId, addressId);
    entity.setFullName(req.fullName());
    entity.setPhone(req.phone());
    entity.setStreet(req.street());
    entity.setWard(req.ward());
    entity.setDistrict(req.district());
    entity.setCity(req.city());
    // Thêm: xử lý isDefault giống createAddress
    if (req.isDefault() && !entity.isDefault()) {
        addressRepo.clearDefaultByUserId(userId);
        entity.setDefault(true);
    }
    return AddressDto.from(addressRepo.save(entity));
}
```

---

### WR-02: HTTP method mismatch — `updateAddress` trong FE dùng `httpPatch` nhưng backend là `PUT`

**File:** `sources/frontend/src/services/users.ts:91-93`

**Issue:** `updateAddress` gọi `httpPatch<SavedAddress>(...)` nhưng backend `AddressController` định nghĩa endpoint `@PutMapping("/{id}")` (dòng 89). PATCH và PUT có semantics khác nhau; nếu backend Spring không map PATCH thì request sẽ trả 405 Method Not Allowed.

**Fix:**
```typescript
// Trong users.ts — đổi httpPatch → httpPut (hoặc thêm hàm httpPut nếu chưa có)
export function updateAddress(id: string, body: AddressBody): Promise<SavedAddress> {
  return httpPut<SavedAddress>(`/api/users/me/addresses/${encodeURIComponent(id)}`, body);
}
```
Nếu không muốn thêm `httpPut`, backend cần thêm `@PatchMapping("/{id}")` hoặc đổi sang `@PutMapping` + `@PatchMapping` cùng lúc.

---

### WR-03: `loadOrders` không phải `useCallback` — instability khi pass vào `RetrySection`

**File:** `sources/frontend/src/app/profile/orders/page.tsx:43-63`

**Issue:** `loadOrders` được khai báo là `async function` bình thường bên trong component (không phải `useCallback`). Hàm này được pass vào `RetrySection onRetry={loadOrders}` (dòng 109). Mỗi render tạo ra một reference mới, có thể gây `RetrySection` re-render không cần thiết nếu nó memo hóa props. Đồng thời, `useEffect` ở dòng 65-69 phụ thuộc vào `searchParams` và `user?.id` nhưng gọi `loadOrders()` trực tiếp — nếu `loadOrders` thay đổi reference thì eslint-exhaustive-deps sẽ cần thêm vào deps.

**Fix:**
```typescript
const loadOrders = useCallback(async () => {
  setOrdersLoading(true);
  setOrdersFailed(false);
  try {
    const resp = await listMyOrders({
      page: 0,
      size: 20,
      sort: 'createdAt,desc',
      status: statusParam !== 'ALL' ? statusParam : undefined,
      from: fromParam || undefined,
      to: toParam || undefined,
      q: qParam || undefined,
    });
    setOrders(resp?.content ?? []);
    setTotalElements(resp?.totalElements ?? 0);
  } catch {
    setOrdersFailed(true);
  } finally {
    setOrdersLoading(false);
  }
// eslint-disable-next-line react-hooks/exhaustive-deps
}, [statusParam, fromParam, toParam, qParam, user?.id]);
```

---

### WR-04: `setDefault` có thể lỗi 500 nếu flush order sai — @Modifying không tự flush trước save

**File:** `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/service/AddressService.java:120-125`

**Issue:** Trong `setDefault`, `clearDefaultByUserId` (dùng `@Modifying`) chạy UPDATE nhưng nếu JPA persistence context chưa flush entity hiện tại (entity được load ở `findAndCheckOwner`), thứ tự write có thể bị đảo: entity cũ vẫn được JPA cache với `isDefault=true`, sau đó UPDATE chạy, rồi `save(entity)` flush lại — tuy nhiên `entity.isDefault()` vẫn là old state ở trong cache. Thực tế `@Modifying` theo mặc định không `clearAutomatically=true`, nên persistence context không bị invalidate. Nếu entity đang được track với `isDefault=true`, sau khi `clearDefaultByUserId` chạy thành công ở DB, `save(entity)` flush entity cũ có thể tạm thời violate constraint (2 rows default=true trong cùng 1 transaction flush window).

**Fix:** Thêm `clearAutomatically = true` vào annotation `@Modifying` để đảm bảo persistence context được làm sạch sau bulk update:
```java
@Modifying(clearAutomatically = true)
@Query("UPDATE AddressEntity a SET a.isDefault = false WHERE a.userId = :userId AND a.isDefault = true")
void clearDefaultByUserId(@Param("userId") String userId);
```

---

### WR-05: `OrderFilterBar` — `onChange` phụ thuộc nhưng bị loại khỏi deps bằng eslint-disable

**File:** `sources/frontend/src/components/ui/OrderFilterBar/OrderFilterBar.tsx:38-44`

**Issue:** `useEffect` (dòng 38-44) gọi `onChange(...)` nhưng comment ở dòng 37 nói "caller must wrap in useCallback" và eslint-disable được dùng để loại `onChange` khỏi deps. Nếu caller không wrap trong `useCallback` (ví dụ `orders/page.tsx` dòng 71 wrap đúng, nhưng không có type-level enforcement), debounce sẽ bị reset liên tục mỗi re-render vì `onChange` thay đổi reference. Đây là một silent contract không được enforce.

**Fix:** Document contract rõ hơn ở propTypes, hoặc dùng `useRef` để giữ stable reference cho `onChange`:
```typescript
const onChangeRef = useRef(onChange);
useEffect(() => { onChangeRef.current = onChange; });

useEffect(() => {
  const timer = setTimeout(() => {
    onChangeRef.current({ status, from, to, q });
  }, 400);
  return () => clearTimeout(timer);
}, [status, from, to, q]);
```
Cách này không cần eslint-disable và không phụ thuộc caller wrap useCallback.

---

## Info

### IN-01: `AddressCard` và `AddressPicker` định nghĩa lại `SavedAddress` cục bộ — trùng với `types/index.ts`

**File:** `sources/frontend/src/components/ui/AddressCard/AddressCard.tsx:6-17`, `sources/frontend/src/components/ui/AddressPicker/AddressPicker.tsx:7-18`

**Issue:** Cả hai file tự khai báo interface `SavedAddress` giống hệt nhau và với `types/index.ts`. Đây là code duplication — nếu backend thêm field mới, phải cập nhật 3 chỗ.

**Fix:** Import từ `@/types` thay vì khai báo lại:
```typescript
import type { SavedAddress } from '@/types';
```

---

### IN-02: `OrderController` thiếu endpoint `GET /orders/me` riêng biệt — logic phân nhánh bằng header dễ gây nhầm

**File:** `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/OrderController.java:31-52`

**Issue:** Cùng endpoint `GET /orders` xử lý cả user (khi có `X-User-Id`) và admin (khi không có). Logic phân nhánh ở dòng 45-51 ẩn trong code và không rõ ràng với API consumer. Nếu gateway không forward header `X-User-Id` đúng cách, user request sẽ fallback sang admin path (trả toàn bộ orders).

**Fix (gợi ý):** Tách thành hai endpoints rõ ràng hoặc document contract này trong API spec. Ít nhất cần log warning khi `userId` vắng mặt trên path user-facing.

---

### IN-03: `profile/page.tsx` — tab "Địa chỉ" không load addresses thực, chỉ hiển thị static text

**File:** `sources/frontend/src/app/profile/page.tsx:173-183`

**Issue:** Tab `addresses` trong `ProfilePage` hiển thị hardcoded text "Chưa có địa chỉ nào" và một nút "+ Thêm địa chỉ mới" không hoạt động (không có `onClick`). Trong khi `/profile/addresses/page.tsx` đã có đầy đủ chức năng. Đây là dead code path — tab addresses trong `ProfilePage` là stub chưa wire-up.

**Fix:** Xóa tab addresses khỏi `ProfilePage` và redirect button "Địa chỉ" sang `/profile/addresses` (đã làm ở dòng 108-111 nhưng tab content vẫn là stub).

---

### IN-04: `listMyOrders` trong `orders.ts` filter `status !== 'ALL'` ở cả FE service và `orders/page.tsx` — logic trùng lặp

**File:** `sources/frontend/src/services/orders.ts:54`, `sources/frontend/src/app/profile/orders/page.tsx:51`

**Issue:** Cả hai chỗ đều kiểm tra `status !== 'ALL'` trước khi set query param. Logic này nên chỉ ở một lớp (service layer).

**Fix:** Bỏ check trong `orders/page.tsx`, để `listMyOrders` service tự normalize (đã làm dòng 54 trong `orders.ts`), hoặc ngược lại.

---

### IN-05: `AddressPicker` không có trạng thái khi `addresses` load nhưng tất cả đều `isDefault=false`

**File:** `sources/frontend/src/components/ui/AddressPicker/AddressPicker.tsx:36-40`

**Issue:** `useEffect` pre-select default address: nếu không có address nào có `isDefault=true`, `selectedId` sẽ là `null`. Khi đó không address nào được highlight là "đang chọn". Người dùng mở picker thấy danh sách nhưng không biết cái nào đang được áp dụng. Không gây crash nhưng UX không rõ ràng.

**Fix:** Nếu không có default, pre-select address đầu tiên trong list và gọi `onSelect` ngay:
```typescript
useEffect(() => {
  if (addresses.length > 0) {
    const defaultAddr = addresses.find((a) => a.isDefault) ?? addresses[0];
    setSelectedId(defaultAddr.id);
    onSelect(defaultAddr); // pre-fill form với default hoặc first
  }
}, [addresses]); // onSelect cần stable ref (useCallback từ caller)
```

---

### IN-06: `createAddress` trong `AddressService` — không xử lý trường hợp `isDefault=true` khi user chưa có address nào

**File:** `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/service/AddressService.java:56-74`

**Issue:** Khi tạo address đầu tiên với `isDefault=false` (default), user sẽ không có default address nào. Không gây bug, nhưng thường convention là address đầu tiên tự động là default. Hiện tại FE không tự set `isDefault=true` cho địa chỉ đầu tiên.

**Fix (gợi ý):** Trong `createAddress`, nếu `countByUserId(userId) == 0` (trước khi tạo), tự động set `isDefault=true` bất kể request body:
```java
boolean shouldBeDefault = req.isDefault() || addressRepo.countByUserId(userId) == 0;
if (shouldBeDefault) {
    addressRepo.clearDefaultByUserId(userId);
    entity.setDefault(true);
}
```

---

_Reviewed: 2026-04-27T10:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
