---
phase: 26
slug: vnpay-payment-integration
status: draft
nyquist_compliant: true
wave_0_complete: true
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
| 26-01-01 | 01 | 1 | PAY-01, PAY-03 | T-26-01, T-26-05 | HMAC SHA512 ký/verify đúng, loại vnp_SecureHash + vnp_SecureHashType khỏi tập ký, vnp_Amount × 100, secret không hardcode | unit | `mvn -q test -pl payment-service -Dtest=VNPaySignatureTest` | ❌ W0 (`VNPaySignatureTest.java`) | ⬜ pending |
| 26-01-02 | 01 | 1 | PAY-03 | T-26-01, T-26-02, T-26-03, T-26-04 | IPN verify chữ ký + so khớp amount + idempotent terminal + return URL chỉ hiển thị; JSON thuần {RspCode,Message} | integration | `mvn -q test -pl payment-service -Dtest=VNPayIpnControllerIT` | ❌ W0 (`VNPayIpnControllerIT.java`) | ⬜ pending |
| 26-01-03 | 01 | 1 | PAY-01 | T-26-05, T-26-06 | POST /payments/sessions provider=VNPAY trả paymentUrl; gateway whitelist IPN/return không lộ JWT | integration | `mvn -q test -pl payment-service` | ❌ W0 (`application-test.yml`) | ⬜ pending |
| 26-02-01 | 02 | 1 | PAY-04 | T-26-07 | V6 migration thêm payment_status/vnp_transaction_no + processed_events; Flyway validate qua ddl-auto=validate | integration | `mvn -q test -pl order-service -Dtest=OrderMapper*` | ❌ W0 (validate gián tiếp qua Spring context) | ⬜ pending |
| 26-02-02 | 02 | 1 | PAY-04 | T-26-08 | OrderEntity/OrderDto/OrderMapper expose payment fields; processed_events infra port | unit | `mvn -q test -pl order-service -Dtest=OrderMapper*` | ❌ W0 (`OrderMapper*` test) | ⬜ pending |
| 26-03-01 | 03 | 2 | PAY-01 | T-26-10, T-26-11 | Nhánh VNPAY tạo session + trì hoãn OrderPlaced (D-09); COD publish ngay (D-10) | integration | `mvn -q test -pl order-service -Dtest=OrderCrudServiceVNPayIT` | ❌ W0 (`OrderCrudServiceVNPayIT.java`) | ⬜ pending |
| 26-03-02 | 03 | 2 | PAY-03, PAY-04 | T-26-09, T-26-12 | Consume PaymentSucceeded/Failed cập nhật payment_status; idempotent processed_events; DLQ classification | integration | `mvn -q test -pl order-service -Dtest=PaymentEventListenerIT` | ❌ W0 (`PaymentEventListenerIT.java`) | ⬜ pending |
| 26-04-01 | 04 | 3 | PAY-01 | T-26-13 | Selector VNPay + redirect window.location paymentUrl; type Order mở rộng | type-check | `cd sources/frontend && npx tsc --noEmit` | ✅ tooling sẵn có | ⬜ pending |
| 26-04-02 | 04 | 3 | PAY-02, PAY-04 | T-26-13, T-26-14 | Trang /checkout/result resolve orderId qua return endpoint + poll payment_status 3s×5 (5 trạng thái); order display payment fields | type-check + smoke | `cd sources/frontend && npx tsc --noEmit && npx playwright test vnpay-payment --list` | ❌ W0 (`vnpay-payment.spec.ts`) | ⬜ pending |

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

- [x] All tasks have `<automated>` verify or Wave 0 dependencies — cả 9 task có lệnh tự động
- [x] Sampling continuity: no 3 consecutive tasks without automated verify — mỗi task có verify riêng
- [x] Wave 0 covers all MISSING references — 6 test artifact liệt kê §Wave 0 Requirements
- [x] No watch-mode flags
- [x] Feedback latency < 120s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved
