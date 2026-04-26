---
phase: 07-search-admin-real-data
plan: "02"
subsystem: database
tags: [flyway, jpa, postgresql, product-service, migration]

requires:
  - phase: 05-database-foundation
    provides: "Flyway V1 schema baseline (product_svc.products) + ProductEntity JPA pattern"

provides:
  - "Flyway V2 migration thêm 4 nullable columns vào product_svc.products (brand, thumbnail_url, short_description, original_price)"
  - "ProductEntity JPA fields brand, thumbnailUrl, shortDescription, originalPrice với @Column mapping"
  - "ProductUpsertRequest mở rộng với 4 nullable fields — backward compatible"
  - "createProduct() và updateProduct() persist 4 fields mới"
  - "toResponse() dùng fields thật từ entity thay vì hardcoded defaults"

affects:
  - "07-04 FE services — ProductResponse giờ có brand/thumbnailUrl/shortDescription/originalPrice thật"
  - "07-05 admin products page — admin form có thể gửi brand/thumbnail và data được persist"

tech-stack:
  added: []
  patterns:
    - "Flyway version trong db/migration/ dùng V2 (không phải V3) vì db/seed-dev có version V2 riêng, Flyway không trộn version giữa locations"
    - "IF NOT EXISTS trong ALTER TABLE — idempotent migration an toàn cho test environments"
    - "Nullable fields trong ProductUpsertRequest (String brand, BigDecimal originalPrice) — không dùng @NotBlank"

key-files:
  created:
    - "sources/backend/product-service/src/main/resources/db/migration/V2__add_product_extended_fields.sql"
  modified:
    - "sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/domain/ProductEntity.java"
    - "sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/service/ProductCrudService.java"

key-decisions:
  - "Flyway version V2 cho db/migration/ vì folder này chỉ có V1; V2 trong db/seed-dev là location khác, không conflict"
  - "Tất cả 4 columns nullable — không có NOT NULL constraint — backward compatible với seeded products hiện tại"
  - "toResponse() cập nhật để dùng entity fields thật (Rule 2 deviation) — loại bỏ hardcoded empty strings/nulls"

patterns-established:
  - "Flyway migration naming: Vn__ trong db/migration/ track độc lập với db/seed-dev — không tính version cross-folder"
  - "JPA fields mới cho nullable columns: @Column(length = X) không có nullable = false"

requirements-completed:
  - UI-02

duration: 15min
completed: "2026-04-26"
---

# Phase 7 Plan 02: Search + Admin Real Data — Product Extended Fields Summary

**Flyway V2 migration + ProductEntity/ProductUpsertRequest mở rộng với 4 nullable fields (brand, thumbnailUrl, shortDescription, originalPrice) — admin form giờ persist đủ data thật vào product_svc.products**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-04-26T10:00:00Z
- **Completed:** 2026-04-26T10:03:53Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments

- Flyway V2 migration tạo 4 nullable columns trong product_svc.products với IF NOT EXISTS (idempotent)
- ProductEntity thêm 4 JPA fields với @Column mapping đúng (brand, thumbnailUrl, shortDescription, originalPrice) + accessors record-style
- ProductUpsertRequest mở rộng 4 nullable fields — không break POST request thiếu các fields này
- createProduct() và updateProduct() truyền 4 fields mới vào entity
- toResponse() cập nhật dùng entity fields thật thay vì hardcoded defaults (deviation Rule 2)

## Task Commits

1. **Task 1: Flyway V2 migration** — `a8b906d` (feat)
2. **Task 2: ProductEntity + ProductUpsertRequest** — `7a395f9` (feat)

## Files Created/Modified

- `sources/backend/product-service/src/main/resources/db/migration/V2__add_product_extended_fields.sql` — 4 ALTER TABLE ADD COLUMN IF NOT EXISTS nullable
- `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/domain/ProductEntity.java` — 4 JPA fields + create()/update() cập nhật + 4 accessors
- `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/service/ProductCrudService.java` — ProductUpsertRequest mở rộng + createProduct()/updateProduct() + toResponse() dùng real fields

## Decisions Made

- **Flyway version V2 trong db/migration/:** Plan gốc đề cập V3 nhưng context note đã chỉnh về V2. Folder `db/migration/` chỉ có V1 — V2 là version tiếp theo hợp lệ. V2 trong `db/seed-dev/` là location khác, Flyway không trộn version giữa các locations khác nhau. Dùng V2 trong `db/migration/` là đúng.
- **Tất cả columns nullable:** Đảm bảo backward compatible với seeded products không có brand/thumbnail.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Cập nhật toResponse() để dùng entity fields thật**
- **Found during:** Task 2 (đọc ProductCrudService.java)
- **Issue:** `toResponse()` đang dùng hardcoded `""` cho shortDescription, `""` cho thumbnailUrl, `null` cho brand, `null` cho originalPrice — dù entity giờ có fields thật. Admin GET /admin/products sẽ không trả về brand/thumbnail ngay cả sau khi persist.
- **Fix:** Cập nhật `toResponse()` dùng `product.shortDescription()`, `product.thumbnailUrl()`, `product.brand()`, `product.originalPrice()` với null-safe fallbacks
- **Files modified:** ProductCrudService.java
- **Verification:** Compile thành công; logic đúng theo acceptance criteria
- **Committed in:** `7a395f9` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (Rule 2 — missing critical)
**Impact on plan:** Fix cần thiết cho correctness — plan nói "Admin GET /admin/products trả product với brand, thumbnailUrl..." nhưng toResponse() sẽ không trả các fields này nếu không cập nhật. Không scope creep.

## Issues Encountered

- `mvn` không có trong PATH trên môi trường Windows — dùng Maven wrapper tại `/c/Users/DoMinhDuc/.m2/wrapper/dists/apache-maven-3.9.12-bin/`. Compile thành công (exit code 0, no output).

## Known Stubs

Không có stubs mới. `toResponse()` giờ dùng entity fields thật. Các fields `description`, `discount`, `images`, `tags`, `rating`, `reviewCount`, `stock` vẫn dùng defaults — đây là stubs có chủ ý từ Phase 5, không phải scope của Plan 07-02.

## Threat Surface Scan

Không có surface mới ngoài threat model. `thumbnailUrl` là URL string — không execute, JPA parameterized queries. `brand`/`shortDescription`/`originalPrice` là business data — không sensitive.

## Next Phase Readiness

- product-service có đủ schema và entity để nhận 4 fields mới từ admin form (D-03)
- Phase 7 Plan 05 (admin products page) có thể gửi brand/thumbnail và data sẽ được persist thật
- Plan 07-04 (FE services) có thể dùng brand/thumbnailUrl từ ProductResponse thật

---
*Phase: 07-search-admin-real-data*
*Completed: 2026-04-26*
