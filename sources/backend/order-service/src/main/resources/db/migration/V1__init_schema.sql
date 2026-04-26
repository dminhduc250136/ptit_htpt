-- Phase 5 — order-service initial schema (Plan 05-05).
-- Cross-cutting note #3 (PATTERNS.md): GIỮ field `note` từ record OrderEntity cũ — nullable, FE có thể consume.
-- Phase 8 (PERSIST-02) sẽ extend: order_items table + shipping_address + payment_method. Phase 5 chỉ ship basic.
-- FK cross-schema (orders.user_id → user_svc.users.id) KHÔNG enforce — vi phạm microservice boundary;
-- consistency assert ở Plan 05-08 Task 8.1 (NOT EXISTS query).

CREATE TABLE order_svc.orders (
  id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL,
  total NUMERIC(12, 2) NOT NULL,
  status VARCHAR(30) NOT NULL,
  note VARCHAR(500),
  deleted BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_orders_user_id ON order_svc.orders(user_id);
