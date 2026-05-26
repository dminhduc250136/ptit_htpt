-- Phase 7 / Plan 02 (D-03): Mo rong products voi 4 fields nullable.
-- Dung IF NOT EXISTS de idempotent khi chay lai (test environments).
ALTER TABLE products ADD COLUMN IF NOT EXISTS brand VARCHAR(200);
ALTER TABLE products ADD COLUMN IF NOT EXISTS thumbnail_url VARCHAR(500);
ALTER TABLE products ADD COLUMN IF NOT EXISTS short_description VARCHAR(500);
ALTER TABLE products ADD COLUMN IF NOT EXISTS original_price NUMERIC(12,2);
