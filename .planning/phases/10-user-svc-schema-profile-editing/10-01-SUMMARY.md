---
phase: 10-user-svc-schema-profile-editing
plan: "01"
subsystem: api
tags: [spring-boot, user-service, profile, jwt, jakarta-validation]

# Dependency graph
requires:
  - phase: 09-auth-middleware-password
    provides: UserMeController foundation với extractUserIdFromBearer() + JWT parsing

provides:
  - "GET /users/me endpoint trả UserDto (hasAvatar=false) sau xác thực Bearer token"
  - "PATCH /users/me endpoint nhận {fullName?, phone?}, validate, update DB, trả UserDto"
  - "UserDto record với 9 components (thêm hasAvatar boolean)"
  - "UserProfileService với getMe(userId) + updateMe(userId, req)"
  - "UpdateMeRequest record với jakarta validation (@Size 1-120 fullName, @Pattern phone)"

affects:
  - 10-03-frontend-profile-settings
  - future-phases-using-UserDto

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Partial-update pattern: nullable optional fields + if-not-null conditional setter (analog AdminUserPatchRequest)"
    - "Controller inject profile service riêng (UserProfileService) — tách concern khỏi UserCrudService + UserPasswordService"
    - "extractUserIdFromBearer() tái dụng — không duplicate JWT parsing logic"

key-files:
  created:
    - "sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/UpdateMeRequest.java"
    - "sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/service/UserProfileService.java"
  modified:
    - "sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/domain/UserDto.java"
    - "sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/domain/UserMapper.java"
    - "sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/UserMeController.java"

key-decisions:
  - "D-06: hasAvatar=false trong Phase 10 — avatar defer per D-08, UserDto record extend thêm field thay vì tạo MeResponse riêng"
  - "Partial update via nullable fields — @Size/@Pattern chỉ validate khi not-null, không dùng @NotBlank"
  - "UserProfileService riêng — không nhét vào UserCrudService (admin) hay UserPasswordService (password)"

patterns-established:
  - "UserProfileService pattern: @Service @Transactional, constructor inject UserRepository, method getMe/updateMe"
  - "UpdateMeRequest pattern: nullable optional fields với jakarta constraint chỉ kick in khi not-null"

requirements-completed:
  - ACCT-03

# Metrics
duration: 15min
completed: "2026-04-27"
---

# Phase 10 Plan 01: User-Svc Backend Profile Endpoints Summary

**Spring Boot GET + PATCH /users/me endpoints với jakarta validation, partial update pattern, và UserDto mở rộng hasAvatar field cho profile editing frontend**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-04-27T03:39:00Z
- **Completed:** 2026-04-27T03:54:02Z
- **Tasks:** 3/3
- **Files modified:** 5 (2 created, 3 modified)

## Accomplishments

- UserDto record extend thêm `boolean hasAvatar` (luôn false Phase 10) — wire format chuẩn cho FE Plan 10-03
- Tạo `UserProfileService` với `getMe(userId)` + `updateMe(userId, UpdateMeRequest)` — cô lập profile concern
- Wire `GET /users/me` + `PATCH /users/me` vào `UserMeController` (extend in-place, tái dụng `extractUserIdFromBearer`)
- `mvn compile` exit 0 sau cả 3 task

## Task Commits

1. **Task 1: Extend UserDto + UserMapper với hasAvatar field** - `011b792` (feat)
2. **Task 2: Tạo UpdateMeRequest record + UserProfileService** - `8a79932` (feat)
3. **Task 3: Wire GET /users/me + PATCH /users/me trong UserMeController** - `67cca8b` (feat)

## Files Created/Modified

- `domain/UserDto.java` - Thêm `boolean hasAvatar` component tại vị trí thứ 7 (giữa phone và createdAt)
- `domain/UserMapper.java` - Update `toDto()` pass `false` tại arg thứ 7 (D-06 comment)
- `web/UpdateMeRequest.java` - NEW: record với nullable `fullName` (@Size 1-120) + `phone` (@Pattern VN-loose)
- `service/UserProfileService.java` - NEW: @Service @Transactional với `getMe()` + `updateMe()` partial update
- `web/UserMeController.java` - Inject `UserProfileService`, thêm @GetMapping + @PatchMapping handlers

## Decisions Made

- `hasAvatar` luôn `false` Phase 10 — giữ consistent với existing UserDto type, KHÔNG tạo MeResponse riêng (D-06)
- Partial update via nullable optional fields — @Size/@Pattern chỉ kick in khi not-null (theo CONTEXT.md Claude's Discretion)
- Phone regex `^\+?[0-9\s-]{7,20}$` — VN-loose pattern, không dùng `\-` escape thừa
- `UserProfileService` tách riêng — không nhét vào `UserCrudService` (admin concern) hay `UserPasswordService` (password concern)

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

- `hasAvatar` trong `UserDto` luôn trả `false` (UserMapper.toDto line 19). Stub có chủ ý per D-06/D-08: avatar upload defer backlog. Plan 10-01 FE (10-03) sẽ hiển thị initials placeholder thay vì avatar image. Phase 12+ wire real value khi cần.

## Issues Encountered

- `mvn` không có trên PATH trong shell environment — dùng Maven wrapper từ `~/.m2/wrapper/dists/apache-maven-3.9.12/6068d197/bin/mvn` với `JAVA_HOME` explicit. Build hoạt động bình thường.

## User Setup Required

None — không có external service configuration required. Endpoints sẵn sàng sau khi docker stack restart user-service.

## Next Phase Readiness

- Backend endpoints sẵn sàng: `GET /api/users/me` + `PATCH /api/users/me` qua gateway
- Plan 10-03 (frontend profile form) có thể gọi endpoints này với Bearer token
- Gateway route `user-service-me` đã đứng trước `user-service-base` — không cần sửa gateway

---
*Phase: 10-user-svc-schema-profile-editing*
*Completed: 2026-04-27*
