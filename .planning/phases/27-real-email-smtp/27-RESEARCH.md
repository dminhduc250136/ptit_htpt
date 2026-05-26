# Phase 27: Gửi Email Thật (SMTP) - Research

**Researched:** 2026-05-22
**Domain:** Spring Boot JavaMailSender + RabbitMQ Producer (user-service) + HTML email templates
**Confidence:** HIGH (dựa chủ yếu vào đọc codebase thực tế + Spring Boot official docs)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** notification-service là service gửi SMTP duy nhất. Chỉ thêm `spring-boot-starter-mail` vào notification-service.
- **D-02:** user-service trở thành RabbitMQ Producer. Topology mới: topic exchange `user.events` (durable), routing keys `user.registered` + `user.password-reset`; queue durable `notification.user-events` bind `user.#`; DLX `user.dlx` + DLQ `user-events.dlq`.
- **D-03:** order-service publish thêm event `OrderStatusChanged` (routing key `order.status-changed`) lên exchange `order.events` đã có. Queue `notification.order-events` đã bind `order.#` — KHÔNG đổi topology phía order.
- **D-04:** Thêm cột `email_verified BOOLEAN NOT NULL DEFAULT false` vào `user_svc.users` qua Flyway migration mới.
- **D-05:** Bảng mới `user_svc.verification_tokens(token VARCHAR(64) PK, user_id VARCHAR(36) NOT NULL, type VARCHAR(20) NOT NULL, expires_at TIMESTAMPTZ NOT NULL, used_at TIMESTAMPTZ NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now())`.
- **D-06:** Token = SecureRandom 32 byte → base64url. Hạn: EMAIL_VERIFY 24h, PASSWORD_RESET 1h.
- **D-07:** Login KHÔNG bị hard-gate bởi verification (soft approach). register() vẫn phát JWT ngay.
- **D-08:** 3 endpoint mới trong AuthController: `GET /auth/verify-email?token=...`, `POST /auth/password/forgot`, `POST /auth/password/reset`.
- **D-09:** Email HTML render bằng Java text block. KHÔNG dùng template engine ngoài. Chỉ gửi HTML.
- **D-10:** 6 template qua enum `MailTemplate`: ACCOUNT_VERIFICATION, PASSWORD_RESET, ORDER_CONFIRMATION, ORDER_SHIPPED, ORDER_DELIVERED, ORDER_CANCELLED.
- **D-11:** Event payload mang sẵn dữ liệu — notification-service KHÔNG gọi REST cross-service.
- **D-12:** order-service `AdminOrderController.PATCH /{id}/state` → publish `OrderStatusChanged` trong `afterCommit`. Chỉ shipped/delivered/cancelled gửi email.
- **D-13:** notification-service: mỗi queue một `@RabbitListener` method; branch theo eventType để chọn MailTemplate. Idempotent qua `processed_events`.
- **D-14:** Config SMTP đọc từ env: `MAIL_SMTP_HOST`, `MAIL_SMTP_PORT`, `MAIL_SMTP_USERNAME`, `MAIL_SMTP_PASSWORD`, `MAIL_FROM_ADDRESS`.
- **D-15:** Thiếu/blank env SMTP → service vẫn khởi động; `EmailSender` log WARN lúc startup; mỗi lần gửi log WARN + ghi `dispatch_log` status=`SKIPPED`.
- **D-16:** Mọi lần gửi email ghi `dispatch_log`: SENT/FAILED/SKIPPED.
- **D-17:** SMTP lỗi tạm thời → throw `TransientMessageException` → retry 3 lần exp backoff Phase 23 → DLQ.
- **D-18:** 3 trang Next.js: `/verify-email`, `/reset-password`, `/forgot-password`. Thêm link "Quên mật khẩu?" ở trang login.
- **D-19:** docker-compose: notification-service nhận env SMTP; user-service nhận `SPRING_RABBITMQ_HOST=rabbitmq` + `depends_on: rabbitmq` + env `APP_BASE_URL`.

### Claude's Discretion

- Tên class cụ thể (`EmailSender`, `MailTemplate`, `VerificationTokenService`, `AccountEventPublisher`...).
- SQL chi tiết Flyway migration `verification_tokens` + cột `email_verified`.
- Có tách `AuthController` thành controller riêng cho verify/reset hay giữ chung.
- Bố cục HTML/CSS inline của 6 template.
- Có gộp 2 listener method hay tách theo queue.

### Deferred Ideas (OUT OF SCOPE)

1. Hard-gate login với tài khoản chưa verified
2. Plain-text multipart alternative
3. Admin UI xem/resend `dispatch_log`
4. Rate-limit request reset mật khẩu
5. Email open/click tracking + i18n
6. Template engine (Thymeleaf)

</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| MAIL-01 | Cấu hình SMTP từ env, graceful degradation khi thiếu env | Spring auto-config conditional trên `spring.mail.host`; pattern `@Autowired(required=false)` hoặc check env null trong `EmailSender` constructor |
| MAIL-02 | Email xác thực tài khoản + reset mật khẩu, gắn vào user-service auth flow | VerificationTokenService sinh token SecureRandom, 3 endpoint mới trong AuthController, user-service Producer mới |
| MAIL-03 | Email xác nhận đơn hàng từ event OrderPlaced (Phase 23) | Nâng cấp NotificationDispatchService gửi SMTP thật; OrderPlacedPayload cần thêm `customerEmail` + `productName` per item |
| MAIL-04 | Email cập nhật trạng thái đơn (shipped/delivered/cancelled) | OrderCrudService.updateOrderState() thêm publish `OrderStatusChanged` afterCommit; listener branch theo status |

</phase_requirements>

---

## Summary

Phase 27 là bước nâng cấp notification-service từ "chỉ ghi `dispatch_log`" (Phase 23) thành **gửi email thật qua SMTP Gmail**. Toàn bộ pattern reliability (idempotency, retry, DLQ, traceId) đã được xây dựng ở Phase 23 và được tái dụng nguyên vẹn — Phase 27 chỉ thêm tầng SMTP trên đầu.

Ba luồng chính đòi hỏi thay đổi ở 4 service: (1) **notification-service** nhận `spring-boot-starter-mail`, viết `EmailSender` với graceful degradation, 6 HTML template, 2 listener mới; (2) **user-service** trở thành RabbitMQ Producer, thêm `verification_tokens` table, 3 endpoint mới trong AuthController; (3) **order-service** publish thêm `OrderStatusChanged` afterCommit; (4) **frontend** thêm 3 trang auth nhẹ.

Rủi ro chính của phase này là **payload không đủ dữ liệu để gửi email** (D-11): `OrderPlacedPayload` hiện tại không có `customerEmail` và mỗi `Item` chỉ có `productId` (không có `productName`). Cần mở rộng payload trước khi notification-service có thể render template. Đây là điểm dễ bị bỏ sót khi lập kế hoạch.

**Primary recommendation:** Bắt đầu từ data model (Flyway migrations + payload expansion), sau đó infrastructure (EmailSender + RabbitMQ topology user.events), rồi mới đến business logic (VerificationTokenService + listeners + templates + FE pages).

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Gửi SMTP email | notification-service | — | D-01: tập trung SMTP tại một nơi, tái dùng reliability Phase 23 |
| Sinh token verify/reset | user-service (API) | — | Token liên quan đến identity, thuộc domain user |
| Lưu verification_tokens | user-service DB | — | DB-per-service (Phase 24) — token phải ở cùng DB với users |
| Publish event tài khoản | user-service (Producer) | — | D-02: user-service biết email + fullName khi register/forgot |
| Publish event OrderStatusChanged | order-service (Producer) | — | D-03: order-service sở hữu trạng thái đơn hàng |
| Render HTML template | notification-service | — | D-09: Java text block, tập trung template tại consumer |
| Ghi dispatch_log | notification-service | — | D-16: audit trail mọi email, đã có DispatchLogEntity |
| Trang /verify-email + /forgot-password + /reset-password | Next.js (FE) | — | D-18: pages đọc query token params, call API gateway |
| Config SMTP env | docker-compose → notification-service | — | D-14: env vars mapped qua spring.mail.* |

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| spring-boot-starter-mail | 3.3.2 (BOM) | JavaMailSender, MimeMessage | Tích hợp sẵn Spring Boot, auto-config |
| spring-boot-starter-amqp | 3.3.2 (BOM) | RabbitMQ Producer user-service | Đã có ở order-service + notification-service — mirror pattern |
| java.security.SecureRandom | JDK built-in | Sinh token ngẫu nhiên | Cryptographically secure, không cần thư viện ngoài |
| java.util.Base64 (URL encoder) | JDK built-in | Encode token → URL-safe string | Sẵn có JDK 8+ |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| jakarta.mail (transitively via starter-mail) | ~2.1 | MIME API | Dùng gián tiếp qua JavaMailSender/MimeMessageHelper |
| spring-amqp | 3.x (BOM) | RabbitTemplate, @RabbitListener | User-service producer cần RabbitTemplate |

**Lưu ý:** `spring-boot-starter-mail` chưa có trong `notification-service/pom.xml` — cần thêm. `spring-boot-starter-amqp` chưa có trong `user-service/pom.xml` — cần thêm.

**Installation:**
```xml
<!-- notification-service/pom.xml -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-mail</artifactId>
</dependency>

<!-- user-service/pom.xml -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

---

## Architecture Patterns

### System Architecture Diagram

```
Luồng 1: Xác thực tài khoản
  [FE Register] → POST /auth/register → [user-service AuthService]
       └── sinh EMAIL_VERIFY token → save verification_tokens
       └── publish user.registered → [RabbitMQ user.events]
                                          └── notification.user-events
                                               └── [notification-service UserEventListener]
                                                    └── EmailSender.send(ACCOUNT_VERIFICATION)
                                                         └── Gmail SMTP ──→ [Hộp thư người dùng]
                                                    └── dispatch_log status=SENT/FAILED/SKIPPED

  [Hộp thư] → click link → [FE /verify-email?token=...] → GET /auth/verify-email?token=...
       └── [user-service AuthController] → VerificationTokenService.verify()
            └── users.email_verified = true, token used_at = now()

Luồng 2: Reset mật khẩu
  [FE /forgot-password] → POST /auth/password/forgot {email} → [user-service]
       └── sinh PASSWORD_RESET token → publish user.password-reset → RabbitMQ
       └── notification-service gửi mail reset

  [FE /reset-password?token=...] → POST /auth/password/reset {token, newPassword}
       └── verify token → changePasswordHash → token used_at = now()

Luồng 3: Xác nhận đơn hàng (Phase 23 nâng cấp)
  [order-service] → publish order.placed [OrderPlaced+customerEmail+productName]
       └── notification.order-events → [notification-service OrderPlacedNotifyListener]
            └── EmailSender.send(ORDER_CONFIRMATION) → Gmail SMTP

Luồng 4: Cập nhật trạng thái đơn
  [Admin PATCH /{id}/state] → [order-service OrderCrudService.updateOrderState()]
       └── afterCommit → publish order.status-changed [OrderStatusChanged]
            └── notification.order-events → [notification-service OrderPlacedNotifyListener]
                 └── branch theo eventType+status → ORDER_SHIPPED/DELIVERED/CANCELLED
                 └── EmailSender.send(template) → Gmail SMTP
```

### Recommended Project Structure

```
notification-service/src/main/java/.../notificationservice/
├── messaging/
│   ├── config/
│   │   └── RabbitMQConfig.java          # thêm user.events topology
│   ├── consumer/
│   │   ├── OrderPlacedNotifyListener.java  # mở rộng: nhánh OrderStatusChanged
│   │   └── UserEventListener.java          # MỚI: consume notification.user-events
│   ├── event/
│   │   ├── OrderEventEnvelope.java         # copy từ order-service (thêm customerEmail, productName)
│   │   └── UserEventEnvelope.java          # MỚI: event tài khoản từ user-service
│   └── exception/                          # đã có Phase 23
├── service/
│   ├── NotificationDispatchService.java    # nâng cấp: gọi EmailSender thật
│   └── email/
│       ├── EmailSender.java                # MỚI: JavaMailSender + graceful degradation
│       └── MailTemplate.java               # MỚI: enum 6 template HTML text block
└── domain/
    └── DispatchLogEntity.java              # đã có, tái dụng

user-service/src/main/java/.../userservice/
├── messaging/
│   ├── config/
│   │   └── UserRabbitMQConfig.java         # MỚI: user.events exchange + queue + DLX
│   └── publisher/
│       └── AccountEventPublisher.java      # MỚI: publish user.registered + user.password-reset
├── service/
│   ├── AuthService.java                    # sửa: gọi VerificationTokenService sau save()
│   └── VerificationTokenService.java       # MỚI: sinh/verify token, Flyway V3 + V4
├── web/
│   └── AuthController.java                 # sửa: thêm 3 endpoint D-08
├── domain/
│   ├── UserEntity.java                     # sửa: thêm emailVerified field + setter
│   └── VerificationTokenEntity.java        # MỚI: JPA entity cho verification_tokens
└── resources/db/migration/
    ├── V3__add_email_verified.sql           # MỚI: ALTER TABLE users ADD COLUMN email_verified
    └── V4__create_verification_tokens.sql   # MỚI: CREATE TABLE verification_tokens

order-service/src/main/java/.../orderservice/
├── messaging/
│   ├── event/
│   │   └── OrderEventEnvelope.java         # sửa: thêm OrderStatusChangedPayload + customerEmail
│   └── publisher/
│       └── OrderEventPublisher.java         # sửa: thêm publishOrderStatusChanged(...)
└── service/
    └── OrderCrudService.java               # sửa: updateOrderState() thêm afterCommit publish

frontend/src/app/
├── verify-email/
│   └── page.tsx                            # MỚI: đọc ?token, call GET /auth/verify-email
├── forgot-password/
│   └── page.tsx                            # MỚI: form nhập email
└── reset-password/
    └── page.tsx                            # MỚI: đọc ?token, form mật khẩu mới
```

### Pattern 1: JavaMailSender với Graceful Degradation

**What:** Inject `JavaMailSender` optional; kiểm tra null/env missing lúc startup; mỗi lần gửi log WARN + ghi SKIPPED thay vì throw.

**When to use:** D-14 + D-15 — service phải khởi động được dù không có env SMTP.

```java
// Source: Spring Boot docs + verified codebase pattern
@Service
public class EmailSender {
  private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

  private final JavaMailSender mailSender;   // null nếu spring.mail.host không set
  private final String fromAddress;
  private final boolean configured;

  public EmailSender(
      @Autowired(required = false) JavaMailSender mailSender,
      @Value("${MAIL_FROM_ADDRESS:}") String fromAddress
  ) {
    this.mailSender = mailSender;
    this.fromAddress = fromAddress;
    this.configured = mailSender != null && !fromAddress.isBlank();
    if (!this.configured) {
      log.warn("[EMAIL-INIT] SMTP chưa cấu hình (MAIL_FROM_ADDRESS hoặc spring.mail.host bị thiếu). " +
               "Mọi email sẽ ghi SKIPPED vào dispatch_log.");
    }
  }

  public EmailResult send(String to, String subject, String htmlBody) {
    if (!configured) {
      log.warn("[EMAIL-SKIP] SMTP chưa cấu hình, bỏ qua email to={} subject={}", to, subject);
      return EmailResult.SKIPPED;
    }
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
      helper.setFrom(fromAddress);
      helper.setTo(to);
      helper.setSubject(subject);
      helper.setText(htmlBody, true);   // true = HTML
      mailSender.send(message);
      return EmailResult.SENT;
    } catch (MailException e) {
      log.error("[EMAIL-FAIL] Gửi email thất bại to={} err={}", to, e.getMessage());
      throw new TransientMessageException("SMTP send failed: " + e.getMessage(), e);
    } catch (MessagingException e) {
      log.error("[EMAIL-PERM-FAIL] MessagingException to={} err={}", to, e.getMessage());
      throw new PermanentMessageException("Invalid email format: " + e.getMessage());
    }
  }

  public enum EmailResult { SENT, FAILED, SKIPPED }
}
```

**Lý do chọn `@Autowired(required = false)`:** Spring Boot auto-configures `JavaMailSender` bean CHỈ khi `spring.mail.host` được set. Nếu env thiếu, bean không được tạo → `@Autowired(required=false)` cho phép inject `null` thay vì fail startup. [VERIFIED: Spring Boot docs io.email section]

### Pattern 2: Spring Mail Properties Mapping từ Env

**What:** Map env vars dạng `MAIL_*` sang `spring.mail.*` trong application.yml.

```yaml
# notification-service/src/main/resources/application.yml (thêm vào)
spring:
  mail:
    host: ${MAIL_SMTP_HOST:}           # blank = không trigger auto-config
    port: ${MAIL_SMTP_PORT:587}
    username: ${MAIL_SMTP_USERNAME:}
    password: ${MAIL_SMTP_PASSWORD:}
    default-encoding: UTF-8
    properties:
      "[mail.smtp.auth]": true
      "[mail.smtp.starttls.enable]": true
      "[mail.smtp.connectiontimeout]": 5000
      "[mail.smtp.timeout]": 3000
      "[mail.smtp.writetimeout]": 5000
```

**Lưu ý quan trọng:** Khi `spring.mail.host` là chuỗi rỗng (`""`), Spring Boot vẫn tạo `JavaMailSender` bean nhưng sẽ fail khi gọi `send()`. Để graceful degradation đúng nghĩa, cần kiểm tra trong `EmailSender` constructor thay vì chỉ dựa vào `@Autowired(required=false)`. [ASSUMED — cần test thực tế với Spring Boot 3.3.2]

### Pattern 3: HTML Email với Java Text Block

**What:** 6 template tiếng Việt dưới dạng static method trong enum `MailTemplate`.

```java
// Source: D-09, Java 15+ text block feature [VERIFIED: JDK 17 feature]
public enum MailTemplate {
  ACCOUNT_VERIFICATION {
    @Override
    public String subject() { return "Xác minh địa chỉ email của bạn"; }

    @Override
    public String render(Map<String, String> vars) {
      return """
          <!DOCTYPE html>
          <html lang="vi">
          <head><meta charset="UTF-8"><title>Xác minh email</title></head>
          <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;">
            <h2 style="color: #2563eb;">Xác minh địa chỉ email</h2>
            <p>Xin chào <strong>%s</strong>,</p>
            <p>Cảm ơn bạn đã đăng ký tại tmdt-use-gsd. Vui lòng bấm link bên dưới để xác minh email:</p>
            <a href="%s" style="background: #2563eb; color: white; padding: 12px 24px;
               text-decoration: none; border-radius: 4px; display: inline-block; margin: 16px 0;">
              Xác minh email
            </a>
            <p style="color: #6b7280; font-size: 14px;">Link có hiệu lực trong 24 giờ.</p>
          </body></html>
          """.formatted(vars.get("fullName"), vars.get("verifyUrl"));
    }
  },
  // ... 5 template còn lại tương tự
  ;
  public abstract String subject();
  public abstract String render(Map<String, String> vars);
}
```

### Pattern 4: SecureRandom Token Generation

**What:** Sinh token 32 byte, encode base64url, không padding.

```java
// Source: java.security.SecureRandom JDK docs [VERIFIED: built-in JDK 17]
import java.security.SecureRandom;
import java.util.Base64;

public class TokenGenerator {
  private static final SecureRandom RANDOM = new SecureRandom();

  public static String generate() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    // URL_ENCODER không padding → safe cho query param, không cần URL encode thêm
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    // Output: 43 char URL-safe string
  }
}
```

Token 32 byte = 256 bit entropy. URL_ENCODER: sử dụng `-` và `_` thay cho `+` và `/` → safe trong URL query param. [VERIFIED: JDK Base64 Javadoc]

### Pattern 5: User-Service RabbitMQ Producer (Mirror Phase 23)

**What:** user-service thêm RabbitTemplate + `UserRabbitMQConfig` + `AccountEventPublisher`, mirror `OrderEventPublisher` đã có.

```java
// Source: mirror OrderEventPublisher [VERIFIED: codebase]
@Component
public class AccountEventPublisher {
  private static final String USER_EXCHANGE = "user.events";
  private static final String ROUTING_KEY_REGISTERED = "user.registered";
  private static final String ROUTING_KEY_PASSWORD_RESET = "user.password-reset";

  private final RabbitTemplate rabbitTemplate;

  // afterCommit pattern: capture traceId TRƯỚC khi vào callback (Pitfall 1 Phase 23)
  public void publishUserRegistered(UserRegisteredPayload payload) {
    String traceId = MDC.get("traceId");
    final String safeTraceId = traceId != null ? traceId : "no-trace";
    final UserEventEnvelope envelope = UserEventEnvelope.createUserRegistered(safeTraceId, payload);

    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          doPublish(envelope, ROUTING_KEY_REGISTERED, safeTraceId);
        }
      });
    } else {
      doPublish(envelope, ROUTING_KEY_REGISTERED, safeTraceId);
    }
  }
  // publishPasswordReset tương tự
}
```

### Pattern 6: OrderStatusChanged Event + Payload Extension

**What:** Mở rộng `OrderEventEnvelope` thêm `OrderStatusChangedPayload`. Thêm `customerEmail` vào cả hai payload.

```java
// Source: mở rộng OrderEventEnvelope.java [VERIFIED: codebase]
public record OrderEventEnvelope(
    String eventId,
    String eventType,
    String occurredAt,
    String traceId,
    Object payload  // Union: OrderPlacedPayload | OrderStatusChangedPayload — Jackson deserialize theo eventType
) {
  // Factory mới cho OrderStatusChanged
  public static OrderEventEnvelope createOrderStatusChanged(
      String traceId, OrderStatusChangedPayload payload) {
    return new OrderEventEnvelope(
        UUID.randomUUID().toString(), "OrderStatusChanged",
        Instant.now().toString(), traceId, payload);
  }

  // Mở rộng OrderPlacedPayload: thêm customerEmail + productName
  public record OrderPlacedPayload(
      String orderId, String userId, String customerEmail,  // MỚI
      List<Item> items, BigDecimal totalAmount, String currency
  ) {}

  public record Item(
      String productId, String productName,  // productName MỚI
      int quantity, BigDecimal priceAtPurchase
  ) {}

  // Payload mới cho OrderStatusChanged
  public record OrderStatusChangedPayload(
      String orderId, String userId, String customerEmail,
      String newStatus, String customerName
  ) {}
}
```

**Vấn đề critical với Union payload:** `OrderEventEnvelope` hiện dùng `OrderPlacedPayload` hardcoded làm kiểu `payload`. Khi thêm `OrderStatusChanged`, notification-service cần deserialize đúng loại theo `eventType`. Hai cách giải quyết:
1. Dùng `Object` rồi re-deserialize với `ObjectMapper` theo `eventType` → linh hoạt nhưng verbose.
2. Tách thành 2 envelope record riêng biệt `OrderPlacedEnvelope` + `OrderStatusChangedEnvelope` → type-safe nhưng cần refactor listener.

**Khuyến nghị:** Cách 2 (tách envelope) — type-safe, dễ đọc hơn, tránh dynamic casting. [ASSUMED — planner quyết định]

### Anti-Patterns to Avoid

- **SMTP credential hardcode:** Không bao giờ đặt password Gmail trong source code. Luôn qua env var. (D-14)
- **Gửi SMTP trong transaction:** `mailSender.send()` phải ngoài `@Transactional` hoặc trong afterCommit — network I/O kéo dài transaction DB.
- **Thiếu customerEmail trong payload:** OrderPlacedPayload hiện tại KHÔNG có `customerEmail` — notification-service không biết gửi tới đâu. Phải mở rộng payload trước mọi thứ khác.
- **Token không có expiry check:** Phải check cả `expires_at > now()` VÀ `used_at IS NULL`.
- **Dùng `@Autowired` thay `@Autowired(required=false)` cho JavaMailSender:** Service sẽ crash startup nếu env SMTP thiếu.
- **payload Object field thay vì typed record:** Jackson 2 có thể deserialize `Object` thành `LinkedHashMap` thay vì record → NPE tại runtime khi gọi `.orderId()`.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| SMTP gửi email | Custom socket SMTP | `spring-boot-starter-mail` / `JavaMailSender` | STARTTLS, auth, timeout, retry đã có; MimeMessageHelper xử lý encoding |
| Token URL-safe encoding | Custom base62 | `Base64.getUrlEncoder().withoutPadding()` | JDK built-in, URL-safe, no-padding |
| Cryptographic random | UUID.randomUUID() | `SecureRandom.nextBytes(32)` | UUID chỉ 122 bit entropy và có thể predictable; SecureRandom = CSPRNG |
| Email HTML escaping | String.replace() | Java text block + `.formatted()` | Text block đã handle indentation; không cần escape nếu vars được control |
| RabbitMQ topology user.events | Topology mới khác Phase 23 | Mirror chính xác Phase 23 pattern | DLX, durable, binding key `user.#` đã proven — đừng sáng tạo lại |

**Key insight:** Phase 23 đã giải quyết toàn bộ vấn đề messaging reliability (idempotency, retry, DLQ, tracing). Phase 27 chỉ thêm "lớp SMTP" trên cùng — đừng reinvent bất cứ thứ gì từ Phase 23.

---

## Common Pitfalls

### Pitfall 1: `spring.mail.host` blank string vẫn tạo JavaMailSender

**What goes wrong:** Nếu `spring.mail.host: ${MAIL_SMTP_HOST:}` mà env `MAIL_SMTP_HOST` không set, giá trị là chuỗi rỗng. Tùy phiên bản Spring Boot, bean `JavaMailSender` có thể vẫn được tạo (với host rỗng) → `@Autowired(required=false)` inject bean không-null nhưng không hoạt động.
**Why it happens:** Spring Boot conditional `@ConditionalOnProperty(name="spring.mail.host")` match với blank string.
**How to avoid:** Trong `EmailSender` constructor, kiểm tra thêm `fromAddress.isBlank()` hoặc dùng `StringUtils.hasText(mailProperties.getHost())` để set `configured = false`.
**Warning signs:** Service khởi động OK nhưng lần đầu gửi email throw `MailSendException: No host name` thay vì log WARN SKIPPED.

### Pitfall 2: MDC traceId bị mất trong afterCommit callback

**What goes wrong:** `AccountEventPublisher.publishUserRegistered()` gọi trong transaction `AuthService.register()`. Callback `afterCommit()` có thể chạy sau khi servlet filter clear MDC.
**Why it happens:** Phase 23 đã document pitfall này trong `OrderEventPublisher` (comment line 53-54).
**How to avoid:** Capture `MDC.get("traceId")` NGAY TẠI đầu method, truyền qua closure vào `afterCommit()`. [VERIFIED: đọc OrderEventPublisher.java]

### Pitfall 3: OrderEventEnvelope.payload type mismatch

**What goes wrong:** Thêm `OrderStatusChangedPayload` vào `OrderEventEnvelope` mà không thay đổi kiểu `payload` field → notification-service deserialize sẽ fail hoặc ra `LinkedHashMap`.
**Why it happens:** Jackson deserialize `Object` field thành `LinkedHashMap<String, Object>` chứ không phải record.
**How to avoid:** Tách thành 2 envelope riêng biệt (khuyến nghị), hoặc dùng `@JsonSubTypes` + `@JsonTypeInfo` trên field payload.
**Warning signs:** `ClassCastException: class LinkedHashMap cannot be cast to class OrderStatusChangedPayload` khi listener cố gọi `.orderId()`.

### Pitfall 4: Token dùng lại sau khi verify thành công

**What goes wrong:** Link verify-email được forward/share → người khác (hoặc attacker) truy cập link thứ hai → `used_at IS NULL` check bị miss → email verified lại không cần thiết (harmless) HOẶC token reset password bị dùng lại (nghiêm trọng).
**Why it happens:** Thiếu `used_at IS NOT NULL` check, hoặc check nhưng không atomic với update.
**How to avoid:** Trong cùng một transaction: `SELECT ... FOR UPDATE` token → check `used_at IS NULL` AND `expires_at > NOW()` → set `used_at = NOW()` → thực hiện business logic. Dùng `@Transactional` trên `VerificationTokenService.verifyAndConsume()`.

### Pitfall 5: customerEmail thiếu trong OrderPlacedPayload

**What goes wrong:** notification-service không biết gửi email tới địa chỉ nào vì payload chỉ có `userId`.
**Why it happens:** Phase 23 thiết kế payload không cần email (chỉ ghi dispatch_log). Phase 27 cần nhưng payload chưa được mở rộng.
**How to avoid:** Order-service cần lấy email user TRƯỚC khi publish. Cách lấy: order-service có thể gọi REST tới user-service lúc createOrder (đã có RestTemplate), hoặc client gửi `customerEmail` lên cùng request body. Theo D-11: **payload mang sẵn dữ liệu** → option 2 hoặc fetch từ user-service. [ASSUMED — planner quyết định cách fetch customerEmail]

### Pitfall 6: Gmail App Password vs. OAuth2

**What goes wrong:** Dùng mật khẩu Gmail thông thường → bị từ chối với "Username and Password not accepted" vì Google đã tắt "Less secure app access".
**Why it happens:** Gmail yêu cầu App Password (16 ký tự không dấu cách) cho SMTP non-OAuth.
**How to avoid:** Tạo App Password tại myaccount.google.com/apppasswords (cần bật 2FA trước). Dùng account `sppteam03@gmail.com` hoặc account do user cấp. [ASSUMED: account cụ thể do user cấp runtime qua env]

---

## Code Examples

### Flyway migration V3 và V4 (user-service)

```sql
-- V3__add_email_verified.sql
-- Phase 27: thêm cột email_verified vào users (D-04)
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT false;
```

```sql
-- V4__create_verification_tokens.sql
-- Phase 27: bảng token xác minh/reset (D-05)
CREATE TABLE IF NOT EXISTS verification_tokens (
  token       VARCHAR(64)  PRIMARY KEY,
  user_id     VARCHAR(36)  NOT NULL,
  type        VARCHAR(20)  NOT NULL CHECK (type IN ('EMAIL_VERIFY', 'PASSWORD_RESET')),
  expires_at  TIMESTAMPTZ  NOT NULL,
  used_at     TIMESTAMPTZ  NULL,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_vt_user_id ON verification_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_vt_type_used ON verification_tokens(type, used_at)
    WHERE used_at IS NULL;
```

### notification-service UserRabbitMQConfig topology mới

```java
// Source: mirror RabbitMQConfig.java notification-service [VERIFIED: codebase]
// Thêm vào RabbitMQConfig.java (hoặc file mới UserTopologyConfig.java)
public static final String USER_EXCHANGE = "user.events";
public static final String USER_DLX = "user.dlx";
public static final String USER_DLQ = "user-events.dlq";
public static final String USER_DLQ_ROUTING = "user-events";
public static final String USER_NOTIFICATION_QUEUE = "notification.user-events";
public static final String USER_BINDING_KEY = "user.#";

@Bean public TopicExchange userEventsExchange() {
    return ExchangeBuilder.topicExchange(USER_EXCHANGE).durable(true).build();
}
@Bean public DirectExchange userDeadLetterExchange() {
    return ExchangeBuilder.directExchange(USER_DLX).durable(true).build();
}
@Bean public Queue userDeadLetterQueue() {
    return QueueBuilder.durable(USER_DLQ).build();
}
@Bean public Queue userNotificationQueue() {
    return QueueBuilder.durable(USER_NOTIFICATION_QUEUE)
        .withArgument("x-dead-letter-exchange", USER_DLX)
        .withArgument("x-dead-letter-routing-key", USER_DLQ_ROUTING)
        .build();
}
@Bean public Binding userNotificationBinding() {
    return BindingBuilder.bind(userNotificationQueue())
        .to(userEventsExchange()).with(USER_BINDING_KEY);
}
```

### UserEventListener skeleton

```java
// Source: mirror OrderPlacedNotifyListener.java [VERIFIED: codebase]
@Component
public class UserEventListener {
  private static final String QUEUE = "notification.user-events";

  @RabbitListener(queues = QUEUE)
  @Transactional
  public void onUserEvent(@Payload UserEventEnvelope envelope,
                           @Header(name = "X-Trace-Id", required = false) String traceId) {
    TraceIdConsumerInterceptor.enter(traceId);
    String eventId = envelope.eventId();
    try {
      boolean inserted = processedEventRepository.insertIfAbsent(eventId, envelope.eventType());
      if (!inserted) { /* skip duplicate */ return; }

      MailTemplate template = switch (envelope.eventType()) {
        case "UserRegistered"   -> MailTemplate.ACCOUNT_VERIFICATION;
        case "PasswordResetRequested" -> MailTemplate.PASSWORD_RESET;
        default -> throw new PermanentMessageException("Unknown user event: " + envelope.eventType());
      };

      // Build vars từ payload, gọi EmailSender.send(), ghi dispatch_log
      notificationDispatchService.sendUserEmail(eventId, template, envelope.payload());
    } finally {
      TraceIdConsumerInterceptor.exit();
    }
  }
}
```

### Docker-compose additions

```yaml
# user-service — thêm RabbitMQ dependency + env
user-service:
  depends_on:
    postgres-user:
      condition: service_healthy
    rabbitmq:
      condition: service_healthy
  environment:
    # ... env cũ giữ nguyên ...
    SPRING_RABBITMQ_HOST: rabbitmq
    SPRING_RABBITMQ_USER: guest
    SPRING_RABBITMQ_PASS: guest
    APP_BASE_URL: http://localhost:3000   # dựng link verify/reset

# notification-service — thêm env SMTP
notification-service:
  environment:
    # ... env cũ giữ nguyên ...
    MAIL_SMTP_HOST: ${MAIL_SMTP_HOST:-smtp.gmail.com}
    MAIL_SMTP_PORT: ${MAIL_SMTP_PORT:-587}
    MAIL_SMTP_USERNAME: ${MAIL_SMTP_USERNAME:-}
    MAIL_SMTP_PASSWORD: ${MAIL_SMTP_PASSWORD:-}
    MAIL_FROM_ADDRESS: ${MAIL_FROM_ADDRESS:-}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Gmail "Less secure app" | Gmail App Password (16 ký tự) | 2022 | Không thể dùng mật khẩu thường qua SMTP |
| Velocity/JSP email template | Java 15+ text block | Java 15 (2020) | Không cần template engine cho template đơn giản |
| `Message-ID` generation thủ công | JavaMailSender tự generate | Spring 5+ | Không cần xử lý thủ công |
| Sending trong cùng HTTP thread | afterCommit async (event-driven) | Phase 23 | SMTP không block HTTP response |

---

## Runtime State Inventory

> Phase này KHÔNG phải rename/refactor — không có runtime state cũ cần migrate.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | Không — bảng `verification_tokens` và cột `email_verified` là mới | Flyway migration tạo mới |
| Live service config | Không — RabbitMQ `user.events` exchange là mới | Khai báo qua `@Bean` (AmqpAdmin idempotent) |
| OS-registered state | Không | — |
| Secrets/env vars | `MAIL_SMTP_PASSWORD` (Gmail App Password) — chưa có trong docker-compose | Thêm vào docker-compose + .env |
| Build artifacts | Không | — |

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| RabbitMQ container | user-service Producer, notification-service Consumer | Có (docker-compose) | rabbitmq:3-management | — |
| PostgreSQL user-service | verification_tokens table | Có (docker-compose postgres-user) | 16-alpine | — |
| Gmail SMTP (smtp.gmail.com:587) | notification-service SMTP | Cần env SMTP_* | — | SKIPPED (graceful degradation D-15) |
| Gmail App Password | MAIL_SMTP_PASSWORD env | Cần user tạo thủ công | — | Service vẫn chạy, email bị SKIPPED |
| Java 17 text block | MailTemplate enum | Có (project dùng Java 17) | 17 | — |
| spring-boot-starter-mail | notification-service | Chưa có trong pom.xml | 3.3.2 (BOM) | — |
| spring-boot-starter-amqp | user-service | Chưa có trong pom.xml | 3.3.2 (BOM) | — |

**Missing dependencies với no fallback:**
- Gmail App Password: nếu không cấp env → email không được gửi (nhưng service vẫn chạy per D-15). Cần user tạo App Password trước khi demo.

**Missing dependencies với fallback:**
- `spring-boot-starter-mail` và `spring-boot-starter-amqp`: chưa trong pom.xml → Wave 0 task thêm dependency.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Testcontainers (đã có trong notification-service pom.xml) |
| Config file | Không có junit-platform.properties riêng; Spring Boot Test convention |
| Quick run command | `mvn test -pl sources/backend/notification-service -Dtest=EmailSenderTest` |
| Full suite command | `mvn test -pl sources/backend/notification-service,sources/backend/user-service,sources/backend/order-service` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| MAIL-01 | Graceful degradation khi thiếu SMTP env | Unit | `mvn test -Dtest=EmailSenderTest#testSkipsWhenNotConfigured` | Wave 0 |
| MAIL-01 | Log WARN lúc startup khi env thiếu | Unit | `mvn test -Dtest=EmailSenderTest#testWarnOnInit` | Wave 0 |
| MAIL-02 | Token sinh + verify + expire | Unit | `mvn test -Dtest=VerificationTokenServiceTest` | Wave 0 |
| MAIL-02 | GET /auth/verify-email trả 200 + flip email_verified | Integration | `mvn test -Dtest=AuthControllerIT#testVerifyEmail` | Wave 0 |
| MAIL-02 | POST /forgot returns 200 dù email không tồn tại (anti-enum) | Integration | `mvn test -Dtest=AuthControllerIT#testForgotReturns200Always` | Wave 0 |
| MAIL-03 | OrderConfirmation email ghi dispatch_log SENT | Integration | `mvn test -Dtest=OrderPlacedNotifyListenerIT` | Có (refactor) |
| MAIL-04 | OrderStatusChanged shipped → dispatch_log SENT | Integration | `mvn test -Dtest=OrderStatusChangedListenerIT` | Wave 0 |

### Wave 0 Gaps

- [ ] `notification-service/src/test/.../email/EmailSenderTest.java` — covers MAIL-01
- [ ] `user-service/src/test/.../service/VerificationTokenServiceTest.java` — covers MAIL-02
- [ ] `user-service/src/test/.../web/AuthControllerIT.java` (verify/forgot/reset endpoints) — covers MAIL-02
- [ ] `notification-service/src/test/.../consumer/OrderStatusChangedListenerIT.java` — covers MAIL-04

---

## Open Questions (RESOLVED)

1. **Cách lấy customerEmail cho OrderPlacedPayload** — RESOLVED
   - Resolution: order-service tự fetch email (producer side — KHÔNG vi phạm D-11 vì notification-service vẫn không gọi REST). Plan 27-01-T3: order-service gắn `customerEmail` vào payload lúc publish; nếu không lấy được → fallback `customerEmail=""` → notification-service ghi `dispatch_log` status=SKIPPED. Executor xác nhận nguồn email (OrderEntity field, request body, hoặc RestTemplate tới user-service) khi đọc code.

2. **Tách hay gộp OrderPlacedNotifyListener cho OrderStatusChanged** — RESOLVED
   - Resolution: Giữ 1 listener method trên queue `notification.order-events`, branch theo `envelope.eventType()` bên trong (Plan 27-05-T1). Không tạo class listener mới.

3. **APP_BASE_URL cho link verify/reset** — RESOLVED
   - Resolution: `APP_BASE_URL=http://localhost:3000` trong docker-compose cho dev (D-19, Plan 27-05-T2). FE chạy port 3000 trên host — user nhận email và click link từ browser host → đúng.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `spring.mail.host` rỗng ("") vẫn khởi tạo JavaMailSender bean | Pitfall 1 | Graceful degradation logic cần điều chỉnh nếu bean không được tạo khi host rỗng |
| A2 | Gmail SMTP không thay đổi endpoint smtp.gmail.com:587 STARTTLS | Standard Stack | Cần kiểm tra lại nếu Google thay đổi policy |
| A3 | Gateway inject `X-User-Email` để order-service lấy customerEmail | Open Questions | Nếu không có header này, cần fetch REST từ user-service hoặc thêm vào order request body |
| A4 | Tách OrderPlacedEnvelope + OrderStatusChangedEnvelope là approach tốt hơn union Object | Pattern 3 | Nếu planner chọn union Object, cần thêm custom Jackson deserializer |
| A5 | Gmail App Password account sppteam03@gmail.com sẵn sàng để demo | Environment | Nếu account chưa có App Password, user cần tạo trước demo |

---

## Sources

### Primary (HIGH confidence)

- `sources/backend/notification-service/` — đọc trực tiếp: RabbitMQConfig.java, OrderPlacedNotifyListener.java, NotificationDispatchService.java, DispatchLogEntity.java, ProcessedEventRepository.java, pom.xml, application.yml [VERIFIED: codebase]
- `sources/backend/user-service/` — đọc trực tiếp: AuthService.java, AuthController.java, UserEntity.java, V1/V2 migrations, pom.xml [VERIFIED: codebase]
- `sources/backend/order-service/` — đọc trực tiếp: OrderEventPublisher.java, OrderEventEnvelope.java, OrderCrudService.java, AdminOrderController.java [VERIFIED: codebase]
- `docker-compose.yml` — đọc trực tiếp [VERIFIED: codebase]
- Spring Boot docs https://docs.spring.io/spring-boot/reference/io/email.html — JavaMailSender auto-config, spring.mail.* properties [CITED]
- Spring Framework docs https://docs.spring.io/spring-framework/reference/integration/email.html — MimeMessageHelper HTML email pattern [CITED]

### Secondary (MEDIUM confidence)

- `.planning/phases/27-real-email-smtp/27-CONTEXT.md` — decisions D-01..D-19 [VERIFIED: user decisions]
- `.planning/phases/23-message-queue-rabbitmq/23-CONTEXT.md` — topology pattern Phase 23 [VERIFIED: project decisions]

### Tertiary (LOW confidence / ASSUMED)

- Gmail SMTP behavior với blank host env var (A1) — cần test thực tế
- Gateway header propagation cho customerEmail (A3) — chưa đọc api-gateway code

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — đọc pom.xml thực tế, confirmed spring-boot-starter-mail chưa có; Spring Boot docs verified
- Architecture: HIGH — đọc toàn bộ code liên quan, pattern mirror rõ ràng từ Phase 23
- Pitfalls: HIGH cho pitfall 2/3/4/5 (từ code thực tế); MEDIUM cho pitfall 1 (behavior blank host cần test)
- Flyway migrations: HIGH — đọc V1+V2 user-service, biết V3 là kế tiếp

**Research date:** 2026-05-22
**Valid until:** 2026-06-22 (stable Spring Boot 3.3.2, Gmail SMTP stable)
