# UC-ADMIN-USER: Admin — User Management

## Tóm tắt
Admin xem list users với filter status/role/date, xem detail user (không password), block/unblock với reason. Admin KHÔNG xóa user (data retention). Không tạo admin qua UI — set manual trong DB.

## Context Links
- Strategy: [../strategy/services/user-business.md#admin-user-mgmt](../strategy/services/user-business.md#admin-user-mgmt)
- Technical Spec: [../technical-spec/ts-admin-user.md](../technical-spec/ts-admin-user.md)
- Architecture: [../architecture/services/user-service.md](../architecture/services/user-service.md)

## Actors
- **Primary**: Admin

## Preconditions
- User logged in với role=ADMIN

---

## Flow A — User List

### Main Flow
1. Admin vào `/admin/users`
2. FE gọi GET /api/v1/admin/users?page=0&size=20&status=&role=&q=&dateFrom=&dateTo=
3. BE trả users (exclude password_hash)
4. FE render:
   - Filter: status (ACTIVE/BLOCKED), role (CUSTOMER/ADMIN), search email/phone/name, date range register
   - Table: Email, Full Name, Phone, Role badge, Status badge, Registered at, Actions
   - Pagination

### Acceptance Criteria
- [ ] AC-A1: Search case-insensitive, match partial (email contains, name contains)
- [ ] AC-A2: Filter combine (AND)
- [ ] AC-A3: Sort default: registeredAt desc
- [ ] AC-A4: Count badges in tabs ("Active 1234" / "Blocked 5")

---

## Flow B — User Detail

### Main Flow
1. Admin click row → `/admin/users/{id}`
2. FE gọi GET /api/v1/admin/users/{id}
3. BE trả user info (không password_hash) + aggregated stats:
   - Số order total, spending total (call Order Service hoặc cached)
   - Last login (track qua login event — backlog)
4. FE render:
   - **Basic info**: email, fullName, phone, avatar, role, status
   - **Audit**: createdAt, updatedAt, blockedAt (nếu), blockedReason, blockedBy
   - **Addresses**: list addresses
   - **Orders**: link đến admin orders filter by userId (cross-link)
   - **Stats**: total orders, total spent, last order date
   - **Actions** (dynamic):
     - ACTIVE → "Block"
     - BLOCKED → "Unblock"

### Acceptance Criteria
- [ ] AC-B1: Password hash NEVER expose trong response
- [ ] AC-B2: Stats fetch từ Order Service (cross-service internal API)
- [ ] AC-B3: Link orders cross-module

---

## Flow C — Block User

### Main Flow
1. Admin click "Block" → confirm modal với reason textarea (required)
2. Admin fill reason → submit
3. FE gửi POST /api/v1/admin/users/{id}/block { reason }
4. BE:
   - Load user
   - Check status != BLOCKED (idempotent)
   - Check role != ADMIN (không block admin qua UI, tránh nhầm)
   - Update: status=BLOCKED, blockedAt=now, blockedReason, blockedBy=adminId
   - Revoke tất cả refresh_token (force logout mọi device)
   - Publish `UserBlocked` event
5. BE trả 200
6. FE refresh, toast "User blocked"

### Side effects
- User không login được (EF-B4 của UC-AUTH)
- User hiện logged-in: call API sẽ fail 401 lần tiếp theo (access token chưa expire), nhưng không refresh được → force logout within 1h
- Orders pending của user: backlog — phase 2 tự cancel

### Exception Flows
- **EF-C1: User là ADMIN** → 400 `CANNOT_BLOCK_ADMIN`
- **EF-C2: Reason empty** → 400 `REASON_REQUIRED`
- **EF-C3: Already blocked** → 409 `ALREADY_BLOCKED` hoặc 200 (idempotent)

### Acceptance Criteria
- [ ] AC-C1: Reason required, lưu audit
- [ ] AC-C2: Refresh tokens revoked ngay
- [ ] AC-C3: User cố login → thấy message "Tài khoản bị khóa. Liên hệ hotline."
- [ ] AC-C4: Kafka event `UserBlocked` publish

---

## Flow D — Unblock User

### Main Flow
1. Admin vào user detail BLOCKED → click "Unblock"
2. Optional reason textarea
3. Submit POST /api/v1/admin/users/{id}/unblock { reason }
4. BE:
   - Update status=ACTIVE, blockedAt=null, blockedReason=null, blockedBy=null
   - Publish `UserUnblocked`
5. BE trả 200

### Exception Flows
- **EF-D1: User chưa blocked** → 409 `NOT_BLOCKED`

### Acceptance Criteria
- [ ] AC-D1: User login lại được sau unblock
- [ ] AC-D2: Password cũ vẫn work (không force reset)

---

## Flow E — View User Orders (cross-link)

### Main Flow
1. Admin ở user detail click "Xem đơn hàng"
2. FE navigate `/admin/orders?userId={userId}`
3. FE gọi GET /api/v1/admin/orders?userId=...
4. BE filter orders by userId, trả paginated
5. FE render orders table pre-filtered

### Acceptance Criteria
- [ ] AC-E1: Cross-link user → orders work
- [ ] AC-E2: Filter userId hidden trong URL (hoặc show tag dismissible)

---

## Flow F — Admin Cannot Self-Block / Cannot Delete

### Rules
- Admin không block chính mình (check `id != currentAdminId`)
- Admin không có endpoint DELETE user
- Không UI endpoint tạo admin — phải vào DB update role manual (security)

### Acceptance Criteria
- [ ] AC-F1: Self-block blocked với message
- [ ] AC-F2: No DELETE endpoint tồn tại

---

## Business Rules (references)
- BR-USER-01: Roles không đổi qua UI
- BR-USER-07: Blocked flow
- No hard delete user

## Non-functional Requirements
- **Performance**: User list <500ms
- **Security**: ADMIN only, audit log mọi block/unblock
- **Privacy**: Password hash không expose, personal data (phone, email) chỉ admin xem
- **GDPR-like**: backlog — export user data endpoint + erase request (manual process MVP)

## UI Screens
- `/admin/users` — list
- `/admin/users/{id}` — detail với actions
- Modal: Block user (reason required)
- Modal: Unblock user (reason optional)
