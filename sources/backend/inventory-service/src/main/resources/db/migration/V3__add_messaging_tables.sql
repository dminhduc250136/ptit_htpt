-- V3: Add messaging tables cho Phase 23 (D-06 idempotency + D-10 stock ledger).
-- Bảng processed_events đóng vai trò idempotency key store cho consumer OrderPlacedListener:
-- INSERT ... ON CONFLICT (event_id) DO NOTHING đảm bảo duplicate eventId chỉ trừ kho 1 lần.
-- Bảng stock_ledger ghi audit từng lần đổi quantity (per-item, per-eventId).

CREATE TABLE IF NOT EXISTS processed_events (
  event_id     VARCHAR(36)  PRIMARY KEY,
  event_type   VARCHAR(64)  NOT NULL,
  processed_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_processed_events_type ON processed_events(event_type);

CREATE TABLE IF NOT EXISTS stock_ledger (
  id              BIGSERIAL    PRIMARY KEY,
  event_id        VARCHAR(36)  NOT NULL,
  order_id        VARCHAR(36)  NOT NULL,
  product_id      VARCHAR(36)  NOT NULL,
  quantity_change INT          NOT NULL,
  reason          VARCHAR(32)  NOT NULL,
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_stock_ledger_order ON stock_ledger(order_id);
CREATE INDEX IF NOT EXISTS idx_stock_ledger_event ON stock_ledger(event_id);
