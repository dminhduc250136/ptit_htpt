# E-commerce Tech Store - Documentation Index (v2)

## Tóm tắt
Bộ tài liệu cho dự án TMĐT theo kiến trúc FE + API Gateway + 6 backend services: user, product, order, payment, inventory, notification.

## 🚀 Quick Start
1. 👉 **Hiểu rõ ownership**: [PROJECT_CONTEXT.md](./PROJECT_CONTEXT.md)
2. 📐 **Design architecture**: [architecture/00-overview.md](architecture/00-overview.md)
3. 🛠️ **Implement feature**: Chọn UC tương ứng trong [ba/](ba/) → [technical-spec/](technical-spec/)
4. 📊 **Business context**: [strategy/](strategy/) → KPI, vision, rules

## Cấu trúc tài liệu (Phân loại theo Service)
```
.
├── README.md (bạn đang xem)
├── _index.json (machine-readable map)
├── PROJECT_CONTEXT.md (entry point for AI)
├── strategy/ (business overview)
│   ├── 00-business-overview.md
│   ├── 01-user-journeys.md
│   ├── 02-business-rules.md
│   └── services/
├── architecture/ (technical design - source of truth)
│   ├── 00-overview.md
│   ├── 01-tech-stack.md
│   ├── 02-sequence-diagrams.md
│   ├── 03-class-diagrams.md
│   ├── frontend.md
│   └── services/ (api-gateway, *.md)
├── ba/ (Use Cases - phân loại theo service)
│   ├── README.md (ba overview)
│   ├── user-service/ (auth, profile, admin)
│   ├── product-service/ (browse, review, admin)
│   ├── order-service/ (cart, checkout, tracking, admin)
│   └── payment-service/ (shared with order)
└── technical-spec/ (Technical Specifications - phân loại theo service)
    ├── README.md (spec overview)
    ├── user-service/ (ts-auth, ts-profile, ts-admin)
    ├── product-service/ (ts-browse, ts-review, ts-admin)
    └── order-service/ (ts-cart, ts-checkout, ts-tracking, ts-admin)
```

## 📍 Navigation Guide (Cheat Sheet)

### Tôi muốn... thì đọc cái này
| Need | Đọc | Tiếp theo |
|---|---|---|
| 🔍 **Tìm service ownership** | [PROJECT_CONTEXT.md](./PROJECT_CONTEXT.md) | [architecture/00-overview.md](architecture/00-overview.md) |
| 💼 **Implement UC (feature)** | [ba/SERVICE/uc-*.md](ba/) | Technical spec tương ứng |
| 🏗️ **Design API endpoint** | [architecture/services/\*.md](architecture/services/) | [ba/SERVICE/](ba/) → code |
| 💰 **Checkout/Payment flow** | [ba/order-service/uc-checkout-payment.md](ba/order-service/) | [architecture/services/order-service.md](architecture/services/order-service.md) + payment-service.md |
| 🔐 **Auth/Profile** | [ba/user-service/](ba/user-service/) | [architecture/services/user-service.md](architecture/services/user-service.md) |
| 📢 **Notification event** | [strategy/services/notification-business.md](strategy/services/notification-business.md) | [architecture/services/notification-service.md](architecture/services/notification-service.md) |
| 📈 **Business rules** | [strategy/02-business-rules.md](strategy/02-business-rules.md) | [strategy/services/](strategy/services/) |
| 🛢️ **Stock/Inventory** | [ba/order-service/uc-checkout-payment.md](ba/order-service/uc-checkout-payment.md) (Section: Inventory) | [architecture/services/inventory-service.md](architecture/services/inventory-service.md) |

## Service Ownership Summary
| Service | UC Documents | Spec | Key Responsibility |
|---|---|---|---|
| **user-service** | [ba/user-service/](ba/user-service/) | [technical-spec/user-service/](technical-spec/user-service/) | auth, profile, address |
| **product-service** | [ba/product-service/](ba/product-service/) | [technical-spec/product-service/](technical-spec/product-service/) | catalog, category, review |
| **order-service** | [ba/order-service/](ba/order-service/) | [technical-spec/order-service/](technical-spec/order-service/) | cart, order lifecycle |
| **payment-service** | [ba/order-service/uc-checkout-payment.md](ba/order-service/uc-checkout-payment.md) | [technical-spec/order-service/ts-checkout-payment.md](technical-spec/order-service/) | payment transaction, VNPay |
| **inventory-service** | [ba/order-service/uc-checkout-payment.md](ba/order-service/uc-checkout-payment.md) | [technical-spec/order-service/ts-checkout-payment.md](technical-spec/order-service/) | stock reserve/commit |
| **notification-service** | N/A | N/A | async event notifications |

## 📋 Decision Guide (Phiên bản mở rộng)

### Scenario: Implement feature mới từ scratch
```
1. Xác định service owner → [PROJECT_CONTEXT.md](./PROJECT_CONTEXT.md) TABLE
2. Đọc UC → ba/SERVICE/uc-*.md
3. Kiểm tra sequence diagram → [architecture/02-sequence-diagrams.md](architecture/02-sequence-diagrams.md)
4. Thiết kế API → [architecture/services/SERVICE.md](architecture/services/)
5. Implement spec → [technical-spec/SERVICE/ts-*.md](technical-spec/)
6. Update docs nếu thay đổi boundary
```

### Scenario: Cập nhật existing flow
```
1. Tìm UC hiện tại → [ba/SERVICE/](ba/)
2. Kiểm tra business rule → [strategy/02-business-rules.md](strategy/02-business-rules.md)
3. Xem sequence diagram → [architecture/02-sequence-diagrams.md](architecture/02-sequence-diagrams.md)
4. Update API contract → [architecture/services/SERVICE.md](architecture/services/)
5. Update technical spec → [technical-spec/SERVICE/](technical-spec/)
6. Update sequence diagram nếu flow thay đổi
```

### Scenario: Cập nhật business rule
```
1. Update rule → [strategy/02-business-rules.md](strategy/02-business-rules.md)
2. Tìm affected service(s) → [strategy/services/](strategy/services/)
3. Update UC(s) → [ba/SERVICE/uc-*.md](ba/)
4. Update spec → [technical-spec/SERVICE/ts-*.md](technical-spec/)
5. Update sequence diagram nếu logic thay đổi
```

## 🔄 Documentation Update Protocol
Khi code thay đổi:
1. **Boundary thay đổi** → update [PROJECT_CONTEXT.md](./PROJECT_CONTEXT.md) + [architecture/services/](architecture/services/) + [_index.json](./_index.json)
2. **Flow UC thay đổi** → update [ba/SERVICE/uc-*.md](ba/) + [architecture/02-sequence-diagrams.md](architecture/02-sequence-diagrams.md)
3. **Event contract thay đổi** → update [_index.json](./_index.json) + service docs
4. **API contract thay đổi** → update [architecture/services/SERVICE.md](architecture/services/) + [technical-spec/SERVICE/ts-*.md](technical-spec/)

## Context Links
- 🤖 AI Context: [PROJECT_CONTEXT.md](./PROJECT_CONTEXT.md)
- 🗂️ Machine-readable: [_index.json](./_index.json)
- 🏗️ Architecture: [architecture/](architecture/)
- 📊 Business: [strategy/](strategy/)
- 📖 Use Cases: [ba/](ba/) (organized by service)
- 🔧 Specs: [technical-spec/](technical-spec/) (organized by service)
