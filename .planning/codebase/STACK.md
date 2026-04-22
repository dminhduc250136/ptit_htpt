# Technology Stack

**Analysis Date:** 2026-04-22

## Languages

**Primary:**
- Java 17 - All backend microservices
- TypeScript - Frontend with React/Next.js
- YAML - Configuration (Spring Boot application.yml)

**Secondary:**
- JavaScript - Next.js runtime

## Runtime

**Environment:**
- Java Runtime Environment (JRE) 17 (eclipse-temurin:17-jre)
- Node.js (Next.js 16.2.3)

**Package Managers:**
- Maven 3.9 - Java dependency and build management
- npm - Node.js package management for frontend

## Frameworks

**Backend:**
- Spring Boot 3.3.2 - Core application framework
- Spring Cloud 2023.0.3 - Microservices orchestration
- Spring Cloud Gateway - API Gateway pattern

**Frontend:**
- Next.js 16.2.3 - React metaframework with SSR
- React 19.2.4 - UI component library

**Build & Dev Tools:**
- Maven Maven 3.9 - Java build tool
- ESLint 9.x - JavaScript linting
- TypeScript 5.x - Type checking

## Microservices Architecture

**Services (All Spring Boot):**
- `api-gateway` - Spring Cloud Gateway (port 8080)
- `user-service` - User management (port 8081)
- `product-service` - Product catalog (port 8082)
- `order-service` - Order processing (port 8083)
- `payment-service` - Payment handling (port 8084)
- `inventory-service` - Inventory management (port 8085)
- `notification-service` - Notifications (port 8086)

**Location:** `sources/backend/`

## Key Dependencies

**Backend (Spring Boot Parent: 3.3.2):**
- `spring-boot-starter-web` - REST API support
- `spring-boot-starter-actuator` - Monitoring and health checks
- `spring-cloud-starter-gateway` - API Gateway routing (api-gateway only)

**Frontend (React Ecosystem):**
- `next` 16.2.3 - React framework
- `react` 19.2.4 - UI library
- `react-dom` 19.2.4 - React DOM rendering
- `@types/react` 19.x - TypeScript types
- `@types/react-dom` 19.x - DOM TypeScript types
- `@types/node` 20.x - Node.js TypeScript types
- `eslint-config-next` 16.2.3 - ESLint rules for Next.js

**Location:**
- Backend: `sources/backend/*/pom.xml`
- Frontend: `sources/frontend/package.json`

## Infrastructure

**Containerization:**
- Docker - Container runtime
- `Dockerfile` per service (multi-stage builds)
  - Build stage: Maven 3.9, eclipse-temurin:17
  - Runtime stage: eclipse-temurin:17-jre
- Location: `sources/backend/*/Dockerfile`

**Orchestration:**
- Docker Compose - Local development orchestration
- File: `docker-compose.yml`

**Build Process:**
- Maven: Clean compilation, offline dependency resolution, JAR packaging
- Frontend: Next.js build for production

## Configuration

**Backend Configuration:**
- Spring Boot YAML profiles: `application.yml`
- API Gateway routing config: Path-based routing with URL rewriting
- CORS settings: Enabled for `http://localhost:3000`
- Actuator endpoints: `/actuator/health`, `/actuator/info`
- Location: `sources/backend/*/src/main/resources/application.yml`

**Frontend Configuration:**
- Next.js config (implicit next.config.js)
- TypeScript: `sources/frontend/tsconfig.json`

## Platform Requirements

**Development:**
- Java 17 JDK
- Maven 3.9
- Node.js (for frontend dev)
- Docker & Docker Compose
- Port availability: 8080-8086 (backend), 3000 (frontend)

**Production:**
- Docker runtime
- JRE 17 for Java services
- Node.js runtime for Next.js (or static export)
- Deployment: Docker containers via Docker Compose or Kubernetes-ready

## Network Configuration

**Frontend:**
- Development: `npm run dev` on port 3000
- Build: `npm run build` + `npm start`
- CORS origin: Configured in API Gateway

**Backend Communication:**
- Internal: Docker DNS (service-name:8080)
- External: API Gateway on port 8080
- Health checks: Spring Boot Actuator endpoints

---

*Stack analysis: 2026-04-22*
