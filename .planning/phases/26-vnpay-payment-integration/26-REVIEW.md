---
phase: 26-vnpay-payment-integration
reviewed: 2026-05-22T00:00:00Z
depth: standard
files_reviewed: 18
files_reviewed_list:
  - docker-compose.yml
  - sources/backend/api-gateway/src/main/resources/application.yml
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/ProcessedEventEntity.java
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/messaging/config/RabbitMQConfig.java
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/messaging/consumer/PaymentEventListener.java
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/repository/ProcessedEventRepository.java
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderCrudService.java
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/PaymentSessionClient.java
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/OrderController.java
  - sources/backend/order-service/src/main/resources/db/migration/V6__add_payment_status.sql
  - sources/backend/payment-service/src/main/java/com/ptit/htpt/paymentservice/api/ApiResponseAdvice.java
  - sources/backend/payment-service/src/main/java/com/ptit/htpt/paymentservice/messaging/config/RabbitMQConfig.java
  - sources/backend/payment-service/src/main/java/com/ptit/htpt/paymentservice/messaging/publisher/PaymentEventPublisher.java
  - sources/backend/payment-service/src/main/java/com/ptit/htpt/paymentservice/service/PaymentCrudService.java
  - sources/backend/payment-service/src/main/java/com/ptit/htpt/paymentservice/vnpay/VNPayConfig.java
  - sources/backend/payment-service/src/main/java/com/ptit/htpt/paymentservice/vnpay/VNPayController.java
  - sources/backend/payment-service/src/main/java/com/ptit/htpt/paymentservice/vnpay/VNPayService.java
  - sources/backend/payment-service/src/main/java/com/ptit/htpt/paymentservice/vnpay/VNPaySignature.java
findings:
  critical: 1
  warning: 6
  info: 4
  total: 11
status: issues_found
---

# Phase 26: Code Review Report

**Reviewed:** 2026-05-22
**Depth:** standard
**Files Reviewed:** 18
**Status:** issues_found

## Summary

Phase 26 tích hợp VNPay với kiến trúc tốt: IPN là nguồn sự thật, return URL chỉ hiển thị,
HMAC SHA512 verify loại đúng `vnp_SecureHash`/`vnp_SecureHashType`, idempotency consumer dựa
trên `processed_events` + `ON CONFLICT DO NOTHING`, exchange `payment.events` tách riêng khỏi
`order.events`. Test coverage cho chữ ký và IPN flow hợp lý.

Tuy nhiên có một lỗ hổng nghiêm trọng: khi `vnp_Amount` của VNPay là một con số hợp lệ về định
dạng nhưng `session.amount()` có phần thập phân, hoặc khi `vnp_TmnCode` không được kiểm tra,
việc xử lý IPN có thể chấp nhận giao dịch không thuộc merchant này. Ngoài ra IPN không kiểm tra
`vnp_TmnCode` — đây là điểm cần xử lý vì endpoint public. Một số vấn đề logic về trạng thái
terminal (FAILED chặn retry hợp lệ về sau) và truncation số tiền cũng cần lưu ý.

## Critical Issues

### CR-01: IPN không xác thực `vnp_TmnCode` — chấp nhận giao dịch của merchant khác

**File:** `sources/backend/payment-service/src/main/java/com/ptit/htpt/paymentservice/vnpay/VNPayService.java:65-97`
**Issue:** `processIpn` verify chữ ký HMAC nhưng KHÔNG so khớp `vnp_TmnCode` trong params với
`cfg.tmnCode()`. Chữ ký được tính bằng `hashSecret` — nếu hash secret đúng thì `vnp_TmnCode`
tự động hợp lệ, nên trong điều kiện bình thường rủi ro thấp. Nhưng đây là endpoint public,
và spec VNPay yêu cầu merchant tự verify `vnp_TmnCode` khớp với terminal của mình trước khi
xác nhận. Thiếu bước này khiến IPN xử lý mọi callback có chữ ký đúng mà không ràng buộc đúng
terminal — nếu hash secret bị dùng chung/cấu hình sai giữa nhiều môi trường, một IPN sandbox
có thể được chấp nhận nhầm. Đồng thời `expectedAmount` so khớp dựa trên `session.amount()`
nhưng không kiểm tra session có thực sự thuộc provider `VNPAY` hay không (`session.provider()`).
**Fix:**
```java
// Sau khi verify chữ ký, trước Bước 2:
String tmnCode = params.get("vnp_TmnCode");
if (tmnCode == null || !tmnCode.equals(cfg.tmnCode())) {
  log.warn("[IPN-AUDIT] TmnCode mismatch — got={} txnRef={}", tmnCode, params.get("vnp_TxnRef"));
  return Map.of("RspCode", "97", "Message", "Invalid merchant");
}
// Sau khi load session:
if (!"VNPAY".equalsIgnoreCase(session.provider())) {
  log.warn("[IPN-AUDIT] Session provider không phải VNPAY — txnRef={}", txnRef);
  return Map.of("RspCode", "01", "Message", "Order not found");
}
```
VNPaySignature cần inject `cfg` (đã có) — bổ sung getter hoặc truyền `cfg.tmnCode()` vào service.

## Warnings

### WR-01: Trạng thái FAILED là terminal — chặn IPN thành công hợp lệ về sau

**File:** `sources/backend/payment-service/src/main/java/com/ptit/htpt/paymentservice/vnpay/VNPayService.java:99-108`
**Issue:** Idempotent check coi cả `PAID` và `FAILED` là terminal → trả `RspCode 02`. Với cùng
một `vnp_TxnRef` (= sessionId), nếu IPN đầu tiên báo FAILED rồi VNPay gửi lại IPN khác báo
thành công (hiếm nhưng xảy ra khi giao dịch được đối soát lại), hệ thống bỏ qua và đơn mãi
FAILED. RESEARCH §Pitfall 6 nói mỗi retry dùng `sessionId` mới, nên thực tế mỗi session chỉ
nhận một kết quả — nhưng logic hiện tại giả định điều đó mà không nêu rõ. Rủi ro thấp nhưng
nên log audit rõ ràng khi nhận IPN trên transaction đã FAILED để phát hiện bất thường.
**Fix:** Tách nhánh: nếu `PAID` → 02 (đúng). Nếu `FAILED` và IPN mới báo `responseCode=00` →
log `[IPN-AUDIT] FAILED→success conflict` và quyết định chính sách (giữ FAILED + cảnh báo, hoặc
cho phép chuyển PAID). Tối thiểu thêm log audit để không âm thầm nuốt.

### WR-02: `BigDecimal.longValue()` cắt phần thập phân và có thể tràn âm thầm

**File:** `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderCrudService.java:206`, `sources/backend/payment-service/src/main/java/com/ptit/htpt/paymentservice/service/PaymentCrudService.java:67`, `sources/backend/payment-service/src/main/java/com/ptit/htpt/paymentservice/vnpay/VNPayService.java:93`
**Issue:** `saved.total().longValue()`, `saved.amount().longValue()` và
`session.amount().multiply(BigDecimal.valueOf(100)).longValue()` dùng `longValue()` — method này
cắt phần thập phân và KHÔNG báo lỗi khi tràn. Nếu coupon math hoặc dữ liệu sinh ra `total` có
phần lẻ (vd 99999.50), số tiền gửi lên VNPay và số tiền so khớp ở IPN sẽ lệch nhau âm thầm
hoặc lệch so với số tiền thật khách phải trả. Đặc biệt nguy hiểm vì IPN so khớp
`expectedAmount` cũng dùng cùng phép cắt — hai chỗ cùng sai sẽ "khớp" nhau nhưng sai so với
thực tế.
**Fix:** Dùng `longValueExact()` để ném `ArithmeticException` khi mất dữ liệu, và chuẩn hóa
`total`/`amount` về số nguyên VND (scale 0) tại điểm tạo order/session:
```java
long amountVnd = saved.total().setScale(0, RoundingMode.HALF_UP).longValueExact();
```

### WR-03: `clientIp` mặc định "127.0.0.1" và `getRemoteAddr()` lấy IP gateway, không phải IP khách

**File:** `sources/backend/payment-service/src/main/java/com/ptit/htpt/paymentservice/web/PaymentController.java:49-53`, `sources/backend/payment-service/src/main/java/com/ptit/htpt/paymentservice/service/PaymentCrudService.java:69`
**Issue:** `createSession` gọi `httpRequest.getRemoteAddr()` để lấy `vnp_IpAddr`. Vì payment-service
nằm sau api-gateway (và order-service gọi qua gateway), `getRemoteAddr()` luôn trả IP của
gateway/order-service container, không phải IP khách thật. VNPay dùng `vnp_IpAddr` cho
fraud-check; gửi IP nội bộ làm giảm hiệu quả và có thể bị cổng từ chối. `PaymentSessionClient`
cũng không forward IP khách.
**Fix:** order-service đọc `X-Forwarded-For` / IP request gốc từ FE, truyền xuống
`PaymentSessionClient.createVNPaySession` như một tham số `clientIp`, và payment-service ưu tiên
giá trị này thay vì `getRemoteAddr()`.

### WR-04: So sánh HMAC không phải constant-time

**File:** `sources/backend/payment-service/src/main/java/com/ptit/htpt/paymentservice/vnpay/VNPaySignature.java:109`
**Issue:** `computed.equalsIgnoreCase(received)` so sánh chuỗi chữ ký bằng `String.equals`, không
phải constant-time → về lý thuyết hở timing side-channel cho phép đoán dần chữ ký. Với HMAC
512-bit và endpoint mạng độ trễ cao thì khai thác rất khó, nhưng đây là code path bảo mật nên
nên dùng so sánh hằng thời gian.
**Fix:**
```java
return MessageDigest.isEqual(
    computed.toLowerCase().getBytes(StandardCharsets.UTF_8),
    received.toLowerCase().getBytes(StandardCharsets.UTF_8));
```

### WR-05: `processIpn` publish event trong `@Transactional` nhưng không bảo vệ trường hợp publish-after-commit thất bại

**File:** `sources/backend/payment-service/src/main/java/com/ptit/htpt/paymentservice/vnpay/VNPayService.java:137-147`
**Issue:** `processIpn` lưu transaction (PAID/FAILED) rồi gọi `eventPublisher.publishPaymentEvent`
trong cùng `@Transactional`. Publisher defer publish tới `afterCommit`. Nếu broker nack/timeout
(đã chỉ log `[MQ-PUB-NACK]`/`[MQ-PUB-TIMEOUT]`, không rollback — theo thiết kế), payment-service
trả `RspCode 00` cho VNPay nhưng order-service KHÔNG bao giờ nhận event → đơn kẹt PENDING vĩnh
viễn dù tiền đã trừ. Không có cơ chế outbox / retry publish.
**Fix:** Cân nhắc transactional outbox: ghi event vào bảng `payment_outbox` trong cùng
transaction, một scheduler/relay publish lại. Tối thiểu: nâng log NACK/TIMEOUT thành cảnh báo
mức cao (`[MQ-PUB-NACK]` đã `log.error`, tốt) và thêm endpoint/job đối soát đơn PENDING quá hạn.
Ghi nhận hạn chế này trong SUMMARY nếu defer.

### WR-06: `validateStockOrThrow` nuốt mọi exception → bỏ qua kiểm tra tồn kho khi product-service lỗi

**File:** `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderCrudService.java:430-433`
**Issue:** Không phải code mới Phase 26 nhưng nằm trực tiếp trong `createOrderFromCommand` đường
VNPAY. `catch (Exception ex)` bắt tất cả và chỉ `log.warn` → nếu product-service timeout, đơn
VNPAY vẫn được tạo và đẩy sang cổng thanh toán dù có thể hết hàng. Kết hợp với D-09 (trừ kho
chỉ sau IPN PAID), khách có thể thanh toán thành công cho hàng đã hết. `catch` quá rộng cũng
nuốt cả lỗi lập trình (NPE, ClassCastException tại `((Number) stockObj)`).
**Fix:** Thu hẹp `catch` xuống `RestClientException`; phân biệt lỗi mạng (best-effort skip) với
lỗi parse (nên ném). Với đơn VNPAY có giá trị, cân nhắc fail-closed khi không validate được tồn
kho thay vì best-effort.

## Info

### IN-01: VNPay sandbox credentials không có giá trị mặc định an toàn — service start được nhưng IPN luôn fail

**File:** `sources/backend/payment-service/src/main/resources/application.yml:49-50`, `sources/backend/payment-service/src/main/java/com/ptit/htpt/paymentservice/vnpay/VNPayConfig.java`
**Issue:** `vnp.tmn-code` và `vnp.hash-secret` mặc định rỗng (`${VNP_TMN_CODE:}`). Nếu env var
thiếu, `hmacSHA512` chạy với key rỗng → mọi chữ ký sai → mọi IPN trả 97, và `buildPaymentUrl`
sinh URL không hợp lệ. Hỏng âm thầm khó chẩn đoán.
**Fix:** Thêm kiểm tra fail-fast lúc khởi động (`@PostConstruct` hoặc `@Validated` +
`@NotBlank`) để service từ chối start khi thiếu `tmnCode`/`hashSecret`, kèm log rõ ràng.

### IN-02: `PaymentServiceApplication` lồng `PingController` — endpoint `/ping` không qua envelope/whitelist

**File:** `sources/backend/payment-service/src/main/java/com/ptit/htpt/paymentservice/PaymentServiceApplication.java:17-23`
**Issue:** `PingController` trả chuỗi thuần `/ping`. Code debug/health tạm — đã có
`/actuator/health`. Endpoint trùng mục đích, nên gỡ để giảm bề mặt.
**Fix:** Xóa `PingController`, dùng `/actuator/health`.

### IN-03: Tên class test gây hiểu nhầm — `*IT` nhưng là unit test Mockito

**File:** `sources/backend/payment-service/src/test/java/com/ptit/htpt/paymentservice/vnpay/VNPayIpnControllerIT.java:31-37`, `sources/backend/order-service/src/test/java/com/ptit/htpt/orderservice/PaymentEventListenerIT.java`
**Issue:** Hậu tố `IT` quy ước cho integration test (failsafe plugin), nhưng cả hai file là unit
test Mockito thuần, không Spring context. Javadoc đã thừa nhận. Có thể bị plugin build phân
loại sai (chạy ở pha sai hoặc bị bỏ qua).
**Fix:** Đổi tên thành `*Test` (vd `VNPayServiceTest`, `PaymentEventListenerTest`) cho đúng quy
ước surefire.

### IN-04: `buildReturnView` không có `@Transactional(readOnly = true)`

**File:** `sources/backend/payment-service/src/main/java/com/ptit/htpt/paymentservice/vnpay/VNPayService.java:162-184`
**Issue:** Javadoc nói "load read-only" nhưng method không đánh dấu `@Transactional(readOnly =
true)`. Với `open-in-view: false`, `sessionRepo.findById` mở transaction ngắn riêng nên hiện
tại không lỗi, nhưng thiếu nhãn rõ ý định và mất defense-in-depth nếu sau này thêm lazy access.
**Fix:** Thêm `@Transactional(readOnly = true)` cho `buildReturnView`.

---

_Reviewed: 2026-05-22_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
