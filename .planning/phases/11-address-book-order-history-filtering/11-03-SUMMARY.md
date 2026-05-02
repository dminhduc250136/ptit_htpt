---
phase: 11-address-book-order-history-filtering
plan: "03"
subsystem: infra
tags: [flyway, gateway, spring-boot, docker, postgresql, migration]

# Dependency graph
requires:
  - phase: 11-address-book-order-history-filtering
    plan: "01"
    provides: "AddressEntity, V101__create_addresses.sql, AddressController tại /users/me/addresses"
  - phase: 11-address-book-order-history-filtering
    plan: "02"
    provides: "order filter backend (không có dependency trực tiếp, cùng wave)"
provides:
  - "Gateway route user-service-me/** verified — forward /api/users/me/addresses/** → /users/me/addresses/** đúng"
  - "Flyway V101__create_addresses.sql applied thành công — bảng user_svc.addresses tồn tại trong DB"
  - "user-service health UP sau rebuild + migration"
affects:
  - "11-04 (address book frontend)"
  - "11-05 (order history frontend)"

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Gateway route user-service-me/** (seg capture) đã bao phủ /api/users/me/addresses/** mà không cần route riêng"
    - "Flyway versioning dùng V1xx để tránh conflict với seed-dev migrations (V1xx > V100)"

key-files:
  created: []
  modified:
    - "sources/backend/api-gateway/src/main/resources/application.yml"
    - "sources/backend/user-service/src/main/resources/db/migration/V101__create_addresses.sql"

key-decisions:
  - "Không tạo route riêng user-service-me-addresses — route user-service-me/** với seg capture đã forward /addresses/** đúng"
  - "Đổi tên migration V4 → V101 vì DB đang ở version V100 (seed-dev data), V4 bị Flyway reject do out-of-order"
  - "Đổi cột id/user_id từ UUID → VARCHAR(36) để match AddressEntity String mapping và nhất quán với pattern users table"
  - "Docker rebuild --no-cache cần thiết — docker compose restart không swap image mới"

patterns-established:
  - "Flyway migration versioning: dùng V1xx (>V100) cho migrations sau seed-dev để tránh out-of-order error"
  - "Gateway seg capture: Path=/api/users/me/** + RewritePath=...(?<seg>.*), /users/me/${seg} bao phủ tất cả sub-paths"

requirements-completed:
  - ACCT-05
  - ACCT-06
  - ACCT-02

# Metrics
duration: ~60min (bao gồm checkpoint resolve)
completed: 2026-04-27
---

# Phase 11 Plan 03: Gateway Route Verify + Flyway V101 Apply Summary

**Gateway route user-service-me/** confirmed đủ cho addresses, Flyway V101 applied qua Docker rebuild — bảng user_svc.addresses tồn tại với VARCHAR(36) schema**

## Performance

- **Duration:** ~60 phút (bao gồm checkpoint human-verify resolve)
- **Started:** 2026-04-27
- **Completed:** 2026-04-27
- **Tasks:** 2/2
- **Files modified:** 2

## Accomplishments

- Verify gateway application.yml: route `user-service-me` với `Path=/api/users/me/**` + `RewritePath` seg capture group đã forward `/api/users/me/addresses/**` → `/users/me/addresses/**` đúng, không cần route riêng
- Flyway V101__create_addresses.sql applied thành công sau Docker rebuild --no-cache
- user-service health endpoint trả `{"status":"UP"}` — bảng `user_svc.addresses` tồn tại trong DB với đúng schema

## Task Commits

Mỗi task được commit atomically:

1. **Task 1: Verify + update gateway route** - `fd79c43` (feat)
2. **Task 2 (deviations):**
   - `38c0c1f` — fix: rename V4 → V101 migration (out-of-order Flyway fix)
   - `c211902` — fix: UUID → VARCHAR(36) cho addresses.id + user_id

## Files Created/Modified

- `sources/backend/api-gateway/src/main/resources/application.yml` — thêm comment Phase 11 document addresses routing qua route user-service-me
- `sources/backend/user-service/src/main/resources/db/migration/V101__create_addresses.sql` — renamed từ V4, fixed cột id/user_id sang VARCHAR(36)

## Decisions Made

- Route `user-service-me/**` đã đủ để forward addresses endpoints: seg capture `(?<seg>.*)` với request `/api/users/me/addresses/123` → seg=`addresses/123` → forward `/users/me/addresses/123` → match `AddressController @RequestMapping("/users/me/addresses") @GetMapping("/{id}")` ✓
- Chọn V101 thay vì V4 vì DB đang chạy ở version V100 (seed-dev migration), Flyway không cho phép out-of-order mặc định
- VARCHAR(36) thay UUID vì AddressEntity dùng `String id` (Hibernate mapping), và users table đã dùng pattern tương tự

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Rename migration V4 → V101 (Flyway out-of-order)**
- **Found during:** Task 2 (rebuild + restart user-service)
- **Issue:** Flyway báo lỗi out-of-order — DB đang ở V100 (seed-dev), V4 < V100 bị reject khi `outOfOrder=false` (default)
- **Fix:** Đổi tên file `V4__create_addresses.sql` → `V101__create_addresses.sql` để version > V100
- **Files modified:** `sources/backend/user-service/src/main/resources/db/migration/V101__create_addresses.sql`
- **Verification:** user-service log không có Flyway error, health = UP
- **Committed in:** `38c0c1f`

**2. [Rule 1 - Bug] Đổi cột id/user_id UUID → VARCHAR(36)**
- **Found during:** Task 2 (sau khi fix V101, service vẫn fail start)
- **Issue:** SQL dùng `id UUID DEFAULT gen_random_uuid()` nhưng `AddressEntity` map `String id` — Hibernate không auto-convert UUID → String, gây column type mismatch error
- **Fix:** Đổi `id VARCHAR(36) DEFAULT gen_random_uuid()::text` và `user_id VARCHAR(36) NOT NULL` để match String mapping, nhất quán với pattern của users table
- **Files modified:** `sources/backend/user-service/src/main/resources/db/migration/V101__create_addresses.sql`
- **Verification:** user-service khởi động thành công, bảng tồn tại với schema đúng
- **Committed in:** `c211902`

---

**Total deviations:** 2 auto-fixed (2 Rule 1 - Bug)
**Impact on plan:** Cả hai fixes cần thiết để migration chạy đúng. Không có scope creep. Kết quả giống nhau với plan gốc: bảng addresses tồn tại + service UP.

## Issues Encountered

- Docker compose restart không load image mới sau rebuild — cần `docker compose up -d --no-deps --build user-service` hoặc `docker compose build --no-cache user-service && docker compose up -d user-service` để swap image. Resolved bằng rebuild --no-cache.

## User Setup Required

None — không cần cấu hình external service. Docker stack chạy tự động.

## Next Phase Readiness

- Gateway route verified → frontend addresses API calls (`/api/users/me/addresses/**`) sẽ được forward đúng
- Bảng `user_svc.addresses` sẵn sàng cho CRUD operations
- Plan 11-04 (address book frontend) có thể bắt đầu ngay
- Plan 11-05 (order history frontend) không phụ thuộc vào plan này — cũng có thể bắt đầu song song

---
*Phase: 11-address-book-order-history-filtering*
*Completed: 2026-04-27*
