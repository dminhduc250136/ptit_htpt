# Architecture

**Analysis Date:** 2026-04-22

## Pattern Overview

**Overall:** Microservices architecture with API Gateway pattern

**Key Characteristics:**
- Distributed service-oriented architecture
- API Gateway as single entry point for all client requests
- Independent Spring Boot microservices for domain-specific business logic
- Service isolation with clear responsibility boundaries
- HTTP-based inter-service communication using DNS service discovery
- Frontend-to-backend communication through API Gateway only

## Layers

**API Gateway Layer:**
- Purpose: Route and filter all incoming HTTP requests to appropriate microservices
- Location: `sources/backend/api-gateway/`
- Contains: Spring Cloud Gateway routing configuration, CORS policies, request rewriting
- Depends on: All downstream microservices (user, product, order, payment, inventory, notification)
- Used by: Next.js frontend client

**Microservices Layer:**
- Purpose: Handle domain-specific business logic for independent service concerns
- Location: `sources/backend/{service-name}/`
- Contains: Spring Boot service implementations, REST controllers, business logic, domain models
- Depends on: External databases, other services (via HTTP), message queues
- Used by: API Gateway, other microservices

**Frontend Layer:**
- Purpose: User-facing e-commerce application with product browsing, cart, checkout, admin dashboard
- Location: `sources/frontend/`
- Contains: Next.js pages, React components, API service abstraction layer, mock data
- Depends on: API Gateway endpoints, uses mock data in development
- Used by: Web browsers

## Data Flow

**Request Flow:**

1. Browser → Next.js Frontend (port 3000)
2. Frontend calls API Gateway (via `http://api-gateway:8080`)
3. API Gateway routes by path prefix: `/api/users/**` → User Service, `/api/products/**` → Product Service, etc.
4. Individual microservices process requests and return responses
5. Responses flow back through API Gateway to frontend

**Service-to-Service Communication:**

- Services communicate via HTTP REST calls using DNS service names
- Example: Order Service calls Inventory Service at `http://inventory-service:8080/...`
- No message broker or event streaming detected in current configuration
- Synchronous request-response pattern

**State Management:**

- Each microservice manages its own data (database-per-service pattern implied)
- Frontend uses component-level state and mock data service abstraction
- No centralized state management layer (Redux, etc.) detected in frontend

## Key Abstractions

**API Gateway Service:**
- Purpose: Centralized request routing, CORS handling, path rewriting
- Examples: `sources/backend/api-gateway/src/main/resources/application.yml`
- Pattern: Spring Cloud Gateway with route predicates and filters

**Microservice Pattern:**
- Purpose: Encapsulate domain logic with independent deployment
- Examples: User Service, Product Service, Order Service, Payment Service, Inventory Service, Notification Service
- Pattern: Spring Boot + REST controllers + @SpringBootApplication

**Service Abstraction Layer (Frontend):**
- Purpose: Decouple UI components from data fetching implementation
- Examples: `sources/frontend/src/services/api.ts`
- Pattern: Single import point for all data operations, easily swappable between mock and real APIs

## Entry Points

**API Gateway:**
- Location: `sources/backend/api-gateway/src/main/java/com/ptit/htpt/apigateway/ApiGatewayApplication.java`
- Triggers: Application startup
- Responsibilities: Accept HTTP requests on port 8080, route to backend services, handle CORS

**Frontend Application:**
- Location: `sources/frontend/src/app/layout.tsx` (root layout) and `sources/frontend/src/app/page.tsx` (home page)
- Triggers: Browser navigation
- Responsibilities: Render pages, fetch data via API service, display UI components

**User Service Entry:**
- Location: `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/UserServiceApplication.java`
- Triggers: Service startup
- Responsibilities: User management endpoints, authentication/authorization

**Order Service Entry:**
- Location: `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/OrderServiceApplication.java`
- Triggers: Service startup
- Responsibilities: Order creation, order history, order management

## Error Handling

**Strategy:** Spring Boot standard exception handling with HTTP status codes

**Patterns:**
- Each microservice exposes `/health` actuator endpoint for health checks
- REST endpoint responses use standard HTTP status codes (200, 400, 404, 500)
- Spring Boot actuator exposed at management endpoints for monitoring

## Cross-Cutting Concerns

**CORS:** Handled centrally at API Gateway level with configuration for frontend origin (localhost:3000)

**Service Discovery:** Uses DNS-based service resolution (Docker container names as hostnames: `user-service`, `product-service`, etc.)

**Health Monitoring:** Spring Boot Actuator endpoints (`/health`, `/info`) available on each service for liveness checks

**Configuration Management:** YAML-based configuration per service in `src/main/resources/application.yml` for port, service name, and actuator settings

---

*Architecture analysis: 2026-04-22*
