---
phase: 03
slug: validation-error-handling-hardening
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-23
---

# Phase 03 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Spring Boot test stack via Maven) |
| **Config file** | Service-level `pom.xml` (Surefire defaults) |
| **Quick run command** | `mvn -q -f sources/backend/user-service/pom.xml -DskipTests compile` |
| **Full suite command** | `mvn -q -f sources/backend/user-service/pom.xml test` |
| **Estimated runtime** | ~45-120 seconds per service |

---

## Sampling Rate

- **After every task commit:** Run quick build command for modified service and gateway modules.
- **After every plan wave:** Run full test command for modified modules plus gateway build.
- **Before `/gsd-verify-work`:** Module tests and contract smoke checks must pass.
- **Max feedback latency:** 180 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 03-01-01 | 01 | 1 | VAL-01 | T-03-01 | Validation returns safe field-level errors | unit+smoke | `mvn -q -f sources/backend/user-service/pom.xml test` | ❌ W0 | ⬜ pending |
| 03-01-02 | 01 | 1 | VAL-02 | T-03-02 | Exception mapping produces stable common codes | unit+smoke | `mvn -q -f sources/backend/order-service/pom.xml test` | ❌ W0 | ⬜ pending |
| 03-02-01 | 02 | 2 | VAL-03 | T-03-03 | Gateway/service keep compatible auth and proxy error shapes | integration-smoke | `mvn -q -f sources/backend/api-gateway/pom.xml test` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠ flaky*

---

## Wave 0 Requirements

- [ ] `sources/backend/user-service/src/test/java/.../GlobalExceptionHandlerTest.java` — validation and code mapping baseline tests
- [ ] `sources/backend/api-gateway/src/test/java/.../GlobalGatewayErrorHandlerTest.java` — pass-through/normalize behavior tests
- [ ] Shared fixture helper for error envelope assertions across services

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Gateway pass-through preserves downstream fieldErrors and code | VAL-03 | Cross-service runtime path validation | Run services + gateway locally, call invalid downstream request through gateway, confirm unchanged compatible error fields |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 180s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
