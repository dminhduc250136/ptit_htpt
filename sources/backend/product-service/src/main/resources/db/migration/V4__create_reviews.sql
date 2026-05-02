-- Phase 13 / Plan 03 (D-11, D-13): Tạo bảng reviews + UNIQUE constraint chống duplicate review.
-- Naming follow V1__init_schema.sql convention: schema product_svc., VARCHAR(36) PK, CONSTRAINT uq_/fk_, idx_.

CREATE TABLE IF NOT EXISTS product_svc.reviews (
  id            VARCHAR(36)              PRIMARY KEY,
  product_id    VARCHAR(36)              NOT NULL,
  user_id       VARCHAR(36)              NOT NULL,
  reviewer_name VARCHAR(150)             NOT NULL,
  rating        SMALLINT                 NOT NULL CHECK (rating BETWEEN 1 AND 5),
  content       TEXT,
  created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT uq_review_product_user UNIQUE (product_id, user_id),
  CONSTRAINT fk_reviews_product FOREIGN KEY (product_id)
    REFERENCES product_svc.products(id)
);
CREATE INDEX IF NOT EXISTS idx_reviews_product_id ON product_svc.reviews(product_id);
