# Phase 10: User-Svc Schema Cluster + Profile Editing - Context

**Gathered:** 2026-04-27
**Status:** Ready for planning

<domain>
## Phase Boundary

Lay foundation cho user account features — ship profile editing (fullName/phone) với rhf+zod form pattern. **Scope trimmed:** avatar upload defer (user xác nhận dự án demo tập trung distributed system/deploy, không cần full account features). Phase 10 chỉ deliver:
1. GET + PATCH /api/users/me backend endpoint
2. Profile Info form tại /profile/settings (extend trang đã có, chỉ thêm section mới)
3. Navbar reflect fullName mới sau save

**Avatar (ACCT-04) defer:** V3 migration, multipart upload, Thumbnailator, BYTEA serve — moved to backlog. Initials placeholder đủ cho demo scope.

</domain>

<decisions>
## Implementation Decisions

### Settings page layout (ACCT-03)

- **D-01:** Tổ chức /profile/settings thành **sections dọc** (single page, scroll) — KHÔNG tab navigation. 3 sections độc lập, mỗi section có Save/Action button riêng:
  - Section 1: **Profile Info** — fullName input + phone input + email read-only + [Save] button
  - Section 2: **Avatar** — initials placeholder, ghi chú "Coming soon" (no upload form Phase 10)
  - Section 3: **Security** — password form (đã có từ Phase 9)
- **D-02:** Unsaved changes khi navigate: **silent discard** — không warning, navigate ngay. Form 2 fields, không phức tạp đủ để cần guard.
- **D-03:** Phản hồi sau save thành công: **toast "Đã cập nhật"** popup 3s rồi tự ẩn. Consistent với admin CRUD pattern hiện tại (xem `src/app/admin/*`).

### Backend PATCH /users/me (ACCT-03)

- **D-04:** Extend `UserMeController` (đã có từ Phase 9) với 2 endpoints mới:
  - `GET /users/me` — trả UserDto + thêm `hasAvatar: boolean` field (Phase 10 luôn false vì chưa có V3 migration)
  - `PATCH /users/me` — nhận `{fullName?, phone?}`, validate, update, trả UserDto updated
- **D-05:** Cả 2 endpoints yêu cầu **Bearer token bắt buộc** — userId lấy từ JWT claims.sub (pattern D-05 Phase 9). 401 nếu thiếu/invalid token.
- **D-06:** UserDto extend thêm `hasAvatar: boolean` — giữ consistent với existing type, KHÔNG tạo MeResponse riêng. Phase 10 hasAvatar luôn `false` (V3 migration defer). Phase 12+ sẽ wire real value khi cần.

### Navbar user state

- **D-07:** **localStorage + router.refresh()** — đơn giản nhất, không cần AuthContext:
  - Login hiện tại lưu user info vào localStorage (key đã có)
  - Sau PATCH /me thành công: cập nhật fullName trong localStorage entry đó → router.refresh() (soft reload, giữ session)
  - Navbar đọc từ localStorage → thấy tên mới
  - Không cần React Context, không cần event system

### Avatar (defer)

- **D-08:** Avatar **KHÔNG ship trong Phase 10**. Initials circle (chữ đầu fullName, CSS) làm placeholder cho toàn bộ v1.2. GET /api/users/{id}/avatar endpoint, V3 Flyway migration, Thumbnailator — defer backlog.
- **D-09:** Flyway V3 reservation vẫn giữ trong ROADMAP.md table để tránh collision nếu tương lai cần — nhưng KHÔNG execute Phase 10.

### Claude's Discretion

- Validation rules cho phone (regex format) — Claude chọn theo VN phone pattern hoặc loose `^\+?[\d\s-]{7,20}$`
- Màu initials circle — Claude chọn CSS color consistent với design hiện tại
- Toast implementation — dùng inline state (như Phase 9 password form) hoặc install react-hot-toast; Claude chọn option nào ít deps hơn
- PATCH /me request body: field-level partial update (chỉ gửi field thay đổi) hay always-full — Claude chọn theo pattern AdminUserPatchBody hiện tại (nullable optional fields)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents PHẢI đọc các file dưới trước khi plan/implement.**

### Project-level
- `.planning/PROJECT.md` — visible-first priority, demo dự án distributed system
- `.planning/REQUIREMENTS.md` §ACCT-03, §ACCT-04 — full requirement spec (ACCT-04 defer Phase 10)
- `.planning/ROADMAP.md` §Phase 10 — Goal + 4 Success Criteria (SC-04 avatar defer)
- `.planning/STATE.md` — locked decisions carry-over

### Research artifacts (v1.2)
- `.planning/research/STACK.md`
- `.planning/research/FEATURES.md`
- `.planning/research/PITFALLS.md` — §Avatar upload (tham khảo để hiểu lý do defer)

### Codebase intel
- `.planning/codebase/CONVENTIONS.md` — error envelope, ApiResponse pattern
- `.planning/codebase/INTEGRATIONS.md` — gateway routes, service-to-service

### Existing code (đụng tới Phase 10)
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/UserMeController.java` — extend với GET + PATCH /me
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/domain/UserEntity.java` — fullName/phone setters đã có
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/domain/UserDto.java` — extend hasAvatar field
- `sources/backend/user-service/src/main/resources/db/migration/` — V1, V2 tồn tại; V3 KHÔNG tạo Phase 10
- `sources/backend/api-gateway/src/main/resources/application.yml` — user-service-me routes đã đứng trước user-service-base (verified Phase 9)
- `sources/frontend/src/app/profile/settings/page.tsx` — extend với Profile Info section (thêm vào trên password section)
- `sources/frontend/src/services/users.ts` — thêm getMe() + patchMe() functions
- `sources/frontend/src/services/http.ts` — httpGet/httpPatch pattern

### Prior phase context
- Phase 9 D-05: JWT role guard pattern (Bearer → claims.sub) — áp dụng cho GET/PATCH /me
- Phase 9 D-10: Toast/inline success message pattern cho /profile/settings

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `UserMeController` — map `/users/me`, đã có extractUserIdFromBearer() helper → tái dụng cho GET/PATCH
- `AdminUserPatchBody` (users.ts) — pattern nullable optional fields cho PATCH → tham khảo cho PatchMeBody
- `changeMyPassword()` service function — pattern httpPost → tham khảo cho patchMe()
- `JwtRoleGuard` — manual JWT parse pattern → GET/PATCH /me cần same approach

### Established Patterns
- `ApiResponse<T>` envelope — tất cả backend response đều wrap qua đây
- Error codes: string codes (`AUTH_INVALID_PASSWORD`) — Phase 10 thêm validation errors PATCH /me
- rhf + zod: chưa có trong frontend (Phase 10 là lần đầu). Admin forms dùng uncontrolled inputs cũ — Phase 10 SET PATTERN mới cho v1.2
- localStorage token: `getAccessToken()` từ `services/token.ts` → http.ts auto-attach Bearer

### Integration Points
- Gateway `user-service-me` routes đã forward `/api/users/me` và `/api/users/me/**` → user-svc `/users/me/**`
- Frontend profile settings page tại `src/app/profile/settings/page.tsx` — extend in-place
- Middleware đã bảo vệ `/profile/*` routes (Phase 9 AUTH-06) → authenticated access đảm bảo

</code_context>

<specifics>
## Specific Ideas

- User muốn approach "đủ dùng" cho demo — tránh over-engineer. Ưu tiên ship nhanh, scope minimal.
- Dự án mục đích chính: demo distributed system + deployment pattern, không phải hoàn thiện e-commerce features.
- Avatar defer là decision chủ động (không phải technical blocker) — simplify để close milestone nhanh hơn.

</specifics>

<deferred>
## Deferred Ideas

- **Avatar upload (ACCT-04)** — V3 Flyway migration (avatar BYTEA, content_type, updated_at), multipart upload endpoint, Thumbnailator 256×256 resize, GET /api/users/{id}/avatar binary serve, img cache invalidation qua ?v=timestamp. Toàn bộ defer backlog — user xác nhận không cần cho demo scope.
- **AuthContext / React Context** — full context provider cho user state. Defer; localStorage+refresh đủ cho Phase 10.
- **Unsaved changes guard** — beforeunload/router guard khi user navigate with dirty form. Defer; silent discard đủ cho 2-field form.

</deferred>

---

*Phase: 10-user-svc-schema-profile-editing*
*Context gathered: 2026-04-27*
