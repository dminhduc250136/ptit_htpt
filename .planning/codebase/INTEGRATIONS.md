# External Integrations

**Analysis Date:** 2026-04-22

## Service-to-Service Communication

**Architecture Pattern:** Spring Cloud Gateway with HTTP routing

**API Gateway Integration:**
- Gateway Service: `api-gateway` (Spring Cloud Gateway)
- Port: 8080 (exposed to clients)
- Location: `sources/backend/api-gateway/src/main/resources/application.yml`

**Routing Configuration:**
```
/api/users/**       → user-service:8080
/api/products/**    → product-service:8080
/api/orders/**      → order-service:8080
/api/payments/**    → payment-service:8080
/api/inventory/**   → inventory-service:8080
/api/notifications/**  → notification-service:8080
```

**Routing Features:**
- Path-based predicates for request routing
- URL path rewriting (strips `/api/{service}` prefix before forwarding)
- Standardized internal port: 8080 per service
- Location: `sources/backend/api-gateway/pom.xml` (spring-cloud-starter-gateway)

## Client-to-Backend Communication

**Frontend to Backend:**
- Origin: `http://localhost:3000`
- CORS enabled in API Gateway
- Endpoints: All requests through `http://localhost:8080` gateway

**CORS Configuration:**
- Allowed origins: `http://localhost:3000`
- Allowed methods: GET, POST, PUT, PATCH, DELETE, OPTIONS
- Allowed headers: All (`*`)
- Credentials: Enabled
- Location: `sources/backend/api-gateway/src/main/resources/application.yml`

## Internal Service Discovery

**DNS-based Discovery:**
- Environment: Docker Compose
- Service names as hostnames: `user-service`, `product-service`, etc.
- Internal communication: HTTP via Docker DNS
- Port: 8080 (standardized across all services)

**Docker Compose Dependencies:**
- API Gateway depends on: all 6 microservices
- Startup order enforced via `depends_on`
- File: `docker-compose.yml`

## Data Storage

**Current Status:** Not yet configured
- No database dependencies in pom.xml files
- Spring Data JPA/Hibernate: Not present
- Database connections: Not configured in application.yml

**Expected Integration (when added):**
- Databases: TBD (likely MySQL/PostgreSQL)
- ORM: Spring Data JPA or similar
- Configuration: Application.yml database properties

## External APIs & Third-Party Services

**Currently Integrated:**
- None detected

**Expected Integration Points:**
- Payment processing (Payment Service)
- Email/SMS notifications (Notification Service)
- Identity/Authentication services (User Service)

## Message Queue / Event Bus

**Current Status:** Not configured
- Spring Cloud Stream: Not present
- Kafka: Not configured
- RabbitMQ: Not configured
- JMS: Not configured

**Future Consideration:**
- Order events (Order Service)
- Payment confirmations (Payment Service)
- Inventory updates (Inventory Service)
- Notification triggers (Notification Service)

## Monitoring & Observability

**Health Monitoring:**
- Spring Boot Actuator endpoints enabled
- Endpoints: `/actuator/health`, `/actuator/info`
- Configured in all services: `management.endpoints.web.exposure.include: health,info`
- Location: All `application.yml` files in `sources/backend/*/src/main/resources/`

**Logging:**
- Default: Spring Boot console logging
- Framework: SLF4J (implicit via Spring Boot Starters)
- No external logging service configured

## Authentication & Security

**Current Status:** Not yet implemented
- No Spring Security dependencies
- No JWT/OAuth2 configuration detected
- CORS is the only security feature configured

## Deployment & Container Orchestration

**Local Development:**
- Docker Compose orchestration
- File: `docker-compose.yml`
- Services: api-gateway + 6 backend services

**Build Pipeline:**
- Multi-stage Docker builds per service
- Build stage: Maven 3.9 + eclipse-temurin:17
- Runtime stage: eclipse-temurin:17-jre
- Locations: `sources/backend/*/Dockerfile`

**Container Networking:**
- Docker internal network for service-to-service communication
- Port mappings exposed via docker-compose.yml
- Gateway port 8080 published to host

## Environment Configuration

**Backend Services:**
- Application name: Spring application property
- Server port: 8080 (all services)
- Actuator endpoints: health, info
- Configuration files: `application.yml` per service

**Frontend:**
- CORS origin: hardcoded in gateway configuration (`http://localhost:3000`)
- Build target: Next.js production build
- Environment variables: Not visible in current configuration

## Service Dependencies Matrix

| Service | Depends On | Used By | Protocol |
|---------|-----------|---------|----------|
| api-gateway | All 6 services | Frontend | HTTP |
| user-service | None | api-gateway | HTTP |
| product-service | None | api-gateway | HTTP |
| order-service | None | api-gateway | HTTP |
| payment-service | None | api-gateway | HTTP |
| inventory-service | None | api-gateway | HTTP |
| notification-service | None | api-gateway | HTTP |
| frontend | api-gateway | Users | HTTP/REST |

---

*Integration audit: 2026-04-22*
