# API Gateway (v2)

## Tóm tắt
API Gateway là entrypoint duy nhất từ FE vào backend. Gateway chịu trách nhiệm route, CORS, edge security và rate limiting.

## Runtime
- Service name: api-gateway
- Default port: 8080
- Tech: Spring Cloud Gateway

## Responsibilities
- Route theo prefix: /api/users, /api/products, /api/orders, /api/payments, /api/inventory, /api/notifications.
- Rewrite path theo downstream contract.
- CORS cho FE origin.
- JWT verification cho protected routes (target design).
- Rate limit cho auth/payment callback endpoints (target design).

## Không responsibilities
- Không xử lý business logic domain.
- Không truy cập DB nghiệp vụ.

## Route Map (hiện trạng config)
| Path prefix | Downstream |
|---|---|
| /api/users/** | user-service |
| /api/products/** | product-service |
| /api/orders/** | order-service |
| /api/payments/** | payment-service |
| /api/inventory/** | inventory-service |
| /api/notifications/** | notification-service |

## Operations
- Health endpoint: actuator health/info.
- Correlation header propagation nên được bật cho tracing end-to-end.
