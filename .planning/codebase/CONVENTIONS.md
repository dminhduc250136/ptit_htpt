# Coding Conventions

**Analysis Date:** 2026-04-22

## Project Structure

**Frontend:**
- `sources/frontend/` - Next.js React TypeScript application
- `sources/backend/` - Seven microservices (Java/Spring Boot)

## Frontend Naming Patterns

**Files:**
- React components: PascalCase (e.g., `Header.tsx`, `Button.tsx`, `ProductCard.tsx`)
- Utility/Service files: camelCase (e.g., `api.ts`, `products.ts`)
- Configuration files: lowercase with dots (e.g., `next.config.ts`, `tsconfig.json`, `eslint.config.mjs`)
- CSS modules: Same as component with `.module.css` extension (e.g., `Button.module.css`)
- Page routes: lowercase folders with `page.tsx` file following Next.js App Router convention

**Functions:**
- Component names: PascalCase (e.g., `export default function Header()`)
- Helper functions: camelCase (e.g., `getProducts()`, `delay()`)
- Hook functions: camelCase with `use` prefix (e.g., `useState`, `useMemo`, `useSearchParams`)
- Type/Interface constructors: PascalCase (e.g., `ButtonVariant`, `ProductFilter`)

**Variables:**
- Constants: camelCase (e.g., `selectedCategory`, `isMobileMenuOpen`, `searchQuery`)
- State variables: camelCase (e.g., `setIsMobileMenuOpen`, `setSearchQuery`)
- Loop/temporary: camelCase (e.g., `filtered`, `result`, `paged`)

**Types & Interfaces:**
- Type definitions: PascalCase (e.g., `ButtonProps`, `User`, `Product`, `Category`)
- Union types: PascalCase (e.g., `ButtonVariant = 'primary' | 'secondary' | 'danger'`)
- Discriminated unions: PascalCase (e.g., `ButtonAsButton`, `ButtonAsLink`)

## Frontend Code Style

**Formatting:**
- ESLint with `@eslint/js` and Next.js config
- No Prettier configuration found - relying on ESLint defaults
- Expected style: 2-space indentation (Next.js default)

**Linting:**
- Tool: ESLint 9.x
- Config: `eslint.config.mjs` (flat config format)
- Extends: `eslint-config-next/core-web-vitals` and `eslint-config-next/typescript`
- Ignore patterns: `.next/**`, `out/**`, `build/**`, `next-env.d.ts`

**TypeScript Configuration:**
- `strict: true` - Strict type checking enabled
- Target: ES2017
- Module resolution: bundler
- Path aliases: `@/*` → `./src/*`
- JSX: react-jsx

## Frontend Import Organization

**Order Observed:**
1. React core imports (e.g., `import React from 'react'`)
2. Next.js imports (e.g., `import Link from 'next/link'`)
3. Relative imports with @ alias (e.g., `import { mockProducts } from '@/mock-data/products'`)
4. CSS module imports last (e.g., `import styles from './Header.module.css'`)

**Path Aliases:**
- `@/` maps to `./src/` for clean imports across the application
- Examples: `@/components`, `@/services`, `@/types`, `@/mock-data`

## Frontend Directory Organization

```
src/
├── app/              # Next.js App Router pages
│   ├── [route]/      # Dynamic routes (e.g., /products/[slug])
│   └── page.tsx      # Route pages
├── components/       # Reusable React components
│   ├── layout/       # Layout components (Header, Footer)
│   └── ui/           # UI component library (Button, Input, Badge, Toast, ProductCard)
├── services/         # API service abstraction layer
│   └── api.ts        # API calls and data fetching
├── types/            # TypeScript interfaces and types
│   └── index.ts      # Centralized type definitions
└── mock-data/        # Mock data for development
    └── products.ts   # Product and category mock data
```

**Component Organization:**
- Components organized by purpose: `layout/` for page layouts, `ui/` for reusable widgets
- Each component in own directory with file of same name (e.g., `Button/Button.tsx`)
- CSS modules co-located with components (e.g., `Button/Button.module.css`)

## Frontend JSDoc/Documentation

**Type Documentation:**
- Interface comments use block comments with detailed explanations
- Exported types include section headers (e.g., `// ===== USER SERVICE =====`)
- Service functions include JSDoc blocks describing purpose and parameters
- Example: `/**\n * API Service Abstraction Layer\n * This module abstracts all data fetching...\n */`

**Code Comments:**
- Inline comments explain non-obvious logic (e.g., filter operations, sorting logic)
- Comments added above code blocks to explain purpose
- Example: `// Filter by category`, `// Pagination`

## Frontend React Patterns

**Client Components:**
- Use `'use client'` directive at top of files that use hooks
- Example: `'use client';` appears in interactive pages and components

**State Management:**
- Functional components with React Hooks
- useState for local state (e.g., filters, UI toggles)
- useMemo for optimized computations
- useSearchParams for URL query parameters

**Type Safety in Props:**
- Props defined as TypeScript types before component definition
- Discriminated unions for flexible component APIs (ButtonAsButton vs ButtonAsLink)
- Default props in function signature (e.g., `variant = 'primary'`)
- Optional chaining for properties (e.g., `filter?.categoryId`)

## Backend Naming Patterns

**Java Package Structure:**
- Convention: `com.ptit.htpt.{servicename}`
- Examples: `com.ptit.htpt.orderservice`, `com.ptit.htpt.userservice`, `com.ptit.htpt.apigateway`

**Application Classes:**
- Naming: `{ServiceName}Application` (e.g., `UserServiceApplication`, `OrderServiceApplication`)
- Located in service root package
- Spring Boot entry point with `@SpringBootApplication` annotation
- Minimal implementation: just `SpringApplication.run()` and optional PingController

**Class Naming:**
- Controllers: `{Domain}Controller` (implicit Spring convention, minimal implementation)
- Services: `{Domain}Service` (implicit Spring convention)
- Repositories: `{Domain}Repository` (implicit Spring convention)
- Models/Entities: Domain name (implicit Spring convention)

**Method Naming:**
- Request handlers: Standard REST method names (implicit Spring Web convention)
- Example health check: `ping()` returns service status string

## Backend Configuration

**Application Properties:**
- YAML format: `application.yml`
- Location: `src/main/resources/application.yml`
- Properties defined: `spring.application.name`, `server.port`, `management.endpoints`

**Environment-Specific Config:**
- No environment profiles found (e.g., `application-dev.yml`, `application-prod.yml`)
- Single unified configuration per service

## Build & Package Management

**Backend - Maven:**
- Build tool: Maven 3.9
- Parent: `spring-boot-starter-parent` version 3.3.2
- Java version: 17
- File: `pom.xml` in each service root
- Plugin: `spring-boot-maven-plugin` for packaging
- Docker build: Uses `maven:3.9-eclipse-temurin-17` image

**Backend - Dependencies:**
- Service dependencies declared via Maven (in pom.xml)
- All services inherit Spring Boot parent BOM
- Services include: `spring-boot-starter-web`, `spring-boot-starter-actuator`, `spring-cloud` (API Gateway)
- Test dependencies: Inherited from parent (JUnit 5, Mockito, spring-boot-test)

**Frontend - npm/Node:**
- Package manager: npm
- Node version: Inferred from Next.js 16.2.3 requirement (likely Node 18+)
- File: `package.json` with npm scripts
- Scripts: `dev`, `build`, `start`, `lint`

**Docker Build Pattern:**
- Multi-stage builds for backend services
- Build stage: Uses Maven to compile and package (skips tests with `-DskipTests`)
- Runtime stage: JRE-only image from `eclipse-temurin:17-jre`
- Port exposure: Standard port 8080 mapped in docker-compose.yml
- Entrypoint: `java -jar /app/app.jar`

## Documentation Standards

**No README in Backend:**
- Backend services lack service-specific documentation
- Only frontend has README.md (default Next.js template)

**Configuration Documentation:**
- Inline YAML comments in application.yml (minimal)
- Type definitions serve as API contract documentation in frontend (`types/index.ts`)

---

*Conventions analysis: 2026-04-22*
