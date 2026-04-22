# UC-AUTH: Xác thực người dùng (v2)

## Tóm tắt
Use case gồm Register, Login, Logout, Reset Password. Domain owner là user-service; notification-service gửi email async theo event.

## Context Links
- Strategy: [../strategy/services/user-business.md](../strategy/services/user-business.md)
- Architecture: [../architecture/services/user-service.md](../architecture/services/user-service.md)
- Technical Spec: [../technical-spec/ts-auth.md](../technical-spec/ts-auth.md)

## Actors
- Primary: Guest, Customer
- Secondary: Notification Service (email), API Gateway

## Preconditions
- FE gọi qua API Gateway.
- User có email hợp lệ.

## Main Flows
### A. Register
1. User nhập email, password, fullName.
2. FE validate form, gọi POST auth register.
3. user-service tạo account CUSTOMER, tạo token.
4. user-service phát UserRegistered.
5. notification-service gửi welcome email async.

### B. Login
1. User nhập email/password.
2. Gateway áp rate-limit login endpoint.
3. user-service kiểm tra lock policy, validate password.
4. Trả access + refresh token nếu thành công.

### C. Logout
1. FE gọi logout.
2. user-service revoke refresh token.
3. FE xóa local auth state.

### D. Reset Password
1. Request reset: luôn trả success để tránh lộ email tồn tại.
2. user-service phát PasswordResetRequested.
3. notification-service gửi email link reset.
4. Confirm reset: update password và revoke refresh tokens cũ.

## Alternative/Exception Flows
- Email đã tồn tại khi register -> EMAIL_EXISTS.
- Sai mật khẩu hoặc email không tồn tại -> INVALID_CREDENTIALS (generic).
- Quá số lần sai -> ACCOUNT_LOCKED_TRY_LATER.
- User blocked -> USER_BLOCKED.
- Token reset hết hạn -> EXPIRED_RESET_TOKEN.

## Service Touchpoints
| Step | Service |
|---|---|
| Auth business logic | user-service |
| Route, CORS, rate-limit | api-gateway |
| Email welcome/reset | notification-service |

## Business Rules
- BR-USER-01, BR-USER-03, BR-USER-04.
- BR-NOTI-01, BR-NOTI-02.
- BR-GW-01, BR-GW-03.

## Acceptance Criteria
- [ ] Register thành công tạo account CUSTOMER và phát event.
- [ ] Login generic error, không leak user existence.
- [ ] Lock policy hoạt động đúng khi login fail nhiều lần.
- [ ] Reset password revoke toàn bộ refresh token cũ.
- [ ] Email gửi async, không chặn response auth API.

## NFR
- Login p95 < 300ms.
- Register p95 < 500ms.
- Availability auth path >= 99.5%.
