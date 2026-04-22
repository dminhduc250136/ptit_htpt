# Inventory Service (v2)

## Tóm tắt
Inventory Service là source of truth cho tồn kho khả dụng và tồn kho reserved theo order flow.

## Runtime
- Service name: inventory-service
- Port: 8080 (container), exposed 8085 ở compose

## Ownership
- Check availability.
- Reserve stock khi order placed.
- Commit stock khi payment success/confirm policy.
- Release stock khi fail/cancel/timeout.
- Low-stock detection và stock movement log.

## Key APIs
- POST /inventory/availability/check
- POST /inventory/reservations
- POST /inventory/reservations/{id}/commit
- POST /inventory/reservations/{id}/release
- PATCH /inventory/admin/items/{productId}/adjust

## Publish Events
- StockReserved
- StockReservationFailed
- StockCommitted
- StockReleased
- LowStockDetected

## Consume Events
- OrderPlaced
- PaymentSucceeded
- PaymentFailed
- OrderCancelled

## Không ownership
- Product metadata và pricing.
- Payment gateway integration.

## Notes for AI
- Mọi stock mutation phải idempotent theo order-item key.
