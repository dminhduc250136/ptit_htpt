---
phase: 16-seed-catalog-realistic
plan: 02
subsystem: seed-data
tags: [seed, flyway, migration, sql, catalog]
requirements:
  - SEED-01
  - SEED-02
  - SEED-03
  - SEED-04
dependency_graph:
  requires:
    - "Plan 16-01 IMAGES.csv (107 Unsplash photo IDs)"
  provides:
    - "V101 Flyway migration trong db/seed-dev/ chạy khi SPRING_PROFILES_ACTIVE=dev"
    - "100 SP active / 5 tech categories cho Phase 18 cart→DB E2E + Phase 19 admin charts + Phase 22 chatbot"
  affects:
    - sources/backend/product-service/src/main/resources/db/seed-dev/V101__seed_catalog_realistic.sql
    - .planning/ROADMAP.md
tech_stack:
  added: []
  patterns:
    - "Soft-delete legacy data (UPDATE deleted=TRUE) thay vì hard-delete để giữ FK cross-service"
    - "ON CONFLICT (id) DO NOTHING idempotent INSERT (Flyway re-apply safe)"
    - "Profile isolation tự nhiên qua path db/seed-dev/ (KHÔNG sửa application.yml)"
    - "Deterministic stock distribution viết tay (KHÔNG random()) cho reproducible CI"
key_files:
  created:
    - sources/backend/product-service/src/main/resources/db/seed-dev/V101__seed_catalog_realistic.sql
  modified:
    - .planning/ROADMAP.md
decisions:
  - "Sử dụng full Unsplash URL trong thumbnail_url thay vì photo_id riêng (D-19 trade-off — pragmatic cho 100 SP dev)"
  - "Stock distribution chốt: 3 stock=0, 12 stock 1-9, 85 stock 15-150 (vượt mức ≥3 và ≥10 trong plan-guidance acceptance)"
  - "Original_price ratio chốt: 72/100 có markup 5-25%, 28/100 NULL (gần D-13 ~70%)"
  - "Soft-delete prod-001..010 set status='INACTIVE' để rõ intent + listing query lọc đúng"
  - "Block 4 chia 5 INSERT statements (1/category) thay vì 1 mega-INSERT để readable + comment per-category"
metrics:
  duration_seconds: 360
  completed_date: 2026-05-02
  task_count: 2
  file_count: 2
---

# Phase 16 Plan 02: V101 Seed Catalog Realistic Summary

Flyway migration V101 reset 5 categories cũ + 10 SP cũ và seed 100 SP tech mới (5 cat × 20 SP) với brand realistic + Unsplash WebP thumbnails — đồng thời sửa drift V7→V101 trong ROADMAP.md.

## Mục tiêu (Objective)

Deliver dữ liệu catalog thực tế cho profile=dev. Profile isolation tự nhiên qua path `db/seed-dev/`. Idempotent qua `ON CONFLICT DO NOTHING`. Soft-delete giữ FK cross-service từ Phase 5 V100.

## Kết quả thực hiện

### Task 2.1 — V101__seed_catalog_realistic.sql

- **File:** `sources/backend/product-service/src/main/resources/db/seed-dev/V101__seed_catalog_realistic.sql`
- **Tổng dòng:** 488 (target ≥130, vượt +275%)
- **Cấu trúc:** 4 blocks
  1. Block 1: 1 UPDATE soft-delete 5 categories cũ (cat-electronics..cat-cosmetics)
  2. Block 2: 1 UPDATE soft-delete 10 products cũ (prod-001..prod-010, set status='INACTIVE')
  3. Block 3: 1 INSERT 5 tech categories mới (cat-phone, cat-laptop, cat-mouse, cat-keyboard, cat-headphone)
  4. Block 4: 5 INSERT statements (1/category) × 20 rows = 100 SP

### Task 2.2 — ROADMAP.md patch V7 → V101

- **File:** `.planning/ROADMAP.md`
- **Edit 1** (line 27): Pre-Phase Setup table row product-svc V7 → V101 với note `db/seed-dev/`
- **Edit 2** (lines 40-41): Spring profile dev isolation prose — `V7`/`seed/dev` → `V101`/`seed-dev` + note đã include trong application.yml (verified RESEARCH F1)

## Validation grep outputs

```bash
$ F=sources/backend/product-service/src/main/resources/db/seed-dev/V101__seed_catalog_realistic.sql

$ test -f $F && echo OK
OK

$ grep -c "INSERT INTO product_svc.products" $F
5

$ grep -cE "^\s*\('prod-(pho|lap|mou|key|hea)-[0-9]{3}'" $F
100

$ for c in pho lap mou key hea; do echo -n "$c: "; grep -cE "^\s*\('prod-$c-" $F; done
pho: 20
lap: 20
mou: 20
key: 20
hea: 20

$ grep -cE "https://images\.unsplash\.com/photo-[^']+\?fm=webp&q=80&w=800" $F
100

$ grep -oE "photo-[a-z0-9-]+\?" $F | sort -u | wc -l
100  # 100 unique thumbnails (no reuse)

$ grep -oE "'(Apple|Samsung|Xiaomi|OPPO|Vivo|Realme|Dell|HP|Lenovo|ASUS|Acer|MSI|Logitech|Razer|SteelSeries|Microsoft|Keychron|Corsair|Akko|Leopold|Sony|Bose|Sennheiser|JBL|Audio-Technica)'" $F | sort -u | wc -l
25  # distinct brands (target ≥15 ✓)

$ grep -cE "^\s+0, 'ACTIVE'" $F
3   # stock=0 (target ≥3 ✓)

$ grep -cE "^\s+[1-9], 'ACTIVE'" $F
12  # stock 1-9 (target ≥10 ✓)

$ grep -cE "^\s+[0-9]+\.00, NULL," $F
28  # original_price NULL (~30%)

$ grep -cE "^\s+[0-9]+\.00, [0-9]+\.00, '" $F
72  # original_price có markup (~70%)

$ grep -c "ON CONFLICT (id) DO NOTHING" $F
6   # 1 categories + 5 products INSERT (header comment dòng 4 không có "(id)")

$ grep -n "V101" .planning/ROADMAP.md
27:| product-svc | V101 | Seed ~100 sản phẩm trong db/seed-dev/ ...
40:- Seed migration `V101` phải ở `classpath:db/seed-dev/` ...
69:  4. ...Flyway V101 idempotent...
72:- [ ] 16-02-PLAN.md — Viết V101__seed_catalog_realistic.sql + patch ROADMAP V7→V101

$ grep -nE "product-svc \| V7 |Seed migration .V7." .planning/ROADMAP.md
(empty — V7 reference đã xoá hoàn toàn cho product-svc)
```

## Phân bổ chi tiết per category

| Category   | Count | Brand list                                                     | Price range (VND)        | Stock=0 | Stock 1-9 | Original_price NULL |
| ---------- | ----- | -------------------------------------------------------------- | ------------------------ | ------- | --------- | ------------------- |
| Phone      | 20    | Apple, Samsung, Xiaomi, OPPO, Vivo, Realme (6)                 | 4,990,000 — 34,990,000   | 1       | 3         | 6                   |
| Laptop     | 20    | Apple, Dell, HP, Lenovo, ASUS, Acer, MSI (7)                   | 13,990,000 — 58,990,000  | 1       | 1         | 6                   |
| Mouse      | 20    | Logitech, Razer, SteelSeries, Microsoft, Apple (5)             | 349,000 — 3,290,000      | 0       | 4         | 6                   |
| Keyboard   | 20    | Keychron, Logitech, Razer, Corsair, Akko, Leopold (6)          | 1,290,000 — 5,790,000    | 1       | 1         | 5                   |
| Headphone  | 20    | Sony, Bose, Apple, Sennheiser, JBL, Audio-Technica (6)         | 390,000 — 11,990,000     | 0       | 3         | 5                   |
| **Total**  | **100** | **25 distinct brands**                                        | **349,000 — 58,990,000** | **3**   | **12**    | **28**              |

## Acceptance criteria — pass/fail

- [x] V101 file exist trong `db/seed-dev/` (KHÔNG `db/migration/`)
- [x] Header comment chứa `-- DEV PROFILE ONLY` và `-- Phase 16 (SEED-01..04)`
- [x] 2 UPDATE soft-delete (categories + products)
- [x] 1 INSERT categories với 5 rows tech (cat-phone..cat-headphone)
- [x] 5 INSERT products statements (1/category)
- [x] 100 product rows total, mỗi category 20
- [x] All IDs match `prod-(pho|lap|mou|key|hea)-\d{3}`
- [x] 100 thumbnail_url match Unsplash WebP pattern + 100 unique IDs (no reuse)
- [x] No slug collision
- [x] 25 distinct brands (≥15 ✓)
- [x] Stock=0: 3 (≥3 ✓), stock 1-9: 12 (≥10 ✓)
- [x] Original_price 72 markup / 28 NULL (~70%/30% ✓ D-13)
- [x] All status='ACTIVE', deleted=FALSE
- [x] ON CONFLICT (id) DO NOTHING trên cả categories và 5 products INSERT (idempotent)
- [x] ROADMAP.md V7 → V101 (table + prose) — KHÔNG còn `| product-svc | V7 |` hay `Seed migration `V7``
- [x] ROADMAP Phase 16 details + phases 17-22 KHÔNG bị edit ngoài scope

## Deviations from Plan

**Không có deviation Rule 4 (architectural).** 2 deviations Rule 1-3 nhỏ:

### 1. [Rule 3 - Auto-fix blocking issue] Stock 1-9 grep count ban đầu = 8 (< target 10)
- **Found during:** Task 2.1 verify
- **Issue:** Acceptance criteria yêu cầu `stock 1-9 ≥10`, lần viết đầu tiên có 8 rows.
- **Fix:** Update 4 rows từ stock cao xuống stock thấp:
  - prod-pho-019: 30 → 4
  - prod-pho-008: (giữ nguyên 120 vì đã đủ phone low-stock)
  - prod-lap-017: 45 → 9
  - prod-mou-018: 30 → 2
  - prod-hea-005: 35 → 3
- **Final:** 12 stock 1-9, 3 stock=0 (đạt ≥10 và ≥3).
- **Files modified:** V101__seed_catalog_realistic.sql
- **Commit:** d46f028 (đã include fix trước commit)

### 2. [Rule 2 - Auto-add critical functionality] Soft-delete cũ products set status='INACTIVE'
- **Found during:** Task 2.1 viết Block 2
- **Issue:** Plan body chỉ nói `SET deleted = TRUE, updated_at = NOW()`. Nhưng prod-001..010 đang status='ACTIVE' — nếu chỉ set deleted=TRUE mà status vẫn ACTIVE, query nào lọc theo status (chứ không qua deleted) sẽ trả ra orphan rows hiển thị nhầm.
- **Fix:** Thêm `status = 'INACTIVE'` vào UPDATE Block 2 để rõ intent + defensive cho mọi listing query.
- **Files modified:** V101__seed_catalog_realistic.sql
- **Commit:** d46f028

## Authentication gates

Không. Phase này chỉ static SQL file + markdown patch.

## Threat Flags

Không có new threat surface ngoài threat_model đã chốt trong PLAN (T-16-03 prod-leak, T-16-04 migration fail, T-16-05 Unsplash hot-link). V101 file static SQL không có user input runtime.

## Known Stubs

Không. 100% data fields có giá trị thật:
- name: brand+model thực tế (Apple iPhone 15 Pro Max 256GB, ...)
- short_description: spec-driven 80-200 chars (KHÔNG placeholder)
- thumbnail_url: 100 unique Unsplash IDs từ IMAGES.csv

**Lưu ý 1 stub-like (đã document trong Plan 16-01 SUMMARY):** ~50% IDs là pattern-augmented (có thể 10-20% trả 404 khi load runtime). Mitigation: ProductCard onError fallback (D-22). Plan 16-03 verifier sẽ spot-check.

## Self-Check

- [x] File `sources/backend/product-service/src/main/resources/db/seed-dev/V101__seed_catalog_realistic.sql` tồn tại (verified `test -f` OK)
- [x] File `.planning/ROADMAP.md` chứa V101 reference (verified `grep -n "V101"` 4 matches)
- [x] Commit d46f028 tồn tại (V101 SQL feat)
- [x] Commit 3aca025 tồn tại (ROADMAP docs)
- [x] 100 product rows + 5 INSERT statements + 100 unique thumbnails

## Self-Check: PASSED
