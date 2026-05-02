# Phase 16: Seed Catalog Hiện Thực — Context

**Gathered:** 2026-05-02
**Status:** Ready for planning
**Mode:** Auto (decisions chốt từ research SUMMARY.md + codebase inspection — không có interactive Q&A)

<domain>
## Phase Boundary

Phase này deliver **dữ liệu catalog thực tế** cho dev environment: ~100 sản phẩm phân phối qua 5 categories tech (điện thoại, laptop, chuột, bàn phím, tai nghe), với brand realistic, ảnh Unsplash WebP, và Flyway profile isolation đúng (chỉ chạy trong Spring profile `dev`).

**In scope:**
- Reset 5 categories tech (xóa sạch fashion / household / books / cosmetics — bao gồm dữ liệu cũ từ V100 seed nếu còn)
- Seed ~100 products distributed ~20/category
- Mỗi product có: name, slug, brand, price, original_price (gạch giá), short_description, thumbnail_url (Unsplash WebP), stock, category_id
- Flyway migration trong `db/seed-dev/` (profile `dev` only) — version `V101` (tiếp nối V100)
- Idempotent (`ON CONFLICT DO NOTHING` trên insert; `UPDATE` cho category cleanup)
- Verify: `/products` list hiển thị đúng catalog mới + FilterSidebar brand list không còn brand sai domain

**Out of scope (defer):**
- Multi-image gallery (chỉ dùng `thumbnail_url`, table không có `product_images` — dùng default empty list từ ProductCrudService)
- Long-form description (`short_description` đủ; long `description` field không tồn tại trong schema hiện tại)
- Product variants / SKU / size-color
- Real product reviews seed (REV phase, defer)
- Admin UI để quản lý seed
- Production seed (phase này CHỈ áp dụng cho profile `dev`)

</domain>

<decisions>
## Implementation Decisions

### Flyway Versioning + Profile Isolation

- **D-01:** Migration file mới = `V101__seed_catalog_realistic.sql` (KHÔNG dùng V7 như roadmap pre-phase setup). **Lý do:** baseline product-svc đã đến V6 với `db.migration.locations=classpath:db/migration` cho mọi profile. V100 đã tồn tại trong `db/seed-dev/` cho dev seed. Tiếp nối V101 trong cùng path `db/seed-dev/` đảm bảo profile isolation tự nhiên (chỉ profile `dev` có `flyway.locations=classpath:db/migration,classpath:db/seed-dev`). REQ SEED-04 cũng nói rõ "V101+ tách khỏi baseline V1-V7". → **Conflict resolution:** roadmap section "Pre-Phase Setup" có ghi nhầm V7 — sẽ patch trong plan phase nếu cần. CONTEXT này là source of truth cho versioning.
- **D-02:** File path = `sources/backend/product-service/src/main/resources/db/seed-dev/V101__seed_catalog_realistic.sql`. KHÔNG đặt trong `db/migration/`. KHÔNG cần thay đổi `application.yml` / `application-dev.yml` (cấu hình profile-isolated locations đã sẵn).
- **D-03:** Idempotency: dùng `INSERT ... ON CONFLICT (id) DO NOTHING` cho cả categories và products. Migration có thể chạy lại (Flyway đã apply rồi sẽ skip; nếu schema reset thì re-apply không lỗi).

### Reset Categories — Strategy

- **D-04:** Reset bằng cách **soft-delete** dữ liệu cũ trước khi seed mới, KHÔNG dùng `TRUNCATE` hoặc `DELETE`. Cụ thể trong cùng V101:
  1. `UPDATE product_svc.categories SET deleted = TRUE WHERE id IN ('cat-electronics', 'cat-fashion', 'cat-household', 'cat-books', 'cat-cosmetics')` — soft-delete 5 cat cũ từ V100.
  2. `UPDATE product_svc.products SET deleted = TRUE WHERE id LIKE 'prod-0%'` — soft-delete 10 products cũ từ V100 (`prod-001` .. `prod-010`).
  3. `INSERT INTO categories ...` 5 categories tech mới với id pattern `cat-phone`, `cat-laptop`, `cat-mouse`, `cat-keyboard`, `cat-headphone`.
  4. `INSERT INTO products ...` ~100 products mới với id pattern `prod-pho-001`..`prod-pho-020`, `prod-lap-001`..`prod-lap-020`, etc.
- **D-05:** **Lý do soft-delete thay vì hard-delete:** (a) `inventory_svc.inventory_items` có cross-service FK reference đến `prod-001..010` từ Phase 5 V100 — hard-delete sẽ làm Plan 08 verify orphan-row check fail. Soft-delete giữ rows nhưng `deleted=TRUE` ẩn khỏi listing query (mọi `findActive*` query đã filter `deleted=false`). (b) Audit trail: dev DB có thể mang dữ liệu test history.
- **D-06:** **Cleanup orphan inventory_items:** trong cùng V101, sau soft-delete products, thêm `UPDATE inventory_svc.inventory_items SET ... ` để decommission inventory cho 10 products cũ — nhưng vì cross-schema migration phức tạp, **defer xử lý** trong plan phase: nếu inventory orphan check fail trong CI, sẽ thêm sub-plan riêng (likely thêm V101 trên inventory-svc seed-dev path) hoặc tạo inventory entries mới cho 100 products mới.

### Catalog Composition — 5 Categories × 20 Products

- **D-07:** **Categories** (id, name VN, slug):
  - `cat-phone` — Điện thoại — `dien-thoai`
  - `cat-laptop` — Laptop — `laptop`
  - `cat-mouse` — Chuột — `chuot`
  - `cat-keyboard` — Bàn phím — `ban-phim`
  - `cat-headphone` — Tai nghe — `tai-nghe`
- **D-08:** **Distribution:** ~20 products / category × 5 = ~100 total. Cho phép ±2 (tổng 95-105).
- **D-09:** **Brand list per category** (research-confirmed realistic brands):
  - Phone: Apple, Samsung, Xiaomi, OPPO, Vivo, Realme
  - Laptop: Apple, Dell, HP, Lenovo, ASUS, Acer, MSI
  - Mouse: Logitech, Razer, SteelSeries, Microsoft, Apple
  - Keyboard: Keychron, Logitech, Razer, Corsair, Akko, AKKO, Leopold
  - Headphone: Sony, Bose, Apple, Sennheiser, JBL, Audio-Technica
- **D-10:** **Naming convention** product `name`: `"{Brand} {Model/Series}"` ví dụ `"Apple iPhone 15 Pro 256GB"`, `"Logitech MX Master 3S"`. KHÔNG có suffix marketing như "(Chính hãng)". Vietnamese ok cho từ generic ("Tai nghe", "Bàn phím cơ") nhưng brand+model giữ tên gốc tiếng Anh.
- **D-11:** **Slug:** kebab-case ASCII của name. Ví dụ `apple-iphone-15-pro-256gb`. Vietnamese diacritics → unaccent rồi kebab. Slug phải UNIQUE (DB constraint hiện có).

### Pricing

- **D-12:** **Price ranges (VND, realistic 2026):**
  - Phone: 3,000,000 — 35,000,000
  - Laptop: 12,000,000 — 60,000,000
  - Mouse: 250,000 — 3,500,000
  - Keyboard: 400,000 — 6,000,000
  - Headphone: 350,000 — 12,000,000
- **D-13:** **`original_price` (gạch giá):** ~70% products có `original_price > price` để demo discount badge. Markup 5-25% trên `price`. Còn lại 30% products `original_price = NULL` (không discount). Phân phối ngẫu nhiên nhưng deterministic — viết tay vào SQL, KHÔNG random ở runtime.
- **D-14:** **Currency:** VND, NUMERIC(12,2), không có decimal cents thực tế (0.00). Format hiển thị FE đã handle.

### Stock Distribution

- **D-15:** **Stock per product:** phân phối có chủ đích để chuẩn bị Phase 19 low-stock alert demo:
  - ~10% products có `stock < 10` (3-9 đơn vị) — demo low-stock banner
  - ~5% có `stock = 0` (out of stock)
  - ~85% còn lại có `stock` 15-150 (random nhưng deterministic)
- **D-16:** Viết tay từng giá trị stock vào SQL, KHÔNG dùng PostgreSQL `random()` (bất tiện reproduce + flaky tests).

### Images — Unsplash WebP Strategy

- **D-17:** **Field used:** `thumbnail_url` (đã có trong schema từ V2). Single image per product cho phase này. Multi-image gallery defer.
- **D-18:** **URL format:** `https://images.unsplash.com/photo-{ID}?fm=webp&q=80&w=800` — đúng pattern v1.2 P15 đã verified. Width 800 phù hợp ProductCard (giữ aspect ratio CSS-side).
- **D-19:** **Photo ID strategy:** Lưu **full URL** trong `thumbnail_url` (KHÔNG chỉ lưu photo.id), vì:
  - Schema field là `VARCHAR(500)` — đủ chỗ cho full URL
  - Backend đã lưu URL trong V100 seed (xem ProductDto trong codebase) — không thay đổi contract
  - Nếu sau này muốn tối ưu, có thể migrate sang `photo_id` column riêng — defer.
  - **Trade-off:** Research SUMMARY khuyến nghị "lưu photo.id, construct URL render-time" cho linh hoạt resize. Nhưng ROI thấp ở scale này (~100 SP, dev only) và làm phức tạp BE response. → **Pragmatic choice: lưu full URL.**
- **D-20:** **Image sourcing process:** Curate ~100 photo IDs từ Unsplash search trước khi viết SQL — collect manually vào file `.planning/phases/16-seed-catalog-realistic/IMAGES.csv` (id, suggested_category, photographer, license_note). Plan phase sẽ break ra sub-task. KHÔNG hard-code random IDs trong SQL — verify từng URL load WebP thật trước khi commit.
- **D-21:** **Next.js `remotePatterns`:** verify `images.unsplash.com` đã có trong `next.config.js` (P15 chắc đã thêm). Nếu chưa, plan phase thêm.
- **D-22:** **Fallback nếu Unsplash URL chết:** UI đã có placeholder image generic; KHÔNG cần CDN proxy / image caching trong phase này (defer).

### Description

- **D-23:** **`short_description`:** 1-2 câu tiếng Việt, mô tả USP (key feature). Ví dụ: `"Chip A17 Pro, camera 48MP, màn 6.1 inch Super Retina XDR"`. Length 80-200 ký tự (DB cho phép tới 500).
- **D-24:** **Tone:** factual specs-driven, KHÔNG marketing fluff. KHÔNG nhái copy từ trang web hãng (license risk). Viết tay theo template structure: `{key spec 1}, {key spec 2}, {use case}`.
- **D-25:** **Long description:** schema hiện tại KHÔNG có `description` field (chỉ có `short_description`). Defer multi-paragraph description sang phase khác nếu user yêu cầu.

### FilterSidebar Brand Multi-Select Verification

- **D-26:** Phase này chỉ chịu trách nhiệm seed data đúng brand domain. **FE FilterSidebar code KHÔNG sửa** — brand list đã được derive dynamically từ products query (kiểm tra trong plan phase). Nếu code đang hardcode brand list, plan phase sẽ thêm task fix.
- **D-27:** Verify acceptance: sau khi seed, `/products` page → FilterSidebar mở "Brand" panel → CHỈ thấy 7-15 brand tech (Apple, Samsung, Logitech, Sony, Razer, ASUS, Dell, HP, Lenovo, Bose, ...) — KHÔNG có brand fashion / cosmetics / books cũ.

### Product Status

- **D-28:** Tất cả 100 SP mới có `status = 'ACTIVE'` (không có DRAFT / OUT_OF_STOCK status — `stock = 0` đủ để FE hiển thị "Hết hàng" badge). Giữ schema enum hiện tại.

### Test Strategy

- **D-29:** **Smoke test sau migration:** Verify counts:
  - `SELECT COUNT(*) FROM categories WHERE deleted=FALSE` = 5
  - `SELECT COUNT(*) FROM products WHERE deleted=FALSE` ∈ [95, 105]
  - `SELECT COUNT(DISTINCT brand) FROM products WHERE deleted=FALSE` ≥ 15
  - `SELECT COUNT(*) FROM products WHERE thumbnail_url LIKE 'https://images.unsplash.com/%' AND thumbnail_url LIKE '%fm=webp%'` = same as total
- **D-30:** **E2E Playwright:** thêm/cập nhật test verify `/products` page render ≥20 cards mỗi category, ảnh load (không 404), FilterSidebar brand filter hoạt động — defer chi tiết sang plan phase.
- **D-31:** **Profile prod negative test:** restart product-svc với `SPRING_PROFILES_ACTIVE=prod` và verify migration V101 KHÔNG chạy (Flyway history table không có row V101). Kiểm thủ công bằng `docker compose --profile prod up` hoặc explicit env.

### Claude's Discretion

- Exact Unsplash photo IDs (Claude curate trong plan/execute, miễn là URL stable WebP)
- Exact product names + specs (chọn realistic, đa dạng range price)
- Exact pricing within ranges D-12
- Exact `short_description` wording
- Order of INSERT statements trong SQL (recommend group theo category)
- ID conventions chi tiết (đề xuất `prod-{cat3}-{NNN}`, ví dụ `prod-pho-001`)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase requirements + research
- `.planning/REQUIREMENTS.md` §SEED — full text 4 SEED REQs (SEED-01 .. SEED-04)
- `.planning/ROADMAP.md` §"Phase 16" — Goal + 4 Success Criteria
- `.planning/ROADMAP.md` §"Pre-Phase Setup (v1.3)" — **NOTE conflict:** roadmap ghi V7, CONTEXT.md D-01 chốt V101 (lý do nêu trong D-01)
- `.planning/research/SUMMARY.md` §"Phase 16 — SEED Catalog Realistic" — rationale + pitfalls
- `.planning/research/PITFALLS.md` (nếu agent cần chi tiết Flyway prod-leak / Unsplash strategy)
- `.planning/research/ARCHITECTURE.md` (nếu cần thêm context về Flyway version state)

### Codebase artifacts
- `sources/backend/product-service/src/main/resources/db/seed-dev/V100__seed_dev_data.sql` — pattern precedent, dữ liệu cần override
- `sources/backend/product-service/src/main/resources/db/migration/V2__add_product_extended_fields.sql` — schema fields (`brand`, `thumbnail_url`, `short_description`, `original_price`)
- `sources/backend/product-service/src/main/resources/db/migration/V3__add_product_stock.sql` — `stock` field
- `sources/backend/product-service/src/main/resources/application.yml` — profile-dev `flyway.locations` config (đã include `classpath:db/seed-dev`)
- `sources/frontend/src/components/ui/FilterSidebar/FilterSidebar.tsx` — brand multi-select component (verify dynamic brand list)
- `sources/frontend/src/components/ui/ProductCard/ProductCard.tsx` — image rendering target
- `sources/frontend/next.config.js` — verify `images.remotePatterns` có `images.unsplash.com`

### Cross-phase context (downstream usage)
- Phase 18 (cart→DB) cần ~100 SP để E2E add-to-cart test — đây là dependency
- Phase 19 (admin charts) cần stock distribution có ~10% low-stock cho demo — D-15 đảm bảo
- Phase 22 (chatbot) cần catalog đầy đủ để recommendation context có giá trị — đây là dependency

### External docs
- Unsplash URL format spec: `?fm=webp&q=80&w=800` — pattern verified Phase 15 (xem v1.2 phases nếu cần)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **Flyway seed-dev pattern (V100):** Đã có `db/seed-dev/V100__seed_dev_data.sql` với INSERT cho categories + products. V101 sẽ follow same style (multi-row INSERT statements, Vietnamese comments okay).
- **Profile isolation config:** `application.yml` đã set `spring.flyway.locations` cho profile `dev` include cả `db/migration` + `db/seed-dev`. Profile mặc định / `prod` chỉ có `db/migration`. KHÔNG cần thay đổi config — chỉ thêm file V101 đúng path.
- **Schema fields:** `products` table đã có đủ field cần (name, slug, brand, thumbnail_url, short_description, price, original_price, stock, category_id, status, deleted). KHÔNG cần ALTER TABLE.
- **`ProductCrudService.images` default empty list** (line 206) — multi-image phải sourced từ separate logic mà phase này không touch.
- **FilterSidebar component** đã exist tại `components/ui/FilterSidebar/`. Verify trong plan phase rằng brand options derive dynamically từ API response (không hardcode).

### Established Patterns
- **Flyway versioning:** baseline trên `db/migration/` (V1-V6); seed dev trên `db/seed-dev/` bắt đầu V100. V101 tiếp nối tự nhiên, KHÔNG đụng namespace baseline.
- **Soft-delete:** mọi entity có `deleted BOOLEAN` column. Listing query filter `deleted=FALSE`. Reset = soft-delete cũ + insert mới.
- **ID convention:** kebab-style với prefix domain (`prod-`, `cat-`). Đề xuất extend `prod-{cat3}-{NNN}` cho 100 SP mới.
- **Vietnamese in seed data:** OK cho `name`, `short_description`, `name` của category. Slug phải ASCII unaccent.
- **Price NUMERIC(12,2):** không có currency column — VND mặc định.

### Integration Points
- `inventory-svc` có cross-service reference từ V100 đến `prod-001..010`. Soft-delete (D-04) giữ FK integrity. Plan phase quyết định có cần seed inventory entries cho 100 SP mới hay không (likely yes, sub-plan).
- `/products` API endpoint đã có (`product-svc` GET `/products` với filter+pagination) — không cần BE code mới, chỉ data.
- FE `services/products.ts` không cần thay đổi.
- `next.config.js` `images.remotePatterns` cần `images.unsplash.com` — verify, thêm nếu thiếu.

</code_context>

<specifics>
## Specific Ideas

- **Image curation file:** trước khi viết SQL, tạo `IMAGES.csv` collect 100+ Unsplash photo IDs với category mapping. Đây là sub-deliverable trong plan phase, có thể commit để audit license.
- **Deterministic data:** TẤT CẢ giá trị (price, stock, original_price) viết tay trong SQL — KHÔNG dùng PostgreSQL `random()` / `generate_series` với random. Lý do: reproducible across dev machines + CI.
- **Reset old V100 data trong CÙNG migration V101:** không tách 2 migrations, vì atomic rollback dễ hơn nếu lỗi. Cùng transaction Flyway.
- **Ưu tiên hiển thị (end-user-visible priority):** thumbnail_url quality > exact spec accuracy. Nếu phải chọn giữa "ảnh đẹp" và "spec chính xác 100%", chọn ảnh đẹp (đây là demo, không phải production catalog).

</specifics>

<deferred>
## Deferred Ideas

- **Multi-image gallery:** schema cần `product_images` table riêng — defer sang phase tương lai nếu user yêu cầu.
- **Long-form product description (markdown):** schema cần thêm `description TEXT` column — defer.
- **Product variants (size/color/storage):** model phức tạp, defer.
- **Real product reviews seeded:** REV phase responsibility, defer.
- **Production catalog seed:** phase này CHỈ dev. Nếu cần demo prod data → riêng phase.
- **Image CDN proxy / caching:** Unsplash trực tiếp đủ cho dev demo. Defer khi traffic thật.
- **Dynamic photo ID column thay vì full URL:** D-19 trade-off — defer sang refactor nếu thực sự cần.
- **Inventory-svc cleanup migration cho prod-001..010 cũ:** D-06 — defer xử lý chi tiết sang plan phase, có thể tạo sub-plan riêng nếu CI orphan check fail.

</deferred>

---

*Phase: 16-seed-catalog-realistic*
*Context gathered: 2026-05-02 (auto mode — no interactive Q&A)*
