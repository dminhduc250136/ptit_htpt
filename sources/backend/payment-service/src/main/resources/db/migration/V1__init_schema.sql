-- Phase 5 — payment-service initial schema
-- Cross-cutting note #4 (PATTERNS.md): GIỮ field cũ session_id, reference, message từ record cũ.
-- Phase 5 KHÔNG rename schema rộng; rename (nếu cần) defer Phase 8.

CREATE TABLE payment_svc.payments (
  id VARCHAR(36) PRIMARY KEY,
  session_id VARCHAR(120),
  reference VARCHAR(120),
  message VARCHAR(500),
  amount NUMERIC(12, 2),
  method VARCHAR(50),
  status VARCHAR(30) NOT NULL,
  deleted BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_payments_session_id ON payment_svc.payments(session_id) WHERE session_id IS NOT NULL;
CREATE INDEX idx_payments_reference ON payment_svc.payments(reference) WHERE reference IS NOT NULL;

-- payment_sessions: Phase 5 Rule 3 deviation — service layer quản lý cả PaymentSession + PaymentTransaction
-- (giống product-service có Product + Category). Không thể chỉ migrate 1 entity.
CREATE TABLE payment_svc.payment_sessions (
  id VARCHAR(36) PRIMARY KEY,
  order_id VARCHAR(36) NOT NULL,
  provider VARCHAR(50) NOT NULL,
  amount NUMERIC(12, 2) NOT NULL,
  status VARCHAR(30) NOT NULL,
  deleted BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_payment_sessions_order_id ON payment_svc.payment_sessions(order_id);
