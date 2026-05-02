# Phase 14: Basic Search Filters — Discussion Log

> **Audit trail only.** Không dùng làm input cho planning/research/execution agents.
> Decisions canonical ở `14-CONTEXT.md` — log này lưu options đã cân nhắc.

**Date:** 2026-05-02
**Phase:** 14-basic-search-filters
**Areas discussed:** Scope pages, Apply UX behavior, Brand list source, Price input UX

---

## Scope pages

| Option | Description | Selected |
|--------|-------------|----------|
| Chỉ /products (Recommended) | /products là single source of truth, /search độc lập keyword-only | ✓ |
| Cả /search và /products | FilterSidebar reusable, drop vào cả 2 page | |
| Chỉ /search | /search thành trang search+filter chính, /products giữ category browse | |
| Consolidate /search → /products | Delete /search, redirect → /products?keyword=X | |

**User's choice:** Chỉ /products
**Notes:** Tránh refactor `/search` (hiện hardcoded fixture), không break URL/header search box hiện tại. /search giữ nguyên độc lập.

---

## Apply UX behavior

| Option | Description | Selected |
|--------|-------------|----------|
| Auto-apply với debounce (Recommended) | Brand instant, price debounce 400ms | ✓ |
| Auto-apply onChange (no debounce) | Fire request mỗi keystroke | |
| Explicit "Apply" button | User tick + gõ freely, bấm "Lọc" | |
| Hybrid: brand auto, price button | Brand instant, price có nút riêng | |

**User's choice:** Auto-apply với debounce
**Notes:** Modern e-commerce UX, giảm friction. Loading skeleton trong khi fetch.

---

## Brand list source

| Option | Description | Selected |
|--------|-------------|----------|
| Endpoint distinct brands, no count (Recommended) | GET /products/brands, fetch 1 lần, alphabetical | ✓ |
| Endpoint distinct + counts | GROUP BY costlier, counts misleading khi filter active | |
| Hardcode brand list FE | Không query DB, list lệch khi catalog thay đổi | |
| Lấy từ response listProducts hiện tại | Bị giới hạn theo page (pagination 20) | |

**User's choice:** Endpoint distinct brands, no count
**Notes:** Query rẻ + đơn giản. Counts có thể add sau khi catalog đủ lớn (deferred).

---

## Price input UX

| Option | Description | Selected |
|--------|-------------|----------|
| 2 input + presets quick chip (Recommended) | min/max VND format + 4 chips <5tr/5-10tr/10-20tr/>20tr | ✓ |
| Chỉ 2 input min/max | Raw VND number, không thousand separator | |
| Slider 2-handle range | Cần biết min/max catalog trước, thêm complexity | |
| Chỉ preset chips, không input free | Mất flexibility, simplest UX | |

**User's choice:** 2 input + presets quick chip
**Notes:** Phù hợp phân khúc thị trường VN (laptop/điện tử). Validate min>max onBlur, inline error, không fire request.

---

## Claude's Discretion

- CSS class names trong FilterSidebar.module.css (BEM-like consistent)
- Component file structure: 1 file vs split BrandFacet/PriceFacet
- Skeleton placeholder height/animation
- Debounce implementation: useDebouncedValue hook vs lodash.debounce
- Preset chip thresholds chính xác

## Deferred Ideas

- URL state encoding (SEARCH-04) → v1.3
- In-stock toggle (SEARCH-04) → v1.3
- Rating filter (SEARCH-03) → v1.3
- Brand counts → khi catalog đủ lớn
- Slider range UI → revisit nếu user feedback cần
- FilterSidebar cho /search → tách phase riêng nếu cần
- Persist filter qua localStorage
- Mobile collapsible filter drawer
