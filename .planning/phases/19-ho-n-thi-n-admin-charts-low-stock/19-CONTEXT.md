# Phase 19: Hoàn Thiện Admin: Charts + Low-Stock - Context

**Gathered:** 2026-05-02
**Status:** Ready for planning

<domain>
## Phase Boundary

Mở rộng `/admin` dashboard hiện tại (Phase 9 — 4 KPI cards) thêm:
- 4 analytics charts (revenue line/area, top-10 products bar, order-status pie/donut, user-signups line) với time-window dropdown (7d/30d/90d/all, default 30d).
- Low-stock alert section: list SP có `stock < 10`, click → trang admin products để edit.

Aggregation từ data thật (orders DELIVERED, order_items qty, orders.status, users.created_at, products.stock).

**KHÔNG bao gồm:** custom date picker (deferred ADMIN-07), CSV export, real-time push, advanced drill-down, separate `/admin/analytics` page.

</domain>

<decisions>
## Implementation Decisions

### Backend API Shape

- **D-01:** **1 endpoint per chart, per service.** order-svc expose 3 endpoints, user-svc expose 1.
  - `GET /admin/orders/charts/revenue?range=30d` → `[{date:"2026-04-15", value: 12500000}, ...]`
  - `GET /admin/orders/charts/top-products?range=30d` → `[{productId, name, brand, thumbnailUrl, qtySold}, ...]` (top-10)
  - `GET /admin/orders/charts/status-distribution` → `[{status:"PENDING", count: 12}, ...]` (KHÔNG có range — hiện trạng snapshot toàn bộ)
  - `GET /admin/users/charts/signups?range=30d` → `[{date:"2026-04-15", count: 5}, ...]`
  - **Why:** match Promise.allSettled per-chart pattern Phase 9 — fail/cache/retry granularity per chart, không bị 1 query chậm chặn cả dashboard.
  - **Gateway routes:** thêm 4 routes vào `application.yml` api-gateway theo pattern `/api/orders/admin/charts/**` → order-svc, `/api/users/admin/charts/**` → user-svc.

- **D-02:** **JWT role check pattern: tái dùng `JwtRoleGuard.requireAdmin(authHeader)`** y hệt AdminStatsController hiện có (D-05 Phase 9). KHÔNG dùng `@PreAuthorize`. Path controller bắt đầu `/admin/orders/charts` và `/admin/users/charts` để khớp gateway rewrite.

- **D-03:** **Top-products enrichment ở backend** — order-svc query top-10 productId+qty từ `order_items` (filter parent order DELIVERED + createdAt trong window), rồi `RestClient` gọi product-svc batch endpoint (cần thêm `POST /admin/products/batch` nhận `[id]` trả `[{id,name,brand,thumbnailUrl}]`) để enrich, trả về FE đầy đủ. **Why:** 1 round-trip từ FE, admin nhìn thấy ngay tên+ảnh; pattern tương tự Phase 17 nhưng làm ở BE để tránh 2 fetch FE cho chart.
  - **Fallback:** nếu product-svc fail (timeout / 5xx), trả productId+qty với name=`"Product {id[:8]}"`, KHÔNG fail toàn bộ endpoint.

- **D-04:** **Time-window param shape**: query string `range` nhận giá trị `7d | 30d | 90d | all`. BE parse bằng enum/switch, mapping ra `Instant from = Instant.now().minus(N, ChronoUnit.DAYS)` (hoặc bỏ filter cho `all`). Validation: invalid range → 400.

- **D-05:** **Daily granularity cho mọi range (kể cả `all`)** — KHÔNG auto rollup weekly/monthly. Dataset thử nghiệm nhỏ (~100 SP, vài chục order), daily đủ. Empty days được trả về với `value: 0` hoặc `count: 0` (BE fill gap để FE render line chart liên tục, không bị skip nhảy điểm).

### Time-window UX + Layout

- **D-06:** **1 dropdown global** ở top dashboard area (sau hàng KPI cards), điều khiển chung cho 3 charts có time-window (revenue, top-products, signups). **Order-status pie KHÔNG bị ảnh hưởng** — luôn snapshot hiện tại.
  - State: `useState<'7d'|'30d'|'90d'|'all'>('30d')`. Khi đổi → refetch cả 3 charts (per-chart Promise.allSettled).
  - **Why:** đơn giản UX cho admin, 1 chỗ control; phù hợp end-user-visible priority — nếu cần per-chart sau này thì mở rộng.

- **D-07:** **Layout dashboard mới (theo thứ tự dọc):**
  1. KPI cards row (giữ nguyên Phase 9 — 4 cards).
  2. Time-window dropdown (label "Khoảng thời gian:" + select 7/30/90/Tất cả).
  3. Charts grid 2x2: `[Revenue | Top Products]` / `[Order Status | User Signups]`. Mỗi card chart có title + chart body + skeleton/error state.
  4. Low-stock section (card riêng full-width ở cuối) — title "Sản phẩm sắp hết hàng" + list.
  - **Why:** dashboard 1 trang scroll, admin không phải điều hướng. KHÔNG tách `/admin/analytics`.
  - Responsive: dưới breakpoint mobile, charts grid → 1 cột.

### Low-stock Section

- **D-08:** **Threshold = 10 hardcoded** trong constant ở BE (`LowStockService.LOW_STOCK_THRESHOLD = 10`). KHÔNG env var giai đoạn này (requirement nói "threshold configurable trong code" = constant để dễ đổi sau, không phải env runtime).

- **D-09:** **Endpoint:** `GET /admin/products/charts/low-stock` (per-svc product-svc) → trả về **toàn bộ SP có stock<10, sort by stock ASC, cap 50 rows** (tránh response quá lớn nếu seed catalog có nhiều SP low-stock).
  - Response shape: `[{id, name, brand, thumbnailUrl, stock}, ...]`
  - Empty state (KHÔNG SP nào low-stock) → trả `[]`, FE render placeholder "Tất cả sản phẩm đủ hàng ✓".

- **D-10:** **FE display:** card section title + list rows. Mỗi row: thumbnail (40x40) + name + brand + stock badge (đỏ nếu <5, cam nếu 5-9) + nút "Sửa". Click row hoặc nút "Sửa" → `router.push('/admin/products?highlight={productId}')`. Giai đoạn này highlight optional — nếu page admin/products chưa support query param, click chỉ điều hướng tới list (planner cân nhắc thêm small enhancement page admin/products đọc query param highlight để scroll/open edit modal).

### Visual + State Handling

- **D-11:** **Recharts 3.8.1** (đã lock ROADMAP) — components dùng: `LineChart`, `BarChart`, `PieChart`, `ResponsiveContainer`, `XAxis`, `YAxis`, `Tooltip`, `Legend`, `CartesianGrid`. Import từ `recharts`. `npm install recharts@3.8.1` ở `sources/frontend`.

- **D-12:** **Color palette:**
  - Revenue line/area: `var(--primary)` (xanh dương đã có).
  - Top-products bar: `var(--secondary)`.
  - User signups line: `#f59e0b` (cam, khớp KPI customer card hiện có).
  - **Order-status pie semantic colors** (override CSS vars):
    - `PENDING` → `#f59e0b` (amber)
    - `CONFIRMED` → `#3b82f6` (blue)
    - `SHIPPED` → `#06b6d4` (cyan)
    - `DELIVERED` → `#10b981` (green)
    - `CANCELLED` → `#dc2626` (red)
  - **Why:** semantic màu giúp admin scan trạng thái nhanh; revenue/topproducts/signups dùng CSS vars để consistent theme.

- **D-13:** **Tooltip + Legend tiếng Việt:** số liệu format `Intl.NumberFormat('vi-VN')` (revenue thêm hậu tố " ₫"). Date axis format `DD/MM` (dùng `Intl.DateTimeFormat('vi-VN')`). Status labels Vietnamese: `Chờ xử lý / Đã xác nhận / Đang giao / Đã giao / Đã huỷ` (helper map status → label, có thể extract sang shared module).

- **D-14:** **State handling pattern** — giữ `Promise.allSettled` per-chart Phase 9. Mỗi chart card có 3 state: `loading` (skeleton block 200px height), `success` (chart render), `error` (text "--" + retry icon ⟳ y hệt KPI card pattern). Code reuse: extract `<ChartCard>` wrapper component nhận `state` + `renderChart` props (giống KpiCard hiện có).

- **D-15:** **Empty data state per chart:**
  - Revenue/signups (line, 0 points): render chart tối thiểu với axis + text overlay "Chưa có dữ liệu trong khoảng này".
  - Top-products (0 rows): card body chỉ render text "Chưa có sản phẩm bán ra trong khoảng này".
  - Order-status (0 orders total): "Chưa có đơn hàng nào".
  - Low-stock (0 rows): "Tất cả sản phẩm đủ hàng ✓".

### Claude's Discretion

- Cụ thể CSS module structure cho `<ChartCard>` (planner quyết: tách file riêng hay extend `page.module.css`).
- Có thêm test E2E Playwright hay không (giữ TEST-02 deferred policy — verifier handle qua artifact-level kiểm tra hiển thị + manual UAT).
- Naming chính xác cho service/repository methods phía BE (planner chọn theo convention codebase).
- Có cần add `@Cacheable` cho chart endpoints (defer — premature optimization).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Roadmap & requirements
- `.planning/ROADMAP.md` §"Phase 19: Hoàn Thiện Admin: Charts + Low-Stock" — goal + 5 success criteria + recharts lock
- `.planning/REQUIREMENTS.md` lines 51–55 — ADMIN-01..05 chi tiết
- `.planning/PROJECT.md` §"Active (v1.3)" — visible-first priority

### Prior phase context (pattern source-of-truth)
- `.planning/phases/09-residual-closure-verification/09-02-PLAN.md` — D-04/D-05/D-06 backend stats pattern (per-svc /admin/* + JwtRoleGuard manual)
- `.planning/phases/09-residual-closure-verification/09-04-PLAN.md` — D-08/D-09 dashboard FE pattern (Promise.allSettled, per-card skeleton+retry)
- `.planning/phases/17-s-a-order-detail-items/17-CONTEXT.md` — useEnrichedItems hook + cross-svc enrichment pattern (inform top-products D-03 BE-side enrichment choice)
- `.planning/phases/16-seed-catalog-realistic/16-CONTEXT.md` — catalog data shape (productId UUID, brand, thumbnailUrl Unsplash)

### Existing code (must read before extending)
- `sources/frontend/src/app/admin/page.tsx` — current AdminDashboard component (KPI cards + Promise.allSettled pattern to extend)
- `sources/frontend/src/app/admin/page.module.css` — dashboard styles to extend
- `sources/frontend/src/services/stats.ts` — fetchOrderStats/Product/User pattern to mirror for chart fetchers
- `sources/frontend/src/services/http.ts` — httpGet (auto Bearer token attach)
- `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/AdminStatsController.java` — controller pattern reference
- `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderStatsService.java` — service pattern + @Transactional readonly
- `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/JwtRoleGuard.java` — manual JWT role guard reused
- `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/OrderEntity.java` — fields: status, total, createdAt
- `sources/backend/api-gateway/src/main/resources/application.yml` — gateway routes to extend (4 new routes)
- `sources/frontend/src/app/admin/products/page.tsx` — admin products page (target nav for low-stock click; lines 52–145 cho thấy edit modal pattern)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `KpiCard<T>` pattern (page.tsx:103–139) — extract concept thành `<ChartCard>` wrapper với cùng 3-state model (loading/success/error+retry).
- `Promise.allSettled` orchestration (page.tsx:57–60) — extend cho 5 fetches mới (4 charts + low-stock).
- `httpGet<T>` (services/http.ts) — auto Bearer; mirror để add chart fetchers.
- `JwtRoleGuard.requireAdmin(authHeader)` — reuse y hệt cho 4 chart controllers mới.
- `OrderRepository` Spring Data — extend với @Query JPQL aggregation methods (revenue by day, top products by qty, status counts, signups by day).
- Thumbnail URL pattern Phase 16 (Unsplash WebP) — top-products + low-stock đã có sẵn ở ProductEntity.

### Established Patterns
- Per-svc admin endpoints under `/admin/{resource}/...` mapped via gateway `/api/{svc}/admin/...` rewrite.
- `ApiResponse.of(200, msg, data)` envelope cho mọi endpoint.
- `@Transactional(readOnly = true)` cho query-only services.
- CSS modules per page (page.module.css), CSS vars `--primary/--secondary/--on-surface/--surface` cho theming.
- Vietnamese UI labels everywhere (memory: feedback_language).

### Integration Points
- AdminDashboard component (`/admin/page.tsx`) — extended to include time-window dropdown + chart grid + low-stock section sau KPI cards.
- API Gateway `application.yml` — 4 new routes:
  - `/api/orders/admin/charts/**` → order-svc
  - `/api/users/admin/charts/**` → user-svc
  - `/api/products/admin/charts/**` → product-svc
  - `/api/products/admin/batch` → product-svc (POST batch fetch cho top-products enrichment)
- product-svc cần expose `POST /admin/products/batch` nhận `{ids:[...]}` trả minimal projection cho enrichment.
- Cross-svc HTTP call: order-svc → product-svc dùng `RestClient` (Spring 6.1+) có sẵn không? Nếu chưa có pattern, planner add bean config (URL từ application.yml `services.product-service.url`).

</code_context>

<specifics>
## Specific Ideas

- User confirmed "auto chọn theo recommend + chain các flow gsd" — mọi gray area còn lại đã chốt theo recommended option, planner tự do trên Claude's Discretion items.
- Recharts version 3.8.1 — KHÔNG bump. ResponsiveContainer wrap mọi chart để fit grid cell.
- BE fill empty days với value=0 (D-05) — important cho line chart không bị skip discontinuity.
- Order-status pie giữ snapshot hiện tại (KHÔNG dùng time-window) — match SC #3 wording "phân phối trạng thái đơn hàng" hiện tại của hệ thống.

</specifics>

<deferred>
## Deferred Ideas

- **Custom date picker (from-to)** — ADMIN-07 đã trong REQUIREMENTS.md deferred bucket, không thêm phase này.
- **Per-chart dropdown time-window** — nếu admin sau này cần compare cross-window, mở rộng từ D-06.
- **Auto rollup weekly/monthly cho range="all"** — defer khi data thực sự lớn (D-05 chốt daily).
- **Env var `LOW_STOCK_THRESHOLD`** — defer (D-08 hardcode constant đủ giai đoạn này).
- **CSV export charts data, drill-down click chart point** — out of scope.
- **Real-time chart updates (WebSocket/SSE)** — out of scope (PROJECT.md "no real-time").
- **Cache `@Cacheable` chart endpoints** — premature optimization, defer khi đo được latency thực.
- **Highlight query param ở /admin/products** — D-10 nói optional; nếu skip, click low-stock chỉ điều hướng tới list không scroll/open modal.

</deferred>

---

*Phase: 19-ho-n-thi-n-admin-charts-low-stock*
*Context gathered: 2026-05-02*
