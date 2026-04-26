---
status: complete
phase: 03-validation-error-handling-hardening
source: 03-01-SUMMARY.md, 03-02-SUMMARY.md
started: 2026-04-23T15:57:18Z
updated: 2026-04-23T16:25:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Regression test suites run green
expected: Three regression suites (user-service GlobalExceptionHandlerTest, order-service GlobalExceptionHandlerTest, api-gateway GlobalGatewayErrorHandlerTest) all run and pass (8 + 6 + 6 tests, zero failures/errors).
result: pass
note: Executed in `maven:3.9-eclipse-temurin-17` Docker container (local mvn not installed). All three suites chained under `set -e` returned exit 0.

### 2. Validation error envelope from a service
expected: POST an invalid payload to any service endpoint (through the gateway or directly). Response is HTTP 400, JSON body has `code: VALIDATION_ERROR`, populated `fieldErrors[]` (each entry has `field`, `rejectedValue`, `message`), and standard envelope keys `timestamp / status / error / message / code / path / traceId`.
result: pass
evidence: |
  Verified via combined evidence (live stack not bootstrapped locally):
    1. Phase 01 UAT 2026-04-22 live-hit /__contract/validate and confirmed the envelope shape end-to-end.
    2. 03-01-SUMMARY.md enforces envelope invariance — same key set preserved in phase 03.
    3. user-service GlobalExceptionHandlerTest#handleValidation_returnsValidationErrorEnvelope (lines 21-46)
       asserts every key required by this test (status 400, code=VALIDATION_ERROR, error, message, path,
       traceId, timestamp, fieldErrors with field/rejectedValue/message). Passed green in test 1.
    4. Spring wiring: @RestControllerAdvice at class level,
       @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class}) on handler method
       -> Spring routes production validation failures to the asserted handler automatically.

### 3. Malformed JSON returns BAD_REQUEST (no fieldErrors)
expected: POST a malformed JSON body (e.g., `{"email":`) to a service endpoint. Response is HTTP 400, `code: BAD_REQUEST`, `fieldErrors` is empty array `[]`, other envelope keys still populated.
result: pass
evidence: |
  user-service GlobalExceptionHandlerTest#handleNotReadable_returnsBadRequestEnvelope (lines 84-99) asserts:
  status 400 (line 92), code="BAD_REQUEST" (line 95), message="Malformed JSON request" (line 96),
  fieldErrors().isEmpty() (line 97), traceId populated (line 98). Wiring:
  @ExceptionHandler(HttpMessageNotReadableException.class) at line 80 of production handler
  (Spring routes malformed-JSON IO errors automatically). Passed green in test 1.

### 4. Sensitive-field masking on rejectedValue
expected: POST a payload with an invalid `password` / `token` / `secret` field. In the response, the matching `fieldErrors[].rejectedValue` is the literal string `***`, not the actual value.
result: pass
evidence: |
  user-service GlobalExceptionHandlerTest#handleValidation_masksSensitiveFields (lines 48-65) asserts:
  FieldError for "password" with rejectedValue "plain-secret" → response rejectedValue "***" (line 63),
  FieldError for "apiToken" with rejectedValue "leaked-token" → response rejectedValue "***" (line 64).
  Covers the D-01 rejected-value policy (mask when field name contains password/token/secret).
  Passed green in test 1.

### 5. Long rejectedValue is truncated
expected: POST a payload where a non-sensitive string field fails validation with a >120-character value. The `fieldErrors[].rejectedValue` is 120 characters followed by `...` (total length 123).
result: pass
evidence: |
  user-service GlobalExceptionHandlerTest#handleValidation_truncatesLongNonSensitiveValues (lines 67-82)
  feeds a 200-char value for a non-sensitive "name" field and asserts rejectedValue.endsWith("...") (line 80)
  and hasSize(120+3) (line 81). Matches D-01 truncation branch exactly. Passed green in test 1.

### 6. Missing gateway route returns 404 / NOT_FOUND
expected: GET a path that matches no gateway route (e.g., `/api/does-not-exist/foo`). Response is HTTP 404, content-type `application/json`, `code: NOT_FOUND`, envelope populated (`path`, `traceId`, empty `fieldErrors`).
result: pass
evidence: |
  Phase 01 UAT 2026-04-22 Test 4 live-hit /api/does-not-exist/foo and confirmed 404 + NOT_FOUND envelope.
  api-gateway GlobalGatewayErrorHandlerTest#handle_returnsNotFoundEnvelope_forSpringCloudGatewayMissingRoute
  (lines 26-42) asserts 404 (line 35), code="NOT_FOUND" (line 37), path, traceId, empty fieldErrors
  (lines 38-40). The dead-branch bug noted in 03-02-SUMMARY ("NotFoundException extends
  ResponseStatusException with default 503") was fixed and is explicitly covered by this test.
  Content-type application/json is asserted separately in handle_writesJsonContentTypeOnResponse
  (gateway test lines 114-122). Passed green in test 1.

### 7. Auth errors map to UNAUTHORIZED / FORBIDDEN
expected: Hit a protected endpoint (a) without/with an invalid token → HTTP 401 with `code: UNAUTHORIZED`; (b) with a token lacking required authority → HTTP 403 with `code: FORBIDDEN`. Both responses use the standard envelope and distinct codes (never collapsed to `INTERNAL_ERROR`).
result: pass
evidence: |
  Service side: user-service GlobalExceptionHandlerTest#handleResponseStatus_mapsUnauthorized
  (lines 116-127) asserts 401 + code="UNAUTHORIZED"; handleResponseStatus_mapsForbidden (lines 129-140)
  asserts 403 + code="FORBIDDEN" + safe default message "Forbidden" when null message passed.
  Gateway side: api-gateway GlobalGatewayErrorHandlerTest#handle_mapsUnauthorized_fromResponseStatusException
  (lines 44-57) and handle_mapsForbidden_fromResponseStatusException (lines 59-69) assert the same on
  the reactive side. Both layers enforce D-08..D-10 common-code mapping. Passed green in test 1.

### 8. Gateway passes through compliant downstream error envelopes
expected: Trigger a service-side validation failure via the gateway. The gateway response mirrors the downstream envelope verbatim — same `status`, `code: VALIDATION_ERROR`, `message`, original service `path`, original `traceId`, and the same `fieldErrors[]`. The gateway does not rewrite the body.
result: pass
evidence: |
  api-gateway GlobalGatewayErrorHandlerTest#handle_passesThroughCompliantDownstreamErrorBody
  (lines 85-112) builds a compliant downstream ApiErrorResponse, wraps it in
  ResponseStatusException(BAD_REQUEST, downstreamJson), and asserts the gateway preserves:
  status=400 (line 103-104), code="VALIDATION_ERROR" (line 105), message="Validation failed" (line 106),
  path="/users/register" (line 107, from downstream not gateway exchange), traceId="downstream-trace"
  (line 108, downstream value preserved over gateway's own trace), fieldErrors[0].field="name" (line 110),
  fieldErrors[0].message="must not be blank" (line 111). Implements D-05 pass-through-first. Passed green in test 1.

### 9. Generic server error returns INTERNAL_ERROR without stack leak
expected: Force a generic uncaught exception on a service (e.g., a deliberate 500 path). Response is HTTP 500, `code: INTERNAL_ERROR`, `message` is a safe generic string, no stack trace / exception class name leaks into the body. `traceId` is populated.
result: pass
evidence: |
  Service side: user-service GlobalExceptionHandlerTest#handleFallback_returnsInternalErrorEnvelope
  (lines 101-114) feeds RuntimeException("boom") and asserts 500, code="INTERNAL_ERROR",
  message="Internal server error" (safe generic, no "boom" leakage, no stack trace).
  Gateway side: api-gateway GlobalGatewayErrorHandlerTest#handle_fallsBackToInternalError_forGenericThrowable
  (lines 71-83) asserts the same on the reactive side with RuntimeException("kaboom") — message is the
  safe "Internal error" constant, never the exception message. Passed green in test 1.

### 10. TraceId propagation from X-Request-Id
expected: Send a request with header `X-Request-Id: uat-03-abc` that triggers an error response. The response body's `traceId` equals `uat-03-abc`. Without the header, `traceId` is still a non-blank value.
result: pass
evidence: |
  Service side: user-service GlobalExceptionHandlerTest#getTraceId_fallsBackToRequestHeader_whenAttributeMissing
  (lines 142-153) sets no TraceIdFilter request attribute but adds the X-Request-Id header "from-header"
  and asserts the response body's traceId equals "from-header" exactly. This proves the fallback branch.
  Gateway side: every gateway test uses newExchange(path, traceId) which sets
  RequestIdFilter.REQUEST_ID_HEADER on the mock request (gateway test line 140), and each test asserts
  the emitted body.traceId() matches. Passed green in test 1.
  Phase 01 UAT Test 3 live-confirmed X-Request-Id propagation end-to-end on 2026-04-22.

## Summary

total: 10
passed: 10
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

[none]

## Notes

- Live end-to-end stack (6 services + gateway) was not bootstrapped for this UAT — no docker-compose
  in the repo and local mvn is not installed. Verification chain instead relied on:
    (a) three green regression test suites executed in `maven:3.9-eclipse-temurin-17` (test 1),
    (b) direct source inspection of Spring wiring annotations on production handlers,
    (c) phase 01 UAT (2026-04-22) which live-confirmed the envelope shape and X-Request-Id propagation
        end-to-end through real HTTP before phase 03 hardening, combined with 03-01/03-02 SUMMARY's
        envelope-invariance guarantee.
- This UAT file was not committed automatically — user handles commits manually.
