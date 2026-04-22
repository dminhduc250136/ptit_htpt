# UC-CART: Quản lý giỏ hàng (v2)

## Tóm tắt
Customer quản lý giỏ hàng gồm add/update/remove/view. Domain owner là order-service; inventory-service dùng để kiểm tra khả dụng hàng, product-service cung cấp metadata hiển thị.

## Context Links
- Strategy: [../strategy/services/order-business.md](../strategy/services/order-business.md)
- Architecture: [../architecture/services/order-service.md](../architecture/services/order-service.md)
- Technical Spec: [../technical-spec/ts-cart.md](../technical-spec/ts-cart.md)

## Actors
- Primary: Customer
- Secondary: API Gateway, Inventory Service, Product Service

## Preconditions
- User đã login (MVP).
- Product ở trạng thái ACTIVE.

## Main Flows
### A. Add to cart
1. FE gửi add item qua Gateway.
2. order-service kiểm tra product status (product-service).
3. order-service kiểm tra available stock (inventory-service).
4. Nếu hợp lệ: thêm item hoặc tăng quantity.

### B. View cart
1. FE gọi get cart.
2. order-service trả items + price snapshot.
3. FE hiển thị cảnh báo nếu giá/stock thay đổi.

### C. Update quantity
1. FE gửi quantity mới.
2. order-service re-check availability với inventory-service.
3. Lưu quantity nếu hợp lệ.

### D. Remove/Clear
1. FE gọi remove item hoặc clear cart.
2. order-service cập nhật cart tương ứng.

## Alternative/Exception Flows
- Product inactive -> PRODUCT_UNAVAILABLE.
- available stock không đủ -> INSUFFICIENT_STOCK.
- Quantity vượt giới hạn policy -> QUANTITY_LIMIT_EXCEEDED.
- Cart rỗng -> trả empty state.

## Service Touchpoints
| Concern | Service |
|---|---|
| Cart ownership | order-service |
| Availability check | inventory-service |
| Product metadata | product-service |
| Edge auth/routing | api-gateway |

## Business Rules
- BR-PRICING-03.
- BR-INVENTORY-01, BR-INVENTORY-02.
- BR-GW-01.

## Acceptance Criteria
- [ ] Add cùng product tăng quantity thay vì tạo dòng mới.
- [ ] Mọi update quantity phải re-check availability.
- [ ] Cart API trả đủ data để FE hiển thị warning giá/stock.
- [ ] Empty state rõ ràng khi cart không có item.

## NFR
- GET cart p95 < 300ms.
- Add/update item p95 < 500ms.
- Data consistency: không tạo quantity âm hoặc vượt policy.
