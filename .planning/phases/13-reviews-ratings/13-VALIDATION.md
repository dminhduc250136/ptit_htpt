---
phase: 13
slug: reviews-ratings
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-27
---

# Phase 13 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Maven (Spring Boot Test / JUnit 5) + Jest/Vitest (Next.js) |
| **Config file** | `sources/backend/product-service/pom.xml` / `sources/frontend/package.json` |
| **Quick run command** | `cd sources/frontend && npm run build` |
| **Full suite command** | `cd sources/frontend && npm run build && cd ../backend/product-service && ./mvnw test` |
| **Estimated runtime** | ~60 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd sources/frontend && npm run build`
- **After every plan wave:** Run full suite (build + mvnw test)
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 13-01-01 | 01 | 1 | REV-01 | — | Jsoup strips XSS payload | unit | `./mvnw test -pl product-service -Dtest=ReviewServiceTest` | ❌ W0 | ⬜ pending |
| 13-01-02 | 01 | 1 | REV-01 | — | REVIEW_NOT_ELIGIBLE 422 khi user chưa mua | unit | `./mvnw test -pl product-service -Dtest=ReviewEligibilityTest` | ❌ W0 | ⬜ pending |
| 13-01-03 | 01 | 1 | REV-03 | — | avg_rating recompute không drift | unit | `./mvnw test -pl product-service -Dtest=ReviewServiceTest` | ❌ W0 | ⬜ pending |
| 13-02-01 | 02 | 2 | REV-02 | — | Review list paginated 10/page sort newest | unit | `./mvnw test -pl product-service -Dtest=ReviewControllerTest` | ❌ W0 | ⬜ pending |
| 13-03-01 | 03 | 3 | REV-03 | — | avg_rating + review_count hiển thị đúng FE | build | `cd sources/frontend && npm run build` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `sources/backend/product-service/src/test/java/.../ReviewServiceTest.java` — stub XSS sanitize + eligibility
- [ ] `sources/backend/product-service/src/test/java/.../ReviewControllerTest.java` — stub list/submit endpoints
- [ ] `sources/backend/product-service/src/test/java/.../ReviewServiceTest.java` — avg_rating recompute (thêm method `createReview_recomputesAvgRating`)

*Existing infrastructure covers frontend build verification; Spring Boot Test infrastructure đã có trong pom.xml.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Star widget click/hover UX | REV-01 | CSS interactive, không có selector đơn giản để automate | Mở PDP /products/[slug], click từng sao 1→5, confirm highlight |
| Eligibility pre-check: user chưa mua | REV-01 | Cần docker stack với real orders data | Login user không có DELIVERED order → mở PDP → confirm form hidden, hint visible |
| Review submit → toast + reload list | REV-01 | E2E flow cần đầy đủ stack | Login buyer → submit review → confirm toast 'Đã gửi đánh giá' + list reload |
| avg_rating display trên product card | REV-03 | Visual check trên card component | Submit review → reload /products → confirm star + decimal hiển thị |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
