# Phase 19: Hoàn Thiện Admin: Charts + Low-Stock — Pattern Map

**Mapped:** 2026-05-02
**Files analyzed:** 17 (8 BE new + 4 BE modified + 8 FE new/modified)
**Analogs found:** 17 / 17 (mọi file đều có analog mạnh trong codebase Phase 9 / 14 / 17)

---

## File Classification

### Backend — NEW

| New File | Role | Data Flow | Closest Analog | Match |
|----------|------|-----------|----------------|-------|
| `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/AdminChartsController.java` | controller | request-response (admin GET) | `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/AdminStatsController.java` | exact (same svc, same admin pattern) |
| `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderChartsService.java` | service | aggregation/transform | `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderStatsService.java` | exact (same svc, same `@Transactional(readOnly=true)`) |
| `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/ProductBatchClient.java` | service (cross-svc client) | request-response (HTTP egress) | `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderCrudService.java` lines 355–427 (`validateStockOrThrow` / `deductStockAfterPersist`) | role-match (RestTemplate cross-svc + try/catch fallback) |
| `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/AdminChartsController.java` | controller | request-response | `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/AdminStatsController.java` | exact |
| `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/service/UserChartsService.java` | service | aggregation | `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderStatsService.java` (cross-svc analog — user-svc chưa có UserStatsService riêng aggregation phức tạp) | role-match |
| `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/web/AdminChartsController.java` | controller | request-response (GET low-stock + POST batch) | `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/web/AdminStatsController.java` | exact |
| `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/service/ProductChartsService.java` (alias `LowStockService` per RESEARCH) | service | derived query | `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/repository/ProductRepository.java` (existing patterns) | role-match |
| `Range` enum (location TBD: nest trong `OrderChartsService` hoặc `service/Range.java` từng svc) | utility | parse param | RESEARCH §Backend Pattern 6 (no codebase analog — greenfield enum) | no analog |

### Backend — MODIFIED

| File | Modification | Closest Analog Inside Same File |
|------|--------------|---------------------------------|
| `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/repository/OrderRepository.java` | thêm 3 `@Query` methods (revenue by day, top products by qty, status counts) | existing `findByUserIdWithFilters` lines 39–51 (`cast(:from as timestamp) IS NULL` nullable param idiom) |
| `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/repository/UserRepository.java` | thêm 1 `@Query` method (signups by day) | mirror order-svc pattern (UserRepository hiện chỉ có `findByUsername`/`findByEmail`) |
| `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/repository/ProductRepository.java` | thêm `findLowStock(threshold, Pageable)` + `findAllByIdIn(List<String>)` cho batch | existing `findWithFilters` lines 27–37 (Pageable + `@SQLRestriction("deleted=false")` auto-applies) |
| `sources/backend/api-gateway/src/main/resources/application.yml` | thêm 4 routes (orders/charts, users/charts, products/charts, products/batch) | existing `order-service-admin` lines 119–124 (RewritePath `/api/orders/admin/(?<seg>.*) → /admin/orders/${seg}` — đã bao phủ `/admin/charts/...` và `/admin/batch` automatically) |

> **CRITICAL gateway observation:** Routes hiện hữu (`order-service-admin`, `product-service-admin`, `user-service-admin`) đã match catch-all `/api/{svc}/admin/**`. **KHÔNG cần thêm route mới** nếu controller path khớp `/admin/{resource}/charts/...`. Planner verify: existing routes đủ; chỉ cần thêm route nếu pattern controller khác thường (vd `/charts/admin/...` thay vì `/admin/charts/...`). Theo D-02 controller path bắt đầu `/admin/{resource}/charts` → existing routes covered. **Skip route additions** trừ khi cần override.

### Frontend — NEW

| New File | Role | Data Flow | Closest Analog | Match |
|----------|------|-----------|----------------|-------|
| `sources/frontend/src/components/admin/ChartCard.tsx` | component (wrapper) | render (3-state) | `sources/frontend/src/app/admin/page.tsx` lines 103–139 (`KpiCard<T>` generic component) | exact (same 3-state model) |
| `sources/frontend/src/components/admin/ChartCard.module.css` | style | — | `sources/frontend/src/app/admin/page.module.css` | exact |
| `sources/frontend/src/components/admin/RevenueChart.tsx` | component | render (Recharts SVG) | RESEARCH §FE Pattern 1 (no codebase analog — greenfield Recharts) | no analog (FE) |
| `sources/frontend/src/components/admin/TopProductsChart.tsx` | component | render | RESEARCH §FE Pattern 2 | no analog (FE) |
| `sources/frontend/src/components/admin/StatusDistributionChart.tsx` | component | render | RESEARCH §FE Pattern 3 | no analog (FE) |
| `sources/frontend/src/components/admin/UserSignupsChart.tsx` | component | render | RESEARCH §FE Pattern 1 (reuse) | no analog (FE) |
| `sources/frontend/src/components/admin/LowStockSection.tsx` | component | render (list + nav) | RESEARCH §FE Pattern 4 + admin/products page navigation | partial |
| `sources/frontend/src/services/charts.ts` | service (fetcher) | request-response | `sources/frontend/src/services/stats.ts` | exact |
| `sources/frontend/src/lib/chartFormat.ts` | utility (i18n + colors) | transform | RESEARCH §FE Pattern 3 (no codebase analog — greenfield) | no analog |

### Frontend — MODIFIED

| File | Modification | Analog Section |
|------|--------------|----------------|
| `sources/frontend/src/app/admin/page.tsx` | extend với time-window dropdown + charts grid (2x2) + low-stock section sau KPI cards | existing `Promise.allSettled` orchestration lines 57–60 (extend với 5 fetches mới) |
| `sources/frontend/src/app/admin/page.module.css` | thêm `.chartsGrid`, `.timeWindowRow`, responsive breakpoints | existing `.statsGrid` lines 5 + `@media` lines 34–35 |
| `sources/frontend/package.json` | thêm `recharts@3.8.1` dep | — (npm install) |

---

## Pattern Assignments

### `order-service/web/AdminChartsController.java` (controller, request-response)

**Analog:** `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/AdminStatsController.java`

**Imports + class structure** (lines 1–27 của analog) — copy nguyên xi, đổi `OrderStatsService` → `OrderChartsService`:
```java
package com.ptit.htpt.orderservice.web;

import com.ptit.htpt.orderservice.api.ApiResponse;
import com.ptit.htpt.orderservice.service.OrderChartsService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/orders/charts")  // D-02: path khớp gateway rewrite
public class AdminChartsController {
  private final OrderChartsService chartsService;
  private final JwtRoleGuard jwtRoleGuard;

  public AdminChartsController(OrderChartsService chartsService, JwtRoleGuard jwtRoleGuard) {
    this.chartsService = chartsService;
    this.jwtRoleGuard = jwtRoleGuard;
  }
```

**Auth + endpoint pattern** (lines 33–42 của analog) — replicate cho mỗi endpoint:
```java
@GetMapping("/revenue")
public ApiResponse<List<RevenuePoint>> revenue(
    @RequestParam(value = "range", required = false, defaultValue = "30d") String range,
    @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
) {
  jwtRoleGuard.requireAdmin(authHeader);  // D-02: tái dùng manual JWT check
  return ApiResponse.of(200, "Revenue chart", chartsService.revenueByDay(Range.parse(range)));
}
```

**Cross-svc auth forwarding (D-03 pitfall #4):** Top-products endpoint phải forward `authHeader` xuống service:
```java
@GetMapping("/top-products")
public ApiResponse<List<TopProductPoint>> topProducts(
    @RequestParam(value = "range", required = false, defaultValue = "30d") String range,
    @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
) {
  jwtRoleGuard.requireAdmin(authHeader);
  return ApiResponse.of(200, "Top products", chartsService.topProducts(Range.parse(range), authHeader));
}
```

---

### `order-service/service/OrderChartsService.java` (service, aggregation)

**Analog:** `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderStatsService.java`

**Class structure + transactional pattern** (lines 1–34 của analog):
```java
@Service
public class OrderChartsService {
  private final OrderRepository orderRepo;
  private final ProductBatchClient productClient;

  public OrderChartsService(OrderRepository orderRepo, ProductBatchClient productClient) {
    this.orderRepo = orderRepo;
    this.productClient = productClient;
  }

  @Transactional(readOnly = true)  // analog OrderStatsService line 20, 30
  public List<RevenuePoint> revenueByDay(Range range) { ... }
}
```

**Aggregation + Java gap-fill pattern:** Theo RESEARCH §Backend Pattern 1 (lines 252–285). Critical: dùng `FUNCTION('DATE', col)` (Pitfall #3), KHÔNG `DATE(col)` raw JPQL.

---

### `order-service/service/ProductBatchClient.java` (service, cross-svc HTTP)

**Analog:** `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderCrudService.java` lines 355–427

**RestTemplate egress + try/catch fallback pattern** (analog `validateStockOrThrow` lines 361–391):
```java
@Component
public class ProductBatchClient {
  private static final Logger log = LoggerFactory.getLogger(ProductBatchClient.class);
  private final RestTemplate restTemplate;

  public ProductBatchClient(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  public Map<String, ProductSummary> fetchBatch(List<String> ids, String authHeader) {
    if (ids.isEmpty()) return Map.of();
    try {
      String url = "http://api-gateway:8080/api/products/admin/batch";  // analog OrderCrudService line 365
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.set(HttpHeaders.AUTHORIZATION, authHeader);  // D-03: forward Bearer (Pitfall #4)
      HttpEntity<Map<String, Object>> entity = new HttpEntity<>(Map.of("ids", ids), headers);
      ResponseEntity<ApiResponse<List<ProductSummary>>> resp = restTemplate.exchange(
          url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
      return resp.getBody().data().stream()
          .collect(Collectors.toMap(ProductSummary::id, p -> p));
    } catch (Exception ex) {
      // analog OrderCrudService lines 383–386: log.warn + best-effort fallback
      log.warn("[D-03] product-svc batch enrichment failed: {}", ex.getMessage());
      return Map.of();
    }
  }

  public record ProductSummary(String id, String name, String brand, String thumbnailUrl) {}
}
```

**Bean wiring:** `RestTemplate` đã có sẵn ở `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/AppConfig.java` lines 8–13 (`@Bean public RestTemplate restTemplate()`). Inject thẳng vào constructor — KHÔNG add config mới.

---

### `user-service/web/AdminChartsController.java` + `service/UserChartsService.java`

**Analog:** `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/AdminStatsController.java`

Identical pattern với order-svc charts controller, đổi:
- Package `com.ptit.htpt.userservice.*`
- `@RequestMapping("/admin/users/charts")`
- 1 endpoint `/signups`
- Service phương thức `signupsByDay(Range range)` mirror `revenueByDay` nhưng `COUNT(u)` thay `SUM(o.total)`.

**JwtRoleGuard:** user-svc đã có `JwtRoleGuard` riêng (analog AdminStatsController line 22–24 inject) — reuse, KHÔNG copy file.

---

### `product-service/web/AdminChartsController.java` (controller, GET low-stock + POST batch)

**Analog:** `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/web/AdminStatsController.java`

**GET endpoint pattern** — copy structure analog AdminStatsController lines 17–37:
```java
@RestController
@RequestMapping("/admin/products/charts")
public class AdminChartsController {
  private final LowStockService lowStockService;
  private final JwtRoleGuard jwtRoleGuard;

  @GetMapping("/low-stock")
  public ApiResponse<List<LowStockItem>> lowStock(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
  ) {
    jwtRoleGuard.requireAdmin(authHeader);
    return ApiResponse.of(200, "Low stock products", lowStockService.list());
  }
}
```

**POST batch endpoint** — separate controller hoặc cùng file:
```java
@RestController
@RequestMapping("/admin/products")
public class AdminProductBatchController {
  @PostMapping("/batch")
  public ApiResponse<List<ProductSummary>> batch(
      @RequestBody Map<String, List<String>> body,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader
  ) {
    jwtRoleGuard.requireAdmin(authHeader);
    return ApiResponse.of(200, "Product batch", productService.findByIds(body.get("ids")));
  }
}
```

**Path note:** `/admin/products/batch` (KHÔNG `/admin/products/charts/batch`) để gateway rewrite `/api/products/admin/batch` → `/admin/products/batch` đi qua existing `product-service-admin` route.

---

### `OrderRepository.java` (modified — thêm 3 @Query methods)

**Analog inside same file:** `findByUserIdWithFilters` lines 39–51 (nullable param idiom).

**Pattern to copy** (nullable instant param):
```java
// existing line 41: AND (cast(:from as timestamp) IS NULL OR o.createdAt >= :from)
```

**New methods** theo RESEARCH §Backend Pattern 1, 2, 3:
```java
@Query("""
    SELECT FUNCTION('DATE', o.createdAt) AS day, SUM(o.total) AS total
    FROM OrderEntity o
    WHERE o.status = 'DELIVERED'
      AND (cast(:from as timestamp) IS NULL OR o.createdAt >= :from)
    GROUP BY FUNCTION('DATE', o.createdAt)
    ORDER BY day ASC
    """)
List<Object[]> aggregateRevenueByDay(@Param("from") Instant from);

@Query("""
    SELECT i.productId, SUM(i.quantity) AS qtySold
    FROM OrderEntity o JOIN o.items i
    WHERE o.status = 'DELIVERED'
      AND (cast(:from as timestamp) IS NULL OR o.createdAt >= :from)
    GROUP BY i.productId
    ORDER BY qtySold DESC
    """)
List<Object[]> aggregateTopProducts(@Param("from") Instant from, Pageable limit);

@Query("SELECT o.status, COUNT(o) FROM OrderEntity o GROUP BY o.status")
List<Object[]> aggregateStatusDistribution();
```

---

### `ProductRepository.java` (modified — thêm low-stock + batch find)

**Analog inside same file:** `findWithFilters` lines 27–37 (Pageable usage). `@SQLRestriction("deleted=false")` trên ProductEntity tự loại deleted records (analog comment line 22).

**New methods:**
```java
@Query("SELECT p FROM ProductEntity p WHERE p.stock < :threshold ORDER BY p.stock ASC")
List<ProductEntity> findLowStock(@Param("threshold") int threshold, Pageable cap);

// JpaRepository default `findAllById(Iterable)` đã có sẵn — KHÔNG cần custom method cho batch.
// Hoặc explicit cho clarity: List<ProductEntity> findAllByIdIn(List<String> ids);
```

---

### `application.yml` (api-gateway — modified)

**Analog inside same file:** `order-service-admin` lines 119–124, `user-service-admin` lines 43–48, `product-service-admin` lines 91–96.

**Pattern to copy** (existing — đã đủ cover charts paths):
```yaml
- id: order-service-admin
  uri: http://order-service:8080
  predicates:
    - Path=/api/orders/admin/**
  filters:
    - RewritePath=/api/orders/admin/(?<seg>.*), /admin/orders/${seg}
```

**Action:** Verify existing 3 admin routes (order/user/product) đã match catch-all `/api/{svc}/admin/**`. **Likely NO modification needed** — chỉ thêm route nếu controller path không khớp. Planner kiểm tra: `/api/orders/admin/charts/revenue` → rewrite → `/admin/orders/charts/revenue` → match `@RequestMapping("/admin/orders/charts")` + `@GetMapping("/revenue")` ✓.

---

### `frontend/src/services/charts.ts` (NEW — fetcher)

**Analog:** `sources/frontend/src/services/stats.ts` (toàn bộ file)

**Imports + interface + fetcher pattern** (lines 1–32 của analog):
```ts
import { httpGet } from './http';

export interface RevenuePoint { date: string; value: number }
export interface TopProductPoint { productId: string; name: string; brand: string|null; thumbnailUrl: string|null; qtySold: number }
export interface StatusPoint { status: string; count: number }
export interface SignupPoint { date: string; count: number }
export interface LowStockItem { id: string; name: string; brand: string|null; thumbnailUrl: string|null; stock: number }

export type Range = '7d' | '30d' | '90d' | 'all';

export const fetchRevenueChart  = (range: Range) => httpGet<RevenuePoint[]>(`/api/orders/admin/charts/revenue?range=${range}`);
export const fetchTopProducts   = (range: Range) => httpGet<TopProductPoint[]>(`/api/orders/admin/charts/top-products?range=${range}`);
export const fetchStatusDistrib = ()             => httpGet<StatusPoint[]>(`/api/orders/admin/charts/status-distribution`);
export const fetchUserSignups   = (range: Range) => httpGet<SignupPoint[]>(`/api/users/admin/charts/signups?range=${range}`);
export const fetchLowStock      = ()             => httpGet<LowStockItem[]>(`/api/products/admin/charts/low-stock`);
```

`httpGet` từ `services/http.ts` line 144 tự attach Bearer token và unwrap ApiResponse envelope (line 108).

---

### `frontend/src/components/admin/ChartCard.tsx` (NEW — wrapper)

**Analog:** `sources/frontend/src/app/admin/page.tsx` lines 103–139 (`KpiCard<T>` generic).

**Generic component + 3-state pattern** (analog lines 112–139):
```tsx
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

Critical: `chartBody { min-height: 250px }` (Pitfall #1 — ResponsiveContainer collapse).

---

### `frontend/src/app/admin/page.tsx` (MODIFIED — extend dashboard)

**Analog inside same file:** lines 22–60 (`AdminDashboard` component + `useCallback` loaders + `Promise.allSettled` orchestration).

**Extension pattern** — thêm vào existing component:

**State + loader pattern** (analog lines 23–56):
```tsx
const [range, setRange] = useState<Range>('30d');
const [revenueCard, setRevenueCard] = useState<CardState<RevenuePoint[]>>({ status: 'loading' });
// ... 4 more cards

const loadRevenue = useCallback(async () => {
  setRevenueCard({ status: 'loading' });
  try {
    const data = await fetchRevenueChart(range);
    setRevenueCard({ status: 'success', data });
  } catch (e) {
    setRevenueCard({ status: 'error', error: (e as Error).message ?? 'Không tải được' });
  }
}, [range]);  // CRITICAL Pitfall #5: deps include `range`, không include loader functions khác
```

**Orchestration** (analog lines 57–60):
```tsx
useEffect(() => {
  Promise.allSettled([loadRevenue(), loadTopProducts(), loadStatus(), loadSignups(), loadLowStock()]);
}, [loadRevenue, loadTopProducts, loadStatus, loadSignups, loadLowStock]);
```

**Render order** (D-07): KPI cards row (existing) → time-window dropdown → 2x2 charts grid → low-stock full-width.

---

### `frontend/src/app/admin/page.module.css` (MODIFIED)

**Analog inside same file:** `.statsGrid` line 5, responsive `@media` lines 34–35.

**Pattern to copy** cho `.chartsGrid`:
```css
.chartsGrid { display: grid; grid-template-columns: repeat(2, 1fr); gap: var(--space-4); }
@media (max-width: 768px) { .chartsGrid { grid-template-columns: 1fr; } }
```

Skeleton + retry styles (analog lines 13–32) — REUSE thay vì duplicate; planner cân nhắc move sang `ChartCard.module.css` riêng để decouple.

---

## Shared Patterns

### Pattern S-1: `JwtRoleGuard.requireAdmin(authHeader)` manual JWT check

**Source:** `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/JwtRoleGuard.java` lines 34–63

**Apply to:** Tất cả 3 chart controllers mới (order/user/product) + product batch controller.

**Per-svc:** Mỗi service có `JwtRoleGuard` riêng (verified user-svc + product-svc đã có) — KHÔNG copy file mới, inject vào constructor:
```java
public AdminChartsController(OrderChartsService chartsService, JwtRoleGuard jwtRoleGuard) {
  this.jwtRoleGuard = jwtRoleGuard;
}
// In endpoint:
jwtRoleGuard.requireAdmin(authHeader);  // 401/403 ResponseStatusException tự handle
```

**Test verification:** No bearer → 401, bearer ROLE!=ADMIN → 403, bearer ADMIN → 200.

---

### Pattern S-2: `ApiResponse.of(status, message, data)` envelope

**Source:** existing `com.ptit.htpt.{svc}.api.ApiResponse` — analog `AdminStatsController.java` line 38, 38, 34

**Apply to:** Tất cả endpoint responses (chart + batch).

```java
return ApiResponse.of(200, "Revenue chart", chartsService.revenueByDay(range));
```

FE `httpGet` (services/http.ts line 108) tự unwrap `parsed.data` → trả về typed payload trực tiếp.

---

### Pattern S-3: `@Transactional(readOnly = true)` cho mọi query-only service

**Source:** `OrderStatsService.java` lines 20, 30

**Apply to:** `OrderChartsService`, `UserChartsService`, `LowStockService` — mọi method aggregation/derived query.

---

### Pattern S-4: `cast(:param as type) IS NULL OR ...` nullable param idiom

**Source:** `OrderRepository.findByUserIdWithFilters` lines 39–44

**Apply to:** Mọi `@Query` JPQL có optional time range (revenue, top-products, signups). Critical cho `range="all"` — pass `Instant from = null`.

```jpql
WHERE (cast(:from as timestamp) IS NULL OR o.createdAt >= :from)
```

---

### Pattern S-5: `useCallback` per loader + `Promise.allSettled` orchestration

**Source:** `app/admin/page.tsx` lines 27–60

**Apply to:** All 5 chart fetches trong dashboard component.

**Critical:** Loader deps phải bao gồm `range` (cho 3 charts có window) hoặc empty `[]` (cho status pie + low-stock — load 1 lần). NEVER include other loader functions trong deps (Pitfall #5).

---

### Pattern S-6: 3-state `CardState<T>` discriminated union

**Source:** `app/admin/page.tsx` line 14

```tsx
type CardState<T> = { status: 'loading' | 'success' | 'error'; data?: T; error?: string };
```

**Apply to:** Tất cả 5 chart cards + low-stock section state.

---

### Pattern S-7: Vietnamese UI labels everywhere

**Source:** memory `feedback_language.md` + analog `KpiCard` `aria-label="Đang tải"` + `aria-label="Tải lại ${label}"` (page.tsx lines 118, 130).

**Apply to:** Mọi user-facing text — chart titles ("Doanh thu", "Sản phẩm bán chạy", "Phân phối trạng thái", "Khách hàng đăng ký", "Sản phẩm sắp hết hàng"), dropdown label ("Khoảng thời gian:"), empty states (D-15), tooltip formatter, status labels (D-13 helper map).

---

### Pattern S-8: CSS modules + design tokens

**Source:** existing `page.module.css` — `var(--surface-container-lowest)`, `var(--radius-xl)`, `var(--space-N)`, `var(--text-title-md)`, `var(--weight-bold)`.

**Apply to:** `ChartCard.module.css`, mọi inline style trong chart components — KHÔNG hardcode color/spacing trừ semantic status colors (D-12 #f59e0b, #3b82f6, ...).

---

## No Analog Found

| File | Role | Reason | Source |
|------|------|--------|--------|
| `Range` enum (BE) | utility | Greenfield — chưa có time-window enum nào trong codebase | RESEARCH §Backend Pattern 6 lines 419–439 |
| `RevenueChart.tsx` / `TopProductsChart.tsx` / `StatusDistributionChart.tsx` / `UserSignupsChart.tsx` | component (Recharts) | Greenfield — chưa có chart component nào trước đây | RESEARCH §FE Pattern 1, 2, 3 |
| `LowStockSection.tsx` | component (list+nav) | Pattern list+nav có analog nhỏ ở `admin/products/page.tsx`, nhưng layout row+badge mới | RESEARCH §FE Pattern 4 |
| `chartFormat.ts` | utility (i18n + colors) | Greenfield — `Intl.NumberFormat`/`DateTimeFormat` chưa được centralize | RESEARCH §FE Pattern 3 lines 532–554 |
| `ProductBatchClient.java` (cấu trúc batch + auth forwarding) | service | Cross-svc batch endpoint chưa tồn tại; closest: `OrderCrudService` GET single product loop | RESEARCH §Backend Pattern 2 lines 304–334 |

→ Cho các file "no analog" trên, planner reference TRỰC TIẾP RESEARCH.md code patterns.

---

## Metadata

**Analog search scope:**
- `sources/backend/{order,user,product}-service/src/main/java/...` (controllers, services, repositories, AppConfig)
- `sources/backend/api-gateway/src/main/resources/application.yml`
- `sources/frontend/src/{app/admin,services,components}/...`

**Files scanned:** 14 (3 AdminStatsController, 1 OrderStatsService, 1 JwtRoleGuard, 1 OrderRepository, 1 UserRepository, 1 ProductRepository, 1 OrderCrudService, 1 AppConfig, 1 application.yml, 1 admin/page.tsx, 1 page.module.css, 1 stats.ts, 1 http.ts).

**Pattern extraction date:** 2026-05-02

---

## PATTERN MAPPING COMPLETE

**Phase:** 19 - Hoàn Thiện Admin: Charts + Low-Stock
**Files classified:** 17 (8 BE NEW + 4 BE MODIFIED + 8 FE NEW/MODIFIED + 1 package.json)
**Analogs found:** 17 / 17

### Coverage
- Files với exact analog (cùng role+flow trong same svc): 9 (3 admin charts controllers, 3 charts services, charts.ts, ChartCard.tsx, page.tsx extension)
- Files với role-match analog (same role, gần data flow): 3 (ProductBatchClient → OrderCrudService cross-svc; LowStockSection → admin/products nav; UserChartsService → OrderStatsService)
- Files với no analog (greenfield Recharts/utility/enum): 5 (4 chart components + chartFormat.ts + Range enum)

### Key Patterns Identified
- **BE controllers** copy nguyên xi `AdminStatsController` shape: `@RequestMapping("/admin/{resource}/{sub}")` + `JwtRoleGuard.requireAdmin(authHeader)` manual + `ApiResponse.of(...)` envelope.
- **Repository @Query** dùng `cast(:param as timestamp) IS NULL OR ...` nullable idiom (existing `findByUserIdWithFilters`) + `FUNCTION('DATE', col)` cho daily aggregation (Pitfall #3 avoidance).
- **Cross-svc HTTP** dùng RestTemplate sẵn ở AppConfig + try/catch fallback (analog `OrderCrudService.validateStockOrThrow` lines 361–391) + auth forwarding `headers.set(AUTHORIZATION, authHeader)` (Pitfall #4).
- **FE dashboard extension** giữ `useCallback`+`Promise.allSettled` từ Phase 9; ChartCard generic mirror KpiCard 3-state model.
- **Gateway routes** đã cover qua existing `{svc}-service-admin` catch-all `/api/{svc}/admin/**` — likely KHÔNG cần modification.
- **Vietnamese labels + design tokens** áp dụng mọi nơi (memory `feedback_language.md` + existing CSS vars).

### File Created
`.planning/phases/19-ho-n-thi-n-admin-charts-low-stock/19-PATTERNS.md`

### Ready for Planning
Pattern mapping hoàn tất. Planner có thể reference analog files + line ranges trực tiếp trong PLAN.md actions, đặc biệt:
- Phase 9 AdminStatsController/OrderStatsService cho BE charts skeleton.
- Phase 17 / OrderCrudService cross-svc pattern cho ProductBatchClient.
- Phase 9 KpiCard cho ChartCard wrapper.
- RESEARCH.md §FE Pattern 1–6 cho 4 Recharts components (no codebase analog).
