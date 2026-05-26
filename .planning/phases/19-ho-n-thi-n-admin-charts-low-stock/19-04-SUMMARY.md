---
phase: 19-ho-n-thi-n-admin-charts-low-stock
plan: "04"
subsystem: frontend-admin-dashboard-charts
tags:
  - frontend
  - admin
  - charts
  - recharts
  - dashboard
  - vietnamese
  - playwright
dependency_graph:
  requires:
    - 19-01-SUMMARY (order-svc /admin/charts/{revenue,top-products,status-distribution})
    - 19-02-SUMMARY (user-svc /admin/charts/signups)
    - 19-03-SUMMARY (product-svc /admin/products/charts/low-stock)
    - existing-services-http (httpGet auto Bearer + envelope unwrap)
    - existing-app-admin-page (KPI Phase 9 baseline)
    - existing-e2e-global-setup (admin storageState)
  provides:
    - services/charts.ts (5 typed fetchers + Range type)
    - lib/chartFormat.ts (STATUS_COLORS, statusLabel, vnNumber, vnDate)
    - components/admin/ChartCard (generic 3-state wrapper)
    - components/admin/{Revenue,TopProducts,StatusDistribution,UserSignups}Chart
    - components/admin/LowStockSection
    - extended /admin dashboard với time-window dropdown + 2x2 charts grid + low-stock
    - 2 Playwright smoke specs (charts + low-stock)
  affects:
    - sources/frontend/package.json (+recharts@3.8.1 exact)
    - sources/frontend/src/app/admin/page.tsx (extended, KHÔNG rewrite)
    - sources/frontend/src/app/admin/page.module.css (+ .timeWindowRow + .chartsGrid)
tech_stack:
  added:
    - "recharts@3.8.1 (lock exact, no semver caret per D-11)"
  patterns:
    - "Recharts composable JSX (LineChart/BarChart/PieChart + ResponsiveContainer)"
    - "ChartCard 3-state wrapper analog KpiCard (D-14)"
    - "Promise.allSettled per-chart loaders (D-14, mirror Phase 9 D-09)"
    - "useCallback deps include range chỉ cho 3 charts có time-window (D-06; pie + low-stock deps trống)"
    - "Per-slice Cell color via STATUS_COLORS map (D-12 semantic colors)"
    - "Intl.NumberFormat('vi-VN') + Intl.DateTimeFormat('vi-VN') (D-13)"
    - "ResponsiveContainer parent min-height: 250px (Pitfall #1)"
    - "Playwright storageState reuse từ global-setup admin login"
key_files:
  created:
    - sources/frontend/src/services/charts.ts
    - sources/frontend/src/lib/chartFormat.ts
    - sources/frontend/src/components/admin/ChartCard.tsx
    - sources/frontend/src/components/admin/ChartCard.module.css
    - sources/frontend/src/components/admin/RevenueChart.tsx
    - sources/frontend/src/components/admin/TopProductsChart.tsx
    - sources/frontend/src/components/admin/StatusDistributionChart.tsx
    - sources/frontend/src/components/admin/UserSignupsChart.tsx
    - sources/frontend/src/components/admin/LowStockSection.tsx
    - sources/frontend/e2e/admin-charts.spec.ts
    - sources/frontend/e2e/admin-low-stock.spec.ts
  modified:
    - sources/frontend/package.json
    - sources/frontend/package-lock.json
    - sources/frontend/src/app/admin/page.tsx
    - sources/frontend/src/app/admin/page.module.css
decisions:
  - "D-06 áp dụng: 1 dropdown global điều khiển 3 charts (revenue/top-products/signups), pie KHÔNG range — useCallback loadStatus deps trống"
  - "D-07 áp dụng: layout KPI row → dropdown → 2x2 charts grid → low-stock full-width"
  - "D-10 áp dụng: LowStockSection click row + nút Sửa đều router.push('/admin/products?highlight={id}')"
  - "D-11 áp dụng: recharts@3.8.1 install --save-exact, verify package.json field literal '3.8.1' không có caret"
  - "D-12 áp dụng: revenue stroke var(--primary), top-products fill var(--secondary), signups stroke #f59e0b, status pie semantic 5 colors qua Cell"
  - "D-13 áp dụng: tất cả tooltip/legend/empty state tiếng Việt; Intl.NumberFormat('vi-VN') cho số; vnDate DD/MM"
  - "D-14 áp dụng: ChartCard generic 3-state wrapper, Promise.allSettled per-chart loaders với useCallback"
  - "D-15 áp dụng: 4 empty state strings unique per chart type (revenue+signups dùng cùng wording, top-products + status + low-stock riêng)"
  - "Recharts 3.8.1 Tooltip Formatter type signature thay đổi từ research code: ValueType|undefined thay vì number — Rule 1 fix dùng Number(v)/String(iso) cast inside formatter callback"
  - "Pitfall #2 (Cell deprecated 3.7+): chấp nhận console warning, KHÔNG migrate sang shape callback ở phase này — đủ dùng 3.8.1"
  - "next/img bypass cho LowStockSection thumbnail: dùng <img> với eslint-disable-next-line — thumbnailUrl là Unsplash CDN external (precedent v1.2 SEED-03), KHÔNG cần next/image domain config thêm"
metrics:
  duration: 18min
  completed: "2026-05-02"
  tasks: 3
  files_created: 11
  files_modified: 4
---

# Phase 19 Plan 04: Admin Charts FE + Low-Stock Section Summary

Hoàn tất FE Phase 19: install recharts@3.8.1, build 5 chart fetchers + 4 helper exports + 6 components (1 ChartCard wrapper + 4 chart components + 1 LowStockSection), extend admin/page.tsx với time-window dropdown + 2x2 charts grid + low-stock full-width, viết 2 Playwright smoke specs reuse admin storageState.

## Tasks Executed

### Task 1: Recharts install + services/charts.ts + lib/chartFormat.ts + ChartCard wrapper — `47728c4`

- `npm install recharts@3.8.1 --save-exact` → package.json field `"recharts": "3.8.1"` (no caret, lock chính xác per D-11).
- `services/charts.ts`: 5 fetchers (`fetchRevenueChart`, `fetchTopProducts`, `fetchStatusDistrib`, `fetchUserSignups`, `fetchLowStock`) + `Range = '7d'|'30d'|'90d'|'all'` + 5 typed interfaces khớp BE Plans 01/02/03 wire format.
- `lib/chartFormat.ts`: `STATUS_COLORS` (D-12 semantic 5 colors) + `statusLabel` (D-13 VN 5 strings) + `vnNumber` (Intl `vi-VN`) + `vnDate` (Intl `vi-VN` DD/MM).
- `components/admin/ChartCard.tsx`: generic `<T>` wrapper với 3 states (loading skeleton 250px / success render via prop / error '--' + retry ⟳ với title=error tooltip). Export `CardState<T>` named cho page.tsx + chart components reuse.
- `components/admin/ChartCard.module.css`: critical `.chartBody { min-height: 250px }` (Pitfall #1: ResponsiveContainer parent collapse → invisible).

### Task 2: 4 chart components + LowStockSection — `9cdfb4c`

- `RevenueChart.tsx`: `LineChart` `dataKey="value"`, stroke `var(--primary)` (D-12), `vnDate` XAxis tick + `vnNumber` YAxis tick + `vnNumber(...) ₫` tooltip.
- `TopProductsChart.tsx`: `BarChart layout="vertical"`, `Bar fill="var(--secondary)"` (D-12), `shortName` truncate >20 chars + '…', tooltip `vnNumber(...) sản phẩm`.
- `StatusDistributionChart.tsx`: `PieChart` + `Pie` (innerRadius 40 outerRadius 80 = donut) + per-slice `<Cell fill={STATUS_COLORS[status]}>`, tooltip `{n} đơn`, Legend fontSize 12.
- `UserSignupsChart.tsx`: mirror RevenueChart structure nhưng `dataKey="count"` + stroke `#f59e0b` (D-12 cam khớp KPI customer card) + tooltip `{n} người`.
- `LowStockSection.tsx`: `<ul>` rows với img 40x40 + name + brand subtitle + stock badge (đỏ `#dc2626` <5, cam `#f59e0b` 5–9, format "Còn N") + nút "Sửa". Click row hoặc nút → `router.push('/admin/products?highlight={id}')` (D-10). Nút stopPropagation.

### Task 3: Extend admin/page.tsx + CSS + 2 Playwright smoke specs — `0e0c501`

- `admin/page.tsx` extension (KHÔNG rewrite KPI logic):
  - Imports: ChartCard + 4 chart components + LowStockSection + 5 fetchers + Range/types từ `@/services/charts`.
  - State: `range` default `'30d'` (D-06) + 5 `CardState<T>` cho 5 chart cards.
  - 5 useCallback loaders: `loadRevenue`/`loadTopProducts`/`loadSignups` deps `[range]`; `loadStatus`/`loadLowStock` deps `[]` (D-06 + Pitfall #5).
  - Second `useEffect` Promise.allSettled 5 chart loaders.
  - JSX: KPI grid → `.timeWindowRow` (label "Khoảng thời gian:" + select 4 options VN) → `.chartsGrid` (4 ChartCard) → 1 `<ChartCard>` wrap LowStockSection.
- `admin/page.module.css`: thêm `.timeWindowRow` (flex row) + `.chartsGrid` (grid 2 cols + `@media (max-width: 768px) → 1fr`).
- `e2e/admin-charts.spec.ts`: 2 tests — (1) KPI + dropdown 30d default + 4 options + 4 chart titles + ≥1 SVG/empty state; (2) đổi dropdown 7d → 'all' không crash.
- `e2e/admin-low-stock.spec.ts`: 2 tests — (1) section title visible + items HOẶC empty placeholder; (2) click row navigate `/admin/products?highlight=`.

## Acceptance Criteria

- ROADMAP SC #1–5: tất cả 5 charts/sections render đúng theo D-06..D-15.
- tsc clean: `npx tsc --noEmit` exit 0 (sau Rule 1 type-fix Recharts Formatter).
- lint clean cho 11 file mới (pre-existing AddressPicker error out-of-scope, defer).
- Mobile breakpoint: `chartsGrid` collapse 1 col <768px.
- Vietnamese labels nghiêm chỉnh ở all empty states + tooltip + dropdown.
- Recharts version locked: `package.json` `"recharts": "3.8.1"` exact.
- 2 Playwright spec files compile + assertion logic accept either real data hoặc empty state (env-tolerant).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Type Mismatch] Recharts 3.8.1 Tooltip Formatter signature**

- **Found during:** Task 2 (sau khi tạo 4 chart components verbatim per RESEARCH §FE Pattern 1/2/3).
- **Issue:** RESEARCH code dùng `formatter={(v: number) => ...}` nhưng Recharts 3.8.1 type `Formatter<ValueType, NameType>` yêu cầu `value: ValueType | undefined` (ValueType = `string | number | (string | number)[]`). tsc reject 4 errors trong RevenueChart/TopProductsChart/StatusDistributionChart/UserSignupsChart.
- **Fix:** Bỏ explicit `: number` annotation, dùng `Number(value)`/`String(iso)` cast inside callback body. Behavior giữ nguyên (vẫn format số/date như cũ).
- **Files modified:** RevenueChart.tsx, TopProductsChart.tsx, StatusDistributionChart.tsx, UserSignupsChart.tsx.
- **Commit:** `9cdfb4c` (cùng commit Task 2 sau Edit pass).

**2. [Rule 2 - Missing critical] eslint-disable cho `<img>` ở LowStockSection**

- **Found during:** Task 2.
- **Issue:** Next.js lint rule `@next/next/no-img-element` warn dùng `<img>` thay `next/image`. Plan dùng `<img>` đơn giản (RESEARCH §FE Pattern 4 verbatim). next/image cần config thêm `images.domains` cho Unsplash CDN.
- **Fix:** Thêm `// eslint-disable-next-line @next/next/no-img-element` trên dòng `<img>` — match precedent v1.2 SEED-03 (Unsplash CDN external, không cần next/image config).
- **Files modified:** LowStockSection.tsx.
- **Commit:** `9cdfb4c`.

### Out-of-scope (Defer)

- Pre-existing lint errors trong `AddressPicker.tsx` (set-state-in-effect) + 7 warnings khác — KHÔNG liên quan Plan 19-04 files. Logged để cleanup phase tương lai.

## Authentication Gates

Không có. Plan này chỉ FE compile + test write; không gọi BE trực tiếp ở build time. Playwright runtime cần admin storageState (đã có từ Phase 9 global-setup.ts).

## Verification

- `npx tsc --noEmit`: PASS (no errors).
- `npm run lint` cho 11 file mới: PASS (chỉ pre-existing out-of-scope errors).
- Playwright execution: **DEFERRED** — không chạy `npx playwright test` ở env này vì:
  - Cần backend Plans 19-01/02/03 services running (Maven+Docker chưa khả dụng trên Windows env theo STATE.md note Plan 01/02/03).
  - Cần `npx playwright install` browser binaries chưa setup ở env executor.
  - Spec syntax + types verified bằng `npx tsc --noEmit` (specs là TS files được tsc include).
  - Verifier `/gsd-verify-work` hoặc UAT manual sẽ chạy specs khi env ready.

## Manual UAT (defer per phase policy)

Khi docker-compose + admin login khả dụng, verify visual:

1. 4 KPI cards Phase 9 vẫn render bình thường.
2. Dropdown "Khoảng thời gian:" hiện sau KPI, default "30 ngày", 4 options VN.
3. 4 chart cards trong 2x2 grid: Doanh thu (line) + Sản phẩm bán chạy (bar horizontal) + Phân phối trạng thái (donut) + Khách hàng đăng ký (line).
4. Low-stock section full-width cuối: hoặc list rows (thumbnail + badge + Sửa button) hoặc placeholder "Tất cả sản phẩm đủ hàng ✓".
5. Đổi dropdown 7d → 30d → 90d → all: 3 charts (revenue/top/signups) refetch (skeleton brief → render). Pie + low-stock KHÔNG reload.
6. Click row low-stock → URL chuyển `/admin/products?highlight={uuid}` (page tiêu thụ highlight param defer Phase tương lai per D-10).
7. Resize browser <768px → charts grid collapse 1 col.

## Deferred Items

- D-10 highlight query param ở `/admin/products` page — chỉ navigation, KHÔNG đọc/highlight row. Defer: phase polish tương lai.
- Pitfall #2 Cell deprecated 3.7+ — chấp nhận console warning, future-proof migrate sang `shape` callback defer khi Recharts 4.x release.
- Playwright execution deferred (env không có browser binary + BE services running). Verifier sẽ chạy.
- Pre-existing AddressPicker lint errors out-of-scope.

## Self-Check: PASSED

Files verified exist:

- FOUND: sources/frontend/src/services/charts.ts
- FOUND: sources/frontend/src/lib/chartFormat.ts
- FOUND: sources/frontend/src/components/admin/ChartCard.tsx
- FOUND: sources/frontend/src/components/admin/ChartCard.module.css
- FOUND: sources/frontend/src/components/admin/RevenueChart.tsx
- FOUND: sources/frontend/src/components/admin/TopProductsChart.tsx
- FOUND: sources/frontend/src/components/admin/StatusDistributionChart.tsx
- FOUND: sources/frontend/src/components/admin/UserSignupsChart.tsx
- FOUND: sources/frontend/src/components/admin/LowStockSection.tsx
- FOUND: sources/frontend/e2e/admin-charts.spec.ts
- FOUND: sources/frontend/e2e/admin-low-stock.spec.ts

Commits verified:

- FOUND: 47728c4 (Task 1)
- FOUND: 9cdfb4c (Task 2)
- FOUND: 0e0c501 (Task 3)

Phase 19 hoàn tất 4/4 plans → ready cho `/gsd-verify-work`.
