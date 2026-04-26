-- Phase 5 Plan 07: inventory-service V2 seed (profile=dev only).
-- product_id PHẢI khớp `prod-001`..`prod-010` từ Plan 03 product_svc V2 seed
-- (RESEARCH Open Q #4 + Plan 07 cross-service IDs block). Plan 08 Task 8.1 sẽ assert
-- NOT EXISTS query trên cross-schema để verify orphan = 0 rows.
INSERT INTO inventory_svc.inventory_items (id, product_id, quantity, reserved, created_at, updated_at) VALUES
  ('inv-001', 'prod-001', 25, 0, NOW(), NOW()),
  ('inv-002', 'prod-002', 40, 0, NOW(), NOW()),
  ('inv-003', 'prod-003', 120, 0, NOW(), NOW()),
  ('inv-004', 'prod-004', 80, 0, NOW(), NOW()),
  ('inv-005', 'prod-005', 15, 0, NOW(), NOW()),
  ('inv-006', 'prod-006', 50, 0, NOW(), NOW()),
  ('inv-007', 'prod-007', 30, 0, NOW(), NOW()),
  ('inv-008', 'prod-008', 70, 0, NOW(), NOW()),
  ('inv-009', 'prod-009', 45, 0, NOW(), NOW()),
  ('inv-010', 'prod-010', 60, 0, NOW(), NOW());
