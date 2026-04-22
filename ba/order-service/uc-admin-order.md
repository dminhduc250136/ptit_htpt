# UC-ADMIN-ORDER: Admin quản lý đơn hàng (v2)

## Tóm tắt
Admin thao tác vòng đời đơn: confirm, processing, shipped, delivered, cancel, refund. order-service là owner state machine; payment/inventory/notification phối hợp theo event.

## Context Links
- Strategy: [../strategy/services/order-business.md](../strategy/services/order-business.md)
- Architecture: [../architecture/services/order-service.md](../architecture/services/order-service.md)
- Technical Spec: [../technical-spec/ts-admin-order.md](../technical-spec/ts-admin-order.md)

## Actors
- Primary: Admin
- Secondary: Payment Service, Inventory Service, Notification Service, API Gateway

## Preconditions
- User role ADMIN.
- Admin gọi endpoint qua Gateway.

## Main Flows
### A. List và lọc đơn
1. FE gọi admin order list.
2. order-service trả dữ liệu theo filter state/date/payment.
3. FE hiển thị action theo từng trạng thái.

### B. State transitions
1. Admin chọn hành động hợp lệ theo state matrix.
2. order-service validate transition + lock concurrency.
3. order-service ghi order_state_log và phát OrderStateChanged.
4. notification-service gửi email tương ứng.

### C. Tương tác payment/inventory
1. Khi cần xác nhận paid status, admin xem payment summary từ payment-service (read model).
2. Với cancel/refund, order-service phát event để inventory release hoặc giữ nguyên theo policy.
3. payment-service ghi nhận refund transaction nếu có tích hợp hoàn tiền.

## Transition Matrix (rút gọn)
| From | To hợp lệ |
|---|---|
| PENDING | CONFIRMED, CANCELLED |
| PAID | CONFIRMED, CANCELLED |
| CONFIRMED | PROCESSING, CANCELLED |
| PROCESSING | SHIPPED |
| SHIPPED | DELIVERED |
| DELIVERED | REFUNDED, COMPLETED |

## Alternative/Exception Flows
- Transition không hợp lệ -> INVALID_STATE_TRANSITION.
- Cập nhật đồng thời -> ORDER_LOCKED/CONFLICT.
- Quá cửa sổ refund -> REFUND_WINDOW_EXPIRED.

## Service Touchpoints
| Concern | Service |
|---|---|
| State machine, audit log | order-service |
| Payment details/refund records | payment-service |
| Stock release/commit policy | inventory-service |
| Notification customer | notification-service |
| Admin auth at edge | api-gateway |

## Business Rules
- BR-ORDER-01..05.
- BR-PAYMENT-01..05.
- BR-INVENTORY-03..05.
- BR-NOTI-02.

## Acceptance Criteria
- [ ] Action buttons đúng theo state hiện tại.
- [ ] Mỗi transition có log + event.
- [ ] Concurrency race được chặn.
- [ ] Cancel/refund đúng policy inventory/payment.
- [ ] Customer nhận thông báo sau mỗi mốc chính.

## NFR
- Admin list p95 < 500ms.
- Transition API p95 < 400ms.
- Audit log đầy đủ actor và timestamp.
