-- Đồng bộ kiểu cột rating với JPA entity (int → INTEGER).
ALTER TABLE product_svc.reviews ALTER COLUMN rating TYPE INTEGER;
