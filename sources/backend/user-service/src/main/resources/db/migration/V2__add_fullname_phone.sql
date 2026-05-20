-- Phase 7 / Plan 03 (D-04): Thêm fullName + phone vào users.
ALTER TABLE users ADD COLUMN IF NOT EXISTS full_name VARCHAR(120);
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone VARCHAR(20);
