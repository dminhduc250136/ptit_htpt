-- order-svc V6 / PAY-04 (D-02): payment_status + vnp_transaction_no cho VNPay
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS vnp_transaction_no VARCHAR(50);

-- processed_events cho PaymentEventListener idempotency (pattern inventory-service)
CREATE TABLE IF NOT EXISTS processed_events (
  event_id     VARCHAR(36) PRIMARY KEY,
  event_type   VARCHAR(64) NOT NULL,
  processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
