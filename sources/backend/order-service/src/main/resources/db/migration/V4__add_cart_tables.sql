-- Phase 18 / STORE-02 (D-01): Cart persistence schema.
-- carts: 1 row per user_id (UNIQUE). cart_items: per-product line, UNIQUE(cart_id, product_id) cho idempotent upsert.

CREATE TABLE IF NOT EXISTS order_svc.carts (
  id          VARCHAR(36)   PRIMARY KEY,
  user_id     VARCHAR(36)   NOT NULL UNIQUE,
  created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS order_svc.cart_items (
  id          VARCHAR(36)   PRIMARY KEY,
  cart_id     VARCHAR(36)   NOT NULL REFERENCES order_svc.carts(id) ON DELETE CASCADE,
  product_id  VARCHAR(36)   NOT NULL,
  quantity    INT           NOT NULL CHECK (quantity > 0),
  created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
  UNIQUE (cart_id, product_id)
);

CREATE INDEX IF NOT EXISTS idx_cart_items_cart_id ON order_svc.cart_items(cart_id);
