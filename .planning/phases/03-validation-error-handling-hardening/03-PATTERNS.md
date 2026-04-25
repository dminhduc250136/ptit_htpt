# Phase 03 Pattern Map: Validation & Error Handling Hardening

## Scope

Map existing analog implementations for validation/error handling hardening so executors can reuse established patterns instead of inventing new shapes.

## Target Pattern Inventory

| Planned Concern | Target Files (expected) | Closest Existing Analog | Pattern Notes |
|---|---|---|---|
| Service validation exception mapping | `sources/backend/*-service/src/main/java/**/api/GlobalExceptionHandler.java` | `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/api/GlobalExceptionHandler.java` | Canonical mapping for `MethodArgumentNotValidException`, `BindException`, malformed JSON, fallback error |
| Service error envelope schema | `sources/backend/*-service/src/main/java/**/api/ApiErrorResponse.java` | `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/api/ApiErrorResponse.java` | Standard keys: `timestamp,status,error,message,code,path,traceId,fieldErrors` |
| Service trace correlation in errors | `sources/backend/*-service/src/main/java/**/web/TraceIdFilter.java` + handler usage | `sources/backend/payment-service/src/main/java/com/ptit/htpt/paymentservice/web/TraceIdFilter.java` | Error responses should keep trace id from request attr/header path |
| Gateway global error mapping | `sources/backend/api-gateway/src/main/java/com/ptit/htpt/apigateway/gateway/GlobalGatewayErrorHandler.java` | same file | Reactive error mapping with status/code classification, request path, request-id trace id |
| Gateway error envelope schema | `sources/backend/api-gateway/src/main/java/com/ptit/htpt/apigateway/gateway/ApiErrorResponse.java` | same file | Keep schema compatible with services to satisfy frontend contract |

## Code Excerpts to Reuse

### Service validation block template

From `user-service` handler:
- Build `FieldErrorItem` list from binding result
- Return `400` with `code = VALIDATION_ERROR`
- Populate `path` and `traceId`

### Service malformed JSON block template

From `user-service` handler:
- Handle `HttpMessageNotReadableException`
- Return `400` with `code = BAD_REQUEST` and empty `fieldErrors`

### Service fallback block template

From `user-service` handler:
- Handle generic `Exception`
- Return `500` with `code = INTERNAL_ERROR`

### Gateway mapping template

From `GlobalGatewayErrorHandler`:
- Map `ResponseStatusException` status to common code
- Map gateway `NotFoundException` to `404/NOT_FOUND`
- Preserve request id header as `traceId`

## Conventions to Preserve

- Keep route prefixes unchanged (`/api/users`, `/api/products`, `/api/orders`, `/api/payments`, `/api/inventory`, `/api/notifications`) per gateway config.
- Keep error-envelope key names unchanged for backward compatibility with Phase 4 frontend alignment.
- Prefer centralized global handlers over per-controller local exception handling.

## Anti-Patterns

- Introducing per-service custom error payload keys.
- Returning sensitive rejected values in validation error items.
- Rewriting compliant downstream gateway errors unnecessarily.
- Conflating `401` and `403` semantics.
