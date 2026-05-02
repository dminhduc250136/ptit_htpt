# Phase 14: Basic Search Filters — Context

**Gathered:** 2026-05-02
**Status:** Ready for planning

<domain>
## Phase Boundary

Ship brand + price filter sidebar trên trang `/products` — JPQL optional params backend, FilterSidebar component với 2 facets (brand multi-select checkbox, price min/max + presets). Same-facet OR, cross-facet AND. KHÔNG URL state (deferred v1.3 — SEARCH-04). KHÔNG rating filter (deferred v1.3 — SEARCH-03). KHÔNG in-stock filter. KHÔNG clear-all UI element ngoài reset button.

**Requirements active:** SEARCH-01 (brand), SEARCH-02 (price).
**Deferred v1.3:** SEARCH-03 (rating), SEARCH-04 (in-stock + URL state).

</domain>

<decisions>
## Implementation Decisions

### Scope & Pages
- **D-01:** FilterSidebar **chỉ áp dụng ở `/products`**. `/search` page (hiện hardcoded fixture) giữ nguyên độc lập keyword-only — KHÔNG add filter sidebar, KHÔNG redirect, KHÔNG break URL hiện tại. Lý do: `/products` đã wired backend thật + có sẵn categories sidebar + priceRange state foundation; consolidate giảm scope mà không phá `/search` link đang dùng.

### Filter Apply UX
- **D-02:** **Auto-apply** với debounce — brand checkbox apply instant onChange; price input debounce 400ms onChange. Loading skeleton hiện trong khi fetch (reuse pattern hiện có ở `/products`). KHÔNG dùng nút "Áp dụng" riêng. Modern e-commerce UX, giảm friction.

### Brand List Source
- **D-03:** Backend thêm endpoint mới `GET /products/brands` → `SELECT DISTINCT brand FROM products WHERE deleted=false AND brand IS NOT NULL ORDER BY brand ASC` (native query hoặc JPQL projection). FE fetch 1 lần khi FilterSidebar mount, cache trong component state. Render checkbox list alphabetical, **KHÔNG show count** (tránh GROUP BY costlier + counts misleading khi filter active).

### Price Input UX
- **D-04:** 2 number input min/max (placeholder "Từ" / "Đến", VND thousand-separator format khi `onBlur`) + **4 preset chips**: "<5tr", "5-10tr", "10-20tr", ">20tr" (single-select chip → fill 2 input + apply ngay). Click chip thay thế giá trị input hiện tại.
- **D-05:** Validation `min > max` chạy **client-side onBlur** → inline error đỏ dưới price section, **KHÔNG fire request server** (SC-3 ROADMAP). Khi user fix → error clear + auto-apply trigger lại.

### Backend Refactor (derived from D-01..D-04)
- **D-06:** `ProductCrudService.listProducts()` hiện dùng in-memory `.filter()` cho keyword (D-02 Phase 5). Phase 14 phải refactor sang JPQL `@Query` trong `ProductRepository` — keyword + brand list + priceMin + priceMax đều thành `:param IS NULL OR ...` clauses. Lý do: in-memory filter break với Pageable thật + không scale; ROADMAP yêu cầu JPQL optional params.
- **D-07:** Same-facet OR (brand IN :brands), cross-facet AND (brand AND price). Pattern: `WHERE (deleted=false) AND (:keyword IS NULL OR LOWER(name) LIKE LOWER(CONCAT('%',:keyword,'%'))) AND (:brands IS NULL OR brand IN :brands) AND (:priceMin IS NULL OR price >= :priceMin) AND (:priceMax IS NULL OR price <= :priceMax)`.
- **D-08:** Query params từ `ProductController.listProducts()` extend: `brands` (List<String>, repeatable `?brands=Dell&brands=HP`) + `priceMin` (BigDecimal optional) + `priceMax` (BigDecimal optional). Giữ nguyên page/size/sort/keyword.

### State & Reset
- **D-09:** Filter state lưu **local React state** trong `/products/page.tsx` (useState) — KHÔNG URL encoding (SEARCH-04 deferred), KHÔNG localStorage. Refresh page → reset toàn bộ filter về default (acceptable cho v1.2 — visible-first scope trim).
- **D-10:** Reset button (label "Xóa bộ lọc") trong sidebar header → set state về default `{ brands: [], priceMin: undefined, priceMax: undefined }` + categories giữ nguyên không bị reset (categories là navigation, không phải filter facet trong scope Phase 14). Reset chỉ affects 2 facets brand + price.

### Pagination Interaction
- **D-11:** Khi filter thay đổi → reset về `page=0` (industry standard, tránh user ở page 5 mà filter ra <5 pages → empty).

### Empty State
- **D-12:** Filter combination 0 results → hiển thị "Không tìm thấy sản phẩm phù hợp với bộ lọc" + nút "Xóa bộ lọc" (gọi cùng handler D-10). Reuse style pattern từ existing empty state ở `/products/page.tsx` nếu có.

### Claude's Discretion
- Tên CSS class names trong `FilterSidebar.module.css` — Claude tự chọn, miễn consistent với BEM-like pattern hiện có.
- Component file structure: 1 file `FilterSidebar.tsx` hay split `BrandFacet.tsx` + `PriceFacet.tsx` — Claude quyết theo độ phức tạp khi implement.
- Skeleton placeholder height/animation cho loading state — Claude pick reasonable defaults.
- Debounce implementation: `useDebouncedValue` hook hay `lodash.debounce` — Claude chọn theo pattern codebase.
- Preset chip thresholds chính xác (e.g. "<5tr" = price < 5000000) — Claude apply chuẩn VND.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Roadmap & Requirements
- `.planning/ROADMAP.md` §"Phase 14: Basic Search Filters" — goal, dependencies, success criteria 1-4
- `.planning/ROADMAP.md` §"Locked Decisions" — JPQL optional params row, Search filters row (Brand+Price only)
- `.planning/REQUIREMENTS.md` — SEARCH-01 (brand), SEARCH-02 (price) acceptance criteria

### Prior Phase Patterns (reuse)
- `.planning/phases/11-address-book-order-history-filtering/11-CONTEXT.md` §"Order filter: server-side" (D-12, D-15) — server-side filtering precedent + pagination pattern
- `.planning/phases/11-address-book-order-history-filtering/11-CONTEXT.md` §"Order filter page" (D-10) — filter bar UI patterns

### Existing Code Touchpoints
- `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/web/ProductController.java` — `listProducts` endpoint cần extend params (D-08)
- `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/service/ProductCrudService.java:33-45` — in-memory filter cần refactor sang JPQL (D-06)
- `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/repository/ProductRepository.java` — thêm `@Query` JPQL methods (D-06, D-07)
- `sources/frontend/src/app/products/page.tsx` — chính trang sẽ wire FilterSidebar (D-01)
- `sources/frontend/src/services/products.ts` — `ListProductsParams` interface cần extend `brands?: string[]`, `priceMin?: number`, `priceMax?: number`
- `sources/frontend/src/components/ui/OrderFilterBar/OrderFilterBar.tsx` — reference component cho filter bar pattern (style + props shape)

### State (no separate ADR docs)
Project KHÔNG có `docs/adr/` directory — toàn bộ decisions track qua `.planning/phases/*/CONTEXT.md`. Không có external spec/library docs cần reference cho phase này (vanilla JPQL + React state, không thêm thư viện mới).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **OrderFilterBar component** (`components/ui/OrderFilterBar/`) — pattern reference cho FilterSidebar layout (filter group title, sticky behavior, mobile toggle).
- **`/products/page.tsx`** — đã có `priceRange` client-side state foundation, sẽ thay bằng server-side `priceMin/priceMax` (D-06).
- **`services/products.ts` `listProducts()`** — chỉ cần extend `ListProductsParams` thêm 3 fields, URLSearchParams handler đã có precedent.
- **Existing categories sidebar** ở `/products` — cùng layout pattern; FilterSidebar add như sibling section (Brand + Price block dưới Categories).
- **`ProductEntity.brand`** (nullable String) + **`ProductEntity.price`** (BigDecimal) — domain model đã sẵn sàng, không cần migration.

### Established Patterns
- **Server-side filtering** (D-12 Phase 11) — backend nhận query params, query DB; client gửi params lên API, không filter client-side. Phase 14 follow tương tự.
- **JPQL optional params** với `:param IS NULL OR ...` clause — pattern Spring Data đã verified production.
- **Pagination reset on filter change** (industry-standard, Phase 11 đã apply).
- **PaginatedResponse<T>** wrapper từ `@/types` — reuse thẳng cho response shape.
- **VND format** — codebase đã có helper format trong product card / cart hiện tại (verify khi implement, có thể trong `lib/format.ts`).

### Integration Points
- Backend: `ProductController` (extend params) → `ProductCrudService.listProducts` (delegate sang Repository) → `ProductRepository` (thêm `@Query` JPQL) + thêm endpoint `/brands` mới.
- Frontend: `/products/page.tsx` (wire FilterSidebar) → `services/products.ts` (extend params) → new component `components/ui/FilterSidebar/`.
- Gateway: KHÔNG cần config thêm — endpoint mới `/products/brands` rơi vào prefix `/api/products/*` đã forward sẵn.

### Anti-patterns to avoid (carry-over)
- KHÔNG dùng JPA Specification API (đã reject ở ROADMAP — JPQL optional params đơn giản hơn cho 2 facets).
- KHÔNG URL-encode filter state (deferred v1.3 — sẽ làm khi cần share-able link).
- KHÔNG client-side filter trên page hiện tại (in-memory `.filter()` chỉ work khi catalog nhỏ — refactor ngay D-06).

</code_context>

<specifics>
## Specific Ideas

- Preset chips price tiers: phù hợp catalog VN (< 5 triệu = entry, 5-10tr = mid, 10-20tr = high, > 20tr = premium) — match phân khúc thị trường laptop/điện tử.
- Brand checkbox alphabetical order, không pin "popular brands" lên đầu (keep simple, không cần curate ranking).
- Empty state copy: "Không tìm thấy sản phẩm phù hợp với bộ lọc" + nút "Xóa bộ lọc".
- Error inline price: "Giá tối thiểu phải nhỏ hơn giá tối đa".

</specifics>

<deferred>
## Deferred Ideas

- **URL state encoding** (SEARCH-04) — share-able filtered URL (`?brands=Dell,HP&priceMin=5000000`) → defer v1.3.
- **In-stock toggle** (SEARCH-04) — checkbox "Chỉ hàng còn" → defer v1.3.
- **Rating filter** (SEARCH-03) — slider/checkbox "Từ 4 sao trở lên" → defer v1.3 (cần avg_rating ổn định từ Phase 13 production data).
- **Brand counts** ("Dell (12)") — defer; sẽ add khi catalog đủ lớn để dynamic count có giá trị.
- **Slider range UI** — defer; sẽ revisit khi user feedback thấy 2 input + presets không đủ.
- **Filter sidebar áp dụng cho `/search`** — defer; nếu UX feedback nói /search cần filter, tách thành phase riêng.
- **Persist filter qua localStorage** — defer; refresh-resets-filter acceptable cho v1.2.
- **Mobile collapsible filter drawer** — current `/products` đã có responsive sidebar; nếu UX cần drawer pattern thì plan-phase / UI-phase quyết.

</deferred>

---

*Phase: 14-basic-search-filters*
*Context gathered: 2026-05-02*
