-- Phase 8 / Plan 01 (D-01): Thêm column stock vào product_svc.products.
-- IF NOT EXISTS đảm bảo idempotent khi chạy lại trong test environments.
ALTER TABLE product_svc.products ADD COLUMN IF NOT EXISTS stock INT NOT NULL DEFAULT 0;

-- Seed stock = 50 cho tất cả products hiện có (10 products từ Phase 5 seed).
-- Products chưa có stock sẽ được cập nhật từ DEFAULT 0 lên 50.
UPDATE product_svc.products SET stock = 50 WHERE deleted = false;
