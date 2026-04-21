# UC-AUTH: Xác thực người dùng

## Tóm tắt
Use case gom 4 flow: Register (đăng ký), Login (đăng nhập), Logout (đăng xuất), Reset password. Actor chính: Customer (đăng ký mới) và Guest (login). Tương tác với User Service duy nhất.

## Context Links
- Strategy: [../strategy/services/user-business.md](../strategy/services/user-business.md)
- Technical Spec: [../technical-spec/ts-auth.md](../technical-spec/ts-auth.md)
- Architecture: [../architecture/services/user-service.md](../architecture/services/user-service.md)
- Sequence: [../architecture/02-sequence-diagrams.md#1-register-uc-auth](../architecture/02-sequence-diagrams.md#1-register-uc-auth)

## Actors
- **Primary**: Guest (chưa có tài khoản) / Customer (đã có tài khoản)
- **Secondary**: Email Provider (SendGrid/SES) — gửi email welcome + reset link

## Preconditions
- User có email hợp lệ (format RFC 5322)
- User có kết nối internet

---

## Flow A — Register

### Preconditions
- Email chưa tồn tại trong hệ thống

### Main Flow
1. User vào trang `/register`
2. User nhập: email, password, confirm password, full name, phone (optional)
3. User tick "Tôi đồng ý điều khoản sử dụng"
4. User click "Đăng ký"
5. FE validate client-side (Zod):
   - Email format đúng
   - Password >= 8 ký tự, có chữ + số
   - Password == confirm password
   - Full name 2-100 ký tự
6. FE gửi request POST /api/v1/auth/register
7. BE validate + check email unique
8. BE hash password bcrypt cost 12
9. BE insert user (role=CUSTOMER, status=ACTIVE)
10. BE publish Kafka event `UserRegistered`
11. BE gen access + refresh token, insert refresh_token
12. BE return `201 { user, accessToken, refreshToken }`
13. FE lưu accessToken (memory), refreshToken (httpOnly cookie — Set-Cookie từ BE)
14. FE redirect về homepage, hiển thị toast "Đăng ký thành công"
15. (Async) Email provider gửi welcome email

### Alternative Flows
- **AF-A1: User hủy giữa chừng** — chưa submit, chỉ cần navigate đi không trigger gì
- **AF-A2: User đóng tab sau khi submit** — register đã success BE nhưng FE không redirect; lần sau login bằng email + password vừa tạo

### Exception Flows
- **EF-A1: Email đã tồn tại** → BE trả 400 `EMAIL_EXISTS` → FE hiển thị "Email đã được đăng ký. Đăng nhập?"
- **EF-A2: Password yếu** (không đủ ký tự/mix) → BE trả 400 `WEAK_PASSWORD` → FE highlight field
- **EF-A3: Email invalid format** → FE block trước khi submit
- **EF-A4: Phone invalid format** → FE warning (optional field, chỉ validate nếu nhập)
- **EF-A5: Rate limit** (register > 3/min/IP) → 429 `TOO_MANY_REQUESTS` → FE "Thử lại sau 1 phút"

### Acceptance Criteria
- [ ] AC-A1: Form register có đủ 4 fields bắt buộc (email, password, confirm password, full name) + 1 optional (phone)
- [ ] AC-A2: Password strength: >= 8 ký tự, có ít nhất 1 chữ + 1 số
- [ ] AC-A3: Email duplicate → error rõ ràng
- [ ] AC-A4: Success → auto-login + redirect home
- [ ] AC-A5: Welcome email gửi trong 30s sau register
- [ ] AC-A6: Password lưu hash (grep DB không thấy plain text)

### Data Inputs
- email (string, required)
- password (string, required, min 8)
- confirmPassword (string, required, match password)
- fullName (string, required, 2-100 ký tự)
- phone (string, optional, VN format)

### Data Outputs
- user: `{ id, email, fullName, phone, role, status, createdAt }`
- accessToken (JWT, 1h TTL)
- refreshToken (JWT, 7d TTL, httpOnly cookie)

### UI Notes
- Page `/register`: form single column, logo top, link "Đã có tài khoản? Đăng nhập" bottom
- Password input có toggle show/hide
- Strength indicator dưới password field
- Button disable khi form invalid

---

## Flow B — Login

### Preconditions
- User đã có tài khoản
- User không bị BLOCKED

### Main Flow
1. User vào `/login`
2. User nhập email + password
3. FE validate format email
4. FE gửi POST /api/v1/auth/login
5. BE check rate limit (max 10 login/min/IP)
6. BE check login-fail-count (max 5 fail/15min/email)
7. BE find user by email
8. BE verify bcrypt password
9. BE check user.status != BLOCKED
10. BE gen access + refresh token, insert refresh_token
11. BE return `200 { user, accessToken, refreshToken }`
12. FE lưu tokens, redirect về page user đang intend (query `?redirect=...`) hoặc homepage

### Alternative Flows
- **AF-B1: Remember me** (checkbox) — extend refresh token TTL 30 ngày (thay vì 7) (backlog)
- **AF-B2: Login với Google** (backlog phase 2)

### Exception Flows
- **EF-B1: Email không tồn tại** → BE trả 401 `INVALID_CREDENTIALS` (generic message, không leak user existence)
- **EF-B2: Password sai** → BE tăng login-fail counter, trả 401 `INVALID_CREDENTIALS`
- **EF-B3: Login fail >= 5 lần/15min** → BE trả 401 `ACCOUNT_LOCKED_TRY_LATER` → FE "Tài khoản tạm khóa, thử lại sau 30 phút hoặc reset password"
- **EF-B4: User BLOCKED** → BE trả 403 `USER_BLOCKED` → FE "Tài khoản bị khóa, liên hệ hotline"
- **EF-B5: Rate limit IP** (>10/min) → 429 → FE "Thử lại sau 1 phút"

### Acceptance Criteria
- [ ] AC-B1: Login success với credentials đúng
- [ ] AC-B2: Login fail message generic, không leak user existence
- [ ] AC-B3: Sau 5 fail → lock 30 phút, counter reset khi user unlock manual (reset password) hoặc hết timeout
- [ ] AC-B4: BLOCKED user không login được
- [ ] AC-B5: Refresh token lưu DB dưới dạng hash (không plain)

### Data Inputs
- email (string, required)
- password (string, required)

### Data Outputs
- Same as register

### UI Notes
- Link "Quên mật khẩu?" bên dưới password field
- Link "Chưa có tài khoản? Đăng ký" bottom
- Error message nằm trên form, màu đỏ

---

## Flow C — Logout

### Main Flow
1. User click "Đăng xuất" (từ dropdown profile)
2. FE gửi POST /api/v1/auth/logout (kèm refresh token)
3. BE revoke refresh_token row (set `revoked=true`)
4. BE trả 204
5. FE clear access token (memory), BE clear refresh cookie (Set-Cookie expired)
6. FE redirect `/login`

### Exception Flows
- **EF-C1: No refresh token** → FE vẫn clear local state, redirect login (không cần block)

### Acceptance Criteria
- [ ] AC-C1: Sau logout, call API authenticated trả 401
- [ ] AC-C2: Refresh token không dùng được sau logout

---

## Flow D — Reset Password

### Main Flow (Step D1: Request)
1. User vào `/reset-password` (từ link trang login)
2. User nhập email
3. FE gửi POST /api/v1/auth/password-reset/request { email }
4. BE **luôn trả 200** (không leak user existence)
5. Nếu email tồn tại + user ACTIVE:
   - BE gen random 32 bytes token, hash SHA-256, lưu `password_reset_token` TTL 15 phút
   - BE gửi email chứa link `https://app.com/reset-password/confirm?token={plainToken}`
6. FE hiển thị "Nếu email tồn tại, link reset đã được gửi. Check email."

### Main Flow (Step D2: Confirm)
1. User click link trong email
2. User landing `/reset-password/confirm?token=XXX`
3. User nhập password mới + confirm
4. FE validate password strength
5. FE gửi POST /api/v1/auth/password-reset/confirm { token, newPassword }
6. BE verify token (hash SHA-256 match DB, chưa expired, chưa used)
7. BE update user.password_hash
8. BE mark password_reset_token used
9. BE revoke tất cả refresh_token của user (force logout mọi device)
10. BE trả 200
11. FE redirect `/login` với toast "Password đã được đặt lại, đăng nhập lại"

### Exception Flows
- **EF-D1: Token invalid** → 400 `INVALID_RESET_TOKEN` → FE "Link không hợp lệ hoặc đã dùng"
- **EF-D2: Token expired** (> 15 phút) → 400 `EXPIRED_RESET_TOKEN` → FE "Link hết hạn, yêu cầu link mới"
- **EF-D3: Token đã used** → 400 `USED_RESET_TOKEN` → same handling
- **EF-D4: Password mới giống password cũ** → 400 `SAME_PASSWORD` (backlog, không check MVP)

### Acceptance Criteria
- [ ] AC-D1: Request endpoint luôn trả 200 (không leak email existence)
- [ ] AC-D2: Email gửi trong 30s
- [ ] AC-D3: Token TTL 15 phút
- [ ] AC-D4: Token chỉ dùng 1 lần
- [ ] AC-D5: Reset success → tất cả device khác bị logout (refresh token revoked)

### Data Inputs
- Step D1: `email`
- Step D2: `token` (từ URL), `newPassword`

### UI Notes
- Request form: 1 field email, button "Gửi link"
- Confirm page: 2 field password + button "Đặt lại mật khẩu"
- Nếu token invalid/expired → show error page với link "Yêu cầu link mới"

---

## Business Rules (references)
- BR-USER-01: Default role CUSTOMER
- BR-USER-02: Email unique
- BR-USER-03: Password strength
- BR-USER-04: Login fail lock
- BR-USER-05: JWT + refresh token TTL
- BR-USER-06: Password reset TTL 15 phút
- BR-USER-07: Blocked user flow

## Non-functional Requirements
- **Performance**: Login p95 < 300ms, Register p95 < 500ms (bcrypt cost 12 là bottleneck)
- **Security**: Bcrypt cost 12, JWT HS512, rate limiting, HTTPS only
- **Availability**: 99.5% (auth critical path)
- **Email delivery**: 95% emails delivered trong 60s

## UI Screens
- `/register`, `/login`, `/reset-password`, `/reset-password/confirm`
- Modal "Đăng nhập" optional (invoke từ header khi add-to-cart fail vì chưa login)
