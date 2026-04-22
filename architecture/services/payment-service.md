# Payment Service (v2)

## Tóm tắt
Payment Service quản lý payment transaction và tích hợp cổng thanh toán VNPay (return/IPN).

## Runtime
- Service name: payment-service
- Port: 8080 (container), exposed 8084 ở compose

## Ownership
- Tạo payment session cho order pending.
- Build payment URL.
- Verify callback chữ ký.
- Payment state transitions và callback idempotency.

## Key APIs
- POST /payments/sessions
- GET /payments/{orderId}
- GET /payments/vnpay/return
- GET /payments/vnpay/ipn

## Publish Events
- PaymentInitiated
- PaymentSucceeded
- PaymentFailed
- PaymentExpired

## Consume Events
- OrderPlaced (nếu dùng payment session async).
- OrderCancelled (để đóng payment pending).

## Không ownership
- Order state machine business rules chi tiết.
- Stock reserve/commit/release.

## Notes for AI
- IPN là source of truth cho trạng thái thanh toán.
- Callback phải idempotent, không double-update.
