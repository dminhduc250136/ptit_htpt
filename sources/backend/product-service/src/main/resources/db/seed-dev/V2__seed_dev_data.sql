-- Phase 5 Plan 03: dev profile seed.
-- 5 categories (cat-electronics .. cat-cosmetics) + 10 products (prod-001 .. prod-010).
-- Cross-service contract: prod-001..prod-010 dùng bởi Plan 07 inventory_svc.inventory_items
-- (Plan 08 verify orphan-row count = 0).

INSERT INTO product_svc.categories (id, name, slug, deleted, created_at, updated_at) VALUES
  ('cat-electronics', 'Điện tử',    'dien-tu',    FALSE, NOW(), NOW()),
  ('cat-fashion',     'Thời trang', 'thoi-trang', FALSE, NOW(), NOW()),
  ('cat-household',   'Gia dụng',   'gia-dung',   FALSE, NOW(), NOW()),
  ('cat-books',       'Sách',       'sach',       FALSE, NOW(), NOW()),
  ('cat-cosmetics',   'Mỹ phẩm',    'my-pham',    FALSE, NOW(), NOW());

INSERT INTO product_svc.products (id, name, slug, category_id, price, status, deleted, created_at, updated_at) VALUES
  ('prod-001', 'Tai nghe bluetooth Sony WH-1000XM5', 'tai-nghe-sony-wh-1000xm5', 'cat-electronics', 7990000.00, 'ACTIVE', FALSE, NOW(), NOW()),
  ('prod-002', 'Bàn phím cơ Keychron K2',            'ban-phim-co-keychron-k2',  'cat-electronics', 2490000.00, 'ACTIVE', FALSE, NOW(), NOW()),
  ('prod-003', 'Áo thun cotton basic',               'ao-thun-cotton-basic',     'cat-fashion',      199000.00, 'ACTIVE', FALSE, NOW(), NOW()),
  ('prod-004', 'Quần jean slim-fit',                 'quan-jean-slim-fit',       'cat-fashion',      549000.00, 'ACTIVE', FALSE, NOW(), NOW()),
  ('prod-005', 'Nồi cơm điện Cuckoo 1.8L',           'noi-com-dien-cuckoo-1-8l', 'cat-household',   3290000.00, 'ACTIVE', FALSE, NOW(), NOW()),
  ('prod-006', 'Bộ chăn ga gối cotton',              'bo-chan-ga-goi-cotton',    'cat-household',    890000.00, 'ACTIVE', FALSE, NOW(), NOW()),
  ('prod-007', 'Sách Clean Code - Robert C. Martin', 'sach-clean-code',          'cat-books',        320000.00, 'ACTIVE', FALSE, NOW(), NOW()),
  ('prod-008', 'Sách Atomic Habits - James Clear',   'sach-atomic-habits',       'cat-books',        180000.00, 'ACTIVE', FALSE, NOW(), NOW()),
  ('prod-009', 'Kem chống nắng Anessa SPF50',        'kem-chong-nang-anessa',    'cat-cosmetics',    489000.00, 'ACTIVE', FALSE, NOW(), NOW()),
  ('prod-010', 'Son môi MAC Ruby Woo',               'son-moi-mac-ruby-woo',     'cat-cosmetics',    690000.00, 'ACTIVE', FALSE, NOW(), NOW());
