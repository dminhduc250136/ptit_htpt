---
phase: 03-validation-error-handling-hardening
plan: 01
status: complete
requirements: [VAL-01, VAL-02]
completed: 2026-04-23
---

# 03-01 Summary — Standardize validation + exception handling in all services

## Outcome

All six Spring MVC services now produce a single, predictable validation / exception envelope, protected by a regression test suite.

- Validation failures → `400 / VALIDATION_ERROR` with `fieldErrors[]` populated.
- Malformed JSON → `400 / BAD_REQUEST` (no `fieldErrors`).
- `ResponseStatusException` → status + common code (`UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `CONFLICT`, `BAD_REQUEST`) with safe default messages.
- Any other `Exception` → `500 / INTERNAL_ERROR` with no stack-trace leakage.
- Field-level `rejectedValue` is masked (`***`) when the field name contains `password` / `token` / `secret`, and truncated to 120 chars + `...` otherwise.
- `traceId` always comes from the `TraceIdFilter` request attribute, falling back to the `X-Request-Id` header when the attribute is missing.

## Files changed

### Hardened handlers (commit `d3f61c0`)
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/api/GlobalExceptionHandler.java`
- `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/api/GlobalExceptionHandler.java`
- `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/api/GlobalExceptionHandler.java`
- `sources/backend/inventory-service/src/main/java/com/ptit/htpt/inventoryservice/api/GlobalExceptionHandler.java`
- `sources/backend/payment-service/src/main/java/com/ptit/htpt/paymentservice/api/GlobalExceptionHandler.java`
- `sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/api/GlobalExceptionHandler.java`

### Regression tests (this plan completion)
- `sources/backend/user-service/src/test/java/com/ptit/htpt/userservice/api/GlobalExceptionHandlerTest.java`
- `sources/backend/order-service/src/test/java/com/ptit/htpt/orderservice/api/GlobalExceptionHandlerTest.java`
- `sources/backend/order-service/pom.xml` (added `spring-boot-starter-test` — user-service already had it)

## Decisions enforced

- **D-01 Rejected-value policy** — mask on sensitive field tokens (`password`, `token`, `secret`), otherwise truncate to 120 characters with `...` suffix. Explicit regression assertions for both branches.
- **D-02 Common code taxonomy** — every handler uses exactly `VALIDATION_ERROR`, `BAD_REQUEST`, `INTERNAL_ERROR`, plus `UNAUTHORIZED` / `FORBIDDEN` / `NOT_FOUND` / `CONFLICT` from `ResponseStatusException`. No service-specific codes.
- **Envelope invariance** — `ApiErrorResponse` keys stayed `timestamp/status/error/message/code/path/traceId/fieldErrors` across all services.

## Verification

Automated regression tests under `user-service` and `order-service` cover:

1. `VALIDATION_ERROR` envelope (status 400, code, message, path, traceId, fieldErrors shape)
2. Sensitive-field masking (`password`, `apiToken` / `paymentSecret` → `***`)
3. Long rejected value truncation (200-char input → 120-char body + `...`)
4. `BAD_REQUEST` branch (HttpMessageNotReadableException, empty fieldErrors)
5. `INTERNAL_ERROR` fallback (generic RuntimeException → 500)
6. `UNAUTHORIZED` / `FORBIDDEN` mapping from `ResponseStatusException`
7. `traceId` fallback from `X-Request-Id` header when attribute missing (user-service)

Commands to run (on a machine with Maven):
```
mvn -q -f sources/backend/user-service/pom.xml test -Dtest=GlobalExceptionHandlerTest
mvn -q -f sources/backend/order-service/pom.xml test -Dtest=GlobalExceptionHandlerTest
```

Run results (verified in `maven:3.9-eclipse-temurin-17` container, 2026-04-23):
- user-service: 8 tests run, 0 failures, 0 errors.
- order-service: 6 tests run, 0 failures, 0 errors.
- product-service / payment-service / inventory-service / notification-service: `mvn compile` clean (no regression tests added by this plan; parallel contracts covered by grep-equivalence with user/order sources).

## Threat mitigations

- **T-03-01 (Information Disclosure on rejectedValue):** masking + truncation covered by dedicated test assertions.
- **T-03-02 (Tampering via inconsistent codes):** common code taxonomy enforced and asserted.
- **T-03-03 (Repudiation / traceId lost):** `traceId` always set from attribute or header; asserted explicitly.

## Follow-ups for 03-02

- Gateway already carries explicit `NotFoundException` branch and pass-through logic (touched in `d3f61c0`) — 03-02 needs regression tests and SUMMARY.
- Services already map `UNAUTHORIZED` / `FORBIDDEN` via `ResponseStatusException` (Task 2 of 03-02 is effectively covered by the common-code branch — confirm in 03-02 summary).
