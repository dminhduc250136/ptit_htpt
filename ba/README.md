# BA Use Case Index

## Tóm tắt
Index của tất cả use case. Mỗi UC có ID format `UC-{SLUG}`, map 1-1 với technical spec `TS-{SLUG}`. Dùng bảng dưới để navigate nhanh.

## Context Links
- Machine map: [../_index.json](../_index.json)
- Strategy: [../strategy/00-business-overview.md](../strategy/00-business-overview.md)
- TS index: [../technical-spec/README.md](../technical-spec/README.md)

## Use Case Table

| UC-ID | Name | Actor | Services | BA File | TS File |
|---|---|---|---|---|---|
| UC-AUTH | Xác thực người dùng | Customer, Guest | user | [uc-auth.md](./uc-auth.md) | [ts-auth.md](../technical-spec/ts-auth.md) |
| UC-USER-PROFILE | Hồ sơ & Sổ địa chỉ | Customer | user | [uc-user-profile.md](./uc-user-profile.md) | [ts-user-profile.md](../technical-spec/ts-user-profile.md) |
| UC-PRODUCT-BROWSE | Duyệt/Tìm/Lọc/Chi tiết sản phẩm | Customer, Guest | product | [uc-product-browse.md](./uc-product-browse.md) | [ts-product-browse.md](../technical-spec/ts-product-browse.md) |
| UC-PRODUCT-REVIEW | Review & Rating | Customer | product, order | [uc-product-review.md](./uc-product-review.md) | [ts-product-review.md](../technical-spec/ts-product-review.md) |
| UC-CART | Giỏ hàng | Customer | order, product | [uc-cart.md](./uc-cart.md) | [ts-cart.md](../technical-spec/ts-cart.md) |
| UC-CHECKOUT-PAYMENT | Đặt hàng & Thanh toán | Customer | order, product, user | [uc-checkout-payment.md](./uc-checkout-payment.md) | [ts-checkout-payment.md](../technical-spec/ts-checkout-payment.md) |
| UC-ORDER-TRACKING | Lịch sử / Tracking / Hủy | Customer | order | [uc-order-tracking.md](./uc-order-tracking.md) | [ts-order-tracking.md](../technical-spec/ts-order-tracking.md) |
| UC-ADMIN-PRODUCT | Admin: Product + Stock + Category | Admin | product | [uc-admin-product.md](./uc-admin-product.md) | [ts-admin-product.md](../technical-spec/ts-admin-product.md) |
| UC-ADMIN-ORDER | Admin: Order management | Admin | order, product | [uc-admin-order.md](./uc-admin-order.md) | [ts-admin-order.md](../technical-spec/ts-admin-order.md) |
| UC-ADMIN-USER | Admin: User management | Admin | user | [uc-admin-user.md](./uc-admin-user.md) | [ts-admin-user.md](../technical-spec/ts-admin-user.md) |

## BA Template

Mỗi BA spec file tuân theo template:

```markdown
# UC-{ID}: {Tên use case}

## Tóm tắt
{1-3 câu}

## Context Links
- Strategy: [link]
- Technical Spec: [link]
- Architecture: [link]

## Actors
- Primary: ...
- Secondary: ...

## Preconditions
- ...

## Main Flow
1. ...

## Alternative Flows
### AF-1: {tên}
...

## Exception Flows
### EF-1: {tên}
...

## Business Rules
- BR-{ID}: ...

## Acceptance Criteria
- [ ] AC-1: ...

## Data Inputs/Outputs
...

## UI Notes
...

## Non-functional Requirements
...
```

## Priority / MRR (must/should/could)

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
