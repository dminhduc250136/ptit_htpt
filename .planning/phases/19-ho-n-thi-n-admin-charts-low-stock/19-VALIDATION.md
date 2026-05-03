---
phase: 19
slug: ho-n-thi-n-admin-charts-low-stock
status: ready
nyquist_compliant: true
wave_0_complete: false
created: 2026-05-02
last_updated: 2026-05-02
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
| **Full suite command (BE)** | `cd sources/backend/order-service && mvn -q test -Dtest='AdminCharts*,Range*,OrderRepositoryCharts*,ProductBatchClient*' && cd ../user-service && mvn -q test -Dtest='AdminCharts*,Range*,UserRepositorySignups*' && cd ../product-service && mvn -q test -Dtest='AdminCharts*,AdminProductBatch*,LowStock*,ProductBatch*,ProductRepositoryLowStock*'` |
| **Full suite command (FE)** | `cd sources/frontend && npx playwright test e2e/admin-charts.spec.ts e2e/admin-low-stock.spec.ts` |
| **Estimated runtime** | ~10s (FE quick) / ~60-180s (BE Testcontainers per-svc) / ~30-60s (FE E2E) |

---

## Sampling Rate

- **After every task commit:** Run quick run command (FE typecheck + lint)
- **After every plan wave:** Run BE full suite for affected service(s) + FE quick
- **Before `/gsd-verify-work`:** All suites green (BE + FE quick + FE E2E)
- **Max feedback latency:** ~180 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 19-01-T1 | 01 | 1 | ADMIN-01/02/03 | T-19-01-02/05/06 | Range parse 400; nullable JPQL idiom; cross-svc auth forward | unit + integration (DataJpaTest + Mockito) | `cd sources/backend/order-service && mvn -q test -Dtest='RangeTest,OrderRepositoryChartsIT,ProductBatchClientTest'` | Range.java, ProductBatchClient.java, OrderRepository.java(extended) | pending |
| 19-01-T2 | 01 | 1 | ADMIN-01/02/03 | T-19-01-01/04/07 | requireAdmin gate; auth-forwarding D-03; fallback name="Product {id[:8]}" | integration (SpringBootTest + Testcontainers) | `cd sources/backend/order-service && mvn -q test -Dtest='AdminChartsControllerIT'` | OrderChartsService.java, AdminChartsController.java | pending |
| 19-02-T1 | 02 | 1 | ADMIN-04 | T-19-02-02/04 | Range parse; nullable param idiom user-svc | unit + integration (DataJpaTest) | `cd sources/backend/user-service && mvn -q test -Dtest='RangeTest,UserRepositorySignupsIT'` | Range.java(user-svc), UserRepository.java(extended) | pending |
| 19-02-T2 | 02 | 1 | ADMIN-04 | T-19-02-01/03 | requireAdmin gate; gap-fill daily count | integration (SpringBootTest) | `cd sources/backend/user-service && mvn -q test -Dtest='AdminChartsControllerIT'` | UserChartsService.java, AdminChartsController.java(user-svc) | pending |
| 19-03-T1 | 03 | 1 | ADMIN-05 | T-19-03-04/05/06/07 | Stock<10 query; Pageable cap 50; @SQLRestriction soft-delete; parameterized | unit + integration (DataJpaTest + Mockito) | `cd sources/backend/product-service && mvn -q test -Dtest='ProductRepositoryLowStockIT,LowStockServiceTest,ProductBatchServiceTest'` | LowStockService.java, ProductBatchService.java, ProductRepository.java(extended) | pending |
| 19-03-T2 | 03 | 1 | ADMIN-05 | T-19-03-01/02/03 | requireAdmin gate cả 2 endpoints; null-safe body parse | integration (SpringBootTest) | `cd sources/backend/product-service && mvn -q test -Dtest='AdminChartsControllerIT,AdminProductBatchControllerIT'` | AdminChartsController.java(product-svc), AdminProductBatchController.java | pending |
| 19-04-T1 | 04 | 2 | ADMIN-01..05 | T-19-04-02/03/05 | recharts@3.8.1 lock; Vietnamese formatters Intl.*; min-height 250px (Pitfall #1) | typecheck + lint | `cd sources/frontend && npx tsc --noEmit && npm run lint` | charts.ts, chartFormat.ts, ChartCard.tsx/.module.css | pending |
| 19-04-T2 | 04 | 2 | ADMIN-01..05 | T-19-04-02/03/04 | React escape text; semantic colors per status; URL hardcoded prefix | typecheck + lint | `cd sources/frontend && npx tsc --noEmit && npm run lint` | RevenueChart.tsx, TopProductsChart.tsx, StatusDistributionChart.tsx, UserSignupsChart.tsx, LowStockSection.tsx | pending |
| 19-04-T3 | 04 | 2 | ADMIN-01..05 | T-19-04-01/06/07 | Range cast safety (BE 400 fallback); error state mask raw msg; D-06 dropdown deps | typecheck + lint + Playwright smoke | `cd sources/frontend && npx tsc --noEmit && npm run lint && npx playwright test e2e/admin-charts.spec.ts e2e/admin-low-stock.spec.ts` | page.tsx(extended), page.module.css(extended), e2e/admin-charts.spec.ts, e2e/admin-low-stock.spec.ts | pending |

---

## Coverage Targets

- **ADMIN-01 (revenue chart):** integration test query JPQL `FUNCTION('DATE', created_at)` GROUP BY + filter status='DELIVERED' + range; FE Playwright asserts chart `<svg>` rendered after dropdown change.
- **ADMIN-02 (top products):** integration test top-10 ordering by qty desc; cross-svc enrichment fallback test (mock ProductBatchClient empty map → endpoint still returns productId-only entries, KHÔNG 5xx).
- **ADMIN-03 (order status):** integration test counts by status; FE Playwright asserts pie SVG render với "Phân phối trạng thái" title visible.
- **ADMIN-04 (user signups):** integration test user-svc daily signup query + Java-side gap fill cho empty days.
- **ADMIN-05 (low-stock):** integration test product-svc low-stock query stock<10 sort asc cap 50; FE Playwright asserts low-stock card renders + click row navigates `/admin/products?highlight=`.

---

## Empty / Edge / Failure Tests (MANDATORY)

- **Empty range** (no DELIVERED orders in 7d): revenue endpoint returns gap-filled days với value=0. FE renders chart line ở 0 (KHÔNG empty placeholder vì gap-fill produces ≥1 point).
- **Truly empty array** (zero rows even without gap-fill): FE empty placeholder per D-15.
- **No low-stock items**: product-svc returns `[]`, FE renders "Tất cả sản phẩm đủ hàng ✓".
- **Cross-svc product-svc down** (top-products): mock ProductBatchClient ném exception → returns Map.of() → endpoint trả 200 với productId-only fallback entries (D-03).
- **Invalid range param**: `?range=invalid` → 400 ResponseStatusException via Range.parse.
- **Unauthenticated request**: missing/invalid Bearer → 401 from JwtRoleGuard. Non-admin role → 403.
- **Cross-svc auth forwarding**: top-products endpoint cũng verify ProductBatchClient.fetchBatch nhận đúng `Authorization` header value (Pitfall #4).

---

*Status:* `ready` — planner đã populate per-task table cho Wave 1+2. Executor sẽ flip `wave_0_complete:true` sau khi BE Wave 1 ship + cập nhật mỗi row Status sau task commit.
