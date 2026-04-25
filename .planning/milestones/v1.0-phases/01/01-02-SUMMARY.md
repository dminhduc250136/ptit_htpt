# 01-02 — SUMMARY — Swagger/OpenAPI enabled in every service

## Completed

Enabled **Springdoc OpenAPI + Swagger UI** consistently in all Spring MVC services:

- `user-service`
- `product-service`
- `order-service`
- `payment-service`
- `inventory-service`
- `notification-service`

## Key changes

- Added `org.springdoc:springdoc-openapi-starter-webmvc-ui` (version `2.6.0`) to each service `pom.xml`.
- Standardized `application.yml` so each service serves:
  - OpenAPI JSON at `/v3/api-docs`
  - Swagger UI at `/swagger-ui.html`

## Verification notes

- Maven builds passed for all services with `mvn -DskipTests package`.
- The Phase 1 response envelope opt-outs (from `01-01`) are expected to keep `/v3/api-docs` and Swagger UI endpoints unwrapped.

