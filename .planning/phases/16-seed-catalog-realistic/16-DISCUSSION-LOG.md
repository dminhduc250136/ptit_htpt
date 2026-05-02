# Phase 16: Seed Catalog Hiện Thực — Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions captured in `16-CONTEXT.md` — this log preserves the alternatives considered.

**Date:** 2026-05-02
**Phase:** 16-seed-catalog-realistic
**Mode:** Auto (Claude tự chọn defaults dựa trên research SUMMARY.md + codebase inspection — không có interactive AskUserQuestion)
**Areas auto-resolved:** Flyway versioning, Reset strategy, Catalog composition, Pricing, Stock distribution, Image strategy, Description, FilterSidebar verification, Test strategy

---

## Flyway Versioning + Profile Isolation

| Option | Description | Selected |
|--------|-------------|----------|
| V7 trong `db/migration/` | Tiếp baseline product-svc; áp dụng mọi profile (cần guard logic trong SQL `WHERE current_setting('spring.profiles.active') ...`) | |
| V101 trong `db/seed-dev/` | Tiếp V100 đã có; profile `dev` only nhờ config `application.yml`; KHÔNG cần guard SQL | ✓ |
| V7 trong `db/seed-dev/` | Phá nguyên tắc baseline-vs-seed namespace; gây nhầm lẫn về thứ tự apply | |

**Auto choice rationale:** REQ SEED-04 chốt "V101+", `application.yml` đã sẵn profile-isolated locations cho `db/seed-dev`, V100 đã tạo precedent. Roadmap pre-phase setup ghi V7 là sai sót — CONTEXT là source of truth.

---

## Reset Strategy cho Categories cũ

| Option | Description | Selected |
|--------|-------------|----------|
| `TRUNCATE products, categories CASCADE` | Hard-clear bảng. Đơn giản nhưng mất audit + phá inventory FK. | |
| `DELETE FROM products WHERE id LIKE 'prod-0%'` | Hard-delete 10 SP cũ. Phá `inventory_svc.inventory_items` FK. | |
| Soft-delete (`UPDATE ... SET deleted=TRUE`) + INSERT mới | Giữ FK integrity, reuse pattern soft-delete project, audit-friendly. | ✓ |

**Auto choice rationale:** `inventory_svc.inventory_items` reference `prod-001..010` từ V100 — Plan 08 verify orphan-row count = 0. Hard-delete sẽ làm CI fail. Soft-delete giữ FK integrity và đã là pattern dự án.

---

## Catalog Composition

| Option | Description | Selected |
|--------|-------------|----------|
| 100 SP / 5 cat (~20 mỗi cat) | Distribution đều, đủ data cho demo charts + chatbot context | ✓ |
| 50 SP / 5 cat (~10 mỗi cat) | Ít việc curation hơn nhưng charts top-10 thiếu variety | |
| 200 SP / 5 cat (~40 mỗi cat) | Variety tốt hơn nhưng tốn 2x effort curation ảnh | |

**Auto choice rationale:** REQ SEED-02 yêu cầu "~100" và "~20/category". Distribution cân bằng để Phase 19 top-10 chart có meaningful diversity.

---

## Pricing — `original_price` (gạch giá)

| Option | Description | Selected |
|--------|-------------|----------|
| 100% products có discount | Demo discount badge mọi nơi nhưng kém realistic | |
| ~70% có discount, ~30% NULL | Realistic mix; FE test cả 2 path render | ✓ |
| 0% có discount | Đơn giản nhất, mất feature demo | |

**Auto choice rationale:** Realistic e-commerce mix + test cả branches của ProductCard render logic.

---

## Stock Distribution

| Option | Description | Selected |
|--------|-------------|----------|
| Random uniform 10-100 | Đơn giản nhưng Phase 19 low-stock alert không có data demo | |
| Curated: ~10% low-stock (3-9), ~5% out (=0), ~85% normal | Chuẩn bị data cho Phase 19 demo + edge cases UI | ✓ |
| Tất cả 50 (như V3 default) | Quá đồng đều, không demo được edge cases | |

**Auto choice rationale:** Phase 19 (admin charts + low-stock alert) phụ thuộc vào catalog có low-stock data. Curated distribution rẻ + đủ.

---

## Image Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Lưu full Unsplash URL trong `thumbnail_url` | Đơn giản, schema sẵn, BE response không thay đổi | ✓ |
| Lưu chỉ `photo_id`, construct URL render-time | Linh hoạt resize sau này nhưng cần thêm column + FE logic | |
| Self-host images trong `/public/seed/` | KHÔNG phụ thuộc Unsplash availability nhưng tốn disk + license risk | |

**Auto choice rationale:** Schema sẵn, ROI thấp cho refactor URL-construction. Research SUMMARY khuyến nghị photo_id approach nhưng đó là long-term optimization — pragmatic choice cho dev seed.

---

## Description Field

| Option | Description | Selected |
|--------|-------------|----------|
| Chỉ dùng `short_description` (đã có) — 80-200 chars | Schema sẵn, đủ cho card display + product detail header | ✓ |
| Thêm `description TEXT` column (markdown) | Long-form mô tả nhưng cần ALTER TABLE + FE rendering | |
| Bỏ description hoàn toàn | Mất context cho chatbot + SEO | |

**Auto choice rationale:** Schema hiện tại CHỈ có `short_description`. Long description defer (deferred ideas).

---

## FilterSidebar Brand Verification

| Option | Description | Selected |
|--------|-------------|----------|
| Sửa hardcoded brand list trong FilterSidebar (nếu có) | Phòng trường hợp FE đang hardcode | |
| Verify trong plan phase: brand list dynamic từ API | Nếu đúng → không touch FE | ✓ |
| Bỏ qua FilterSidebar | REQ SEED-01 yêu cầu verify brand domain đúng — không thể bỏ qua | |

**Auto choice rationale:** Project có pattern dynamic filter từ API (kiểm trong V100 setup). Plan phase verify; nếu hardcode thì thêm sub-plan fix.

---

## Test Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Smoke SQL counts + Playwright E2E + manual prod-profile verify | Coverage đủ với cost hợp lý | ✓ |
| Chỉ smoke SQL counts | Nhanh nhưng miss UI regression | |
| Full integration test với real product detail flow | Overkill cho phase data-only | |

**Auto choice rationale:** Cân bằng coverage / cost. Profile isolation cần manual verify (CI khó simulate prod profile cleanly).

---

## Claude's Discretion (defer to plan/execute)

- Exact Unsplash photo IDs (curate trong plan phase qua IMAGES.csv)
- Exact product names + spec wording
- Exact prices trong ranges đã chốt (D-12)
- Exact `short_description` content
- Order of INSERT statements

## Deferred Ideas

- Multi-image gallery (need `product_images` table)
- Long-form description (need `description TEXT` column)
- Product variants (size/color/storage)
- Real product reviews seed
- Production catalog seed
- Image CDN proxy / caching
- Photo ID column refactor
- Inventory-svc cleanup migration cho 10 SP cũ (sub-plan if CI orphan check fails)

## Open issue resolved trong CONTEXT (không block planning)

- **Roadmap V7 vs CONTEXT V101 conflict:** D-01 chốt V101 với rationale chi tiết. Plan phase có thể đề xuất patch ROADMAP.md "Pre-Phase Setup" table để consistent.
