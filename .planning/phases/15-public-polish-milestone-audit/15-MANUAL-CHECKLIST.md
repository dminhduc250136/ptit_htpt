# Phase 15 — Manual Visual Smoke Checklist

**Created:** 2026-05-02
**Trigger:** Chạy SAU khi Wave 1 + Wave 2 + Wave 3 complete, TRƯỚC khi `/gsd-verify-work` Phase 15.

## Setup

1. `cd sources/frontend && npm run build` → exit 0
2. `cd sources/frontend && npm start` → app running ở http://localhost:3000
3. (Smoke E2E) Đảm bảo docker compose stack BE running: `docker compose up -d --build` (root)

## Checklist (5 items)

### M1: Hero LCP < 2.5s (PUB-01) — Lighthouse

- Mở Chrome DevTools → Lighthouse tab → Mobile preset → Performance category only → Generate report
- ✅ PASS: LCP metric < 2500ms (xanh hoặc cam, KHÔNG đỏ)
- ❌ FAIL: LCP > 2500ms → check WebP file size (`du -k public/hero/*.webp`), kiểm tra `priority` prop trên `<Image>`, rebuild với `rm -rf .next && npm run build`

### M2: Featured + New Arrivals dedupe (PUB-02) — Visual

- Mở `/`
- Scroll qua Featured carousel (8 cards) — note 8 product names/images
- Scroll xuống New Arrivals grid (8 cards)
- ✅ PASS: KHÔNG product nào trùng giữa 2 sections (single-fetch slice(0,8) vs slice(8,16) đảm bảo không overlap)
- ⚠️ Catalog < 16 products: New Arrivals có thể empty hoặc nhỏ hơn 8 — acceptable per D-09 trade-off

### M3: PDP Thumbnail click → main swap (PUB-03) — Functional

- Mở PDP của product có > 1 ảnh (e.g. `/products/{slug}`)
- Click thumbnail thứ 2, 3, 4 lần lượt
- ✅ PASS: Main image đổi đúng + active thumbnail có border 2px primary color rõ rệt
- ✅ PASS a11y: Inspect HTML — thumbnail active có `aria-current="true"`, inactive KHÔNG có `aria-current`

### M4: Stock badge 3-tier color match (PUB-04) — Visual

- Setup: cần 3 products với `stock` ∈ {0, 5, 100}. Nếu seed không có → admin tạo qua `/admin/products/new` hoặc SQL direct.
- Mở 3 PDP riêng:
  - `stock=100`: ✅ Badge xanh "✓ Còn hàng" + add-to-cart visible
  - `stock=5`: ✅ Badge vàng/amber "⚠ Sắp hết hàng (còn 5)" + add-to-cart visible
  - `stock=0`: ✅ Badge đỏ "✗ Hết hàng" + add-to-cart **HIDDEN hoàn toàn** (return null) + inline message "Sản phẩm tạm hết — vui lòng quay lại sau." + "Xem giỏ hàng" button vẫn show
- ❌ FAIL nếu add-to-cart chỉ disabled (chưa hide): trái D-16

### M5: PDP Breadcrumb brand link (PUB-03) — Functional

- Mở PDP product có brand
- ✅ PASS: Breadcrumb format `Trang chủ / {Brand} / {Name}` (3 segments, KHÔNG còn category)
- Click brand segment → ✅ Navigate sang `/products?brand={brand}`
- Verify FilterSidebar (Phase 14) pre-check brand đó (continuity navigation)
- Mở PDP product KHÔNG có brand → ✅ Fallback: `Trang chủ / Sản phẩm / {Name}` (link "Sản phẩm" → `/products`)

## Sign-off

- [ ] M1 LCP PASS
- [ ] M2 Dedupe PASS
- [ ] M3 Thumbnail PASS
- [ ] M4 Stock badge PASS
- [ ] M5 Breadcrumb PASS

**Tester:** _____________
**Date:** _____________
**Phase 15 verify status:** ⬜ pending → ✅ ready cho `/gsd-verify-work`
