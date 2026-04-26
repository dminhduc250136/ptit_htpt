-- Phase 5 Plan 07: inventory-service V1 init schema.
-- Cross-service note: product_id references logical product_svc.products.id (KHÔNG có FK
-- cross-schema/cross-service — service layer validation only). V2 seed dùng prod-001..prod-010
-- khớp Plan 03 product seed.
CREATE TABLE inventory_svc.inventory_items (
  id VARCHAR(36) PRIMARY KEY,
  product_id VARCHAR(36) NOT NULL,
  quantity INT NOT NULL DEFAULT 0,
  reserved INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT uq_inventory_product UNIQUE (product_id)
);
