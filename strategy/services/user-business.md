# User Service — Business Strategy

## Tóm tắt
User Service quản lý authentication, authorization, profile, và sổ địa chỉ. Scope: đăng ký, đăng nhập, JWT token, quản lý role (CUSTOMER/ADMIN), profile data, address book (max 5/user).

## Context Links
- Business rules (user): [../02-business-rules.md#br-user](../02-business-rules.md#br-user)
- Architecture: [../../architecture/services/user-service.md](../../architecture/services/user-service.md)
- BA: [../../ba/uc-auth.md](../../ba/uc-auth.md), [../../ba/uc-user-profile.md](../../ba/uc-user-profile.md), [../../ba/uc-admin-user.md](../../ba/uc-admin-user.md)

## Responsibility
- Đăng ký tài khoản customer
- Đăng nhập / Đăng xuất
- Reset password qua email
- Quản lý JWT token (access + refresh)
- CRUD profile (name, phone, avatar)
- CRUD address book (max 5/user, 1 default)
- Admin: view users, block/unblock

## Auth Model

### Role
- **CUSTOMER**: Role mặc định khi register. Truy cập: browse catalog, cart, checkout, order, review, profile.
- **ADMIN**: Set manual qua DB (không UI register). Truy cập: toàn bộ admin endpoints + tất cả quyền customer.
- **GUEST**: Không login. Truy cập: browse catalog + cart local (localStorage). Không checkout (phase 2 guest checkout).

### Token strategy
- **Access token** (JWT): 1 giờ TTL, stateless, chứa `userId`, `email`, `role`.
- **Refresh token**: 7 ngày TTL, lưu DB table `refresh_token` (để revoke được).
- **Rotation**: Khi dùng refresh → issue cặp token mới, revoke refresh cũ.
- **Logout**: xoá refresh token khỏi DB. Access token hết hạn tự nhiên (không blacklist ở MVP).

### Signup flow
1. Customer nhập: email, password, fullName, phone (optional)
2. Validate: email unique, password strength
3. Hash password (bcrypt cost 12)
4. Insert user với role=CUSTOMER, status=ACTIVE
5. Publish event `UserRegistered` (cho marketing/notification listener)
6. Auto-login (issue token) hoặc yêu cầu login (tuỳ UX)

### Login flow
1. Nhập email + password
2. Find user by email
3. Compare bcrypt
4. Check status != BLOCKED
5. Check fail-login-count (BR-USER-04)
6. Issue access + refresh token
7. Return tokens + user info (không kèm password)

### Password reset
1. Nhập email
2. Always return success (không leak user existence)
3. Nếu email tồn tại → gen reset token (random 32 bytes), hash lưu DB, TTL 15 phút
4. Gửi email chứa link `/reset-password?token=XXX`
5. User click link → nhập password mới
6. Verify token → update password → invalidate all refresh tokens của user

## Profile Rules
- **Email**: không đổi được sau register (unique identity). Muốn đổi phải tạo tài khoản mới.
- **Name**: 2-100 ký tự, Unicode.
- **Phone**: VN format `0xxxxxxxxx` (10 số bắt đầu 0) hoặc `+84xxxxxxxxx`. Optional.
- **Avatar**: URL lưu ở S3/CloudFront. Max 2MB, jpg/png/webp.
- **DOB, gender**: optional, phase 2.

## Address Book Rules
- Max 5 địa chỉ/user (BR-USER-09).
- Mỗi address: `recipientName`, `phone`, `addressLine1`, `ward`, `district`, `city`, `isDefault`.
- Exactly 1 default (enforce qua logic, không DB constraint).
- Xoá default → default mới auto = address đầu tiên còn lại.
- Address dùng trong order: snapshot (copy vào order_address), không reference foreign key.

## Admin User Mgmt
- Admin xem list user (paginated) với filter: status, role, createdAt.
- Admin xem detail user (không thấy password hash).
- Admin block/unblock: set `status` = BLOCKED/ACTIVE. Kafka event `UserBlocked`.
- Admin KHÔNG xóa user (giữ reference cho order history). Chỉ block.

## Security Requirements
- Password hash: bcrypt cost 12 (không MD5/SHA1).
- Password strength: >= 8 ký tự, có chữ + số. Validate cả FE + BE.
- Rate limit login: 5 fail/15 phút → lock 30 phút (BR-USER-04). Redis counter key `login-fail:{email}`.
- JWT secret: đọc từ env, rotate mỗi 90 ngày (có migration plan).
- HTTPS bắt buộc ở production.
- HttpOnly + Secure cookie cho refresh token (preferred); access token trong memory/localStorage.

## Events Publish
- `UserRegistered` — payload: `userId`, `email`, `fullName`, `registeredAt`
- `UserBlocked` — payload: `userId`, `reason`, `blockedBy`, `blockedAt`
- `UserUnblocked` — payload: `userId`, `unblockedBy`, `unblockedAt`

## Events Consume
- KHÔNG consume event từ service khác ở MVP.

## KPI cho service
- Registration rate: signups/day
- Login fail rate: < 2% của total login attempts
- Password reset rate: < 1% WAU
- Blocked users: < 0.1% total
