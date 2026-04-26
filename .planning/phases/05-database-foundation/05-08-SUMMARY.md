---
phase: 05-database-foundation
plan: 08
subsystem: integration-smoke
tags: [integration, docker-compose, flyway, smoke-test, gateway, openapi-diff]

requires:
  - phase: 05-database-foundation
    plan: 03
    provides: product-service JPA + V1+V2 seed (prod-001..prod-010)
  - phase: 05-database-foundation
    plan: 04
    provides: user-service JPA + V1+V2 seed (admin + demo_user)
  - phase: 05-database-foundation
    plan: 05
    provides: order-service JPA + V1+V2 seed (2 orders referencing demo_user)
  - phase: 05-database-foundation
    plan: 06
    provides: payment-service JPA + V1 schema
  - phase: 05-database-foundation
    plan: 07
    provides: inventory-service JPA + V1+V2 seed (10 items referencing prod-001..prod-010)

provides:
  - Integration smoke evidence file at baseline/integration-evidence.md
  - Post-refactor OpenAPI captured (5 services) at baseline/openapi-*-service-post.json
  - Stack verified: postgres + 5 backend services + api-gateway all UP
  - Flyway V1+V2 confirmed applied across all 5 schemas
  - Cross-service ID consistency confirmed (0 orphan rows)
  - Gateway round-trip GET /api/products → 10 seeded products from Postgres
  - No entity field leak (deleted not in gateway payload)
  - Wave 5 (mock-data deletion) UNBLOCKED

affects:
  - 05-09 Wave 5 FE rewire (backend ground truth confirmed ready)

tech-stack:
  added: []
  patterns:
    - "Docker Compose full-stack smoke: postgres (healthy) → 5 services → api-gateway → actuator/health"
    - "Flyway multi-schema history verification per schema"
    - "Cross-schema orphan-row check via NOT EXISTS cross-schema JOIN"
    - "Spring Cloud Gateway two-route-per-service pattern (base + wildcard) to avoid trailing-slash ambiguity"

key-files:
  created:
    - .planning/phases/05-database-foundation/baseline/integration-evidence.md
    - .planning/phases/05-database-foundation/baseline/openapi-user-service-post.json
    - .planning/phases/05-database-foundation/baseline/openapi-product-service-post.json
    - .planning/phases/05-database-foundation/baseline/openapi-order-service-post.json
    - .planning/phases/05-database-foundation/baseline/openapi-payment-service-post.json
    - .planning/phases/05-database-foundation/baseline/openapi-inventory-service-post.json
  modified:
    - sources/backend/api-gateway/src/main/resources/application.yml (RewritePath bug fix)
    - sources/backend/payment-service/src/test/java/com/ptit/htpt/paymentservice/repository/PaymentTransactionRepositoryJpaTest.java (@ServiceConnection removal)

key-decisions:
  - "Gateway two-route-per-service pattern: split each route into -base (exact) + /** (wildcard) to correctly handle /api/products (no trailing slash) → /products. Avoids RewritePath trailing-slash ambiguity."
  - "OpenAPI drift documented, not blocked: 4 services have expected drift pre-documented in Plans 03/04/06/07 SUMMARYs. order-service = 0 diff (clean baseline preserved). Wave 5 proceeds."
  - "Cross-service orphan check = 0: confirms Plans 03+04+05+07 used consistent canonical IDs (admin=...001, demo_user=...002, prod-001..prod-010)."

requirements-completed: [DB-06]

duration: ~14 min
completed: 2026-04-26
---

# Phase 5 Plan 08: Integration Smoke Test Summary

**Toàn stack docker compose lên green; Flyway V1+V2 applied; 10 seeded products qua gateway round-trip; cross-service orphan check = 0; OpenAPI diff documented; Wave 5 UNBLOCKED.**

## Performance

- **Duration:** ~14 min
- **Completed:** 2026-04-26
- **Tasks:** 2 auto + 1 checkpoint (auto-approved)
- **Commits:** 2 (`69c8884`, `4a6afe1`)
- **Files:** 6 created + 2 modified = 8 touches

## Accomplishments

### Task 8.1 — Reset stack + boot + Flyway verify + row counts + orphan check

Commit `69c8884`:

- `docker compose down -v` + `docker compose build` (5 backend services) + `docker compose up -d`
- **Actuator health check:** user/product/order/payment/inventory → `{"status":"UP"}` — tất cả 5 UP
- **Flyway history:**
  - `user_svc`, `product_svc`, `order_svc`, `inventory_svc`: V1 (init) + V2 (seed dev data), success=t
  - `payment_svc`: V1 (init only), success=t — expected (payment có no seed data)
- **Row counts:**
  ```
  users=2 ✓  categories=5 ✓  products=10 ✓
  orders=2 ✓  payments=0 ✓  inventory_items=10 ✓
  ```
- **Cross-service orphan check:**
  - `orphan_orders = 0` ✓ (orders.user_id ⊆ users.id)
  - `orphan_inventory = 0` ✓ (inventory.product_id ⊆ products.id)

### Task 8.2 — Gateway round-trip + OpenAPI diff

Commit `4a6afe1`:

- **Gateway `GET /api/products`:** HTTP 200, 10 products
- **DB ground-truth match:** Python-sorted names from DB và gateway response — diff = 0
- **No deleted leak:** `deleted` field không xuất hiện trong gateway payload
- **OpenAPI diff:**

| Service | Diff Lines | Status |
|---------|-----------|--------|
| order-service | 0 | ✓ UNCHANGED |
| product-service | 11 | ⚠ expected (Plan 03: slug→parentId/status) |
| user-service | 326 | ⚠ expected (Plan 04: legacy endpoints removed) |
| payment-service | 8 | ⚠ expected (Plan 06: additive amount/method fields) |
| inventory-service | 385 | ⚠ expected (Plan 07: InventoryReservation removed, productId/reserved restructure) |

### Task 8.3 — Manual sign-off (auto-approved in auto mode)

Checkpoint auto-approved: evidence file đầy đủ, stack green, gateway verified.

## Task Commits

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 8.1 | Stack + Flyway + rows + orphan + evidence | `69c8884` | PaymentTest.java, api-gateway/application.yml, integration-evidence.md |
| 8.2 | Gateway + OpenAPI post capture | `4a6afe1` | 5 × openapi-*-post.json |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] api-gateway RewritePath broken — trailing slash + $PATH env var conflict**
- **Found during:** Task 8.1 (a) stack boot + actuator check, then Task 8.2 (a) gateway round-trip
- **Issue (bug 1):** `RewritePath=/api/products/(?<seg>.*), /${seg}` — khi request là `/api/products` (không có trailing slash), `(?<seg>.*)` capture chuỗi rỗng → path downstream thành `/` → product-service throws `NoResourceFoundException: No static resource .`
- **Issue (bug 2):** Renamed capture group to `path` → `RewritePath=/api/(?<path>.*), /${path}` — YAML variable expansion resolves `${path}` as shell `$PATH` environment variable (`/opt/java/openjdk/bin:/usr/local/sbin:...`) → path forwarded as literal env string → 500.
- **Fix:** Two-route-per-service pattern: separate `-base` route for exact match + `/**` route for sub-paths, each with explicit prefix in replacement and `seg` capture group name:
  ```yaml
  - id: product-service-base
    predicates: [Path=/api/products]
    filters: [RewritePath=/api/products, /products]
  - id: product-service
    predicates: [Path=/api/products/**]
    filters: [RewritePath=/api/products/(?<seg>.*), /products/${seg}]
  ```
- **Files:** `sources/backend/api-gateway/src/main/resources/application.yml`
- **Commit:** `69c8884`

**2. [Rule 1 - Bug] payment-service test @ServiceConnection compile error**
- **Found during:** Task 8.1 (a) `docker compose build` — payment-service build fail
- **Issue:** `@ServiceConnection` annotation requires `spring-boot-testcontainers` dep (separate from `spring-boot-starter-test`), which was not in payment-service pom.xml. Build error: `cannot find symbol: class ServiceConnection`.
- **Fix:** Remove `@ServiceConnection` annotation; add `spring.datasource.url/username/password` wiring to existing `@DynamicPropertySource` method (standard Testcontainers pattern, already used in other services).
- **Files:** `sources/backend/payment-service/src/test/java/.../PaymentTransactionRepositoryJpaTest.java`
- **Commit:** `69c8884`

## OpenAPI Drift Analysis

### order-service: 0 diff ✓
Schema contract fully preserved. FE codegen pipeline safe.

### product-service: 11 diff lines (expected)
`CategoryUpsertRequest` fields changed: `parentId + status` → `slug`. Pre-documented in Plan 03 SUMMARY key-decisions: "CategoryEntity drop parentId/status field — Wave 4 verifier sẽ flag, planner đã accept." FE categories create/update page uses this request — minimal UI impact (field rename, not removal of functional data).

### user-service: 326 diff lines (expected)
`AddressUpsertRequest` + `ProfileUpsertRequest` schemas removed. Plan 04 rewrote user-service, removing legacy UserProfile + UserAddress + block-unblock endpoints (v1.0 residual). These paths were NOT in FE API contract (FE uses `/auth/*` + `/users/me` only). Zero FE impact.

### payment-service: 8 diff lines (expected, additive)
`SessionUpsertRequest` response schema added `amount` + `method` fields. Additive only — existing FE callers unaffected.

### inventory-service: 385 diff lines (expected)
`ItemUpsertRequest` restructured: `name/sku` → `productId/reserved`. `InventoryReservation` + 5 reservation endpoints removed (PATTERNS §Phase 8 scope-cut, Plan 07 pre-documented). Phase 8 will re-introduce proper reservation flow. FE does not call inventory reservation endpoints in current Phase 5 scope.

## Cross-Service ID Verification

All canonical IDs consistent across 5 services:
- `admin_user_id = 00000000-0000-0000-0000-000000000001` — seeded in user_svc
- `demo_user_id  = 00000000-0000-0000-0000-000000000002` — seeded in user_svc; referenced in order_svc.orders
- `prod-001..prod-010` — seeded in product_svc; referenced in inventory_svc.inventory_items

## Known Stubs

None — evidence file records real DB data, real Flyway migrations, real gateway responses.

## Threat Flags

None — Plan 08 is read-only smoke test. No new endpoints, no new auth paths, no schema changes.

## Self-Check

- File `integration-evidence.md` — FOUND ✓
- File `openapi-product-service-post.json` — FOUND ✓ (24702 bytes)
- File `openapi-user-service-post.json` — FOUND ✓ (12326 bytes)
- File `openapi-order-service-post.json` — FOUND ✓ (20197 bytes)
- File `openapi-payment-service-post.json` — FOUND ✓ (23036 bytes)
- File `openapi-inventory-service-post.json` — FOUND ✓ (12746 bytes)
- Commit `69c8884` — FOUND ✓
- Commit `4a6afe1` — FOUND ✓
- Gateway `GET /api/products` → 10 products — VERIFIED ✓
- Flyway V1+V2 all schemas — VERIFIED ✓
- Row counts (2/5/10/2/0/10) — VERIFIED ✓
- Orphan rows (0,0) — VERIFIED ✓

## Self-Check: PASSED

---
*Phase: 05-database-foundation*
*Completed: 2026-04-26*
