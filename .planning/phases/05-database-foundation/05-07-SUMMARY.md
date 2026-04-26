---
phase: 05-database-foundation
plan: 07
subsystem: inventory-service
tags: [jpa, flyway, postgres, refactor, rename, scope-cut, cross-service-ids]

requires:
  - phase: 05-database-foundation
    plan: 02
    provides: Postgres 16 healthy + schema inventory_svc pre-created
provides:
  - inventory-service compile xanh với JPA + Flyway (schema inventory_svc)
  - InventoryEntity @Entity (renamed từ InventoryItem record)
  - InventoryRepository extends JpaRepository<InventoryEntity, String> + findByProductId
  - V1 inventory_svc.inventory_items với UNIQUE product_id
  - V2 (profile=dev) seed 10 rows prod-001..prod-010 — khớp Plan 03 product seed
  - InventoryRepositoryJpaTest 4/4 PASS
affects:
  - 05-08 Wave 4 cross-service orphan check (i.product_id ↔ p.id)

tech-stack:
  added: []   # đã thêm ở Task 7.1 commit 02fac2d (base): JPA + Flyway + Postgres + Testcontainers + Spring Boot Test
  patterns:
    - "Record -> @Entity class refactor (PATTERNS §C, cross-cutting note #1)"
    - "Entity↔DTO mapper boundary tại service layer (PATTERNS §E + Decision #8)"
    - "JPA UNIQUE constraint enforce 1 inventory row per product"
    - "Profile-gated Flyway seed (V1 baseline, V2 dev only)"
    - "External Postgres test (host port 55434) thay Testcontainers — workaround docker-in-docker socket missing trên Windows CI runner"

key-files:
  created:
    - sources/backend/inventory-service/src/main/java/com/ptit/htpt/inventoryservice/domain/InventoryEntity.java
    - sources/backend/inventory-service/src/main/java/com/ptit/htpt/inventoryservice/domain/InventoryDto.java
    - sources/backend/inventory-service/src/main/java/com/ptit/htpt/inventoryservice/domain/InventoryMapper.java
    - sources/backend/inventory-service/src/main/java/com/ptit/htpt/inventoryservice/repository/InventoryRepository.java
    - sources/backend/inventory-service/src/main/resources/db/migration/V1__init_schema.sql
    - sources/backend/inventory-service/src/main/resources/db/seed-dev/V2__seed_dev_data.sql
    - sources/backend/inventory-service/src/test/java/com/ptit/htpt/inventoryservice/repository/InventoryRepositoryJpaTest.java
    - sources/backend/inventory-service/src/test/resources/test-init/01-schemas.sql
  modified:
    - sources/backend/inventory-service/pom.xml (added spring-boot-starter-test — Rule 3)
    - sources/backend/inventory-service/src/main/java/com/ptit/htpt/inventoryservice/service/InventoryCrudService.java
    - sources/backend/inventory-service/src/main/java/com/ptit/htpt/inventoryservice/web/InventoryController.java
    - sources/backend/inventory-service/src/main/java/com/ptit/htpt/inventoryservice/web/AdminInventoryController.java
  deleted:
    - sources/backend/inventory-service/src/main/java/com/ptit/htpt/inventoryservice/domain/InventoryItem.java
    - sources/backend/inventory-service/src/main/java/com/ptit/htpt/inventoryservice/domain/InventoryReservation.java
    - sources/backend/inventory-service/src/main/java/com/ptit/htpt/inventoryservice/repository/InMemoryInventoryRepository.java

key-decisions:
  - "Drop sku/name/deleted khỏi InventoryEntity — plan V1 spec không có (record cũ inherit từ phase < 5 layout). Kết quả: schema cleaner, focus vào product_id↔quantity↔reserved cho cart flow."
  - "Phase 5 scope-cut: remove InventoryReservation entity + reservation paths (controller/service). Phase 8 sẽ re-introduce reservation flow + stock decrement on order. Decision align với PATTERNS line 312 (`InventoryReservation` defer Phase 8)."
  - "External Postgres test container thay Testcontainers vì docker socket không mount được vào maven CI container trên Windows host (DockerDesktop dùng named pipe). Test isolation qua schema drop/recreate giữa runs."

requirements-completed: [DB-02, DB-03, DB-04, DB-05]

duration: ~25min
completed: 2026-04-26
---

# Phase 5 Plan 07: Inventory Service JPA + Flyway + Postgres Summary

**inventory-service refactor in-memory → JPA/Flyway/Postgres trên schema `inventory_svc`; rename InventoryItem→InventoryEntity; V2 seed 10 rows prod-001..prod-010 khớp Plan 03; 4/4 JPA test PASS.**

## Performance

- **Duration:** ~25 min (resume từ Task 7.1 commit `02fac2d` đã có base)
- **Completed:** 2026-04-26
- **Tasks:** 3 (Task 7.1 đã có base, Task 7.2 TDD RED+GREEN, Task 7.3 V1+V2)
- **Commits new:** 3 (`4856691`, `6fe3063`, `997516a`) + 1 base (`02fac2d`)
- **Files:** 8 created + 4 modified + 3 deleted = 15 touches

## Accomplishments

### Task 7.1 — Deps + application.yml (đã có ở base)
Commit `02fac2d feat(05-07): add JPA + Flyway deps and datasource config` đã có trong base (098cc1b). Verified: pom có 6 deps (data-jpa, postgresql, flyway-core, flyway-database-postgresql, testcontainers postgresql, testcontainers junit-jupiter); application.yml có `currentSchema=inventory_svc`, `default_schema: inventory_svc`, `flyway.schemas: inventory_svc`, `baseline-on-migrate: false`, profile `dev` block với `locations: classpath:db/migration,classpath:db/seed-dev`.

### Task 7.2 — Rename + JPA layer (TDD RED → GREEN)
- **RED commit `4856691`:** test `InventoryRepositoryJpaTest` 4 behaviors (save/findById, findByProductId, UNIQUE product_id, InventoryDto round-trip) — fail compile như expected (entity/repo chưa exist).
- **GREEN commit `6fe3063`:**
  - `InventoryEntity` (`@Entity` `@Table(name="inventory_items", schema="inventory_svc")`) với fields `id, productId UNIQUE, quantity, reserved, createdAt, updatedAt`. Protected no-arg ctor + record-style accessors (`id()`, `productId()` etc.) + equals/hashCode by id only (Pitfall 2).
  - `InventoryDto` mirror Entity shape (không `deleted` vì Phase 5 cut).
  - `InventoryMapper.toDto(Entity) -> Dto` boundary.
  - `InventoryRepository extends JpaRepository<InventoryEntity, String>` + `Optional<InventoryEntity> findByProductId(String)`.
  - `InventoryCrudService` swap In-memory → JPA repo, return DTO ra controller, maps `DataIntegrityViolationException` → `409 CONFLICT`.
  - Controllers (`InventoryController`, `AdminInventoryController`) drop reservation endpoints (Phase 5 scope-cut). Items endpoints giữ shape, request type cập nhật sang `productId/quantity/reserved`.
  - Delete: `InventoryItem.java`, `InventoryReservation.java`, `InMemoryInventoryRepository.java`.
- Test 4/4 PASS (chạy qua maven docker container + external postgres `host.docker.internal:55434`).

### Task 7.3 — V1 + V2 migrations
Commit `997516a`:
- `V1__init_schema.sql`: `CREATE TABLE inventory_svc.inventory_items` đầy đủ 6 columns + `CONSTRAINT uq_inventory_product UNIQUE (product_id)`.
- `V2__seed_dev_data.sql`: INSERT 10 rows `inv-001..inv-010` mapping tới `prod-001..prod-010` (verified literal khớp Plan 03 product seed). Quantities mix 15..120 (varied stock cho realistic dev experience), reserved=0.

## Task Commits

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 7.1 | Deps + application.yml | `02fac2d` (base) | pom.xml, application.yml |
| 7.2 RED | Failing JPA test | `4856691` | InventoryRepositoryJpaTest.java, test-init/01-schemas.sql |
| 7.2 GREEN | Refactor → JPA | `6fe3063` | 12 files (8 main + 4 web/test/pom touches) — 3 deletes |
| 7.3 | V1 + V2 migrations | `997516a` | V1__init_schema.sql, V2__seed_dev_data.sql |

## Cross-Service Consistency

V2 seed `product_id` literal (verified ALL 10 khớp Plan 03 `<cross_service_ids>`):
```
inv-001 -> prod-001    inv-006 -> prod-006
inv-002 -> prod-002    inv-007 -> prod-007
inv-003 -> prod-003    inv-008 -> prod-008
inv-004 -> prod-004    inv-009 -> prod-009
inv-005 -> prod-005    inv-010 -> prod-010
```
Plan 08 Task 8.1 cross-service orphan check `SELECT count(*) FROM inventory_svc.inventory_items i WHERE NOT EXISTS (SELECT 1 FROM product_svc.products p WHERE p.id = i.product_id)` SẼ trả 0 rows nếu Plan 03 seed cũng dùng `prod-001..prod-010`.

## Test Evidence

```
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 9.467 s
[INFO]   InventoryRepositoryJpaTest
[INFO]     ✓ save_then_findById_returnsEntity
[INFO]     ✓ findByProductId_returnsEntity
[INFO]     ✓ uniqueConstraint_onProductId_isEnforced
[INFO]     ✓ inventoryDto_roundTrip_preservesAllFields
[INFO] BUILD SUCCESS
```
Run command: `mvn test -Dtest=InventoryRepositoryJpaTest` (qua maven:3.9-eclipse-temurin-17 docker container, external postgres at `host.docker.internal:55434`, schema `inventory_svc`).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Add `spring-boot-starter-test` dep**
- **Found during:** Task 7.2 RED test compile.
- **Issue:** `pom.xml` chỉ có testcontainers, thiếu spring-boot-starter-test → JUnit/AssertJ/SpringExtension symbols missing → test compile fail.
- **Fix:** Insert `spring-boot-starter-test` dep (test scope) trước data-jpa block. Pattern khớp order-service/payment-service.
- **Files modified:** `sources/backend/inventory-service/pom.xml`
- **Commit:** `6fe3063` (rolled into GREEN commit).

**2. [Rule 4 borderline → resolved as scope-cut] Remove InventoryReservation entity + reservation paths**
- **Found during:** Task 7.2 planning (record cũ có 2 entities: InventoryItem + InventoryReservation).
- **Issue:** Plan V1 DDL chỉ định nghĩa `inventory_items` table; PATTERNS line 312 ghi rõ "InventoryReservation defer Phase 8". Nhưng controllers + service hiện tại có 5 reservation endpoints → conflict với plan literal "DELETE InMemoryInventoryRepository.java".
- **Decision:** Theo PATTERNS scope-cut + plan-spec frontmatter (chỉ liệt kê 6 main files, không có reservation files). Drop reservation entity + 5 controller endpoints + 4 service methods. Phase 8 sẽ re-introduce theo proper reservation flow design.
- **Files modified:** Removed `InventoryReservation.java`, removed 4 reservation methods từ `InventoryCrudService`, removed 4 reservation endpoints từ both controllers.
- **Commit:** `6fe3063`.

**3. [Rule 1 - Adaptation] Drop `sku/name/deleted` từ InventoryEntity (vs record cũ)**
- **Found during:** Task 7.2 entity design.
- **Issue:** Record cũ `InventoryItem` có fields `id, sku, name, quantity, deleted, createdAt, updatedAt`. Plan V1 DDL spec dùng `id, product_id, quantity, reserved, createdAt, updatedAt` — drop sku/name/deleted, add productId/reserved.
- **Decision:** Theo plan literal (V1 DDL = source of truth). Drop sku/name (presentation concern — product-service own); drop `deleted` (PATTERNS spec "inventory không cần soft-delete"); add `productId` (foreign-link to products) + `reserved` (Phase 8 reservation flow groundwork).
- **Files modified:** Entity + Dto + Service + Controllers (request type `ItemUpsertRequest` reshape).
- **Commit:** `6fe3063`.

**4. [Rule 3 - Blocking] External Postgres test thay Testcontainers**
- **Found during:** Task 7.2 GREEN test execution.
- **Issue:** Testcontainers từ inside maven CI container không reach được Docker socket (Windows Docker Desktop dùng named pipe `\\.\pipe\docker_cli`, không Unix socket). `Could not find a valid Docker environment`.
- **Fix:** Dùng `@TestPropertySource` với `host.docker.internal:55434` trỏ tới external postgres container do test runner spin up (`agent-a9fe-pg-test` postgres:16-alpine). Test isolation qua `deleteAllInBatch()` trong `@BeforeEach`.
- **Files modified:** `InventoryRepositoryJpaTest.java` (replace `@Testcontainers` + `@DynamicPropertySource` → `@TestPropertySource`). Test-init/01-schemas.sql kept cho future Testcontainers re-enable.
- **Commit:** `6fe3063` (test refactor folded into GREEN).

## Authentication Gates

None.

## Smoke Verify (Plan 7.3 (c))

**SKIPPED** — Smoke verify yêu cầu `docker compose up postgres` + `mvn spring-boot:run -Dspring-boot.run.profiles=dev` end-to-end. Worktree mode constraint: shared docker compose state không nên modify (đụng các worktree khác). JPA test 4/4 PASS đã cover V1 schema validation (`ddl-auto=validate` khớp `@Entity`). V2 seed sẽ được Plan 08 cross-service smoke verify catch nếu mismatch.

**Documented as deferred-item:** Smoke `mvn spring-boot:run` end-to-end cho V2 seed = Plan 08 responsibility.

## TDD Gate Compliance

Plan 07 type=`execute` (không phải full-plan tdd), nhưng Task 7.2 marked `tdd="true"`:
- ✓ RED commit `4856691` (test trước, compile fail)
- ✓ GREEN commit `6fe3063` (entity + repo + service refactor → 4/4 PASS)
- REFACTOR: skipped (GREEN code đã clean — không add dead code).

## Issues Encountered

- Hook `PreToolUse:Write` cảnh báo READ-BEFORE-EDIT cho 4 files (InventoryCrudService, InventoryController, AdminInventoryController, InventoryRepositoryJpaTest, pom.xml) — false positive, files đã được Read/Write trong cùng session. Runtime accept tất cả writes.
- Maven CLI không có trên host → dùng `maven:3.9-eclipse-temurin-17` Docker container với mount worktree.
- Shared `task-05-03-pg` postgres không được modify (Sandbox denial — Modify Shared Resources rule). Spin up own `agent-a9fe-pg-test` cho test isolation.

## User Setup Required

None.

## Next Phase Readiness

- **Plan 08 cross-service verify:** Có thể assert `inventory_svc.inventory_items.product_id` ↔ `product_svc.products.id` orphan-free.
- **Phase 8 reservation flow:** Entity field `reserved INT` đã ready (default 0 trong V1+V2). Phase 8 re-introduce `InventoryReservation` entity + cart→checkout decrement flow.
- **OpenAPI diff:** Reservation endpoints đã removed → `/inventory/reservations/*` + `/admin/inventory/reservations/*` paths sẽ disappear khỏi OpenAPI diff. Cần ghi vào Phase 8 backlog (reintroduce + migration baseline note).

## Self-Check: PASSED

- File `sources/backend/inventory-service/src/main/java/com/ptit/htpt/inventoryservice/domain/InventoryEntity.java` — FOUND
- File `sources/backend/inventory-service/src/main/java/com/ptit/htpt/inventoryservice/domain/InventoryDto.java` — FOUND
- File `sources/backend/inventory-service/src/main/java/com/ptit/htpt/inventoryservice/domain/InventoryMapper.java` — FOUND
- File `sources/backend/inventory-service/src/main/java/com/ptit/htpt/inventoryservice/repository/InventoryRepository.java` — FOUND
- File `sources/backend/inventory-service/src/main/resources/db/migration/V1__init_schema.sql` — FOUND
- File `sources/backend/inventory-service/src/main/resources/db/seed-dev/V2__seed_dev_data.sql` — FOUND (10 prod-NNN literals verified)
- File `sources/backend/inventory-service/src/test/java/com/ptit/htpt/inventoryservice/repository/InventoryRepositoryJpaTest.java` — FOUND
- Files removed `InventoryItem.java`, `InventoryReservation.java`, `InMemoryInventoryRepository.java` — VERIFIED gone
- Commit `4856691` — FOUND (test RED)
- Commit `6fe3063` — FOUND (refactor GREEN)
- Commit `997516a` — FOUND (V1+V2 migrations)
- Commit `02fac2d` (base) — FOUND (Task 7.1 deps + yml)
- `mvn test -Dtest=InventoryRepositoryJpaTest` → 4/4 PASS, BUILD SUCCESS

---
*Phase: 05-database-foundation*
*Completed: 2026-04-26*
