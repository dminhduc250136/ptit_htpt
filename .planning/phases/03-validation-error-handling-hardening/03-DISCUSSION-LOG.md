# Phase 3: Validation & Error Handling Hardening - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-22
**Phase:** 03-validation-error-handling-hardening
**Areas discussed:** Validation field errors, Business error mapping and HTTP status, Gateway downstream error propagation, Auth/AuthZ error contract

---

## Validation field errors

| Option | Description | Selected |
|--------|-------------|----------|
| Always include rejectedValue | Return full rejected input values in field errors | |
| Include only non-sensitive rejectedValue and mask/truncate sensitive data | Safer for secrets and large payloads while preserving debug value | ✓ |
| Never include rejectedValue | Maximum privacy, less debugging context | |

**User's choice:** Option 2
**Notes:** User selected this directly.

| Option | Description | Selected |
|--------|-------------|----------|
| Use raw Bean Validation messages | Detailed per-validator text | |
| Use only generic standardized messages | Fully centralized wording | |
| Return standardized code plus specific message | Stable machine parsing + human detail | ✓ |

**User's choice:** Option 3
**Notes:** User selected this directly.

---

## Business error mapping and HTTP status

| Option | Description | Selected |
|--------|-------------|----------|
| HTTP-common code only | `NOT_FOUND`, `BAD_REQUEST`, `CONFLICT` only | |
| Domain-specific code only | `USER_NOT_FOUND`, `PRODUCT_CONFLICT`, etc. | |
| Hybrid common code + domain detail | Cross-service consistency with domain detail in payload details | ✓ |

**User's choice:** Delegated to agent
**Notes:** Applied recommended option due to user delegation.

| Option | Description | Selected |
|--------|-------------|----------|
| Keep per-service free-form reason | Minimal refactor | |
| Standardize message/reason templates | Consistent UX/FE handling across services | ✓ |
| Standardize only status+code | Message remains service-specific | |

**User's choice:** Delegated to agent
**Notes:** Applied recommended option due to user delegation.

---

## Gateway downstream error propagation

| Option | Description | Selected |
|--------|-------------|----------|
| Always normalize at gateway | Gateway rewrites every downstream error | |
| Pass-through when schema is valid, normalize only fallback | Preserve downstream details while ensuring contract safety | ✓ |
| Always pass-through raw downstream body | Maximum transparency but less safety | |

**User's choice:** Delegated to agent
**Notes:** Applied recommended option due to user delegation.

---

## Auth/AuthZ error contract

| Option | Description | Selected |
|--------|-------------|----------|
| Minimal auth error contract now | Defer strict 401/403 contract to later phase | |
| Lock standardized 401/403 contract now | Stabilize FE behavior before Phase 4 | ✓ |
| Service-specific auth error shapes | Flexible but inconsistent | |

**User's choice:** Delegated to agent
**Notes:** Applied recommended option due to user delegation.

---

## the agent's Discretion

- Exact implementation mechanics for shared constants/utilities.
- Final sensitive-field masking/truncation policy details.

## Deferred Ideas

None.
