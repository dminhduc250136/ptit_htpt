-- Phase 20 / COUP-01 (D-01, D-02): Coupon system schema.
-- V5 vì V4 đã shipped Phase 18 (cart tables). KHÔNG dùng V3.

CREATE TABLE IF NOT EXISTS order_svc.coupons (
  id                 VARCHAR(36)   PRIMARY KEY,
  code               VARCHAR(64)   NOT NULL UNIQUE,
  type               VARCHAR(16)   NOT NULL CHECK (type IN ('PERCENT','FIXED')),
  value              NUMERIC(15,2) NOT NULL CHECK (value > 0),
  min_order_amount   NUMERIC(15,2) NOT NULL DEFAULT 0,
  max_total_uses     INT           NULL,
  used_count         INT           NOT NULL DEFAULT 0,
  expires_at         TIMESTAMPTZ   NULL,
  active             BOOLEAN       NOT NULL DEFAULT true,
  created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS order_svc.coupon_redemptions (
  id           VARCHAR(36)   PRIMARY KEY,
  coupon_id    VARCHAR(36)   NOT NULL REFERENCES order_svc.coupons(id) ON DELETE RESTRICT,
  user_id      VARCHAR(36)   NOT NULL,
  order_id     VARCHAR(36)   NOT NULL UNIQUE,
  redeemed_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
  UNIQUE (coupon_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_redemptions_coupon ON order_svc.coupon_redemptions(coupon_id);
CREATE INDEX IF NOT EXISTS idx_redemptions_user ON order_svc.coupon_redemptions(user_id);

ALTER TABLE order_svc.orders ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(15,2) NOT NULL DEFAULT 0;
ALTER TABLE order_svc.orders ADD COLUMN IF NOT EXISTS coupon_code VARCHAR(64) NULL;
