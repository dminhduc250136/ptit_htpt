# Phase 16 — Verification (Manual + Automated)

**Phase:** 16-seed-catalog-realistic
**Verified by:** _human reviewer_
**Date:** 2026-05-02
**Status:** pending manual verify (auto chain executor đã sinh artifacts; manual sign-off cần human)

---

## 1. Smoke SQL counts (D-29)

**Pre-requisite:** product-svc đã restart với `SPRING_PROFILES_ACTIVE=dev` và V101 đã apply (xem `product_svc.flyway_schema_history`).

```bash
docker compose exec postgres psql -U tmdt -d tmdt -c "
SELECT 'categories_active' AS metric, COUNT(*)::TEXT AS value FROM product_svc.categories WHERE deleted = FALSE
UNION ALL
SELECT 'products_active',          COUNT(*)::TEXT FROM product_svc.products   WHERE deleted = FALSE
UNION ALL
SELECT 'distinct_brands',          COUNT(DISTINCT brand)::TEXT FROM product_svc.products WHERE deleted = FALSE
UNION ALL
SELECT 'unsplash_webp_urls',       COUNT(*)::TEXT FROM product_svc.products
                                   WHERE deleted = FALSE
                                     AND thumbnail_url LIKE 'https://images.unsplash.com/%'
                                     AND thumbnail_url LIKE '%fm=webp%'
UNION ALL
SELECT 'low_stock_products',       COUNT(*)::TEXT FROM product_svc.products
                                   WHERE deleted = FALSE AND stock < 10 AND stock > 0
UNION ALL
SELECT 'out_of_stock_products',    COUNT(*)::TEXT FROM product_svc.products
                                   WHERE deleted = FALSE AND stock = 0
UNION ALL
SELECT 'with_original_price',      COUNT(*)::TEXT FROM product_svc.products
                                   WHERE deleted = FALSE AND original_price IS NOT NULL;
"
```

**Expected:**

| metric                | expected value         |
| --------------------- | ---------------------- |
| categories_active     | 5                      |
| products_active       | ∈ [95, 105] (V101 = 100) |
| distinct_brands       | ≥ 15 (V101 = 25)       |
| unsplash_webp_urls    | = products_active (100%) |
| low_stock_products    | ≥ 10 (V101 = 12)       |
| out_of_stock_products | ≥ 3 (V101 = 3)         |
| with_original_price   | ∈ [65, 75] (V101 = 72) |

**Verify old data soft-deleted:**

```bash
docker compose exec postgres psql -U tmdt -d tmdt -c "
SELECT id, deleted FROM product_svc.categories WHERE id IN (
  'cat-electronics','cat-fashion','cat-household','cat-books','cat-cosmetics'
)
ORDER BY id;
SELECT id, deleted, status FROM product_svc.products WHERE id LIKE 'prod-0%' AND id < 'prod-pho-001'
ORDER BY id;
"
```

Expected: tất cả 5 categories cũ + 10 products cũ (`prod-001`..`prod-010`) có `deleted = TRUE`. Products cũ cũng có `status = 'INACTIVE'` (deviation Plan 16-02 §2 — defensive).

**Sign-off:** ☐ PASS  ☐ FAIL — note: ___

---

## 2. D-31 Profile=prod negative test (SEED-04)

Verify V101 KHÔNG chạy khi profile=prod.

```bash
# 1. Reset DB hoàn toàn (XÓA dev data — chỉ chạy trong môi trường local)
docker compose down -v
docker compose up postgres -d
sleep 5

# 2. Start product-svc với SPRING_PROFILES_ACTIVE=prod (override profile mặc định dev)
SPRING_PROFILES_ACTIVE=prod docker compose up product-service -d
sleep 25

# 3. Query Flyway history — V101 KHÔNG được có
docker compose exec postgres psql -U tmdt -d tmdt -c \
  "SELECT version, script, success FROM product_svc.flyway_schema_history WHERE version IN ('100','101');"
```

**Expected:** 0 rows (cả V100 và V101 skip trong prod profile vì path `db/seed-dev/` không trong `flyway.locations` scan của profile prod — verified application.yml RESEARCH F1).

**Cleanup sau test:**

```bash
docker compose down -v
docker compose up -d  # về lại profile dev mặc định
```

**Sign-off:** ☐ PASS  ☐ FAIL — note: ___

---

## 3. Add-to-cart smoke (verify A2 inventory assumption — Pitfall 5)

Verify 1 SP mới có thể add-to-cart không cần inventory entries (D-06 defer assumption).

**Steps:**

1. Mở browser http://localhost:3000/products (anonymous OK hoặc login user demo)
2. Click product card đầu tiên (ví dụ `prod-pho-001` Apple iPhone 15 Pro Max 256GB)
3. Click button "Thêm vào giỏ"
4. Quan sát:
   - **PASS:** Toast/notification "Đã thêm vào giỏ" hoặc cart icon update count → A2 đúng, KHÔNG cần inventory seed
   - **FAIL:** Error "Không đủ hàng" / "Inventory not found" / 500 → A2 sai, CẦN tạo sub-plan inventory V3 seed

**Nếu FAIL → tạo Plan 16-04 sub-plan:** thêm `sources/backend/inventory-service/src/main/resources/db/seed-dev/V3__seed_catalog_inventory.sql` với 100 inventory rows tương ứng `prod-{pho|lap|mou|key|hea}-NNN` (mapping product_id từ V101).

**Sign-off:** ☐ PASS (A2 confirmed) ☐ FAIL (cần Plan 16-04) — note: ___

---

## 4. Automated E2E (Playwright)

```bash
cd sources/frontend
npx playwright test e2e/seed-catalog.spec.ts --reporter=list
# Expect: 7 passed (5 per-category + 1 image + 1 brand)
```

**Full regression (đảm bảo không break baseline):**

```bash
cd sources/frontend
npx playwright test --reporter=list
# Expect: full suite green (auth + admin-* + order-detail + password-change + smoke + seed-catalog)
```

**Sign-off:** ☐ PASS  ☐ FAIL — note: ___

---

## 5. UI walkthrough (visual check — D-27)

Mở http://localhost:3000/products, kiểm trực quan:

- [ ] 5 categories tech hiển thị trong sidebar Danh mục: Điện thoại, Laptop, Chuột, Bàn phím, Tai nghe
- [ ] KHÔNG còn category cũ (Thời trang / Gia dụng / Sách / Mỹ phẩm / Điện tử)
- [ ] Mỗi card có ảnh load (không placeholder), brand thực tế, giá VND format
- [ ] ~70% SP có giá gốc gạch ngang (original_price) — verify D-13
- [ ] FilterSidebar mở "Thương hiệu" panel → chỉ thấy brand domain-tech (Apple/Samsung/Logitech/Sony/Razer/ASUS/Dell/HP/Lenovo/Bose/Xiaomi/Keychron/OPPO/Vivo/Realme/Acer/MSI/SteelSeries/Microsoft/Corsair/Akko/Leopold/Sennheiser/JBL/Audio-Technica). KHÔNG có "MAC"/"Anessa"/"Cuckoo".
- [ ] Click 1 brand → grid filter còn đúng SP của brand đó
- [ ] Click category "Tai nghe" → grid render ≥20 cards thuộc cat-headphone

**Sign-off:** ☐ PASS  ☐ FAIL — note: ___

---

## Verification Verdict

- [ ] Section 1 (smoke SQL): PASS
- [ ] Section 2 (D-31 prod negative): PASS
- [ ] Section 3 (add-to-cart smoke): PASS — hoặc FAIL với Plan 16-04 created
- [ ] Section 4 (Playwright e2e): PASS — 7/7 + full regression green
- [ ] Section 5 (UI walkthrough): PASS

**Overall:** ☐ Phase 16 ACCEPTED  ☐ Phase 16 NEEDS REWORK

**Notes / Issues found:**

___

---

*Verification doc: 16-VERIFICATION.md — Phase 16 Plan 16-03 Task 3.2*
