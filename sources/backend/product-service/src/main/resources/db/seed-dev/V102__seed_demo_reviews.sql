-- Seed bổ sung (profile=dev) — tạo review tiền đề cho bộ E2E test:
--   - REV-03/04/05 (07-reviews): cần review của user demo để sửa/xóa/sắp xếp.
--   - ADM-14 (08-admin):         cần review visible để admin ẩn/bỏ ẩn.
--   - SEED-CAT / ReviewSection:  PDP có review để hiển thị.
--
-- user demo id = 00000000-0000-0000-0000-000000000002 (user_svc V100 seed)
-- product      = prod-pho-001 (Apple iPhone 15 Pro Max) — khớp catalog V101.
--   User demo có đơn DELIVERED (ord-demo-001) chứa prod-pho-001 → là verified-buyer hợp lệ.
-- product prod-lap-001 nhận thêm 1 review từ admin để PDP laptop cũng có đánh giá.
--
-- Idempotent: ON CONFLICT DO NOTHING (partial unique index uq_review_product_user_active).

-- created_at để NOW() (vừa tạo) — review của user demo còn trong cửa sổ chỉnh sửa 24h
-- để test REV-03 (sửa review) chạy được. Review của admin để 2 ngày trước (chỉ cần hiển thị).
INSERT INTO reviews
  (id, product_id, user_id, reviewer_name, rating, content, hidden, deleted_at, created_at, updated_at)
VALUES
  ('rev-demo-001', 'prod-pho-001', '00000000-0000-0000-0000-000000000002',
   'demo_user', 5, 'Máy đẹp, chạy mượt, camera xuất sắc. Rất hài lòng với sản phẩm.',
   FALSE, NULL, NOW(), NOW()),
  ('rev-demo-002', 'prod-lap-001', '00000000-0000-0000-0000-000000000001',
   'admin', 4, 'MacBook hiệu năng mạnh, màn hình đẹp. Giá hơi cao nhưng xứng đáng.',
   FALSE, NULL, NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days')
ON CONFLICT (id) DO NOTHING;

-- Cập nhật thống kê rating cho 2 sản phẩm có review (avg_rating + review_count — V5 columns)
UPDATE products SET avg_rating = 5.0, review_count = 1 WHERE id = 'prod-pho-001';
UPDATE products SET avg_rating = 4.0, review_count = 1 WHERE id = 'prod-lap-001';
