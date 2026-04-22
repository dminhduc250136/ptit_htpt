# Notification Service - Business Strategy (v2)

## Tóm tắt
Notification Service gửi thông báo bất đồng bộ cho user/admin dựa trên domain events. Service này tối ưu độ tin cậy gửi hơn là tốc độ realtime tuyệt đối.

## Context Links
- Rules: [../02-business-rules.md](../02-business-rules.md)
- Architecture: [../../architecture/services/notification-service.md](../../architecture/services/notification-service.md)
- BA: [../../ba/uc-auth.md](../../ba/uc-auth.md), [../../ba/uc-checkout-payment.md](../../ba/uc-checkout-payment.md), [../../ba/uc-order-tracking.md](../../ba/uc-order-tracking.md)

## Ownership
- Consume domain events từ user/order/payment/inventory.
- Render template và gửi email/system message.
- Retry và DLQ handling.
- Theo dõi trạng thái gửi.

## Không ownership
- Quyết định business state của order/payment.
- Xử lý logic đồng bộ trên request path.

## Event milestones
- UserRegistered.
- OrderPlaced.
- PaymentSucceeded/PaymentFailed.
- OrderShipped.
- OrderDelivered.
- OrderCancelled.

## Reliability policies
| Chủ đề | Policy |
|---|---|
| Delivery mode | Async by default |
| Retry | 3 lần, exponential backoff |
| Dedupe | notificationId/idempotency key |
| Fallback | DLQ + manual replay |

## KPI gợi ý
- Delivery success rate.
- Time-to-send p95.
- DLQ ratio.
- Template error rate.
