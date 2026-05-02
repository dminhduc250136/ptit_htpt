---
phase: 16
slug: seed-catalog-realistic
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-02
---

# Phase 16 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Source: `16-RESEARCH.md` §"Validation Architecture".

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Playwright 1.x (E2E) + manual SQL queries (smoke) |
| **Config file** | `sources/frontend/playwright.config.ts` |
| **Quick run command** | `cd sources/frontend && npx playwright test e2e/smoke.spec.ts` |
| **Full suite command** | `cd sources/frontend && npx playwright test` (7 spec files: smoke, auth, admin-products, admin-orders, admin-users, order-detail, password-change) |
| **Estimated runtime** | ~45 seconds (smoke 30s + manual SQL <5s + manual prod negative ~10s) |

**Note:** Backend không có JUnit tests. Validation chủ yếu qua: (1) SQL counts manual, (2) Playwright E2E hit `/products`, (3) profile negative test manual bash.

---

## Sampling Rate

- **After every task commit:** Smoke SQL count query (manual, <5s) hoặc `npx playwright test e2e/smoke.spec.ts -g SMOKE-1` (~10s)
- **After every plan wave:** `npx playwright test e2e/smoke.spec.ts` + `e2e/seed-catalog.spec.ts` mới (~30s)
- **Before `/gsd-verify-work`:** Full Playwright suite green + manual prod-profile negative test pass
- **Max feedback latency:** 45 seconds

---

## Per-Task Verification Map

> *Filled by gsd-planner khi tạo PLAN.md. Sẽ có 1 row mỗi task trong waves.*

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 16-01-XX | 01 | 1 | SEED-03 | — | URLs hot-link Unsplash CDN ổn định | manual | `wc -l < .planning/phases/16-seed-catalog-realistic/IMAGES.csv` ≥ 100 | ❌ W0 — IMAGES.csv NEW | ⬜ pending |
| 16-02-XX | 02 | 2 | SEED-01..04 | — | V101 idempotent, profile-dev only | smoke SQL | `psql -c "SELECT COUNT(*) FROM product_svc.products WHERE deleted=FALSE"` ∈ [95,105] | ❌ W0 manual | ⬜ pending |
| 16-03-XX | 03 | 3 | SEED-02, SEED-03 | — | `/products` render ≥20 cards/category, brand domain-tech | E2E | `npx playwright test e2e/seed-catalog.spec.ts` | ❌ W0 — seed-catalog.spec.ts NEW | ⬜ pending |
| 16-03-XX | 03 | 3 | SEED-04 | — | Profile prod KHÔNG run V101 | manual integration | bash script `SPRING_PROFILES_ACTIVE=prod restart + verify history` | ❌ W0 — textual command | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

> Planner sẽ refine với task IDs cụ thể sau khi PLAN.md files tạo xong.

---

## Wave 0 Requirements

- [ ] `sources/frontend/e2e/seed-catalog.spec.ts` (NEW) — verify catalog page render ≥20 cards/category, no broken images (network response listener), FilterSidebar brand list domain-tech (chỉ Apple/Samsung/Logitech/Sony/Razer/ASUS/Dell/HP/Lenovo/Bose/...). **Cover SEED-02, SEED-03, D-27, D-30.**
- [ ] `.planning/phases/16-seed-catalog-realistic/IMAGES.csv` (NEW) — 100+ curated Unsplash photo IDs với category mapping (id, suggested_category, photographer, license_note).
- [ ] Manual smoke SQL queries (textual commands trong `16-VERIFICATION.md`, không phải auto runner): COUNT categories, COUNT products, COUNT DISTINCT brand, COUNT thumbnail_url WebP pattern, COUNT stock<10.
- [ ] Manual D-31 profile=prod negative test bash script (textual command trong `16-VERIFICATION.md` — restart product-svc với `SPRING_PROFILES_ACTIVE=prod` và verify Flyway history KHÔNG có row V101).

**KHÔNG có gap test framework** — Playwright đã configured. Chỉ cần thêm 1 spec file mới + 1 CSV file + 4 manual command snippets.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Profile=prod KHÔNG chạy V101 | SEED-04 | CI khó switch Spring profile cleanly + reset DB; manual nhanh hơn | (1) `docker compose down -v` → reset DB; (2) Set `SPRING_PROFILES_ACTIVE=prod` env; (3) `docker compose up product-svc`; (4) `psql -c "SELECT version FROM product_svc.flyway_schema_history WHERE version='101'"` → expect 0 rows. |
| Smoke SQL counts | SEED-01..04 | Backend không có JUnit; pgAdmin/psql là tool tự nhiên | Chạy 5 SQL queries trong `16-VERIFICATION.md` "Smoke SQL" section, expect: 5 cat active, [95-105] products, ≥15 distinct brands, 100% URL khớp WebP pattern, ~10% stock<10. |
| Add-to-cart 1 SP mới hoạt động | SEED-02 (indirect) | Verify A2 assumption — inventory entries thiếu có break add-to-cart không | Mở `/products`, click 1 product card (`prod-pho-001`), click "Thêm vào giỏ", verify success message. Nếu fail → tạo sub-plan inventory V3 seed. |

---

## Validation Sign-Off

- [ ] All tasks have automated verify hoặc Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers IMAGES.csv + seed-catalog.spec.ts + manual SQL + manual prod test
- [ ] No watch-mode flags (Playwright default headless oneshot)
- [ ] Feedback latency < 45s
- [ ] `nyquist_compliant: true` set in frontmatter (after Wave 0 artifacts created)

**Approval:** pending (planner sẽ refine Per-Task Verification Map sau khi tạo PLAN.md files)
