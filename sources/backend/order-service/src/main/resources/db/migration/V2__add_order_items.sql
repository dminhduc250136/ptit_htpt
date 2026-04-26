-- Phase 8 / Plan 02 (D-06, D-08, D-09): Mở rộng order-service schema.
-- Tạo bảng order_items, thêm shipping_address + payment_method vào orders.

-- Bảng order_items: per-item breakdown (D-06)
CREATE TABLE IF NOT EXISTS order_svc.order_items (
  id            VARCHAR(36)    PRIMARY KEY,
  order_id      VARCHAR(36)    NOT NULL,
  product_id    VARCHAR(36)    NOT NULL,
  product_name  VARCHAR(300)   NOT NULL,
  quantity      INT            NOT NULL,
  unit_price    DECIMAL(12,2)  NOT NULL,
  line_total    DECIMAL(12,2)  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON order_svc.order_items(order_id);

-- Mở rộng orders table: shipping_address JSONB (D-08) + payment_method VARCHAR (D-09)
ALTER TABLE order_svc.orders ADD COLUMN IF NOT EXISTS shipping_address JSONB;
ALTER TABLE order_svc.orders ADD COLUMN IF NOT EXISTS payment_method VARCHAR(30);
