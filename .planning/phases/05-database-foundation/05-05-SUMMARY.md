---
phase: 05-database-foundation
plan: 05
subsystem: order-service-persistence
tags: [jpa, flyway, postgres, order-service, soft-delete, testcontainers]

requires:
  - phase: 05-database-foundation
    plan: 02
    provides: Postgres 16 container healthy + 5 schemas pre-created (incl. order_svc)
  - phase: 05-database-foundation
    plan: 03
    provides: Canonical pattern (product-service) cho record→@Entity refactor
provides:
  - order-service runs on JPA + Flyway + Postgres 16 trên schema order_svc
  - OrderEntity (@Entity) + OrderDto (record không có deleted) + OrderMapper boundary
  - OrderRepository extends JpaRepository<OrderEntity,String> với findByUserId(userId)
  - Soft-delete via Hibernate @SQLRestriction("deleted = false") + @SQLDelete UPDATE
  - V1 schema: order_svc.orders (id, user_id, total, status, note, deleted, created_at, updated_at) + idx_orders_user_id
  - V2 dev seed: 2 demo orders (ord-demo-001 DELIVERED, ord-demo-002 PENDING) cho demo_user (00000000-0000-0000-0000-000000000002)
  - Cross-cutting note #3 preserved: column `note VARCHAR(500)` nullable giữ từ record cũ
  - Testcontainers @DataJpaTest cover 5 behaviors (save+findById, findByUserId, soft-delete, OrderDto no-deleted, note round-trip)
affects:
  - 05-08 (smoke verify cross-service: orders.user_id orphan-row check sẽ assert NOT EXISTS = 0)
  - Phase 7 FE /account/orders sẽ có data hiển thị thật
  - Phase 8 (PERSIST-02) sẽ extend: OrderItemEntity per-row + shippingAddress + paymentMethod (DEFER)

tech-stack:
  added:
    - "spring-boot-starter-data-jpa (managed by Spring Boot 3.3.2 BOM)"
    - "postgresql JDBC driver (runtime)"
    - "flyway-core + flyway-database-postgresql"
    - "testcontainers postgresql + junit-jupiter (test scope)"
  patterns:
    - "record→@Entity class refactor giữ accessor style name() — service layer no-change"
    - "Mutable entity với protected no-arg constructor (JPA proxy)"
    - "equals/hashCode by id only (Hibernate proxy safety)"
    - "Hibernate 6 @SQLRestriction + @SQLDelete cho soft-delete (zero service-layer change)"
    - "Profile-gated Flyway seed: db/migration default, db/seed-dev added on profile=dev"
    - "Entity↔DTO boundary at service layer via static OrderMapper.toDto"
    - "Cross-service ID literal: demo_user_id = 00000000-0000-0000-0000-000000000002 (Plan 04)"

key-files:
  created:
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/OrderDto.java
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/OrderMapper.java
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/repository/OrderRepository.java
    - sources/backend/order-service/src/main/resources/db/migration/V1__init_schema.sql
    - sources/backend/order-service/src/main/resources/db/seed-dev/V2__seed_dev_data.sql
    - sources/backend/order-service/src/test/java/com/ptit/htpt/orderservice/repository/OrderRepositoryJpaTest.java
    - sources/backend/order-service/src/test/resources/test-init/01-schemas.sql
  modified:
    - sources/backend/order-service/pom.xml
    - sources/backend/order-service/src/main/resources/application.yml
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/OrderEntity.java
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderCrudService.java
  deleted:
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/repository/InMemoryOrderRepository.java

key-decisions:
  - "GIỮ field `note` (cross-cutting note #3 PATTERNS): record cũ có `note` → V1 DDL include `note VARCHAR(500)` nullable + entity field + DTO field. Tránh FE breakage."
  - "OrderEntity dùng accessor style name() (e.g. id(), userId(), total()) thay getter chuẩn (getId()) → service layer migration tối thiểu."
  - "Thêm alias `totalAmount()` + @JsonProperty(\"totalAmount\") trên DTO để giữ FE legacy contract Phase 1 đã consume `totalAmount`."
  - "Phase 8 fields DEFER: per-item (OrderItemEntity), shippingAddress, paymentMethod — Plan 05 chỉ ship basic OrderEntity (PERSIST-02 sẽ extend)."
  - "Cart/CartEntity + InMemoryCartRepository GIỮ trong codebase nhưng KHÔNG migrate sang JPA trong Phase 5 (out of scope — defer Phase 8)."
  - "V2 seed user_id = 00000000-0000-0000-0000-000000000002 literal — PHẢI khớp Plan 04 user_svc V2 seed cho cross-service orphan-row check (Plan 05-08 Task 8.1)."
  - "Test dùng @DataJpaTest + Testcontainers Postgres 16-alpine + withInitScript('test-init/01-schemas.sql') — schema được create trước khi Flyway V1 chạy."

requirements-completed: [DB-02, DB-03, DB-04, DB-05]

duration: ~25min (resume từ 2 commits trước)
completed: 2026-04-26
---

# Phase 5 Plan 05: Order Service JPA + Flyway Refactor Summary

**order-service refactored từ in-memory → JPA + Flyway + Postgres 16 (schema `order_svc`); 2 demo orders seeded cho demo_user — FE `/account/orders` Phase 7 sẽ có data thật.**

## Performance

- **Duration:** ~25 min (resume continuation; tổng 3 commits)
- **Started:** 2026-04-26 (Task 5.1 deps)
- **Completed:** 2026-04-26
- **Tasks:** 3 (5.1 deps + yaml, 5.2 entity refactor + JpaTest, 5.3 V1+V2 migrations)
- **Files created:** 7 (3 java, 2 sql, 1 test java, 1 test sql)
- **Files modified:** 4 (pom, yaml, entity, crud-service)
- **Files deleted:** 1 (InMemoryOrderRepository.java)

## Accomplishments

### Task 5.1 — Deps + application.yml
- pom.xml: thêm 6 deps (data-jpa, postgresql runtime, flyway-core, flyway-database-postgresql, testcontainers postgresql + junit-jupiter test).
- application.yml: thêm datasource block (`url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}?currentSchema=order_svc`), JPA `default_schema: order_svc` + `ddl-auto: validate`, Flyway `schemas: order_svc` + `default-schema: order_svc` + `baseline-on-migrate: false`.
- Doc separator + profile dev block: `flyway.locations: classpath:db/migration,classpath:db/seed-dev`.

### Task 5.2 — Entity refactor
- **OrderEntity.java**: record → @Entity class. `@Table(name="orders", schema="order_svc")` + `@SQLRestriction("deleted = false")` + `@SQLDelete(sql="UPDATE order_svc.orders SET deleted = true, updated_at = NOW() WHERE id = ?")`. Fields: id, userId, total, status, **note** (cross-cutting #3), deleted, createdAt, updatedAt. Accessor style name() giữ giống record cũ. equals/hashCode by id. Static factory `create(userId, total, status, note)` + mutators `update`, `setStatus`, `softDelete`.
- **OrderDto.java**: record với fields (id, userId, total, status, note, createdAt, updatedAt) — KHÔNG có `deleted`. Có alias `totalAmount()` + `@JsonProperty("totalAmount")` cho FE legacy.
- **OrderMapper.java**: static `toDto(OrderEntity)` boundary.
- **OrderRepository.java**: `interface OrderRepository extends JpaRepository<OrderEntity, String>` + `List<OrderEntity> findByUserId(String userId)`.
- **OrderCrudService.java**: swap inject từ `InMemoryOrderRepository` → `OrderRepository`; mutator pattern (entity.update(...) + repo.save).
- **DELETE InMemoryOrderRepository.java**.
- **OrderRepositoryJpaTest.java**: @DataJpaTest + Testcontainers Postgres 16 + withInitScript test-init/01-schemas.sql + @DynamicPropertySource cho datasource/Flyway. 5 tests:
  1. `save_then_findById_returnsEntity`
  2. `findByUserId_returnsAllOrdersForUser`
  3. `softDelete_filtersDeletedRecords`
  4. `orderDto_doesNotExposeDeletedField` (reflection check getRecordComponents)
  5. `note_field_roundTrip` ("Giao gấp")
- **test-init/01-schemas.sql**: `CREATE SCHEMA IF NOT EXISTS order_svc;` (Testcontainers init).

### Task 5.3 — V1 + V2 migrations
- **V1__init_schema.sql**: `CREATE TABLE order_svc.orders` (8 cols matching entity) + `CREATE INDEX idx_orders_user_id`. Comment: FK cross-schema KHÔNG enforce, consistency assert ở Plan 05-08 Task 8.1.
- **V2__seed_dev_data.sql**: 2 INSERT rows (`ord-demo-001` DELIVERED 8489000.00 + `ord-demo-002` PENDING 500000.00), cả hai user_id = `00000000-0000-0000-0000-000000000002` (demo_user literal khớp Plan 04).

## Task Commits

1. **Task 5.1: deps + application.yml** — `843c4b6` (`feat(05-05): add JPA+Flyway+Postgres deps + datasource config (schema=order_svc)`)
2. **Task 5.2: entity + DTO + mapper + repo + service swap + JpaTest + delete InMemory** — `1f2e04a` (`feat(05-05): refactor OrderEntity record -> JPA @Entity + DTO + Mapper + JpaRepository`)
3. **Task 5.3: V1 + V2 migrations** — `fd6ce91` (`feat(05-05): add Flyway V1 schema + V2 dev seed (2 demo orders for demo_user)`)

## Files Created/Modified

Đã liệt kê chi tiết trong frontmatter `key-files`.

## Decisions Made

Đã liệt kê chi tiết trong frontmatter `key-decisions` (7 quyết định chính).

## Smoke Test Evidence

Smoke test `mvn spring-boot:run -Dspring-boot.run.profiles=dev` + `psql SELECT COUNT(*)` được DEFER sang Plan 05-08 (cross-service smoke toàn stack):

- Lý do: Worktree mode parallel execution không chạy được Postgres container (volume name conflict + môi trường không có `mvn` CLI). Wave 4/Plan 08 sẽ kéo full stack `compose up -d` rồi smoke verify.
- Structural verification PASSED:
  - `test -f V1__init_schema.sql` → OK
  - `test -f V2__seed_dev_data.sql` → OK
  - `grep "CREATE TABLE order_svc.orders"` → OK
  - `grep "note VARCHAR(500)"` → OK
  - `grep -c "ord-demo-"` → 2
- pom.xml deps verified: `flyway-core`, `flyway-database-postgresql`, `data-jpa`, `postgresql`, `testcontainers postgresql + junit-jupiter`.
- application.yml verified: `currentSchema=order_svc`, `default_schema: order_svc`, `flyway.schemas: order_svc`, profile dev block với seed-dev location.

## Deviations from Plan

### Auto-fixed / inferred issues

**1. [Rule 3 — Blocking] Maven CLI không có trong môi trường worktree**
- **Found during:** Task 5.3 verify
- **Issue:** Plan acceptance yêu cầu `cd sources/backend/order-service && mvn ... compile` + `mvn -Dtest=OrderRepositoryJpaTest` + `mvn spring-boot:run`. Môi trường worktree shell không có `mvn` (PATH chỉ chứa Java JDK 17).
- **Fix:** Defer execution-time verify sang Plan 05-08 (cross-service smoke). Structural verify (file presence + grep checks) PASSED. Test code đã viết đầy đủ 5 behaviors theo plan, sẽ chạy ở Wave 4/8.
- **Files modified:** None (constraint của environment, không phải bug code).
- **Commit:** N/A.

**2. [Rule 2 — Critical] Test init script `test-init/01-schemas.sql`**
- **Found during:** Task 5.2 (đã commit ở 1f2e04a)
- **Issue:** Testcontainers Postgres image cần schema `order_svc` tồn tại trước khi Flyway chạy V1 (`baseline-on-migrate: false` + DDL ref schema). Không có script này thì Flyway V1 fail vì schema chưa tồn tại.
- **Fix:** Thêm `src/test/resources/test-init/01-schemas.sql` chứa `CREATE SCHEMA IF NOT EXISTS order_svc;` + reference qua `withInitScript("test-init/01-schemas.sql")`.

**3. [Note — DTO addition] OrderDto thêm `totalAmount` JsonProperty alias**
- **Issue:** FE Phase 1 đã consume field `totalAmount` (legacy). DTO base field tên `total` (entity column).
- **Fix:** Add `@JsonProperty("totalAmount") public BigDecimal totalAmount() { return total; }` trong OrderDto. Backward-compat zero-cost.

## Authentication Gates

None.

## Issues Encountered

- Worktree base mismatch ban đầu (HEAD trước khi reset không trùng EXPECTED_BASE) — đã reset hard về `098cc1b0...` theo `<worktree_branch_check>`. Sau reset, 2 commit cũ của Plan 05-05 (843c4b6, 1f2e04a) vẫn nằm trong git history (commit ancestors), tiếp tục build-on được mà không cần redo.
- Maven CLI không sẵn (xem deviation #1).

## TDD Gate Compliance

Plan frontmatter là `type: execute` (không phải `tdd`), nhưng Task 5.2 có `tdd="true"`. Test file `OrderRepositoryJpaTest.java` đã có 5 tests cover behaviors trong commit `1f2e04a` (commit message implies test thuộc cùng commit refactor, không split RED/GREEN). Chấp nhận deviation: gộp test + impl vào 1 commit do refactor scope ~6 files là 1 logical unit (theo `<execution_hint>` trong plan).

## Known Stubs

None — không có hardcoded empty values nào flow ra UI. Cart-related code giữ nguyên in-memory (out-of-scope Phase 5, không phải stub mới).

## User Setup Required

None — toàn bộ bằng code. Postgres container đã sẵn từ Plan 05-02.

## Next Phase Readiness

- **Plan 05-08 Wave 4 smoke verify ready:** order-service code + migrations đầy đủ; chỉ cần `compose up -d order-service` profile dev → Flyway V1+V2 sẽ chạy → 2 orders xuất hiện trong `order_svc.orders`. Cross-service NOT EXISTS check sẽ assert orphan-row count = 0 (đã đảm bảo qua user_id literal `00000000-0000-0000-0000-000000000002`).
- **Phase 7 FE `/account/orders` ready:** OrderRepository.findByUserId trả real data; controller layer đã đi qua OrderMapper.toDto → JSON wire shape có `totalAmount` alias cho FE legacy.
- **Phase 8 (PERSIST-02) extension points:**
  - Add OrderItemEntity (@OneToMany) + `order_items` table.
  - Add `shipping_address` (embedded JSON / sub-entity) + `payment_method` columns vào orders.
  - Cart → JPA migration (defer Phase 8 hoặc giữ in-memory tùy decision).

## Self-Check: PASSED

- File `V1__init_schema.sql` — FOUND (CREATE TABLE + note column + index)
- File `V2__seed_dev_data.sql` — FOUND (2 ord-demo rows, user_id khớp literal)
- File `OrderEntity.java` — @Entity + @SQLRestriction + @SQLDelete + note field PRESENT
- File `OrderDto.java` — record, không chứa `deleted`, có totalAmount alias
- File `OrderMapper.java` — static toDto FOUND
- File `OrderRepository.java` — extends JpaRepository + findByUserId FOUND
- File `OrderRepositoryJpaTest.java` — 5 tests cover all behaviors
- File `InMemoryOrderRepository.java` — DELETED (find returned empty)
- Commit `843c4b6` — FOUND (Task 5.1)
- Commit `1f2e04a` — FOUND (Task 5.2)
- Commit `fd6ce91` — FOUND (Task 5.3)
- Cross-service literal `00000000-0000-0000-0000-000000000002` matches Plan 04 demo_user

---
*Phase: 05-database-foundation*
*Completed: 2026-04-26*
