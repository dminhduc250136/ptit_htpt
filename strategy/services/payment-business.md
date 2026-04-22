# Payment Service - Business Strategy (v2)

## Tóm tắt
Payment Service chịu trách nhiệm toàn bộ giao dịch thanh toán online và callback đối tác, hiện tại tập trung vào VNPay và chuẩn bị mở rộng cổng thanh toán khác.

## Context Links
- Rules: [../02-business-rules.md](../02-business-rules.md)
- Architecture: [../../architecture/services/payment-service.md](../../architecture/services/payment-service.md)
- BA: [../../ba/uc-checkout-payment.md](../../ba/uc-checkout-payment.md), [../../ba/uc-admin-order.md](../../ba/uc-admin-order.md)

## Ownership
- Tạo payment session từ order pending.
- Build VNPay URL và xác thực callback.
- Quản lý payment transaction state.
- Phát event PaymentSucceeded/PaymentFailed.

## Không ownership
- Tạo/cập nhật order state business chi tiết.
- Stock reservation.

## Core policies
| Chủ đề | Policy |
|---|---|
| Source of truth | IPN là nguồn trạng thái thanh toán chính |
| Idempotency | Callback xử lý idempotent theo txnRef + responseCode |
| Amount | Theo spec VNPay, amount * 100 |
| Timeout | Paid flow timeout theo policy checkout |

## Event contracts
| Event | Consumer |
|---|---|
| PaymentInitiated | notification-service (optional), monitoring |
| PaymentSucceeded | order-service, inventory-service(optional), notification-service |
| PaymentFailed | order-service, inventory-service, notification-service |

## KPI gợi ý
- Payment success rate.
- Callback error rate.
- Callback latency p95.
- Duplicate callback handled rate.
