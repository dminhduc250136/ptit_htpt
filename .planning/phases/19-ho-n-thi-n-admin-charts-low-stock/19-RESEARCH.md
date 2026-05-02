# Phase 19: Hoàn Thiện Admin: Charts + Low-Stock - Research

**Researched:** 2026-05-02
**Domain:** Admin analytics dashboard (Spring Boot aggregation + Next.js Recharts SVG rendering + cross-svc enrichment)
**Confidence:** HIGH

## Summary

Phase 19 mở rộng `/admin` dashboard hiện tại (KPI cards Phase 9) thêm 4 analytics charts (revenue, top-products, status pie, signups) + low-stock alert section. Tất cả 15 implementation decisions đã chốt trong CONTEXT.md (D-01..D-15) — research này tập trung verify khả thi kỹ thuật, identify pitfalls, cung cấp code patterns sẵn dùng cho planner.

Backend: 5 endpoint mới (3 ở order-svc, 1 ở user-svc, 1 ở product-svc) + 1 batch endpoint product-svc cho top-products enrichment. Reuse y hệt pattern `/admin/{resource}/stats` Phase 9: `JwtRoleGuard.requireAdmin(authHeader)` manual check + `ApiResponse.of(...)` envelope + `@Transactional(readOnly=true)`. Aggregation queries đi qua JPQL `GROUP BY` với BE-side gap fill (Java fill empty days). Cross-svc call order-svc → product-svc bằng `RestTemplate` (đã có bean trong `AppConfig`, pattern y hệt `OrderCrudService` line 365–419).

Frontend: `npm install recharts@3.8.1` (verified npm registry latest, published 2026-03-25). Extract `<ChartCard>` wrapper component reusing `KpiCard` 3-state model (loading skeleton / success render / error+retry). Time-window dropdown global controlling 3 charts (revenue/top-products/signups) qua `Promise.allSettled` per-chart refetch. Vietnamese formatters: `Intl.NumberFormat('vi-VN')` cho VND + `Intl.DateTimeFormat('vi-VN', {day:'2-digit', month:'2-digit'})` cho axis labels.

**Primary recommendation:** Implement theo plan đã chốt trong CONTEXT.md. Lưu ý 3 điểm pitfall HIGH-confidence: (1) ResponsiveContainer cần parent có height cố định (KHÔNG để collapse 0px), (2) Recharts `Cell` deprecated từ 3.7 — dùng `shape` prop hoặc fill array thay thế cho pie semantic colors, (3) JPQL `GROUP BY` không native cho `DATE_TRUNC` Postgres → dùng native query hoặc Java-side aggregation (recommend Java-side để portable + dễ test).

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Backend API Shape:**
- **D-01:** 1 endpoint per chart, per service. order-svc 3 endpoints (`/admin/orders/charts/revenue`, `/top-products`, `/status-distribution`), user-svc 1 (`/admin/users/charts/signups`), product-svc 1 (`/admin/products/charts/low-stock`). + product-svc `POST /admin/products/batch` cho enrichment. Gateway routes pattern `/api/{svc}/admin/charts/**` → svc.
- **D-02:** Tái dùng `JwtRoleGuard.requireAdmin(authHeader)` manual JWT check, KHÔNG `@PreAuthorize`. Path controller bắt đầu `/admin/{resource}/charts`.
- **D-03:** Top-products enrichment ở backend — order-svc query top-10 từ `order_items` + `RestClient`/`RestTemplate` gọi product-svc batch endpoint. Fallback: product-svc fail → trả productId+qty với name=`"Product {id[:8]}"`, KHÔNG fail toàn endpoint.
- **D-04:** Time-window param `range` ∈ `7d | 30d | 90d | all`. Invalid → 400. Mapping: `Instant from = Instant.now().minus(N, ChronoUnit.DAYS)`, `all` bỏ filter.
- **D-05:** Daily granularity cho mọi range (kể cả `all`). BE fill empty days với `value: 0` / `count: 0`.

**Time-window UX + Layout:**
- **D-06:** 1 dropdown global ở top dashboard (sau KPI cards), điều khiển 3 charts có time-window (revenue, top-products, signups). Order-status pie KHÔNG bị ảnh hưởng (snapshot toàn bộ).
- **D-07:** Layout dọc: KPI cards → dropdown → charts grid 2x2 `[Revenue|Top Products] / [Order Status|User Signups]` → low-stock card full-width cuối. Mobile breakpoint → 1 cột.

**Low-stock Section:**
- **D-08:** Threshold = 10 hardcoded constant `LowStockService.LOW_STOCK_THRESHOLD = 10`. KHÔNG env var.
- **D-09:** Endpoint `GET /admin/products/charts/low-stock` → SP có `stock<10`, sort by stock ASC, cap 50 rows. Empty → `[]`.
- **D-10:** FE display: card section title + list rows (thumbnail 40x40 + name + brand + stock badge đỏ<5/cam 5–9 + nút "Sửa"). Click → `router.push('/admin/products?highlight={productId}')`. Highlight optional.

**Visual + State:**
- **D-11:** Recharts 3.8.1, components: `LineChart`, `BarChart`, `PieChart`, `ResponsiveContainer`, `XAxis`, `YAxis`, `Tooltip`, `Legend`, `CartesianGrid`. `npm install recharts@3.8.1` ở `sources/frontend`.
- **D-12:** Color palette: revenue=`var(--primary)`, top-products=`var(--secondary)`, signups=`#f59e0b`. Pie semantic: PENDING=#f59e0b, CONFIRMED=#3b82f6, SHIPPED=#06b6d4, DELIVERED=#10b981, CANCELLED=#dc2626.
- **D-13:** Tooltip + Legend tiếng Việt: `Intl.NumberFormat('vi-VN')` (revenue + " ₫"), date `DD/MM` `Intl.DateTimeFormat('vi-VN')`. Status labels: Chờ xử lý/Đã xác nhận/Đang giao/Đã giao/Đã huỷ.
- **D-14:** Promise.allSettled per-chart. Mỗi chart card: loading skeleton 200px / success render / error "--" + retry ⟳. Extract `<ChartCard>` wrapper.
- **D-15:** Empty data state per chart (text overlay tiếng Việt theo từng loại).

### Claude's Discretion

- CSS module structure cho `<ChartCard>` (file riêng vs extend `page.module.css`).
- Có thêm test E2E Playwright hay không (giữ TEST-02 deferred policy — verifier handle).
- Naming chính xác cho service/repository methods phía BE.
- Có cần `@Cacheable` cho chart endpoints (defer — premature optimization).

### Deferred Ideas (OUT OF SCOPE)

- Custom date picker (from-to) — ADMIN-07 deferred.
- Per-chart dropdown time-window.
- Auto rollup weekly/monthly cho range="all".
- Env var `LOW_STOCK_THRESHOLD`.
- CSV export, drill-down click chart point.
- Real-time chart updates (WebSocket/SSE).
- `@Cacheable` chart endpoints.
- Highlight query param ở `/admin/products` (D-10 nói optional).
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ADMIN-01 | Revenue chart (line/area) — orders DELIVERED, dropdown 7d/30d/90d/all default 30d | Pattern §Backend Pattern 1 (revenue JPQL aggregation + Java gap fill) + §FE Pattern 1 (Recharts LineChart) |
| ADMIN-02 | Top-10 products bán chạy (bar chart) trong window đã chọn | Pattern §Backend Pattern 2 (top-products JPQL + cross-svc enrichment) + §FE Pattern 2 (Recharts BarChart) |
| ADMIN-03 | Order status distribution (pie/donut chart) — counts pending/confirmed/shipped/delivered/cancelled | Pattern §Backend Pattern 3 (status COUNT GROUP BY) + §FE Pattern 3 (PieChart + per-slice fill array) |
| ADMIN-04 | User signups theo thời gian (line chart) — daily new user count | Pattern §Backend Pattern 4 (user-svc signups JPQL aggregation) + §FE Pattern 1 reused |
| ADMIN-05 | Low-stock alert: SP `stock<10`, threshold configurable trong code, click → trang admin products edit | Pattern §Backend Pattern 5 (ProductRepository derived query) + §FE Pattern 4 (low-stock card list) |
</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Revenue aggregation by day | API/Backend (order-svc) | Database (Postgres) | DELIVERED orders + daily SUM(total) phải tính BE-side để FE render thẳng; gap-fill ở Java cho portable. |
| Top-products by qty | API/Backend (order-svc + product-svc) | — | Aggregation từ order_items + cross-svc enrichment ở BE (D-03) → FE 1 round-trip. |
| Status distribution | API/Backend (order-svc) | — | Snapshot COUNT GROUP BY status — nhỏ, BE-only. |
| User signups by day | API/Backend (user-svc) | — | Tương tự revenue, BE aggregation + gap fill. |
| Low-stock list | API/Backend (product-svc) | — | Threshold 10 + sort + cap 50 — derived query JPA, BE-only. |
| Cross-svc enrichment (order-svc → product-svc) | API/Backend (RestTemplate) | — | Đặt ở BE để giảm round-trip FE; fallback name=`Product {id[:8]}`. |
| Chart rendering (SVG) | Browser/Client (Next.js client component) | — | Recharts SVG — chỉ chạy client-side, KHÔNG SSR (RechartsResponsiveContainer cần `window.ResizeObserver`). |
| Time-window state + refetch orchestration | Browser/Client | — | `useState<'7d'|'30d'|'90d'|'all'>('30d')` + `Promise.allSettled` per-card. |
| Vietnamese formatting (Intl.NumberFormat / Intl.DateTimeFormat) | Browser/Client | — | Format ngay trước render — KHÔNG cần BE format. |
| Auth gate per endpoint | API/Backend (JwtRoleGuard) | — | Manual JWT check tái dùng Phase 9 D-05 pattern. |
| Gateway route mapping `/api/{svc}/admin/charts/**` | Edge (api-gateway) | — | Spring Cloud Gateway RewritePath, đồng nhất pattern hiện có. |

## Standard Stack

### Core (FE - new)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `recharts` | 3.8.1 | SVG-based React chart library, declarative JSX API | [VERIFIED: npm view recharts dist-tags] latest 3.8.1, published 2026-03-25. ROADMAP đã lock. React 19 compatible (verified release notes 3.x). |

**Installation (FE):**
```bash
cd sources/frontend
npm install recharts@3.8.1
```

**Verified version:** [VERIFIED: `npm view recharts@3.8.1 version`] = `3.8.1`, published 2026-03-25 — đây là `latest` dist-tag tại thời điểm research. KHÔNG bump.

### Core (BE - reused, no new deps)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 3.3.2 | [VERIFIED: pom.xml order-service] đã có | Reused — KHÔNG add deps mới |
| Spring Data JPA | (Spring Boot BOM) | JPQL aggregation + derived queries | Reused — `@Query` JPQL pattern đã có nhiều ví dụ |
| `RestTemplate` | (Spring Web) | Cross-svc HTTP call order-svc → product-svc | [VERIFIED: `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/AppConfig.java`] @Bean đã sẵn. Pattern hiện hữu trong `OrderCrudService` lines 365–419 (gọi product-svc qua api-gateway URL `http://api-gateway:8080/api/products/{id}`). |

**KHÔNG dùng `RestClient` (Spring 6.1+):** mặc dù có sẵn (Spring Boot 3.3.2 → Spring Framework 6.1), codebase chuẩn hoá `RestTemplate`. Đổi sang `RestClient` ở phase này sẽ phá nhất quán; defer migration. [CITED: existing `OrderCrudService.java`, `ReviewService.java` (product-svc) đều dùng `RestTemplate`]

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Recharts | Chart.js + react-chartjs-2 | Chart.js render bằng `<canvas>` (perf tốt hơn cho big datasets >10k points), nhưng JSX API kém composable + không match React-idiomatic style đã chuẩn. Recharts SVG đủ cho dataset Phase 19 (~30 days, ~10 products). [ASSUMED] |
| Recharts | Apache ECharts (echarts-for-react) | Mạnh hơn nhưng heavyweight (bundle ~900KB vs Recharts ~250KB minified). Không đáng cho 4 simple charts. [ASSUMED] |
| BE-side Postgres `DATE_TRUNC` aggregation | Java-side aggregation (Stream `groupingBy`) | Postgres `DATE_TRUNC` nhanh hơn cho dataset lớn nhưng yêu cầu native query (JPQL không support). Java-side dễ test (DataJpaTest), portable, đủ nhanh cho dataset Phase 19. **Recommend Java-side.** |
| `RestTemplate` cross-svc | Feign client | Feign declarative đẹp hơn nhưng cần thêm dependency `spring-cloud-starter-openfeign` + cấu hình. KHÔNG worth cho 1 endpoint mới. |

## Architecture Patterns

### System Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│ Browser (Next.js client component /admin/page.tsx)                      │
│                                                                         │
│  [KPI Cards row]  ─── (already Phase 9, unchanged)                      │
│                                                                         │
│  [Time-Window Dropdown: 7d|30d|90d|all]                                 │
│         │                                                               │
│         │ onChange → setRange() → triggers refetch of 3 charts          │
│         ▼                                                               │
│  Promise.allSettled([                                                   │
│    fetchRevenueChart(range),   ─┐                                       │
│    fetchTopProducts(range),    ─┤── per-card state machine             │
│    fetchStatusDist(),          ─┤   (loading|success|error+retry)       │
│    fetchUserSignups(range),    ─┤                                       │
│    fetchLowStock()             ─┘                                       │
│  ])                                                                     │
│         │                                                               │
│         ▼                                                               │
│  ┌──────────────┬──────────────┐                                        │
│  │  <ChartCard> │  <ChartCard> │   2x2 grid                             │
│  │  (Revenue)   │ (TopProducts)│   each renders Recharts                │
│  ├──────────────┼──────────────┤   ResponsiveContainer with             │
│  │  <ChartCard> │  <ChartCard> │   parent height: 250px                 │
│  │ (StatusPie)  │  (Signups)   │                                        │
│  └──────────────┴──────────────┘                                        │
│  ┌──────────────────────────────┐                                       │
│  │  <LowStockCard> (full-width) │                                       │
│  └──────────────────────────────┘                                       │
└──────────────┬──────────────────────────────────────────────────────────┘
               │ HTTP (Authorization: Bearer <token>)
               ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ Spring Cloud Gateway (api-gateway:8080) — application.yml routes        │
│                                                                          │
│ /api/orders/admin/charts/**   → RewritePath → order-svc:8080            │
│ /api/users/admin/charts/**    → RewritePath → user-svc:8080             │
│ /api/products/admin/charts/** → RewritePath → product-svc:8080          │
│ /api/products/admin/batch     → RewritePath → product-svc:8080          │
└──────────────┬───────────┬──────────────┬───────────────────────────────┘
               │           │              │
               ▼           ▼              ▼
       ┌────────────┐ ┌──────────┐ ┌──────────────┐
       │ order-svc  │ │ user-svc │ │ product-svc  │
       │            │ │          │ │              │
       │ Controllers│ │ Controllers│ Controllers  │
       │ +JwtRoleGuard.requireAdmin(authHeader)  │
       │            │ │          │ │              │
       │ Services   │ │ Services │ │ Services     │
       │ @Trans(RO) │ │ @Trans(RO)│ │ @Trans(RO)  │
       │            │ │          │ │              │
       │ Repository │ │ Repo     │ │ Repository   │
       │  @Query    │ │  @Query  │ │  derived     │
       │  JPQL      │ │  JPQL    │ │              │
       └─────┬──────┘ └────┬─────┘ └──────┬───────┘
             │             │              │
             └─────┬───────┴──────────────┘
                   ▼
            ┌──────────────┐
            │  Postgres    │
            │  schemas:    │
            │ order_svc    │
            │ user_svc     │
            │ product_svc  │
            └──────────────┘

         Cross-svc enrichment (D-03):
         order-svc TopProductsService
              │ RestTemplate.exchange(POST)
              ▼
         http://api-gateway:8080/api/products/admin/batch
         body: {"ids":[uuid1,uuid2,...]}
         → product-svc returns [{id,name,brand,thumbnailUrl}]
         → fallback: timeout/5xx → name="Product {id[:8]}", KHÔNG fail toàn endpoint
```

### Recommended Project Structure (delta only)

```
sources/frontend/
├── package.json                         # +recharts@3.8.1
├── src/
│   ├── app/admin/
│   │   ├── page.tsx                     # extend: + dropdown + charts grid + low-stock
│   │   └── page.module.css              # extend: + .chartsGrid, .chartCard, .lowStockCard
│   ├── components/admin/                # NEW dir
│   │   ├── ChartCard.tsx                # wrapper: 3-state (loading/success/error+retry)
│   │   ├── ChartCard.module.css
│   │   ├── RevenueChart.tsx             # Recharts LineChart wrapper
│   │   ├── TopProductsChart.tsx         # Recharts BarChart wrapper
│   │   ├── StatusDistributionChart.tsx  # Recharts PieChart wrapper
│   │   ├── UserSignupsChart.tsx         # Recharts LineChart wrapper
│   │   └── LowStockSection.tsx          # full-width list section
│   ├── services/
│   │   └── charts.ts                    # NEW: 5 chart fetchers (mirror stats.ts pattern)
│   └── lib/
│       └── chartFormat.ts               # NEW: vnNumber, vnDate, statusLabel, statusColor

sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/
├── web/AdminChartsController.java       # NEW: 3 endpoints + JwtRoleGuard
├── service/OrderChartsService.java      # NEW: aggregation + cross-svc enrichment
├── service/ProductBatchClient.java      # NEW: RestTemplate wrapper + fallback
└── repository/OrderRepository.java      # extend: 3 @Query methods

sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/
├── web/AdminChartsController.java       # NEW: 1 endpoint + JwtRoleGuard
├── service/UserChartsService.java       # NEW
└── repository/UserRepository.java       # extend: 1 @Query

sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/
├── web/AdminChartsController.java       # NEW: low-stock endpoint
├── web/AdminProductBatchController.java # NEW: POST /admin/products/batch
├── service/LowStockService.java         # NEW: + LOW_STOCK_THRESHOLD = 10
└── repository/ProductRepository.java    # extend: findLowStock + findAllByIdIn

sources/backend/api-gateway/src/main/resources/application.yml
                                         # extend: 4 new routes (3 chart families + batch)
```

### Backend Pattern 1: Revenue aggregation (JPQL + Java gap fill)

```java
// OrderRepository.java — JPQL daily aggregation
@Query("""
    SELECT FUNCTION('DATE', o.createdAt) AS day, SUM(o.total) AS total
    FROM OrderEntity o
    WHERE o.status = 'DELIVERED'
      AND (cast(:from as timestamp) IS NULL OR o.createdAt >= :from)
    GROUP BY FUNCTION('DATE', o.createdAt)
    ORDER BY day ASC
    """)
List<Object[]> aggregateRevenueByDay(@Param("from") Instant from);

// OrderChartsService.java — gap fill in Java
@Transactional(readOnly = true)
public List<RevenuePoint> revenueByDay(Range range) {
  Instant from = range.toFromInstant();          // null nếu "all"
  Map<LocalDate, BigDecimal> raw = orderRepo.aggregateRevenueByDay(from).stream()
      .collect(Collectors.toMap(
          row -> ((java.sql.Date) row[0]).toLocalDate(),
          row -> (BigDecimal) row[1]));
  // Fill gaps: từ `from` → today, mỗi ngày KHÔNG có data → BigDecimal.ZERO
  LocalDate start = from != null
      ? from.atZone(ZoneId.systemDefault()).toLocalDate()
      : raw.keySet().stream().min(Comparator.naturalOrder()).orElse(LocalDate.now());
  LocalDate end = LocalDate.now();
  List<RevenuePoint> points = new ArrayList<>();
  for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
    points.add(new RevenuePoint(d.toString(), raw.getOrDefault(d, BigDecimal.ZERO)));
  }
  return points;
}

public record RevenuePoint(String date, BigDecimal value) {}
```

**Source:** [VERIFIED via codebase] mirror pattern `OrderRepository.findByUserIdWithFilters` cho `cast(:from as timestamp) IS NULL` nullable param idiom. JPQL `FUNCTION('DATE', ...)` works trên Postgres dialect Hibernate 6.

### Backend Pattern 2: Top-products aggregation + cross-svc enrichment

```java
// OrderRepository.java
@Query("""
    SELECT i.productId, SUM(i.quantity) AS qtySold
    FROM OrderEntity o JOIN o.items i
    WHERE o.status = 'DELIVERED'
      AND (cast(:from as timestamp) IS NULL OR o.createdAt >= :from)
    GROUP BY i.productId
    ORDER BY qtySold DESC
    """)
List<Object[]> aggregateTopProducts(@Param("from") Instant from, Pageable limit);
// Caller: aggregateTopProducts(from, PageRequest.of(0, 10))

// ProductBatchClient.java — D-03 fallback wrapper
@Component
public class ProductBatchClient {
  private final RestTemplate restTemplate;
  private static final Logger log = LoggerFactory.getLogger(ProductBatchClient.class);

  public ProductBatchClient(RestTemplate restTemplate) { this.restTemplate = restTemplate; }

  /** Returns map productId → enrichment fields. Empty map nếu fail (fallback handled by caller). */
  public Map<String, ProductSummary> fetchBatch(List<String> ids) {
    if (ids.isEmpty()) return Map.of();
    try {
      String url = "http://api-gateway:8080/api/products/admin/batch";
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      // Reuse caller's auth: pass through admin JWT từ controller (cần forward authHeader)
      // — chi tiết wiring trong controller layer
      HttpEntity<Map<String, Object>> entity = new HttpEntity<>(Map.of("ids", ids), headers);
      ResponseEntity<ApiResponse<List<ProductSummary>>> resp = restTemplate.exchange(
          url, HttpMethod.POST, entity,
          new ParameterizedTypeReference<>() {});
      List<ProductSummary> data = resp.getBody().data();
      return data.stream().collect(Collectors.toMap(ProductSummary::id, p -> p));
    } catch (Exception ex) {
      log.warn("[D-03] product-svc batch enrichment failed, returning empty map for fallback", ex);
      return Map.of();
    }
  }

  public record ProductSummary(String id, String name, String brand, String thumbnailUrl) {}
}

// OrderChartsService.java — combine + fallback
public List<TopProductPoint> topProducts(Range range, String authHeader) {
  Instant from = range.toFromInstant();
  List<Object[]> raw = orderRepo.aggregateTopProducts(from, PageRequest.of(0, 10));
  List<String> ids = raw.stream().map(r -> (String) r[0]).toList();
  Map<String, ProductSummary> enriched = productBatchClient.fetchBatch(ids);
  return raw.stream().map(r -> {
    String id = (String) r[0];
    long qty = ((Number) r[1]).longValue();
    ProductSummary s = enriched.get(id);
    if (s != null) return new TopProductPoint(id, s.name(), s.brand(), s.thumbnailUrl(), qty);
    // D-03 fallback
    return new TopProductPoint(id, "Product " + id.substring(0, Math.min(8, id.length())), null, null, qty);
  }).toList();
}
```

**Source:** [VERIFIED via codebase] `OrderCrudService` lines 365–419 đã có pattern `restTemplate.getForObject("http://api-gateway:8080/api/products/" + id, Map.class)` — phase này extend sang `POST /admin/products/batch`. **Auth forwarding:** controller cần extract Bearer token từ `authHeader` đã verify, gắn vào `headers.set(HttpHeaders.AUTHORIZATION, authHeader)` để batch endpoint cũng pass `JwtRoleGuard`.

### Backend Pattern 3: Status distribution (snapshot)

```java
// OrderRepository.java
@Query("SELECT o.status, COUNT(o) FROM OrderEntity o GROUP BY o.status")
List<Object[]> aggregateStatusDistribution();

// OrderChartsService.java — KHÔNG range, snapshot toàn bộ (D-06)
@Transactional(readOnly = true)
public List<StatusPoint> statusDistribution() {
  return orderRepo.aggregateStatusDistribution().stream()
      .map(r -> new StatusPoint((String) r[0], ((Number) r[1]).longValue()))
      .toList();
}

public record StatusPoint(String status, long count) {}
```

### Backend Pattern 4: User signups (mirror Pattern 1)

```java
// UserRepository.java
@Query("""
    SELECT FUNCTION('DATE', u.createdAt) AS day, COUNT(u) AS cnt
    FROM UserEntity u
    WHERE (cast(:from as timestamp) IS NULL OR u.createdAt >= :from)
    GROUP BY FUNCTION('DATE', u.createdAt)
    ORDER BY day ASC
    """)
List<Object[]> aggregateSignupsByDay(@Param("from") Instant from);

// UserChartsService.java — gap fill identical to revenue, count thay value
```

### Backend Pattern 5: Low-stock derived query

```java
// ProductRepository.java
@Query("SELECT p FROM ProductEntity p WHERE p.stock < :threshold ORDER BY p.stock ASC")
List<ProductEntity> findLowStock(@Param("threshold") int threshold, Pageable cap);

// LowStockService.java
@Service
public class LowStockService {
  public static final int LOW_STOCK_THRESHOLD = 10;
  public static final int CAP = 50;

  private final ProductRepository productRepo;
  public LowStockService(ProductRepository productRepo) { this.productRepo = productRepo; }

  @Transactional(readOnly = true)
  public List<LowStockItem> list() {
    return productRepo.findLowStock(LOW_STOCK_THRESHOLD, PageRequest.of(0, CAP)).stream()
        .map(p -> new LowStockItem(p.id(), p.name(), p.brand(), p.thumbnailUrl(), p.stock()))
        .toList();
  }

  public record LowStockItem(String id, String name, String brand, String thumbnailUrl, int stock) {}
}
```

### Backend Pattern 6: Range enum + parsing

```java
public enum Range {
  D7(7), D30(30), D90(90), ALL(null);

  private final Integer days;
  Range(Integer days) { this.days = days; }

  public Instant toFromInstant() {
    return days == null ? null : Instant.now().minus(days, ChronoUnit.DAYS);
  }

  public static Range parse(String s) {
    return switch (s == null ? "30d" : s) {
      case "7d" -> D7;
      case "30d" -> D30;
      case "90d" -> D90;
      case "all" -> ALL;
      default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "Invalid range: " + s + " (expected 7d|30d|90d|all)");
    };
  }
}
```

### FE Pattern 1: Recharts LineChart (Revenue + Signups)

```tsx
// components/admin/RevenueChart.tsx
'use client';
import { LineChart, Line, XAxis, YAxis, Tooltip, CartesianGrid, ResponsiveContainer } from 'recharts';
import { vnNumber, vnDate } from '@/lib/chartFormat';

export interface RevenuePoint { date: string; value: number }

export function RevenueChart({ data }: { data: RevenuePoint[] }) {
  if (data.length === 0) return <p style={{ color: 'var(--on-surface-variant)' }}>Chưa có dữ liệu trong khoảng này</p>;
  return (
    <ResponsiveContainer width="100%" height={250}>
      <LineChart data={data} margin={{ top: 10, right: 16, left: 0, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="var(--outline)" />
        <XAxis
          dataKey="date"
          tickFormatter={(iso) => vnDate(iso)}
          tick={{ fontSize: 12 }}
        />
        <YAxis tickFormatter={(n) => vnNumber(n)} tick={{ fontSize: 12 }} />
        <Tooltip
          formatter={(value: number) => `${vnNumber(value)} ₫`}
          labelFormatter={(iso: string) => vnDate(iso)}
        />
        <Line type="monotone" dataKey="value" stroke="var(--primary)" strokeWidth={2} dot={false} />
      </LineChart>
    </ResponsiveContainer>
  );
}
```
**Source:** [CITED: recharts.org/en-US/api/LineChart] — composable JSX API standard pattern.

### FE Pattern 2: Recharts BarChart (Top Products)

```tsx
// components/admin/TopProductsChart.tsx
'use client';
import { BarChart, Bar, XAxis, YAxis, Tooltip, CartesianGrid, ResponsiveContainer } from 'recharts';
import { vnNumber } from '@/lib/chartFormat';

export interface TopProductPoint { productId: string; name: string; brand: string|null; qtySold: number }

export function TopProductsChart({ data }: { data: TopProductPoint[] }) {
  if (data.length === 0) return <p>Chưa có sản phẩm bán ra trong khoảng này</p>;
  // Truncate name cho horizontal-bar readability
  const display = data.map(d => ({ ...d, shortName: d.name.length > 20 ? d.name.slice(0, 20) + '…' : d.name }));
  return (
    <ResponsiveContainer width="100%" height={250}>
      <BarChart data={display} layout="vertical" margin={{ top: 10, right: 16, left: 80, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="var(--outline)" />
        <XAxis type="number" tickFormatter={(n) => vnNumber(n)} tick={{ fontSize: 12 }} />
        <YAxis type="category" dataKey="shortName" tick={{ fontSize: 11 }} width={120} />
        <Tooltip formatter={(v: number) => `${vnNumber(v)} sản phẩm`} />
        <Bar dataKey="qtySold" fill="var(--secondary)" />
      </BarChart>
    </ResponsiveContainer>
  );
}
```

### FE Pattern 3: Recharts PieChart (Status Distribution) — semantic per-slice colors

```tsx
// components/admin/StatusDistributionChart.tsx
'use client';
import { PieChart, Pie, Tooltip, Legend, ResponsiveContainer, Cell } from 'recharts';
import { statusLabel, STATUS_COLORS } from '@/lib/chartFormat';

export interface StatusPoint { status: string; count: number }

export function StatusDistributionChart({ data }: { data: StatusPoint[] }) {
  if (data.length === 0) return <p>Chưa có đơn hàng nào</p>;
  const display = data.map(d => ({ name: statusLabel(d.status), value: d.count, status: d.status }));
  return (
    <ResponsiveContainer width="100%" height={250}>
      <PieChart>
        <Pie data={display} dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius={80} innerRadius={40} label>
          {display.map((entry) => (
            <Cell key={entry.status} fill={STATUS_COLORS[entry.status] ?? 'var(--outline)'} />
          ))}
        </Pie>
        <Tooltip formatter={(v: number) => `${v} đơn`} />
        <Legend wrapperStyle={{ fontSize: 12 }} />
      </PieChart>
    </ResponsiveContainer>
  );
}

// lib/chartFormat.ts
export const STATUS_COLORS: Record<string, string> = {
  PENDING:   '#f59e0b',
  CONFIRMED: '#3b82f6',
  SHIPPED:   '#06b6d4',
  DELIVERED: '#10b981',
  CANCELLED: '#dc2626',
};
export function statusLabel(s: string): string {
  return ({
    PENDING:   'Chờ xử lý',
    CONFIRMED: 'Đã xác nhận',
    SHIPPED:   'Đang giao',
    DELIVERED: 'Đã giao',
    CANCELLED: 'Đã huỷ',
  } as Record<string,string>)[s] ?? s;
}
export const vnNumber = (n: number) => new Intl.NumberFormat('vi-VN').format(n);
export const vnDate = (iso: string) => {
  const d = new Date(iso);
  return new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit' }).format(d);
};
```

**Important caveat:** `Cell` component được [CITED: GitHub recharts releases v3.7.0] *"deprecated in favor of the `shape` prop"* — nhưng đến v3.8.1 vẫn hoạt động (chỉ là deprecated warning). Cell vẫn đủ dùng cho phase này; planner có thể chọn migrate sang `shape` callback nếu muốn future-proof. [VERIFIED: GitHub release notes 3.7.0]

**Alternative (3.7+ idiomatic):** dùng `fill` array + cycle, hoặc `shape={(props) => <CustomCell {...props} />}`. Cell vẫn render đúng — chỉ là cảnh báo console, không break.

### FE Pattern 4: Low-stock list section

```tsx
// components/admin/LowStockSection.tsx
'use client';
import { useRouter } from 'next/navigation';
import type { LowStockItem } from '@/services/charts';

export function LowStockSection({ data }: { data: LowStockItem[] }) {
  const router = useRouter();
  if (data.length === 0) return <p>Tất cả sản phẩm đủ hàng ✓</p>;
  return (
    <ul style={{ listStyle: 'none', padding: 0 }}>
      {data.map(item => (
        <li key={item.id} onClick={() => router.push(`/admin/products?highlight=${item.id}`)}
            style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)', padding: 'var(--space-3)', cursor: 'pointer', borderBottom: '1px solid var(--outline)' }}>
          {item.thumbnailUrl && <img src={item.thumbnailUrl} alt={item.name} width={40} height={40} style={{ objectFit: 'cover', borderRadius: 'var(--radius-sm)' }} />}
          <div style={{ flex: 1 }}>
            <p style={{ fontWeight: 'var(--weight-medium)' }}>{item.name}</p>
            {item.brand && <p style={{ fontSize: 'var(--text-body-sm)', color: 'var(--on-surface-variant)' }}>{item.brand}</p>}
          </div>
          <span style={{
            padding: '2px 8px', borderRadius: 'var(--radius-sm)', fontWeight: 'var(--weight-bold)',
            background: item.stock < 5 ? '#dc2626' : '#f59e0b', color: 'white'
          }}>
            Còn {item.stock}
          </span>
          <button type="button" onClick={(e) => { e.stopPropagation(); router.push(`/admin/products?highlight=${item.id}`); }}>
            Sửa
          </button>
        </li>
      ))}
    </ul>
  );
}
```

### FE Pattern 5: ChartCard wrapper (3-state, mirror KpiCard)

```tsx
// components/admin/ChartCard.tsx
'use client';
import { ReactNode } from 'react';
import styles from './ChartCard.module.css';

type CardState<T> = { status: 'loading' | 'success' | 'error'; data?: T; error?: string };

interface Props<T> {
  title: string;
  state: CardState<T>;
  renderChart: (data: T) => ReactNode;
  onRetry: () => void;
}

export function ChartCard<T>({ title, state, renderChart, onRetry }: Props<T>) {
  return (
    <div className={styles.chartCard}>
      <h3 className={styles.chartTitle}>{title}</h3>
      <div className={styles.chartBody}>
        {state.status === 'loading' && <div className={styles.skeleton} aria-label="Đang tải biểu đồ" />}
        {state.status === 'success' && state.data && renderChart(state.data)}
        {state.status === 'error' && (
          <div className={styles.errorRow}>
            <span style={{ color: 'var(--on-surface-variant)' }}>--</span>
            <button type="button" onClick={onRetry} aria-label={`Tải lại ${title}`} title={state.error}>⟳</button>
          </div>
        )}
      </div>
    </div>
  );
}
```

```css
/* ChartCard.module.css */
.chartCard {
  background: var(--surface-container-lowest);
  border-radius: var(--radius-xl);
  padding: var(--space-4);
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}
.chartTitle { font-size: var(--text-title-md); font-weight: var(--weight-bold); }
.chartBody { min-height: 250px; /* CRITICAL: prevent ResponsiveContainer collapse */ }
.skeleton {
  height: 250px; /* match chart height */
  background: linear-gradient(90deg, var(--surface-variant) 0%, var(--surface) 50%, var(--surface-variant) 100%);
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite;
  border-radius: var(--radius-md);
}
@keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }
```

### FE Pattern 6: services/charts.ts (mirror stats.ts)

```ts
import { httpGet, httpPost } from './http';

export interface RevenuePoint   { date: string; value: number }
export interface TopProductPoint{ productId: string; name: string; brand: string|null; thumbnailUrl: string|null; qtySold: number }
export interface StatusPoint    { status: string; count: number }
export interface SignupPoint    { date: string; count: number }
export interface LowStockItem   { id: string; name: string; brand: string|null; thumbnailUrl: string|null; stock: number }

export type Range = '7d' | '30d' | '90d' | 'all';

export const fetchRevenueChart    = (range: Range) => httpGet<RevenuePoint[]>(`/api/orders/admin/charts/revenue?range=${range}`);
export const fetchTopProducts     = (range: Range) => httpGet<TopProductPoint[]>(`/api/orders/admin/charts/top-products?range=${range}`);
export const fetchStatusDistrib   = ()             => httpGet<StatusPoint[]>(`/api/orders/admin/charts/status-distribution`);
export const fetchUserSignups     = (range: Range) => httpGet<SignupPoint[]>(`/api/users/admin/charts/signups?range=${range}`);
export const fetchLowStock        = ()             => httpGet<LowStockItem[]>(`/api/products/admin/charts/low-stock`);
```

### Anti-Patterns to Avoid

- **Đặt enrichment ở FE thay vì BE (D-03 violation):** sẽ tạo 11 round-trips (1 list + 10 detail) — slow, defeats Promise.allSettled granularity. **Always enrich BE-side cho top-products.**
- **Promise.all thay Promise.allSettled:** 1 chart fail → toàn dashboard fail. Đã ghi rõ D-14.
- **ResponsiveContainer KHÔNG có parent height:** collapse to 0px → chart invisible. Phải set parent `min-height: 250px` hoặc inline `height: {N}` trong ResponsiveContainer.
- **JPQL `DATE_TRUNC`:** không phải standard JPQL function — cần native query hoặc `FUNCTION('DATE', ...)`. **Recommend `FUNCTION('DATE', col)`** (Hibernate dispatch sang Postgres `DATE(col)`).
- **Server-side rendering Recharts:** Recharts cần `window.ResizeObserver` → SSR fail. AdminDashboard đã `'use client'` (✓).
- **Hardcode tiếng Anh status trong UI:** vi phạm D-13 + memory `feedback_language.md`. Always qua `statusLabel()` helper.
- **Quên forward Bearer token khi cross-svc call:** product-svc batch endpoint cũng `JwtRoleGuard.requireAdmin` → order-svc phải set `Authorization: Bearer ...` header trong RestTemplate exchange. Xem Pattern 2.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| SVG chart rendering | Custom `<svg>` line/bar/pie code | `recharts` (D-11 locked) | Edge cases: viewBox sizing, tick spacing, tooltip positioning, accessibility, responsive resize. Recharts đã solve. |
| Cross-svc HTTP client | Raw `HttpURLConnection` | `RestTemplate` (đã có `@Bean`) | Connection pooling, error mapping, retry semantics. |
| Vietnamese number/date format | Manual `toString().replace(/(\d)(?=(\d{3})+$)/g, '$1.')` | `Intl.NumberFormat('vi-VN')` + `Intl.DateTimeFormat('vi-VN')` | Locale-correct (dấu chấm vs phẩy, RTL, etc.); built into V8. |
| Date gap fill | Manual loop tự viết JS | Backend Java fill (D-05) | Đặt ở BE đảm bảo FE chỉ render — KHÔNG có business logic. Test BE-side rõ ràng. |
| Auth role check | `@PreAuthorize` (cần Spring Security setup) | `JwtRoleGuard.requireAdmin(authHeader)` | Phase 9 D-05 đã chốt manual check; reuse y hệt. |
| API envelope/error handling | Manual fetch wrapper | `httpGet<T>` (services/http.ts) | Auto Bearer attach, ApiResponse unwrap, ApiError throw. |

**Key insight:** Recharts đã chuẩn hoá toàn bộ chart primitives + responsive behavior. KHÔNG ai trong React ecosystem hand-roll SVG charts cho dashboard internal admin nữa — chỉ những case very specific (D3 custom viz) mới đi raw SVG. Phase 19 dataset nhỏ + simple shapes → Recharts hoàn toàn đủ.

## Common Pitfalls

### Pitfall 1: ResponsiveContainer collapse to 0×0 trong CSS Grid cell

**What goes wrong:** Charts không hiển thị, console không error. Inspect → `<div class="recharts-responsive-container">` có `width=0, height=0`.

**Why it happens:** `ResponsiveContainer` đo `getBoundingClientRect()` của parent. Nếu parent là CSS Grid cell với `align-items: stretch` và child không có explicit height → grid tính height = max(content) = 0 cho ResponsiveContainer (vì nó cần parent biết height trước).

**How to avoid:**
- ChartCard `chartBody` set `min-height: 250px` (xem Pattern 5 CSS).
- Hoặc set `<ResponsiveContainer width="100%" height={250}>` (numeric height, không "100%").
- KHÔNG dùng `height="100%"` mà parent flex/grid không define height.

**Warning signs:** chart card render rỗng nhưng skeleton trước đó hiển thị OK; resize window → chart suddenly appears.

**Source:** [CITED: recharts.org issue tracker — common ResponsiveContainer FAQ pattern]

### Pitfall 2: Recharts `Cell` deprecation từ v3.7

**What goes wrong:** Console warning `"Cell is deprecated, use shape prop instead"`. Vẫn render đúng nhưng noise trong dev console.

**Why it happens:** [VERIFIED: GitHub recharts release v3.7.0] team deprecate `Cell` để statically configure shapes.

**How to avoid:** Phase 19 chấp nhận warning — cell vẫn work. Future-proof: migrate sang `<Pie data={...} fill={(entry) => STATUS_COLORS[entry.status]}>` (function-as-prop) hoặc `shape` callback. **Recommend defer migration** — Cell deprecated vẫn supported throughout 3.x.

**Warning signs:** dev console warning ở /admin sau khi load.

### Pitfall 3: JPQL `GROUP BY DATE(col)` không work cho mọi dialect

**What goes wrong:** `IllegalArgumentException: validation failed for query "...GROUP BY DATE(o.createdAt)..."` — JPQL spec không có `DATE()` function chuẩn.

**Why it happens:** `DATE()` là Postgres-specific. Hibernate JPQL chỉ accept registered functions.

**How to avoid:** Dùng `FUNCTION('DATE', col)` syntax — Hibernate JPA spec allows passing native function name. Pattern này đã verified work với Hibernate 6 + Postgres dialect. Alternative: `@Query(nativeQuery=true)` với raw SQL `DATE(created_at)` hoặc `DATE_TRUNC('day', created_at)`.

**Warning signs:** Spring Boot startup log: `org.hibernate.query.SemanticException: Couldn't resolve function 'DATE' ...`

**Source:** [CITED: Hibernate 6 User Guide §JPQL function calls — `FUNCTION('name', args...)`]

### Pitfall 4: Cross-svc auth forwarding bị quên

**What goes wrong:** order-svc gọi `POST /api/products/admin/batch` → 401 từ product-svc → enrichment fallback kích hoạt → top-products chart hiển thị "Product abcd1234..." cho mọi item.

**Why it happens:** `RestTemplate.exchange()` không tự động attach token. order-svc phải pass `authHeader` đã verify từ controller xuống `ProductBatchClient.fetchBatch(ids, authHeader)`.

**How to avoid:**
1. Controller method: `topProducts(@RequestHeader(AUTHORIZATION) String authHeader, ...)` → pass xuống service.
2. Service forward authHeader xuống `ProductBatchClient.fetchBatch(ids, authHeader)`.
3. Client set `headers.set(HttpHeaders.AUTHORIZATION, authHeader)` trên `HttpEntity`.

**Warning signs:** Top-products chart luôn show fallback names dù product-svc up; product-svc log thấy `JwtRoleGuard` throw 401.

### Pitfall 5: Time-window dropdown fan-out N+1 fetch

**What goes wrong:** Đổi range → 3 charts re-fetch song song NHƯNG state update từng cái → re-render chain khiến other charts also re-fetch (over-fetching).

**Why it happens:** `useEffect([range, loadRevenue, loadTopProducts, ...])` — mỗi load function dependency thay đổi sẽ retrigger.

**How to avoid:**
- Wrap loaders trong `useCallback([range])` — chỉ thay đổi khi range đổi.
- KHÔNG put `range` trong dep của individual loader. Loader đọc range từ closure mới nhất, hoặc nhận range làm argument.
- Pattern recommend: `loadAllCharts(range)` orchestrator gọi `Promise.allSettled([loadRevenue(range), loadTopProducts(range), loadSignups(range)])`. Status pie + low-stock chỉ load 1 lần (mount).

**Warning signs:** Network tab thấy 4–6 chart fetches mỗi lần đổi dropdown thay vì đúng 3.

### Pitfall 6: Top-products bar chart truncate name → tooltip cũng truncate

**What goes wrong:** Hover bar thấy "iPhone 15 Pro Max 25…" thay vì full name.

**Why it happens:** Sample code Pattern 2 dùng `shortName` cho YAxis — nhưng Tooltip mặc định display dataKey value của tick (tức `shortName`).

**How to avoid:** Override `Tooltip content={<CustomTooltip />}` để render full `name` field. Hoặc giữ `name` field nguyên, chỉ truncate ở tick formatter (không trong data).

**Better pattern:**
```tsx
<YAxis type="category" dataKey="name" tickFormatter={(s) => s.length > 20 ? s.slice(0,20)+'…' : s} />
```

### Pitfall 7: Empty data render Recharts vẫn occupy 250px

**What goes wrong:** range="7d" có 0 DELIVERED orders → BE trả 7 days × value:0 → chart render flat line at 0 — looks broken (admin tưởng lỗi).

**Why it happens:** D-15 quy định empty state phải có text overlay rõ ràng.

**How to avoid:** Check `data.every(p => p.value === 0)` ở FE → render text "Chưa có dữ liệu trong khoảng này" thay vì flat chart. Hoặc check `data.length === 0` nếu BE trả `[]` cho range không có nào.

**Decision needed at plan time:** BE trả `[]` (FE check length) hay BE trả filled gaps with 0 (FE check `every === 0`)? CONTEXT D-05 nói "BE fill empty days" → FE check `every === 0`.

## Runtime State Inventory

Phase 19 KHÔNG phải rename/refactor/migration phase — đây là greenfield feature add. Section này omit theo template guidance.

**Nothing found in any category:** N/A — không có rename, không có cấu hình runtime cũ cần update.

## Code Examples

Tất cả examples thuộc §Architecture Patterns (Backend Pattern 1–6, FE Pattern 1–6). Không lặp lại ở đây.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Recharts 2.x `Cell` always idiomatic | Recharts 3.7+ deprecate `Cell` → `shape` prop | 3.7.0 (Jan 2025) | Cell still works in 3.8.1 — chỉ là warning, không break. Phase 19 dùng Cell OK. |
| Spring `RestTemplate` (Spring 5) | Spring `RestClient` (Spring 6.1+, Spring Boot 3.2+) | Spring Framework 6.1 (Nov 2023) | RestClient là fluent API kế thừa RestTemplate. Codebase chuẩn RestTemplate → giữ consistency. |
| Spring Security `@PreAuthorize` | Manual `JwtRoleGuard` (Phase 9 D-05) | 2026-04 (codebase decision) | Trade-off: less framework integration, more explicit. Phase 19 reuse. |

**Deprecated/outdated:**
- Recharts 2.x patterns: vẫn nhiều tutorial cũ trên web. Verify code mẫu cho Recharts 3.x trước khi copy. [VERIFIED: Recharts release v3.0 — substantial breaking changes vs 2.x dù docs không liệt kê chi tiết]

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Java-side gap fill hiệu năng đủ tốt cho dataset Phase 19 | Backend Pattern 1 | LOW — dataset ~30 days × ~vài chục orders/day = trivial. Worst case migrate sang native query DATE_TRUNC sau. |
| A2 | Recharts 3.8.1 `Cell` vẫn render đúng (chỉ deprecated warning) trong React 19 | FE Pattern 3 | MEDIUM — nếu Cell broken, planner fallback `shape` prop callback. Verify khi `npm install` xong: render 1 PieChart test. |
| A3 | `FUNCTION('DATE', o.createdAt)` JPQL syntax work với Hibernate 6 + Postgres dialect không cần custom dialect register | Pitfall 3 | MEDIUM — Phase planner test smoke ngay. Backup: switch native query. |
| A4 | Bundle size impact của recharts (~250KB minified) acceptable cho /admin route | §Alternatives | LOW — admin chỉ admin user load. Code-splitting Next.js tự cô lập route bundle. |
| A5 | Apache ECharts ~900KB bundle | §Alternatives | LOW — chỉ là comparison, không ảnh hưởng quyết định (đã lock recharts). |
| A6 | Chart.js render bằng canvas | §Alternatives | LOW — chỉ comparison. |
| A7 | DataJpaTest @AutoConfigureTestDatabase Replace.NONE + Testcontainers Postgres 16 đã work cho aggregation queries (theo OrderRepositoryJpaTest existing pattern) | Validation | LOW — pattern đã proven trong codebase. |

## Open Questions (RESOLVED)

1. **Auth forwarding cho cross-svc batch — kiến trúc đúng chưa?**
   - What we know: Pattern hiện tại (`OrderCrudService` line 365) gọi product-svc qua api-gateway URL — gateway KHÔNG re-check JWT (gateway hiện tại không có security filter). Nhưng product-svc `JwtRoleGuard` sẽ check.
   - What's unclear: Có nên thêm internal endpoint `/internal/products/batch` (no JWT, chỉ trust service-to-service network) hay buộc forward JWT admin?
   - RESOLVED: **Forward JWT admin** — admin token đã có ở browser, order-svc đã có nó từ request, đơn giản nhất + audit trail rõ. Adopted in Plan 19-01 ProductBatchClient + documented Pitfall 4.

2. **Highlight query param ở /admin/products?**
   - What we know: D-10 nói "optional".
   - What's unclear: Planner có muốn add nhỏ enhancement (đọc `?highlight=` → scroll-to-row + open edit modal) không?
   - RESOLVED: **Deferred** — Plan 19-04 LowStockSection navigate `/admin/products?highlight={productId}` query param có sẵn nhưng /admin/products không đọc; ghi vào CONTEXT.md Deferred Ideas cho follow-up phase.

3. **Test E2E Playwright cho 4 charts?**
   - What we know: TEST-02 deferred policy; verifier handle artifact-level + manual UAT.
   - What's unclear: Có spec smoke "ADM-CHART-1: render 4 chart cards" + "ADM-LOW-1: low-stock list rendered" giúp regression-guard không?
   - RESOLVED: **Add 2 smoke specs** (ADM-CHART-1 + ADM-LOW-1) — adopted in Plan 19-04 Task 3 (e2e/admin-charts.spec.ts + e2e/admin-low-stock.spec.ts), pattern theo existing admin-products.spec.ts.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Node.js + npm | FE recharts install | ✓ | (project chuẩn) | — |
| `recharts@3.8.1` (npm) | All charts | ✓ | 3.8.1 latest | None — locked |
| Spring Boot 3.3.2 | All BE charts | ✓ | 3.3.2 | — |
| Postgres 16 | Aggregation queries + TestContainers | ✓ | 16-alpine | — |
| `RestTemplate` @Bean order-svc | Cross-svc enrichment | ✓ | (Spring) | — |
| api-gateway running | Cross-svc URL `http://api-gateway:8080` | ✓ | — | service direct URL `http://product-service:8080` (alternative) |

**Missing dependencies with no fallback:** Không có. Tất cả deps đã sẵn hoặc install qua `npm install`.

**Missing dependencies with fallback:** Không có blocker.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Backend framework | JUnit 5 + Spring Boot Test + Testcontainers (Postgres 16-alpine) |
| Backend config | `pom.xml` per service (đã có), Testcontainers `@DataJpaTest`/`@SpringBootTest` patterns |
| Frontend framework | Playwright 1.59 (E2E), KHÔNG có unit test framework hiện tại |
| Backend run command (per svc) | `cd sources/backend/{svc} && ./mvnw test` (Maven wrapper) |
| Backend single test | `./mvnw test -Dtest=OrderChartsServiceTest` |
| Frontend Playwright | `cd sources/frontend && npx playwright test e2e/admin-charts.spec.ts` |
| Full suite | `./mvnw test` từng svc + `npx playwright test` từ FE |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| ADMIN-01 | Revenue endpoint trả daily aggregation gap-filled | unit (JPA + service) | `./mvnw test -Dtest=OrderChartsServiceTest#revenueByDay_fillsEmptyDays` | ❌ Wave 0 |
| ADMIN-01 | Revenue chart render trên /admin với data thật | E2E | `npx playwright test admin-charts.spec.ts -g "ADM-CHART-1"` | ❌ Wave 0 |
| ADMIN-02 | Top-products endpoint với enrichment OK | unit (service + RestTemplate mock) | `./mvnw test -Dtest=OrderChartsServiceTest#topProducts_enriches` | ❌ Wave 0 |
| ADMIN-02 | Top-products endpoint với product-svc 5xx → fallback name | unit | `./mvnw test -Dtest=OrderChartsServiceTest#topProducts_fallbackOnDownstreamError` | ❌ Wave 0 |
| ADMIN-03 | Status distribution snapshot trả 5 statuses | unit | `./mvnw test -Dtest=OrderChartsServiceTest#statusDistribution` | ❌ Wave 0 |
| ADMIN-04 | Signups endpoint daily aggregation gap-filled | unit (user-svc) | `./mvnw test -Dtest=UserChartsServiceTest#signupsByDay_fillsEmptyDays` | ❌ Wave 0 |
| ADMIN-05 | Low-stock endpoint trả SP stock<10, sort ASC, cap 50 | unit (product-svc) | `./mvnw test -Dtest=LowStockServiceTest#listsThresholdSorted` | ❌ Wave 0 |
| ADMIN-05 | Click low-stock item navigate /admin/products | E2E | `npx playwright test admin-charts.spec.ts -g "ADM-LOW-1"` | ❌ Wave 0 |
| ALL | Empty data state per chart hiển thị text VN | E2E (manual UAT acceptable) | manual checklist | — |
| ALL | Auth gate: unauthenticated → 401 cho mọi chart endpoint | unit (controller + JwtRoleGuard) | `./mvnw test -Dtest=AdminChartsControllerTest#requiresAdmin` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** unit test relevant service `./mvnw test -Dtest={Class}#{method}` (~10s)
- **Per wave merge:** full svc `./mvnw test` cho svc bị touched (~2 min/svc)
- **Phase gate:** all 3 svc full `./mvnw test` xanh + 1 Playwright spec admin-charts.spec.ts xanh trước `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `sources/backend/order-service/src/test/java/.../service/OrderChartsServiceTest.java` — covers ADMIN-01, ADMIN-02, ADMIN-03 (use `@Mock` cho ProductBatchClient để test enrichment + fallback)
- [ ] `sources/backend/order-service/src/test/java/.../repository/OrderRepositoryJpaTest.java` — extend với revenue + top-products + status JPQL aggregation tests (Testcontainers existing)
- [ ] `sources/backend/user-service/src/test/java/.../service/UserChartsServiceTest.java` — covers ADMIN-04
- [ ] `sources/backend/product-service/src/test/java/.../service/LowStockServiceTest.java` — covers ADMIN-05
- [ ] `sources/backend/{order,user,product}-service/src/test/java/.../web/AdminChartsControllerTest.java` — auth gate + 200 happy path (use `@WebMvcTest` + mock service)
- [ ] `sources/frontend/e2e/admin-charts.spec.ts` — 2 smoke specs:
  - `ADM-CHART-1`: load /admin → 4 chart card titles visible (Doanh thu / Sản phẩm bán chạy / Trạng thái đơn / Người dùng mới)
  - `ADM-LOW-1`: load /admin → low-stock section visible, click item → URL chuyển sang /admin/products
- [ ] No new framework install needed — JUnit + Testcontainers + Playwright đã có.

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | Bearer JWT (existing — Phase 9) |
| V3 Session Management | no | Stateless JWT — no session state to manage |
| V4 Access Control | yes | `JwtRoleGuard.requireAdmin(authHeader)` per endpoint (D-02) |
| V5 Input Validation | yes (range param) | Range enum parse → 400 cho invalid value (D-04) |
| V6 Cryptography | no | No new crypto (reuse JWT verification) |
| V11 Logging | partial | Log fallback events (`[D-03] product-svc batch enrichment failed`) — không log JWT |
| V13 API & Web Service | yes | RESTful + envelope (`ApiResponse.of(...)`) — consistent với existing |

### Known Threat Patterns for Spring Boot + Next.js admin dashboard

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Unauth role escalation (regular USER call admin endpoint) | Elevation of Privilege | `JwtRoleGuard.requireAdmin` — 403 nếu role claim không có ADMIN. Reuse Phase 9 D-05. |
| Token replay sau logout | Spoofing | JWT short expiry (existing setup) — defer hardening. Visible-first policy. |
| SSRF qua range parameter | Tampering | `range` validate enum, KHÔNG nhận URL hay path. Range.parse() throw 400. |
| Cross-svc request forgery (order-svc gọi product-svc với token user thường) | Elevation of Privilege | Forward authHeader đã verified ADMIN — product-svc cũng check ADMIN, double check OK. |
| Information disclosure qua chart data | Information Disclosure | Chart endpoints chỉ admin truy cập (D-02). Aggregated data không lộ PII (chỉ count/sum/productId). |
| XSS qua product name trong tooltip | Tampering | Recharts SVG escape text mặc định. Không inject `<script>` qua data. KHÔNG `dangerouslySetInnerHTML` ở components mới. |
| SQL injection qua range/threshold | Tampering | JPQL parameterized (`@Param`) — không string concat. ✓ |

**KHÔNG có security item nào thuộc D1..D17 backend hardening — phase này thuộc visible-first scope, security baseline đủ.**

## Sources

### Primary (HIGH confidence)
- **Codebase inspection** (đã đọc trực tiếp):
  - `sources/frontend/src/app/admin/page.tsx` (KpiCard pattern)
  - `sources/frontend/src/app/admin/page.module.css` (statsGrid + skeleton pattern)
  - `sources/frontend/src/services/stats.ts` + `services/http.ts`
  - `sources/backend/order-service/src/main/java/.../web/AdminStatsController.java`
  - `sources/backend/order-service/src/main/java/.../service/OrderStatsService.java`
  - `sources/backend/order-service/src/main/java/.../web/JwtRoleGuard.java`
  - `sources/backend/order-service/src/main/java/.../domain/OrderEntity.java` + `OrderItemEntity.java`
  - `sources/backend/order-service/src/main/java/.../repository/OrderRepository.java`
  - `sources/backend/order-service/src/main/java/.../service/OrderCrudService.java` (RestTemplate cross-svc pattern lines 365–419)
  - `sources/backend/order-service/src/main/java/.../AppConfig.java` (RestTemplate @Bean)
  - `sources/backend/product-service/src/main/java/.../web/AdminStatsController.java`
  - `sources/backend/product-service/src/main/java/.../web/AdminProductController.java`
  - `sources/backend/product-service/src/main/java/.../repository/ProductRepository.java`
  - `sources/backend/user-service/src/main/java/.../web/AdminStatsController.java`
  - `sources/backend/api-gateway/src/main/resources/application.yml` (route patterns)
  - `sources/backend/order-service/src/test/java/.../repository/OrderRepositoryJpaTest.java` (Testcontainers pattern)
  - `sources/frontend/e2e/admin-products.spec.ts` (Playwright admin pattern)
- **npm registry verification:** `npm view recharts@3.8.1 version` → `3.8.1` (latest, published 2026-03-25)

### Secondary (MEDIUM confidence)
- GitHub recharts releases page (3.7.0 Cell deprecation, 3.8.0 features, 3.8.1 bugfixes — fetched 2026-05-02)

### Tertiary (LOW confidence) — flagged for validation
- Recharts ResponsiveContainer collapse pitfall (well-known community pattern, no single official source — verify khi implement với 1 smoke test)
- Apache ECharts ~900KB bundle size estimate (rough — không ảnh hưởng quyết định, recharts đã lock)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — recharts version verified npm; BE deps đều reused.
- Architecture: HIGH — pattern đã có trong codebase (Phase 9 stats), chỉ extend.
- Pitfalls: HIGH cho 1, 4, 5; MEDIUM cho 2, 3 (cần smoke verify khi `npm install` xong).
- Security: HIGH — reuse existing JwtRoleGuard pattern.
- Validation: HIGH — Testcontainers + Playwright infra đã có.

**Research date:** 2026-05-02
**Valid until:** ~2026-06-02 (recharts dist-tag có thể bump; codebase patterns stable hơn)

## Project Constraints (from CLAUDE.md)

KHÔNG có file `./CLAUDE.md` ở root project. KHÔNG có `.claude/skills/` hoặc `.agents/skills/`. Các constraints áp dụng từ memory đã ghi:
- **Vietnamese language:** toàn bộ chat/docs/commits dùng tiếng Việt; identifiers + commit prefixes giữ EN. → Charts UI labels, tooltips, error messages PHẢI tiếng Việt (đã chốt D-13).
- **Visible-first priority:** ưu tiên UI/UX visible features, defer backend hardening/security/observability. → Phase 19 strict visible-first; KHÔNG add `@Cacheable` (D-discretion defer), KHÔNG add Spring Security setup.
- **Project nature:** dự án thử nghiệm GSD workflow, KHÔNG phải PTIT/HTPT student assignment. → Tránh language references này trong artifacts.
