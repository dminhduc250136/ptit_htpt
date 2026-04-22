# E-commerce Tech Store - Documentation Index (v2)

## Tóm tắt
Bộ tài liệu cho dự án TMĐT theo kiến trúc FE + API Gateway + 6 backend services: user, product, order, payment, inventory, notification.

## Context Links
- Machine-readable map: [_index.json](./_index.json)
- Project context: [PROJECT_CONTEXT.md](./PROJECT_CONTEXT.md)

## Cấu trúc tài liệu
```
.
├── README.md
├── _index.json
├── strategy/
│   ├── 00-business-overview.md
│   ├── 01-user-journeys.md
│   ├── 02-business-rules.md
│   └── services/
│       ├── user-business.md
│       ├── product-business.md
│       ├── order-business.md
│       ├── payment-business.md
│       ├── inventory-business.md
│       └── notification-business.md
├── ba/
│   ├── README.md
│   ├── uc-auth.md
│   ├── uc-user-profile.md
│   ├── uc-product-browse.md
│   ├── uc-product-review.md
│   ├── uc-cart.md
│   ├── uc-checkout-payment.md
│   ├── uc-order-tracking.md
│   ├── uc-admin-product.md
│   ├── uc-admin-order.md
│   └── uc-admin-user.md
├── architecture/
│   ├── 00-overview.md
│   ├── 01-tech-stack.md
│   ├── 02-sequence-diagrams.md
│   ├── 03-class-diagrams.md
│   ├── frontend.md
│   └── services/
│       ├── api-gateway.md
│       ├── user-service.md
│       ├── product-service.md
│       ├── order-service.md
│       ├── payment-service.md
│       ├── inventory-service.md
│       └── notification-service.md
└── technical-spec/
    └── ts-*.md
```

## Read Order cho AI
1. PROJECT_CONTEXT.md
2. README.md
3. _index.json
4. strategy -> ba -> architecture -> technical-spec theo UC.

## Decision Guide
| Task type | Đọc trước | Đọc tiếp |
|---|---|---|
| Implement feature theo UC | _index.json | ba/uc-*.md -> technical-spec/ts-*.md |
| Cập nhật business rule | strategy/02-business-rules.md | strategy/services/*.md |
| Cập nhật flow checkout/payment | ba/uc-checkout-payment.md | architecture/services/order-service.md, payment-service.md, inventory-service.md |
| Cập nhật auth/profile | ba/uc-auth.md, ba/uc-user-profile.md | architecture/services/user-service.md |
| Cập nhật notification | strategy/services/notification-business.md | architecture/services/notification-service.md |

## Service Ownership tóm tắt
| Service | Ownership chính |
|---|---|
| api-gateway | edge auth, route, rate-limit |
| user-service | auth/profile/address |
| product-service | catalog/category/review |
| order-service | cart/order lifecycle |
| payment-service | payment transaction + VNPay callback |
| inventory-service | stock ledger reserve/commit/release |
| notification-service | async notifications |

## Quy ước update docs
1. Khi đổi boundary service: update strategy/services + architecture/services + _index.json.
2. Khi đổi flow UC: update ba/uc-*.md và sequence tương ứng.
3. Khi thêm event: update _index.json events và service docs publish/consume.
4. Giữ docs ngắn gọn, ưu tiên thông tin phục vụ AI route task.
