-- V102 (seed-dev): D-13 — Copy stock từ product_svc.products sang inventory_svc.inventory_items.
-- Idempotent: chỉ insert nếu product_id chưa có trong inventory_items.
-- Note: bảng product_svc.products KHÔNG có cột deleted_at (verified V1..V7 product migrations),
-- nên KHÔNG filter soft-delete ở đây.

INSERT INTO inventory_svc.inventory_items (id, product_id, quantity, reserved, created_at, updated_at)
SELECT gen_random_uuid()::text, p.id, COALESCE(p.stock, 0), 0, NOW(), NOW()
FROM product_svc.products p
WHERE NOT EXISTS (
  SELECT 1 FROM inventory_svc.inventory_items i WHERE i.product_id = p.id
);
