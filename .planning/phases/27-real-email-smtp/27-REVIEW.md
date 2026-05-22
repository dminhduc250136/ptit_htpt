---
phase: 27-real-email-smtp
reviewed: 2026-05-22T08:00:00Z
depth: standard
files_reviewed: 32
files_reviewed_list:
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/messaging/event/OrderEventEnvelope.java
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/messaging/config/RabbitMQConfig.java
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/messaging/publisher/OrderEventPublisher.java
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderCrudService.java
  - sources/backend/user-service/src/main/resources/db/migration/V102__add_email_verified.sql
  - sources/backend/user-service/src/main/resources/db/migration/V103__create_verification_tokens.sql
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/domain/UserEntity.java
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/domain/VerificationTokenEntity.java
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/repository/VerificationTokenRepository.java
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/service/VerificationTokenService.java
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/messaging/config/UserRabbitMQConfig.java
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/messaging/tracing/TraceIdMessagePostProcessor.java
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/messaging/event/UserEventEnvelope.java
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/messaging/publisher/AccountEventPublisher.java
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/service/AuthService.java
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/AuthController.java
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/dto/ForgotPasswordRequest.java
  - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/dto/ResetPasswordRequest.java
  - sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/service/email/EmailSender.java
  - sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/service/email/MailTemplate.java
  - sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/service/NotificationDispatchService.java
  - sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/messaging/event/OrderEventEnvelope.java
  - sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/messaging/event/UserEventEnvelope.java
  - sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/messaging/config/RabbitMQConfig.java
  - sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/messaging/consumer/UserEventListener.java
  - sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/messaging/consumer/OrderPlacedNotifyListener.java
  - docker-compose.yml
  - sources/frontend/src/app/forgot-password/page.tsx
  - sources/frontend/src/app/verify-email/page.tsx
  - sources/frontend/src/app/verify-email/page.module.css
  - sources/frontend/src/app/reset-password/page.tsx
  - sources/frontend/src/services/auth.ts
findings:
  critical: 3
  warning: 5
  info: 4
  total: 12
status: issues_found
---

# Phase 27: Code Review Report — Real Email SMTP

**Reviewed:** 2026-05-22T08:00:00Z
**Depth:** standard
**Files Reviewed:** 32
**Status:** issues_found

## Summary

Phase 27 triển khai luồng email SMTP thực cho 4 tính năng: xác minh email khi đăng ký, quên/đặt lại mật khẩu, xác nhận đơn hàng, cập nhật trạng thái đơn. Kiến trúc tổng thể tốt — sử dụng đúng `SecureRandom` 256-bit cho token, có chống replay bằng `SELECT FOR UPDATE`, chống email enumeration, graceful degradation khi SMTP chưa cấu hình.

Tuy nhiên có **3 vấn đề Critical** cần xử lý trước khi merge:

1. **HTML injection** trong tất cả 6 email template — dữ liệu người dùng (`fullName`, `orderId`) được đưa thẳng vào `%s` trong HTML mà không escape, cho phép bất kỳ ai có tên hiển thị chứa `<script>` hoặc HTML tag làm hỏng email của người khác.
2. **Credentials RabbitMQ mặc định** `guest/guest` được hardcode trong `docker-compose.yml` và sẽ theo vào môi trường không phải dev nếu không overwrite.
3. **Token lộ qua URL** khi `order-service` gọi `resolveCustomerEmail()` — URL chứa `userId` lộ ra trong access log của api-gateway (mức độ thấp hơn nhưng đáng ghi nhận cho dữ liệu đơn hàng).

Ngoài ra có 5 Warning liên quan đến race condition khi đăng ký, thiếu rate-limit endpoint forgot-password, lỗi `@Transactional` import sai package trong `VerificationTokenService`, v.v.

---

## Critical Issues

### CR-01: HTML Injection trong email template — `fullName` và `orderId` không được escape

**File:** `sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/service/email/MailTemplate.java:65,112,178`

**Issue:** Tất cả 6 template dùng `.formatted(vars.get("fullName"), vars.get("verifyUrl"))` để chèn giá trị vào HTML mà không HTML-escape. Nếu người dùng đăng ký với `fullName = <b>hack</b>` hoặc thậm chí `<img src=x onerror=alert(1)>`, giá trị này xuất hiện nguyên trạng trong email HTML gửi đến người nhận khác (ví dụ admin nhận thông báo đơn hàng). Với `orderId` do DB tạo ra (UUID) thì rủi ro thấp hơn, nhưng `fullName` là user-controlled input.

Các dòng bị ảnh hưởng:
- `ACCOUNT_VERIFICATION.render()` line 65: `vars.get("fullName")`
- `PASSWORD_RESET.render()` line 112: `vars.get("fullName")`
- `ORDER_CONFIRMATION.render()` line 178: `vars.get("orderId")` (thấp hơn vì UUID)
- `ORDER_SHIPPED/DELIVERED/CANCELLED`: `vars.get("orderId")`

**Fix:** Thêm hàm `escapeHtml()` và áp dụng cho tất cả user-controlled values trước khi đưa vào template:

```java
// Thêm vào MailTemplate hoặc một utility class
private static String esc(String s) {
    if (s == null) return "";
    return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;");
}

// Trong ACCOUNT_VERIFICATION.render():
.formatted(esc(vars.get("fullName")), esc(vars.get("verifyUrl")));

// Trong PASSWORD_RESET.render():
.formatted(esc(vars.get("fullName")), esc(vars.get("resetUrl")));
```

Lưu ý: `verifyUrl` và `resetUrl` là URL được xây dựng bởi server (`appBaseUrl + "/verify-email?token=" + token`), token là base64url nên không chứa HTML chars nguy hiểm — nhưng `appBaseUrl` đến từ config env, cũng nên escape phòng thủ.

---

### CR-02: RabbitMQ credentials mặc định `guest/guest` hardcode trong docker-compose

**File:** `docker-compose.yml:122-124`

**Issue:** RabbitMQ khởi động với `RABBITMQ_DEFAULT_USER: guest` / `RABBITMQ_DEFAULT_PASS: guest`. Tất cả các service (`user-service`, `order-service`, `inventory-service`, `notification-service`) đều dùng `SPRING_RABBITMQ_USER: guest` / `SPRING_RABBITMQ_PASS: guest` hardcode trong compose file (không qua `${VAR:-default}` như SMTP). Nếu file này được dùng làm template deploy lên staging/prod mà không đổi, broker RabbitMQ sẽ dùng credentials mặc định nổi tiếng.

```yaml
# docker-compose.yml line 121-124
rabbitmq:
  environment:
    RABBITMQ_DEFAULT_USER: guest      # hardcoded
    RABBITMQ_DEFAULT_PASS: guest      # hardcoded
```

Các service connect:
```yaml
# line 170-171 (user-service) và tương tự ở order/inventory/notification
SPRING_RABBITMQ_USER: guest          # hardcoded, không dùng ${VAR}
SPRING_RABBITMQ_PASS: guest
```

**Fix:** Đổi sang pattern variable substitution giống SMTP:

```yaml
rabbitmq:
  environment:
    RABBITMQ_DEFAULT_USER: ${RABBITMQ_USER:-guest}
    RABBITMQ_DEFAULT_PASS: ${RABBITMQ_PASS:-guest}

# Và ở mỗi service:
SPRING_RABBITMQ_USER: ${RABBITMQ_USER:-guest}
SPRING_RABBITMQ_PASS: ${RABBITMQ_PASS:-guest}
```

---

### CR-03: `@Transactional` import sai package trong `VerificationTokenService` — SELECT FOR UPDATE có thể không hoạt động đúng

**File:** `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/service/VerificationTokenService.java:5,54,73`

**Issue:** `VerificationTokenService` dùng `import jakarta.transaction.Transactional` (Jakarta EE) thay vì `import org.springframework.transaction.annotation.Transactional` (Spring). Điều này có thể gây ra hành vi không mong muốn:

- `@Lock(LockModeType.PESSIMISTIC_WRITE)` trong `VerificationTokenRepository.findByTokenForUpdate()` phụ thuộc vào Spring Transaction context để phát `SELECT FOR UPDATE`. Nếu transaction không được quản lý đúng bởi Spring `PlatformTransactionManager`, lock có thể không được phát — dẫn đến race condition khi 2 request đồng thời dùng cùng token.
- `AuthService` (line 13) cũng dùng `import jakarta.transaction.Transactional` — cả 2 service nested trong cùng 1 transaction context từ `AuthService`, nhưng `VerificationTokenService.verifyAndConsume()` được gọi từ `AuthService` cũng có `@Transactional(jakarta)`. Spring Boot mặc định integrate cả 2, nhưng chuẩn là dùng Spring annotation để đảm bảo.

Thực tế: Spring Boot 3.x tích hợp Jakarta `@Transactional` thông qua `JtaTransactionManager` hoặc annotation processor, nhưng best practice và an toàn nhất là dùng Spring annotation cho JPA/Hibernate context.

**Fix:**

```java
// VerificationTokenService.java — đổi import
// TRƯỚC:
import jakarta.transaction.Transactional;

// SAU:
import org.springframework.transaction.annotation.Transactional;
```

---

## Warnings

### WR-01: Race condition TOCTOU khi đăng ký — `findByUsername` + `findByEmail` check trước `save`

**File:** `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/service/AuthService.java:65-73`

**Issue:** `register()` kiểm tra trùng username/email bằng 2 query SELECT riêng trước khi INSERT. Với concurrent requests (2 người đăng ký cùng email gần như đồng thời), cả 2 có thể pass check và đến `userRepo.save()` — lúc đó DB unique constraint mới bắt, và exception sẽ là `DataIntegrityViolationException` không được handle, trả về 500 thay vì 409.

```java
// AuthService.java line 65-73
if (userRepo.findByUsername(req.username()).isPresent()) {  // TOCTOU window
    throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
}
if (userRepo.findByEmail(req.email()).isPresent()) {        // TOCTOU window
    throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
}
```

**Fix:** Thêm `@ExceptionHandler` cho `DataIntegrityViolationException` trong `GlobalExceptionHandler` (hoặc trong `AuthService.register()` bắt exception từ `save()`):

```java
// GlobalExceptionHandler hoặc catch trong register()
try {
    userRepo.save(entity);
} catch (DataIntegrityViolationException e) {
    throw new ResponseStatusException(HttpStatus.CONFLICT, "Username hoặc email đã tồn tại");
}
```

---

### WR-02: Thiếu rate limiting cho endpoint `POST /auth/password/forgot` — có thể bị lợi dụng spam email

**File:** `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/AuthController.java:97-101`

**Issue:** Endpoint `POST /auth/password/forgot` không có bất kỳ rate limiting nào. Kẻ tấn công có thể gọi endpoint này liên tục với email của nạn nhân, gây ra việc gửi hàng trăm email reset password đến hộp thư của nạn nhân (email bombing). Endpoint anti-enumeration (luôn 200) là đúng nhưng cần kết hợp với rate limit.

**Fix:** Tầng ngắn hạn: thêm rate limit tại API gateway hoặc dùng Bucket4j ở Spring layer. Tầng DB: thêm check "chỉ tạo token mới nếu token cũ đã quá `X` giây" trong `VerificationTokenService.createToken()`:

```java
// VerificationTokenService.createToken() — thêm cooldown check
// (ví dụ: không tạo token mới nếu đã có token cùng userId+type tạo trong 60 giây qua)
// Hoặc đơn giản hơn: xóa/invalidate token cũ khi tạo token mới
```

---

### WR-03: `customerEmail` trong `OrderStatusChangedPayload` có thể blank string — notification-service ghi log nhưng SKIPPED không phản ánh lỗi logic

**File:** `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderCrudService.java:219-226`

**Issue:** `resolveCustomerEmail()` trả `""` khi user-service không available (line 448-449). `updateOrderState()` truyền kết quả này vào `OrderStatusChangedPayload.customerEmail` (line 222). Khi notification-service nhận event, `EmailSender.send()` kiểm tra `to == null || to.isBlank()` và trả `SKIPPED` (EmailSender.java line 62-65) — dispatch_log ghi `SKIPPED`. Tuy nhiên, không có cảnh báo nào ở order-service khi email bị blank, nên vấn đề này có thể bị bỏ qua trong production.

Vấn đề sâu hơn: `resolveCustomerEmail()` gọi REST đến `api-gateway` trong cùng `@Transactional` (line 211 `@Transactional` trên `updateOrderState`). Nếu REST call chậm (timeout), transaction giữ lock DB lâu hơn cần thiết.

**Fix:** Di chuyển `resolveCustomerEmail()` ra ngoài transaction, gọi trước khi mở transaction:

```java
// updateOrderState: resolve email TRƯỚC khi @Transactional bắt đầu
public OrderDto updateOrderState(String id, OrderStateRequest request) {
    String customerEmail = resolveCustomerEmail(id); // gọi user-service ngoài tx
    return updateOrderStateTransactional(id, request, customerEmail);
}

@Transactional
private OrderDto updateOrderStateTransactional(String id, OrderStateRequest request, String customerEmail) {
    // ... persist + publish
}
```

---

### WR-04: `OrderPlacedNotifyListener` — idempotency key là `eventId` nhưng không xét trường hợp `OrderPlaced` và `OrderStatusChanged` có thể có cùng `eventId`

**File:** `sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/messaging/consumer/OrderPlacedNotifyListener.java:63`

**Issue:** `processed_events` deduplication dùng `eventId` làm primary key. `eventId` được tạo bằng `UUID.randomUUID()` (order-service `OrderEventEnvelope.java:34,43`) — nên không thể trùng giữa các event. Tuy nhiên, cùng 1 listener `OrderPlacedNotifyListener` xử lý cả `OrderPlaced` VÀ `OrderStatusChanged` (từ cùng queue `notification.order-events`). Nếu có bug khiến cùng UUID được reuse (ví dụ từ event replay thủ công), `insertIfAbsent` sẽ skip cả hai loại event.

Đây là edge case nhỏ nhưng `processedEventRepository.insertIfAbsent(eventId, envelope.eventType())` truyền `eventType` như thông tin phụ — nếu schema `processed_events` chỉ có `eventId` làm PK (không composite key với `eventType`), thì dedup đang đúng. Cần xác nhận schema.

**Fix:** Không cần thay đổi nếu `processed_events.event_id` là PK UUID duy nhất. Nếu muốn an toàn hơn, composite key `(event_id, event_type)` sẽ rõ ràng hơn.

---

### WR-05: `verify-email` page gọi API trong `useEffect` không có AbortController — memory leak khi unmount

**File:** `sources/frontend/src/app/verify-email/page.tsx:29-43`

**Issue:** `useEffect` gọi `fetch()` mà không có cleanup function trả về `AbortController.abort()`. Nếu component unmount (ví dụ user navigate đi nhanh), `setState` sẽ được gọi trên unmounted component, gây React warning và tiềm ẩn memory leak.

```typescript
// page.tsx line 29-42
useEffect(() => {
    if (!token) return;
    fetch(`/api/users/auth/verify-email?token=${encodeURIComponent(token)}`)
      .then(...)  // không có cleanup
}, [token]);
```

**Fix:**

```typescript
useEffect(() => {
    if (!token) return;
    const controller = new AbortController();
    fetch(`/api/users/auth/verify-email?token=${encodeURIComponent(token)}`,
          { signal: controller.signal })
      .then((res) => {
        if (res.ok) setState('success');
        else if (res.status === 410) setState('expired');
        else setState('invalid');
      })
      .catch((err) => {
        if (err.name !== 'AbortError') setState('invalid');
      });
    return () => controller.abort();
}, [token]);
```

---

## Info

### IN-01: `@Deprecated` method `recordOrderConfirmation` cần được xóa sau khi refactor hoàn tất

**File:** `sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/service/NotificationDispatchService.java:129-134`

**Issue:** Method `recordOrderConfirmation()` được đánh dấu `@Deprecated` với comment "sẽ xóa ở Plan 27-05" nhưng Plan 27-05 đã hoàn tất. Method này hiện là dead code — `OrderPlacedNotifyListener` đã gọi thẳng `sendOrderConfirmation()`.

**Fix:** Xóa method `recordOrderConfirmation()` trong lần cleanup tiếp theo.

---

### IN-02: `APP_BASE_URL` hardcode `http://localhost:3000` trong docker-compose — link email không hoạt động khi deploy

**File:** `docker-compose.yml:172`

**Issue:** `user-service` environment có `APP_BASE_URL: http://localhost:3000` — đây là giá trị cứng, không dùng pattern `${VAR:-default}`. Khi deploy lên server thực (staging/prod), link xác minh email trong email gửi đến người dùng sẽ trỏ về `localhost:3000` thay vì domain thực.

```yaml
# docker-compose.yml line 172
APP_BASE_URL: http://localhost:3000  # nên là ${APP_BASE_URL:-http://localhost:3000}
```

**Fix:**

```yaml
APP_BASE_URL: ${APP_BASE_URL:-http://localhost:3000}
```

---

### IN-03: `OrderStatusChangedPayload.customerName` luôn `null` khi gửi từ `updateOrderState`

**File:** `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderCrudService.java:225`

**Issue:** `customerName` được set `null` với comment "notification-service có thể bỏ qua null". Trong `notification-service`, email `ORDER_SHIPPED/DELIVERED/CANCELLED` không dùng `customerName` (các template này chỉ dùng `orderId`), nên không crash. Tuy nhiên, field này tồn tại trong schema payload nhưng luôn null — gây nhầm lẫn cho người đọc code.

**Fix:** Nếu `customerName` không được dùng trong bất kỳ template nào, xóa field này khỏi `OrderStatusChangedPayload`. Hoặc giữ nguyên và thêm comment rõ ràng hơn trong record definition.

---

### IN-04: `MimeMessageHelper` dùng `multipart=false` — không attach file, nhưng nên document rõ

**File:** `sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/service/email/EmailSender.java:68`

**Issue:** `new MimeMessageHelper(message, false, "UTF-8")` — tham số `false` là `multipart`. Đây là lựa chọn đúng cho email HTML-only không cần attachment. Tuy nhiên, nếu sau này cần thêm CID inline image hoặc attachment, sẽ cần đổi thành `true`. Không phải bug, nhưng thiếu comment.

**Fix:** Thêm comment:

```java
// false = không cần multipart (không có attachment/inline image trong phase này)
MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
```

---

_Reviewed: 2026-05-22T08:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
