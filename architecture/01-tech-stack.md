# Tech Stack (v2)

## Tóm tắt
Stack chuẩn cho kiến trúc FE + API Gateway + 6 backend services. Tài liệu này mô tả baseline kỹ thuật để AI chọn đúng library, pattern và integration style.

## Backend
| Layer | Tech |
|---|---|
| Runtime | Java 17, Spring Boot 3.3.x |
| Edge | Spring Cloud Gateway |
| API | Spring Web + Validation |
| Build | Maven |
| Messaging | Kafka |
| Cache/Rate limit | Redis |
| DB | PostgreSQL (DB per service) |
| Observability | Actuator + Micrometer |

## Frontend
| Layer | Tech |
|---|---|
| Framework | NextJS + TypeScript |
| Data fetching | React Query / fetch wrapper |
| Form validation | React Hook Form + Zod |
| State | Zustand |

## Service-by-service baseline
| Service | Trọng tâm stack |
|---|---|
| api-gateway | spring-cloud-starter-gateway, actuator |
| user-service | spring-web, security/auth modules, postgres |
| product-service | spring-web, search/filter, postgres |
| order-service | spring-web, transactional order state |
| payment-service | spring-web, VNPay adapter, idempotency store |
| inventory-service | spring-web, stock ledger transaction |
| notification-service | event consumer, email provider adapter |

## Data & Event conventions
- API prefix: /api/v1.
- Event envelope: eventId, eventType, occurredAt, producer, data.
- Topic naming: <service>.<entity>.<action>.
- Idempotency: bắt buộc cho checkout và payment callbacks.

## Security baseline
- JWT verification tại gateway.
- Service trust headers từ gateway cho user context.
- Rate limit tại gateway cho auth/payment endpoints.
- Secrets cấu hình qua env vars.

## Maturity status
- Hiện trạng code backend: skeleton service + gateway routing cơ bản.
- Tài liệu này mô tả target architecture để triển khai tiếp theo.
