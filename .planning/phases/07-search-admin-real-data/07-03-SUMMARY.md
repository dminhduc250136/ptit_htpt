---
phase: 07-search-admin-real-data
plan: "03"
subsystem: user-service
tags: [user-service, flyway, migration, patch-endpoint, admin]
dependency_graph:
  requires: []
  provides: [fullName-phone-columns, patch-admin-users-endpoint]
  affects: [user-service, admin-users-page]
tech_stack:
  added: []
  patterns: [flyway-migration, partial-update-record, jpa-entity-setter]
key_files:
  created:
    - sources/backend/user-service/src/main/resources/db/migration/V2__add_fullname_phone.sql
  modified:
    - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/domain/UserEntity.java
    - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/domain/UserDto.java
    - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/domain/UserMapper.java
    - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/service/UserCrudService.java
    - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/AdminUserController.java
decisions:
  - "Flyway V2 dùng ADD COLUMN IF NOT EXISTS — idempotent, không break existing rows"
  - "AdminUserPatchRequest là inner record của UserCrudService — giữ scope gần service, không tạo class riêng"
  - "patchUser() dùng null-check cho từng field — partial update đúng nghĩa; roles cần thêm isBlank() guard"
  - "touch() luôn gọi sau các setter để đảm bảo updatedAt được update kể cả khi body rỗng"
  - "PATCH không dùng @Valid vì tất cả fields đều nullable theo design"
metrics:
  duration: "~15 min"
  completed: "2026-04-26"
  tasks_completed: 2
  tasks_total: 2
  files_created: 1
  files_modified: 5
---

# Phase 7 Plan 03: User fullName/phone + PATCH admin endpoint — Summary

Thêm fullName/phone vào user-service (D-04) và PATCH /admin/users/{id} endpoint (D-05). Chain hoàn chỉnh: Flyway V2 migration → UserEntity fields/setters → UserDto record → UserMapper.toDto() → AdminUserPatchRequest → patchUser() service → @PatchMapping controller.

## Tasks Completed

| Task | Tên | Commit | Files |
|------|-----|--------|-------|
| 1 | D-04 chain: Flyway V2 + UserEntity + UserDto + UserMapper | `279b2d3` | V2__add_fullname_phone.sql, UserEntity.java, UserDto.java, UserMapper.java |
| 2 | D-05: AdminUserPatchRequest + patchUser() + PATCH endpoint | `c85e7dd` | UserCrudService.java, AdminUserController.java |

## D-04 Chain Detail

**Flyway V2 Migration (`V2__add_fullname_phone.sql`):**
- `ALTER TABLE user_svc.users ADD COLUMN IF NOT EXISTS full_name VARCHAR(120)`
- `ALTER TABLE user_svc.users ADD COLUMN IF NOT EXISTS phone VARCHAR(20)`
- Idempotent với `IF NOT EXISTS` — safe re-run

**UserEntity.java:**
- Thêm 2 JPA fields: `@Column(name = "full_name") private String fullName` + `@Column private String phone`
- Thêm 4 mutator methods: `setFullName()`, `setPhone()`, `setRoles()`, `touch()` — mỗi setter update `updatedAt`
- Thêm 2 accessors: `fullName()`, `phone()`
- Constructor protected mở rộng với fullName + phone params
- `create()` static factory truyền `null, null` cho fullName + phone (user mới không có)

**UserDto.java:**
- Record thêm `String fullName` và `String phone` (nullable) sau `roles`, trước `createdAt`

**UserMapper.java:**
- `toDto()` thêm `e.fullName()` và `e.phone()` theo đúng thứ tự record mới

## D-05 PATCH Endpoint Detail

**UserCrudService.java — AdminUserPatchRequest record:**
```java
public record AdminUserPatchRequest(
    String fullName,   // nullable — update nếu not null
    String phone,      // nullable — update nếu not null
    String roles       // nullable — update nếu not null và not blank
) {}
```

**UserCrudService.java — patchUser() method:**
- null-check từng field trước khi gọi setter
- roles có thêm `isBlank()` guard tránh overwrite với empty string
- `touch()` luôn được gọi để đảm bảo `updatedAt` luôn refresh
- Trả về `UserMapper.toDto(userRepo.save(user))`

**AdminUserController.java — @PatchMapping:**
- Import `PatchMapping` + `AdminUserPatchRequest`
- `@PatchMapping("/{id}")` — không dùng `@Valid` vì fields nullable

## Verification Results

| Check | Kết quả |
|-------|---------|
| `mvn compile -pl user-service` | PASS (exit 0) |
| `ls db/migration/` — có V1 + V2 | PASS |
| `grep "ADD COLUMN IF NOT EXISTS full_name"` | PASS (1 match) |
| `grep "ADD COLUMN IF NOT EXISTS phone"` | PASS (1 match) |
| `grep "@PatchMapping"` trong AdminUserController | PASS (1 match) |
| `grep "e.fullName()"` trong UserMapper | PASS (1 match) |
| `grep "private String fullName"` trong UserEntity | PASS (1 match) |
| `grep "AdminUserPatchRequest"` trong UserCrudService | PASS (3 matches) |

## Deviations from Plan

None — plan executed exactly as written.

## Threat Flags

Không phát hiện threat surface mới ngoài plan's threat_model (T-07-03-01, T-07-03-02, T-07-03-03 đã được đánh giá trong plan).

## Known Stubs

None — không có stubs. Endpoint functional và compile clean.

## Self-Check: PASSED

- `V2__add_fullname_phone.sql` — FOUND
- `UserEntity.java` với `private String fullName` — FOUND
- `UserDto.java` với `String fullName` — FOUND
- `UserMapper.java` với `e.fullName()` — FOUND
- `UserCrudService.java` với `AdminUserPatchRequest` — FOUND
- `AdminUserController.java` với `@PatchMapping` — FOUND
- Commits `279b2d3` và `c85e7dd` — FOUND
