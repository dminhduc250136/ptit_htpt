-- Phase 27 / Plan 27-02: thêm cột email_verified vào users (MAIL-02 D-04)
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT false;
