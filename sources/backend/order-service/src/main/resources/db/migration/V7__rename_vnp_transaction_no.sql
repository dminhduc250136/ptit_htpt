-- order-svc V7 / PAY-04 (D-07): rename column dùng chung cho mọi gateway (VNPay → MoMo migration)
-- Quyết định: rename trực tiếp (Claude's Discretion §D-07) — DDL atomic, không mất dữ liệu.
-- Đơn legacy có paymentMethod='VNPAY' chấp nhận orphan (D-09).
ALTER TABLE orders RENAME COLUMN vnp_transaction_no TO payment_transaction_no;
