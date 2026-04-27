---
phase: 11-address-book-order-history-filtering
plan: "01"
subsystem: api
tags: [spring-boot, jpa, flyway, postgres, jwt, address-book]

# Dependency graph
requires:
  - phase: 09-auth-security
    provides: "JWT extractUserIdFromBearer pattern, GlobalExceptionHandler base"
  - phase: 10-user-profile
    provides: "UserMeController pattern, ApiResponse envelope, UserProfileService pattern"
provides:
  - "V4 Flyway migration: bảng user_svc.addresses + partial unique index WHERE is_default=true"
  - "AddressEntity JPA entity với hard-delete và static factory"
  - "AddressRepository với clearDefaultByUserId @Modifying query"
  - "AddressService: 5 methods CRUD + set-default với ownership check + address cap"
  - "AddressController: 5 REST endpoints /users/me/addresses/**"
  - "GlobalExceptionHandler mở rộng với ADDRESS_LIMIT_EXCEEDED → 422"
affects:
  - 11-02-frontend-address-book
  - 11-03-checkout-address-picker
  - checkout-integration

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "AddressEntity factory pattern: AddressEntity.create(userId, ...) sinh UUID + Instant.now()"
    - "Ownership check pattern: findById + userId.equals() → 403 FORBIDDEN nếu không phải owner"
    - "clearDefaultByUserId @Modifying JPQL pattern trước khi set default mới"
    - "Partial unique index WHERE is_default=true enforces SC-3 tại DB level"

key-files:
  created:
    - sources/backend/user-service/src/main/resources/db/migration/V4__create_addresses.sql
    - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/domain/AddressEntity.java
    - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/domain/AddressDto.java
    - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/repository/AddressRepository.java
    - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/exception/AddressLimitExceededException.java
    - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/service/AddressRequest.java
    - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/service/AddressService.java
    - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/AddressController.java
  modified:
    - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/api/GlobalExceptionHandler.java

key-decisions:
  - "AddressRequest tạo trong package service (không phải web) để tránh circular dependency với controller"
  - "Hard-delete addresses (D-07): không cần @SQLRestriction, entity đơn giản hơn"
  - "Partial unique index ở DB level (không application-level lock) để đảm bảo SC-3 an toàn với concurrent requests"
  - "userId lấy từ JWT claims.sub (extractUserIdFromBearer) — không từ path/body param (T-11-01-01)"

patterns-established:
  - "Address ownership check: findById(id).orElseThrow(404) + userId.equals() check → 403 FORBIDDEN"
  - "Set-default pattern: clearDefaultByUserId(userId) trước entity.setDefault(true) + save"
  - "Address cap check: countByUserId(userId) >= 10 → throw AddressLimitExceededException"

requirements-completed: [ACCT-05, ACCT-06]

# Metrics
duration: 4min
completed: 2026-04-27
---

# Phase 11 Plan 01: Address Book Backend Summary

**V4 Flyway migration + 5-endpoint address CRUD REST API cho user-service với JWT ownership checks, 10-address cap (422), và DB-level partial unique index enforcing set-default atomicity**

## Performance

- **Duration:** ~4 min
- **Started:** 2026-04-27T07:04:01Z
- **Completed:** 2026-04-27T07:07:34Z
- **Tasks:** 2/2
- **Files modified:** 9 (8 tạo mới, 1 cập nhật)

## Accomplishments

- V4 Flyway migration tạo bảng `user_svc.addresses` với partial unique index `WHERE is_default=true` (SC-3 enforcement tại DB level)
- 5 REST endpoints `/users/me/addresses/**`: GET list, POST create, PUT update, DELETE, PUT set-default — tất cả JWT-protected, ownership-checked
- Business logic: cap 10 addresses/user (422 `ADDRESS_LIMIT_EXCEEDED`), clearDefaultByUserId @Modifying JPQL, hard-delete (D-07)
- GlobalExceptionHandler extended với `AddressLimitExceededException` → 422 + errorCode consistent với codebase pattern

## Task Commits

1. **Task 1: V4 Flyway migration + AddressEntity + AddressDto + AddressRepository** - `464fa89` (feat)
2. **Task 2: AddressService + AddressController + AddressLimitExceededException + GlobalExceptionHandler** - `6045e4a` (feat)

## Files Created/Modified

- `sources/backend/user-service/src/main/resources/db/migration/V4__create_addresses.sql` - Schema addresses table + 2 indexes (partial unique + user_created)
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/domain/AddressEntity.java` - JPA entity với static factory, record-style accessors, hard-delete
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/domain/AddressDto.java` - Record wire format với `from(AddressEntity)` factory
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/repository/AddressRepository.java` - JpaRepository với `clearDefaultByUserId` @Modifying
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/exception/AddressLimitExceededException.java` - Custom exception cho cap 10
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/service/AddressRequest.java` - Validated record request body
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/service/AddressService.java` - Service layer: 5 methods + ownership check + address cap
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/AddressController.java` - 5 REST endpoints với JWT extraction pattern
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/api/GlobalExceptionHandler.java` - Thêm handler AddressLimitExceededException → 422

## Decisions Made

- `AddressRequest` đặt trong package `service` (không phải `web`) để tránh circular import
- `AddressEntity` không dùng `@SQLRestriction` vì hard-delete (D-07) — đơn giản hơn UserEntity
- Threat T-11-01-04 (concurrent set-default) mitigated tại DB level bằng partial unique index, không cần application-level locking

## Deviations from Plan

Không có deviation — plan thực thi đúng theo spec. Chú ý nhỏ: `AddressRequest` tạo file riêng trong package `service` thay vì inner record trong `AddressService.java` để đảm bảo tái sử dụng từ controller.

## Issues Encountered

Không có vấn đề nào. Tất cả pattern copy từ UserMeController và UserProfileService hoạt động tốt.

## Known Stubs

Không có stub. Backend API hoàn chỉnh. Frontend plans (11-02, 11-03) sẽ tích hợp sau.

## Threat Flags

Không có threat surface mới ngoài plan's threat model. Tất cả 5 threats đã được mitigate theo spec:
- T-11-01-01: userId từ JWT (không từ path/body)
- T-11-01-02: Ownership check → 403
- T-11-01-03: Cap 10 → 422 ADDRESS_LIMIT_EXCEEDED
- T-11-01-04: DB partial unique index + clearDefaultByUserId
- T-11-01-05: findByUserId filter trong query

## Next Phase Readiness

- Backend address API sẵn sàng để frontend plans (11-02: `/profile/addresses` page, 11-03: AddressPicker checkout) tích hợp
- Gateway routing `/api/users/me/addresses/**` cần verify trong `api-gateway/application.yml` (nằm ngoài scope plan này)
- Flyway V4 migration sẽ tự động apply khi user-service khởi động

---
*Phase: 11-address-book-order-history-filtering*
*Completed: 2026-04-27*
