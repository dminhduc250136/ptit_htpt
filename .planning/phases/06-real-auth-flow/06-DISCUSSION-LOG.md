# Phase 6: Real Auth Flow - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-26
**Phase:** 06-real-auth-flow
**Areas discussed:** Register contract, Logout approach, Auto-login sau register, Middleware + route protection

---

## Register Contract

| Option | Description | Selected |
|--------|-------------|----------|
| username + email + password | Backend nhận 3 fields, FE update form bỏ fullName/phone, thêm username | ✓ |
| email + password (email là username) | Email làm luôn username/login key | |
| fullName + email + phone + password | UserEntity thêm fullName/phone columns, nhiều effort hơn | |

**User's choice:** `username + email + password`
**Notes:** Giữ UserEntity Phase 5 không đổi.

---

| Option | Description | Selected |
|--------|-------------|----------|
| email (Recommended) | Login bằng email + password | ✓ |
| username | Login bằng username + password | |
| cả hai | Backend accept email hoặc username vào cùng 1 field 'identifier' | |

**User's choice:** `email`
**Notes:** FE form hiện đã dùng email field → không cần đổi label.

---

## Logout Approach

| Option | Description | Selected |
|--------|-------------|----------|
| Client-side discard only | Backend trả 200 OK ngay, không blacklist. FE xóa token + zero cookie | ✓ |
| Token blacklist (in-memory) | Backend lưu Set<String> blacklisted JTI trong memory | |
| Token blacklist (DB) | Lưu blacklist vào Postgres, persist qua restart | |

**User's choice:** `Client-side discard only`
**Notes:** Đơn giản nhất cho MVP — token vẫn valid đến khi hết hạn, chấp nhận được.

---

| Option | Description | Selected |
|--------|-------------|----------|
| 24 giờ | Cân bằng UX và security | ✓ |
| 7 ngày | Token tồn tại lâu hơn cần thiết | |
| 1 giờ | Không có refresh token → UX khó chịu | |

**User's choice:** `24 giờ`
**Notes:** JWT expiration = 24h.

---

## Auto-login sau register

| Option | Description | Selected |
|--------|-------------|----------|
| Trả token + auto-login | Backend trả {accessToken, user} giống login, FE lưu token + redirect / | ✓ |
| Chỉ trả 201 + redirect /login | Backend trả 201 Created + UserDto, thêm 1 bước cho user | |

**User's choice:** `Trả token + auto-login`
**Notes:** services/auth.ts đã handle case này.

---

| Option | Description | Selected |
|--------|-------------|----------|
| Redirect về / (trang chủ) | Phổ biến nhất, user bắt đầu browse | ✓ |
| Redirect về /account | User thấy profile ngay sau register | |

**User's choice:** `Redirect về /`

---

## Middleware + Route Protection

| Option | Description | Selected |
|--------|-------------|----------|
| Thêm /account/* vào matcher | matcher: /checkout/*, /profile/*, /admin/*, /account/* | ✓ |
| Giữ nguyên matcher hiện tại | /account/* chưa có trang thực cần protect ngay | |

**User's choice:** `Thêm /account/*`

---

| Option | Description | Selected |
|--------|-------------|----------|
| Check role ADMIN trong middleware | Middleware đọc cookie user_role để verify ADMIN cho /admin/* | ✓ |
| Chỉ check auth_present, defer role check | Role check defer sang Phase 7 | |

**User's choice:** `Check role ADMIN trong middleware`

---

| Option | Description | Selected |
|--------|-------------|----------|
| Redirect về /403 | Explicit forbidden page, cần tạo /403 route | ✓ |
| Redirect về / (trang chủ) | Đơn giản, không cần trang 403 riêng | |
| Redirect về /login | Nhầm lẫn UX — user đã logged in | |

**User's choice:** `Redirect về /403`
**Notes:** Cần tạo /403 page đơn giản.

---

| Option | Description | Selected |
|--------|-------------|----------|
| Cookie 'user_role' riêng | FE set cookie user_role=ADMIN khi login, middleware đọc cookie | ✓ |
| Parse JWT trong middleware | Decode JWT (không verify signature) — không an toàn | |
| localStorage + sync vào cookie | Phức tạp hơn không cần thiết | |

**User's choice:** `Cookie 'user_role' riêng`
**Notes:** Edge runtime không thể import Node crypto modules → không thể verify JWT signature → cookie approach là đúng.

---

## Claude's Discretion

- JJWT version — researcher/planner chọn compatible với Spring Boot 3.3.2
- BCrypt bean config — standard `@Bean PasswordEncoder`
- Error message text — theo ApiErrorResponse envelope pattern hiện có
- `/403` page design — minimal

## Deferred Ideas

- Refresh token flow — defer v1.2
- Email verification, password reset — defer
- Rate limiting / brute-force protection — invisible hardening, defer
- `fullName` và `phone` fields — defer sang Edit Profile phase nếu cần
