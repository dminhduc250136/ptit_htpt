---
phase: 26
slug: vnpay-payment-integration
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-22
---

# Phase 26 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test (backend, Maven Surefire/Failsafe); Playwright (frontend E2E) |
| **Config file** | `sources/backend/*/pom.xml`; `sources/frontend/playwright.config.ts` |
| **Quick run command** | `mvn -q test -pl payment-service` (per-service unit/slice tests) |
| **Full suite command** | `mvn -q verify` (payment-service + order-service IT) |
| **Estimated runtime** | ~120 seconds |

---

## Sampling Rate

- **After every task commit:** Run `mvn -q test -pl <service>` cho service vừa sửa
- **After every plan wave:** Run `mvn -q verify` cho các service liên quan
- **Before `/gsd-verify-work`:** Full suite phải xanh
- **Max feedback latency:** 120 seconds

---

## Per-Task Verification Map

> Hoàn thiện bởi gsd-planner sau khi chốt task breakdown. Mỗi task ánh xạ tới ≥1 yêu cầu PAY-01..PAY-04 và có lệnh verify tự động hoặc Wave 0 dependency.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 26-01-01 | 01 | 1 | PAY-03 | T-26-01 | HMAC SHA512 verify đúng, loại vnp_SecureHash khỏi tập ký | unit | `mvn -q test -pl payment-service` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Test class `VnPaySignatureTest` — verify HMAC SHA512 sign/verify round-trip + reject tampered hash (PAY-03)
- [ ] Test class `VnPayIpnControllerIT` — IPN idempotency + amount mismatch reject + signature invalid reject (PAY-03)
- [ ] Test class cho order-service consume PaymentSucceeded/PaymentFailed → cập nhật payment_status (PAY-04)
- [ ] Playwright spec stub `vnpay-payment.spec.ts` — checkout chọn VNPay + trang kết quả (PAY-01, PAY-02)

*Existing infrastructure (JUnit 5, Spring Boot Test, Playwright) đã có — không cần cài framework mới.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Redirect thật sang cổng VNPay sandbox + thanh toán trên sandbox | PAY-01 | Cần tương tác với cổng VNPay sandbox bên ngoài | Chọn VNPay tại `/checkout`, đặt hàng, hoàn tất thanh toán trên trang sandbox |
| IPN callback server-to-server từ VNPay tới localhost | PAY-03 | VNPay gọi từ internet; demo local cần tunnel hoặc curl mô phỏng | Dùng ngrok/cloudflared hoặc script curl mô phỏng IPN payload có chữ ký hợp lệ |

*Các kiểm thử logic ký/verify, idempotency, cập nhật trạng thái đều có verify tự động.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 120s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
