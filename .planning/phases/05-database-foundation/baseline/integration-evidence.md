# Integration Evidence — Phase 5 Plan 08

**Date:** 2026-04-26
**Executor:** claude-sonnet-4-6 (Plan 08 sequential agent)

---

## Assertion 1: Stack Up (docker compose ps)

**Command:** `docker compose up -d` + `docker compose ps`

```
NAME                                  IMAGE                               STATUS
tmdt-use-gsd-api-gateway-1            tmdt-use-gsd-api-gateway            Up
tmdt-use-gsd-inventory-service-1      tmdt-use-gsd-inventory-service      Up
tmdt-use-gsd-notification-service-1   tmdt-use-gsd-notification-service   Up
tmdt-use-gsd-order-service-1          tmdt-use-gsd-order-service          Up
tmdt-use-gsd-payment-service-1        tmdt-use-gsd-payment-service        Up
tmdt-use-gsd-postgres-1               postgres:16-alpine                   Up (healthy)
tmdt-use-gsd-product-service-1        tmdt-use-gsd-product-service        Up
tmdt-use-gsd-user-service-1           tmdt-use-gsd-user-service           Up
```

**Actuator health check:**
```
user-service    (8081): {"status":"UP"}
product-service (8082): {"status":"UP"}
order-service   (8083): {"status":"UP"}
payment-service (8084): {"status":"UP"}
inventory-service (8085): {"status":"UP"}
```

**RESULT: PASS** — postgres + 5 backend services + api-gateway UP.
Note: frontend container failed to start (port 3000 occupied by host Next.js dev server) — không ảnh hưởng backend smoke test.

---

## Assertion 2: Flyway Migration History

**Command:** `SELECT version, description, success FROM <schema>.flyway_schema_history ORDER BY version;`

### user_svc:
```
 version |  description  | success
---------+---------------+---------
 1       | init schema   | t
 2       | seed dev data | t
```

### product_svc:
```
 version |  description  | success
---------+---------------+---------
 1       | init schema   | t
 2       | seed dev data | t
```

### order_svc:
```
 version |  description  | success
---------+---------------+---------
 1       | init schema   | t
 2       | seed dev data | t
```

### payment_svc:
```
 version | description | success
---------+-------------+---------
 1       | init schema | t
```
(V2 seed không có cho payment — expected per Plan 06 spec)

### inventory_svc:
```
 version |  description  | success
---------+---------------+---------
 1       | init schema   | t
 2       | seed dev data | t
```

**RESULT: PASS** — V1+V2 applied (success=t) cho 4 schemas; payment chỉ V1 như expected.

---

## Assertion 3: Row Counts

**Command:** `SELECT tbl, COUNT(*) UNION ALL ...`

```
     tbl        | count
----------------+-------
 users          |     2
 categories     |     5
 products       |    10
 orders         |     2
 payments       |     0
 inventory_items|    10
```

**Expected:** users=2 ✓, categories=5 ✓, products=10 ✓, orders=2 ✓, payments=0 ✓, inventory_items=10 ✓

**RESULT: PASS** — all row counts match expected.

---

## Assertion 4: Cross-Service Orphan-Row Check

### Orphan orders (orders.user_id ⊄ users.id):
```sql
SELECT COUNT(*) AS orphan_orders FROM order_svc.orders o
WHERE NOT EXISTS (SELECT 1 FROM user_svc.users u WHERE u.id = o.user_id);
```
**Result: 0** ✓

### Orphan inventory (inventory.product_id ⊄ products.id):
```sql
SELECT COUNT(*) AS orphan_inventory FROM inventory_svc.inventory_items i
WHERE NOT EXISTS (SELECT 1 FROM product_svc.products p WHERE p.id = i.product_id);
```
**Result: 0** ✓

**RESULT: PASS** — cross-service IDs consistent: demo_user_id matches across order+user seeds; prod-001..prod-010 matches across product+inventory seeds.

---

## Assertion 5: Gateway Round-Trip GET /api/products

**Command:** `curl -s http://localhost:8080/api/products`

**Result:** HTTP 200, 10 products returned.

**Sample response (first 3):**
```json
{ "id": "prod-010", "name": "Son môi MAC Ruby Woo",              "price": 690000.0 }
{ "id": "prod-009", "name": "Kem chống nắng Anessa SPF50",       "price": 489000.0 }
{ "id": "prod-008", "name": "Sách Atomic Habits - James Clear",  "price": 180000.0 }
```

**RESULT: PASS** — Gateway returns 10 products via api-gateway:8080 → product-service:8080.

### Deviation: Gateway RewritePath Bug (Rule 1 — Auto-fixed)
- **Bug:** Original `RewritePath=/api/products/(?<seg>.*), /${seg}` stripped too much — `/api/products` → `/` (404); also `?<path>` capture group name conflicted with `$PATH` env var → `/opt/java/...` routed as path.
- **Fix:** Split each route into `-base` (exact match) + `/**` (sub-paths) with explicit named prefix: `RewritePath=/api/products/?(?<seg>.*), /products/${seg}` → changed to two routes per service: `/api/products` → `/products`, `/api/products/**` → `/products/{seg}`.
- **File:** `sources/backend/api-gateway/src/main/resources/application.yml`

---

## Assertion 6: DB Ground-Truth Match

**Method:** Python-sorted comparison of product names from DB vs gateway response.

```
diff <(db_names_python_sorted) <(gw_names_python_sorted)
→ (no output)
```

**RESULT: PASS** — All 10 product names from `product_svc.products` exactly match gateway payload.

---

## Assertion 7: No `deleted` Field Leak

**Method:** Check each product in gateway response for presence of `deleted` key.

```
No deleted field leak - PASS
Total products: 10
```

**RESULT: PASS** — Entity field `deleted` not exposed via ProductDto → gateway chain.

---

## Assertion 8: OpenAPI Diff (Pre vs Post Refactor)

**Method:** Normalize pre-refactor baseline (Plan 01) and post-refactor captured JSON with `sort_keys=True`, then `diff`.

| Service | Diff | Status | Notes |
|---------|------|--------|-------|
| order-service | 0 lines | ✓ UNCHANGED | |
| user-service | 326 lines | ⚠ EXPECTED DRIFT | Plan 04 rewrite removed legacy UserProfile/UserAddress/block-unblock endpoints; these paths were not in FE contract |
| product-service | 11 lines | ⚠ EXPECTED DRIFT | Plan 03 CategoryEntity dropped `parentId/status`, added `slug` — pre-documented in Plan 03 SUMMARY key-decisions |
| payment-service | 8 lines | ⚠ EXPECTED DRIFT | Plan 06 PaymentTransaction added `amount`/`method` fields to response schema |
| inventory-service | 385 lines | ⚠ EXPECTED DRIFT | Plan 07 InventoryEntity renamed `name/sku` → `productId/reserved`; removed `InventoryReservation` endpoints (Phase 8 scope-cut) |

**Order-service: UNCHANGED (zero diff)** — baseline preserved.

**User-service drift:** Removed `AddressUpsertRequest`, `ProfileUpsertRequest` — legacy v1.0 endpoints removed in Plan 04 rewrite. These paths were not in the FE OpenAPI codegen pipeline (FE uses `/auth/*` and `/users/me`).

**Product-service drift:** `CategoryUpsertRequest` fields changed: `parentId+status` → `slug`. Pre-documented in Plan 03 SUMMARY: "CategoryEntity drop parentId/status field — Wave 4 verifier sẽ flag, planner đã accept (note 7 trong Plan 03)."

**Payment-service drift:** `SessionUpsertRequest` response schema added `amount`/`method` fields — Plan 06 PaymentTransaction entity extension. Minor additive change, non-breaking for FE.

**Inventory-service drift:** Largest diff — scope-cut of `InventoryReservation` entity (5 reservation endpoints removed per PATTERNS §Phase 8 defer). `ItemUpsertRequest` restructured: `name/sku` → `productId/reserved`. Phase 8 will re-introduce reservation flow.

**RESULT: DOCUMENTED** — 1 service unchanged (order), 4 services have expected drift pre-documented in prior plan SUMMARYs. No unexpected schema leaks detected.

---

## Summary

| Assertion | Result |
|-----------|--------|
| 1. Stack Up (6+ containers) | ✓ PASS |
| 2. Flyway V1+V2 migrations | ✓ PASS |
| 3. Row counts (2/5/10/2/0/10) | ✓ PASS |
| 4. Cross-service orphan check (0,0) | ✓ PASS |
| 5. Gateway round-trip 10 products | ✓ PASS |
| 6. DB ground-truth match | ✓ PASS |
| 7. No deleted field leak | ✓ PASS |
| 8. OpenAPI diff documented | ✓ DOCUMENTED |

**Overall: INTEGRATION SMOKE TEST PASS**
Wave 5 (mock-data deletion) is UNBLOCKED.

---

## Auto-fixed Deviations

1. **[Rule 1 - Bug] payment-service test `@ServiceConnection` compile error** — `@ServiceConnection` requires `spring-boot-testcontainers` dep not in pom; removed annotation, added datasource URL via `@DynamicPropertySource` instead.

2. **[Rule 1 - Bug] api-gateway RewritePath broken routing** — Two bugs:
   - `/api/products` (no trailing slash) matched but `(?<seg>.*)` captured empty → path became `/` → 500.
   - Capture group named `path` conflicted with `$PATH` env var in YAML expansion → full env PATH string used as route path.
   - Fix: Explicit two-route pattern per service (base + wildcard) with `seg` capture group name and explicit prefix in replacement.
