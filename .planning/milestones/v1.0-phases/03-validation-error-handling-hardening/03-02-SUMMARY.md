---
phase: 03-validation-error-handling-hardening
plan: 02
status: complete
requirements: [VAL-03]
completed: 2026-04-23
---

# 03-02 Summary — Gateway error propagation and auth error alignment

## Outcome

Gateway and services now produce a single, frontend-compatible error contract for both proxied failures and auth/authz outcomes.

- **Missing routes** through the gateway return `404 / NOT_FOUND` with standard envelope (`traceId`, `path`, empty `fieldErrors`).
- **Proxied downstream errors** whose body already matches the standard envelope are **passed through without rewrite** — status, code, message, and `fieldErrors` are preserved.
- **Non-compliant downstream errors and gateway-native failures** are normalized into the same envelope with a resolved common code.
- **Auth/authz outcomes** (`401 UNAUTHORIZED`, `403 FORBIDDEN`) map to distinct common codes on both sides of the gateway boundary via `ResponseStatusException` handling.
- **Gateway-native content type** is always `application/json`.
- All gateway routes for the six services remain intact in `application.yml` (user, product, order, payment, inventory, notification).

## Files changed

### Gateway handler hardening (commit `d3f61c0` + this plan completion)
- `sources/backend/api-gateway/src/main/java/com/ptit/htpt/apigateway/gateway/GlobalGatewayErrorHandler.java`
- `sources/backend/api-gateway/src/main/java/com/ptit/htpt/apigateway/gateway/ApiErrorResponse.java`

### Service-side 401/403 mapping (already in place via 03-01 + commit `d3f61c0`)
Each service's `GlobalExceptionHandler` now routes `ResponseStatusException` through a `mapCommonCode` branch that emits `UNAUTHORIZED` / `FORBIDDEN` for 401/403 respectively. No additional edits needed in 03-02 — the common-code taxonomy enforced in 03-01 already covers this requirement.

### Regression tests (this plan completion)
- `sources/backend/api-gateway/src/test/java/com/ptit/htpt/apigateway/gateway/GlobalGatewayErrorHandlerTest.java`
- `sources/backend/api-gateway/pom.xml` — added `spring-boot-starter-test` and `reactor-test` (test scope)

### Bug caught and fixed during verification
The tests caught a dead branch in `GlobalGatewayErrorHandler`. Spring Cloud Gateway's `NotFoundException` **extends** `ResponseStatusException` with a default 503 status, so the original branch order
```
if (ex instanceof ResponseStatusException rse) { ... }
else if (ex.getClass().getSimpleName().contains("NotFoundException")) { ... }
```
meant the second branch was unreachable — every missing route returned 503. Fixed by moving the `NotFoundException` simple-name check **before** the generic `ResponseStatusException` branch. This makes route-not-found reliably return `404 / NOT_FOUND` as the plan requires, regardless of the Spring Cloud Gateway default status.

## Decisions enforced

- **D-05 Pass-through-first compatibility** — `tryExtractPassThroughBody` detects when the downstream error reason is a compliant JSON envelope (must have `status > 0`, non-blank `code`, non-blank `message`) and forwards it without mutation, preserving `status`, `code`, `message`, `path`, `traceId`, and `fieldErrors`. Asserted in `handle_passesThroughCompliantDownstreamErrorBody`.
- **D-06 Normalize only incompatible** — gateway-native and non-JSON errors go through the fallback branch, which always emits the full envelope.
- **D-07 Envelope invariance** — gateway `ApiErrorResponse` record shares the same key order as services.
- **D-08..D-10 Auth/authz mapping** — `mapCommonCode` at gateway and service layers both emit `UNAUTHORIZED` / `FORBIDDEN` for 401/403; fallback branch does not swallow auth exceptions because `ResponseStatusException` is dispatched before the generic `Exception` branch.
- **Route preservation** — `application.yml` still contains all six service routes with their prefix/rewrite pairs; springdoc swagger-ui urls still point at each gateway-proxied `/v3/api-docs`.

## Verification

Automated tests under `api-gateway` cover:

1. Missing route via Spring Cloud Gateway `NotFoundException` → 404 / NOT_FOUND with envelope keys populated.
2. `ResponseStatusException(UNAUTHORIZED, "token expired")` → 401 / UNAUTHORIZED with preserved message.
3. `ResponseStatusException(FORBIDDEN, null)` → 403 / FORBIDDEN with safe default message.
4. Generic `RuntimeException` → 500 / INTERNAL_ERROR with traceId preserved from `X-Request-Id` header.
5. Compliant downstream JSON body pass-through → 400 / VALIDATION_ERROR with original `path`, `traceId`, `fieldErrors[]` preserved verbatim.
6. Response content type is always `application/json`.

Commands to run:
```
mvn -q -f sources/backend/api-gateway/pom.xml test -Dtest=GlobalGatewayErrorHandlerTest
```

Run results (verified in `maven:3.9-eclipse-temurin-17` container, 2026-04-23): **6 tests run, 0 failures, 0 errors.**

## Threat mitigations

- **T-03-04 (Tampering via gateway pass-through):** compliance validation in `tryExtractPassThroughBody` ensures only structurally valid envelopes are preserved; otherwise the fallback path normalizes.
- **T-03-05 (Information Disclosure via auth error collapse):** `mapCommonCode` emits distinct `UNAUTHORIZED` / `FORBIDDEN` codes; regression tests prevent regression to generic fallback.
- **T-03-06 (Repudiation / cross-boundary trace loss):** `traceId` is read from `X-Request-Id` header in the reactive handler and written into every envelope; test asserts this for both normalized and pass-through paths.

## Phase-level exit checklist

- [x] VAL-01 — Invalid requests return structured field errors across all services (03-01).
- [x] VAL-02 — Common failure modes produce stable common codes (03-01).
- [x] VAL-03 — Gateway and services produce compatible error shapes for frontend handling (03-02).
- [x] Automated tests cover the envelope contract in representative services and the gateway.
- [x] `application.yml` still maps all six service routes.

Phase 3 is ready for `/gsd-verify-work`.
