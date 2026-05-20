-- V1: Bootstrap notification-service persistence (Phase 23 — Plan 23-02)
-- Tạo schema notification_svc + bảng dispatch_log (D-14) + processed_events (D-06).
-- Idempotent (IF NOT EXISTS) để safe re-run trong môi trường dev có sẵn schema từ db/init.

CREATE SCHEMA IF NOT EXISTS notification_svc;

-- dispatch_log: log mỗi lần consumer xử lý OrderPlaced (KHÔNG gửi SMTP thật, chỉ ghi log)
CREATE TABLE IF NOT EXISTS notification_svc.dispatch_log (
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
CREATE INDEX IF NOT EXISTS idx_dispatch_log_event ON notification_svc.dispatch_log(event_id);
CREATE INDEX IF NOT EXISTS idx_dispatch_log_user ON notification_svc.dispatch_log(recipient_user_id);

-- processed_events: idempotency table (D-06). PK event_id đảm bảo INSERT ... ON CONFLICT DO NOTHING atomic.
CREATE TABLE IF NOT EXISTS notification_svc.processed_events (
  event_id     VARCHAR(36)  PRIMARY KEY,
  event_type   VARCHAR(64)  NOT NULL,
  processed_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_processed_events_type ON notification_svc.processed_events(event_type);
