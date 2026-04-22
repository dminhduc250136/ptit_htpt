# UC-ORDER-TRACKING: Lịch sử, tracking, hủy đơn (v2)

## Tóm tắt
Customer theo dõi trạng thái đơn hàng, xem chi tiết timeline, hủy đơn trong cửa sổ hợp lệ. notification-service gửi thông báo khi trạng thái thay đổi.

## Context Links
- Strategy: [../strategy/services/order-business.md](../strategy/services/order-business.md)
- Architecture: [../architecture/services/order-service.md](../architecture/services/order-service.md)
- Technical Spec: [../technical-spec/ts-order-tracking.md](../technical-spec/ts-order-tracking.md)

## Actors
- Primary: Customer
- Secondary: API Gateway, Notification Service

## Preconditions
- User login hợp lệ.
- Order thuộc user hiện tại.

## Main Flows
### A. View order list
1. FE gọi GET orders qua Gateway.
2. order-service trả list theo user + filter state.
3. FE hiển thị badge trạng thái và tổng tiền.

### B. View order detail
1. FE gọi GET order detail.
2. order-service trả timeline state, payment summary, tracking info.
3. FE hiển thị progress và CTA phù hợp theo trạng thái.

### C. Cancel order
1. User click cancel ở trạng thái PENDING.
2. order-service validate ownership + transition.
3. order-service cập nhật state CANCELLED và phát OrderCancelled.
4. inventory-service release stock.
5. notification-service gửi email hủy đơn.

## Alternative/Exception Flows
- Order không thuộc user -> NOT_FOUND (ẩn thông tin).
- Trạng thái không cho cancel -> CANNOT_CANCEL_NOW.
- Đơn đã cancel -> ALREADY_CANCELLED.

## Service Touchpoints
| Concern | Service |
|---|---|
| Order timeline và transition | order-service |
| Stock release khi cancel | inventory-service |
| Notification trạng thái | notification-service |
| Route/auth | api-gateway |

## Business Rules
- BR-ORDER-01..05.
- BR-INVENTORY-04.
- BR-NOTI-02.

## Acceptance Criteria
- [ ] User chỉ thấy order của chính mình.
- [ ] Timeline hiển thị đúng thứ tự trạng thái.
- [ ] Cancel hợp lệ phát event và release stock.
- [ ] Email trạng thái gửi async.

## NFR
- GET orders p95 < 500ms.
- GET order detail p95 < 400ms.
- Authorization check bắt buộc ở backend.
