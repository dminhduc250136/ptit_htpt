# Order Service (v2)

## Tóm tắt
Order Service quản lý cart và order lifecycle, đóng vai trò orchestration domain cho checkout.

## Runtime
- Service name: order-service
- Port: 8080 (container), exposed 8083 ở compose

## Ownership
- Cart CRUD, merge cart.
- Create order từ checkout.
- State machine + order state log.
- Customer tracking APIs và admin transition APIs.

## Key APIs
- GET/POST/PATCH/DELETE /cart
- POST /checkout
- GET /orders, GET /orders/{id}
- POST /orders/{id}/cancel
- GET /admin/orders
- PATCH /admin/orders/{id}/state

## Publish Events
- OrderPlaced
- OrderStateChanged
- OrderConfirmed
- OrderShipped
- OrderDelivered
- OrderCancelled
- OrderRefunded

## Consume Events
- PaymentSucceeded/PaymentFailed (từ payment-service)
- StockReserved/StockReservationFailed/StockReleased/StockCommitted (từ inventory-service)

## Không ownership
- VNPay callback signature logic.
- Stock ledger mutation.

## Notes for AI
- Đừng implement payment callback ở order-service trong kiến trúc v2.
