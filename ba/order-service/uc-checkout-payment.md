# UC-CHECKOUT-PAYMENT: Đặt hàng và thanh toán (v2)

## Tóm tắt
Checkout được orchestration bởi order-service, thanh toán do payment-service xử lý, tồn kho do inventory-service xử lý. notification-service gửi email theo milestone.

## Context Links
- Strategy: [../strategy/services/order-business.md](../strategy/services/order-business.md)
- Architecture: [../architecture/services/order-service.md](../architecture/services/order-service.md)
- Technical Spec: [../technical-spec/ts-checkout-payment.md](../technical-spec/ts-checkout-payment.md)

## Actors
- Primary: Customer
- Secondary: API Gateway, Payment Service, Inventory Service, Notification Service

## Preconditions
- User đã login.
- Cart hợp lệ và không rỗng.
- Có địa chỉ giao hàng.

## Main Flows
### A. Checkout create order
1. FE gọi POST checkout qua Gateway, kèm Idempotency-Key.
2. order-service validate cart, address, pricing.
3. order-service tạo order trạng thái PENDING.
4. order-service phát OrderPlaced.
5. inventory-service consume OrderPlaced, thực hiện reserve.
6. inventory-service phát StockReserved hoặc StockReservationFailed.
7. Nếu reserve fail, order-service chuyển CANCELLED.

### B. VNPay flow
1. order-service yêu cầu payment-service tạo session VNPAY.
2. payment-service trả paymentUrl.
3. FE redirect sang VNPay.
4. VNPay callback return/IPN vào payment-service.
5. payment-service verify chữ ký, xử lý idempotent.
6. payment-service phát PaymentSucceeded hoặc PaymentFailed.
7. order-service consume payment result để cập nhật state.
8. inventory-service commit/release theo payment result.
9. notification-service gửi email kết quả.

### C. COD flow
1. order-service tạo order PENDING và reserve stock.
2. payment-service ghi nhận payment method COD (không có IPN).
3. Admin confirm đơn COD trong UC-ADMIN-ORDER.
4. inventory-service commit khi nhận tín hiệu confirm policy.

## Alternative/Exception Flows
- Idempotency key trùng -> trả order đã tạo.
- Reserve stock thất bại -> INSUFFICIENT_STOCK và cancel order pending.
- VNPay signature invalid -> payment failed event.
- Payment timeout -> PaymentExpired và release stock.
- Callback duplicate -> bỏ qua cập nhật lần 2.

## Service Touchpoints
| Concern | Service |
|---|---|
| Create order + state machine | order-service |
| Gateway redirect/callback verification | payment-service |
| Reserve/commit/release stock | inventory-service |
| Address ownership | user-service |
| Email milestone | notification-service |
| Route/auth/rate-limit | api-gateway |

## Business Rules
- BR-PRICING-01..05.
- BR-INVENTORY-01..05.
- BR-PAYMENT-01..05.
- BR-ORDER-01..05.

## Acceptance Criteria
- [ ] Checkout create order là idempotent.
- [ ] Payment callback là idempotent.
- [ ] Reserve stock bắt buộc trước khi paid flow tiếp tục.
- [ ] Payment fail luôn dẫn đến release stock.
- [ ] Notification gửi async, không chặn API.

## NFR
- POST checkout p95 < 1s.
- VNPay callback response p95 < 500ms.
- Data consistency không oversell.
