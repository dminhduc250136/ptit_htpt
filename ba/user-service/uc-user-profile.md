# UC-USER-PROFILE: Hồ sơ & Sổ địa chỉ

## Tóm tắt
Customer quản lý thông tin cá nhân (name, phone, avatar) và sổ địa chỉ (max 5, 1 default). Email không đổi được. Address dùng cho checkout — snapshot vào order lúc đặt hàng.

## Context Links
- Strategy: [../strategy/services/user-business.md](../strategy/services/user-business.md)
- Technical Spec: [../technical-spec/ts-user-profile.md](../technical-spec/ts-user-profile.md)
- Architecture: [../architecture/services/user-service.md](../architecture/services/user-service.md)

## Actors
- **Primary**: Customer (đã login)

## Preconditions
- User đã đăng nhập, role=CUSTOMER, status=ACTIVE

---

## Flow A — Xem & Cập nhật hồ sơ

### Main Flow
1. User vào `/account/profile`
2. FE gọi GET /api/v1/users/me
3. BE trả user info (không kèm password)
4. FE render form:
   - Email (disabled, không đổi)
   - Full name (editable)
   - Phone (editable)
   - Avatar upload (editable)
5. User sửa field, click "Lưu"
6. FE validate client-side
7. FE gửi PATCH /api/v1/users/me với field đổi
8. BE validate + update
9. BE trả 200 { user }
10. FE hiển thị toast "Cập nhật thành công"

### Alternative Flows
- **AF-A1: Upload avatar**:
  - User chọn file image
  - FE request presigned URL `POST /api/v1/users/me/avatar/presign`
  - FE upload S3 trực tiếp
  - FE gửi PATCH /me với `avatarUrl = {S3 url}`

### Exception Flows
- **EF-A1: Phone invalid format** → 400 `INVALID_PHONE` → FE highlight
- **EF-A2: Full name < 2 ký tự** → 400 `INVALID_NAME`
- **EF-A3: Avatar > 2MB** → FE block trước upload
- **EF-A4: Avatar format không hỗ trợ** → FE block (chỉ jpg/png/webp)

### Acceptance Criteria
- [ ] AC-A1: Load profile < 500ms
- [ ] AC-A2: Email field disabled, không submit được
- [ ] AC-A3: Avatar upload success hiển thị ngay (không cần refresh)
- [ ] AC-A4: Phone validate VN format (0xxxxxxxxx hoặc +84xxxxxxxxx)

### Data Inputs
- fullName (string, 2-100)
- phone (string, VN format, optional)
- avatarUrl (string, URL)

### Data Outputs
- `{ id, email, fullName, phone, avatarUrl, role, status, createdAt, updatedAt }`

---

## Flow B — Đổi mật khẩu

### Main Flow
1. User vào `/account/profile/password`
2. User nhập: current password, new password, confirm new password
3. FE validate: new != current, new >= 8 ký tự có chữ + số
4. FE gửi POST /api/v1/users/me/password { currentPassword, newPassword }
5. BE verify current password (bcrypt compare)
6. BE hash new password, update
7. BE revoke tất cả refresh_token của user (force re-login)
8. BE trả 200
9. FE hiển thị "Đổi thành công, vui lòng đăng nhập lại"
10. FE logout client-side, redirect `/login`

### Exception Flows
- **EF-B1: Current password sai** → 400 `WRONG_CURRENT_PASSWORD`
- **EF-B2: New password yếu** → 400 `WEAK_PASSWORD`
- **EF-B3: New giống current** → 400 `SAME_PASSWORD`

### Acceptance Criteria
- [ ] AC-B1: Sau đổi thành công, tất cả session khác bị logout
- [ ] AC-B2: New password phải khác current

---

## Flow C — Quản lý Sổ Địa Chỉ

### Main Flow (C1: List)
1. User vào `/account/addresses`
2. FE gọi GET /api/v1/users/me/addresses
3. BE trả list (sort: default first, rồi createdAt desc)
4. FE render cards với actions: Edit, Delete, Set Default

### Main Flow (C2: Add)
1. User click "Thêm địa chỉ"
2. Modal form mở với fields:
   - recipientName (người nhận)
   - phone (SĐT người nhận)
   - addressLine1 (số nhà, đường)
   - ward (phường/xã)
   - district (quận/huyện)
   - city (tỉnh/thành)
   - setAsDefault (checkbox)
3. User fill + submit
4. FE gửi POST /api/v1/users/me/addresses
5. BE validate + check user chưa có > 5 address
6. BE nếu `isDefault=true` → update tất cả address khác `isDefault=false`
7. BE insert address, trả 201
8. FE close modal, refresh list

### Main Flow (C3: Edit)
1. Click "Sửa" trên address card → modal prefilled
2. User edit, submit
3. FE gửi PATCH /api/v1/users/me/addresses/{id}
4. BE validate ownership (address.userId == currentUser)
5. BE update, trả 200

### Main Flow (C4: Delete)
1. Click "Xóa" → confirm dialog
2. FE gửi DELETE /api/v1/users/me/addresses/{id}
3. BE check ownership
4. BE delete
5. Nếu address deleted là default → auto set default = address đầu tiên còn lại (nếu có)
6. BE trả 204

### Main Flow (C5: Set Default)
1. Click "Đặt làm mặc định" → FE gửi PUT /api/v1/users/me/addresses/{id}/default
2. BE update: target = true, rest = false
3. BE trả 200

### Exception Flows
- **EF-C1: Đã có 5 address** → 400 `ADDRESS_LIMIT_EXCEEDED`
- **EF-C2: Phone invalid** → 400
- **EF-C3: Required field thiếu** → 400
- **EF-C4: User không sở hữu address** → 403 `FORBIDDEN` (hoặc 404 tránh leak)

### Acceptance Criteria
- [ ] AC-C1: Max 5 address enforce
- [ ] AC-C2: Exactly 1 default (không 0, không 2)
- [ ] AC-C3: Xóa default → tự động promote address khác
- [ ] AC-C4: Dropdown address ở checkout show list sort default first

### Data Inputs
- recipientName (string, 2-100, required)
- phone (string, VN format, required)
- addressLine1 (string, 5-255, required)
- ward (string, optional)
- district (string, required)
- city (string, required)
- isDefault (boolean)

### UI Notes
- Address card: recipient name + phone + full address (1 dòng) + badge "Mặc định" nếu default + actions
- Add modal: dropdown city/district từ danh sách tỉnh/thành VN (static data hoặc API provinces)

---

## Business Rules (references)
- BR-USER-08: Email không đổi
- BR-USER-09: Max 5 address

## Non-functional Requirements
- **Performance**: GET /me < 200ms
- **Security**: user chỉ xem/sửa data của mình (check via JWT userId)
- **Data privacy**: không expose email user khác qua admin/public API

## UI Screens
- `/account/profile` — Hồ sơ
- `/account/profile/password` — Đổi password
- `/account/addresses` — Sổ địa chỉ
