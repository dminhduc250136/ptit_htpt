# Phase 16: Seed Catalog Hiện Thực — Research

**Researched:** 2026-05-02
**Domain:** Postgres Flyway seed migration + Unsplash CDN URL strategy + soft-delete pattern + FE FilterSidebar verification
**Confidence:** HIGH (codebase inspection trực tiếp + CONTEXT.md đã chốt 31 decisions; Unsplash hot-link policy verified qua Unsplash docs)

---

## Summary

Phase 16 deliver ~100 sản phẩm tech / 5 categories (điện thoại / laptop / chuột / bàn phím / tai nghe) cho dev environment qua **một file migration duy nhất** `V101__seed_catalog_realistic.sql` đặt trong `db/seed-dev/`. Profile isolation đã sẵn (`application.yml` chỉ inject `db/seed-dev` location khi `SPRING_PROFILES_ACTIVE=dev`) — **không cần sửa config**, chỉ thêm file đúng path.

Strategy reset đã chốt là **soft-delete cũ + insert mới trong cùng V101** (CONTEXT D-04). Codebase audit xác nhận điều này an toàn:
- `inventory_svc.inventory_items.product_id` là **logical reference, KHÔNG phải FK cross-schema** (V1 init có comment rõ "KHÔNG có FK cross-schema/cross-service — service layer validation only"). Soft-delete prod-001..010 KHÔNG break inventory FK constraint.
- `product_svc.reviews` có FK `fk_reviews_product` → products(id), nhưng FK này **không cản soft-delete** (chỉ cản `DELETE` thật). Soft-delete `UPDATE deleted=TRUE` không trigger FK violation.
- Không có FK nào từ `order_svc` đến `product_svc.products` (cross-schema).

FE FilterSidebar đã derive brand list **dynamic** từ API `GET /api/products/brands` (verified `products/page.tsx` line 56-73 + `services/products.ts` `listBrands()`). FilterSidebar component nhận `brands: string[]` prop từ parent — **không có hardcode brand list**. Sau khi seed mới, brand list tự động phản ánh catalog mới.

`next.config.ts` đã có `images.unsplash.com` trong `images.remotePatterns` (line 14-25) — **không cần sửa**.

**Primary recommendation:** Execute SQL gồm 4 blocks tuần tự trong V101: (1) UPDATE soft-delete 5 categories cũ, (2) UPDATE soft-delete 10 products cũ, (3) INSERT 5 categories mới, (4) INSERT ~100 products mới. Cập nhật inventory cho 100 SP mới sang sub-plan riêng (CONTEXT D-06 defer).

---

## User Constraints (from CONTEXT.md)

### Locked Decisions

CONTEXT.md đã chốt 31 decisions (D-01 đến D-31). Tóm tắt locked items quan trọng nhất cho planner — đọc full text trong `16-CONTEXT.md`:

- **D-01:** Migration version = `V101` (KHÔNG phải V7 như roadmap pre-phase setup ghi nhầm). File path = `sources/backend/product-service/src/main/resources/db/seed-dev/V101__seed_catalog_realistic.sql`. KHÔNG sửa application.yml.
- **D-02 / D-03:** Profile isolation tự nhiên qua path `db/seed-dev`. Idempotency: `INSERT ... ON CONFLICT (id) DO NOTHING`.
- **D-04 / D-05:** Reset bằng soft-delete (`UPDATE ... SET deleted=TRUE`) trong CÙNG V101, KHÔNG `TRUNCATE` / `DELETE`. Lý do: cross-service inventory reference + audit trail.
- **D-06:** Cleanup orphan inventory_items DEFER (sub-plan riêng nếu CI fail).
- **D-07 / D-08:** 5 categories tech với id `cat-phone | cat-laptop | cat-mouse | cat-keyboard | cat-headphone`, ~20 SP/category × 5 = 95-105 total.
- **D-09:** Brand list per category locked (Apple, Samsung, Xiaomi, OPPO, Vivo, Realme cho phone; Apple, Dell, HP, Lenovo, ASUS, Acer, MSI cho laptop; etc.).
- **D-10 / D-11:** Naming `"{Brand} {Model}"`, slug kebab-case ASCII unaccent.
- **D-12 / D-13 / D-14:** Price ranges VND (xem CONTEXT D-12), 70% products có original_price > price (markup 5-25%), 30% NULL. Currency NUMERIC(12,2).
- **D-15 / D-16:** Stock distribution chủ đích — 10% < 10, 5% = 0, 85% trong [15, 150]. Viết tay, KHÔNG `random()`.
- **D-17 / D-18 / D-19:** `thumbnail_url` field. URL `https://images.unsplash.com/photo-{ID}?fm=webp&q=80&w=800`. Lưu **full URL** (KHÔNG photo.id).
- **D-20:** Curate 100+ photo IDs vào `IMAGES.csv` trước khi viết SQL.
- **D-21 / D-22:** `next.config.ts` remotePatterns đã có. Fallback nếu URL chết = placeholder image generic (existing).
- **D-23 / D-24 / D-25:** `short_description` 80-200 ký tự tiếng Việt factual. KHÔNG có `description` field trong schema.
- **D-26 / D-27:** FE FilterSidebar KHÔNG sửa (verify dynamic). Sau seed: brand panel chỉ thấy 7-15 brand tech.
- **D-28:** Tất cả 100 SP `status = 'ACTIVE'`.
- **D-29 / D-30 / D-31:** Smoke SQL count tests + Playwright E2E + profile prod negative test.

### Claude's Discretion

- Exact Unsplash photo IDs (curate khi viết SQL, miễn URL stable WebP)
- Exact product names + specs realistic 2026
- Exact pricing trong ranges D-12
- Exact `short_description` wording
- ID conventions chi tiết (đề xuất `prod-{cat3}-{NNN}`, ví dụ `prod-pho-001`)
- Order INSERT statements (recommend group theo category)

### Deferred Ideas (OUT OF SCOPE)

- Multi-image gallery (`product_images` table)
- Long-form description (`description TEXT` column)
- Product variants / SKU / size-color
- Real review seed (REV phase)
- Production catalog seed
- Image CDN proxy / caching
- `photo_id` column thay full URL
- Inventory cleanup migration cho prod-001..010 cũ (defer xử lý chi tiết sang plan)

---

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SEED-01 | Reset categories sang 5 tech (xóa fashion/household/books/cosmetics) | Soft-delete strategy verified an toàn (không FK breakage) — § Findings 2 |
| SEED-02 | Seed ~100 SP với name/brand/price/original_price/short_description/stock | Schema fields đã đủ (V2 + V3) — § Findings 4. Brand list domain-realistic D-09 |
| SEED-03 | Mỗi SP có Unsplash WebP URL `?fm=webp&q=80&w=800` stable | Unsplash hot-link policy verified — full URL OK, IDs stable lifetime của photo trên Unsplash — § Findings 5 |
| SEED-04 | Flyway profile isolation: V101+ tách khỏi baseline V1-V6 | Config `application.yml` đã có profile-dev locations include `db/seed-dev` — § Findings 1 |

---

## Findings

### Finding 1 — Flyway Profile Isolation: ĐÃ SẴN, không cần sửa config

**Confidence:** HIGH (codebase inspection trực tiếp)

`sources/backend/product-service/src/main/resources/application.yml`:

```yaml
# Default profile (prod implicit):
spring.flyway.locations: classpath:db/migration

---
# Profile=dev override:
spring:
  config:
    activate:
      on-profile: dev
  flyway:
    locations: classpath:db/migration,classpath:db/seed-dev
```

**Verification approach (D-31 negative test):**

1. Khi `SPRING_PROFILES_ACTIVE=dev`: Flyway scan cả `classpath:db/migration` (V1-V6 baseline) + `classpath:db/seed-dev` (V100, V101). Chạy theo version order.
2. Khi `SPRING_PROFILES_ACTIVE=prod` (hoặc unset): chỉ scan `db/migration` — V100 và V101 hoàn toàn không tồn tại trong classpath scan path → **không có row trong `product_svc.flyway_schema_history`**. Verify bằng:
   ```sql
   SELECT version, script FROM product_svc.flyway_schema_history WHERE version IN ('100', '101');
   ```
   Profile prod: 0 rows. Profile dev: 2 rows.

**Important: `out-of-order: true`** ([VERIFIED: codebase line 22 application.yml]) cho phép V100/V101 chạy SAU V6 mà không bị Flyway block với "Detected resolved migration not applied to database". Đây là **tính năng bắt buộc** với pattern V100+ seed-dev (vì V100/V101 numerically > V6 baseline; nhưng nếu sau này thêm V7 baseline migration, V101 đã apply rồi mà V7 chưa, Flyway cần `out-of-order` để apply V7).

**Edge case risk:** `out-of-order=true` cho phép thêm migration với version cũ hơn migrations đã chạy. Nếu ai đó thêm V101.5 sau khi V101 đã apply, Flyway sẽ chạy V101.5 (out of order). Trong scope phase này, V101 là duy nhất → **không có rủi ro thực tế**.

[CITED: Flyway docs — out-of-order](https://documentation.red-gate.com/fd/out-of-order-migrations-273973920.html)

---

### Finding 2 — Soft-delete Strategy: An toàn với cross-service references

**Confidence:** HIGH (codebase grep + FK inspection)

CONTEXT D-04/D-05 chốt soft-delete `prod-001..010`. Verify FK impact:

| Reference | Type | Impact của soft-delete |
|-----------|------|------------------------|
| `inventory_svc.inventory_items.product_id = 'prod-001'..'prod-010'` | **Logical only — NO cross-schema FK** (verified V1__init_schema.sql comment line 2-4) | Soft-delete OK. Inventory rows giữ nguyên. Plan 08 orphan check vẫn pass (rows vẫn tồn tại, just `deleted=TRUE` ở phía product). |
| `product_svc.reviews.product_id` (intra-schema FK `fk_reviews_product`) | Real FK (V4 line 14-15) | Soft-delete OK — FK chỉ trigger khi DELETE/TRUNCATE row parent. UPDATE `deleted=TRUE` không vi phạm. Reviews hiện tại trên prod-001..010 nếu có sẽ thành "orphan logical" (parent product invisible) — UI listing đã filter `deleted=FALSE` nên không hiển thị. |
| `order_svc` → product (cross-schema) | KHÔNG có FK (mỗi service own schema riêng) | N/A |

**Conclusion:** Soft-delete là choice ĐÚNG. Nếu hard-delete:
- Hard-delete products → reviews FK violate (lỗi migration).
- Workaround sẽ phức tạp: `DELETE FROM reviews WHERE product_id IN (...)` trước, rồi DELETE products. Soft-delete tránh được.

**Listing query filter verified:** mọi `findActive*` query trong codebase filter `WHERE deleted = FALSE`. UI sẽ chỉ thấy 100 SP mới sau seed.

[VERIFIED: V1__init_schema.sql line 2-4, V4__create_reviews.sql line 14-15]

---

### Finding 3 — Inventory cross-service: D-06 DEFER là quyết định đúng

**Confidence:** HIGH (codebase analysis)

`inventory_svc.inventory_items` hiện chỉ có 10 rows (`inv-001..010` link với `prod-001..010`). Sau Phase 16:

- **Soft-delete prod-001..010** trong product_svc. Inventory rows `inv-001..010` vẫn tồn tại nhưng tham chiếu logical đến products `deleted=TRUE`.
- **100 SP mới** (`prod-pho-001` etc.) **CHƯA có inventory entries**. Add-to-cart cho SP mới có thể fail nếu BE check inventory trước (cần verify).

**Plan 08 orphan check:** Nếu Plan 08 implement query `SELECT inventory_items WHERE product_id NOT IN (SELECT id FROM products WHERE deleted=FALSE)` — sẽ trả về 10 rows orphan (inv-001..010). Nếu query là `... NOT IN (SELECT id FROM products)` (không filter deleted) — 0 rows orphan (rows vẫn tồn tại).

**Recommendation cho planner:**

1. Thêm 1 task trong plan để **inspect Plan 08 orphan check query** xác định nó dùng version `WHERE deleted=FALSE` hay không.
2. Nếu CI orphan check fail sau seed → tạo sub-plan riêng:
   - Option A: Update inventory_items SET quantity=0 WHERE product_id IN (prod-001..010) — keep rows but zero out.
   - Option B: Tạo `inventory-svc/db/seed-dev/V3__seed_catalog_inventory.sql` thêm 100 inventory rows cho SP mới + soft-mark 10 cũ.
3. Nếu CI pass → defer hoàn toàn, document trong VERIFICATION.md.

**100 SP mới có cần inventory entries không?** PHỤ THUỘC vào logic add-to-cart hiện tại — cần plan phase verify. Nếu add-to-cart query inventory bằng `findByProductId()` và thiếu row → fail. Plan phase nên có 1 manual smoke task: thử add SP `prod-pho-001` vào cart, xem có lỗi không.

---

### Finding 4 — Schema fields: ĐỦ, không cần ALTER TABLE

**Confidence:** HIGH

| Field | Source | Type | Phase 16 sử dụng |
|-------|--------|------|------------------|
| `id` | V1 | VARCHAR(36) PK | `prod-pho-001` etc. (≤ 36 char ✓) |
| `name` | V1 | VARCHAR(300) NOT NULL | "Apple iPhone 15 Pro 256GB" (≤ 300 ✓) |
| `slug` | V1 | VARCHAR(320) UNIQUE NOT NULL | `apple-iphone-15-pro-256gb` (≤ 320 ✓) |
| `category_id` | V1 | VARCHAR(36) FK | `cat-phone` etc. |
| `price` | V1 | NUMERIC(12,2) NOT NULL | VND value (≤ 9.99 tỷ ✓) |
| `status` | V1 | VARCHAR(20) NOT NULL | `'ACTIVE'` |
| `deleted` | V1 | BOOLEAN DEFAULT FALSE | `FALSE` |
| `created_at` / `updated_at` | V1 | TIMESTAMP WITH TIME ZONE NOT NULL | `NOW()` |
| `brand` | V2 | VARCHAR(200) nullable | "Apple", "Samsung", etc. |
| `thumbnail_url` | V2 | VARCHAR(500) nullable | Full Unsplash URL |
| `short_description` | V2 | VARCHAR(500) nullable | 80-200 ký tự tiếng Việt |
| `original_price` | V2 | NUMERIC(12,2) nullable | Markup hoặc NULL |
| `stock` | V3 | INT NOT NULL DEFAULT 0 | 0, 3-9, hoặc 15-150 |

**`thumbnail_url` length check:** Unsplash URL pattern `https://images.unsplash.com/photo-{ID}?fm=webp&q=80&w=800`. Photo ID dạng `1505740420928-5e560c06d30e` (~30 ký tự). Full URL ~95 ký tự. VARCHAR(500) **dư thừa**.

[VERIFIED: V1__init_schema.sql, V2__add_product_extended_fields.sql, V3__add_product_stock.sql]

---

### Finding 5 — Unsplash WebP URL Strategy: Stable cho dev, fallback đã có

**Confidence:** HIGH (Unsplash docs hot-link policy 2026 verified)

**Hot-link policy ([CITED: Unsplash Hotlinking Guidelines]):**

- Unsplash **bắt buộc** hot-link images.unsplash.com URLs (không cho phép re-host trên CDN khác).
- Không có rate limit trên image CDN serve (CDN requests KHÔNG tính vào API limit 50/h).
- Photo IDs **stable suốt vòng đời photo** trên Unsplash. Photo bị xóa = URL trả 404.

**URL pattern `?fm=webp&q=80&w=800`:**

- `fm=webp`: format conversion (Unsplash CDN tự convert từ original JPEG sang WebP). VERIFIED stable parameter từ Unsplash imgix-based CDN.
- `q=80`: quality 80 (good balance, ~70KB cho 800px width).
- `w=800`: width 800px (height auto-scale theo aspect ratio).

**Curation strategy thực tế cho 100 photo IDs:**

1. Collect photo IDs từ Unsplash search/collections, theo category:
   - Phone: search "iphone", "samsung galaxy", "smartphone"
   - Laptop: "macbook", "dell xps", "laptop"
   - Mouse: "computer mouse", "gaming mouse"
   - Keyboard: "mechanical keyboard", "keychron"
   - Headphone: "headphones", "sony wh-1000xm5"
2. Verify mỗi URL load bằng cách mở browser hoặc `curl -I`:
   ```bash
   curl -I "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?fm=webp&q=80&w=800"
   # Expect: HTTP/2 200, content-type: image/webp
   ```
3. Lưu vào `.planning/phases/16-seed-catalog-realistic/IMAGES.csv` với columns: `id,suggested_category,photographer,license_note`. Audit-friendly + ngăn duplicate.
4. **Reuse policy:** Unsplash cho phép re-use cùng 1 photo ID cho nhiều product — KHÔNG nên (UX kém). Cần 100 unique IDs.

**Fallback nếu URL chết:** UI hiện đã có placeholder image generic (verified Phase 15 setup). Nếu 1-2 ảnh chết, FE render placeholder, không crash.

**Risk: URL stability over time.** Unsplash photo có thể bị photographer xóa/Unsplash takedown. Test định kỳ (CI cron monthly?) ngoài scope phase này.

[CITED: https://help.unsplash.com/en/articles/2511271-guideline-hotlinking-images]
[CITED: https://help.unsplash.com/en/articles/2511245-unsplash-api-guidelines]

---

### Finding 6 — FE FilterSidebar: ĐÃ derive dynamic, không cần sửa code

**Confidence:** HIGH (code inspection)

**Verification chain:**

1. `sources/frontend/src/app/products/page.tsx` line 27: `const [availableBrands, setAvailableBrands] = useState<string[]>([])`.
2. Line 56-73: `useEffect` gọi `listBrands()` từ `services/products.ts` → fetch `GET /api/products/brands`.
3. Line 204-213: pass `brands={availableBrands}` xuống `<FilterSidebar>`.
4. `FilterSidebar.tsx` line 35-40: nhận `brands: string[]` prop, **render checkboxes từ list này** (line 160-170: `brands.map((b) => <label>...)`).

**Conclusion:** Brand list **100% dynamic từ API**. Sau khi seed mới, BE endpoint `/api/products/brands` sẽ trả về brands mới (Apple, Samsung, Logitech, etc.) — FE tự động hiển thị đúng.

**Acceptance test (D-27):** Mở `/products` → FilterSidebar mở "Thương hiệu" panel → chỉ thấy ~7-15 brand tech, **không có** brand từ catalog cũ (e.g., không có "MAC" cosmetics, "Anessa", "Cuckoo").

**KHÔNG cần task FE code change trong phase này.**

[VERIFIED: products/page.tsx:27,56-73,204-213; FilterSidebar.tsx:35-40,160-170; services/products.ts:78-80]

---

### Finding 7 — Vietnamese unaccent for slug: client-side write-time, không cần Postgres extension

**Confidence:** HIGH

CONTEXT D-11: slug = kebab-case ASCII của name. Vietnamese diacritics → unaccent.

**Decision: viết tay slug trong SQL** (không dùng Postgres `unaccent` extension).

Lý do:
1. Seed data deterministic — slug viết tay 1 lần, ko phụ thuộc runtime function.
2. Postgres `unaccent` extension cần `CREATE EXTENSION` privilege và migration tracking phức tạp hơn.
3. Brand+model thường đã ASCII (Apple, Samsung, iPhone, MacBook). Chỉ vài SP có VN từ generic ("Tai nghe Sony WH-1000XM5") cần unaccent — viết tay nhanh.

**Conversion table cho words common:**

| Vietnamese | ASCII unaccent |
|-----------|----------------|
| điện thoại | dien-thoai |
| chuột | chuot |
| bàn phím | ban-phim |
| tai nghe | tai-nghe |
| không dây | khong-day |

**Slug uniqueness:** Schema có `uq_products_slug UNIQUE`. Strategy avoid collision:
- Slug = `{brand-lowercase}-{model-lowercase}-{variant}` ví dụ `apple-iphone-15-pro-256gb`, `apple-iphone-15-pro-512gb`.
- Nếu 2 SP cùng brand+model nhưng khác variant: thêm storage/color suffix.
- Nếu vẫn collision (rare): suffix `-v2` hoặc số seq.

---

### Finding 8 — INSERT 100 rows trong 1 statement vs nhiều statements

**Confidence:** HIGH

**Recommendation: nhiều INSERT statements group theo category** (5 INSERT statements, mỗi statement multi-row VALUES).

Lý do:
- Flyway migration mặc định **wrap toàn bộ file trong 1 transaction** (Postgres). Nếu 1 row sai → rollback toàn bộ migration → easier debug.
- 5 INSERT chia theo category dễ đọc, dễ patch nếu cần fix 1 category sau này.
- Performance không phải issue ở scale 100 rows.

**Pattern:**
```sql
-- Block 3a: phone category
INSERT INTO product_svc.products (id, name, slug, category_id, brand, price, original_price, short_description, thumbnail_url, stock, status, deleted, created_at, updated_at) VALUES
  ('prod-pho-001', 'Apple iPhone 15 Pro 256GB', 'apple-iphone-15-pro-256gb', 'cat-phone', 'Apple', 28990000.00, 32990000.00, '...', 'https://images.unsplash.com/photo-...?fm=webp&q=80&w=800', 50, 'ACTIVE', FALSE, NOW(), NOW()),
  -- ... 19 more rows
ON CONFLICT (id) DO NOTHING;
```

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Seed data persistence | Database (product_svc) | — | Flyway migration là single source for seed. |
| Profile isolation | Backend config (Spring profile) | Database (Flyway location scan) | `application.yml` quyết định Flyway scan path nào theo `SPRING_PROFILES_ACTIVE`. |
| Brand list display | Frontend (FilterSidebar) | API (BE `/api/products/brands` DISTINCT query) | FE đã dynamic, không cần code change — chỉ cần data đúng ở DB. |
| Image hosting | External CDN (Unsplash) | Frontend (next/image optimization) | Đã có `images.unsplash.com` trong remotePatterns. |
| Category cleanup | Database (UPDATE deleted=TRUE) | Service layer (filter `deleted=FALSE` query) | Soft-delete pattern phổ quát toàn codebase. |

---

## Standard Stack

KHÔNG có dependency mới. Stack đã đủ:

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Flyway | 10.x (Spring Boot 3.3.2 default) | Migration runner | Đã configured V1-V6 baseline + V100 seed |
| PostgreSQL | 15+ | DB | Production runtime |
| Next.js | 16 | FE framework | `next.config.ts` đã có Unsplash domain |

### Supporting

KHÔNG cần.

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `INSERT ... ON CONFLICT DO NOTHING` | `INSERT IF NOT EXISTS` (Postgres không có) | Postgres native pattern là ON CONFLICT — chuẩn |
| Soft-delete | Hard-delete + cascade | Hard-delete vi phạm reviews FK — đã verify |
| Lưu photo.id | Lưu full URL | D-19 lock — full URL pragmatic cho 100 SP scale |
| Postgres `unaccent` | Hand-written ASCII slug | Hand-written deterministic, ko cần extension |

**Installation:** N/A (không thêm package).

---

## Architecture Patterns

### System Architecture Diagram

```
                    SPRING_PROFILES_ACTIVE=dev
                              │
                              ▼
                    ┌─────────────────────┐
                    │ Spring Boot startup │
                    │ (product-service)   │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │ Flyway scan classpath│
                    │  db/migration  (V1-V6)│
                    │  db/seed-dev   (V100,V101 NEW) │
                    └──────────┬──────────┘
                               │
                               ▼
            ┌──────────────────┴──────────────────┐
            │     V101 transaction               │
            │  ┌──────────────────────────────┐  │
            │  │ 1. UPDATE categories         │  │
            │  │    SET deleted=TRUE          │  │
            │  │    WHERE id IN (5 cũ)        │  │
            │  ├──────────────────────────────┤  │
            │  │ 2. UPDATE products           │  │
            │  │    SET deleted=TRUE          │  │
            │  │    WHERE id LIKE 'prod-0%'   │  │
            │  ├──────────────────────────────┤  │
            │  │ 3. INSERT 5 categories tech  │  │
            │  │    ON CONFLICT DO NOTHING    │  │
            │  ├──────────────────────────────┤  │
            │  │ 4. INSERT ~100 products      │  │
            │  │    (5 INSERT × 20 rows)      │  │
            │  │    ON CONFLICT DO NOTHING    │  │
            │  └──────────────────────────────┘  │
            └──────────────────┬──────────────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │ flyway_schema_history│
                    │ row V101 = SUCCESS   │
                    └──────────┬──────────┘
                               │
            ┌──────────────────┴──────────────────┐
            ▼                                     ▼
   ┌────────────────┐                    ┌────────────────┐
   │ GET /products  │                    │ GET /products/ │
   │ → filter       │                    │     brands     │
   │   deleted=FALSE│                    │ → DISTINCT     │
   │ → 100 SP       │                    │ → 7-15 brands  │
   └───────┬────────┘                    └───────┬────────┘
           │                                     │
           ▼                                     ▼
   ┌────────────────┐                    ┌────────────────┐
   │ FE products/   │                    │ FilterSidebar  │
   │ ProductCard ×N │ (Unsplash CDN load)│ render brands  │
   └────────────────┘                    └────────────────┘
```

### Recommended Project Structure

```
sources/backend/product-service/src/main/resources/
├── application.yml                            # Profile dev đã có locations
└── db/
    ├── migration/                             # Baseline V1-V6 — KHÔNG TOUCH
    └── seed-dev/                              # Profile dev only
        ├── V100__seed_dev_data.sql            # Existing — KHÔNG xóa
        └── V101__seed_catalog_realistic.sql   # NEW — phase 16 deliverable

.planning/phases/16-seed-catalog-realistic/
├── 16-CONTEXT.md
├── 16-RESEARCH.md   ← this file
├── IMAGES.csv       # NEW — curated photo IDs (audit trail)
└── 16-PLAN-XX.md    # planner deliverables
```

### Pattern 1: Soft-delete + Insert-new trong 1 migration

**What:** Reset domain data bằng cách ẩn cũ + thêm mới trong cùng transaction.

**When to use:** Khi cần thay catalog/categories nhưng cross-service references chỉ logical (không hard FK).

**Example:**

```sql
-- V101 single transaction (Flyway default wrap)
BEGIN;  -- implicit

-- Step 1: hide old categories
UPDATE product_svc.categories
SET deleted = TRUE, updated_at = NOW()
WHERE id IN ('cat-electronics', 'cat-fashion', 'cat-household', 'cat-books', 'cat-cosmetics');

-- Step 2: hide old products
UPDATE product_svc.products
SET deleted = TRUE, updated_at = NOW()
WHERE id IN ('prod-001','prod-002','prod-003','prod-004','prod-005',
             'prod-006','prod-007','prod-008','prod-009','prod-010');

-- Step 3: insert new categories
INSERT INTO product_svc.categories (id, name, slug, deleted, created_at, updated_at) VALUES
  ('cat-phone',     'Điện thoại', 'dien-thoai', FALSE, NOW(), NOW()),
  ('cat-laptop',    'Laptop',     'laptop',     FALSE, NOW(), NOW()),
  ('cat-mouse',     'Chuột',      'chuot',      FALSE, NOW(), NOW()),
  ('cat-keyboard',  'Bàn phím',   'ban-phim',   FALSE, NOW(), NOW()),
  ('cat-headphone', 'Tai nghe',   'tai-nghe',   FALSE, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Step 4: insert ~20 products per category
INSERT INTO product_svc.products (id, name, slug, category_id, brand, price, original_price,
  short_description, thumbnail_url, stock, status, deleted, created_at, updated_at) VALUES
  ('prod-pho-001', 'Apple iPhone 15 Pro 256GB', 'apple-iphone-15-pro-256gb', 'cat-phone',
   'Apple', 28990000.00, 32990000.00,
   'Chip A17 Pro, camera 48MP, màn 6.1 inch Super Retina XDR',
   'https://images.unsplash.com/photo-XXXXXXXX?fm=webp&q=80&w=800',
   50, 'ACTIVE', FALSE, NOW(), NOW()),
  -- ... 19 phone rows
ON CONFLICT (id) DO NOTHING;

-- Same pattern for laptop, mouse, keyboard, headphone (4 more INSERT statements)
```

### Anti-Patterns to Avoid

- **`TRUNCATE products`** — cascade vào reviews FK violation; mất audit trail.
- **Postgres `random()` cho stock** — non-deterministic, flaky tests.
- **Inline image URL không curate trước** — dễ chọn URL chết / vi phạm license.
- **Hardcode brand list trong FE** — đã không có (verified), đừng tạo regression.
- **Tách migration thành V101a/V101b** — phá atomic rollback. Một file duy nhất.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Migration runner | Bash script wrap psql | Flyway (đã có) | Migration history table, ordering, idempotent |
| Image CDN | Local image storage / S3 setup | Unsplash hot-link | Demo dev, không cần auth/upload |
| Image format conversion | Pre-build WebP cho mỗi photo | Unsplash `?fm=webp` query param | CDN tự convert |
| Random stock distribution | Postgres `random()` + workaround | Hand-written values | Reproducible |
| Vietnamese slug | Postgres `unaccent` extension | Hand-written ASCII slug | Avoid extension dependency |

**Key insight:** Phase 16 là DATA work, không phải CODE work. Mọi tooling đã sẵn — chỉ viết SQL chính xác.

---

## Runtime State Inventory

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | `product_svc.products` (10 rows V100), `product_svc.categories` (5 rows V100), `inventory_svc.inventory_items` (10 rows V2 inventory seed). | (a) Soft-delete 10 products + 5 categories cũ trong V101. (b) 10 inventory rows giữ nguyên (logical reference, no FK). (c) 100 SP mới cần inventory entries — DEFER (D-06). |
| Live service config | KHÔNG có. Phase 16 không touch n8n / external services / Datadog. | None. |
| OS-registered state | KHÔNG có. Không có OS-level task/registration liên quan. | None. |
| Secrets/env vars | KHÔNG có. SPRING_PROFILES_ACTIVE đã configured trong docker-compose. | None — verified profile=dev đang set trong docker-compose dev stack. |
| Build artifacts | Flyway compile classpath include `db/seed-dev/*.sql` qua Maven resource filter. | None — thêm V101 file mới sẽ tự được include trong next build. |

**Database row inventory cụ thể:**
- product_svc.categories: 5 rows cần soft-delete (`cat-electronics`, `cat-fashion`, `cat-household`, `cat-books`, `cat-cosmetics`).
- product_svc.products: 10 rows cần soft-delete (`prod-001` đến `prod-010`).
- product_svc.reviews: KHÔNG có row trên prod-001..010 trong V100 seed (V4 chỉ tạo schema, không seed reviews). Nếu dev đã tạo reviews qua UI sẽ bị "mất hiển thị" sau soft-delete (review parent invisible) — acceptable cho dev DB reset.
- inventory_svc.inventory_items: 10 rows giữ nguyên (logical orphan acceptable).

---

## Common Pitfalls

### Pitfall 1: Slug collision UNIQUE constraint violation

**What goes wrong:** 2 products khác nhau cùng brand+model sinh ra cùng slug → migration fail.

**Why it happens:** Generic naming "Tai nghe Sony" hoặc 2 variants storage khác mà chưa thêm suffix.

**How to avoid:**
- Slug pattern luôn include variant: `apple-iphone-15-pro-256gb` vs `apple-iphone-15-pro-512gb`.
- Trước khi commit: `awk -F"'" '/INSERT INTO product_svc.products/,/ON CONFLICT/ {print $4}' V101*.sql | sort | uniq -d` — phải empty.

**Warning signs:** Flyway migration error "duplicate key value violates unique constraint uq_products_slug".

---

### Pitfall 2: Unsplash photo bị photographer xóa giữa lúc seed và lúc demo

**What goes wrong:** Seed lúc curate URL OK, sau 2 tuần demo URL trả 404.

**Why it happens:** Photographer xóa photo / Unsplash takedown.

**How to avoid:**
- Curate IDs từ Unsplash **collections** (curator-managed, ít bị xóa) thay vì random search results.
- Verify URLs ngay trước commit migration (curl HEAD all 100).
- FE đã có placeholder fallback — UI không crash.

**Warning signs:** Browser console 404 khi load product card.

---

### Pitfall 3: Migration V101 fail giữa chừng → DB partial state

**What goes wrong:** INSERT 70/100 thành công, row 71 lỗi (e.g., FK to non-existent category) → toàn bộ V101 rollback nhưng `flyway_schema_history` đã ghi state pending.

**Why it happens:** Wrong category_id reference, duplicate slug, NUMERIC overflow.

**How to avoid:**
- Categories INSERT phải đứng TRƯỚC products INSERT (atomic block 3 trước block 4).
- Test V101 trên fresh dev DB trước commit: `docker compose down -v && docker compose up product-service -d` (logs scan migration).
- Flyway tự rollback transaction nếu fail. Migration history sẽ ghi FAIL state — dùng `flyway repair` để clean trước retry.

**Warning signs:** Service crash log có "ERROR: insert or update on table ... violates foreign key constraint" hoặc "duplicate key".

---

### Pitfall 4: Profile prod accidentally pickup V101

**What goes wrong:** Dev đặt nhầm V101 vào `db/migration/` thay vì `db/seed-dev/` → migration chạy trong prod, đè dữ liệu prod thật.

**Why it happens:** Copy-paste path từ baseline migration.

**How to avoid:**
- File path phải là `db/seed-dev/V101__seed_catalog_realistic.sql` (verify trong PR review).
- Header comment trong file ghi rõ: `-- DEV PROFILE ONLY — không chạy trong prod`.
- D-31 negative test: chạy `SPRING_PROFILES_ACTIVE=prod` trên fresh DB, verify `flyway_schema_history` không có row V101.

**Warning signs:** Prod DB có catalog giả tech sau deploy.

---

### Pitfall 5: Add-to-cart fail vì 100 SP mới chưa có inventory entries

**What goes wrong:** User click "Thêm vào giỏ" cho `prod-pho-001` → BE check inventory → row không tồn tại → 500 error.

**Why it happens:** D-06 defer inventory cleanup — nếu BE add-to-cart yêu cầu inventory row tồn tại trước, sẽ fail.

**How to avoid:**
- Plan phase add 1 manual smoke task: thử add-to-cart 1 SP mới, verify response 200.
- Nếu fail → tạo sub-plan inventory seed: `inventory-svc/db/seed-dev/V3__seed_catalog_inventory.sql` thêm 100 rows tương ứng.
- Stock trong product_svc.products KHÁC với inventory_svc.inventory_items.quantity (2 source-of-truth riêng biệt — đây là tech debt từ Phase 8).

**Warning signs:** Playwright SMOKE-2 fail ở step add-to-cart sau khi seed.

---

### Pitfall 6: thumbnail_url quá dài hoặc query string thay đổi

**What goes wrong:** URL có Unsplash auto-generated `ixid` / `ixlib` query params → gần 500 char → vi phạm VARCHAR(500).

**Why it happens:** Copy URL từ Unsplash API response có nhiều params.

**How to avoid:**
- LUÔN dùng pattern minimal: `https://images.unsplash.com/photo-{ID}?fm=webp&q=80&w=800` (~95 char).
- KHÔNG copy URL có `?ixid=...&ixlib=...&auto=...` từ API response — strip về chỉ 3 params.

**Warning signs:** INSERT fail với "value too long for type character varying(500)".

---

## Code Examples

### Example 1: V101 SQL skeleton (full pattern)

```sql
-- V101__seed_catalog_realistic.sql
-- Phase 16 (SEED-01..04): Reset catalog cũ + seed ~100 SP / 5 tech categories.
-- Profile: dev only (path db/seed-dev/ excluded từ prod Flyway location scan).
-- Idempotent: ON CONFLICT DO NOTHING cho INSERT.
-- Soft-delete: UPDATE deleted=TRUE thay vì DELETE để giữ audit trail + cross-service refs.

-- ─────────────────────────────────────────────────────────
-- Block 1: Soft-delete 5 categories cũ (V100)
-- ─────────────────────────────────────────────────────────
UPDATE product_svc.categories
SET deleted = TRUE, updated_at = NOW()
WHERE id IN ('cat-electronics', 'cat-fashion', 'cat-household', 'cat-books', 'cat-cosmetics');

-- ─────────────────────────────────────────────────────────
-- Block 2: Soft-delete 10 products cũ (V100: prod-001 .. prod-010)
-- ─────────────────────────────────────────────────────────
UPDATE product_svc.products
SET deleted = TRUE, updated_at = NOW()
WHERE id IN ('prod-001','prod-002','prod-003','prod-004','prod-005',
             'prod-006','prod-007','prod-008','prod-009','prod-010');

-- ─────────────────────────────────────────────────────────
-- Block 3: 5 categories tech mới
-- ─────────────────────────────────────────────────────────
INSERT INTO product_svc.categories (id, name, slug, deleted, created_at, updated_at) VALUES
  ('cat-phone',     'Điện thoại', 'dien-thoai', FALSE, NOW(), NOW()),
  ('cat-laptop',    'Laptop',     'laptop',     FALSE, NOW(), NOW()),
  ('cat-mouse',     'Chuột',      'chuot',      FALSE, NOW(), NOW()),
  ('cat-keyboard',  'Bàn phím',   'ban-phim',   FALSE, NOW(), NOW()),
  ('cat-headphone', 'Tai nghe',   'tai-nghe',   FALSE, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ─────────────────────────────────────────────────────────
-- Block 4a: ~20 phone products
-- ─────────────────────────────────────────────────────────
INSERT INTO product_svc.products (
  id, name, slug, category_id, brand,
  price, original_price, short_description, thumbnail_url,
  stock, status, deleted, created_at, updated_at
) VALUES
  ('prod-pho-001', 'Apple iPhone 15 Pro 256GB', 'apple-iphone-15-pro-256gb', 'cat-phone', 'Apple',
   28990000.00, 32990000.00,
   'Chip A17 Pro, camera 48MP, màn 6.1 inch Super Retina XDR ProMotion',
   'https://images.unsplash.com/photo-XXXXXXXX?fm=webp&q=80&w=800',
   45, 'ACTIVE', FALSE, NOW(), NOW()),
  -- ... 19 more rows
  ('prod-pho-020', 'Realme C53 8GB+256GB', 'realme-c53-8gb-256gb', 'cat-phone', 'Realme',
   3990000.00, NULL,
   'Màn 6.74 inch 90Hz, RAM 8GB, pin 5000mAh, sạc nhanh 33W',
   'https://images.unsplash.com/photo-YYYYYYYY?fm=webp&q=80&w=800',
   85, 'ACTIVE', FALSE, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Block 4b, 4c, 4d, 4e: laptop, mouse, keyboard, headphone (cùng pattern)
```

### Example 2: Smoke test SQL counts (D-29)

```sql
-- Run sau migration apply, expect specific counts
SELECT 'categories_active' AS metric, COUNT(*) AS value FROM product_svc.categories WHERE deleted = FALSE
UNION ALL
SELECT 'products_active',          COUNT(*) FROM product_svc.products   WHERE deleted = FALSE
UNION ALL
SELECT 'distinct_brands',          COUNT(DISTINCT brand) FROM product_svc.products WHERE deleted = FALSE
UNION ALL
SELECT 'unsplash_webp_urls',       COUNT(*) FROM product_svc.products
                                   WHERE deleted = FALSE
                                     AND thumbnail_url LIKE 'https://images.unsplash.com/%'
                                     AND thumbnail_url LIKE '%fm=webp%'
UNION ALL
SELECT 'low_stock_products',       COUNT(*) FROM product_svc.products
                                   WHERE deleted = FALSE AND stock < 10 AND stock > 0
UNION ALL
SELECT 'out_of_stock_products',    COUNT(*) FROM product_svc.products
                                   WHERE deleted = FALSE AND stock = 0
UNION ALL
SELECT 'with_original_price',      COUNT(*) FROM product_svc.products
                                   WHERE deleted = FALSE AND original_price IS NOT NULL;

-- Expected:
-- categories_active     = 5
-- products_active       ∈ [95, 105]
-- distinct_brands       ≥ 15
-- unsplash_webp_urls    = products_active (100%)
-- low_stock_products    ∈ [8, 12]   (~10% of 100)
-- out_of_stock_products ∈ [3, 7]    (~5% of 100)
-- with_original_price   ∈ [65, 75]  (~70% of 100)
```

### Example 3: Profile prod negative test (D-31)

```bash
# Test V101 KHÔNG chạy khi profile=prod
docker compose down -v
docker compose up postgres -d
sleep 5

# Start product-service với profile prod (override)
SPRING_PROFILES_ACTIVE=prod docker compose up product-service -d
sleep 15

# Verify Flyway history KHÔNG có V101
docker compose exec postgres psql -U tmdt -d tmdt -c \
  "SELECT version, script, success FROM product_svc.flyway_schema_history WHERE version IN ('100', '101');"

# Expected output: 0 rows (cả V100 và V101 đều skip trong prod profile)
```

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Playwright 1.x (E2E) + manual SQL queries (smoke) |
| Config file | `sources/frontend/playwright.config.ts` |
| Quick run command | `cd sources/frontend && npx playwright test e2e/smoke.spec.ts` |
| Full suite command | `cd sources/frontend && npx playwright test` (7 spec files: smoke, auth, admin-products, admin-orders, admin-users, order-detail, password-change) |

**Note:** Backend không có JUnit tests. Validation chủ yếu qua: (1) SQL counts manually, (2) Playwright E2E hit `/products` page, (3) profile negative test manual.

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SEED-01 | 5 tech categories active, 5 cũ deleted | smoke SQL | `psql -c "SELECT id, deleted FROM product_svc.categories ORDER BY id"` | ❌ Wave 0 — manual SQL trong VERIFICATION.md |
| SEED-02 | ~100 SP với brand/price/desc | smoke SQL | counts query (Example 2 above) | ❌ Wave 0 |
| SEED-02 | E2E `/products` render ≥20 cards | E2E | `npx playwright test e2e/smoke.spec.ts -g SMOKE-1` | ✅ smoke.spec.ts SMOKE-1 (verify ProductCard render) |
| SEED-03 | Mọi SP có URL `images.unsplash.com/?fm=webp` | smoke SQL | `SELECT COUNT(*) WHERE thumbnail_url LIKE ...` | ❌ Wave 0 |
| SEED-03 | Ảnh load thật (no 404) | E2E network | Playwright `page.on('response')` listener | ❌ Wave 0 — extend smoke.spec.ts hoặc tạo `seed-catalog.spec.ts` |
| SEED-04 | Profile=prod KHÔNG run V101 | manual integration | bash script (Example 3 above) | ❌ Wave 0 — manual command trong VERIFICATION.md, không tự động |
| D-27 | FilterSidebar brand list domain-tech | E2E | mở `/products`, verify 7-15 brands hiển thị, không có "MAC"/"Anessa" | ❌ Wave 0 — extend smoke hoặc test riêng |

### Sampling Rate

- **Per task commit:** SQL count query (manual, < 5s)
- **Per wave merge:** `npx playwright test e2e/smoke.spec.ts` (4 tests, ~30s)
- **Phase gate:** Full suite + profile negative test + manual UI walkthrough

### Wave 0 Gaps

- [ ] `sources/frontend/e2e/seed-catalog.spec.ts` (NEW) — verify catalog page render ≥20 cards/category, no broken images, FilterSidebar brand list domain-tech. **Cover SEED-02, SEED-03, D-27, D-30.**
- [ ] `.planning/phases/16-seed-catalog-realistic/IMAGES.csv` (NEW) — curated 100+ Unsplash photo IDs.
- [ ] Manual smoke SQL queries trong `16-VERIFICATION.md` block (textual commands, không phải auto runner).
- [ ] Manual D-31 profile negative test bash script (textual command trong VERIFICATION.md).

**KHÔNG có gap test framework** — Playwright đã configured, chỉ cần thêm spec file.

---

## Sources

### Primary (HIGH confidence)
- Codebase: `sources/backend/product-service/src/main/resources/application.yml` — profile-dev locations (line 17-23, 45-51)
- Codebase: `sources/backend/product-service/src/main/resources/db/migration/V1..V6` — schema baseline
- Codebase: `sources/backend/product-service/src/main/resources/db/seed-dev/V100__seed_dev_data.sql` — pattern precedent
- Codebase: `sources/backend/inventory-service/src/main/resources/db/migration/V1__init_schema.sql` — verified NO cross-schema FK
- Codebase: `sources/frontend/src/app/products/page.tsx`, `services/products.ts`, `components/ui/FilterSidebar/FilterSidebar.tsx` — verified dynamic brand list
- Codebase: `sources/frontend/next.config.ts` — verified `images.unsplash.com` remotePatterns
- `.planning/research/SUMMARY.md` §"Phase 16 — SEED" + `.planning/research/PITFALLS.md` Pitfall 5,6
- `.planning/phases/16-seed-catalog-realistic/16-CONTEXT.md` — 31 locked decisions

### Secondary (MEDIUM confidence)
- [Unsplash Hotlinking Guidelines](https://help.unsplash.com/en/articles/2511271-guideline-hotlinking-images) — hot-link required, CDN không rate-limit
- [Unsplash API Guidelines](https://help.unsplash.com/en/articles/2511245-unsplash-api-guidelines) — photo ID stability
- [Flyway out-of-order docs](https://documentation.red-gate.com/fd/out-of-order-migrations-273973920.html)

### Tertiary (LOW confidence)
- (none — không có claim LOW confidence trong research này)

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Unsplash photo ID lifetime stable cho dev demo (vài tháng) | Finding 5 | Ảnh 404 → FE placeholder fallback (acceptable). Không block phase. |
| A2 | BE add-to-cart KHÔNG fail nếu inventory_items thiếu row cho prod-pho-XXX | Finding 3, Pitfall 5 | Add-to-cart fail → cần sub-plan inventory seed (D-06 defer cover trường hợp này). |
| A3 | `flyway_schema_history.version` field stores version as VARCHAR (so query `WHERE version IN ('100', '101')` hoạt động) | Finding 1 verification command | Format khác → query sai cách. Workaround: dùng `script LIKE '%V101%'`. |
| A4 | Spring Boot 3.3.2 wrap toàn bộ Flyway SQL file trong 1 transaction (Postgres default) | Pitfall 3 | Nếu không atomic → partial state risk. Mitigate: Flyway `transactional.lock` setting (default true). |

---

## Open Questions

1. **Inventory entries cho 100 SP mới có cần thiết để add-to-cart hoạt động?**
   - What we know: D-06 defer. inventory_items.product_id KHÔNG có FK cross-schema.
   - What's unclear: BE OrderService/CartService có check inventory.quantity > 0 trước khi add-to-cart không? Nếu có và row missing → fail with NPE hoặc 404.
   - Recommendation: Plan phase add 1 manual smoke task (add `prod-pho-001` to cart). Nếu fail → tạo sub-plan `inventory-svc/db/seed-dev/V3__seed_catalog_inventory.sql`. Đây là **vấn đề thực sự cần planner verify**.

2. **Conflict roadmap V7 vs CONTEXT V101 — khi nào fix roadmap?**
   - What we know: CONTEXT D-01 đã chốt V101. Roadmap "Pre-Phase Setup" ghi V7.
   - What's unclear: Plan phase có cần task patch ROADMAP.md không?
   - Recommendation: Add 1 task nhỏ trong plan: `chore(roadmap): patch V7 → V101 reference cho Phase 16`. Hoặc accept là known doc drift, fix khi audit milestone.

3. **Reviews trên prod-001..010 từ dev test history — có cần soft-delete cùng?**
   - What we know: V4 reviews schema chưa có seed. Dev có thể đã tạo reviews qua UI cho prod-001..010 trong dev DB.
   - What's unclear: Sau soft-delete products, reviews vẫn tồn tại nhưng hiển thị "orphan" trong admin moderation view (REV-06 future phase).
   - Recommendation: Acceptable cho dev DB. Document trong VERIFICATION: "reviews trên products đã soft-delete sẽ không hiển thị qua public API (filtered) nhưng vẫn trong DB cho audit." Nếu admin moderation view broken → Phase 21 khắc phục.

4. **Curate 100 photo IDs — manual hay có script support?**
   - What we know: D-20 curate IDs vào IMAGES.csv.
   - What's unclear: Plan phase chia thành sub-task nào? Một task curate hay tách 5 task per category?
   - Recommendation: Plan phase tạo 1 task lớn "Curate IMAGES.csv" trước task viết SQL. Có thể parallel 5 sub-task per category nếu granularity standard.

5. **Frontend ImageError fallback verified?**
   - What we know: Phase 15 nói có placeholder (CONTEXT D-22 reference).
   - What's unclear: ProductCard có dùng `next/image` với `onError` fallback hay không. Chưa verify trực tiếp.
   - Recommendation: Plan phase add 1 verification task đọc ProductCard.tsx, confirm fallback. Nếu không có → defer (KHÔNG scope creep — D-22 đã chốt acceptable).

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| PostgreSQL | Flyway migration | ✓ (assumed dev stack running) | 15+ | — |
| Docker Compose | Profile prod negative test | ✓ (assumed) | — | Manual env override |
| Flyway | Migration runner | ✓ (Spring Boot bundled) | 10.x | — |
| Playwright | E2E verification | ✓ | 1.x | — |
| Unsplash CDN | Image hosting | ✓ (verified accessible) | — | UI placeholder image |
| `curl` (for HEAD verify) | URL stability check khi curate | ✓ (assumed dev machine) | — | Browser open |

**Missing dependencies với no fallback:** None.

**Missing dependencies với fallback:** None.

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Flyway seed trong `db/migration/` chính | Tách `db/seed-dev/` profile-isolated | v1.0 (V100 baseline) | Prod safe |
| Lưu binary image trong DB | CDN hot-link (Unsplash) | Phase 15 v1.2 | DB nhỏ, lazy load tốt |
| Hardcode brand list FE | API DISTINCT query | Phase 14 SEARCH-01 | Catalog change → FE auto reflect |

**Deprecated/outdated:**
- 5 categories cũ (electronics/fashion/household/books/cosmetics): Phase 5 V100 seed — sai domain cho v1.3.
- 10 products demo: Phase 5 V100 — không đủ để demo charts/chatbot.

---

## Project Constraints (from CLAUDE.md / memory)

- **Vietnamese language**: chat/docs/commits tiếng Việt; technical identifiers (table names, SQL keywords, file paths) giữ tiếng Anh.
- **Visible-first priority**: ưu tiên UI/UX visible features. Phase 16 là DATA foundation — visible-first compliant.
- **Dự án thử nghiệm GSD workflow**: KHÔNG phải PTIT student assignment — tránh các framing học thuật trong commit/docs.
- **Auto mode active**: execute decisions từ CONTEXT.md, không Q&A interactive cho routine choices.

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — không có dep mới, mọi tooling verified trong codebase
- Architecture (V101 path + soft-delete + dynamic brand): HIGH — cross-referenced với 6+ codebase files
- Pitfalls (slug collision, Unsplash 404, profile leak, partial state): HIGH — derived từ schema constraints + Unsplash docs
- FilterSidebar dynamic verification: HIGH — code path traced từ products/page → services → component
- Inventory cross-service impact: MEDIUM — verified NO FK, nhưng add-to-cart runtime behavior chưa test (Open Q #1)

**Research date:** 2026-05-02
**Valid until:** 2026-08-02 (3 months — stack stable, Unsplash policy unlikely to change)

---

*Phase: 16-seed-catalog-realistic*
*Researched: 2026-05-02 (auto mode — informed by 31 locked decisions trong CONTEXT.md)*
