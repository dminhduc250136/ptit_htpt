---
phase: 14-basic-search-filters
plan: 03
status: completed
completed_at: 2026-05-02
---

# Plan 14-03 Summary — Wire Products Page + UAT

## Files modified

- `sources/frontend/src/services/products.ts` — +16 lines (ListProductsParams +3 fields; `qs.append('brands')`; `listBrands()` export)
- `sources/frontend/src/app/products/page.tsx` — +59/-43 (state migration `priceRange` → `filterBrands`/`filterPriceMin`/`filterPriceMax`/`availableBrands`; FilterSidebar wired; client-side `.filter()` removed; `clearFilters` vs `clearAll` split; D-12 empty-state copy)
- `sources/frontend/src/components/ui/FilterSidebar/FilterSidebar.tsx` — UI polish post-UAT (price inputs stacked vertically, separator removed, full-width inputs)
- `sources/frontend/src/components/ui/FilterSidebar/FilterSidebar.module.css` — flex direction column cho `.priceInputs`, `min-width: 0` fix, full-width input
- `sources/frontend/src/app/products/page.module.css` — `padding-right` + `scrollbar-gutter: stable` cho `.sidebar`

## Commits

- `cf8190a` feat(14-03): mở rộng ListProductsParams + thêm listBrands()
- `53aecd0` feat(14-03): wire FilterSidebar vào /products + bỏ client-side filter
- `19c8941` fix(14-02): khắc phục price input tràn width sidebar (UAT polish)
- `d48ab9a` fix(14-02): stack price inputs dọc + thêm gutter cho sidebar scroll (UAT polish)

## Verification

- `npx tsc --noEmit` → exit 0
- `npm run build` → exit 0 (Next 16.2.3 Turbopack, 19 static pages, không warning phase 14)
- Grep audit `page.tsx`:
  - Forbidden patterns (`priceRange`, `filteredProducts`, `products.filter(`) → 0 matches
  - Required tokens (`FilterSidebar`, `listBrands`, `filterBrands`, `filterPriceMin`, `filterPriceMax`, `page: 0`, copy strings D-12) → 17 matches

## UAT — backend + bundle (orchestrator headless verify)

Stack rebuild + sample brand seed (9 brands UPDATE vào product_svc.products vì DB seed thiếu brand data — note debt cho seed-data phase tương lai).

| UAT | Item | Result |
|-----|------|--------|
| 1 | `/api/products/brands` DISTINCT alphabetical | 9 brands sorted ✓ |
| 2 | Single brand filter (`?brands=Sony`) | total=1 ✓ |
| 3 | Multi-brand OR (`?brands=Sony&brands=MAC`) | total=2 ✓ |
| 4 | Price range (`?priceMin=5M&priceMax=10M`) | total=1 ✓ |
| – | Cross-facet brand+price | NXB Trẻ + priceMin → 2 sách ✓ |
| 6 | Validation copy "Giá tối thiểu phải nhỏ hơn giá tối đa" | bundled JS chunk ✓ |
| 7 | 4 preset labels `<5tr` `5-10tr` `10-20tr` `>20tr` | bundled ✓ |
| 10 | Empty state copy D-12 | bundled ✓ |
| – | A11y `aria-pressed`, `role="alert"`, `role="group"` | bundled ✓ |

## UAT — visual/interactive (user verify)

- Price input UI tràn width sidebar → fix `19c8941` + `d48ab9a` (stack dọc, full-width). User confirm "đã ngon".
- Sidebar scrollbar dính products grid → fix `d48ab9a` (`scrollbar-gutter: stable`). User confirm.
- Items 5/8/9/11/12/13/14 (VND format onBlur, reset behavior, header "Xóa tất cả", pagination reset, categories not reset, A11y keyboard, mobile drawer): logic đúng ở source level, defer interactive verify cho follow-up session — pattern giống Phase 11/13 UAT debt.

## Outcome

- SEARCH-01 (brand multi-select): DONE
- SEARCH-02 (price range): DONE
- SEARCH-03/04 (advanced filters): vẫn deferred v1.3 theo ROADMAP
- Phase 14 ready for milestone v1.2 closure planning.

## Notes

- Brand seed UPDATE còn lại trong DB sample data — không revert vì giúp UAT future phases. Cân nhắc thêm brand vào Flyway seed migration ở phase v1.3.
- `priceSeparator` CSS class còn unused sau khi remove `<span>` — cleanup nhỏ có thể đính kèm vào phase 15 cleanup pass.
