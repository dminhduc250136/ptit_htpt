-- Seed bổ sung (profile=dev) — tạo dữ liệu tiền đề cho bộ E2E test:
--   1. Giỏ hàng có sẵn item cho user demo  → module 03-cart, 04-checkout chạy được.
--   2. order_items cho 2 đơn demo có sẵn   → ORD-04 (xem chi tiết đơn) chạy được.
--
-- user demo id  = 00000000-0000-0000-0000-000000000002  (user_svc V100 seed)
-- 2 đơn có sẵn  = ord-demo-001 (DELIVERED), ord-demo-002 (PENDING)  (order_svc V100 seed)
-- product ids   = prod-pho-001 (iPhone 15 Pro Max), prod-lap-001 (MacBook Pro 16)
--                 — khớp catalog thật product_svc V101.
--
-- Idempotent: ON CONFLICT DO NOTHING để re-run không lỗi.

-- ── 1. Giỏ hàng cho user demo ──────────────────────────────────────
INSERT INTO carts (id, user_id, created_at, updated_at)
VALUES ('cart-demo-001', '00000000-0000-0000-0000-000000000002', NOW(), NOW())
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO cart_items (id, cart_id, product_id, quantity, created_at, updated_at)
VALUES
  ('citem-demo-001', 'cart-demo-001', 'prod-pho-001', 1, NOW(), NOW()),
  ('citem-demo-002', 'cart-demo-001', 'prod-lap-001', 2, NOW(), NOW())
ON CONFLICT (cart_id, product_id) DO NOTHING;

-- ── 2. order_items cho 2 đơn demo có sẵn ───────────────────────────
-- Đơn ord-demo-001 (DELIVERED, total 8.489.000) — dùng cho ORD-04 + reviews verified-buyer
INSERT INTO order_items (id, order_id, product_id, product_name, quantity, unit_price, line_total)
VALUES
  ('oitem-demo-001', 'ord-demo-001', 'prod-pho-001', 'Apple iPhone 15 Pro Max 256GB', 1, 34990000.00, 34990000.00),
  ('oitem-demo-002', 'ord-demo-002', 'prod-lap-001', 'Apple MacBook Pro 16 M3 Max 1TB', 1, 58990000.00, 58990000.00)
ON CONFLICT (id) DO NOTHING;
