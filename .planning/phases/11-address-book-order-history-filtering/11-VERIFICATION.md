---
phase: 11-address-book-order-history-filtering
verified: 2026-04-27T15:00:00Z
status: human_needed
score: 17/17 must-haves verified
overrides_applied: 0
re_verification:
  previous_status: gaps_found
  previous_score: 14/17
  gaps_closed:
    - "PUT /users/me/addresses/{id} cập nhật address — WR-02 RESOLVED: httpPatch → httpPut trong updateAddress()"
    - "setDefaultAddress() dùng httpPut cho PUT /users/me/addresses/{id}/default — WR-02 RESOLVED: httpPatch → httpPut"
    - "Checkout page AddressPicker — ảnh hưởng gián tiếp của WR-02 đã được giải quyết"
  gaps_remaining: []
  regressions: []
human_verification:
  - test: "Kiểm tra UI /profile/addresses — create + edit + delete + set-default flow"
    expected: "Create modal mở với AddressForm 6 fields; submit thêm địa chỉ mới vào list (toast 'Đã thêm địa chỉ'); Sửa mở modal edit → submit gọi PUT → cập nhật đúng; Đặt mặc định gửi PUT /{id}/default → highlight address; Xóa hiện confirm modal → deleteAddress → toast; limit 10 vô hiệu hóa button Thêm"
    why_human: "Luồng UI end-to-end cần browser + running backend stack. WR-02 đã fix programmatically — cần xác nhận 405 không còn xảy ra khi test thực tế"
  - test: "Kiểm tra /profile/orders — filter bar hoạt động, URL state encode"
    expected: "Chọn status → URL cập nhật (?status=DELIVERED); thay đổi date range → URL cập nhật; nhập keyword → debounce 400ms → URL cập nhật; 'Xóa bộ lọc' hiện khi có filter active và reset URL về /profile/orders"
    why_human: "Behavior debounce timing và URL state cần browser interaction thực tế"
  - test: "Kiểm tra /checkout — AddressPicker hiển thị, chọn address snap-fill form"
    expected: "Picker dropdown hiển thị khi đã login; chọn saved address → 6 fields (fullName, phone, street, ward, district, city) tự điền; divider '— hoặc điền thủ công —' hiện; logged-out → picker ẩn"
    why_human: "Cần logged-in user với saved addresses và running API để test fetch + snap-fill"
---

# Phase 11: Address Book + Order History Filtering — Verification Report

**Phase Goal:** Address Book + Order History Filtering — user có thể xem/thêm/sửa/xóa địa chỉ tại /profile/addresses, đặt địa chỉ mặc định, snap-fill checkout form; xem order history với filter status/date/keyword tại /profile/orders.
**Verified:** 2026-04-27T15:00:00Z
**Status:** human_needed
**Re-verification:** Yes — sau khi fix WR-02 (httpPatch → httpPut)

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | GET /users/me/addresses trả list addresses của user đang đăng nhập (filter by user_id từ JWT) | ✓ VERIFIED | AddressController @GetMapping + AddressService.listAddresses() filter by userId từ JWT. findByUserIdOrderByIsDefaultDescCreatedAtDesc() |
| 2 | POST /users/me/addresses tạo address mới; nếu tổng > 10 → 422 ADDRESS_LIMIT_EXCEEDED | ✓ VERIFIED | AddressService.createAddress() kiểm tra countByUserId >= 10 → throw AddressLimitExceededException → GlobalExceptionHandler → 422 |
| 3 | PUT /users/me/addresses/{id} cập nhật address (chỉ owner); address không tồn tại → 404 | ✓ VERIFIED | Backend @PutMapping("/{id}") khớp. Frontend services/users.ts dòng 92: httpPut. WR-02 RESOLVED. |
| 4 | DELETE /users/me/addresses/{id} xóa address (hard-delete, chỉ owner) | ✓ VERIFIED | AddressController @DeleteMapping("/{id}") + AddressService.deleteAddress() với ownership check + deleteById() |
| 5 | PUT /users/me/addresses/{id}/default set is_default=true cho 1 row, clear is_default trên các row khác | ✓ VERIFIED | Backend @PutMapping("/{id}/default") + clearDefaultByUserId() đúng. Frontend services/users.ts dòng 102: httpPut. WR-02 RESOLVED. |
| 6 | Partial unique index enforces: chỉ 1 row is_default=true per user_id | ✓ VERIFIED | V4__create_addresses.sql: CREATE UNIQUE INDEX idx_addresses_user_default ON user_svc.addresses (user_id) WHERE is_default = true |
| 7 | GET /orders?userId=X&status=DELIVERED&from=Y&to=Z&q=ORD trả đúng orders lọc theo filter | ✓ VERIFIED | OrderRepository.findByUserIdWithFilters() JPQL @Query với 5 @Param; OrderCrudService.listMyOrders() với timezone UTC+7 |
| 8 | Date range filter: from/to bao gồm full day UTC+7 (23:59:59) | ✓ VERIFIED | OrderCrudService: to → atTime(23, 59, 59).toInstant(ZoneOffset.of("+07:00")) |
| 9 | Keyword q tìm kiếm trên order.id ILIKE — không join order items | ✓ VERIFIED | JPQL: LOWER(o.id) LIKE LOWER(CONCAT('%', :q, '%')) — chỉ tìm trên id |
| 10 | Khi không có filter params, behavior giống cũ | ✓ VERIFIED | OrderController: userId=null → fallback listOrders() cũ |
| 11 | SavedAddress type + 5 address service functions export từ services/users.ts | ✓ VERIFIED | listAddresses, createAddress, updateAddress, deleteAddress, setDefaultAddress đều export. types/index.ts có SavedAddress + AddressBody |
| 12 | listMyOrders() extend với filter params (status, from, to, q) backward-compat | ✓ VERIFIED | ListOrdersParams có 4 fields optional mới; listMyOrders() set params khi != null/"ALL" |
| 13 | User vào /profile/addresses thấy danh sách addresses + nút Thêm địa chỉ mới | ✓ VERIFIED | profile/addresses/page.tsx: load listAddresses() on mount, Button "Thêm địa chỉ mới" disabled khi >= 10 |
| 14 | Create/edit address mở Modal với AddressForm; submit gọi createAddress/updateAddress; toast; refresh | ✓ VERIFIED | Custom overlay modal cho create/edit với AddressForm. handleCreate → createAddress(); handleEdit → updateAddress() dùng httpPut (WR-02 fixed). |
| 15 | Xóa address: Modal confirm → deleteAddress → toast; Đặt mặc định → setDefaultAddress → toast | ✓ VERIFIED | deleteAddress() dùng DELETE đúng. setDefaultAddress() hiện dùng httpPut → PUT /{id}/default (WR-02 fixed). |
| 16 | User vào /profile/orders thấy OrderFilterBar (sticky) + order list; filter → URL encode → fetch mới | ✓ VERIFIED | profile/orders/page.tsx: useSearchParams, handleFilterChange với useCallback, router.push encode URL, useEffect dep on searchParams |
| 17 | Checkout page: AddressPicker + snap-fill 6 fields; fetch fail → ẩn picker (silent) | ✓ VERIFIED | checkout/page.tsx: pickerVisible pattern, alive-flag cleanup, handleAddressSelect merge 6 fields. WR-02 fix loại bỏ ảnh hưởng gián tiếp lên setDefault từ picker. |

**Score:** 17/17 truths verified

### WR-02 Resolution Evidence

| Artifact | Dòng | Trước fix | Sau fix | Status |
|----------|------|-----------|---------|--------|
| `sources/frontend/src/services/users.ts` | 92 | `httpPatch<SavedAddress>(...)` | `httpPut<SavedAddress>(...)` | ✓ FIXED |
| `sources/frontend/src/services/users.ts` | 102 | `httpPatch<SavedAddress>(...)` | `httpPut<SavedAddress>(...)` | ✓ FIXED |

Backend `AddressController.java` không thay đổi — `@PutMapping("/{id}")` (dòng 89) và `@PutMapping("/{id}/default")` (dòng 126) đều đúng từ đầu. Frontend giờ khớp.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `sources/backend/user-service/src/main/resources/db/migration/V4__create_addresses.sql` | Addresses table + partial unique index | ✓ VERIFIED | CREATE TABLE + CREATE UNIQUE INDEX WHERE is_default = true |
| `sources/backend/user-service/.../domain/AddressEntity.java` | JPA entity | ✓ VERIFIED | Tồn tại |
| `sources/backend/user-service/.../service/AddressService.java` | 5 methods CRUD + set-default | ✓ VERIFIED | listAddresses, createAddress, updateAddress, deleteAddress, setDefault — tất cả có ownership check |
| `sources/backend/user-service/.../web/AddressController.java` | 5 REST endpoints | ✓ VERIFIED | GET, POST, PUT /{id}, DELETE /{id}, PUT /{id}/default |
| `sources/backend/order-service/.../service/OrderCrudService.java` | listMyOrders() với filter | ✓ VERIFIED | ListMyOrdersQuery record + listMyOrders() với ZoneOffset UTC+7 |
| `sources/frontend/src/types/index.ts` | SavedAddress + AddressBody interfaces | ✓ VERIFIED | SavedAddress (10 fields) + AddressBody (6+1 fields) |
| `sources/frontend/src/services/users.ts` | 5 address service functions, httpPut cho write ops | ✓ VERIFIED | updateAddress() + setDefaultAddress() dùng httpPut — WR-02 RESOLVED |
| `sources/frontend/src/app/profile/addresses/page.tsx` | Address book CRUD page | ✓ VERIFIED | Code đầy đủ; tất cả 5 operations đúng HTTP method |
| `sources/frontend/src/app/profile/orders/page.tsx` | Order history filter page | ✓ VERIFIED | useSearchParams, OrderFilterBar, handleFilterChange, URL state |
| `sources/frontend/src/app/checkout/page.tsx` | AddressPicker integration | ✓ VERIFIED | pickerVisible, alive-flag, handleAddressSelect snap-fill 6 fields |
| `sources/frontend/src/components/ui/AddressCard/AddressCard.tsx` | Address card UI | ✓ VERIFIED | Tồn tại |
| `sources/frontend/src/components/ui/AddressForm/AddressForm.tsx` | rhf+zod form | ✓ VERIFIED | zodResolver, addressSchema, VN phone regex |
| `sources/frontend/src/components/ui/AddressPicker/AddressPicker.tsx` | Dropdown snap-fill | ✓ VERIFIED | role="listbox", max-height 240px, isDefault pre-highlighted |
| `sources/frontend/src/components/ui/OrderFilterBar/OrderFilterBar.tsx` | Filter bar debounce | ✓ VERIFIED | setTimeout 400ms, clearTimeout cleanup, hasFilter "Xóa bộ lọc" |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| AddressController → AddressService | JWT userId | extractUserIdFromBearer() | ✓ WIRED | Lấy userId từ JWT claims.sub, không từ body |
| AddressService.setDefault() → addressRepository | clearDefault + save | clearDefaultByUserId() + entity.setDefault(true) | ✓ WIRED | Đúng thứ tự: clear trước, set sau |
| services/users.ts updateAddress() → PUT /api/users/me/addresses/{id} | httpPut | ✓ WIRED | WR-02 RESOLVED — PUT khớp @PutMapping("/{id}") |
| services/users.ts setDefaultAddress() → PUT /api/users/me/addresses/{id}/default | httpPut | ✓ WIRED | WR-02 RESOLVED — PUT khớp @PutMapping("/{id}/default") |
| OrderController.listOrders() → OrderCrudService.listMyOrders() | ListMyOrdersQuery record | ✓ WIRED | userId != null → listMyOrders(); userId = null → listOrders() cũ |
| profile/orders/page.tsx → listMyOrders() | useSearchParams + URL | ✓ WIRED | searchParams.get() → filter params → listMyOrders() |
| checkout/page.tsx AddressPicker → form fields | onSelect → setForm merge | ✓ WIRED | handleAddressSelect merge 6 fields đúng |
| profile/page.tsx tab orders | router.push('/profile/orders') | ✓ WIRED | onClick={() => router.push('/profile/orders')} |
| profile/page.tsx tab addresses | router.push('/profile/addresses') | ✓ WIRED | onClick={() => router.push('/profile/addresses')} |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|-------------------|--------|
| profile/addresses/page.tsx | addresses: SavedAddress[] | listAddresses() → GET /api/users/me/addresses | DB query (findByUserIdOrderByIsDefaultDescCreatedAtDesc) | ✓ FLOWING |
| profile/orders/page.tsx | orders: Order[] | listMyOrders() → GET /api/orders/orders?filters | DB query (findByUserIdWithFilters JPQL) | ✓ FLOWING |
| checkout/page.tsx | savedAddresses: SavedAddress[] | listAddresses() → GET /api/users/me/addresses | DB query | ✓ FLOWING |
| OrderFilterBar/OrderFilterBar.tsx | {status, from, to, q} | URL searchParams (controlled state) | N/A — UI component | ✓ FLOWING |

### Behavioral Spot-Checks

Step 7b: SKIPPED — yêu cầu running backend stack để test endpoints thực tế. Không thể chạy tĩnh.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| ACCT-05 | 11-01, 11-04, 11-05, 11-06 | Address book CRUD tại /profile/addresses — list/create/edit/delete/set-default | ✓ VERIFIED | Tất cả 5 operations đúng HTTP method sau WR-02 fix. Code đầy đủ. Cần human test để confirm end-to-end. |
| ACCT-06 | 11-01, 11-04, 11-05, 11-06 | AddressPicker checkout snap-fill 6 fields | ✓ VERIFIED | Core snap-fill đúng (pickerVisible, handleAddressSelect, alive-flag). WR-02 fix loại bỏ dependency broken. |
| ACCT-02 | 11-02, 11-04, 11-05, 11-06 | Order history filtering với filter bar + URL state | ✓ VERIFIED | Backend filter JPQL đúng. Frontend OrderFilterBar + useSearchParams + router.push URL encode đúng. |

### Anti-Patterns Found

Không có blockers sau WR-02 fix. Tất cả HTTP method calls trong services/users.ts khớp với backend mappings.

### Human Verification Required

### 1. Full ACCT-05 UI Flow — Edit + Set-Default (sau WR-02 fix)

**Test:** Đăng nhập, vào /profile/addresses. Tạo 1 địa chỉ → Sửa địa chỉ vừa tạo (modal edit, thay đổi tên/phone/street) → submit. Sau đó bấm "Đặt mặc định" cho địa chỉ đó.

**Expected:** Sửa: gọi PUT /api/users/me/addresses/{id} → 200, danh sách refresh với thông tin mới, toast xuất hiện. Đặt mặc định: gọi PUT /api/users/me/addresses/{id}/default → 200, địa chỉ được highlight là mặc định, toast xuất hiện. Không còn 405 Method Not Allowed.

**Why human:** WR-02 đã fix programmatically nhưng cần xác nhận PUT thực sự đến đúng endpoint qua browser devtools và backend logs.

### 2. ACCT-05 Create + Delete + Limit Flow

**Test:** Thêm địa chỉ mới → submit. Xóa địa chỉ (bấm Xóa → confirm). Thêm đủ 10 địa chỉ → verify nút "Thêm địa chỉ mới" bị disabled.

**Expected:** Create → toast "Đã thêm địa chỉ", list refresh; Delete → confirm modal → toast "Đã xóa địa chỉ", list refresh; >=10 addresses → button disabled.

**Why human:** UI modal flow và toast feedback cần browser + running backend.

### 3. ACCT-02 Order Filter Flow

**Test:** Vào /profile/orders, thay đổi status dropdown, nhập date range, nhập keyword. Verify URL thay đổi sau 400ms debounce. Bấm "Xóa bộ lọc".

**Expected:** Mỗi thay đổi encode vào URL (router.push); danh sách đơn hàng refresh theo filter; "Xóa bộ lọc" chỉ hiện khi có filter active và reset URL về /profile/orders.

**Why human:** Debounce timing và URL update cần browser interaction thực tế.

### 4. ACCT-06 Checkout AddressPicker

**Test:** Đăng nhập có ít nhất 1 địa chỉ đã lưu, vào /checkout. Verify AddressPicker hiện. Chọn 1 địa chỉ từ picker.

**Expected:** Picker dropdown hiển thị; chọn address → 6 fields form (fullName, phone, street, ward, district, city) tự điền; divider "— hoặc điền thủ công —" hiện; logged-out → picker ẩn.

**Why human:** Cần logged-in user với saved addresses và running API để test fetch + snap-fill.

---

## Gaps Summary

Không còn gaps sau WR-02 fix. Tất cả 17/17 must-haves đã pass automated verification.

**WR-02 Root Cause — RESOLVED:**
- `updateAddress()` trong services/users.ts: `httpPatch` → `httpPut` (dòng 92)
- `setDefaultAddress()` trong services/users.ts: `httpPatch` → `httpPut` (dòng 102)
- Backend AddressController không thay đổi — @PutMapping đúng từ đầu.

**Còn lại:** 4 human verification items để xác nhận end-to-end UI flow trong browser — đây là yêu cầu bình thường cho UI phase, không phải gap.

---

_Verified: 2026-04-27T15:00:00Z_
_Verifier: Claude (gsd-verifier)_
_Re-verification: Yes — sau fix WR-02 httpPatch → httpPut_
