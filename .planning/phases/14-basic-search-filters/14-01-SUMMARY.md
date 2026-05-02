---
phase: 14-basic-search-filters
plan: 01
subsystem: product-service backend
tags: [search, filter, jpql, brands, price-range, pageable]
requires:
  - ProductEntity (existing — Phase 5/8)
  - OrderRepository.findByUserIdWithFilters (analog Phase 11 — JPQL cast pattern)
provides:
  - ProductRepository.findWithFilters(keyword, brands, priceMin, priceMax, Pageable)
  - ProductRepository.findDistinctBrands()
  - ProductCrudService.listProducts (8-arg overload)
  - ProductCrudService.listBrands()
  - GET /api/products?brands=&priceMin=&priceMax= (extended)
  - GET /api/products/brands (new)
affects:
  - Plan 14-02 (price slider FE — sẽ wire priceMin/priceMax)
  - Plan 14-03 (brand chips FE — sẽ fetch /products/brands)
tech-stack:
  added: []
  patterns:
    - "JPQL cast(:param as type) IS NULL — optional filter (analog OrderRepository)"
    - "Page<Entity> findXxx(..., Pageable) — sort delegation"
    - "@SQLRestriction tự loại deleted — không cần AND p.deleted = false trong JPQL"
key-files:
  created: []
  modified:
    - sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/repository/ProductRepository.java
    - sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/service/ProductCrudService.java
    - sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/web/ProductController.java
decisions:
  - "JPQL cast pattern (D-06/D-07) — same as Phase 11 OrderRepository, không thử Specification API"
  - "includeDeleted param giữ chữ ký không break callers — bị ignore vì @SQLRestriction"
  - "Sort delegate cho Pageable + parseSort helper — xoá productComparator (không còn caller)"
  - "Smoke curl skip — product-service container chưa chạy (chỉ postgres_db Up); UAT pending docker stack"
metrics:
  duration: "~6 min"
  completed: 2026-05-02
  tasks: 3
  files: 3
---

# Phase 14 Plan 01: Backend JPQL Brands + Price Filter Summary

JPQL `findWithFilters` thay in-memory `findAll().stream()` cho `/api/products` + endpoint mới `GET /api/products/brands` cho FE FilterSidebar.

## Files Modified

| File | LOC delta | Note |
|------|-----------|------|
| `ProductRepository.java` | +37 | Thêm `findWithFilters` JPQL + `findDistinctBrands` |
| `ProductCrudService.java` | +57/-22 | 8-arg overload + `listBrands` + `parseSort` helper; xoá `productComparator` + in-memory filter |
| `ProductController.java` | +13/-2 | 3 query params optional + `GET /products/brands` |

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| 1 | `36ec83e` | `feat(14-01): JPQL findWithFilters + findDistinctBrands` |
| 2 | `ab6fb1b` | `feat(14-01): mở rộng listProducts 8-arg + thêm listBrands` |
| 3 | `216a4de` | `feat(14-01): mở rộng /products params + thêm GET /products/brands` |

## Verification Results

- **Maven compile** (Docker `maven:3.9-eclipse-temurin-17`): EXIT 0 sau Task 1, Task 2
- **Maven package -DskipTests** (Docker): EXIT 0 sau Task 3
- **Grep audit:** `findWithFilters | findDistinctBrands | listBrands` → 7 matches across repository/service/controller (≥6 required) PASS
- **Acceptance grep all PASS:**
  - `findBySlug` vẫn còn ✅
  - 3 overload `public Map<String, Object> listProducts(` ✅
  - 0 match `productComparator` ✅
  - 0 match `productRepo.findAll().stream()` ✅
  - `cast(:keyword as string) IS NULL` ✅
  - `:brands IS NULL OR p.brand IN :brands` ✅
  - `Page<ProductEntity>` ✅
  - `@RequestParam(required = false) List<String> brands` + `BigDecimal priceMin` + `BigDecimal priceMax` ✅
  - `@GetMapping("/brands")` + `productCrudService.listBrands()` ✅

## Curl Smoke Test

**Skipped** — `docker ps` cho thấy chỉ `postgres_db` Up, `product-service` container chưa chạy. Plan đã pre-approve fallback "UAT pending docker stack" — đồng bộ pattern Phase 11 UAT pending.

UAT debt thêm vào: cần chạy stack đầy đủ và verify:

```bash
curl -sf "http://localhost:8082/api/products/brands" | head -c 200
curl -sf "http://localhost:8082/api/products?priceMin=1000000&priceMax=99999999&page=0&size=5" | head -c 500
curl -sf "http://localhost:8082/api/products?brands=Dell&brands=HP" | head -c 500
```

Expected: status 200, ApiResponse envelope `{status, message, data}`, content array đúng filter.

## Decisions Made

- **D-06/D-07 implementation:** JPQL `cast(:param as type) IS NULL` pattern — copy từ Phase 11 `OrderRepository.findByUserIdWithFilters`. KHÔNG thử Specification API hay QueryDSL (over-engineering cho 4 param).
- **Sort handling:** `parseSort(String)` helper convert "field,dir" → `Sort` cho `PageRequest`. Default `updatedAt DESC` đồng bộ comparator cũ. `productComparator` xoá vì không còn caller (`paginate` helper giữ lại cho `listCategories`).
- **Backward compat:** 3 overload `listProducts` (4-arg, 5-arg, 8-arg) — 4-arg và 5-arg delegate sang 8-arg với null params. Không có caller nào trong repo dùng `listProducts` 4-arg/5-arg ngoài `listCategories` (khác method) nhưng giữ chữ ký để future-proof.
- **`includeDeleted` ignore:** `@SQLRestriction("deleted = false")` filter ở SQL layer — param giữ chữ ký để không break public API contract.

## Deviations from Plan

None — plan executed exactly as written. Verification commands trong plan dùng `./mvnw` không tồn tại trên repo (no maven wrapper); fallback dùng Docker `maven:3.9-eclipse-temurin-17` (matches Dockerfile build base) để chạy `mvn -q -DskipTests compile/package`. Kết quả EXIT 0 cả compile + package.

## Auth Gates

None.

## Known Stubs

None — backend logic complete và đã wire end-to-end.

## Threat Flags

None — endpoint GET không thay đổi auth boundary, không thêm trust surface mới.

## Ready Signal cho Plan 03

Backend đã sẵn sàng cho FE wire:

- `GET /api/products/brands` → ApiResponse<List<String>> (DISTINCT alphabetical, không null/empty)
- `GET /api/products?keyword=&brands=Dell&brands=HP&priceMin=&priceMax=&page=&size=&sort=` → ApiResponse<Page-shape>
- Cross-facet AND giữa keyword/brand/price; same-facet OR cho brands (IN clause)
- Response shape KHÔNG đổi (content/totalElements/totalPages/currentPage/pageSize/isFirst/isLast)

## Self-Check: PASSED

**Files:**
- FOUND: sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/repository/ProductRepository.java
- FOUND: sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/service/ProductCrudService.java
- FOUND: sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/web/ProductController.java

**Commits:**
- FOUND: 36ec83e
- FOUND: ab6fb1b
- FOUND: 216a4de
