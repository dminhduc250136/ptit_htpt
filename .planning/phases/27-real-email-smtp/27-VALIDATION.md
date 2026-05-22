---
phase: 27
slug: real-email-smtp
status: draft
nyquist_compliant: false
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
| TBD | TBD | TBD | MAIL-01 | — | SMTP creds env-only, no crash on missing config | integration | `mvn -q test` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | MAIL-02 | — | verification token single-use + expiry enforced | integration | `mvn -q test` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | MAIL-03 | — | OrderPlaced → email sent, idempotent | integration | `mvn -q test` | ❌ W0 | ⬜ pending |
| TBD | TBD | TBD | MAIL-04 | — | OrderStatusChanged → email async, non-blocking | integration | `mvn -q test` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky — planner fills concrete Task IDs/Plans/Waves.*

---

## Wave 0 Requirements

- [ ] `spring-boot-starter-mail` dependency added to `notification-service/pom.xml`
- [ ] `spring-boot-starter-amqp` dependency added to `user-service/pom.xml`
- [ ] Test fixtures for SMTP (GreenMail or mock JavaMailSender) in notification-service test scope
- [ ] Testcontainers RabbitMQ fixture extended for `user.events` topology

*Backend test infrastructure exists (spring-boot-starter-test, Testcontainers per Phase 23) — Wave 0 only adds mail deps + mail test fixtures.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Email thật tới hộp thư người dùng | MAIL-02, MAIL-03, MAIL-04 | Cần inbox Gmail thật + App Password — không tự động hoá được trong CI | Đăng ký tài khoản → kiểm tra inbox nhận mail xác minh; bấm link → tài khoản verified. Đặt đơn → nhận mail xác nhận. Admin đổi trạng thái → nhận mail cập nhật. |
| Render HTML email hiển thị đúng tiếng Việt | MAIL-03, MAIL-04 | Cần mắt người xác nhận layout HTML trong mail client | Mở email trong Gmail, kiểm tra subject + body tiếng Việt + đủ dữ liệu (mã đơn, sản phẩm, tổng tiền) |
| Trang `/verify-email`, `/reset-password`, `/forgot-password` | MAIL-02 | UI flow cần thao tác trình duyệt | Mở link từ email, xác nhận trang hiển thị trạng thái đúng (thành công / hết hạn) |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 120s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
