# Codebase Structure

**Analysis Date:** 2026-04-22

## Directory Layout

```
sources/
├── backend/                             # All microservices and API gateway
│   ├── api-gateway/                     # API routing and request forwarding
│   ├── user-service/                    # User management microservice
│   ├── product-service/                 # Product catalog microservice
│   ├── order-service/                   # Order processing microservice
│   ├── payment-service/                 # Payment processing microservice
│   ├── inventory-service/               # Inventory management microservice
│   └── notification-service/            # Notification delivery microservice
└── frontend/                            # Next.js e-commerce web application
    ├── src/
    │   ├── app/                         # Next.js app router pages
    │   ├── components/                  # React component library
    │   ├── services/                    # API client abstraction
    │   ├── types/                       # TypeScript type definitions
    │   └── mock-data/                   # Mock data for development
    ├── public/                          # Static assets
    └── package.json                     # Frontend dependencies
```

## Directory Purposes

**sources/backend/:**
- Purpose: Contains all backend microservices built with Spring Boot
- Contains: Seven independent Maven projects (api-gateway, user-service, product-service, order-service, payment-service, inventory-service, notification-service)
- Key files: `pom.xml` (Maven build config for each service), `Dockerfile` (containerization)

**sources/backend/api-gateway/:**
- Purpose: Central API routing and gateway for all incoming requests
- Contains: Spring Cloud Gateway configuration, route definitions, CORS policies
- Key files: `src/main/resources/application.yml` (route definitions), `pom.xml`

**sources/backend/{service-name}/ (user-service, product-service, etc.):**
- Purpose: Domain-specific microservice implementation
- Contains: Spring Boot application class, controllers, services, domain models
- Key files: `src/main/java/com/ptit/htpt/{service-name}/` (Java code), `application.yml` (service configuration)

**sources/frontend/:**
- Purpose: Next.js e-commerce web application
- Contains: React pages, components, services, types, and mock data
- Key files: `package.json` (dependencies), `tsconfig.json` (TypeScript config), `next.config.ts`

**sources/frontend/src/app/:**
- Purpose: Next.js App Router pages and layouts
- Contains: Page components for all customer and admin routes
- Key files: `page.tsx` (home page), `layout.tsx` (root layout), `admin/layout.tsx` (admin layout), `[slug]/page.tsx` (dynamic routes)

**sources/frontend/src/components/:**
- Purpose: Reusable React component library
- Contains: UI components (Button, Input, Toast, Badge, ProductCard) and layout components (Header, Footer)
- Key files: `ui/{ComponentName}/{ComponentName}.tsx`

**sources/frontend/src/services/:**
- Purpose: API client abstraction layer
- Contains: `api.ts` - centralized data fetching functions
- Key files: `api.ts`

**sources/frontend/src/types/:**
- Purpose: Shared TypeScript type definitions
- Contains: `Product`, `Category`, `Order`, `User` interfaces
- Key files: `index.ts`

**sources/frontend/src/mock-data/:**
- Purpose: Mock data for development and testing
- Contains: Sample products, categories, orders, users
- Key files: `products.ts`, `orders.ts`

## Key File Locations

**Entry Points:**

- `sources/backend/api-gateway/src/main/java/com/ptit/htpt/apigateway/ApiGatewayApplication.java`: API Gateway startup
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/UserServiceApplication.java`: User Service startup
- `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/ProductServiceApplication.java`: Product Service startup
- `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/OrderServiceApplication.java`: Order Service startup
- `sources/backend/payment-service/src/main/java/com/ptit/htpt/paymentservice/PaymentServiceApplication.java`: Payment Service startup
- `sources/backend/inventory-service/src/main/java/com/ptit/htpt/inventoryservice/InventoryServiceApplication.java`: Inventory Service startup
- `sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/NotificationServiceApplication.java`: Notification Service startup
- `sources/frontend/src/app/page.tsx`: Frontend home page
- `sources/frontend/src/app/layout.tsx`: Frontend root layout

**Configuration:**

- Backend: `sources/backend/{service}/src/main/resources/application.yml` (Spring Boot configuration per service)
- Backend: `sources/backend/{service}/pom.xml` (Maven build configuration)
- Frontend: `sources/frontend/package.json` (NPM dependencies and scripts)
- Frontend: `sources/frontend/tsconfig.json` (TypeScript compiler options)
- Frontend: `sources/frontend/next.config.ts` (Next.js configuration)

**Core Logic:**

- Backend routing: `sources/backend/api-gateway/src/main/resources/application.yml` (gateway routes)
- Backend services: `sources/backend/{service}/src/main/java/com/ptit/htpt/{service}/` (business logic)
- Frontend API abstraction: `sources/frontend/src/services/api.ts`
- Frontend pages: `sources/frontend/src/app/**/*.tsx`
- Frontend components: `sources/frontend/src/components/ui/**/*.tsx`

**Testing:**

- Not yet implemented or tests located in `src/test/` directories (not observed in current structure)

## Naming Conventions

**Files:**

- Backend Java files: PascalCase (e.g., `UserServiceApplication.java`, `UserController.java`)
- Frontend React components: PascalCase with `.tsx` extension (e.g., `ProductCard.tsx`, `Header.tsx`)
- Frontend pages: lowercase with hyphens or [brackets] (e.g., `page.tsx`, `[slug]/page.tsx`)
- Configuration files: lowercase with extensions (e.g., `application.yml`, `package.json`)
- Mock data files: camelCase (e.g., `products.ts`, `orders.ts`)

**Directories:**

- Backend services: lowercase with hyphens (e.g., `user-service`, `product-service`, `api-gateway`)
- Frontend directories: lowercase (e.g., `app`, `components`, `services`, `types`, `mock-data`)
- Feature directories: lowercase (e.g., `ui/`, `layout/`, `admin/`)

**Package/Module Names:**

- Backend: `com.ptit.htpt.{service}` (e.g., `com.ptit.htpt.userservice`, `com.ptit.htpt.productservice`)
- Frontend: `@/` alias for `src/` (e.g., `@/components/ui/Button`, `@/services/api`)

## Where to Add New Code

**New Backend Microservice:**
- Create directory: `sources/backend/{new-service}/`
- Create Maven structure: `src/main/java/com/ptit/htpt/{newservice}/`
- Create entry point: `{NewService}ServiceApplication.java` with `@SpringBootApplication`
- Configuration: `src/main/resources/application.yml`
- Add route in: `sources/backend/api-gateway/src/main/resources/application.yml`

**New Frontend Feature/Page:**
- Create directory in: `sources/frontend/src/app/{feature}/`
- Create page component: `page.tsx`
- Add layout if needed: `layout.tsx`
- Import and use existing components from: `sources/frontend/src/components/`

**New React Component:**
- Create directory: `sources/frontend/src/components/{category}/{ComponentName}/`
- Create component file: `{ComponentName}.tsx`
- Export from barrel file: `sources/frontend/src/components/{category}/index.ts`

**New Utility Functions:**
- Add to: `sources/frontend/src/services/api.ts` (for API calls)
- Create new file in: `sources/frontend/src/services/` for specialized services

**Type Definitions:**
- Add to: `sources/frontend/src/types/index.ts`

## Special Directories

**sources/backend/{service}/Dockerfile:**
- Purpose: Container image definition for each microservice
- Generated: No (manually created)
- Committed: Yes

**sources/backend/{service}/target/:**
- Purpose: Maven build output directory
- Generated: Yes (by Maven during `mvn clean package`)
- Committed: No (in .gitignore)

**sources/frontend/.next/:**
- Purpose: Next.js build cache and output
- Generated: Yes (by `npm run build`)
- Committed: No (in .gitignore)

**sources/frontend/node_modules/:**
- Purpose: NPM package dependencies
- Generated: Yes (by `npm install`)
- Committed: No (in .gitignore)

**sources/backend/*/pom.xml:**
- Purpose: Maven configuration for dependency management and build
- Special note: Each service has independent pom.xml; no parent reactor pom found

---

*Structure analysis: 2026-04-22*
