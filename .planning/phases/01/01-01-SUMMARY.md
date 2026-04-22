---
phase: 01
plan: 01-01
subsystem: backend-services
tags: [api-contract, error-schema, trace-id, response-envelope]
requires: []
provides:
  - consistent-success-envelope
  - consistent-error-schema
  - request-trace-id-propagation
affects:
  - sources/backend/user-service
  - sources/backend/product-service
  - sources/backend/order-service
  - sources/backend/payment-service
  - sources/backend/inventory-service
  - sources/backend/notification-service
---

# Phase 01 Plan 01-01: Standard API response + error schema Summary

Implemented a baseline API contract across all Spring MVC services: a consistent **success envelope**, consistent **error schema**, and stable **traceId** propagation via `X-Request-Id`, verified by per-service smoke endpoints.

## What shipped

- **Contract DTOs (per service)**:
  - `ApiResponse<T>`: `{ timestamp, status, message, data }`
  - `ApiErrorResponse`: `{ timestamp, status, error, message, code, path, traceId, fieldErrors[] }`
  - `FieldErrorItem`: `{ field, rejectedValue, message }`
- **Global exception handling (per service)**:
  - Validation → `VALIDATION_ERROR` (400) with non-empty `fieldErrors[]`
  - Malformed JSON → `BAD_REQUEST` (400)
  - Fallback → `INTERNAL_ERROR` (500)
- **Trace ID propagation (per service)**:
  - Reads `X-Request-Id` (generates one if missing)
  - Exposes it to exception handler (request attribute) and returns header back
- **Contract smoke endpoints (per service)**:
  - `GET /__contract/ping` → `{ "service": "<service-name>" }` (wrapped by success envelope)
  - `POST /__contract/validate` → uses `@Valid` to force validation failures on `{}` input
- **Success envelope wrapping (per service)**:
  - Implemented via `ResponseBodyAdvice`
  - Explicitly **skips** `/actuator/**`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/swagger-resources/**`

## Commits

- `cb3c964`: `chore(01-01): add validation starter to services`
- `9c59fc6`: `feat(01-01): user-service contract baseline`
- `bc3b125`: `feat(01-01): product-service contract baseline`
- `fa93041`: `feat(01-01): order-service contract baseline`
- `fdcd76e`: `feat(01-01): payment-service contract baseline`
- `d89424a`: `feat(01-01): inventory-service contract baseline`
- `ef9c917`: `feat(01-01): notification-service contract baseline`

## Key files (examples)

- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/api/GlobalExceptionHandler.java`
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/TraceIdFilter.java`
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/ContractSmokeController.java`
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/api/ApiResponseAdvice.java`

## Deviations from Plan

- None. (Validation starter was added because it is required for `@Valid` smoke endpoint behavior in Spring Boot 3.)

## Known stubs

- None.

## Self-Check: PASSED

- Summary file exists: `.planning/phases/01/01-01-SUMMARY.md`
- Commits present in history: `cb3c964`, `9c59fc6`, `bc3b125`, `fa93041`, `fdcd76e`, `d89424a`, `ef9c917`

