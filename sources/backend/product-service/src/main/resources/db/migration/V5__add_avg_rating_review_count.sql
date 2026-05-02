-- Phase 13 / Plan 03 (D-13): Thêm cached avg_rating + review_count vào products.
-- IF NOT EXISTS đảm bảo idempotent.

ALTER TABLE product_svc.products
  ADD COLUMN IF NOT EXISTS avg_rating   DECIMAL(3,1) DEFAULT 0,
  ADD COLUMN IF NOT EXISTS review_count INT          DEFAULT 0;
