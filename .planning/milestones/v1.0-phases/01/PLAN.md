# Phase 1 Plan — API Contract & Swagger Baseline

## Goal

Establish a **consistent, documented REST contract** across the API Gateway and all backend services, and make **Swagger/OpenAPI available everywhere** so the frontend can integrate reliably.

This phase satisfies **API-01..API-04** and Phase 1 success criteria from `.planning/ROADMAP.md`.

## Non-goals (explicitly out of scope for Phase 1)

- Implementing/finishing CRUD endpoint coverage (that is Phase 2).
- Full validation hardening (that is Phase 3). We only define the conventions and ensure error shapes are compatible.
- Production-grade security, auth, and rate limiting policies (student/MVP constraint).
- True OpenAPI aggregation (merging specs into one). We will provide **discoverable multi-service docs** without heavy custom code.

## Assumptions

- Services are Spring Boot **3.3.2**, Java **17**, Maven, and currently include `spring-boot-starter-web` + `actuator`.
- Gateway is Spring Cloud Gateway (WebFlux) with `spring-cloud-starter-gateway` + `actuator`.
- Gateway routes use **resource-based prefixes** and rewrite to service path (from `sources/backend/api-gateway/src/main/resources/application.yml`):
  - `user-service`: `/api/users/**`
  - `product-service`: `/api/products/**`
  - `order-service`: `/api/orders/**`
  - `payment-service`: `/api/payments/**`
  - `inventory-service`: `/api/inventory/**`
  - `notification-service`: `/api/notifications/**`
  - Gateway external port 8080, services internal 8080.
- No existing OpenAPI dependencies are present.
- Student/MVP constraint: prefer **configuration and small shared utilities** over complex platform code.

## Deliverables

- **Standard API contract** document in this plan:
  - Response envelope schema (success)
  - Error response schema (failure) with `fieldErrors[]`
  - Status code conventions for CRUD patterns
- **Swagger/OpenAPI enabled** in:
  - `user-service`, `product-service`, `order-service`, `payment-service`, `inventory-service`, `notification-service`
  - `api-gateway` (as the entry point for discoverable docs)
- **Gateway docs surface** that is realistic:
  - A gateway-hosted Swagger UI listing each service’s OpenAPI via gateway routes (no spec merging)
- **Consistency verification checklist** (curl + docs endpoints) runnable locally

---

## Design: Standard Response Envelope (Success)

### Envelope schema

All successful JSON responses from service controllers should follow this envelope:

```json
{
  "timestamp": "2026-04-22T12:34:56.789Z",
  "status": 200,
  "message": "OK",
  "data": { }
}
```

#### Field rules

- `timestamp`: ISO-8601 instant (UTC recommended)
- `status`: HTTP status code as number
- `message`: short human-readable message (stable, non-sensitive)
- `data`: the actual payload (object/array/primitive/null)

### Examples

**GET /products**

```json
{
  "timestamp": "2026-04-22T12:34:56.789Z",
  "status": 200,
  "message": "OK",
  "data": [
    { "id": 1, "name": "Laptop A", "price": 1000 }
  ]
}
```

**POST /orders** (created)

```json
{
  "timestamp": "2026-04-22T12:34:56.789Z",
  "status": 201,
  "message": "CREATED",
  "data": { "id": 99, "state": "PENDING" }
}
```

### Where to implement

- **Primary mechanism**: Spring MVC `ResponseBodyAdvice<?>` (per-service) to wrap controller return values into the envelope consistently.
- **Opt-out**:
  - Do not wrap responses that are already the envelope type.
  - Do not wrap endpoints that return `ResponseEntity<Void>` / empty bodies.
  - Do not wrap non-JSON (files, streams) and actuator endpoints.

---

## Design: Standard Error Schema (Failure)

### Error schema (required fields)

All error responses (4xx/5xx) must follow:

```json
{
  "timestamp": "2026-04-22T12:34:56.789Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "code": "VALIDATION_ERROR",
  "path": "/api/products/123",
  "traceId": "2f3b0b9d6b0f4b7a",
  "fieldErrors": [
    { "field": "name", "rejectedValue": "", "message": "must not be blank" }
  ]
}
```

#### Field rules

- `timestamp`: ISO-8601 instant
- `status`: HTTP status code
- `error`: the HTTP reason phrase (or a stable equivalent)
- `message`: safe, human-readable message (no stack traces)
- `code`: stable, machine-friendly error code string
- `path`: request path *as seen by the caller* (when proxied, prefer gateway path)
- `traceId`: request correlation id (see decisions below)
- `fieldErrors`: array; empty `[]` when not a validation error

### Common error code set (baseline)

Use these codes consistently across services:

- `VALIDATION_ERROR` → 400
- `BAD_REQUEST` → 400 (non-validation)
- `UNAUTHORIZED` → 401
- `FORBIDDEN` → 403
- `NOT_FOUND` → 404
- `CONFLICT` → 409
- `UNPROCESSABLE_ENTITY` → 422 (optional; only if you already use it consistently)
- `INTERNAL_ERROR` → 500
- `SERVICE_UNAVAILABLE` → 503 (timeouts/downstream unavailable)

### Where to implement

- **Per service**: `@RestControllerAdvice` + exception handlers to map:
  - `MethodArgumentNotValidException` / `BindException` → `VALIDATION_ERROR` with `fieldErrors[]`
  - `HttpMessageNotReadableException` → `BAD_REQUEST`
  - domain exceptions (later phases) → `NOT_FOUND`, `CONFLICT`, etc.
  - fallback `Exception` → `INTERNAL_ERROR`
- **Gateway**:
  - Do **not** transform downstream service error bodies (pass through) to avoid double-wrapping.
  - Implement a **gateway-level error handler** for errors produced by the gateway itself (route not found, predicate mismatch, etc.) to return the same schema.

---

## Design: CRUD Status Code Conventions (API-04)

Apply consistently for typical REST CRUD patterns:

- **GET list / GET item**:
  - 200 with envelope + `data`
  - 404 for missing item (error schema)
- **POST create**:
  - 201 with created object in `data`
  - 400 for validation
  - 409 if unique constraint / duplicate
- **PUT update / PATCH**:
  - 200 with updated object in `data` (preferred for simplicity)
  - 404 if item missing
  - 400 for validation
  - 409 for conflict
- **DELETE**:
  - 204 (no body) for successful delete (preferred)
  - 404 if missing

**Rule:** Status code must match semantics even when proxied through the gateway; the gateway should not rewrite statuses.

---

## Decisions (implementation choices to keep this realistic)

### D1 — Envelope for success responses

- Implement in each Spring MVC service via **`ResponseBodyAdvice`** (wrap JSON controller responses).
- Keep envelope type as a shared DTO class (either copied per service or placed in a small shared module if repo structure supports it).

Why: minimal impact on controllers, consistent behavior, avoids per-endpoint boilerplate.

### D2 — Error handling

- Implement in each service via **`@RestControllerAdvice`** producing the standard error schema.
- For validation: include `fieldErrors[]` with `field`, `rejectedValue`, `message`.

Why: errors are created at the source of truth (the service), not guessed by the gateway.

### D3 — Trace ID propagation (`traceId`)

Baseline approach without adding distributed tracing systems:

- Gateway generates/ensures a request id:
  - If incoming header `X-Request-Id` exists → reuse it
  - Else → generate a short UUID and add `X-Request-Id`
- Gateway forwards `X-Request-Id` to downstream services.
- Services read `X-Request-Id` and include it as `traceId` in error schema (and optionally in success envelope later if desired).

Why: provides correlation with minimal dependencies and fits student/MVP constraints.

### D4 — Swagger/OpenAPI enablement

- For Spring MVC services: use `springdoc-openapi-starter-webmvc-ui`.
- For gateway (WebFlux): use `springdoc-openapi-starter-webflux-ui`.
- Standardize docs endpoints:
  - OpenAPI JSON: `/v3/api-docs`
  - Swagger UI: `/swagger-ui.html` (springdoc will redirect to `/swagger-ui/index.html` in newer versions; keep both working)

### D5 — Gateway “OpenAPI surface” (API-02)

Choose **gateway-hosted Swagger UI with multiple configured upstream specs** (no aggregation):

- Gateway runs Swagger UI at: `/swagger-ui.html`
- Gateway Swagger UI shows multiple APIs using `springdoc.swagger-ui.urls[*]`
- Each URL points to gateway-proxied docs endpoints:
  - `/api/users/v3/api-docs`
  - `/api/products/v3/api-docs`
  - `/api/orders/v3/api-docs`
  - `/api/payments/v3/api-docs`
  - `/api/inventory/v3/api-docs`
  - `/api/notifications/v3/api-docs`

**Trade-offs**

- Pros:
  - Minimal code; mostly configuration
  - Works naturally with gateway routing (single entry point for frontend)
  - Avoids complex/fragile OpenAPI merging logic
- Cons:
  - Not a single merged spec; consumers choose a service in UI
  - Cross-service “one client SDK” generation is harder (can be handled later by generating per-service clients or by a build-time merger if needed)

This is the most realistic baseline for Spring Cloud Gateway without heavy custom code.

---

## Swagger Enablement Details (API-01)

### Dependencies (per service)

Add to each service `pom.xml`:

- `org.springdoc:springdoc-openapi-starter-webmvc-ui`

Add to gateway `pom.xml`:

- `org.springdoc:springdoc-openapi-starter-webflux-ui`

Versioning: keep **springdoc version compatible with Spring Boot 3.3.x** (use a property in each pom, or a parent dependencyManagement if present).

### Configuration (per service)

Add `application.yml` (or extend existing) to standardize:

- `springdoc.api-docs.path=/v3/api-docs`
- `springdoc.swagger-ui.path=/swagger-ui.html`
- Ensure actuator endpoints do not shadow swagger paths (keep actuator at `/actuator`).

Also ensure OpenAPI includes correct `servers` base URL when proxied:

- Set `springdoc.server-url` if needed, or use an `OpenAPI` customizer bean to set server URL to `/api/<resource-prefix>` when accessed through gateway (optional; only if UI shows wrong base paths).

### Configuration (gateway)

Add gateway `application.yml`:

- Enable swagger-ui in gateway at `/swagger-ui.html`
- Configure `springdoc.swagger-ui.urls` list pointing to the **real gateway resource prefixes** (e.g., `/api/users/v3/api-docs`, `/api/products/v3/api-docs`, ...)

---

## Tasks (ordered, with file-level touchpoints)

> Note: This plan is “doable” for students because it favors small, repeatable changes per service, and avoids complex OpenAPI merging.

### Task 1 — Define shared contract DTOs and conventions (API-03, API-04)

**Target services:** all Spring MVC services:
`user-service`, `product-service`, `order-service`, `payment-service`, `inventory-service`, `notification-service`

**Where to place code (concrete packages):**

- `user-service` base package: `com.ptit.htpt.userservice`
- `product-service` base package: `com.ptit.htpt.productservice`
- `order-service` base package: `com.ptit.htpt.orderservice`
- `payment-service` base package: `com.ptit.htpt.paymentservice`
- `inventory-service` base package: `com.ptit.htpt.inventoryservice`
- `notification-service` base package: `com.ptit.htpt.notificationservice`

Create:
- API contract types under `<basePackage>.api.*`
- Web filters/controllers under `<basePackage>.web.*`

**Files to create/modify (example shown for `user-service`; replicate in each service with its base package):**

- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/api/ApiResponse.java` (envelope DTO)
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/api/ApiErrorResponse.java` (error DTO)
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/api/FieldErrorItem.java` (field error DTO)
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/api/GlobalExceptionHandler.java` (`@RestControllerAdvice`)
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/TraceIdFilter.java` (reads `X-Request-Id` into request context)
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/ContractSmokeController.java` (minimal endpoints so Phase 1 is falsifiable)
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/api/ApiResponseAdvice.java` (`ResponseBodyAdvice`, can be added after errors + smoke endpoints are working)

**Action steps:**

#### Task 1A — Error schema + traceId + smoke endpoints (make Phase 1 verifiable)

1. Create `ApiErrorResponse` and `FieldErrorItem` with the fields specified in the design section.
2. Implement `@RestControllerAdvice`:
   - map validation exceptions to `VALIDATION_ERROR` with `fieldErrors[]`
   - map parse errors to `BAD_REQUEST`
   - map unknown exceptions to `INTERNAL_ERROR`
3. Implement a simple filter that:
   - reads `X-Request-Id` from header
   - makes it available to exception handler (request attribute / MDC)
   - ensures `traceId` is populated in the error schema
4. Add a minimal `ContractSmokeController` per service so Phase 1 can be tested before Phase 2 CRUD exists:
   - `GET /__contract/ping` → returns `{ "service": "<service-name>" }` (later will be envelope-wrapped)
   - `POST /__contract/validate` → accepts a `@Valid` DTO (e.g., `name @NotBlank`) to force `VALIDATION_ERROR`

#### Task 1B — Success response envelope (wrap responses safely)

5. Create `ApiResponse<T>` with fields: `timestamp`, `status`, `message`, `data`.
6. Implement `ResponseBodyAdvice` to wrap successful controller responses into `ApiResponse<T>`.
7. Ensure these endpoints are NOT wrapped (opt-out list):
   - `/actuator/**`
   - `/v3/api-docs/**`
   - `/swagger-ui/**`
   - `/swagger-ui.html`
   - `/swagger-resources/**` (if present)
   - non-JSON (files/streams)

**Verification (per service):**

- Compile:
  - `cd sources/backend/<service>; mvn -DskipTests package`
- Run service and check shapes directly (replace port if different locally):
  - `curl -sS http://localhost:8080/actuator/health`
- Verify smoke endpoints (falsifiable baseline):
  - `curl -i http://localhost:8080/__contract/ping` → 200
  - `curl -i -H "Content-Type: application/json" -d "{}" http://localhost:8080/__contract/validate` → 400 with `code=VALIDATION_ERROR` and non-empty `fieldErrors[]`
  - Confirm `traceId` is present when `X-Request-Id` is sent:
    - `curl -i -H "X-Request-Id: test-trace-1" -H "Content-Type: application/json" -d "{}" http://localhost:8080/__contract/validate`
- Verify envelope wrapping after Task 1B:
  - `curl -sS http://localhost:8080/__contract/ping` should be wrapped in `ApiResponse<T>`

### Task 2 — Enable Swagger/OpenAPI consistently in every service (API-01)

**Target services:** all Spring MVC services listed above.

**Files to modify (per service):**

- `sources/backend/user-service/pom.xml`
- `sources/backend/user-service/src/main/resources/application.yml`

- `sources/backend/product-service/pom.xml`
- `sources/backend/product-service/src/main/resources/application.yml`

- `sources/backend/order-service/pom.xml`
- `sources/backend/order-service/src/main/resources/application.yml`

- `sources/backend/payment-service/pom.xml`
- `sources/backend/payment-service/src/main/resources/application.yml`

- `sources/backend/inventory-service/pom.xml`
- `sources/backend/inventory-service/src/main/resources/application.yml`

- `sources/backend/notification-service/pom.xml`
- `sources/backend/notification-service/src/main/resources/application.yml`

**Action steps:**

1. Add `springdoc-openapi-starter-webmvc-ui` dependency.
2. Configure standard paths (`/v3/api-docs`, `/swagger-ui.html`).
3. Verify swagger is reachable and does not conflict with actuator.
4. Confirm the generated OpenAPI reflects real endpoints/DTOs:
   - Ensure controller request/response DTOs are annotated enough for schema (e.g., `@Schema` is optional; start without heavy annotation and only add where required for clarity).

**Verification (per service):**

- Run service and verify endpoints:
  - `curl -sS http://localhost:8080/v3/api-docs` (expect JSON)
  - PowerShell alternative: `curl -sS http://localhost:8080/v3/api-docs | Select-Object -First 20`
  - `curl -I http://localhost:8080/swagger-ui.html`
  - Ensure actuator still works:
    - `curl -sS http://localhost:8080/actuator/health`

### Task 3 — Gateway: publish discoverable OpenAPI surface + gateway error compatibility (API-02, API-03)

**Target:** `sources/backend/api-gateway`

**Files to modify/create:**

- `sources/backend/api-gateway/pom.xml` (add springdoc WebFlux UI)
- `sources/backend/api-gateway/src/main/resources/application.yml` (springdoc swagger-ui urls)
- `sources/backend/api-gateway/src/main/java/com/ptit/htpt/apigateway/gateway/RequestIdFilter.java` (ensure `X-Request-Id`)
- `sources/backend/api-gateway/src/main/java/com/ptit/htpt/apigateway/gateway/GlobalGatewayErrorHandler.java` (gateway-generated errors -> standard error schema)

**Action steps:**

1. Add `springdoc-openapi-starter-webflux-ui` dependency.
2. Configure gateway swagger UI with `springdoc.swagger-ui.urls` list, one per service, pointing to **gateway-proxied** OpenAPI JSON endpoints (matching the gateway’s existing route prefixes).

Add this to `sources/backend/api-gateway/src/main/resources/application.yml`:

```yml
springdoc:
  swagger-ui:
    path: /swagger-ui.html
    urls:
      - name: users
        url: /api/users/v3/api-docs
      - name: products
        url: /api/products/v3/api-docs
      - name: orders
        url: /api/orders/v3/api-docs
      - name: payments
        url: /api/payments/v3/api-docs
      - name: inventory
        url: /api/inventory/v3/api-docs
      - name: notifications
        url: /api/notifications/v3/api-docs
```

3. Ensure gateway routes allow the OpenAPI docs paths to reach each downstream service docs endpoint (respecting your existing prefix + rewrite rules).
4. Implement a gateway filter that:
   - adds `X-Request-Id` if missing
   - forwards it downstream
5. Implement gateway error handler for gateway-local errors:
   - route not found / no matching route → return standard error schema with `code=NOT_FOUND` and proper `path` and `traceId`
   - keep downstream errors unmodified (pass through status + body)

**Verification (gateway):**

- Build:
  - `cd sources/backend/api-gateway; mvn -DskipTests package`
- Run gateway and verify:
  - Swagger UI is reachable:
    - `curl -I http://localhost:8080/swagger-ui.html`
  - Each proxied docs endpoint is reachable (requires services running):
    - `curl -sS http://localhost:8080/api/users/v3/api-docs` (expect JSON)
    - `curl -sS http://localhost:8080/api/products/v3/api-docs` (expect JSON)
    - `curl -sS http://localhost:8080/api/orders/v3/api-docs` (expect JSON)
    - `curl -sS http://localhost:8080/api/payments/v3/api-docs` (expect JSON)
    - `curl -sS http://localhost:8080/api/inventory/v3/api-docs` (expect JSON)
    - `curl -sS http://localhost:8080/api/notifications/v3/api-docs` (expect JSON)
    - PowerShell alternative: append `| Select-Object -First 20` if you want to preview only the first lines.
  - Gateway-local not found returns standard error schema:
    - `curl -sS -i http://localhost:8080/api/does-not-exist/foo`
    - Confirm `status=404`, `code=NOT_FOUND`, and `traceId` present.

---

## Verification Checklist (phase-level)

### A. Docs reachable everywhere (Success criterion #1, API-01)

For each service (direct):

- `GET http://localhost:<servicePort>/v3/api-docs` returns JSON OpenAPI
- `GET http://localhost:<servicePort>/swagger-ui.html` returns 200/302 and UI loads

For gateway (proxied):

- `GET http://localhost:8080/api/<resource-prefix>/v3/api-docs` returns same service OpenAPI JSON
- `GET http://localhost:8080/swagger-ui.html` loads and lists all services

### B. Error format consistent (Success criterion #2, API-03)

For each service and through gateway, verify at least:

- Invalid JSON → 400 with standard error schema and `code=BAD_REQUEST`
- Validation failure (if any validated endpoint exists) → 400 with `code=VALIDATION_ERROR` and non-empty `fieldErrors[]`
- Not found → 404 with standard error schema and `code=NOT_FOUND`
- Gateway-local route miss → 404 with standard error schema and `code=NOT_FOUND`

### C. Response envelopes consistent (Success criterion #3)

For at least one known endpoint per service:

- 200/201 responses are wrapped in `ApiResponse<T>`
- 204 delete remains body-less (not wrapped)
- Status codes match CRUD conventions

---

## Security considerations (MVP-appropriate)

- Swagger UI is a high-signal surface; keep it **enabled in local/dev**. If you have profiles, consider enabling it only under `dev`/`local` profiles later (not required for Phase 1 unless you already have profile separation).
- Ensure error responses **never** include stack traces or sensitive internal details.
- Trace ID should be treated as non-secret; do not put user PII in it.

---

## Risks / Pitfalls & Mitigation

- **Springdoc dependency mismatch** with Spring Boot 3.3.x:
  - Mitigation: use a known compatible springdoc version and keep it consistent across all services and gateway.
- **Gateway path rewriting breaks `/v3/api-docs` proxying**:
  - Mitigation: explicitly test `/api/<resource-prefix>/v3/api-docs` through gateway (e.g., `/api/users/v3/api-docs`); adjust rewrite predicates if needed.
- **Swagger UI path confusion** (`/swagger-ui.html` vs `/swagger-ui/index.html`):
  - Mitigation: standardize on `/swagger-ui.html` and accept redirects; document both if needed.
- **Double-wrapping responses** (envelope wraps already-wrapped or error bodies):
  - Mitigation: ResponseBodyAdvice must detect and skip envelope/error types; gateway must pass-through downstream bodies.
- **Actuator endpoint conflicts**:
  - Mitigation: keep actuator under `/actuator`; avoid moving springdoc paths under `/actuator` or vice versa.

---

## Mapping to requirements (traceability)

- **API-01**: Task 2 + Task 3 docs endpoints and UI reachable for every service/gateway
- **API-02**: Task 3 gateway-hosted Swagger UI with per-service specs via gateway routes
- **API-03**: Task 1 standard envelope + error schema + traceId; Task 3 gateway-local errors match schema
- **API-04**: CRUD status code conventions documented here + verified in checklist

