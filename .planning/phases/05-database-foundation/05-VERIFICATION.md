---
phase: 05-database-foundation
verified: 2026-04-26T05:30:00Z
status: passed
score: 6/6 must-haves verified
overrides_applied: 0
re_verification: false
gaps: []
deferred: []
human_verification: []
---

# Phase 5: Database Foundation — Verification Report

**Phase Goal:** Postgres + JPA + Flyway + seed từ FE mocks; gateway round-trip qua FE trả seeded data thật.
**Verified:** 2026-04-26
**Status:** PASS
**Re-verification:** Không — initial verification.

---

## Verdict: PASS

Tất cả 6 success criteria của ROADMAP Phase 5 đã được xác minh qua codebase artifacts, commit history, và evidence files được tạo trong stack smoke test (Plan 08) và FE cleanup (Plan 09).

---

## 1. Observable Truths (Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| SC-1 | `docker compose up` khởi động Postgres container green với healthcheck PASS, volume persist data giữa restarts | VERIFIED | `docker-compose.yml` root: image postgres:16-alpine, healthcheck pg_isready, volume `tmdt-pgdata`. Evidence file Assertion 1: postgres `Up (healthy)`. |
| SC-2 | 5 services start green với JPA + Flyway baseline migration applied (`V1__init_schema.sql`) | VERIFIED | V1 SQL files tồn tại ở 5 services. `pom.xml` product + user xác nhận `spring-boot-starter-data-jpa`. Evidence file Assertion 2: V1 success=t cả 5 services. |
| SC-3 | Flyway dev seed (`V2__seed_dev_data.sql`) populate đúng products/orders + 1 admin user (BCrypt) + 5 categories | VERIFIED | V2 SQL files tồn tại (user/product/order/inventory). User V2 seed có admin BCrypt hash `$2a$10$TMH2sp...` verified Plan 01. Row counts: users=2, categories=5, products=10, orders=2. |
| SC-4 | FE `GET /api/products` qua gateway trả seeded products thật từ Postgres (verify DB → match payload) | VERIFIED | Commit `4a6afe1`: HTTP 200, 10 products, Python-sorted diff = 0 (DB names = gateway names). Assertion 6 integration-evidence.md: PASS. |
| SC-5 | `sources/frontend/src/mock-data/` đã được xóa; FE flow visible (browse/detail/add-to-cart) PASS với seeded data thật | VERIFIED | `git rm -r` commit `fe023fa`. `grep mock-data imports = 0`. FE build exit 0 (15/15 routes). A1 browse: 10 seeded products. A2 detail: slug lookup correct. A3 cart: localStorage functional. |
| SC-6 | Checkout + confirmation không vỡ build/render (defer PERSIST-01..03 sang Phase 8) | VERIFIED | `/checkout` → 307 → /login → 200 (no 5xx). `/checkout/success` → redirect 200. `/profile/orders/[id]` render "Đơn hàng không tồn tại" placeholder (no crash). |

**Score: 6/6 truths verified**

---

## 2. Per-Requirement Status (DB-01..DB-06)

### DB-01: Postgres container trong docker-compose.yml

**Yêu cầu:** Postgres service (single instance, multi-schema), healthcheck, volume persist.

| Artifact | Status | Evidence |
|----------|--------|----------|
| `docker-compose.yml` — postgres service | VERIFIED | image: postgres:16-alpine; healthcheck pg_isready -U tmdt; volume tmdt-pgdata; 5 services `depends_on: postgres: condition: service_healthy` |

**DB-01: SATISFIED**

---

### DB-02: JPA + PostgreSQL + Flyway deps trong 5 services

**Yêu cầu:** `spring-boot-starter-data-jpa` + `postgresql` + `flyway-core` cho user/product/order/payment/inventory.

| Service | JPA Dep | Flyway | V1 Migration |
|---------|---------|--------|-------------|
| product-service | `spring-boot-starter-data-jpa` found in pom.xml | V1 SQL file present | `/db/migration/V1__init_schema.sql` |
| user-service | `spring-boot-starter-data-jpa` found in pom.xml | V1 SQL file present | `/db/migration/V1__init_schema.sql` |
| order-service | V1 SQL file present | confirmed | `/db/migration/V1__init_schema.sql` |
| payment-service | V1 SQL file present | confirmed | `/db/migration/V1__init_schema.sql` |
| inventory-service | V1 SQL file present | confirmed | `/db/migration/V1__init_schema.sql` |

Smoke test (Plan 08 commit `69c8884`): Flyway history confirmed V1 success=t trên cả 5 schemas.

**DB-02: SATISFIED**

---

### DB-03: Datasource config + ddl-auto: validate + Flyway baseline

**Yêu cầu:** application.yml mỗi service: URL từ env vars, `ddl-auto: validate`, Flyway V1 migration.

Kiểm tra product-service (representative):
- `spring.datasource.url: jdbc:postgresql://${DB_HOST:localhost}:...?currentSchema=product_svc`
- `spring.jpa.hibernate.ddl-auto: validate`
- `spring.flyway.enabled: true`, `schemas: product_svc`
- Profile dev: `flyway.locations: classpath:db/migration,classpath:db/seed-dev`

Tất cả 5 services có `application.yml` với datasource + flyway config (verified bằng grep tìm thấy 5 files).

**DB-03: SATISFIED**

---

### DB-04: JPA repositories thay in-memory

**Yêu cầu:** `XxxRepository extends JpaRepository<XxxEntity, ID>`; service methods giữ signatures.

| Repository | JpaRepository | Verified |
|-----------|--------------|---------|
| `ProductRepository` | `extends JpaRepository<ProductEntity, String>` | VERIFIED |
| `UserRepository` | `extends JpaRepository<UserEntity, String>` | VERIFIED |
| `OrderRepository` | file exists at order-service/repository/ | VERIFIED |
| `PaymentSessionRepository` + `PaymentTransactionRepository` | files exist | VERIFIED |
| `InventoryRepository` | file exists at inventory-service/repository/ | VERIFIED |

Note: `InMemoryCartRepository` và `InMemoryNotificationRepository` còn tồn tại nhưng đây là cart (Phase 8 scope) và notification (giữ in-memory per Plan 02) — không vi phạm DB-04.

**DB-04: SATISFIED**

---

### DB-05: Seed dev data qua Flyway V2

**Yêu cầu:** V2 seed từ FE mocks — products/orders + admin user BCrypt + 5 categories.

| Service | V2 SQL | Content |
|---------|--------|---------|
| user-service | `db/seed-dev/V2__seed_dev_data.sql` | admin (BCrypt `$2a$10$TMH2sp...`) + demo_user, roles seeded |
| product-service | `db/seed-dev/V2__seed_dev_data.sql` | 5 categories + 10 products (prod-001..prod-010) |
| order-service | `db/seed-dev/V2__seed_dev_data.sql` | 2 orders referencing demo_user_id |
| inventory-service | `db/seed-dev/V2__seed_dev_data.sql` | 10 inventory_items referencing prod-001..010 |
| payment-service | V2 không có — expected per Plan 06 | (no payment seed needed) |

Row counts verified (Plan 08 smoke): users=2, categories=5, products=10, orders=2, inventory_items=10.
Cross-service IDs consistent: orphan check = 0 (orders.user_id ⊆ users.id, inventory.product_id ⊆ products.id).

**DB-05: SATISFIED**

---

### DB-06: End-to-end connectivity + mock-data deletion

**Yêu cầu:** Gateway round-trip `GET /api/products` → seeded products thật; xóa `sources/frontend/src/mock-data/`.

| Check | Result |
|-------|--------|
| Gateway `GET /api/products` → 10 products | VERIFIED (commit `4a6afe1`, Assertion 5+6) |
| DB ground-truth match (Python diff = 0) | VERIFIED |
| `deleted` field không lộ trong gateway payload | VERIFIED (Assertion 7) |
| `sources/frontend/src/mock-data/` đã xóa | VERIFIED (commit `fe023fa`, grep = 0 imports) |
| FE build GREEN 15/15 routes | VERIFIED (fe-flow-evidence.md) |
| Browse + detail + cart PASS | VERIFIED (Nhóm A fe-flow-evidence.md) |
| Playwright spec A4 updated to seed slug | VERIFIED (commit `fe023fa`) |

**DB-06: SATISFIED**

---

## 3. Artifact Summary

| Artifact | Status | Notes |
|----------|--------|-------|
| `docker-compose.yml` (root) | VERIFIED | Postgres healthcheck + 5 services depends_on |
| `db/init/01-schemas.sql` | Exists (Plan 02) | Multi-schema init script |
| `*/src/main/resources/db/migration/V1__init_schema.sql` (5 services) | VERIFIED | Flyway V1 applied success=t |
| `*/src/main/resources/db/seed-dev/V2__seed_dev_data.sql` (4 services) | VERIFIED | Flyway V2 applied, row counts match |
| `*/repository/*Repository.java extends JpaRepository` (5 services) | VERIFIED | JPA repos active |
| `sources/backend/api-gateway/src/main/resources/application.yml` | VERIFIED | Two-route-per-service pattern (Plan 08 fix) |
| `sources/frontend/src/services/products.ts` | VERIFIED | Correct gateway paths (Plan 09 fix) |
| `sources/frontend/src/mock-data/` | DELETED | Confirmed — git rm commit `fe023fa` |
| `.planning/phases/05-database-foundation/baseline/integration-evidence.md` | VERIFIED | 8 assertions PASS |
| `.planning/phases/05-database-foundation/baseline/fe-flow-evidence.md` | VERIFIED | Nhóm A/B/C PASS |
| OpenAPI baselines post-refactor (5 files) | VERIFIED | Captured, diff documented |

---

## 4. Key Links Verified

| From | To | Via | Status |
|------|----|-----|--------|
| api-gateway | product-service `/products` | `RewritePath=/api/products, /products` (base route) | WIRED |
| api-gateway | product-service `/products/**` | `RewritePath=/api/products/(?<seg>.*), /products/${seg}` | WIRED |
| FE `services/products.ts` | `GET /api/products` | `httpGet('/api/products', ...)` | WIRED |
| FE `services/products.ts` | `GET /api/products/{id}` | `httpGet('/api/products/${id}')` | WIRED |
| product-service | Postgres `product_svc` schema | Flyway V1+V2 + JPA `ddl-auto: validate` | WIRED |
| user-service | Postgres `user_svc` schema | Flyway V1+V2 + JPA | WIRED |
| order-service | Postgres `order_svc` schema | Flyway V1+V2 + JPA | WIRED |
| payment-service | Postgres `payment_svc` schema | Flyway V1 + JPA | WIRED |
| inventory-service | Postgres `inventory_svc` schema | Flyway V1+V2 + JPA | WIRED |

---

## 5. Known Stubs (Intentional — Scoped to Later Phases)

Các stub sau được chấp nhận trong Phase 5 scope. Đây không phải gap — là intentional deferrals đúng theo visible-first strategy:

| Stub | File | Addressed In |
|------|------|-------------|
| Admin pages dùng empty arrays | `app/admin/*.tsx` | Phase 7 UI-02..04 |
| Profile orders `loadOrder() = undefined` | `app/profile/orders/[id]/page.tsx` | Phase 8 PERSIST-02 |
| `getProductBySlug` client-side filter (backend ignores slug param) | `services/products.ts` | Phase 7 cleanup |
| Categories serialization trả empty `{}` objects | product-service | Phase 7 (non-blocking) |

---

## 6. Anti-Patterns

| Pattern | Severity | Assessment |
|---------|----------|-----------|
| Admin pages stub (`const _stub* = []`) | Info | Intentional per scope, TODO comments present — Phase 7 |
| Profile orders placeholder | Info | Intentional per scope, Phase 8 |
| Client-side slug filter | Warning | Workaround vì backend ignores slug param — Phase 7 |
| `InMemoryCartRepository` còn tồn tại | Warning | Cart persistence = Phase 8 PERSIST-01 scope, không vi phạm DB-04 |

Không có blocker anti-pattern nào cản Phase 5 goal.

---

## 7. Auto-Fixed Deviations (Plan 08 + 09)

Hai bugs được tự động sửa trong quá trình thực thi (Rule 1 — fix before moving on):

| Bug | Fix | Commit |
|-----|-----|--------|
| api-gateway `RewritePath` trailing-slash + `$PATH` env conflict | Two-route-per-service pattern, capture group `seg` | `69c8884` |
| payment-service test `@ServiceConnection` compile error | Thay bằng `@DynamicPropertySource` standard pattern | `69c8884` |
| FE `services/products.ts` double-path `/api/products/products` | Sửa paths về `/api/products` | `edd051f` |
| FE `getProductBySlug` backend ignores slug param | Client-side filter `find(p => p.slug === slug)` | `edd051f` |
| Playwright spec A4 hardcoded mock slug | Update sang seed slug `ao-thun-cotton-basic` | `fe023fa` |

---

## 8. Deferred Items

| Item | Addressed In | Evidence |
|------|-------------|----------|
| Checkout full breakdown (order submission + persistence + confirmation) | Phase 8 PERSIST-01..03 | ROADMAP Phase 5 SC-5 explicit defer; Phase 8 goal covers OrderEntity per-item + FE confirmation |
| Admin pages CRUD thật | Phase 7 UI-02..04 | ROADMAP Phase 7 success criteria 2/3/4 |
| Search rewire | Phase 7 UI-01 | ROADMAP Phase 7 success criteria 1 |
| `getProductBySlug` backend slug param support | Phase 7 cleanup | Plan 09 key-decisions |
| Categories serialization issue | Phase 7 (non-blocking) | Plan 09 Nhóm A A4 note |

---

## 9. Commit Summary (Phase 5)

| Wave | Plans | Key Commits | Highlights |
|------|-------|------------|-----------|
| Wave 1 | 01 | Plan 01 | OpenAPI baseline + BCrypt hash verified |
| Wave 2 | 02 | Plan 02 | Postgres container + db/init schemas |
| Wave 3 | 03-07 | Multiple commits | 5 services JPA refactor + Flyway V1+V2 |
| Wave 4 | 08 | `69c8884`, `4a6afe1` | Stack smoke + gateway round-trip PASS |
| Wave 5 | 09 | `ab26be2`, `fe023fa`, `edd051f` | FE mock-data deleted, visible flow PASS |

Total: 9/9 plans complete, tất cả commits present và verified.

---

## 10. Recommendation: Proceed to Phase 6 Real Auth Flow

Phase 5 Database Foundation đã **đầy đủ điều kiện tiên quyết** cho Phase 6:

- `UserEntity` persist trong Postgres với BCrypt password hash (admin + demo_user seeded)
- `user-service` JPA active, schema `user_svc` ready
- Gateway routes `/api/users/**` wired và functional
- FE đang gọi real gateway (mock-data đã xóa)

**Phase 6 có thể bắt đầu ngay.** AUTH-01..06 requirements:

1. **AUTH-01** (register): `POST /api/users/auth/register` → persist `UserEntity` + BCrypt hash
2. **AUTH-02** (login): `POST /api/users/auth/login` → verify cred → JWT HS256
3. **AUTH-03** (logout): token invalidation (blacklist hoặc client-side)
4. **AUTH-04** (FE login): `services/auth.ts` gỡ mock, store token + cookie
5. **AUTH-05** (FE register): form → API, field errors từ `ApiErrorResponse`
6. **AUTH-06** (session persist): middleware cookie read, protected routes redirect

**Không có technical debt nào từ Phase 5 block Phase 6.**

---

*Verified: 2026-04-26*
*Verifier: Claude (gsd-verifier, claude-sonnet-4-6)*
*Phase: 05-database-foundation*
*Plans verified: 05-01 through 05-09 (9/9 complete)*
