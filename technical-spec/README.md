# Technical Spec Index (v2) - Organized by Service

## Tóm tắt
Index các technical spec (TS) map 1-1 với BA use cases. TS chứa API contracts, DB changes, event contracts, class design, implementation steps, test strategy — file AI đọc gần nhất khi code.

**Cấu trúc mới:** Specs được phân loại theo service để dễ mở rộng.

## Cấu trúc thư mục
```
technical-spec/
├── README.md (file này - overview)
├── user-service/
│   ├── ts-auth.md
│   ├── ts-user-profile.md
│   └── ts-admin-user.md
├── product-service/
│   ├── ts-product-browse.md
│   ├── ts-product-review.md
│   └── ts-admin-product.md
├── order-service/
│   ├── ts-cart.md
│   ├── ts-checkout-payment.md
│   ├── ts-order-tracking.md
│   └── ts-admin-order.md
└── payment-service/ (shared specs with order-service)
```

## Specs by Service

### 👤 user-service
| TS-ID | UC | File |
|---|---|---|
| TS-AUTH | UC-AUTH | [ts-auth.md](./user-service/ts-auth.md) |
| TS-USER-PROFILE | UC-USER-PROFILE | [ts-user-profile.md](./user-service/ts-user-profile.md) |
| TS-ADMIN-USER | UC-ADMIN-USER | [ts-admin-user.md](./user-service/ts-admin-user.md) |

### 📦 product-service
| TS-ID | UC | File |
|---|---|---|
| TS-PRODUCT-BROWSE | UC-PRODUCT-BROWSE | [ts-product-browse.md](./product-service/ts-product-browse.md) |
| TS-PRODUCT-REVIEW | UC-PRODUCT-REVIEW | [ts-product-review.md](./product-service/ts-product-review.md) |
| TS-ADMIN-PRODUCT | UC-ADMIN-PRODUCT | [ts-admin-product.md](./product-service/ts-admin-product.md) |

### 🛒 order-service
| TS-ID | UC | File |
|---|---|---|
| TS-CART | UC-CART | [ts-cart.md](./order-service/ts-cart.md) |
| TS-CHECKOUT-PAYMENT | UC-CHECKOUT-PAYMENT | [ts-checkout-payment.md](./order-service/ts-checkout-payment.md) |
| TS-ORDER-TRACKING | UC-ORDER-TRACKING | [ts-order-tracking.md](./order-service/ts-order-tracking.md) |
| TS-ADMIN-ORDER | UC-ADMIN-ORDER | [ts-admin-order.md](./order-service/ts-admin-order.md) |

## Complete Technical Spec Table
| TS-ID | UC | Services | BA File | TS File |
|---|---|---|---|---|
| TS-AUTH | UC-AUTH | user, notif | [↗](../ba/user-service/uc-auth.md) | [↗](./user-service/ts-auth.md) |
| TS-USER-PROFILE | UC-USER-PROFILE | user | [↗](../ba/user-service/uc-user-profile.md) | [↗](./user-service/ts-user-profile.md) |
| TS-ADMIN-USER | UC-ADMIN-USER | user, notif | [↗](../ba/user-service/uc-admin-user.md) | [↗](./user-service/ts-admin-user.md) |
| TS-PRODUCT-BROWSE | UC-PRODUCT-BROWSE | product, inv | [↗](../ba/product-service/uc-product-browse.md) | [↗](./product-service/ts-product-browse.md) |
| TS-PRODUCT-REVIEW | UC-PRODUCT-REVIEW | product, order | [↗](../ba/product-service/uc-product-review.md) | [↗](./product-service/ts-product-review.md) |
| TS-ADMIN-PRODUCT | UC-ADMIN-PRODUCT | product, inv | [↗](../ba/product-service/uc-admin-product.md) | [↗](./product-service/ts-admin-product.md) |
| TS-CART | UC-CART | order, product, inv | [↗](../ba/order-service/uc-cart.md) | [↗](./order-service/ts-cart.md) |
| TS-CHECKOUT-PAYMENT | UC-CHECKOUT-PAYMENT | order, pay, inv, user, notif | [↗](../ba/order-service/uc-checkout-payment.md) | [↗](./order-service/ts-checkout-payment.md) |
| TS-ORDER-TRACKING | UC-ORDER-TRACKING | order, pay, notif | [↗](../ba/order-service/uc-order-tracking.md) | [↗](./order-service/ts-order-tracking.md) |
| TS-ADMIN-ORDER | UC-ADMIN-ORDER | order, pay, inv, notif | [↗](../ba/order-service/uc-admin-order.md) | [↗](./order-service/ts-admin-order.md) |

## TS Template (cho file mới)

```markdown
# TS-{ID}: {Tên — match UC}

## Tóm tắt
{1-3 câu — services liên quan, endpoints chính}

## Context Links
- BA Spec: ../ba/SERVICE/uc-*.md
- Architecture: ../architecture/services/SERVICE.md
- Related TS: (nếu có)

## Services & Responsibilities
{Bảng service nào owns cái gì}

## API Contracts
### {Service} — {Endpoint}
- Method, path
- Request schema
- Response schema
- Error cases

## Database Changes
{Schema changes, indices, constraints}

## Event Contracts (Kafka)
{Topic, schema, when published}

## Implementation Steps
1. ...
2. ...

## Test Strategy
{Unit, integration, E2E}
```

## Context Links
- Main: [../README.md](../README.md)
- Project Context: [../PROJECT_CONTEXT.md](../PROJECT_CONTEXT.md)
- BA Index: [../ba/README.md](../ba/README.md)
- Architecture: [../architecture/00-overview.md](../architecture/00-overview.md)
- Machine Map: [../_index.json](../_index.json)

## Rule of use cho AI
1. Đọc TS theo service folder + ID từ ba/README.
2. TS là file đọc khi implement — chứa đủ detail API contract + DB + events.
3. Nếu TS chưa tồn tại → tạo file theo template, đặt trong service folder.
4. Cập nhật TS nếu API contract thay đổi.

## Sequence
(Mermaid)

## Class/Component Design
### Backend — {Service}
- Controller, Service, Repository, Entity

### Frontend
- Pages, Components, API client, State

## Implementation Steps
...

## Test Strategy
...

## Edge Cases & Gotchas
...
```

## Convention cho Request/Response schema

Viết **dưới dạng TypeScript-like / JSON schema** để dễ đọc:
- Fields với type, required/optional, constraint
- Error codes: list tất cả với HTTP status

## Convention cho implementation steps

Viết as checklist theo order. Mỗi step nên <30 phút execute. Ví dụ:
1. [ ] BE: Create migration V1__create_user_table.sql
2. [ ] BE: Create User entity + UserRepository
3. [ ] BE: Create UserService (register method)
4. [ ] BE: Create AuthController POST /register
5. [ ] BE: Unit test UserService
6. [ ] FE: Create api/auth.api.ts with register function
7. [ ] FE: Create /register page + RegisterForm component
8. [ ] FE: Integration test register flow
