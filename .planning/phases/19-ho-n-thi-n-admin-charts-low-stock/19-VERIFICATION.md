---
phase: 19-ho-n-thi-n-admin-charts-low-stock
verified: 2026-05-02T00:00:00Z
status: human_needed
score: 5/5 success criteria verified at code/file level
verify_mode: structural+grep (Maven & Playwright runtime KHÔNG khả dụng trên Windows env — defer test execution sang manual UAT)
human_verification:
  - test: "Mở /admin trong browser, dropdown 7d/30d/90d/all → 3 charts (revenue/top-products/signups) re-fetch khớp range; pie + low-stock KHÔNG đổi"
    expected: "Tất cả 4 charts render data thật từ DB; tooltip vi-VN format; loading skeleton hiển thị trước khi data về"
    why_human: "Yêu cầu chạy stack đầy đủ (gateway + 3 BE services + DB) — env hiện tại không có Docker/Maven runtime"
  - test: "Click row trong LowStockSection → navigate đến /admin/products?highlight={id}; sản phẩm được highlight"
    expected: "URL chuyển đúng; product list page xử lý query param highlight (Phase trước)"
    why_human: "End-to-end navigation behavior cần browser thực"
  - test: "Chạy 2 Playwright specs: admin-charts.spec.ts + admin-low-stock.spec.ts"
    expected: "Cả 2 specs PASS với admin storageState"
    why_human: "Playwright runtime cần BE stack live"
  - test: "Status pie chart hiển thị đúng 5 màu semantic per slice"
    expected: "PENDING vàng (#f59e0b) / CONFIRMED xanh dương (#3b82f6) / SHIPPED cyan (#06b6d4) / DELIVERED xanh lá (#10b981) / CANCELLED đỏ (#dc2626)"
    why_human: "Visual color verification"
---

# Phase 19: Hoàn Thiện Admin (Charts + Low-Stock) — Verification Report

**Phase Goal:** Admin nhìn vào dashboard thấy 4 biểu đồ analytics + cảnh báo tồn kho thấp.
**Verified:** 2026-05-02
**Status:** human_needed (5/5 SCs PASS ở structural level — runtime/visual UAT defer sang HUMAN-UAT.md)
**Verify Mode:** Structural + grep + file existence. Maven/Playwright/Docker không khả dụng trên Windows env.

## Goal Achievement — 5 Success Criteria

| # | SC | Status | Evidence |
|---|-----|--------|----------|
| 1 | Revenue chart + dropdown 4-option time-window | PASS | `AdminChartsController.java:38 @GetMapping("/revenue")`; `Range.java:22 D7(7), D30(30), D90(90), ALL(null)`; `RevenueChart.tsx` exists; `admin/page.tsx:87 useState<Range>('30d')` + 4 options 7d/30d/90d/all (line 199-202) |
| 2 | Top-products bar chart + auth forwarding | PASS | `AdminChartsController.java:47 @GetMapping("/top-products")`; `ProductBatchClient.java:48 headers.set(HttpHeaders.AUTHORIZATION, authHeader)` (D-03 forward Bearer); `TopProductsChart.tsx` exists |
| 3 | Order-status pie với semantic colors | PASS | `AdminChartsController.java:57 @GetMapping("/status-distribution")`; `chartFormat.ts:6-11 STATUS_COLORS` định nghĩa đủ 5 hex (#f59e0b, #3b82f6, #06b6d4, #10b981, #dc2626); `StatusDistributionChart.tsx:44 fill={STATUS_COLORS[entry.status]}` |
| 4 | User signups line chart | PASS | `user-service AdminChartsController.java:36 @GetMapping("/signups")`; `UserSignupsChart.tsx` exists |
| 5 | Low-stock list + click navigate | PASS | `LowStockService.java:25 LOW_STOCK_THRESHOLD = 10` (D-08 hardcoded); `product-service AdminChartsController.java:34 @GetMapping("/low-stock")`; `LowStockSection.tsx:23,72 router.push('/admin/products?highlight=${item.id}')` (D-10) |

**Score:** 5/5 SCs verified at code level

## Required Artifacts — All Present

### Backend (6 files)
- order-service: `AdminChartsController.java`, `Range.java`, `ProductBatchClient.java` — VERIFIED
- user-service: `AdminChartsController.java` — VERIFIED
- product-service: `AdminChartsController.java`, `LowStockService.java` — VERIFIED

### Frontend (9 files)
- Components: `RevenueChart.tsx`, `TopProductsChart.tsx`, `StatusDistributionChart.tsx`, `UserSignupsChart.tsx`, `LowStockSection.tsx`, `ChartCard.tsx` — VERIFIED
- Lib: `chartFormat.ts` — VERIFIED
- E2E: `admin-charts.spec.ts`, `admin-low-stock.spec.ts` — VERIFIED

### Dependencies
- `recharts@3.8.1` (exact, no caret per D-11) — VERIFIED in `package.json:19`

## Key Link Verification

| From | To | Via | Status | Detail |
|------|-----|-----|--------|--------|
| RevenueChart/TopProductsChart/UserSignupsChart | range dropdown | `useState<Range>('30d')` + `setRange` | WIRED | admin/page.tsx:87,194-202 |
| StatusDistributionChart | STATUS_COLORS | `import { STATUS_COLORS } from '@/lib/chartFormat'` + `Cell fill={STATUS_COLORS[entry.status]}` | WIRED | StatusDistributionChart.tsx:16,44 |
| LowStockSection row click | /admin/products | `router.push('/admin/products?highlight=${item.id}')` | WIRED | LowStockSection.tsx:23,72 |
| order-svc → product-svc batch | Bearer JWT forward | `headers.set(HttpHeaders.AUTHORIZATION, authHeader)` | WIRED | ProductBatchClient.java:48 (D-03) |
| All charts | vi-VN formatter | `Intl.NumberFormat('vi-VN')` + `Intl.DateTimeFormat('vi-VN')` | WIRED | chartFormat.ts:26,30 (D-13) |

## Anti-Patterns Scan

Không phát hiện stub/TODO/placeholder blocker. Notes từ SUMMARY decisions:
- INFO: Recharts 3.7+ Cell deprecation warning — chấp nhận tại phase này (Pitfall #2, không block goal).
- INFO: `<img>` thay vì next/image cho LowStockSection thumbnail — precedent SEED-03 v1.2.

## Requirements Coverage

| REQ | Description | Status | Evidence |
|-----|-------------|--------|----------|
| ADMIN-01 | Revenue chart with time-window | SATISFIED | SC1 |
| ADMIN-02 | Top-products bar chart | SATISFIED | SC2 |
| ADMIN-03 | Order-status pie | SATISFIED | SC3 |
| ADMIN-04 | User signups line | SATISFIED | SC4 |
| ADMIN-05 | Low-stock alerts | SATISFIED | SC5 |

## Behavioral Spot-Checks

SKIPPED — runtime stack (Docker/Maven/Playwright) không khả dụng trên env này. Defer sang HUMAN-UAT.md (xem `19-HUMAN-UAT.md`).

## Gaps Summary

Không có structural gaps. Tất cả 5 SCs có code evidence. Items còn lại là **runtime/visual verification** cần browser + BE stack live → routed sang `19-HUMAN-UAT.md`.

---

_Verified: 2026-05-02_
_Verifier: Claude (gsd-verifier, retry instance)_
