# Codebase Concerns

**Analysis Date:** 2026-04-22

## Tech Debt

**Backend Services Lack Implementation:**
- Issue: All microservices contain only application entry points (`ApiGatewayApplication.java`, `UserServiceApplication.java`, etc.), with no actual service logic, controllers, repositories, or business implementation
- Files: `sources/backend/*/src/main/java/com/ptit/htpt/*/` - all services
- Impact: Services are non-functional shells; cannot process requests or fulfill their intended business purposes
- Fix approach: Implement controllers, service classes, repositories, and domain models for each microservice

**Frontend Entirely Mock Data:**
- Issue: All frontend pages directly import and render hardcoded mock data (`mockProducts`, `mockCategories`, `mockOrders`) instead of calling real APIs
- Files: `sources/frontend/src/app/*.tsx`, `sources/frontend/src/app/*/page.tsx`, `sources/frontend/src/components/*`
- Impact: No real data flows through the system; cannot validate API contracts or test actual business logic
- Fix approach: Replace mock data imports with API service calls; migrate mock data to backend APIs

**API Service Layer Incomplete:**
- Issue: `sources/frontend/src/services/api.ts` contains only mock data handlers and simulated delays, no actual HTTP clients or API endpoints
- Files: `sources/frontend/src/services/api.ts`
- Impact: Cannot connect to backend services; all operations fail immediately when mock data is removed
- Fix approach: Add fetch/axios HTTP clients and implement proper API endpoints for all services

## Missing Critical Infrastructure

**No Database Configuration:**
- Issue: No database drivers (JDBC, JPA, MongoDB client) configured in any `pom.xml`; no datasource, connection pooling, or ORM setup in `application.yml`
- Files: `sources/backend/*/pom.xml`, `sources/backend/*/src/main/resources/application.yml`
- Impact: Services cannot persist or retrieve data; no transactional guarantees; no schema management
- Fix approach: Add Spring Data JPA + PostgreSQL/MySQL driver; configure datasource in `application.yml`; define entity models and repositories

**No Authentication/Authorization:**
- Issue: No security framework (Spring Security, JWT, OAuth2) configured; frontend login page is mock only (`handleSubmit` shows `alert()`)
- Files: `sources/frontend/src/app/login/page.tsx`, `sources/backend/*/pom.xml`
- Impact: Any user can perform any action; no protected resources; no user identification; compliance violations
- Fix approach: Add Spring Security to gateway; implement JWT token generation and validation; create auth controllers in user-service

**No Input Validation Framework:**
- Issue: Frontend has basic form validation but no backend validation; no `@Valid`, `@NotNull`, `@Min` annotations observed
- Files: `sources/frontend/src/app/login/page.tsx`, `sources/frontend/src/app/checkout/page.tsx`
- Impact: Invalid/malicious data can reach business logic unchecked; no consistency guarantees across API consumers
- Fix approach: Add Bean Validation (Spring Validation) to all controllers; implement custom validators for domain constraints

**No Error Handling Strategy:**
- Issue: No global exception handler (ControllerAdvice), no standardized error response format, no error codes defined
- Files: All backend services
- Impact: Errors leak implementation details; inconsistent client error handling; poor debugging capability
- Fix approach: Implement `@RestControllerAdvice` with standard error response format; define error code catalog

**No Logging Framework:**
- Issue: No SLF4J, Log4j, or similar configured in pom.xml; no logging statements observed in source code
- Files: `sources/backend/*/pom.xml`
- Impact: Cannot debug production issues; no audit trail; no performance monitoring capability
- Fix approach: Add spring-boot-starter-logging; implement structured logging with correlation IDs

## Scalability Concerns

**Hardcoded Service Routing:**
- Issue: All service URLs hardcoded in `sources/backend/api-gateway/src/main/resources/application.yml` (e.g., `http://user-service:8080`)
- Files: `sources/backend/api-gateway/src/main/resources/application.yml`
- Impact: Cannot scale services horizontally; no load balancing; service discovery unavailable; Docker-only hostnames
- Fix approach: Integrate Spring Cloud Consul or Eureka for service discovery; externalize service URLs to environment variables

**Hardcoded CORS Configuration:**
- Issue: Allowed origin hardcoded to `http://localhost:3000` in API Gateway global CORS config
- Files: `sources/backend/api-gateway/src/main/resources/application.yml`
- Impact: Cannot deploy to production without code change; breaks on multiple frontend deployments; security risk with wildcard origins
- Fix approach: Move CORS origins to environment-specific property files or config server

**All Services on Port 8080:**
- Issue: Every microservice configured to listen on `port: 8080` in individual `application.yml` files
- Files: All `sources/backend/*/src/main/resources/application.yml`
- Impact: Port collision in containerized/local environments; cannot run multiple instances of same service
- Fix approach: Use dynamic port assignment or externalize ports via environment variables

**No Caching Layer:**
- Issue: No cache configuration (Spring Cache, Redis, Memcached) in any service
- Files: All backend services
- Impact: Database queries repeat for identical requests; slow product lookups; poor response times at scale
- Fix approach: Add Spring Cache abstraction; configure Redis for product and category caching; implement cache invalidation strategy

**No Connection Pooling Configuration:**
- Issue: No datasource/connection pool settings visible (no HikariCP, Apache DBCP configuration)
- Files: All `sources/backend/*/src/main/resources/application.yml`
- Impact: Database connection exhaustion under load; resource leaks; poor concurrent user support
- Fix approach: Add Spring Boot data source autoconfiguration with HikariCP pooling parameters

## Security Issues

**Checkout with Hardcoded User Data:**
- Issue: Checkout form auto-fills with hardcoded test data: name "Nguyễn Văn A", phone "0912 345 678", email "nguyenvana@email.com"
- Files: `sources/frontend/src/app/checkout/page.tsx`
- Impact: Exposes test credentials; demonstrates lack of authenticated user context; information leakage in code
- Fix approach: Fetch user data from authenticated session; remove all test data from production code

**CORS Allows All Headers:**
- Issue: `allowedHeaders: ["*"]` in API Gateway configuration
- Files: `sources/backend/api-gateway/src/main/resources/application.yml`
- Impact: Enables CSRF attacks; allows arbitrary header injection; bypasses security headers validation
- Fix approach: Whitelist specific headers (Content-Type, Authorization, X-Requested-With)

**No HTTPS Configuration:**
- Issue: No SSL/TLS configuration in any `application.yml`; frontend not enforcing secure cookies
- Files: All `sources/backend/*/src/main/resources/application.yml`, frontend
- Impact: Credentials transmitted in plaintext; man-in-the-middle vulnerabilities; fails security audit
- Fix approach: Configure server.ssl in application.yml; set secure flag on session cookies

**Payment Service Without Security:**
- Issue: Payment service in `sources/backend/payment-service/` has no encryption, tokenization, or PCI DSS compliance measures
- Files: `sources/backend/payment-service/`
- Impact: Credit card data exposure; regulatory compliance violations; liability in breach scenario
- Fix approach: Never store raw payment data; integrate PCI-compliant payment gateway (Stripe, PayPal); implement tokenization

**No Rate Limiting:**
- Issue: No rate limiting configuration in API Gateway or individual services
- Files: `sources/backend/api-gateway/`
- Impact: Vulnerability to brute force attacks, DDoS, credential stuffing, API abuse
- Fix approach: Add Spring Cloud Gateway rate limiting filter; implement token bucket algorithm with Redis

**Sensitive Test Data in Code:**
- Issue: Mock data includes realistic PII (email addresses, phone numbers); used in production code paths
- Files: `sources/frontend/src/mock-data/`, `sources/frontend/src/app/checkout/page.tsx`
- Impact: Sensitive data in version control; potential exposure in logs; demonstrates PII handling misunderstanding
- Fix approach: Remove all PII from mock data; use realistic but clearly fake data (test@example.com, etc.)

## Dependency Issues

**Spring Boot 3.3.2 with Spring Cloud 2023.0.3:**
- Issue: Potential version mismatch between Spring Boot and Spring Cloud; spring-cloud-dependencies not in all service pom.xml files
- Files: `sources/backend/api-gateway/pom.xml` includes Spring Cloud; others don't
- Impact: Inconsistent dependency resolution; potential classpath conflicts; missing transitive dependencies
- Fix approach: Use parent BOM with aligned versions; verify Spring Cloud dependencies across all services

**Missing Essential Dependencies:**
- Issue: No Validation, Logging, Persistence, or Security starter dependencies configured
- Files: All `sources/backend/*/pom.xml`
- Impact: Cannot build core functionality without manual dependency hunting; versions unmanaged; build fragility
- Fix approach: Add spring-boot-starter-data-jpa, spring-boot-starter-security, spring-boot-starter-validation, spring-boot-starter-logging

**Frontend Missing API Client:**
- Issue: No axios, fetch wrapper, or SWR configured; no type-safe API client generation
- Files: `sources/frontend/package.json`
- Impact: Manual fetch calls prone to errors; no automatic retry/caching; type safety lost with real APIs
- Fix approach: Add axios or TanStack Query; consider code generation (OpenAPI Generator) for type safety

**No Testing Frameworks:**
- Issue: No JUnit, Mockito, Jest, React Testing Library, or similar configured
- Files: All `pom.xml` and `package.json`
- Impact: Cannot write or run tests; no test coverage; breaking changes undetected; no regression protection
- Fix approach: Add spring-boot-starter-test, jest, @testing-library/react to respective projects

## Performance Concerns

**Hardcoded Network Delays:**
- Issue: API service simulates delays with `setTimeout`: `const delay = (ms: number = 500) => new Promise(...)`
- Files: `sources/frontend/src/services/api.ts`
- Impact: Users experience artificial slowness; masks real performance issues; habituation to poor UX
- Fix approach: Remove delays; implement real API calls; add loading states instead of artificial delay

**No Query Optimization Hints:**
- Issue: No pagination, filtering, or sorting implemented in backend; frontend does all filtering in-memory
- Files: `sources/frontend/src/services/api.ts` contains client-side filtering
- Impact: Scales poorly with large datasets; transfers entire dataset over network; high memory usage on frontend
- Fix approach: Implement pagination, filtering, sorting on backend; use query parameters to reduce payload

**Browser Alert for Success Messages:**
- Issue: Uses native `alert('Đăng nhập thành công! (Mock)')` for user feedback
- Files: `sources/frontend/src/app/login/page.tsx`, `sources/frontend/src/app/checkout/page.tsx`
- Impact: Blocks UI; poor UX; unprofessional appearance; not mobile-friendly
- Fix approach: Implement toast/snackbar component (Toast.tsx exists but unused); use Toast component for feedback

**No Image Optimization:**
- Issue: Frontend directly uses Unsplash URLs in `<img>` tags with `<!-- eslint-disable @next/next/no-img-element -->`
- Files: `sources/frontend/src/app/page.tsx`, product mock data
- Impact: No lazy loading, responsive sizing, or format optimization; large payloads; poor Core Web Vitals
- Fix approach: Use Next.js Image component with responsive sizes; implement WebP format with fallbacks

## Incomplete Features

**No Order Management:**
- Issue: Order service exists but has no implementation; checkout page shows hardcoded mock order completion
- Files: `sources/backend/order-service/`, `sources/frontend/src/app/checkout/page.tsx`
- Impact: Orders not persisted; no order history; no order status tracking; cannot fulfill orders
- Fix approach: Implement order entity, repository, and REST endpoints in order-service; integrate with inventory and payment services

**No Inventory Tracking:**
- Issue: Inventory service exists but lacks implementation; checkout doesn't check stock availability
- Files: `sources/backend/inventory-service/`
- Impact: Overselling possible; stock levels not accurate; no backorder or low-stock alerts
- Fix approach: Implement inventory entity with stock counts; add stock validation in order workflow

**No Notification System:**
- Issue: Notification service exists but has no implementation; no email, SMS, or push notifications sent
- Files: `sources/backend/notification-service/`
- Impact: Users don't receive order confirmations, status updates, or shipping notifications
- Fix approach: Integrate email provider (SendGrid, AWS SES); implement notification templates and event handlers

**No Inter-Service Communication:**
- Issue: Services are isolated; no message queue or API calls between services for order → inventory → payment workflow
- Files: All `sources/backend/*/pom.xml`
- Impact: Cannot orchestrate order fulfillment; data remains inconsistent; no transactional guarantees
- Fix approach: Add RabbitMQ/Apache Kafka; implement saga pattern for distributed transactions

**User Profile Features Missing:**
- Issue: `/profile/orders/[id]` route exists but displays hardcoded mock data
- Files: `sources/frontend/src/app/profile/orders/[id]/page.tsx`
- Impact: Cannot view actual order history or details; user context lost
- Fix approach: Connect profile pages to user-service and order-service APIs; implement authentication flow

**Admin Pages Non-Functional:**
- Issue: `/admin/products`, `/admin/orders`, `/admin/users` pages render mock data without real CRUD operations
- Files: `sources/frontend/src/app/admin/` pages
- Impact: No data management capability; admin workflows not implemented
- Fix approach: Implement admin service endpoints; connect pages to real CRUD operations with permission checks

## Architectural Issues

**No API Contract Documentation:**
- Issue: No OpenAPI/Swagger specification, no endpoint documentation, no request/response examples
- Files: All backend services
- Impact: Frontend and backend teams work in isolation; breaking changes go undetected; integration testing impossible
- Fix approach: Add springdoc-openapi-starter-webmvc-ui; generate OpenAPI specs from controllers

**Frontend-Backend API Mismatch:**
- Issue: Frontend expects certain API contracts that services cannot fulfill (no real endpoints exist)
- Files: `sources/frontend/src/services/api.ts` vs all `sources/backend/*/` services
- Impact: Integration fails immediately when mocks removed; type mismatches; field name inconsistencies
- Fix approach: Define OpenAPI specification first; implement backend to spec; generate frontend client code

**No Configuration Management:**
- Issue: All configuration hardcoded in `application.yml`; no environment-specific profiles (dev, staging, prod)
- Files: All `sources/backend/*/src/main/resources/application.yml`
- Impact: Cannot deploy to different environments; secrets exposed in code; configuration duplicated across environments
- Fix approach: Create application-{profile}.yml files; use Spring Cloud Config or environment variables for secrets

**Missing Service Mesh Patterns:**
- Issue: No circuit breaker, retry logic, timeout configuration, or fallback handling between services
- Files: All services
- Impact: Cascading failures; no resilience; poor failure handling; impossible to debug distributed failures
- Fix approach: Add Spring Cloud CircuitBreaker (Resilience4j); implement retry and timeout policies

## Maintenance Challenges

**No Centralized Logging/Monitoring:**
- Issue: No log aggregation (ELK, Splunk), no metrics collection (Prometheus), no tracing (Jaeger)
- Files: All services
- Impact: Cannot diagnose production issues; no performance visibility; must check each service independently
- Fix approach: Add spring-boot-starter-actuator; integrate Micrometer for metrics; add Sleuth for distributed tracing

**No Health Check Endpoints:**
- Issue: Only `health,info` exposed in actuator; no readiness/liveness probes for Kubernetes
- Files: All `sources/backend/*/src/main/resources/application.yml`
- Impact: Container orchestration cannot detect service health; failed services marked as running; cascading outages
- Fix approach: Expose `/actuator/health/liveness` and `/actuator/health/readiness`; add custom health indicators

**No Deployment Automation:**
- Issue: No CI/CD pipeline, no build scripts, no automated tests, no deployment playbooks
- Files: None (missing entirely)
- Impact: Manual deployments error-prone; no regression testing; cannot quickly rollback; releases take days
- Fix approach: Create GitHub Actions/GitLab CI pipeline; add containerization verification; implement automated testing gates

**No Database Migration Strategy:**
- Issue: No Flyway or Liquibase configuration; no version-controlled schema changes
- Files: Missing entirely
- Impact: Schema changes ad-hoc and manual; no rollback capability; environment drift; data loss risk
- Fix approach: Add spring-boot-starter-flyway; version schema changes in git; automate migrations

**No API Versioning Strategy:**
- Issue: No version in endpoints (`/api/v1/...`); no backward compatibility plan
- Files: API Gateway routes and would-be controllers
- Impact: Breaking changes force all clients to update; no gradual rollout capability
- Fix approach: Version all endpoints; maintain multiple API versions for transition period

**Code Organization Scattered:**
- Issue: Frontend code duplicates logic across pages (filtering, pagination); no shared utilities
- Files: Multiple `sources/frontend/src/app/*/page.tsx` files
- Impact: Bug fixes require changes in multiple places; inconsistent behavior; high maintenance cost
- Fix approach: Extract shared hooks for filtering/pagination; create utility functions module

## Recommendations for Improvement

**Priority 1 - Critical for Functionality:**
1. Implement database layer in all backend services
2. Remove mock data; implement real API endpoints
3. Add authentication and authorization
4. Implement inter-service communication pattern (message queue or service-to-service API)

**Priority 2 - Critical for Production Readiness:**
1. Add comprehensive error handling and logging
2. Implement input validation on backend
3. Add configuration management for environment-specific settings
4. Implement health checks and monitoring

**Priority 3 - Essential for Scalability:**
1. Add caching layer (Redis)
2. Implement database connection pooling
3. Add API rate limiting
4. Implement service discovery for dynamic routing

**Priority 4 - Quality and Maintainability:**
1. Add unit and integration tests
2. Document API contracts (OpenAPI/Swagger)
3. Set up CI/CD pipeline
4. Implement database migrations (Flyway/Liquibase)

---

*Concerns audit: 2026-04-22*
