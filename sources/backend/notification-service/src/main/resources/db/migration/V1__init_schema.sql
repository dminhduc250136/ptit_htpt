-- V1: Bootstrap notification-service persistence (Phase 23 — Plan 23-02)
-- Bảng dispatch_log (D-14) + processed_events (D-06).
-- Phase 24 (Database Per Service): notification-service có database Postgres riêng
-- → dùng schema `public` mặc định, KHÔNG tạo schema riêng.

-- dispatch_log: log mỗi lần consumer xử lý OrderPlaced (KHÔNG gửi SMTP thật, chỉ ghi log)
CREATE TABLE IF NOT EXISTS dispatch_log (
  id                  VARCHAR(36)  PRIMARY KEY,
  event_id            VARCHAR(36)  NOT NULL,
  recipient_user_id   VARCHAR(36)  NOT NULL,
  channel             VARCHAR(16)  NOT NULL,
  subject             VARCHAR(255) NOT NULL,
  body                TEXT         NOT NULL,
  status              VARCHAR(16)  NOT NULL,
  sent_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_dispatch_log_event ON dispatch_log(event_id);
CREATE INDEX IF NOT EXISTS idx_dispatch_log_user ON dispatch_log(recipient_user_id);

-- processed_events: idempotency table (D-06). PK event_id đảm bảo INSERT ... ON CONFLICT DO NOTHING atomic.
CREATE TABLE IF NOT EXISTS processed_events (
  event_id     VARCHAR(36)  PRIMARY KEY,
  event_type   VARCHAR(64)  NOT NULL,
  processed_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_processed_events_type ON processed_events(event_type);
