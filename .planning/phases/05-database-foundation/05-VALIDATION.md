---
phase: 5
slug: database-foundation
status: planned
nyquist_compliant: true
wave_0_complete: false
created: 2026-04-26
updated: 2026-04-26
---

# Phase 5 — Validation Strategy

> Per-phase validation contract. Plans 05-01..05-09 đã được tạo; map per-task verification dưới đây.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit Jupiter 5.10 (Spring Boot 3.3.2 BOM) + Testcontainers `postgres:16-alpine` (singleton reuse) |
| **FE framework** | Playwright (existing v1.0 suite) + `npm run build` (Next.js) |
| **Config file** | None — defaults Spring Boot parent + per-service test dirs |
| **Quick run command** | `mvn -pl sources/backend/<service> test -Dtest=<TestClass>` |
| **Full suite command per service** | `mvn -pl sources/backend/<service> test` |
| **Estimated runtime** | ~30s per service (Testcontainers cold start ~10s, warm reuse ~3s) |

---

## Sampling Rate

- **After every task commit:** Run `{quick run command}` (chỉ test class liên quan)
- **After every plan wave:** Run service-level `mvn test` + smoke `docker compose up -d postgres`
- **Before `/gsd-verify-work` (Wave 4):** Full stack `docker compose up` + curl gateway round-trip
- **Phase gate (Wave 5):** FE flow chính + Playwright subset
- **Max feedback latency:** ~30 seconds (Testcontainers warm)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 5-01-01 | 01 | 1 | DB-06 | — | Capture pre-refactor OpenAPI baselines | smoke (curl) | `for entry in user:8081 product:8082 order:8083 payment:8084 inventory:8085; do ...; done` | ✅ created Plan 01 | ⬜ pending |
| 5-01-02 | 01 | 1 | DB-05 | — | BCrypt hash matches admin123 | unit | `mvn -pl user-service test -Dtest=BCryptSeedHashTest` | ✅ created Plan 01 | ⬜ pending |
| 5-02-01 | 02 | 2 | DB-01 | — | db/init/01-schemas.sql tạo 5 schemas | smoke | `grep -c "CREATE SCHEMA" db/init/01-schemas.sql` | ✅ created Plan 02 | ⬜ pending |
| 5-02-02 | 02 | 2 | DB-01 | — | docker-compose.yml valid + healthcheck wired | smoke | `docker compose config --quiet` | ✅ existing | ⬜ pending |
| 5-02-03 | 02 | 2 | DB-01 | — | Postgres container green + 5 schemas + volume persist | manual checkpoint | `docker compose ps`, `psql \dn` | manual | ⬜ pending |
| 5-03-01 | 03 | 3 | DB-02, DB-03 | — | product-service deps + datasource config | unit | `mvn compile && grep currentSchema=product_svc app.yml` | ✅ created Plan 03 | ⬜ pending |
| 5-03-02 | 03 | 3 | DB-04 | — | ProductRepository JPA + soft-delete + DTO no leak | integration | `mvn -pl product-service test -Dtest=ProductRepositoryJpaTest` | ✅ created Plan 03 | ⬜ pending |
| 5-03-03 | 03 | 3 | DB-05 | — | V1 schema + V2 seed (5 cats + 10 products) | integration | `psql -c "SELECT COUNT(*) FROM product_svc.products"` | ✅ created Plan 03 | ⬜ pending |
| 5-04-01..03 | 04 | 3 | DB-02..05 | — | user-service refactor + V1 + V2 (admin + demo seed) | integration | `mvn -pl user-service test -Dtest=UserRepositoryJpaTest` + psql counts | ✅ created Plan 04 | ⬜ pending |
| 5-05-01..03 | 05 | 3 | DB-02..05 | — | order-service refactor + V1 + V2 (2 demo orders, note preserved) | integration | `mvn -pl order-service test -Dtest=OrderRepositoryJpaTest` + psql counts | ✅ created Plan 05 | ⬜ pending |
| 5-06-01..03 | 06 | 3 | DB-02, DB-03, DB-04 | — | payment-service refactor + V1 (no V2 seed) + entity actual fields preserved | integration | `mvn -pl payment-service test -Dtest=PaymentTransactionRepositoryJpaTest` | ✅ created Plan 06 | ⬜ pending |
| 5-07-01..03 | 07 | 3 | DB-02..05 | — | inventory-service refactor + V1 + V2 (10 rows) + rename | integration | `mvn -pl inventory-service test -Dtest=InventoryRepositoryJpaTest` + psql counts | ✅ created Plan 07 | ⬜ pending |
| 5-08-01 | 08 | 4 | DB-06 | — | docker compose stack lên + Flyway history + row counts | e2e smoke | `docker compose ps` + Flyway history queries + row count query | ✅ created Plan 08 | ⬜ pending |
| 5-08-02 | 08 | 4 | DB-06 | — | Gateway round-trip 10 products + DB ground-truth match + OpenAPI diff = 0 | e2e | `curl http://gateway/api/products` + diff baseline JSONs | ✅ created Plan 08 | ⬜ pending |
| 5-08-03 | 08 | 4 | DB-06 | — | Manual sign-off integration evidence | manual checkpoint | evidence file review | manual | ⬜ pending |
| 5-09-01 | 09 | 5 | DB-06 | — | FE build xanh + zero mock-data imports + admin pages stubbed | smoke | `npm run build` + `grep mock-data` | ✅ created Plan 09 | ⬜ pending |
| 5-09-02 | 09 | 5 | DB-06 | — | Folder mock-data deleted + Playwright audit | smoke | `test ! -d mock-data && npm run build` | ✅ created Plan 09 | ⬜ pending |
| 5-09-03 | 09 | 5 | DB-06 | — | FE flow chính manual verify với seeded data | manual checkpoint | browser walk + screenshot evidence | manual | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements (covered by Plan 01)

- [x] OpenAPI baseline JSON capture (5 services) — Plan 01 Task 1.1
- [x] BCrypt hash verify test — Plan 01 Task 1.2
- [ ] (No additional Wave 0 needed — Plans 03..07 self-contain Testcontainers `@DataJpaTest` setup)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Postgres container `(healthy)` + volume persist + 5 schemas pre-created | DB-01 | Smoke verify cần `docker compose` runtime | Plan 02 Task 2.3 instructions |
| Integration evidence sign-off | DB-06 | Cross-cutting holistic check | Plan 08 Task 8.3 instructions |
| FE flow chính (browse→cart→checkout→confirm) | DB-06 success criterion #5 | UI interactive flow không hoàn toàn automatable trong Phase 5 (Playwright subset chạy được nhưng final verify cần human spot-check) | Plan 09 Task 9.3 instructions |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify hoặc explicit manual checkpoint
- [x] Sampling continuity: mỗi plan có ít nhất 1 automated verify
- [x] Wave 0 covers OpenAPI baseline + BCrypt verify
- [x] No watch-mode flags
- [x] Feedback latency < 30s (Testcontainers warm)
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-04-26 (post planning)
