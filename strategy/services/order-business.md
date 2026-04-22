# Order Service - Business Strategy (v2)

## Tóm tắt
Order Service sở hữu cart và order lifecycle. Service này điều phối với payment-service và inventory-service thay vì xử lý trực tiếp payment hoặc stock mutation.

## Context Links
- Rules: [../02-business-rules.md](../02-business-rules.md)
- Architecture: [../../architecture/services/order-service.md](../../architecture/services/order-service.md)
- BA: [../../ba/uc-cart.md](../../ba/uc-cart.md), [../../ba/uc-checkout-payment.md](../../ba/uc-checkout-payment.md), [../../ba/uc-order-tracking.md](../../ba/uc-order-tracking.md), [../../ba/uc-admin-order.md](../../ba/uc-admin-order.md)

## Ownership
- Cart CRUD và merge cart.
- Tạo order từ checkout.
- Quản lý order state machine và audit trail.
- Admin transition: confirm/process/ship/deliver/cancel/refund.

## Không ownership
- Verify chữ ký VNPay/IPN.
- Reserve/commit/release stock.
- Gửi email trực tiếp.

## State machine
PENDING -> PAID -> CONFIRMED -> PROCESSING -> SHIPPED -> DELIVERED -> COMPLETED
Nhánh phụ: CANCELLED, REFUNDED theo policy.

## Orchestration boundaries
1. Checkout tạo order PENDING và phát OrderPlaced.
2. Payment status do payment-service phát PaymentSucceeded/Failed.
3. Inventory status do inventory-service phát StockReserved/Released/Committed.
4. Notification nhận event từ order/payment để gửi email.

## Event contracts
| Event | Ý nghĩa |
|---|---|
| OrderPlaced | Đơn mới tạo |
| OrderStateChanged | Mọi lần chuyển trạng thái |
| OrderCancelled | Hủy đơn |
| OrderDelivered | Giao thành công |

## KPI gợi ý
- Checkout success rate.
- Cancel rate.
- Time-to-ship.
- Transition error rate.
