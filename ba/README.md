# BA Use Case Index (v2) - Organized by Service

## Tóm tắt
Index use case cho kiến trúc FE + API Gateway + 6 backend services. Bảng này là điểm vào nhanh để AI map UC -> service -> tài liệu kỹ thuật.

**Cấu trúc mới:** Use cases được phân loại theo service để dễ mở rộng.

## Cấu trúc thư mục
```
ba/
├── README.md (file này - overview)
├── user-service/
│   ├── uc-auth.md
│   ├── uc-user-profile.md
│   └── uc-admin-user.md
├── product-service/
│   ├── uc-product-browse.md
│   ├── uc-product-review.md
│   └── uc-admin-product.md
├── order-service/
│   ├── uc-cart.md
│   ├── uc-checkout-payment.md
│   ├── uc-order-tracking.md
│   └── uc-admin-order.md
└── payment-service/ (shared UCs with order-service)
```

## Use Case by Service

### 👤 user-service
| UC-ID | Name | Files |
|---|---|---|
| UC-AUTH | Xác thực người dùng | [uc-auth.md](./user-service/uc-auth.md) |
| UC-USER-PROFILE | Hồ sơ và sổ địa chỉ | [uc-user-profile.md](./user-service/uc-user-profile.md) |
| UC-ADMIN-USER | Admin quản lý user | [uc-admin-user.md](./user-service/uc-admin-user.md) |

### 📦 product-service
| UC-ID | Name | Files |
|---|---|---|
| UC-PRODUCT-BROWSE | Duyệt/tìm/lọc sản phẩm | [uc-product-browse.md](./product-service/uc-product-browse.md) |
| UC-PRODUCT-REVIEW | Review và rating | [uc-product-review.md](./product-service/uc-product-review.md) |
| UC-ADMIN-PRODUCT | Admin quản lý catalog | [uc-admin-product.md](./product-service/uc-admin-product.md) |

### 🛒 order-service
| UC-ID | Name | Files |
|---|---|---|
| UC-CART | Quản lý giỏ hàng | [uc-cart.md](./order-service/uc-cart.md) |
| UC-CHECKOUT-PAYMENT | Đặt hàng và thanh toán | [uc-checkout-payment.md](./order-service/uc-checkout-payment.md) |
| UC-ORDER-TRACKING | Lịch sử, tracking, hủy | [uc-order-tracking.md](./order-service/uc-order-tracking.md) |
| UC-ADMIN-ORDER | Admin quản lý đơn | [uc-admin-order.md](./order-service/uc-admin-order.md) |

### 💳 payment-service
> Chia sẻ UC với order-service
- [uc-checkout-payment.md](./order-service/uc-checkout-payment.md) — Payment section

## Complete Use Case Table
| UC-ID | Name | Actor | Services | BA File | TS File | Priority |
|---|---|---|---|---|---|---|
| UC-AUTH | Xác thực | Guest, Customer | user, notif | [↗](./user-service/uc-auth.md) | [↗](../technical-spec/user-service/ts-auth.md) | Must |
| UC-USER-PROFILE | Hồ sơ | Customer | user | [↗](./user-service/uc-user-profile.md) | [↗](../technical-spec/user-service/ts-user-profile.md) | Should |
| UC-ADMIN-USER | User admin | Admin | user, notif | [↗](./user-service/uc-admin-user.md) | [↗](../technical-spec/user-service/ts-admin-user.md) | Should |
| UC-PRODUCT-BROWSE | Browse/search | Guest, Customer | product, inv | [↗](./product-service/uc-product-browse.md) | [↗](../technical-spec/product-service/ts-product-browse.md) | Must |
| UC-PRODUCT-REVIEW | Review | Customer | product, order | [↗](./product-service/uc-product-review.md) | [↗](../technical-spec/product-service/ts-product-review.md) | Should |
| UC-ADMIN-PRODUCT | Product admin | Admin | product, inv | [↗](./product-service/uc-admin-product.md) | [↗](../technical-spec/product-service/ts-admin-product.md) | Must |
| UC-CART | Cart | Customer | order, inv, product | [↗](./order-service/uc-cart.md) | [↗](../technical-spec/order-service/ts-cart.md) | Must |
| UC-CHECKOUT-PAYMENT | Checkout | Customer | order, pay, inv, user, notif | [↗](./order-service/uc-checkout-payment.md) | [↗](../technical-spec/order-service/ts-checkout-payment.md) | Must |
| UC-ORDER-TRACKING | Tracking | Customer | order, pay, notif | [↗](./order-service/uc-order-tracking.md) | [↗](../technical-spec/order-service/ts-order-tracking.md) | Must |
| UC-ADMIN-ORDER | Order admin | Admin | order, pay, inv, notif | [↗](./order-service/uc-admin-order.md) | [↗](../technical-spec/order-service/ts-admin-order.md) | Must |

## Context Links
- Main: [../README.md](../README.md)
- Project Context: [../PROJECT_CONTEXT.md](../PROJECT_CONTEXT.md)
- Machine Map: [../_index.json](../_index.json)
- Strategy: [../strategy/00-business-overview.md](../strategy/00-business-overview.md)
- Architecture: [../architecture/00-overview.md](../architecture/00-overview.md)
- Technical Specs: [../technical-spec/README.md](../technical-spec/README.md)

## Rule of use cho AI
1. Đọc UC theo service folder + ID từ _index trước.
2. Mỗi UC phải kiểm tra service touchpoints trước khi code.
3. Nếu endpoint chưa có trong code, đánh dấu planned contract.
4. Khi thêm UC mới: tạo file trong service folder tương ứng.
