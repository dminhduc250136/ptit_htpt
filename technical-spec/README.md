# Technical Spec Index

## Tóm tắt
Index các technical spec (TS) map 1-1 với BA use cases. TS chứa API contracts, DB changes, event contracts, class design, implementation steps, test strategy — file AI đọc gần nhất khi code.

## Context Links
- BA index: [../ba/README.md](../ba/README.md)
- Architecture: [../architecture/00-overview.md](../architecture/00-overview.md)
- Machine map: [../_index.json](../_index.json)

## TS Table

| TS-ID | UC | Services | File |
|---|---|---|---|
| TS-AUTH | UC-AUTH | user | [ts-auth.md](./ts-auth.md) |
| TS-USER-PROFILE | UC-USER-PROFILE | user | [ts-user-profile.md](./ts-user-profile.md) |
| TS-PRODUCT-BROWSE | UC-PRODUCT-BROWSE | product | [ts-product-browse.md](./ts-product-browse.md) |
| TS-PRODUCT-REVIEW | UC-PRODUCT-REVIEW | product, order | [ts-product-review.md](./ts-product-review.md) |
| TS-CART | UC-CART | order, product | [ts-cart.md](./ts-cart.md) |
| TS-CHECKOUT-PAYMENT | UC-CHECKOUT-PAYMENT | order, product, user | [ts-checkout-payment.md](./ts-checkout-payment.md) |
| TS-ORDER-TRACKING | UC-ORDER-TRACKING | order | [ts-order-tracking.md](./ts-order-tracking.md) |
| TS-ADMIN-PRODUCT | UC-ADMIN-PRODUCT | product | [ts-admin-product.md](./ts-admin-product.md) |
| TS-ADMIN-ORDER | UC-ADMIN-ORDER | order, product | [ts-admin-order.md](./ts-admin-order.md) |
| TS-ADMIN-USER | UC-ADMIN-USER | user | [ts-admin-user.md](./ts-admin-user.md) |

## TS Template

```markdown
# TS-{ID}: {Tên — match UC}

## Tóm tắt
{1-3 câu — services liên quan, endpoints chính}

## Context Links
- BA Spec: [link]
- Services affected: ...

## Services & Responsibilities
...

## API Contracts
### {Service} — {Endpoint}
- Method, path, request schema, response schema, errors

## Database Changes
...

## Event Contracts (Kafka)
...

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
