-- Seed bổ sung (profile=dev) — tạo 2 địa chỉ cố định cho user demo:
--   - CHK-02 (04-checkout): AddressPicker cần địa chỉ đã lưu để chọn.
--   - ADDR-01 (06-profile): /profile/addresses có địa chỉ để hiển thị.
--
-- user demo id = 00000000-0000-0000-0000-000000000002 (user_svc V100 seed).
-- Chỉ seed 2 địa chỉ (giới hạn hệ thống 10) — chừa chỗ cho ADDR-02/03 tạo thêm.
--
-- Idempotent: ON CONFLICT (id) DO NOTHING.

INSERT INTO addresses
  (id, user_id, full_name, phone, street, ward, district, city, is_default, created_at)
VALUES
  ('addr-demo-001', '00000000-0000-0000-0000-000000000002',
   'Nguyễn Văn Demo', '0901234567', '123 Đường Lê Lợi', 'Phường Bến Nghé',
   'Quận 1', 'TP. Hồ Chí Minh', TRUE,  NOW()),
  ('addr-demo-002', '00000000-0000-0000-0000-000000000002',
   'Nguyễn Văn Demo', '0907654321', '456 Đường Nguyễn Huệ', 'Phường Bến Thành',
   'Quận 1', 'TP. Hồ Chí Minh', FALSE, NOW())
ON CONFLICT (id) DO NOTHING;
