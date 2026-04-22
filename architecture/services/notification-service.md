# Notification Service (v2)

## Tóm tắt
Notification Service tiêu thụ domain events và gửi email/system notifications theo template.

## Runtime
- Service name: notification-service
- Port: 8080 (container), exposed 8086 ở compose

## Ownership
- Quản lý template notification.
- Consume event và dispatch message.
- Retry, DLQ, delivery log.

## Key APIs
- GET /notifications/templates (admin/internal)
- POST /notifications/test-send (admin/internal)
- GET /notifications/logs (admin/internal)

## Consume Events
- UserRegistered
- PasswordResetRequested
- OrderPlaced
- PaymentSucceeded/PaymentFailed
- OrderShipped
- OrderDelivered
- OrderCancelled

## Publish Events
- NotificationSent
- NotificationFailed (optional monitoring)

## Không ownership
- Không quyết định trạng thái đơn/thanh toán.
- Không block request path của checkout/auth.

## Notes for AI
- Notification là async side effect; lỗi gửi không rollback order/payment.
