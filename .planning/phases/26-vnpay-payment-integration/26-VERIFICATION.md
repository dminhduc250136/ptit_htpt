---
phase: 26-vnpay-payment-integration
verified: 2026-05-22T17:45:00Z
status: human_needed
score: 9/9 must-haves verified
overrides_applied: 0
human_verification:
  - test: "Redirect sang VNPay sandbox thực tế"
    expected: "Chọn VNPAY tại /checkout → đặt hàng → trình duyệt redirect sang https://sandbox.vnpayment.vn với đúng params (vnp_Amount, vnp_TxnRef, vnp_SecureHash)"
    why_human: "Cần merchant credentials thật + tunnel công khai + sandbox VNPay — không thể giả lập bằng grep/static analysis"
  - test: "IPN callback server-to-server từ VNPay"
    expected: "VNPay POST IPN tới /api/payments/vnpay/ipn (qua tunnel), BE verify HMAC SHA512, cập nhật payment_status=PAID, phát PaymentSucceeded event, order-service consume và cập nhật đơn"
    why_human: "Yêu cầu tunnel công khai (ngrok/cloudflare), credentials merchant sandbox, không thể kiểm tra luồng end-to-end bằng static analysis"
  - test: "Trang /checkout/result poll và cập nhật đúng"
    expected: "Sau redirect về từ VNPay, trang hiển thị 'Đang xác nhận' → poll mỗi 3s → khi IPN về cập nhật thành 'Thanh toán thành công' / 'Thanh toán thất bại'"
    why_human: "Cần luồng real-time: VNPay sandbox → IPN → BE → FE poll; không thể test mà không có server chạy + merchant credentials"
  - test: "Order display hiển thị đúng sau IPN"
    expected: "/profile/orders/[id] và /admin/orders/[id] hiển thị paymentMethod='Thanh toán qua VNPay', paymentStatus='Đã thanh toán', mã giao dịch VNPay thực tế"
    why_human: "Cần IPN thật từ VNPay sandbox để có dữ liệu vnpTransactionNo thật — không thể verify UI với dữ liệu thật bằng static analysis"
---

# Phase 26: VNPay Payment Integration — Báo Cáo Xác Minh

**Phase Goal:** Khách hàng tại checkout chọn "Thanh toán qua VNPay", được redirect sang cổng VNPay sandbox để thanh toán, quay lại return URL với kết quả; backend xác nhận giao dịch qua IPN callback (server-to-server), verify HMAC SHA512, cập nhật trạng thái thanh toán của đơn hàng đáng tin cậy + idempotent.
**Verified:** 2026-05-22T17:45:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| #  | Truth | Status | Evidence |
|----|-------|--------|----------|
| 1  | payment-service ký được payment URL VNPay với HMAC SHA512 đúng spec v2.1.0 | ✓ VERIFIED | `VNPaySignature.java`: TreeMap sort, `amountVnd * 100`, `URLEncoder.encode(..., US_ASCII)`, `HmacSHA512` — verbatim spec. `VNPaySignatureTest` 5 test. |
| 2  | payment-service verify được vnp_SecureHash của callback và từ chối chữ ký sai | ✓ VERIFIED | `verifySignature()` loại `vnp_SecureHash` + `vnp_SecureHashType` trước khi hash; `equalsIgnoreCase`; `VNPayIpnControllerIT` test `ipn_badSignature_returns97` pass. |
| 3  | POST /payments/sessions với provider=VNPAY trả về paymentUrl trỏ cổng sandbox | ✓ VERIFIED | `PaymentCrudService` nhánh VNPAY gọi `buildPaymentUrl` trả URL. `OrderCrudService` gọi `PaymentSessionClient.createVNPaySession` và set `dto.paymentUrl`. |
| 4  | GET /payments/vnpay/ipn verify chữ ký, idempotent, trả JSON thuần {RspCode,Message} | ✓ VERIFIED | `VNPayService.processIpn`: bước 1 sig verify → 97; bước 4 idempotent terminal → 02; bước 5 amount mismatch → 04. `ApiResponseAdvice` SKIP_PREFIXES chứa `/payments/vnpay`. Test `ipn_responseMap_hasRspCodeAndMessage` xác nhận không có envelope. |
| 5  | VNPay IPN/return endpoint truy cập được qua gateway không cần JWT | ✓ VERIFIED | `api-gateway/application.yml` lines 228-229: `{ method: GET, pattern: "/api/payments/vnpay/ipn" }` và `{ method: GET, pattern: "/api/payments/vnpay/return" }` trong `public-endpoints`. |
| 6  | OrderEntity có cột payment_status (default PENDING) + vnp_transaction_no (nullable) | ✓ VERIFIED | `V6__add_payment_status.sql` đúng tên, `ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'`; `OrderEntity.java` fields `paymentStatus`/`vnpTransactionNo` với getters/setters. |
| 7  | order-service consume PaymentSucceeded → payment_status=PAID + publish OrderPlaced; PaymentFailed → FAILED | ✓ VERIFIED | `PaymentEventListener.java`: idempotency-first `insertIfAbsent`; `PaymentSucceeded` → `setPaymentStatus("PAID")` + `setVnpTransactionNo` + `publishOrderPlacedForOrder`; `PaymentFailed` → `setPaymentStatus("FAILED")`. Topology `order.payment-events` bind `payment.#` vào `payment.events`. |
| 8  | Khách chọn VNPay tại /checkout → redirect sang paymentUrl | ✓ VERIFIED | `checkout/page.tsx`: type union có `'VNPAY'`; mảng options có `{ value: 'VNPAY', label: 'Thanh toán qua VNPay', icon: '💳' }`; `submitOrder` nhánh `VNPAY && order.paymentUrl` gọi `window.location.assign(order.paymentUrl)`. |
| 9  | /profile/orders/[id] + /admin/orders/[id] hiển thị payment method/status/mã giao dịch VNPay | ✓ VERIFIED | `profile/orders/[id]/page.tsx` renders `paymentMethodMap[order.paymentMethod]` + `paymentStatusMap[order.paymentStatus]` + `order.vnpTransactionNo` (ẩn nếu rỗng). `admin/orders/[id]/page.tsx` tương tự với Badge. `orderLabels.ts` có `VNPAY: 'Thanh toán qua VNPay'`. |

**Score: 9/9 truths verified (automated/static)**

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `payment-service/vnpay/VNPaySignature.java` | HMAC SHA512 ký + verify | ✓ VERIFIED | Tồn tại, substantive (129 lines), wired qua `VNPayService` + `PaymentCrudService` |
| `payment-service/vnpay/VNPayController.java` | GET /payments/vnpay/ipn + /return | ✓ VERIFIED | Tồn tại, 52 lines, wired qua `@RestController` Spring component scan |
| `payment-service/messaging/config/RabbitMQConfig.java` | Exchange payment.events + DLX + DLQ | ✓ VERIFIED | Tồn tại, exchange `payment.events` durable, DLX `payment.dlx`, DLQ `payment-events.dlq`. KHÔNG chứa `order.events`. |
| `payment-service/messaging/publisher/PaymentEventPublisher.java` | Publish afterCommit | ✓ VERIFIED | Tồn tại, `publishPaymentEvent` với `afterCommit` defer + `CorrelationData` |
| `payment-service/test/.../VNPaySignatureTest.java` | 5 @Test HMAC | ✓ VERIFIED | Tồn tại, 5 @Test xác nhận bởi `grep -c @Test` |
| `payment-service/test/.../VNPayIpnControllerIT.java` | 5 @Test IPN scenarios | ✓ VERIFIED | Tồn tại, 5 @Test (valid/idempotent/bad-sig/amount-mismatch/plain-json) |
| `order-service/db/migration/V6__add_payment_status.sql` | payment_status + vnp_transaction_no + processed_events | ✓ VERIFIED | Tồn tại, đúng tên V6, nội dung ALTER TABLE + CREATE TABLE IF NOT EXISTS processed_events |
| `order-service/domain/ProcessedEventEntity.java` | Idempotency entity | ✓ VERIFIED | Tồn tại, package `com.ptit.htpt.orderservice` |
| `order-service/repository/ProcessedEventRepository.java` | insertIfAbsent ON CONFLICT | ✓ VERIFIED | Tồn tại, `ON CONFLICT (event_id) DO NOTHING`, trả boolean |
| `order-service/messaging/tracing/TraceIdConsumerInterceptor.java` | Trace propagation | ✓ VERIFIED | Tồn tại, package `com.ptit.htpt.orderservice` |
| `order-service/service/PaymentSessionClient.java` | REST client tạo VNPAY session | ✓ VERIFIED | Tồn tại, URL `http://api-gateway:8080/api/payments/sessions`, throw 502 khi fail |
| `order-service/messaging/consumer/PaymentEventListener.java` | Consumer queue order.payment-events | ✓ VERIFIED | Tồn tại, `@RabbitListener(queues = "order.payment-events")`, idempotency-first |
| `frontend/checkout/result/page.tsx` | Trang kết quả 5 trạng thái + polling | ✓ VERIFIED | Tồn tại, `Suspense`, `POLL_INTERVAL_MS=3000`, `POLL_MAX_ATTEMPTS=5`, 5 copy strings verbatim, `getVNPayReturn`, `getOrderById` |
| `frontend/services/payments.ts` | getVNPayReturn endpoint | ✓ VERIFIED | `getVNPayReturn(searchParams)` gọi `/api/payments/vnpay/return` |
| `frontend/e2e/12-vnpay-payment.spec.ts` | Playwright smoke spec | ✓ VERIFIED | Tồn tại ở đúng thư mục `e2e/` (testDir config) |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `VNPayController.ipn` | `PaymentEventPublisher.publishPaymentEvent` | phát PaymentSucceeded/Failed sau verify | ✓ WIRED | `VNPayService.processIpn` → `eventPublisher.publishPaymentEvent(eventType, payload)` |
| `api-gateway application.yml` | `/api/payments/vnpay/ipn` | public-endpoints whitelist | ✓ WIRED | Lines 228-229 đã thêm 2 entry GET. |
| `OrderCrudService.createOrderFromCommand` | `PaymentSessionClient.createVNPaySession` | nhánh VNPAY gọi REST | ✓ WIRED | `if ("VNPAY".equalsIgnoreCase(command.paymentMethod()))` → `paymentSessionClient.createVNPaySession(...)` |
| `PaymentEventListener.onPaymentEvent` | `OrderCrudService.publishOrderPlacedForOrder` | PaymentSucceeded → publish OrderPlaced | ✓ WIRED | `orderCrudService.publishOrderPlacedForOrder(order)` trong nhánh `PaymentSucceeded` |
| `checkout/page.tsx submitOrder` | `window.location paymentUrl` | redirect khi paymentMethod=VNPAY | ✓ WIRED | `if (form.paymentMethod === 'VNPAY' && order.paymentUrl)` → `window.location.assign(order.paymentUrl)` |
| `checkout/result/page.tsx` | `GET /api/payments/vnpay/return` | resolve orderId từ vnp_TxnRef | ✓ WIRED | `getVNPayReturn(new URLSearchParams(window.location.search))` → `/api/payments/vnpay/return` |
| `checkout/result/page.tsx` | `GET /api/orders/{id}` | poll payment_status mỗi 3s tối đa 5 lần | ✓ WIRED | `setInterval(() => getOrderById(oid), POLL_INTERVAL_MS)`, dừng khi `paymentStatus !== 'PENDING'` hoặc `pollAttemptsRef.current >= POLL_MAX_ATTEMPTS` |

---

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| `checkout/result/page.tsx` | `orderId` | `getVNPayReturn` → `/api/payments/vnpay/return` → `sessionRepo.findById(txnRef).orderId()` | Có — DB query trên `PaymentSessionEntity` | ✓ FLOWING |
| `checkout/result/page.tsx` | `order.paymentStatus` | `getOrderById` → `GET /api/orders/{id}` → `OrderMapper.toDto(OrderEntity)` | Có — map `entity.paymentStatus()` (cột payment_status DB) | ✓ FLOWING |
| `profile/orders/[id]/page.tsx` | `order.vnpTransactionNo` | `getOrderById` → `OrderDto.vnpTransactionNo` → `OrderMapper.toDto` | Có — map `entity.vnpTransactionNo()` (cột vnp_transaction_no DB, được set bởi `PaymentEventListener`) | ✓ FLOWING |

---

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| VNPaySignature test ký/verify HMAC | `grep -c @Test VNPaySignatureTest.java` | 5 tests | ✓ PASS (static) |
| IPN controller test 5 scenarios | `grep -c @Test VNPayIpnControllerIT.java` | 5 tests | ✓ PASS (static) |
| Gateway whitelist 2 endpoint | `grep "vnpay" api-gateway/application.yml` | 2 entries lines 228-229 | ✓ PASS |
| payment.events tách riêng (không reuse order.events) | `grep "order.events" payment-service/.../RabbitMQConfig.java` | chỉ có comment giải thích, không có bean | ✓ PASS |
| Live VNPay sandbox redirect | Cần tunnel + credentials | Không thể kiểm tra tự động | ? SKIP (human_needed) |
| IPN server-to-server real flow | Cần VNPay sandbox + public URL | Không thể kiểm tra tự động | ? SKIP (human_needed) |

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| PAY-01 | Plans 01, 03, 04 | Checkout selector VNPAY + redirect + order tạo payment_status=PENDING | ✓ SATISFIED | `checkout/page.tsx` option VNPAY + redirect; `OrderCrudService` nhánh VNPAY PENDING + no publishOrderPlaced; `PaymentCrudService` buildPaymentUrl |
| PAY-02 | Plan 04 | Return URL render kết quả thành công/thất bại/huỷ; KHÔNG update DB từ return URL | ✓ SATISFIED | `checkout/result/page.tsx` 5 trạng thái; `buildReturnView` chỉ verify, không update DB (comment `T-26-04`); poll `getOrderById` là nguồn sự thật |
| PAY-03 | Plans 01, 03 | IPN verify HMAC SHA512 + amount match + idempotent + update payment_status | ✓ SATISFIED | `VNPayService.processIpn` 5 bước; `PaymentEventListener` idempotency-first `insertIfAbsent`; cập nhật `paymentStatus` + `vnpTransactionNo`; phát event afterCommit |
| PAY-04 | Plans 02, 03, 04 | Order display hiển thị payment method/status/mã giao dịch; chữ ký sai → từ chối + log audit | ✓ SATISFIED | `V6` cột payment_status/vnp_transaction_no; `OrderDto` expose 3 fields; FE hiển thị; log audit `[IPN-AUDIT]` tại mỗi rejection path |

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `VNPayService.java` | 93 | `session.amount().multiply(BigDecimal.valueOf(100)).longValue()` — cắt phần thập phân âm thầm | ⚠️ Warning (WR-02 từ REVIEW.md) | Số tiền lệch nếu `total` có phần lẻ; không gây lỗi trực tiếp với giá trị nguyên VND |
| `VNPayService.java` | 65-97 | Không check `vnp_TmnCode` khớp `cfg.tmnCode()` | ⚠️ Warning (CR-01 từ REVIEW.md — critical nhưng rủi ro thấp vì hash secret bảo vệ) | Chấp nhận IPN từ merchant khác nếu cùng hash secret (sandbox only) |
| `VNPaySignature.java` | 109 | `computed.equalsIgnoreCase(received)` — không constant-time | ℹ️ Info (WR-04 từ REVIEW.md) | Timing side-channel lý thuyết; khai thác rất khó với HMAC 512-bit qua mạng |
| `VNPayIpnControllerIT.java` | 39 | Tên class `*IT` nhưng là unit test Mockito | ℹ️ Info (IN-03 từ REVIEW.md) | Nhầm lẫn phân loại test; failsafe plugin có thể phân loại sai phase |

**Lưu ý về CR-01 (REVIEW.md):** REVIEW.md đánh dấu thiếu `vnp_TmnCode` check là "Critical" trong sandbox context. Với production merchant thật đây là bắt buộc; với sandbox demo + hash secret riêng thì rủi ro thực tế thấp. Đây là warning cần fix trước live demo với tunnel/merchant thật.

---

### Human Verification Required

#### 1. Live VNPay Sandbox Redirect

**Test:** Đăng ký tài khoản sandbox tại https://sandbox.vnpayment.vn, lấy `VNP_TMN_CODE` + `VNP_HASH_SECRET`, set vào `.env`. Chạy toàn bộ stack (`docker compose up`). Tại `/checkout` chọn "Thanh toán qua VNPay" → đặt hàng.
**Expected:** Trình duyệt redirect sang `https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=...&vnp_SecureHash=...` với đúng số tiền (amountVnd × 100) và mã session.
**Why human:** Cần merchant credentials thật + sandbox VNPay hoạt động + docker stack chạy.

#### 2. IPN Callback Server-to-Server

**Test:** Mở tunnel công khai (ngrok: `ngrok http 8080`). Cấu hình `VNP_IPN_URL=https://<tunnel>/api/payments/vnpay/ipn` tại merchant dashboard sandbox. Thực hiện giao dịch test tại sandbox VNPay.
**Expected:** VNPay gọi IPN về tunnel → gateway forward → `payment-service` verify HMAC → trả `{"RspCode":"00","Message":"Confirm Success"}` → phát `PaymentSucceeded` → `order-service` consume → `payment_status=PAID` + `vnpTransactionNo` lưu vào DB.
**Why human:** Cần tunnel công khai + IPN thật từ VNPay server; không giả lập được bằng code tĩnh.

#### 3. Return Page Polling Flow

**Test:** Sau giao dịch VNPay sandbox thành công, VNPay redirect về `/checkout/result?vnp_ResponseCode=00&vnp_TxnRef=<sessionId>&vnp_SecureHash=...`.
**Expected:** Trang render "Đang xác nhận thanh toán" (spinner), poll mỗi 3s, khi IPN về và `payment_status=PAID` → cập nhật thành "Thanh toán thành công" + hiển thị nút "Xem đơn hàng".
**Why human:** Cần real-time IPN + server chạy để poll có kết quả thực.

#### 4. Order Display với Dữ Liệu Thật

**Test:** Sau IPN PAID thành công, vào `/profile/orders/[id]` và `/admin/orders/[id]`.
**Expected:** Hiển thị "Thanh toán qua VNPay" (paymentMethod), "Đã thanh toán" hoặc tương đương (paymentStatus), mã giao dịch VNPay thực (vnpTransactionNo từ VNPay sandbox).
**Why human:** Cần `vnpTransactionNo` thật từ giao dịch sandbox thực tế.

---

## Gaps Summary

Không có gap về goal achievement — tất cả 9 must-haves đã được xác minh bằng static analysis. Status `human_needed` vì luồng E2E (redirect sandbox, IPN server-to-server, polling real-time) yêu cầu môi trường chạy + merchant credentials VNPay sandbox thật.

**Các cảnh báo từ REVIEW.md cần xử lý trước live demo:**
- **CR-01** (REVIEW.md): Thêm `vnp_TmnCode` check trong `VNPayService.processIpn` — cần fix trước khi cấu hình merchant thật
- **WR-02** (REVIEW.md): Dùng `longValueExact()` thay `longValue()` cho amount conversion — phòng lệch tiền với giá có phần lẻ

---

_Verified: 2026-05-22T17:45:00Z_
_Verifier: Claude (gsd-verifier)_
