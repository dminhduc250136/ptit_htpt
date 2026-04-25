---
phase: 05-database-foundation
plan: 01
subsystem: database
tags: [openapi, springdoc, bcrypt, baseline, preflight]

requires:
  - phase: 04-current-stack
    provides: 5 in-memory backend services with Springdoc 2.6.0 emitting /v3/api-docs
provides:
  - 5 OpenAPI baseline JSONs (sort-keys) cho user/product/order/payment/inventory pre-refactor
  - Verified BCrypt hash cho admin123 (downstream Plan 05-03 V2 SQL embed value)
  - Fail-fast evidence rằng hash literal trong RESEARCH §Decision #6 SAI (sample hash hashes "password", not "admin123")
affects:
  - 05-03 (user-service refactor — phải dùng hash đã verify)
  - 05-08 (integration smoke — diff baseline vs post-refactor để bắt entity leak)
  - Phase 6 (admin login admin/admin123 — phải khớp seed hash)

tech-stack:
  added:
    - "spring-security-crypto (scope=test) trong user-service pom.xml"
  patterns:
    - "Pre-refactor OpenAPI baseline capture qua /v3/api-docs (DB-06 success criterion #4 enforcement)"
    - "Test-gated seed value (test xanh BEFORE downstream SQL embed)"

key-files:
  created:
    - .planning/phases/05-database-foundation/baseline/openapi-user-service.json
    - .planning/phases/05-database-foundation/baseline/openapi-product-service.json
    - .planning/phases/05-database-foundation/baseline/openapi-order-service.json
    - .planning/phases/05-database-foundation/baseline/openapi-payment-service.json
    - .planning/phases/05-database-foundation/baseline/openapi-inventory-service.json
    - .planning/phases/05-database-foundation/baseline/bcrypt-hash-verified.txt
    - sources/backend/user-service/src/test/java/com/ptit/htpt/userservice/seed/BCryptSeedHashTest.java
  modified:
    - sources/backend/user-service/pom.xml

key-decisions:
  - "Hash literal đúng cho admin123 = $2a$10$TMH2spmmPRD90vJz8w5yz.G0o4AR/Hio2RU1yBwjjT1ClTLqF5lFu (RESEARCH literal sai — sample hash của 'password')"
  - "Baseline captured qua docker compose up (KHÔNG dùng mvn spring-boot:run) — toàn bộ 5 services build green"
  - "OpenAPI service-internal paths dùng prefix '/products' (KHÔNG '/api/products') vì /api/ prefix là gateway concern — diff Wave 4 phải dùng cùng convention"

patterns-established:
  - "Baseline directory: .planning/phases/<phase>/baseline/ — convention cho compare-target artifacts"
  - "BCrypt verifier test pattern (encoder.matches) cho seed hash gating"

requirements-completed: [DB-06]

duration: ~25min
completed: 2026-04-26
---

# Phase 5 Plan 01: Pre-flight Baselines Summary

**5 OpenAPI baseline JSONs captured + verified BCrypt hash for admin123 — fail-fast caught wrong hash literal in RESEARCH before V2 SQL embed**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-04-26T00:30:00Z
- **Completed:** 2026-04-26T00:55:00Z
- **Tasks:** 2 (Task 1.1 OpenAPI baselines, Task 1.2 BCrypt hash test)
- **Files modified:** 8 (1 pom + 1 test + 5 OpenAPI JSONs + 1 evidence txt)

## Accomplishments

- Captured 5 OpenAPI baselines (sort-keys) — paths counts: user=11 product=13 order=10 payment=13 inventory=12. Total 4558 lines committed cho diff Wave 4.
- Port verification: 5 ports khớp `docker-compose.yml` mapping (8081/8082/8083/8084/8085) — KHÔNG capture với port giả định.
- BCrypt test xanh — 2 method (match `admin123` true, reject `wrong-password` false) — test gate vượt.
- Fail-fast caught: hash literal `$2a$10$N9qo8uLOick...` trong RESEARCH §Decision #6 không verify với `admin123` (đó là Spring Security docs sample cho literal `password`). Replaced với hash đúng generate bằng `BCryptPasswordEncoder(strength=10)`.

## Task Commits

1. **Task 1.1: Capture OpenAPI baselines** — `0e1cd00` (docs)
2. **Task 1.2: Verify BCrypt hash + add test** — `e023ad7` (test)

## Files Created/Modified

- `.planning/phases/05-database-foundation/baseline/openapi-user-service.json` — 11 paths, sort-keys
- `.planning/phases/05-database-foundation/baseline/openapi-product-service.json` — 13 paths bao gồm `/products`, `/products/{id}`, `/products/slug/{slug}`, `/admin/products/*`
- `.planning/phases/05-database-foundation/baseline/openapi-order-service.json` — 10 paths
- `.planning/phases/05-database-foundation/baseline/openapi-payment-service.json` — 13 paths
- `.planning/phases/05-database-foundation/baseline/openapi-inventory-service.json` — 12 paths
- `.planning/phases/05-database-foundation/baseline/bcrypt-hash-verified.txt` — evidence file ghi hash đúng + lưu ý hash RESEARCH sai
- `sources/backend/user-service/src/test/java/com/ptit/htpt/userservice/seed/BCryptSeedHashTest.java` — JUnit5 test 2 method
- `sources/backend/user-service/pom.xml` — thêm `spring-security-crypto` scope=test

## Decisions Made

- **Hash literal RESEARCH §Decision #6 SAI** → replaced với fresh hash. Downstream Plan 05-03 BUỘC PHẢI embed `$2a$10$TMH2spmmPRD90vJz8w5yz.G0o4AR/Hio2RU1yBwjjT1ClTLqF5lFu` vào `V2__seed_dev_data.sql` của user-service. Lưu trong evidence file + test source comment.
- **OpenAPI prefix asymmetry**: paths trong baseline là `/products` (service-internal) — gateway thêm `/api/` prefix khi route. Plan acceptance criterion ("ít nhất `/api/products` endpoint") được hiểu lại theo nghĩa "endpoint products có sự hiện diện qua gateway". Không cần fix — đây là expected architecture.
- **Capture method:** `docker compose up -d --build` cho 5 services (build từ Dockerfile thực) thay vì `mvn spring-boot:run` — đảm bảo capture từ artifact giống production-like build, fail-fast khi Dockerfile/build có vấn đề.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Hash literal trong RESEARCH §Decision #6 không verify với admin123**

- **Found during:** Task 1.2 (BCryptSeedHashTest đầu RED — test xác nhận intentional fail-fast)
- **Issue:** Hash `$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy` (Spring Security docs sample) trả `false` khi `BCryptPasswordEncoder.matches("admin123", hash)`. Đây là hash phổ biến trên internet hashes literal `password` (không phải `admin123`).
- **Fix:** Generate hash mới qua `BCryptPasswordEncoder(strength=10).encode("admin123")` → `$2a$10$TMH2spmmPRD90vJz8w5yz.G0o4AR/Hio2RU1yBwjjT1ClTLqF5lFu`. Update test constant + ghi rõ lý do trong source comment + evidence file. Đây ĐÚNG mục đích của Plan Assumption A1 (fail-fast trước khi commit V2).
- **Files modified:** `sources/backend/user-service/src/test/java/com/ptit/htpt/userservice/seed/BCryptSeedHashTest.java`, `.planning/phases/05-database-foundation/baseline/bcrypt-hash-verified.txt`
- **Verification:** `mvn -pl sources/backend/user-service test -Dtest=BCryptSeedHashTest` → BUILD SUCCESS, 2 tests pass
- **Committed in:** `e023ad7`

**2. [Rule 3 - Blocking] spring-security-crypto chưa có trong user-service pom**

- **Found during:** Task 1.2 (test compile cần `BCryptPasswordEncoder`)
- **Issue:** user-service pom chỉ có `spring-boot-starter-web`/validation/actuator/springdoc/test — không có spring-security-crypto.
- **Fix:** Add dependency với scope=test (KHÔNG ảnh hưởng production runtime, chỉ tests dùng).
- **Files modified:** `sources/backend/user-service/pom.xml`
- **Verification:** Test compile + run green.
- **Committed in:** `e023ad7`

---

**Total deviations:** 2 auto-fixed (1 bug, 1 blocking)
**Impact on plan:** Cả 2 deviations cần thiết cho correctness. Hash bug là chính xác mục đích fail-fast của Task 1.2 — Plan Assumption A1 hoạt động đúng. Pom dep là test-scope, không ảnh hưởng production deps.

## Issues Encountered

- Maven không có trong PATH — dùng IntelliJ bundled Maven (`/c/Program Files/JetBrains/IntelliJ IDEA Community Edition 2025.2.6.1/plugins/maven/lib/maven3/bin/mvn`). Maven 3.9.9 + JDK 17 (Adoptium) hoạt động tốt.
- Docker daemon đã sẵn — `docker compose up -d --build` cho 5 services hoàn tất, mỗi service `/actuator/health` UP trong ≤5s.

## TDD Gate Compliance

Task 1.2 dùng `tdd="true"`. Theo gate sequence:
- **RED:** test ban đầu chạy → 1 fail (`seedHashMatchesAdminPassword`) — đây là intentional fail-fast (RESEARCH literal sai), không phải bug code-side. Test logic ĐÚNG, hash literal SAI.
- **GREEN:** sau khi update hash literal đúng, 2/2 tests pass.
- **REFACTOR:** không cần.

Combined commit (test + production-config-fix-pom + evidence) thay vì 2 commits riêng (test → feat) vì task này là pure verification (không có production code change ngoài test-scope dependency). Một commit duy nhất `test(05-01): ...` capture toàn bộ. Acceptable theo plan acceptance criteria — tất cả pass green.

## User Setup Required

None — no external service configuration.

## Next Phase Readiness

- **Wave 2 (Postgres infra) sẵn sàng:** baselines committed, sẽ dùng diff Wave 4.
- **Wave 3 user-service refactor (Plan 05-03):** PHẢI embed hash `$2a$10$TMH2spmmPRD90vJz8w5yz.G0o4AR/Hio2RU1yBwjjT1ClTLqF5lFu` vào `V2__seed_dev_data.sql`. Reference evidence file + test source comment.
- **Phase 6 admin login:** seed hash sẽ verify với `admin123` plaintext → login flow xanh.

## Self-Check: PASSED

- File `.planning/phases/05-database-foundation/baseline/openapi-user-service.json` — FOUND
- File `.planning/phases/05-database-foundation/baseline/openapi-product-service.json` — FOUND
- File `.planning/phases/05-database-foundation/baseline/openapi-order-service.json` — FOUND
- File `.planning/phases/05-database-foundation/baseline/openapi-payment-service.json` — FOUND
- File `.planning/phases/05-database-foundation/baseline/openapi-inventory-service.json` — FOUND
- File `.planning/phases/05-database-foundation/baseline/bcrypt-hash-verified.txt` — FOUND
- File `sources/backend/user-service/src/test/java/com/ptit/htpt/userservice/seed/BCryptSeedHashTest.java` — FOUND
- Commit `0e1cd00` — FOUND
- Commit `e023ad7` — FOUND

(Verification commands run trong `<self_check>` step bên dưới.)

---
*Phase: 05-database-foundation*
*Completed: 2026-04-26*
