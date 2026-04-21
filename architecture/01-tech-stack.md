# Tech Stack (LLD)

## Tóm tắt
Stack: Java 21 + Spring Boot 3.x (BE), NextJS 14 + TypeScript (FE), PostgreSQL 16, Kafka 3.x, Redis 7, Docker Compose (dev) / Kubernetes (prod). Chọn theo tiêu chí: mature, active community, có hosting managed, team đã có kinh nghiệm.

## Context Links
- Overview: [00-overview.md](./00-overview.md)
- Services detail: [services/](./services/)
- Frontend: [frontend.md](./frontend.md)

## Backend Stack

| Component | Tech | Version | Rationale |
|---|---|---|---|
| Language | Java | 21 (LTS) | Records, pattern matching, virtual threads stable; team expertise |
| Framework | Spring Boot | 3.2.x | Industry standard, rich ecosystem, Spring Cloud integration |
| Build | Maven | 3.9.x | Stable, simple pom.xml; alternative Gradle OK |
| Persistence | Spring Data JPA + Hibernate | 6.x | Mature, abstraction trên JDBC, lazy loading |
| DB Migration | Flyway | 10.x | SQL-first, version control friendly |
| API docs | springdoc-openapi | 2.x | Auto-gen OpenAPI 3 từ annotations |
| Validation | Jakarta Validation | 3.x | Chuẩn JSR 380, integrate Spring `@Valid` |
| Security | Spring Security + jjwt | 6.x / 0.12.x | JWT handling, filter chain customizable |
| Messaging | Spring Kafka | 3.x | Producer/consumer abstraction, retry, DLQ built-in |
| Caching | Spring Data Redis + Lettuce | 3.x | Reactive capable, connection pool |
| Resilience | Resilience4j | 2.x | Circuit breaker, retry, rate limiter |
| Observability | Micrometer + OpenTelemetry | Latest | Prometheus metrics, tracing export |
| Logging | Logback + Logstash encoder | — | Structured JSON logs |
| Code quality | Spotless (Google Java Format) | — | Auto format, CI enforce |
| Test | JUnit 5 + Mockito + AssertJ | 5.x | Standard |
| Integration test | Testcontainers | 1.x | Real Postgres/Kafka trong test |

### Java 21 features áp dụng
- **Records**: DTOs (Request/Response classes)
- **Pattern matching**: switch expressions cho order state, event type
- **Sealed classes**: event hierarchy (`OrderEvent` sealed permit `OrderPlaced`, `OrderPaid`, ...)
- **Virtual threads**: async consumer, IO-bound tasks (`@Async` with `VirtualThreadPerTaskExecutor`)
- **Text blocks**: SQL queries, test fixtures

## Frontend Stack

| Component | Tech | Version | Rationale |
|---|---|---|---|
| Framework | NextJS | 14.x | App Router stable, Server Components, SSR/SSG mixed |
| Language | TypeScript | 5.3.x | Type safety, tooling |
| UI components | shadcn/ui + Radix | Latest | Copy-paste, full control, accessible |
| Styling | TailwindCSS | 3.4.x | Utility-first, small bundle |
| Data fetching | React Query (TanStack) | 5.x | Cache, revalidation, optimistic updates |
| State | Zustand | 4.x | Simple global state, no boilerplate |
| Form | React Hook Form + Zod | 7.x / 3.x | Perf, validation schema shared with BE |
| HTTP client | Fetch (native) với wrapper | — | Đơn giản, fit NextJS |
| Auth | NextAuth / custom JWT | 4.x | Custom (control full flow với BE JWT) |
| Icons | lucide-react | Latest | Consistent icon set |
| Testing | Jest + RTL + Playwright | Latest | Unit + Component + E2E |
| Linting | ESLint + Prettier | Latest | Code quality |

### NextJS patterns áp dụng
- **App Router**: `app/` directory, Server Components default
- **Server Actions**: cho form submit không-realtime (VD: contact form)
- **Route groups**: `(auth)/login`, `(shop)/product/[slug]`, `(admin)/admin/...`
- **Middleware**: `middleware.ts` check JWT cookie, redirect admin routes
- **Metadata API**: SEO cho product pages (`generateMetadata`)
- **Streaming SSR**: Suspense boundaries cho slow parts
- **Image optimization**: `next/image` với S3 + CloudFront remote pattern

## Database

| Component | Tech | Version |
|---|---|---|
| RDBMS | PostgreSQL | 16 |
| Schema per service | `user_db`, `product_db`, `order_db` |
| Migration | Flyway | V{version}__{desc}.sql |
| Connection pool | HikariCP | Bundled Spring Boot |

### Config
- `max_connections`: 100 per DB instance (dev), 200 (prod)
- Hikari pool: 10-20 per service
- Timezone: UTC (convert ở FE)
- Collation: `vi_VN.UTF-8` cho text search

### Extensions
- `pg_trgm`: fuzzy search cho product name
- `unaccent`: bỏ dấu tiếng Việt cho search
- `uuid-ossp`: gen UUID cho event ID

## Message Bus

| Component | Tech | Version |
|---|---|---|
| Broker | Apache Kafka | 3.6.x |
| Coordination | Zookeeper | 3.8.x (hoặc KRaft ở phase 2) |
| Schema Registry | Confluent (optional) | — |
| Serialization | JSON (MVP) → Avro (phase 2) | — |

### Topic config
- Replication factor: 3 (prod), 1 (dev)
- Partition: 3 per topic (default), scale khi cần
- Retention: 7 ngày (events), 30 ngày (audit log topics)

## Cache

| Component | Tech | Version |
|---|---|---|
| In-memory | Redis | 7.2 |
| Mode | Standalone (dev), Cluster 3 master 3 replica (prod) |

### Use cases
- Session (refresh token): `session:{userId}:{tokenId}` TTL 7d
- Product cache: `product:{id}` TTL 30m
- Category tree: `category:tree` TTL 1h
- Cart (backup): `cart:{userId}` TTL 30d
- Rate limit: `rate:{ip}:{endpoint}` TTL 1m
- Idempotency: `idem:{key}` TTL 24h

## Gateway

| Component | Tech |
|---|---|
| Gateway | Spring Cloud Gateway |
| Service discovery | Kubernetes DNS (prod) / static routes (dev) |
| Rate limit | Redis RateLimiter filter |
| JWT verify | Custom global filter |
| CORS | Spring Cloud Gateway CorsConfiguration |

## DevOps

| Component | Tech |
|---|---|
| Container | Docker (multi-stage build) |
| Orchestration (dev) | Docker Compose |
| Orchestration (prod) | Kubernetes (EKS/GKE/self-hosted) |
| Helm charts | One chart per service |
| CI/CD | GitHub Actions |
| Registry | Docker Hub / ECR |
| Secrets | K8s Secret (dev), Vault / AWS Secrets Manager (prod) |
| Monitoring | Prometheus + Grafana |
| Logging | Fluent Bit → Loki / ELK |
| Tracing | Jaeger / Tempo |
| Alerting | AlertManager → Slack/Email |

## Payment

- **VNPay**: Official PHP SDK not available for Java → tự implement theo spec (HMAC-SHA512 signature).
- VNPay sandbox: `https://sandbox.vnpayment.vn/paymentv2/vpcpay.html`
- Production: `https://vnpayment.vn/...` (apply contract)

## Email

- SendGrid / AWS SES (prod)
- Mailhog (dev — local SMTP catcher)
- Template engine: Thymeleaf (server-side render email)
- Use cases: welcome, password reset, order confirmation, shipment update

## Storage

- **S3 + CloudFront** (prod)
- **MinIO** (dev — S3-compatible local)
- Presigned URL cho upload (admin), public URL cho product images
- Lifecycle policy: archive sau 90 ngày (không áp dụng với images live)

## Rationale summary

**Why microservices?** — Dự đoán catalog sẽ grow nhanh (thêm review, recommendation, search riêng), order flow phức tạp (saga pattern), user profile đơn giản. Tách riêng giúp scale độc lập.

**Why Java/Spring?** — Team expertise, enterprise-grade, tối ưu cho long-running processes (order saga, scheduled jobs).

**Why NextJS?** — SEO critical cho product pages, App Router stable, Server Components tối ưu. Hydrate cho interactive pages (cart, checkout).

**Why Kafka thay vì RabbitMQ?** — Replay capability (audit, reconstruct state), high throughput, integrate analytics pipeline phase 2.

**Why Postgres?** — ACID cho order/payment critical, JSONB cho product specs flexible, FTS đủ cho MVP (thay ES phase 2 nếu catalog > 50k SKU).
