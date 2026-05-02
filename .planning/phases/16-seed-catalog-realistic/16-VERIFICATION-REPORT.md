---
phase: 16-seed-catalog-realistic
verified: 2026-05-02T16:00:00Z
status: human_needed
score: 4/4 must-haves verified (artifact-level) — manual UAT pending
re_verification:
  previous_status: none
  previous_score: n/a
  gaps_closed: []
  gaps_remaining: []
  regressions: []
human_verification:
  - test: "Smoke SQL counts trên DB live (16-VERIFICATION.md §1)"
    expected: "categories_active=5, products_active=100, distinct_brands≥15, unsplash_webp_urls=100, low_stock≥10, out_of_stock≥3, with_original_price∈[65,75]"
    why_human: "Cần DB Postgres chạy với V101 đã apply (SPRING_PROFILES_ACTIVE=dev) — verifier không có DB live"
  - test: "Profile=prod negative test (SEED-04, 16-VERIFICATION.md §2)"
    expected: "Flyway history: 0 rows V100/V101 khi SPRING_PROFILES_ACTIVE=prod (cả V100 V101 nằm trong db/seed-dev/)"
    why_human: "Cần docker compose down -v + restart product-svc với profile prod — destructive, môi trường live"
  - test: "Add-to-cart smoke (verify A2 inventory assumption)"
    expected: "Click 'Thêm vào giỏ' trên 1 SP V101 → toast/cart count update, KHÔNG 500/inventory error"
    why_human: "Cần browser session tại http://localhost:3000/products"
  - test: "Playwright e2e seed-catalog.spec.ts run"
    expected: "7/7 tests pass (5 per-category + 1 image fail-rate ≤20% + 1 brand domain-tech)"
    why_human: "Cần FE+BE stack chạy + DB live data từ V101 — verifier static-analysis only"
  - test: "UI walkthrough /products visual check"
    expected: "5 cat tech sidebar, brand domain-tech only, ảnh load OK, ~70% có original_price gạch ngang, FilterSidebar không có MAC/Anessa/Cuckoo"
    why_human: "Visual rendering quality cần human eyes"
---

# Phase 16: Seed Catalog Hiện Thực — Verification Report

**Phase Goal:** Người dùng truy cập trang sản phẩm thấy ~100 sản phẩm thực tế với ảnh WebP chất lượng cao, thuộc đúng 5 danh mục tech, hiển thị brand chính xác và giá realistic.

**Verified:** 2026-05-02T16:00:00Z
**Status:** human_needed (artifact-level PASSED, manual UAT pending)
**Re-verification:** No — initial verification

---

## VERDICT: PASSED (artifact-level) — Manual UAT pending

Tất cả 4 Success Criteria đã được verify ở mức artifact (file structure + SQL content + spec + config). Phase 16 đã commit đầy đủ artifacts theo plan. Manual UAT (DB live + browser + Playwright run) defer cho `/gsd-verify-work` invocation tiếp theo theo design có chủ đích trong auto mode chain.

---

## Goal Achievement — 4 Success Criteria

### SC-1: ~100 SP / 5 categories tech, categories cũ biến mất

**Status:** PASSED (artifact)

| Check | Method | Result |
|---|---|---|
| 5 INSERT product blocks (1/category) | grep `INSERT INTO product_svc.products` | 5 ✓ |
| 100 product rows | grep `prod-(pho\|lap\|mou\|key\|hea)-\d{3}` | 100 ✓ |
| 5 categories mới INSERT | Block 3 visual: cat-phone, cat-laptop, cat-mouse, cat-keyboard, cat-headphone | ✓ |
| 5 categories cũ soft-delete | Block 1 UPDATE WHERE id IN ('cat-electronics','cat-fashion','cat-household','cat-books','cat-cosmetics') SET deleted=TRUE | ✓ |
| 10 products cũ soft-delete | Block 2 UPDATE WHERE id IN ('prod-001'..'prod-010') SET deleted=TRUE, status='INACTIVE' | ✓ |
| Slug match category | dien-thoai/laptop/chuot/ban-phim/tai-nghe khớp Playwright spec | ✓ |

**Evidence:** `V101__seed_catalog_realistic.sql` 488 lines, 4 blocks structure — Block 1 soft-delete categories, Block 2 soft-delete products, Block 3 INSERT 5 cat tech, Block 4a-4e × 20 SP each.

### SC-2: Mỗi SP có ảnh WebP Unsplash + brand thực tế

**Status:** PASSED (artifact) — runtime image load defer §4 manual

| Check | Method | Result |
|---|---|---|
| 100 thumbnail_url Unsplash WebP | grep `fm=webp&q=80&w=800` | 100 ✓ (100% coverage) |
| 25 distinct brands (≥15 target) | SUMMARY 16-02 | 25 ✓ |
| Brand domain-tech (Apple/Samsung/Dell/Logitech/Sony/Razer/ASUS) | All 7 expected brands appear trong V101 + 18 brand bổ sung | ✓ |
| Phone brands (D-09): Apple/Samsung/Xiaomi/OPPO/Vivo/Realme | Block 4a inspection | ✓ 6/6 |
| Laptop brands: Apple/Dell/HP/Lenovo/ASUS/Acer/MSI | Block 4b | ✓ 7/7 |
| Mouse brands: Logitech/Razer/SteelSeries/Microsoft/Apple | Block 4c | ✓ 5/5 |
| Keyboard brands: Keychron/Logitech/Razer/Corsair/Akko/Leopold | Block 4d | ✓ 6/6 |
| Headphone brands: Sony/Bose/Apple/Sennheiser/JBL/Audio-Technica | Block 4e | ✓ 6/6 |

**Caveat:** Plan 16-01 SUMMARY ghi nhận ~50% photo IDs là pattern-augmented → estimated 10-20% có thể 404 runtime. Mitigation: ProductCard onError fallback (D-22). Tolerance threshold 20% trong Playwright SEED-CAT-2.

### SC-3: FilterSidebar brand multi-select hiển thị brand domain tech

**Status:** PASSED (artifact) — runtime UI render defer §4/§5 manual

| Check | Method | Result |
|---|---|---|
| Playwright spec test exists | `seed-catalog.spec.ts` SEED-CAT-3 describe block | ✓ |
| Selector dùng FilterSidebar component thật | `[role="group"][aria-label="Danh sách thương hiệu"]` (verified component source) | ✓ |
| Assert ≥7 tech brands | `techBrandHits.length ≥ 7` từ list 25 brands | ✓ |
| Negative assert KHÔNG có MAC (cosmetics V100) | `not.toMatch(/\bMAC\b/)` (word-boundary để không match MacBook) | ✓ |
| Negative assert KHÔNG có Anessa | `not.toContain('Anessa')` | ✓ |
| Negative assert KHÔNG có Cuckoo | `not.toContain('Cuckoo')` | ✓ |

**Wiring:** Brand list xuất phát từ `/api/products/brands` endpoint trả DISTINCT brand WHERE deleted=FALSE. Sau V101 apply: 10 products cũ deleted=TRUE → brand cũ bị filter; 100 products mới deleted=FALSE → 25 brand tech hiển thị. Logic chain đúng.

### SC-4: Profile=dev chạy V101, profile=prod KHÔNG chạy. Idempotent.

**Status:** PASSED (artifact)

| Check | Method | Result |
|---|---|---|
| V101 path = `db/seed-dev/` (KHÔNG `db/migration/`) | File location: `sources/backend/product-service/src/main/resources/db/seed-dev/V101__seed_catalog_realistic.sql` | ✓ |
| application.yml default `flyway.locations` = `classpath:db/migration` only | line 23 | ✓ |
| application.yml profile=dev `flyway.locations` adds `classpath:db/seed-dev` | line 50-51 (dev profile block) | ✓ |
| Profile isolation: prod profile KHÔNG scan db/seed-dev | Mặc định flyway chỉ scan `db/migration` → V101 invisible khi không có profile dev | ✓ |
| ON CONFLICT (id) DO NOTHING idempotent | grep count = 7 (1 categories INSERT + 5 products INSERT + 1 trong header comment) | ✓ |
| Soft-delete idempotent (UPDATE WHERE id IN ...) | UPDATE re-apply không thay đổi state khi đã deleted=TRUE | ✓ |

**Evidence (application.yml):**
```yaml
# Default profile (line 17-23):
flyway:
  locations: classpath:db/migration

# Dev profile override (line 46-51):
spring:
  config:
    activate:
      on-profile: dev
  flyway:
    locations: classpath:db/migration,classpath:db/seed-dev
```

Logic này được verified bởi RESEARCH F1 và confirm trong inspection thực tế. SEED-04 contract đáp ứng.

---

## Required Artifacts

| Artifact | Expected | Status | Details |
|---|---|---|---|
| `sources/backend/product-service/src/main/resources/db/seed-dev/V101__seed_catalog_realistic.sql` | 100 INSERT SP + soft-delete cũ + ON CONFLICT | VERIFIED | 488 lines, 4 blocks, 100 SP / 5 cat / 25 brands, 100 unique Unsplash WebP URLs |
| `.planning/phases/16-seed-catalog-realistic/IMAGES.csv` | ≥100 photo IDs / 5 cat | VERIFIED | 107 rows / 5 cat distributed ≥21 each (per 16-01 SUMMARY) |
| `sources/frontend/e2e/seed-catalog.spec.ts` | 3 describe / 7 tests | VERIFIED | 177 lines, 3 describe (SEED-CAT-1/2/3), 7 tests, anonymous storageState |
| `.planning/phases/16-seed-catalog-realistic/16-VERIFICATION.md` | Manual UAT doc 5 sections | VERIFIED | 172 lines, 5 sections (smoke SQL / prod negative / add-to-cart / Playwright / UI walkthrough) + Verdict |
| `.planning/ROADMAP.md` patch V7→V101 | Phase 16 ROADMAP updated | VERIFIED | 4 V101 references trong ROADMAP, KHÔNG còn `product-svc | V7` |

## Key Link Verification

| From | To | Via | Status |
|---|---|---|---|
| application.yml (dev profile) | V101 SQL file | flyway.locations adds `classpath:db/seed-dev` | WIRED |
| V101 INSERT thumbnail_url | IMAGES.csv | 100 unique photo IDs derived | WIRED |
| seed-catalog.spec.ts category test | products/page.tsx | URL `?category={slug}` matches `searchParams.get('category')` | WIRED |
| seed-catalog.spec.ts brand test | FilterSidebar component | Selector `[role="group"][aria-label="Danh sách thương hiệu"]` (verified source line 156-157) | WIRED |
| FilterSidebar brand list | DB products WHERE deleted=FALSE | `/api/products/brands` DISTINCT brand | WIRED (logic chain verified, runtime defer manual) |

## Requirements Coverage

| REQ-ID | Source Plan | Description | Status | Evidence |
|---|---|---|---|---|
| SEED-01 | 16-02 | Reset categories sang 5 tech | SATISFIED (artifact) | V101 Block 1 soft-delete cũ + Block 3 INSERT mới |
| SEED-02 | 16-01, 16-02 | 100 SP realistic + brand thực | SATISFIED (artifact) | 100 SP / 25 brands / 5 cat / D-09 brand list complete |
| SEED-03 | 16-01, 16-02, 16-03 | Ảnh Unsplash WebP `?fm=webp&q=80&w=800` | SATISFIED (artifact) — runtime defer | 100 thumbnail_url match pattern, runtime load test trong manual UAT §4 |
| SEED-04 | 16-02, 16-03 | Profile dev chạy seed, prod skip, idempotent | SATISFIED (artifact) — prod negative test defer | application.yml profile isolation verified, ON CONFLICT 7 lần, prod live test trong manual UAT §2 |

**No orphaned requirements.** REQUIREMENTS.md SEED-01..04 đã claim trong plans 16-01/16-02/16-03.

## Anti-Patterns Found

**None.** Inspection các file modified Phase 16:
- V101 SQL: 100% data fields có giá trị thật (name brand+model, short_description spec-driven 80-200 chars, thumbnail_url unique). KHÔNG TODO/FIXME/PLACEHOLDER. KHÔNG return null/empty array stub.
- seed-catalog.spec.ts: Real assertions (count ≥20, fail rate ≤20%, brand list match). KHÔNG console.log only, KHÔNG empty test body.
- 16-VERIFICATION.md: Concrete expected values (categories_active=5, products_active=100, distinct_brands≥15, V101=25). KHÔNG placeholder values.

**1 known caveat (đã document trong 16-01 SUMMARY và 16-02 SUMMARY):** ~50% Unsplash photo IDs là pattern-augmented → 10-20% rủi ro 404 runtime. Mitigation: ProductCard onError fallback + Playwright tolerance 20% threshold. KHÔNG là stub vì có data thật + có error path defensive.

## Behavioral Spot-Checks

**SKIPPED** — Phase 16 là data-only (Flyway SQL + Playwright spec + config). KHÔNG có app source code change để test runtime behavior. Behavioral checks defer cho `/gsd-verify-work` manual UAT (5 sections trong 16-VERIFICATION.md).

## Human Verification Required

Xem frontmatter `human_verification` cho 5 items:

1. **Smoke SQL counts** (16-VERIFICATION.md §1) — chạy psql query trên DB live
2. **Profile=prod negative** (§2) — destructive test cần docker compose down -v
3. **Add-to-cart smoke** (§3) — verify A2 inventory assumption (D-06)
4. **Playwright e2e run** (§4) — `npx playwright test e2e/seed-catalog.spec.ts`
5. **UI walkthrough** (§5) — visual check /products

Manual UAT này được defer **có chủ đích** trong auto mode chain (note rõ trong 16-03 SUMMARY Task 3.3 và REQUIREMENTS.md SEED-03/04 status). Verifier không có DB live + browser session để execute.

## Gaps Summary

**Không có gaps blocking goal.** Phase 16 artifacts đầy đủ và đúng kỹ thuật:

- SC-1 (100 SP / 5 cat tech, cũ biến mất): SQL Block 1+2+3+4 verified
- SC-2 (Unsplash WebP + brand thực): 100 thumbnail + 25 brand verified
- SC-3 (FilterSidebar tech brand): Playwright spec verify selectors thực
- SC-4 (Profile dev/prod isolation + idempotent): application.yml + ON CONFLICT verified

Manual UAT là expected next step, không phải gap. Phase đã đáp ứng artifact contract.

## Recommendations

1. **Run `/gsd-verify-work`** để execute 5 manual UAT sections — đó là milestone closure path đã được kiến trúc trong design auto-mode + human-verify split.
2. **Nếu Playwright SEED-CAT-2 fail** (Unsplash >20% 404): Plan 16-04 sub-plan curate IMAGES.csv lại với verified IDs (Plan 16-01 disclaimer đã chuẩn bị cho contingency này).
3. **Nếu add-to-cart fail** (A2 inventory assumption sai): Plan 16-04 sub-plan inventory-svc V3 seed (mapping prod-{pho|lap|mou|key|hea}-NNN → inventory rows) — đã document branching trong 16-VERIFICATION.md §3.
4. **Sẵn sàng `/gsd-plan-phase 17`** parallel với manual UAT (Phase 17 không depend on Phase 16 manual sign-off, chỉ cần SQL artifact đã commit).

---

*Verified: 2026-05-02T16:00:00Z*
*Verifier: Claude (gsd-verifier, Opus 4.7 1M)*
