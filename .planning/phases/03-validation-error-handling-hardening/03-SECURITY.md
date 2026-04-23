---
phase: 3
slug: validation-error-handling-hardening
status: verified
threats_open: 0
asvs_level: 1
created: 2026-04-23
---

# Phase 3 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

Verification strategy: preliminary classification based on mitigation evidence already
captured in 03-01-SUMMARY.md, 03-02-SUMMARY.md, and the regression test suites exercised
in 03-UAT.md Test 1. Deep auditor verification was offered and declined by the user; all
threats are classified CLOSED by code + test evidence documented below.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| client → service API | Untrusted request body and query inputs enter validation pipeline | Request bodies, headers, query strings (untrusted) |
| service exception mapper → client response | Internal exception details must not leak via mapped error payload | Error envelope (JSON) — must be sanitized before serialization |
| client → gateway | Untrusted input and upstream failures enter reactive error pipeline | HTTP requests + reactive error signals |
| gateway → downstream services | Error payload crossing service boundary may be malformed or overly rewritten | Proxied JSON envelopes (pass-through or normalized) |
| auth/authz mapper → client | Authorization state must be represented correctly (401 vs 403) | Auth outcome codes, principal-agnostic |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-03-01 | I (Info Disclosure) | `GlobalExceptionHandler` rejectedValue output | mitigate | `maskOrTruncate(field, value)` in all six services — returns `***` when field name contains `password`/`token`/`secret`, otherwise truncates to 120 chars + `...`. Verified by `handleValidation_masksSensitiveFields` (user-service test lines 48–65) and `handleValidation_truncatesLongNonSensitiveValues` (user-service test lines 67–82). | closed |
| T-03-02 | T (Tampering) | Error-code mapping consistency | mitigate | Common code taxonomy (`VALIDATION_ERROR`, `BAD_REQUEST`, `INTERNAL_ERROR`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `CONFLICT`) enforced in every service's `GlobalExceptionHandler`. No service-specific codes. Verified by `handleValidation_returnsValidationErrorEnvelope` (user-service test lines 21–46) and parallel envelope-shape assertions in order-service test. | closed |
| T-03-03 | R (Repudiation) | Traceability of failures | mitigate | `traceId` resolved from `TraceIdFilter.ATTR_NAME` request attribute with fallback to `X-Request-Id` header; written into every mapped envelope. Verified by `getTraceId_fallsBackToRequestHeader_whenAttributeMissing` (user-service test lines 142–153) and the traceId assertions in `handleValidation_returnsValidationErrorEnvelope`. | closed |
| T-03-04 | T (Tampering) | Gateway pass-through logic | mitigate | `tryExtractPassThroughBody` in `GlobalGatewayErrorHandler` validates downstream envelope structure (`status > 0`, non-blank `code`, non-blank `message`) before preserving body. Non-compliant payloads fall through to the normalization branch. Bug fix recorded in 03-02-SUMMARY: `NotFoundException` simple-name check moved ahead of the generic `ResponseStatusException` branch to prevent missing routes from incorrectly returning 503. Verified by `handle_passesThroughCompliantDownstreamErrorBody` (gateway test lines 85–112) and `handle_returnsNotFoundEnvelope_forSpringCloudGatewayMissingRoute` (gateway test lines 26–42). | closed |
| T-03-05 | I (Info Disclosure) | Gateway/service auth errors | mitigate | `mapCommonCode` at both gateway and service layers emits distinct `UNAUTHORIZED` / `FORBIDDEN` codes. `ResponseStatusException` is dispatched before the generic `Exception` branch, so auth outcomes never collapse into `INTERNAL_ERROR`. Verified on both sides: service tests `handleResponseStatus_mapsUnauthorized` (lines 116–127), `handleResponseStatus_mapsForbidden` (lines 129–140); gateway tests `handle_mapsUnauthorized_fromResponseStatusException` (lines 44–57), `handle_mapsForbidden_fromResponseStatusException` (lines 59–69). | closed |
| T-03-06 | R (Repudiation) | Cross-boundary tracing | mitigate | Reactive gateway handler reads `X-Request-Id` from request headers and writes it into every envelope (normalized and pass-through). `handle_passesThroughCompliantDownstreamErrorBody` preserves the downstream `traceId` rather than overwriting with the gateway's own trace (gateway test line 108). Every gateway test routes through `newExchange(path, traceId)` which sets `RequestIdFilter.REQUEST_ID_HEADER` (line 140). Phase 01 UAT Test 3 (2026-04-22) live-confirmed end-to-end X-Request-Id propagation. | closed |

*Status: open · closed*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|

No accepted risks.

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-04-23 | 6 | 6 | 0 | /gsd-secure-phase (option 2 — accept preliminary classification; evidence-based, no auditor agent) |

### 2026-04-23 — Audit notes

- Input state: B (no prior SECURITY.md; PLANs and SUMMARYs exist).
- Classification method: evidence collected during `/gsd-verify-work 3` — three regression
  test suites executed in `maven:3.9-eclipse-temurin-17` on 2026-04-23 all passed
  (user-service 8 / order-service 6 / api-gateway 6 — chained under `set -e`, exit 0).
- Spring wiring verified by grep: `@RestControllerAdvice` + appropriate
  `@ExceptionHandler` annotations exist on every service's `GlobalExceptionHandler`.
- No implementation gaps found by inspection; every mitigation clause maps to a named
  regression assertion that is green.
- User explicitly declined spawning `gsd-security-auditor` (option 2 in workflow gate);
  if a deeper audit is required later, re-run `/gsd-secure-phase 3`.

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log (none)
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-04-23 (evidence-based; no separate auditor agent)
