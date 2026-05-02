---
phase: 19
slug: ho-n-thi-n-admin-charts-low-stock
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-02
---

# Phase 19 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Source: `19-RESEARCH.md` §Validation Architecture.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + Testcontainers Postgres (BE) + Playwright `^1.59.1` (FE E2E) + TypeScript compiler + ESLint |
| **Config file (BE)** | `sources/backend/{order,user,product}-service/src/test/...` (existing per-svc) |
| **Config file (FE)** | `sources/frontend/playwright.config.ts` |
| **Quick run command** | `cd sources/frontend && npx tsc --noEmit && npm run lint` |
| **Full suite command (BE)** | `cd sources/backend/order-service && mvn -q test -Dtest='AdminCharts*' && cd ../user-service && mvn -q test -Dtest='AdminCharts*' && cd ../product-service && mvn -q test -Dtest='AdminCharts*'` |
| **Full suite command (FE)** | `cd sources/frontend && npx playwright test e2e/admin-charts.spec.ts e2e/admin-low-stock.spec.ts` |
| **Estimated runtime** | ~10s (FE quick) / ~60-120s (BE Testcontainers) / ~30-60s (FE E2E) |

---

## Sampling Rate

- **After every task commit:** Run quick run command (FE typecheck + lint)
- **After every plan wave:** Run BE full suite for affected service(s) + FE quick
- **Before `/gsd-verify-work`:** All suites green (BE + FE quick + FE E2E)
- **Max feedback latency:** ~120 seconds

---

## Per-Task Verification Map

> Filled in by `gsd-planner` during plan generation (see PLAN.md `<verification>` blocks).
> Each task references the validation row by Task ID.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| TBD     | TBD  | TBD  | TBD         | TBD        | TBD             | TBD       | TBD               | TBD         | TBD    |

---

## Coverage Targets

- **ADMIN-01 (revenue chart):** integration test query JPQL `FUNCTION('DATE', created_at)` GROUP BY + filter status='DELIVERED' + range; FE E2E asserts chart `<svg>` rendered with at least 1 data point on seed data.
- **ADMIN-02 (top products):** integration test top-10 ordering by qty desc; cross-svc enrichment fallback test (mock product-svc 5xx → endpoint still returns productId-only entries, not 5xx).
- **ADMIN-03 (order status):** integration test counts by status, all 5 statuses present (zero-fill if absent).
- **ADMIN-04 (user signups):** integration test user-svc daily signup query + empty-day fill (Java-side gap fill).
- **ADMIN-05 (low-stock):** integration test product-svc low-stock query stock<10 sort asc cap 50; FE E2E asserts low-stock card renders + click row navigates.

---

## Empty / Edge / Failure Tests (MANDATORY)

- **Empty range** (no DELIVERED orders in 7d): revenue endpoint returns `[]` or zero-filled days. FE renders empty placeholder "Chưa có dữ liệu".
- **No low-stock items**: product-svc returns `[]`, FE renders "Tất cả sản phẩm đủ hàng ✓".
- **Cross-svc product-svc down** (top-products): mock 5xx, top-products still 200 with productId-only fallback (degraded, not failed).
- **Invalid range param**: `?range=invalid` → 400 ApiResponse.
- **Unauthenticated request**: missing/invalid Bearer → 401 from JwtRoleGuard. Non-admin role → 403.

---

*Status:* `draft` — planner will populate per-task table; executor will mark `wave_0_complete:true` after Wave 0 (test infra setup) and `nyquist_compliant:true` when all required dimensions covered.
