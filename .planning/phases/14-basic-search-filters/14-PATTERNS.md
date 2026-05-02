# Phase 14: Basic Search Filters — Pattern Map

**Mapped:** 2026-05-02
**Files analyzed:** 8 (4 backend + 4 frontend)
**Analogs found:** 8 / 8

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `product-service/web/ProductController.java` (M) | controller | request-response | self (sibling endpoint `listProducts` line 31-40) | exact |
| `product-service/service/ProductCrudService.java` (M) | service | CRUD/query | self (lines 33-53 — refactor target) | exact |
| `product-service/repository/ProductRepository.java` (M) | repository | JPQL optional params | `order-service/repository/OrderRepository.java` lines 16-33 | exact |
| `product-service/web/ProductController#listBrands` (NEW endpoint) | controller | request-response | sibling `listCategories` line 71-78 | exact |
| `frontend/components/ui/FilterSidebar/FilterSidebar.tsx` (NEW) | component | event-driven (controlled) | `components/ui/OrderFilterBar/OrderFilterBar.tsx` lines 22-129 | role-match (sidebar vs bar) |
| `frontend/components/ui/FilterSidebar/FilterSidebar.module.css` (NEW) | styles | n/a | `components/ui/OrderFilterBar/OrderFilterBar.module.css` (toàn bộ) + `app/products/page.module.css` lines 49-300 | role-match |
| `frontend/app/products/page.tsx` (M) | page | request-response | self (lines 50-96 wire FilterSidebar in place of `priceRange` state) | exact |
| `frontend/services/products.ts` (M) | service-api | request-response | self (`listProducts` lines 28-45) | exact |
| `frontend/hooks/useDebouncedValue.ts` (NEW, optional) | hook | transform | inline pattern in `app/search/page.tsx` lines 28-38 | role-match |

> KHÔNG có `hooks/` hoặc `lib/` directory hiện hữu — Claude tự tạo `hooks/useDebouncedValue.ts` (preferred) HOẶC inline `useEffect+setTimeout` trong `FilterSidebar.tsx` theo precedent search page.

---

## Pattern Assignments

### `ProductRepository.java` (repository, JPQL optional params) — MODIFIED

**Analog:** `order-service/.../repository/OrderRepository.java` lines 16-33 (Phase 11 ACCT-02)

**Imports pattern** (OrderRepository.java:1-8):
```java
package com.ptit.htpt.productservice.repository;

import com.ptit.htpt.productservice.domain.ProductEntity;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
```

**JPQL optional-params pattern** (OrderRepository.java:21-33) — COPY EXACTLY pattern này cho `findWithFilters`:
```java
@Query("SELECT DISTINCT o FROM OrderEntity o LEFT JOIN FETCH o.items WHERE o.userId = :userId " +
       "AND (cast(:status as string) IS NULL OR o.status = :status) " +
       "AND (cast(:from as timestamp) IS NULL OR o.createdAt >= :from) " +
       "AND (cast(:to as timestamp) IS NULL OR o.createdAt <= :to) " +
       "AND (cast(:q as string) IS NULL OR LOWER(o.id) LIKE LOWER(CONCAT('%', cast(:q as string), '%'))) " +
       "ORDER BY o.createdAt DESC")
List<OrderEntity> findByUserIdWithFilters(
    @Param("userId") String userId,
    @Param("status") String status,
    @Param("from") Instant from,
    @Param("to") Instant to,
    @Param("q") String q
);
```

**Adaptation cho Phase 14** (D-06, D-07):
- Đổi entity: `OrderEntity` → `ProductEntity` (alias `p`).
- WHERE clause: `(cast(:keyword as string) IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', cast(:keyword as string), '%'))) AND (:brands IS NULL OR p.brand IN :brands) AND (cast(:priceMin as big_decimal) IS NULL OR p.price >= :priceMin) AND (cast(:priceMax as big_decimal) IS NULL OR p.price <= :priceMax)`.
- **CRITICAL:** dùng `Page<ProductEntity>` + `Pageable` (xem ReviewRepository.java:12 — `Page<ReviewEntity> findByProductIdOrderByCreatedAtDesc(String productId, Pageable pageable)`) thay vì `List<>` + tự paginate, vì D-06 yêu cầu Pageable thật. `@SQLRestriction("deleted = false")` đã có ở entity nên KHÔNG cần `AND p.deleted = false`.
- Brand IN clause: dùng `List<String> brands` param; xử lý null/empty ở service layer (truyền `null` khi list rỗng).
- Sort: pass qua `Pageable` chứ không hardcode `ORDER BY` trong JPQL (khác OrderRepository — vì sort động).

**Brand DISTINCT projection method** (NEW — D-03):
```java
@Query("SELECT DISTINCT p.brand FROM ProductEntity p " +
       "WHERE p.brand IS NOT NULL ORDER BY p.brand ASC")
List<String> findDistinctBrands();
```
(Soft-delete đã filter via `@SQLRestriction` trên entity.)

**Existing method giữ lại:** `Optional<ProductEntity> findBySlug(String slug);` (line 8 hiện tại).

---

### `ProductCrudService.java` (service, CRUD) — MODIFIED

**Analog:** Self lines 33-53 (in-memory filter cần refactor) + ReviewService pattern delegate sang Repository.

**Current pattern to REPLACE** (ProductCrudService.java:33-53):
```java
public Map<String, Object> listProducts(int page, int size, String sort,
                                        boolean includeDeleted, String keyword) {
  List<ProductEntity> all = productRepo.findAll().stream()
      .filter(product -> includeDeleted || !product.deleted())
      .filter(product -> keyword == null || keyword.isBlank() ||
          product.name().toLowerCase().contains(keyword.toLowerCase()))
      .sorted(productComparator(sort))
      .toList();
  Map<String, Object> page0 = paginate(all, page, size);
  // ...
}
```

**Refactor pattern (D-06)**:
1. Build `Pageable` từ `page`, `size`, `sort` (parse `"price,asc"` → `Sort.by(...).ascending()`). Có thể tạo helper `parseSort(String)` static.
2. Normalize params: `keyword = (keyword == null || keyword.isBlank()) ? null : keyword;`. `brands = (brands == null || brands.isEmpty()) ? null : brands;`.
3. Call `Page<ProductEntity> page = productRepo.findWithFilters(keyword, brands, priceMin, priceMax, pageable);`.
4. Build response Map giống `paginate()` hiện có (lines 211-229) nhưng đọc từ `Page<T>` (`page.getContent()`, `page.getTotalElements()`, `page.getTotalPages()`, `page.getNumber()`, `page.getSize()`, `page.isFirst()`, `page.isLast()`).
5. Map content qua `this::toResponse`.

**Method signature mới (extend D-08):**
```java
public Map<String, Object> listProducts(int page, int size, String sort,
                                        boolean includeDeleted, String keyword,
                                        List<String> brands, BigDecimal priceMin,
                                        BigDecimal priceMax) { ... }
```
Giữ overload cũ (line 33) delegate sang signature mới với `brands=null, priceMin=null, priceMax=null` để không break callers khác.

**New method `listBrands()`:**
```java
public List<String> listBrands() {
  return productRepo.findDistinctBrands();
}
```

**Error handling** (kế thừa pattern hiện có): không thêm try/catch — `GlobalExceptionHandler` đã wrap `ResponseStatusException` (xem `getProduct` line 55-62).

---

### `ProductController.java` (controller, request-response) — MODIFIED

**Analog:** Self — `listProducts` line 31-40 (extend params) + `listCategories` line 71-78 (template cho NEW `/brands` endpoint).

**Current pattern to EXTEND** (ProductController.java:31-40):
```java
@GetMapping
public ApiResponse<Map<String, Object>> listProducts(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size,
    @RequestParam(defaultValue = "updatedAt,desc") String sort,
    @RequestParam(required = false) String keyword
) {
  return ApiResponse.of(200, "Products listed",
      productCrudService.listProducts(page, size, sort, false, keyword));
}
```

**Add params (D-08):**
```java
@RequestParam(required = false) List<String> brands,           // ?brands=Dell&brands=HP
@RequestParam(required = false) BigDecimal priceMin,
@RequestParam(required = false) BigDecimal priceMax
```
Pass thêm imports: `java.math.BigDecimal`, `java.util.List`.

**NEW endpoint `/brands`** — copy template từ `listCategories` (line 71-78):
```java
@GetMapping("/brands")
public ApiResponse<List<String>> listBrands() {
  return ApiResponse.of(200, "Brands listed", productCrudService.listBrands());
}
```

**Response wrapper:** ALL endpoints dùng `ApiResponse.of(status, message, data)` — KHÔNG return raw data (precedent line 38, 44, 50, ...). FE `httpGet` đã unwrap envelope (xem `services/products.ts` header comment).

---

### `services/products.ts` (service-api, request-response) — MODIFIED

**Analog:** Self — `ListProductsParams` interface (line 28-34) + serialization block (line 37-43).

**Imports pattern** (products.ts:22-24): keep as-is, không cần thêm import.

**Extend interface** (lines 28-34):
```typescript
export interface ListProductsParams {
  page?: number;
  size?: number;
  sort?: string;
  categoryId?: string;
  keyword?: string;
  brands?: string[];        // NEW — D-08 (multi-value)
  priceMin?: number;        // NEW
  priceMax?: number;        // NEW
}
```

**Serialization pattern** (lines 37-43) — append:
```typescript
const qs = new URLSearchParams();
if (params?.page !== undefined) qs.set('page', String(params.page));
// ... existing lines ...
if (params?.brands && params.brands.length > 0) {
  params.brands.forEach(b => qs.append('brands', b));   // repeatable param
}
if (params?.priceMin !== undefined) qs.set('priceMin', String(params.priceMin));
if (params?.priceMax !== undefined) qs.set('priceMax', String(params.priceMax));
```
Note: `qs.append` (KHÔNG `set`) cho `brands` để gửi `?brands=Dell&brands=HP` — match Spring `@RequestParam List<String>` convention.

**NEW function `listBrands()`** — copy template từ `listCategories` (line 62-64):
```typescript
export function listBrands(): Promise<string[]> {
  return httpGet<string[]>(`/api/products/brands`);
}
```

---

### `FilterSidebar/FilterSidebar.tsx` (component, controlled) — NEW

**Analog:** `components/ui/OrderFilterBar/OrderFilterBar.tsx` lines 22-129 (controlled inputs + debounce + clear button + Vietnamese copy).

**Imports + Component shape** (OrderFilterBar.tsx:1-12, 22-32):
```typescript
'use client';

import { useEffect, useState } from 'react';
import Button from '@/components/ui/Button/Button';
import styles from './FilterSidebar.module.css';

interface FilterValue {
  brands: string[];
  priceMin?: number;
  priceMax?: number;
}

interface FilterSidebarProps {
  brands: string[];               // available brands list (parent fetch via listBrands())
  value: FilterValue;             // controlled
  onChange: (next: FilterValue) => void;
  loading?: boolean;              // optional — show skeleton trong brand list khi đang fetch /brands
}
```

**Auto-apply / debounce pattern** (OrderFilterBar.tsx:38-44 — 400ms):
```typescript
useEffect(() => {
  const timer = setTimeout(() => {
    onChange({ status, from, to, q });
  }, 400);
  return () => clearTimeout(timer);
  // eslint-disable-next-line react-hooks/exhaustive-deps
}, [status, from, to, q]);
```
**Adaptation Phase 14 (D-02):**
- Brand checkbox: apply NGAY (no debounce) → call `onChange` trong handler trực tiếp.
- Price input min/max: debounce 400ms — wrap riêng `useEffect` cho `[priceMin, priceMax]` only. Brand không vào dep array của price effect.
- Preset chip click: apply NGAY (skip debounce) — gọi `onChange` trực tiếp với new min+max.

**Clear button pattern** (OrderFilterBar.tsx:46-51, 114-126):
```typescript
function handleClear() {
  setBrands([]);
  setPriceMin(undefined);
  setPriceMax(undefined);
  setPriceError(null);
}
// JSX:
<Button
  variant="tertiary"
  size="sm"
  onClick={handleClear}
  aria-label="Xóa bộ lọc thương hiệu và giá"
  className={styles.clearBtn}
>
  Xóa bộ lọc
</Button>
```
(D-10 — KHÔNG reset categories vì categories nằm ở parent `/products/page.tsx`, không trong sidebar component.)

**Validation pattern (D-05)** — onBlur min > max:
```typescript
function handlePriceBlur() {
  if (priceMin !== undefined && priceMax !== undefined && priceMin > priceMax) {
    setPriceError('Giá tối thiểu phải nhỏ hơn giá tối đa');
    return;  // KHÔNG fire onChange
  }
  setPriceError(null);
  // VND format display update
}
```
Render error: `{priceError && <p role="alert" className={styles.priceError}>{priceError}</p>}` (a11y D-05 + UI-SPEC accessibility).

**Brand checkbox a11y** (UI-SPEC Component Inventory):
```tsx
<div role="group" aria-label="Danh sách thương hiệu" className={styles.brandList}>
  {brands.map(b => (
    <label key={b} htmlFor={`brand-${b}`} className={styles.brandRow}>
      <input
        id={`brand-${b}`}
        type="checkbox"
        checked={selectedBrands.includes(b)}
        onChange={(e) => {
          const next = e.target.checked
            ? [...selectedBrands, b]
            : selectedBrands.filter(x => x !== b);
          setSelectedBrands(next);
          onChange({ ...value, brands: next });   // apply NGAY
        }}
      />
      {b}
    </label>
  ))}
</div>
```

---

### `FilterSidebar/FilterSidebar.module.css` (styles) — NEW

**Analog:** `OrderFilterBar.module.css` (full file, copy token approach) + `app/products/page.module.css` selectors `.sidebar` (line 49), `.filterTitle` (95), `.filterChip` (107), `.priceInputs` (131), `.presetBtn` (172), `.clearBtn` (72), `.emptyState` (232).

**Token-only convention** (OrderFilterBar.module.css — toàn bộ file):
- KHÔNG hardcode hex/px ngoài `1.5px` border, `0.15s` transition, `40px` input height (precedent line 25, 35, 44).
- Spacing dùng `var(--space-{1..6})`.
- Colors dùng `var(--surface-container-low)`, `var(--primary)`, `var(--on-surface)`, `var(--outline-variant)`, `var(--error)`.
- Typography dùng `var(--text-body-md)`, `var(--text-label-md)`, `var(--weight-semibold)`, etc.
- Radius dùng `var(--radius-lg)`, `var(--radius-xl)`.

**Input focus pattern** (OrderFilterBar.module.css:43-58):
```css
.priceInput {
  height: 40px;
  padding: 0 var(--space-2);
  border: 1.5px solid var(--outline-variant);
  border-radius: var(--radius-lg);
  background: var(--surface-container-lowest);
  color: var(--on-surface);
  font-size: var(--text-body-md);
  font-family: inherit;
  outline: none;
  transition: border-color 0.15s ease;
}
.priceInput:focus { border-color: var(--primary); }
.priceInput.invalid { border-color: var(--error); }   /* D-05 */
```

**Brand list scroll (UI-SPEC Spacing Exceptions):**
```css
.brandList {
  max-height: 240px;       /* ~6 rows */
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}
```

**Mobile** (OrderFilterBar.module.css:72-87 pattern): `@media (max-width: 768px)` — sidebar đã có drawer trong `page.module.css:288-300` (`.sidebarOpen`), FilterSidebar chỉ cần style nội dung.

---

### `app/products/page.tsx` (page) — MODIFIED

**Analog:** Self lines 19-96 (state shape + load callback + clearFilters).

**Replace `priceRange` state (D-06, D-09)** — current line 23:
```typescript
const [priceRange, setPriceRange] = useState<[number, number]>([0, 10000000]);
```
**With:**
```typescript
const [filterBrands, setFilterBrands] = useState<string[]>([]);
const [filterPriceMin, setFilterPriceMin] = useState<number | undefined>(undefined);
const [filterPriceMax, setFilterPriceMax] = useState<number | undefined>(undefined);
const [availableBrands, setAvailableBrands] = useState<string[]>([]);
```

**Add brand fetch effect** (parallel với `listCategories` effect line 31-48):
```typescript
useEffect(() => {
  let alive = true;
  listBrands()
    .then(list => { if (alive) setAvailableBrands(list ?? []); })
    .catch(() => {/* non-fatal — brand facet hides if empty */});
  return () => { alive = false; };
}, []);
```

**Extend `load` callback** (line 50-80) — add to listProducts call (line 66-71):
```typescript
const resp = await listProducts({
  page: 0,                                             // D-11: reset page on filter change
  size: 24,
  sort: sortParam,
  categoryId: selectedCategory ?? undefined,
  keyword: searchQuery.trim() || undefined,
  brands: filterBrands.length > 0 ? filterBrands : undefined,
  priceMin: filterPriceMin,
  priceMax: filterPriceMax,
});
```
Add deps: `[sortBy, selectedCategory, searchQuery, filterBrands, filterPriceMin, filterPriceMax]`.

**REMOVE client-side filter** (lines 86-89):
```typescript
// DELETE: const filteredProducts = products.filter(...)
// Replace all `filteredProducts` references → `products` (server đã filter rồi).
```

**Wire FilterSidebar** — insert SAU "Categories" block (sau line 169), TRƯỚC "Mobile close" (line 199):
```tsx
<FilterSidebar
  brands={availableBrands}
  value={{ brands: filterBrands, priceMin: filterPriceMin, priceMax: filterPriceMax }}
  onChange={(next) => {
    setFilterBrands(next.brands);
    setFilterPriceMin(next.priceMin);
    setFilterPriceMax(next.priceMax);
  }}
/>
```

**REMOVE old price block** (lines 171-197 — `.filterGroup` "Khoảng giá" với inline inputs + presets) — đã chuyển vào FilterSidebar.

**Update `clearFilters`** (lines 91-96) — D-10: KHÔNG reset categories:
```typescript
const clearFilters = () => {
  setFilterBrands([]);
  setFilterPriceMin(undefined);
  setFilterPriceMax(undefined);
  // categories + searchQuery + sortBy giữ nguyên (D-10)
};
```
Hoặc giữ "Xóa tất cả" header button (line 131) reset toàn bộ + thêm reset filter-only riêng trong FilterSidebar.

**Empty state copy update (D-12)** — lines 243-251:
```tsx
<h3>Không tìm thấy sản phẩm phù hợp với bộ lọc</h3>
<p>Thử bỏ bớt thương hiệu hoặc nới rộng khoảng giá</p>
<Button variant="secondary" onClick={clearFilters}>Xóa bộ lọc</Button>
```

---

### `hooks/useDebouncedValue.ts` (hook, transform) — NEW (optional)

**Analog:** Inline pattern `app/search/page.tsx` lines 28-38:
```typescript
const [query, setQuery] = useState(initialQuery);
const [debouncedQuery, setDebouncedQuery] = useState(initialQuery);

useEffect(() => {
  const t = setTimeout(() => setDebouncedQuery(query), 350);
  return () => clearTimeout(t);
}, [query]);
```

**Extracted hook** (Claude tự tạo `sources/frontend/src/hooks/useDebouncedValue.ts`):
```typescript
import { useEffect, useState } from 'react';

export function useDebouncedValue<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(t);
  }, [value, delayMs]);
  return debounced;
}
```

**Decision matrix:** Nếu FilterSidebar chỉ cần debounce 1 cặp (priceMin+priceMax) thì có thể inline `useEffect` trực tiếp (precedent OrderFilterBar.tsx:38-44). Nếu split `BrandFacet` + `PriceFacet` thì hook tái sử dụng. Claude quyết khi implement.

---

## Shared Patterns

### Backend: ApiResponse envelope
**Source:** `product-service/api/ApiResponse` + usage `ProductController.java` lines 38, 44, 50, 73, 87
**Apply to:** ALL controller endpoints (existing + NEW `/brands`)
```java
return ApiResponse.of(200, "<message>", <data>);
```

### Backend: Soft-delete via @SQLRestriction
**Source:** `ProductEntity` (annotation) — referenced trong ProductCrudService.java:39 comment
**Apply to:** Tất cả JPQL queries trong ProductRepository — KHÔNG cần `AND p.deleted = false`. Hibernate inject tự động.

### Backend: cast() in JPQL for nullable params
**Source:** `OrderRepository.java` lines 22-25
**Apply to:** Nullable `:keyword`, `:priceMin`, `:priceMax` trong `findWithFilters`. List `:brands` KHÔNG cần cast — handle null ở service layer (truyền `null` thay vì empty list).

### Frontend: 'use client' + CSS Modules
**Source:** `OrderFilterBar.tsx:1` + `app/products/page.tsx:1`
**Apply to:** FilterSidebar.tsx (interactive component cần client-side state).

### Frontend: Button tertiary cho destructive idempotent action
**Source:** `OrderFilterBar.tsx:114-126`
**Apply to:** Reset button "Xóa bộ lọc" trong FilterSidebar header.

### Frontend: Non-fatal fetch failure
**Source:** `app/products/page.tsx:42-44` (categories fetch swallow error)
**Apply to:** `listBrands()` fetch trong useEffect — `.catch(() => {})` để brand facet ẩn graceful nếu API fail, KHÔNG crash page.

### Frontend: VND price format
**Source:** `formatPrice` từ `@/services/api` (used in `ProductCard.tsx:9, 79`)
**Apply to:** Display giá trị onBlur trong PriceFacet. Verify import path: `import { formatPrice } from '@/services/api';`. Nếu helper hiện không phù hợp (cần thousand-separator dấu chấm chuẩn VN khi blur), Claude inline `value.toLocaleString('vi-VN')` trong FilterSidebar (UI-SPEC Copywriting Contract VND format note).

### Frontend: Pagination reset on filter change (D-11)
**Source:** Industry standard — Phase 11 Order list precedent.
**Apply to:** `app/products/page.tsx` `load()` — hardcode `page: 0` trong `listProducts()` call khi filter thay đổi. (Optionally store `page` state riêng và `setPage(0)` trong filter handlers — Claude chọn approach.)

---

## No Analog Found

KHÔNG có file nào hoàn toàn thiếu analog. Tất cả 9 files đều có precedent rõ ràng trong codebase.

Edge case: Nếu Claude split `FilterSidebar` thành `BrandFacet.tsx` + `PriceFacet.tsx` (D-discretion UI-SPEC Component Inventory), 2 file con vẫn copy chung pattern từ `OrderFilterBar.tsx` — không cần analog mới.

---

## Metadata

**Analog search scope:**
- Backend: `sources/backend/{order,product,user}-service/src/main/java/.../{web,service,repository}/`
- Frontend: `sources/frontend/src/{app,components/ui,services,hooks,lib}/`

**Files scanned:** 9 (read in full or targeted ranges) + 4 grep scans

**Pattern extraction date:** 2026-05-02

**Key takeaways for planner:**
1. JPQL pattern HAS production precedent (OrderRepository) → low risk refactor.
2. FilterSidebar có 2 layer analog (OrderFilterBar tsx + existing inline price block trong page.tsx) → có thể tham khảo cả hai.
3. Debounce hook OPTIONAL — inline `useEffect+setTimeout` đã chuẩn hoá ở 2 nơi (OrderFilterBar:38-44, search/page.tsx:35-38) → không bắt buộc tạo hook mới.
4. KHÔNG có `lib/format.ts` riêng — `formatPrice` nằm trong `services/api`. Verify khi implement xem có phù hợp cho min/max display, nếu không thì inline `toLocaleString('vi-VN')`.
5. KHÔNG cần migration DB (brand cột đã tồn tại trên ProductEntity).
