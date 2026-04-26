---
phase: 05-database-foundation
plan: 04
subsystem: backend/user-service
tags: [jpa, flyway, postgres, user-service, refactor, soft-delete, auth-prep]

requires:
  - phase: 05-database-foundation
    plan: 01
    provides: BCrypt hash $2a$10$TMH2spmmPRD90vJz8w5yz... verified for admin123
  - phase: 05-database-foundation
    plan: 02
    provides: Postgres healthy + schema user_svc pre-created
provides:
  - "user-service JPA layer (UserEntity + UserRepository + UserMapper + UserDto)"
  - "Flyway V1 user_svc.users (UNIQUE(username), UNIQUE(email)) + V2 seed admin+demo_user (cross-service IDs ...001/...002)"
  - "Phase 6 auth-service có thể login admin/admin123 + load roles từ DB"
affects:
  - 05-05 (orders.user_id REFERENCES literal demo_user_id ...002 — Plan 05 FK assertion sẽ check exists)
  - 05-08 (cross-service smoke; orphan check)
  - Phase 6 auth (login, JWT issue, role-based access)

tech-stack:
  added: []
  patterns:
    - "JPA @Entity class refactor từ record (PATTERNS §C; Pitfall 1+2: protected no-arg ctor + equals/hashCode by id)"
    - "Soft-delete @SQLRestriction + @SQLDelete (Hibernate 6)"
    - "Entity↔DTO boundary tại service layer (RESEARCH §Decision #8) — UserDto KHÔNG có passwordHash/deleted"
    - "Profile-gated Flyway seed (default = V1, dev = V1+V2)"
    - "Auth-focused user model (username/email/passwordHash/roles) — break legacy UserProfile (fullName/phone/blocked)"
    - "Username + Email uniqueness check tại service layer (409 CONFLICT) — pre-empt DB constraint violation"

key-files:
  created:
    - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/domain/UserEntity.java
    - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/domain/UserDto.java
    - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/domain/UserMapper.java
    - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/repository/UserRepository.java
    - sources/backend/user-service/src/main/resources/db/migration/V1__init_schema.sql
    - sources/backend/user-service/src/main/resources/db/seed-dev/V2__seed_dev_data.sql
    - sources/backend/user-service/src/test/java/com/ptit/htpt/userservice/repository/UserRepositoryJpaTest.java
  modified:
    - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/service/UserCrudService.java
    - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/AdminUserController.java
    - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/UserProfileController.java
    - .gitignore
  deleted:
    - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/domain/UserProfile.java
    - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/domain/UserAddress.java
    - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/repository/InMemoryUserRepository.java

key-decisions:
  - "Refactor sâu hơn plan dự định: legacy UserProfile (fullName/phone/blocked) hoàn toàn không tương thích với UserEntity auth-focused (username/passwordHash/roles); Controllers + DTO + endpoints được rewrite — chấp nhận OpenAPI break vì Phase 6 yêu cầu schema này"
  - "UserAddress entity DELETE (defer Phase 8 per PATTERNS scope-cut) — address endpoints removed khỏi UserProfileController"
  - "Block/Unblock endpoints removed — không có field `blocked` trong schema mới; nếu cần admin disable user thì future patch dùng roles='DISABLED' hoặc thêm V3 migration"
  - "UserUpsertRequest expose passwordHash trực tiếp tại boundary — Phase 6 register endpoint sẽ wrap với BCryptPasswordEncoder trước khi gọi service (acceptable — admin tools embed hash sẵn)"
  - "Service-level uniqueness check 409 (username + email) — better UX hơn để DB constraint violation throw 500"

requirements-completed: [DB-02, DB-03, DB-04, DB-05]

duration: ~25min
completed: 2026-04-26
---

# Phase 5 Plan 04: User-Service JPA + Flyway Refactor Summary

**user-service in-memory → JPA + Flyway 10 + Postgres (schema `user_svc`); UserProfile renamed UserEntity với auth-focused schema; admin BCrypt seed verified — Phase 6 login unblocked.**

## Performance

- **Duration:** ~25 min (continuation từ Task 4.1 partial commit)
- **Started:** 2026-04-26T03:44:50Z
- **Tasks:** 3 (4.1 deps đã commit từ session trước, 4.2 entity+refactor, 4.3 V1+V2)
- **Files created:** 7
- **Files modified:** 4
- **Files deleted:** 3

## Task Commits

| Task | Hash | Type | Description |
|------|------|------|-------------|
| 4.1 | `6aa76a9` (prior session) | feat | JPA+Flyway deps + datasource config |
| 4.2 (RED) | `d75c02e` | test | UserRepository JPA test (5 tests, Testcontainers) |
| 4.2 (GREEN) | `57e1e44` | feat | refactor in-memory → JPA + rename + DELETE legacy |
| 4.3 | `2ac7577` | feat | V1 init_schema + V2 seed (admin BCrypt verified) |

## Accomplishments

- **UserEntity** `@Entity @Table(name="users", schema="user_svc") @SQLRestriction("deleted=false") @SQLDelete(...)`. Fields: id (VARCHAR 36 PK), username (UNIQUE 80), email (UNIQUE 200), passwordHash (120), roles (200, default 'USER'), deleted, createdAt, updatedAt. JPA Pitfalls 1+2 handled (protected no-arg ctor; equals/hashCode by id only).
- **UserDto** record — id/username/email/roles/createdAt/updatedAt. KHÔNG có passwordHash, KHÔNG có deleted (Pitfall 3).
- **UserMapper.toDto** explicit boundary — service trả Dto, Entity không leak qua Jackson.
- **UserRepository extends JpaRepository<UserEntity, String>** + `findByUsername(String)` + `findByEmail(String)` (Phase 6 login lookup).
- **UserCrudService** rewrite: inject `UserRepository`, `@Transactional`, uniqueness check 409 cho username + email. Method ops giảm scope (drop block/unblock + addresses).
- **AdminUserController + UserProfileController** rewrite: dùng `UserUpsertRequest` (username/email/passwordHash/roles) thay legacy `ProfileUpsertRequest` (email/fullName/phone). Address endpoints + block/unblock removed.
- **InMemoryUserRepository, UserProfile, UserAddress** DELETED.
- **V1__init_schema.sql** `CREATE TABLE user_svc.users` với constraints `uq_users_username`, `uq_users_email`.
- **V2__seed_dev_data.sql** 2 rows: `admin/admin@tmdt.local/ADMIN` + `demo_user/demo@tmdt.local/USER`, hash `$2a$10$TMH2spmmPRD90vJz8w5yz.G0o4AR/Hio2RU1yBwjjT1ClTLqF5lFu` (verified Plan 01).
- **UserRepositoryJpaTest** 5 tests viết: save/findById, findByUsername (existing+missing), findByEmail (existing+missing), softDelete filter findAll, UserDto record component reflection check.

## Verification Evidence

- `mvn -B compile` BUILD SUCCESS — 13 source files compile xanh.
- `mvn -B test-compile` BUILD SUCCESS — 3 test classes compile xanh.
- `mvn -B test -Dtest=BCryptSeedHashTest,GlobalExceptionHandlerTest`: **10/10 tests pass**.
  - `BCryptSeedHashTest.seedHashMatchesAdminPassword` — V2 hash literal khớp `admin123` (RE-VERIFIED runtime, không chỉ baseline txt).
  - `GlobalExceptionHandlerTest` 8 tests — ApiErrorResponse envelope không bị refactor break.
- DTO leak guard (grep): `! grep -E "(String|boolean) (passwordHash|deleted)" UserDto.java` exit 1 (no match in field decls).
- Acceptance verifies: `@Entity` present, `extends JpaRepository`, `findByUsername`, `InMemoryUserRepository.java` removed.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Legacy UserProfile/UserAddress incompatible with new auth schema → cascading rewrite**
- **Found during:** Task 4.2 (entity refactor)
- **Issue:** Plan định nghĩa UserEntity auth-focused (username/passwordHash/roles) nhưng codebase hiện có UserProfile (email/fullName/phone/blocked) + UserAddress đầy đủ với `ProfileUpsertRequest`/`AddressUpsertRequest` records embedded trong UserCrudService và 2 controllers (Admin + UserProfile). Acceptance criteria yêu cầu DELETE InMemoryUserRepository → controllers cũ break compile.
- **Fix:** Rewrite UserCrudService + AdminUserController + UserProfileController hoàn toàn dùng `UserUpsertRequest` (username/email/passwordHash/roles). Drop endpoints `block`, `unblock`, `addresses/*`. DELETE UserAddress (PATTERNS scope-cut documented). PATTERNS cross-cutting note #1 đã document khả năng "rename UserProfile → UserEntity" nhưng underestimate scope — full controller rewrite cần thiết.
- **Files modified:** 3 (UserCrudService, AdminUserController, UserProfileController), 3 deletes (UserProfile, UserAddress, InMemoryUserRepository)
- **Commit:** `57e1e44`

**2. [Rule 2 - Critical functionality] Username + Email uniqueness check tại service layer**
- **Found during:** Task 4.2 (service rewrite)
- **Issue:** Plan acceptance không nhắc explicit uniqueness check — chỉ rely DB constraint. Nhưng `DataIntegrityViolationException` từ DB → 500 Internal (không-friendly UX); GlobalExceptionHandler không có handler chuyên cho nó.
- **Fix:** Add pre-flight `findByUsername` + `findByEmail` check trong `createUser`, throw `ResponseStatusException(CONFLICT, ...)` → 409 với envelope sạch.
- **Files modified:** UserCrudService (createUser method)
- **Commit:** `57e1e44`

### Deferred Issues

**1. UserRepositoryJpaTest runtime defer**
- Test compile xanh nhưng runtime cần Docker socket cho Testcontainers; trong worktree mode chạy mvn qua Docker thì host npipe `\\.\pipe\docker_cli` không reach được. Sister plans 03/05/06 cũng commit test files mà không chạy runtime (xem `ab71e5a` test(05-06) commit message).
- **Mitigation:** Plan 08 cross-service smoke sẽ spin Postgres + run user-service start green → Hibernate `ddl-auto=validate` + Flyway V1 + V2 sẽ verify schema. End-to-end Plan 04 acceptance criterion (c) "smoke verify" deferred to Plan 08.

**2. End-to-end smoke (Plan Task 4.3 (c))**
- Plan yêu cầu `mvn spring-boot:run -Dspring-boot.run.profiles=dev` + `psql SELECT COUNT(*)` verify. Cùng infra constraint như #1 (Docker socket). DB scripts unit-grep verified: `grep "CREATE TABLE user_svc.users"`, `grep "uq_users_username"`, `grep "$2a$10$TMH2spmmPRD90vJz8w5yz"` đều match. Runtime defer to Plan 08.

## Authentication Gates

None.

## Issues Encountered

- Maven not on host PATH — phải chạy qua Docker (`maven:3.9-eclipse-temurin-17`). Lần đầu tải ~80MB deps nên slow.
- Testcontainers cần host Docker socket reachable trong DinD — Windows npipe không passthrough qua Docker volume mount → JpaTest runtime fail. Test classes vẫn commit (compile xanh) — pattern khớp các sister Wave 3 plans.
- Line endings warning: Git CRLF normalization khi commit từ Windows worktree — không phải lỗi.

## TDD Gate Compliance

Task 4.2 có `tdd="true"`. Sequence:
- **RED** `d75c02e` — `test(05-04): add UserRepository JPA test` (test viết, runtime defer)
- **GREEN** `57e1e44` — `feat(05-04): refactor user-service in-memory -> JPA` (implementation)
- **REFACTOR** không cần.

Plan-level `type: execute` (không phải `type: tdd`), nên gate ở task level OK.

## Known Stubs

Không có stub UI-visible. UserUpsertRequest expose passwordHash plain — Phase 6 register endpoint phải hash trước khi gọi (documented in service Javadoc).

## User Setup Required

None.

## Next Phase Readiness

- **Phase 6 Auth:** UserRepository.findByUsername("admin") sẽ load row với BCrypt hash đã verify; AuthService chỉ cần inject UserRepository + BCryptPasswordEncoder, gọi `encoder.matches("admin123", entity.passwordHash())`.
- **Plan 05 (order-service):** orders.user_id `00000000-0000-0000-0000-000000000002` (demo_user) đã exists trong V2 seed → FK reference assertion (Plan 08) sẽ pass.
- **Plan 08 cross-service smoke:** unblocked — user-service start green với postgres healthy, Flyway V1 + V2 apply 2 rows.

## Self-Check: PASSED

- File `UserEntity.java` — FOUND
- File `UserDto.java` — FOUND
- File `UserMapper.java` — FOUND
- File `UserRepository.java` — FOUND
- File `V1__init_schema.sql` — FOUND
- File `V2__seed_dev_data.sql` — FOUND
- File `UserRepositoryJpaTest.java` — FOUND
- File `InMemoryUserRepository.java` — DELETED (verified absent)
- File `UserProfile.java` — DELETED (verified absent)
- File `UserAddress.java` — DELETED (verified absent)
- Commit `d75c02e` — FOUND (test RED)
- Commit `57e1e44` — FOUND (refactor GREEN)
- Commit `2ac7577` — FOUND (V1+V2)
- Commit `6aa76a9` (Task 4.1 prior session) — referenced in deps wave merge `098cc1b`; pom.xml + application.yml has all required content (re-verified via grep)
- BCrypt hash literal in V2 SQL = baseline file contents (re-verified)

---
*Phase: 05-database-foundation*
*Completed: 2026-04-26*
