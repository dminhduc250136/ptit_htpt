-- Phase 5 Plan 03: product_svc schema baseline.
-- Tables: categories, products. UUID String PK. Soft-delete column.

CREATE TABLE categories (
  id VARCHAR(36) PRIMARY KEY,
  name VARCHAR(200) NOT NULL,
  slug VARCHAR(220) NOT NULL,
  deleted BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT uq_categories_slug UNIQUE (slug)
);

CREATE TABLE products (
  id VARCHAR(36) PRIMARY KEY,
  name VARCHAR(300) NOT NULL,
  slug VARCHAR(320) NOT NULL,
  category_id VARCHAR(36) NOT NULL,
  price NUMERIC(12,2) NOT NULL,
  status VARCHAR(20) NOT NULL,
  deleted BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT uq_products_slug UNIQUE (slug),
  CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories(id)
);

CREATE INDEX idx_products_category_id ON products(category_id);
CREATE INDEX idx_products_status ON products(status) WHERE deleted = FALSE;
