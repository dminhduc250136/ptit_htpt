---
status: complete
phase: 01-api-contract-swagger-baseline
source: 01-01-SUMMARY.md, 01-02-SUMMARY.md, 01-03-SUMMARY.md
started: 2026-04-22T21:35:00Z
updated: 2026-04-22T21:50:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Swagger/OpenAPI reachable through gateway and services
expected: Gateway Swagger UI loads and every proxied /v3/api-docs endpoint returns JSON.
result: pass

### 2. Validation error contract shape is consistent
expected: POST /__contract/validate with invalid payload returns 400 with code=VALIDATION_ERROR and non-empty fieldErrors[].
result: pass

### 3. Trace ID is propagated in error responses
expected: Sending X-Request-Id on validation request returns same traceId in the error body (and response header if exposed).
result: pass

### 4. Gateway-local route miss uses standard error schema
expected: GET /api/does-not-exist/foo returns 404 with standard error schema and code=NOT_FOUND.
result: pass

### 5. Success envelope and opt-out behavior are both correct
expected: /__contract/ping is wrapped in success envelope while /actuator/**, /v3/api-docs/**, and /swagger-ui/** are not wrapped.
result: pass

## Summary

total: 5
passed: 5
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

[none yet]
