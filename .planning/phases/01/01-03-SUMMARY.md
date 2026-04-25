# 01-03 — SUMMARY — Gateway docs surface + request-id propagation

## Completed

Updated the API gateway to provide a **single docs entry point** and baseline request-correlation support.

## Key changes

- **Gateway Swagger UI**:
  - Added `org.springdoc:springdoc-openapi-starter-webflux-ui` (version `2.6.0`)
  - Configured `/swagger-ui.html` in the gateway to list each service’s spec via the existing gateway routes:
    - `/api/users/v3/api-docs`
    - `/api/products/v3/api-docs`
    - `/api/orders/v3/api-docs`
    - `/api/payments/v3/api-docs`
    - `/api/inventory/v3/api-docs`
    - `/api/notifications/v3/api-docs`

- **`X-Request-Id` propagation**:
  - Added a `GlobalFilter` that ensures `X-Request-Id` exists and is forwarded downstream
  - Echoes `X-Request-Id` on responses for easier debugging

- **Gateway-local error schema (baseline)**:
  - Added an `ErrorWebExceptionHandler` to return the standard error schema for gateway-generated errors (e.g., route miss), without rewriting downstream service error bodies.

## Verification notes

- Gateway Maven build passed with `mvn -DskipTests package`.

