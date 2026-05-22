---
phase: 27
slug: real-email-smtp
status: approved
nyquist_compliant: true
wave_0_complete: false
created: 2026-05-22
---

# Phase 27 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test (backend); Testcontainers (RabbitMQ + PostgreSQL) |
| **Config file** | per-service `pom.xml` (spring-boot-starter-test already present) |
| **Quick run command** | `mvn -q test` (per affected service module) |
| **Full suite command** | `mvn -q verify` (notification-service + user-service + order-service) |
| **Estimated runtime** | ~120 seconds |

---

## Sampling Rate

- **After every task commit:** Run `mvn -q test` in the affected service module
- **After every plan wave:** Run `mvn -q verify` across affected modules
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 120 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 27-01-T1 | 27-01 | 1 | MAIL-03/04 | T-27-01 | Polymorphic event payload deserialize đúng | unit | `mvn -q compile` | ❌ W0 | ⬜ pending |
| 27-01-T2 | 27-01 | 1 | MAIL-04 | T-27-02 | publishOrderStatusChanged afterCommit | unit | `mvn -q compile` | ❌ W0 | ⬜ pending |
| 27-01-T3 | 27-01 | 1 | MAIL-03/04 | — | OrderPlacedPayload có customerEmail | unit | `mvn -q compile` | ❌ W0 | ⬜ pending |
| 27-02-T1 | 27-02 | 1 | MAIL-02 | T-27-03/04/05 | token single-use + expiry enforced | unit | `mvn -q test -Dtest=VerificationTokenServiceTest` | ❌ W0 | ⬜ pending |
| 27-02-T2 | 27-02 | 1 | MAIL-02 | T-27-06 | user-service publish lên user.events | unit | `mvn -q compile` | ❌ W0 | ⬜ pending |
| 27-03-T1 | 27-03 | 2 | MAIL-01 | T-27-07/08 | SMTP creds env-only, graceful degradation | unit | `mvn -q test -Dtest=EmailSenderTest` | ❌ W0 | ⬜ pending |
| 27-03-T3 | 27-03 | 2 | MAIL-03/04 | T-27-09 | dispatch_log SENT/FAILED/SKIPPED | unit | `mvn -q test -Dtest=NotificationDispatchServiceTest` | ❌ W0 | ⬜ pending |
| 27-04-T2 | 27-04 | 2 | MAIL-02 | T-27-10/11 | verify/forgot/reset; anti-enumeration | integration | `mvn -q test -Dtest=AuthControllerIT` | ❌ W0 | ⬜ pending |
| 27-05-T1 | 27-05 | 3 | MAIL-02/03/04 | T-27-15 | listener idempotent, status branch | integration | `mvn -q test -Dtest=OrderStatusChangedListenerIT,UserEventListenerIT` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky.*

---

## Wave 0 Requirements

- [ ] `spring-boot-starter-mail` dependency added to `notification-service/pom.xml` (Plan 27-03 Task 1)
- [ ] `spring-boot-starter-amqp` dependency added to `user-service/pom.xml` (Plan 27-02 Task 0)
- [ ] `EmailSenderTest.java` created — covers MAIL-01 (Plan 27-03 Task 1)
- [ ] `VerificationTokenServiceTest.java` created — covers MAIL-02 (Plan 27-02 Task 1)
- [ ] `NotificationDispatchServiceTest.java` created — covers MAIL-03/04 (Plan 27-03 Task 3)
- [ ] `AuthControllerIT.java` created — covers MAIL-02 endpoints (Plan 27-04 Task 2)
- [ ] `OrderStatusChangedListenerIT.java` + `UserEventListenerIT.java` created — covers MAIL-03/04 (Plan 27-05 Task 1)

*Backend test infrastructure exists (spring-boot-starter-test, Testcontainers per Phase 23) — Wave 0 adds mail deps + new test files inline với từng task tdd.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Email thật tới hộp thư người dùng | MAIL-02, MAIL-03, MAIL-04 | Cần inbox Gmail thật + App Password | Đăng ký → kiểm tra inbox nhận mail xác minh; bấm link → verified. Đặt đơn → nhận mail xác nhận. Admin đổi trạng thái → nhận mail cập nhật. |
| Render HTML email tiếng Việt | MAIL-03, MAIL-04 | Cần mắt người xác nhận layout | Mở email trong Gmail, kiểm tra subject + body tiếng Việt + đủ dữ liệu |
| 3 trang FE auth | MAIL-02 | UI flow cần thao tác trình duyệt | Checkpoint Plan 27-05 Task 3 |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 120s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved
