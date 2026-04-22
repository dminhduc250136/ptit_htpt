# BA Use Case Index (v2)

## Tóm tắt
Index use case cho kiến trúc FE + API Gateway + 6 backend services. Bảng này là điểm vào nhanh để AI map UC -> service -> tài liệu kỹ thuật.

## Context Links
- Machine map: [../_index.json](../_index.json)
- Strategy: [../strategy/00-business-overview.md](../strategy/00-business-overview.md)
- Architecture: [../architecture/00-overview.md](../architecture/00-overview.md)
- TS index: [../technical-spec/README.md](../technical-spec/README.md)

## Use Case Table
| UC-ID | Name | Actor | Services chính | BA File | TS File |
|---|---|---|---|---|---|
| UC-AUTH | Xác thực người dùng | Guest, Customer | user, notification | [uc-auth.md](./uc-auth.md) | [ts-auth.md](../technical-spec/ts-auth.md) |
| UC-USER-PROFILE | Hồ sơ và sổ địa chỉ | Customer | user | [uc-user-profile.md](./uc-user-profile.md) | [ts-user-profile.md](../technical-spec/ts-user-profile.md) |
| UC-PRODUCT-BROWSE | Duyệt/tìm/lọc sản phẩm | Guest, Customer | product, inventory | [uc-product-browse.md](./uc-product-browse.md) | [ts-product-browse.md](../technical-spec/ts-product-browse.md) |
| UC-PRODUCT-REVIEW | Review và rating | Customer | product, order | [uc-product-review.md](./uc-product-review.md) | [ts-product-review.md](../technical-spec/ts-product-review.md) |
| UC-CART | Quản lý giỏ hàng | Customer | order, inventory, product | [uc-cart.md](./uc-cart.md) | [ts-cart.md](../technical-spec/ts-cart.md) |
| UC-CHECKOUT-PAYMENT | Đặt hàng và thanh toán | Customer | order, payment, inventory, user, notification | [uc-checkout-payment.md](./uc-checkout-payment.md) | [ts-checkout-payment.md](../technical-spec/ts-checkout-payment.md) |
| UC-ORDER-TRACKING | Lịch sử, tracking, hủy | Customer | order, payment, notification | [uc-order-tracking.md](./uc-order-tracking.md) | [ts-order-tracking.md](../technical-spec/ts-order-tracking.md) |
| UC-ADMIN-PRODUCT | Admin quản lý catalog | Admin | product, inventory | [uc-admin-product.md](./uc-admin-product.md) | [ts-admin-product.md](../technical-spec/ts-admin-product.md) |
| UC-ADMIN-ORDER | Admin quản lý đơn | Admin | order, payment, inventory, notification | [uc-admin-order.md](./uc-admin-order.md) | [ts-admin-order.md](../technical-spec/ts-admin-order.md) |
| UC-ADMIN-USER | Admin quản lý user | Admin | user, notification | [uc-admin-user.md](./uc-admin-user.md) | [ts-admin-user.md](../technical-spec/ts-admin-user.md) |

## Priority
| UC-ID | Priority | Phase |
|---|---|---|
| UC-AUTH | Must | MVP |
| UC-PRODUCT-BROWSE | Must | MVP |
| UC-CART | Must | MVP |
| UC-CHECKOUT-PAYMENT | Must | MVP |
| UC-ORDER-TRACKING | Must | MVP |
| UC-ADMIN-PRODUCT | Must | MVP |
| UC-ADMIN-ORDER | Must | MVP |
| UC-USER-PROFILE | Should | MVP |
| UC-ADMIN-USER | Should | MVP |
| UC-PRODUCT-REVIEW | Should | v1.1 |

## Rule of use cho AI
1. Đọc UC theo ID từ _index trước.
2. Mỗi UC phải kiểm tra service touchpoints trước khi code.
3. Nếu endpoint chưa có trong code, đánh dấu planned contract.
