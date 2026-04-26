-- Phase 5 — order-service dev seed (Plan 05-05).
-- 2 demo orders gắn với demo_user (id = 00000000-0000-0000-0000-000000000002 — Plan 04 user_svc seed).
-- user_id literal PHẢI khớp Plan 04 V2 seed cho cross-service consistency
-- (Plan 05-08 Task 8.1 sẽ assert orphan-row count = 0).

INSERT INTO order_svc.orders (id, user_id, total, status, note, deleted, created_at, updated_at) VALUES
  ('ord-demo-001', '00000000-0000-0000-0000-000000000002', 8489000.00, 'DELIVERED', 'Đơn demo 1 — đã giao',  FALSE, NOW() - INTERVAL '7 days', NOW() - INTERVAL '5 days'),
  ('ord-demo-002', '00000000-0000-0000-0000-000000000002',  500000.00, 'PENDING',   'Đơn demo 2 — chờ xử lý', FALSE, NOW() - INTERVAL '1 day',  NOW() - INTERVAL '1 day');
