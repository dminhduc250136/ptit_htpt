# User Service (v2)

## Tóm tắt
User Service quản lý identity domain: authentication, profile, address và admin user control.

## Runtime
- Service name: user-service
- Port: 8080 (container), exposed 8081 ở compose

## Ownership
- Register/login/logout/refresh/reset password.
- CRUD profile/address.
- Block/unblock user.

## Key APIs
- POST /auth/register
- POST /auth/login
- POST /auth/logout
- POST /auth/password-reset/request
- POST /auth/password-reset/confirm
- GET/PATCH /users/me
- GET/POST/PATCH/DELETE /users/me/addresses
- GET /admin/users, POST /admin/users/{id}/block

## Publish Events
- UserRegistered
- UserBlocked
- UserUnblocked
- PasswordResetRequested

## Consume Events
- None (MVP).

## Storage
- user, address, refresh_token, password_reset_token.

## Notes for AI
- Mọi auth request đi qua gateway.
- Email gửi qua notification-service bằng event, không sync trực tiếp.
