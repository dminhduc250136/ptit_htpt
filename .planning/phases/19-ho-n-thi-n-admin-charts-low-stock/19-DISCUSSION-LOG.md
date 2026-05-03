# Phase 19: Hoàn Thiện Admin: Charts + Low-Stock - Discussion Log

> **Audit trail only.** Không dùng làm input cho planning/research/execution agents.
> Decisions chính thức ở `19-CONTEXT.md` — log này lưu lại các option đã cân nhắc.

**Date:** 2026-05-02
**Phase:** 19-ho-n-thi-n-admin-charts-low-stock
**Areas discussed:** Backend API shape, Time-window + Layout, Low-stock chi tiết, Visual + State handling
**Mode:** Interactive cho 2 câu đầu, sau đó user yêu cầu auto-mode → còn lại chốt theo recommended.

---

## Backend API shape

| Option | Description | Selected |
|--------|-------------|----------|
| 1 endpoint/chart per-svc | 4 endpoints riêng (/admin/orders/charts/{revenue,top-products,status} + /admin/users/charts/signups). Granular fail/cache/retry, khớp Promise.allSettled Phase 9. | ✓ |
| 1 bundled endpoint per-svc | /admin/orders/dashboard trả gộp + /admin/users/dashboard. Ít request nhưng mất granularity. | |
| 1 mega gateway aggregate | /api/admin/dashboard fan-out gateway. Đơn FE nhưng phá per-svc isolation. | |

**User's choice:** 1 endpoint/chart per-svc (Recommended).
**Notes:** Match Phase 9 D-09 Promise.allSettled per-card pattern.

---

## Top-products enrichment

| Option | Description | Selected |
|--------|-------------|----------|
| BE order-svc gọi product-svc enrich | order-svc query top-10 + RestClient batch product-svc, FE 1 round-trip. | ✓ |
| FE enrich (useEnrichedItems Phase 17) | BE trả productId+qty, FE gọi product-svc bổ sung. Tái dùng pattern nhưng 2 round-trip cho chart. | |
| Chỉ hiện productId | BE raw productId, FE render UUID. UX kém. | |

**User's choice:** BE order-svc tự enrich (Recommended).
**Notes:** Fallback nếu product-svc fail → trả productId-only, không fail toàn endpoint.

---

## Time-window UX (auto-chọn theo recommend)

| Option | Description | Selected |
|--------|-------------|----------|
| 1 dropdown global cho 3 charts có time-window | State ở dashboard, đổi → refetch. Order-status pie không bị ảnh hưởng (snapshot). | ✓ |
| Per-chart dropdown | Mỗi chart dropdown riêng, compare cross-window. | |
| Fixed 30d, không dropdown | Bỏ dropdown, contradict requirement. | |

**User's choice:** 1 dropdown global (auto — recommended).
**Notes:** Đơn giản UX, mở rộng per-chart sau nếu cần.

---

## Granularity cho range="all" (auto-chọn theo recommend)

| Option | Description | Selected |
|--------|-------------|----------|
| Daily cho mọi range | KHÔNG auto rollup. BE fill empty days với value=0 cho line chart liên tục. | ✓ |
| Auto rollup weekly khi >90d | Phức tạp BE, defer khi data lớn. | |

**User's choice:** Daily cho mọi range (auto — recommended).

---

## Layout dashboard (auto-chọn theo recommend)

| Option | Description | Selected |
|--------|-------------|----------|
| Extend /admin (giữ KPI + charts grid 2x2 + low-stock cuối) | 1 trang scroll, không tách analytics. Match end-user-visible priority. | ✓ |
| Tách /admin/analytics riêng | Thêm route, navigation, phức tạp hơn cần thiết. | |

**User's choice:** Extend /admin 1 trang scroll (auto — recommended).

---

## Low-stock chi tiết (auto-chọn theo recommend)

| Option | Description | Selected |
|--------|-------------|----------|
| List full sort by stock asc, cap 50, thumbnail+brand+stock badge, click → /admin/products?highlight= | Đầy đủ thông tin admin cần ra quyết định nhập hàng. | ✓ |
| Top-N urgent only (5 SP) | Quá ít, miss SP khác cũng low. | |
| Banner đếm số + link "Xem chi tiết" | Phải click thêm 1 lần, kém visible. | |

**User's choice:** List full với cap 50 + click → admin/products (auto — recommended).
**Notes:** Threshold = 10 hardcoded constant trong code (không env var).

---

## Visual + State handling (auto-chọn theo recommend)

| Option | Description | Selected |
|--------|-------------|----------|
| CSS vars cho line/bar + semantic colors cho pie + Promise.allSettled per-chart | Nhất quán theme + scan trạng thái nhanh + tái dùng pattern Phase 9. | ✓ |
| Palette data-viz riêng (Tableau/Material) | Đẹp hơn nhưng break theme tokens hiện tại. | |
| SWR/Suspense thay Promise.allSettled | Library mới, refactor nhiều. | |

**User's choice:** CSS vars + semantic pie colors + Promise.allSettled (auto — recommended).
**Notes:** Tooltip/Legend Vietnamese, vi-VN number/date format, status labels Vietnamese.

---

## Claude's Discretion

- ChartCard wrapper component structure (file location, prop API).
- BE service/repository method naming chi tiết.
- Có add @Cacheable chart endpoints không (defer — premature optimization).
- E2E Playwright spec (giữ TEST-02 deferred policy).

## Deferred Ideas

- ADMIN-07 custom date picker (đã deferred bucket REQUIREMENTS.md).
- Per-chart dropdown time-window.
- Auto rollup weekly/monthly cho range="all".
- Env var LOW_STOCK_THRESHOLD.
- CSV export, drill-down chart click.
- Real-time chart updates (WebSocket/SSE).
- @Cacheable optimization.
- Query param highlight ở /admin/products (optional enhancement).
