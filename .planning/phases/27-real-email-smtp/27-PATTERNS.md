# Phase 27: Gửi Email Thật (SMTP) — Pattern Map

**Mapped:** 2026-05-22
**Files analyzed:** 22 (new/modified files)
**Analogs found:** 20 / 22

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `notification-service/.../messaging/config/RabbitMQConfig.java` | config | event-driven | `notification-service/.../messaging/config/RabbitMQConfig.java` (existing) | exact — thêm user.events topology |
| `notification-service/.../service/email/EmailSender.java` | service | request-response | `notification-service/.../service/NotificationDispatchService.java` | role-match |
| `notification-service/.../service/email/MailTemplate.java` | utility | transform | `notification-service/.../service/NotificationDispatchService.java` (string-concat) | role-match |
| `notification-service/.../service/NotificationDispatchService.java` | service | CRUD | self (nâng cấp) | exact |
| `notification-service/.../messaging/consumer/UserEventListener.java` | consumer | event-driven | `notification-service/.../messaging/consumer/OrderPlacedNotifyListener.java` | exact |
| `notification-service/.../messaging/consumer/OrderPlacedNotifyListener.java` | consumer | event-driven | self (mở rộng) | exact |
| `notification-service/.../messaging/event/OrderEventEnvelope.java` | model | event-driven | `order-service/.../messaging/event/OrderEventEnvelope.java` | exact |
| `notification-service/.../messaging/event/UserEventEnvelope.java` | model | event-driven | `order-service/.../messaging/event/OrderEventEnvelope.java` | role-match |
| `notification-service/src/main/resources/application.yml` | config | — | self (thêm spring.mail.*) | exact |
| `notification-service/pom.xml` | config | — | `order-service/pom.xml` (pattern khai báo starter) | role-match |
| `user-service/.../messaging/config/UserRabbitMQConfig.java` | config | event-driven | `notification-service/.../messaging/config/RabbitMQConfig.java` | exact |
| `user-service/.../messaging/publisher/AccountEventPublisher.java` | publisher | event-driven | `order-service/.../messaging/publisher/OrderEventPublisher.java` | exact |
| `user-service/.../service/VerificationTokenService.java` | service | CRUD | `user-service/.../service/AuthService.java` | role-match |
| `user-service/.../service/AuthService.java` | service | CRUD | self (sửa register + thêm verify/reset) | exact |
| `user-service/.../web/AuthController.java` | controller | request-response | self (thêm 3 endpoint) | exact |
| `user-service/.../domain/UserEntity.java` | model | CRUD | self (thêm emailVerified) | exact |
| `user-service/.../domain/VerificationTokenEntity.java` | model | CRUD | `user-service/.../domain/UserEntity.java` | role-match |
| `user-service/src/main/resources/db/migration/V102__add_email_verified.sql` | migration | — | `user-service/.../db/migration/V2__add_fullname_phone.sql` | exact |
| `user-service/src/main/resources/db/migration/V103__create_verification_tokens.sql` | migration | — | `notification-service/.../db/migration/V1__init_schema.sql` | role-match |
| `order-service/.../messaging/event/OrderEventEnvelope.java` | model | event-driven | self (mở rộng payload + thêm OrderStatusChangedPayload) | exact |
| `order-service/.../messaging/publisher/OrderEventPublisher.java` | publisher | event-driven | self (thêm publishOrderStatusChanged) | exact |
| `order-service/.../service/OrderCrudService.java` | service | CRUD | self (updateOrderState thêm publish) | exact |
| `frontend/src/app/forgot-password/page.tsx` | component | request-response | `sources/frontend/src/app/login/page.tsx` | exact |
| `frontend/src/app/reset-password/page.tsx` | component | request-response | `sources/frontend/src/app/register/page.tsx` | exact |
| `frontend/src/app/verify-email/page.tsx` | component | request-response | `sources/frontend/src/app/login/page.tsx` (Suspense + useSearchParams) | role-match |

---

## Pattern Assignments

### `notification-service/.../messaging/config/RabbitMQConfig.java` (config, event-driven)

**Action:** Thêm user.events topology vào file hiện có.
**Analog:** `sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/messaging/config/RabbitMQConfig.java`

**Imports pattern** (lines 1–16):
```java
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
```

**Core pattern — existing order.events topology** (lines 22–91):
```java
// Tái dụng chính xác cấu trúc này cho user.events:
// 1. public static final String USER_EXCHANGE = "user.events";
// 2. public static final String USER_DLX = "user.dlx";
// 3. public static final String USER_DLQ = "user-events.dlq";
// 4. public static final String USER_DLQ_ROUTING = "user-events";
// 5. public static final String USER_NOTIFICATION_QUEUE = "notification.user-events";
// 6. public static final String USER_BINDING_KEY = "user.#";

@Bean public TopicExchange userEventsExchange() {
    return ExchangeBuilder.topicExchange(USER_EXCHANGE).durable(true).build();
}
@Bean public DirectExchange userDeadLetterExchange() {
    return ExchangeBuilder.directExchange(USER_DLX).durable(true).build();
}
@Bean public Queue userDeadLetterQueue() {
    return QueueBuilder.durable(USER_DLQ).build();
}
@Bean public Binding userDlqBinding() {
    return BindingBuilder.bind(userDeadLetterQueue()).to(userDeadLetterExchange()).with(USER_DLQ_ROUTING);
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

**Quan trọng:** `jacksonMessageConverter` và `rabbitTemplate` bean đã có — KHÔNG khai báo lại.

---

### `notification-service/.../service/email/EmailSender.java` (service, request-response)

**Action:** File MỚI.
**Analog:** `sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/service/NotificationDispatchService.java`

**Imports pattern:**
```java
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
```

**Constructor pattern — graceful degradation (D-14, D-15):**
```java
// @Autowired(required=false): nếu spring.mail.host blank → bean null → không crash startup
public EmailSender(
    @Autowired(required = false) JavaMailSender mailSender,
    @Value("${MAIL_FROM_ADDRESS:}") String fromAddress
) {
    this.mailSender = mailSender;
    this.fromAddress = fromAddress;
    // Kiểm tra CỘNG fromAddress.isBlank() vì blank host vẫn có thể tạo bean (Pitfall 1)
    this.configured = mailSender != null && !fromAddress.isBlank();
    if (!this.configured) {
        log.warn("[EMAIL-INIT] SMTP chưa cấu hình. Mọi email sẽ ghi SKIPPED vào dispatch_log.");
    }
}
```

**Core send pattern:**
```java
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
```

**Lưu ý:** `TransientMessageException` và `PermanentMessageException` đã có tại `notification-service/.../messaging/exception/` — import trực tiếp.

---

### `notification-service/.../service/email/MailTemplate.java` (utility, transform)

**Action:** File MỚI — enum với 6 template HTML Java text block.
**Analog:** `sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/service/NotificationDispatchService.java` (pattern string-concat, nâng cấp thành text block)

**Core pattern:**
```java
public enum MailTemplate {
  ACCOUNT_VERIFICATION {
    @Override public String subject() { return "Xác minh địa chỉ email của bạn"; }
    @Override public String render(Map<String, String> vars) {
      return """
          <!DOCTYPE html><html lang="vi">...
          """.formatted(vars.get("fullName"), vars.get("verifyUrl"));
    }
  },
  PASSWORD_RESET {
    @Override public String subject() { return "Đặt lại mật khẩu"; }
    @Override public String render(Map<String, String> vars) { ... }
  },
  ORDER_CONFIRMATION { ... },
  ORDER_SHIPPED { ... },
  ORDER_DELIVERED { ... },
  ORDER_CANCELLED { ... };

  public abstract String subject();
  public abstract String render(Map<String, String> vars);
}
```

**Vars map keys cần thiết cho mỗi template:**
- `ACCOUNT_VERIFICATION`: `fullName`, `verifyUrl` (link đầy đủ từ payload)
- `PASSWORD_RESET`: `fullName`, `resetUrl` (link đầy đủ từ payload)
- `ORDER_CONFIRMATION`: `customerEmail`, `orderId`, `totalAmount`, `currency`, `itemCount`
- `ORDER_SHIPPED`: `customerEmail`, `orderId`, `newStatus`
- `ORDER_DELIVERED`: `customerEmail`, `orderId`
- `ORDER_CANCELLED`: `customerEmail`, `orderId`

---

### `notification-service/.../service/NotificationDispatchService.java` (service, CRUD)

**Action:** Nâng cấp — thêm `EmailSender` inject + gọi SMTP thật + hỗ trợ status SENT/FAILED/SKIPPED.
**Analog:** self — `sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/service/NotificationDispatchService.java`

**Hiện tại** (lines 17–48): inject `DispatchLogRepository`, method `recordOrderConfirmation()` ghi SENT hardcoded.

**Pattern mở rộng:**
```java
@Service
public class NotificationDispatchService {
  private static final String CHANNEL_EMAIL = "email";
  private final DispatchLogRepository dispatchLogRepository;
  private final EmailSender emailSender;  // MỚI

  // Method cũ: nâng cấp gọi emailSender thật
  @Transactional
  public DispatchLogEntity sendOrderConfirmation(String eventId,
                                                  OrderPlacedEnvelope.OrderPlacedPayload payload) {
    MailTemplate template = MailTemplate.ORDER_CONFIRMATION;
    Map<String, String> vars = Map.of(
        "customerEmail", payload.customerEmail(),  // FIELD MỚI trong payload
        "orderId", payload.orderId(),
        "totalAmount", payload.totalAmount().toPlainString(),
        "currency", payload.currency(),
        "itemCount", String.valueOf(payload.items().size())
    );
    String subject = template.subject();
    String body = template.render(vars);
    EmailSender.EmailResult result = emailSender.send(payload.customerEmail(), subject, body);
    return dispatchLogRepository.save(DispatchLogEntity.create(
        eventId, payload.userId(), CHANNEL_EMAIL, subject, body, result.name()
    ));
  }
  // Thêm: sendUserEmail(eventId, template, userPayload), sendOrderStatusChanged(...)
}
```

**DispatchLogEntity.create() factory** (lines 71–86 của DispatchLogEntity.java):
```java
// Signature đã có — tái dụng nguyên vẹn, chỉ thay status value:
public static DispatchLogEntity create(String eventId, String recipientUserId, String channel,
                                        String subject, String body, String status)
// status ∈ {"SENT", "FAILED", "SKIPPED"} — tất cả đều VARCHAR(16) fit schema
```

---

### `notification-service/.../messaging/consumer/UserEventListener.java` (consumer, event-driven)

**Action:** File MỚI.
**Analog:** `sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/messaging/consumer/OrderPlacedNotifyListener.java` — copy chính xác cấu trúc.

**Full pattern** (copy từ lines 1–89 của OrderPlacedNotifyListener.java):
```java
@Component
public class UserEventListener {
  private static final Logger log = LoggerFactory.getLogger(UserEventListener.class);
  private static final String QUEUE = "notification.user-events";  // compile-time constant

  private final ProcessedEventRepository processedEventRepository;
  private final NotificationDispatchService notificationDispatchService;

  // Constructor injection (giống OrderPlacedNotifyListener lines 47–51)

  @RabbitListener(queues = QUEUE)
  @Transactional
  public void onUserEvent(@Payload UserEventEnvelope envelope,
                           @Header(name = "X-Trace-Id", required = false) String traceIdHeader) {
    TraceIdConsumerInterceptor.enter(traceIdHeader);   // lines 57
    String eventId = envelope.eventId();
    try {
      log.info("[MQ-CONSUME] queue={} eventId={} status=received", QUEUE, eventId);

      // Idempotency — copy lines 63–67
      boolean inserted = processedEventRepository.insertIfAbsent(eventId, envelope.eventType());
      if (!inserted) {
        log.info("[MQ-CONSUME] queue={} eventId={} status=skipped-duplicate", QUEUE, eventId);
        return;
      }

      // Branch theo eventType (D-13)
      switch (envelope.eventType()) {
        case "UserRegistered" ->
            notificationDispatchService.sendUserEmail(eventId, MailTemplate.ACCOUNT_VERIFICATION, envelope.payload());
        case "PasswordResetRequested" ->
            notificationDispatchService.sendUserEmail(eventId, MailTemplate.PASSWORD_RESET, envelope.payload());
        default ->
            throw new PermanentMessageException("Unknown user event: " + envelope.eventType());
      }

      log.info("[MQ-CONSUME] queue={} eventId={} status=done", QUEUE, eventId);
    } catch (PermanentMessageException e) {   // copy lines 73–75
      log.error("[MQ-DLQ] eventId={} reason={} payload={}", eventId, e.getMessage(), envelope);
      throw e;
    } catch (DataAccessResourceFailureException | TransientDataAccessException e) {  // copy lines 77–79
      log.warn("[MQ-RETRY] eventId={} error={}", eventId, e.getMessage());
      throw new TransientMessageException("DB transient on consume eventId=" + eventId, e);
    } catch (RuntimeException e) {  // copy lines 81–84
      log.error("[MQ-DLQ] eventId={} reason=unexpected error={} payload={}",
          eventId, e.getMessage(), envelope);
      throw new AmqpRejectAndDontRequeueException("Unexpected: " + e.getMessage(), e);
    } finally {
      TraceIdConsumerInterceptor.exit();  // line 87
    }
  }
}
```

---

### `notification-service/.../messaging/consumer/OrderPlacedNotifyListener.java` (consumer, event-driven)

**Action:** Sửa — thêm nhánh `OrderStatusChanged` vào listener đã có.
**Analog:** self

**Pattern thêm nhánh** (sau line 68 — sau idempotency check):
```java
// Thay vì gọi trực tiếp recordOrderConfirmation, branch theo eventType:
switch (envelope.eventType()) {
  case "OrderPlaced" ->
      notificationDispatchService.sendOrderConfirmation(eventId, envelope.orderPlacedPayload());
  case "OrderStatusChanged" -> {
      // Chỉ shipped/delivered/cancelled gửi email (D-12)
      String status = envelope.orderStatusChangedPayload().newStatus();
      MailTemplate template = switch (status) {
          case "shipped"   -> MailTemplate.ORDER_SHIPPED;
          case "delivered" -> MailTemplate.ORDER_DELIVERED;
          case "cancelled" -> MailTemplate.ORDER_CANCELLED;
          default          -> null;  // pending/confirmed: bỏ qua, không gửi email
      };
      if (template != null) {
          notificationDispatchService.sendOrderStatusChanged(eventId,
              envelope.orderStatusChangedPayload(), template);
      }
  }
  default -> throw new PermanentMessageException("Unknown order event: " + envelope.eventType());
}
```

**Lưu ý:** Vì OrderEventEnvelope hiện dùng typed `OrderPlacedPayload` (không phải union), cần tách thành 2 envelope riêng (pattern A4 RESEARCH) hoặc dùng `Object` + custom deserializer. Khuyến nghị từ RESEARCH: **tách 2 envelope** `OrderPlacedEnvelope` + `OrderStatusChangedEnvelope`. Planner quyết định.

---

### `notification-service/.../messaging/event/UserEventEnvelope.java` (model, event-driven)

**Action:** File MỚI.
**Analog:** `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/messaging/event/OrderEventEnvelope.java` (lines 1–45)

**Pattern:**
```java
public record UserEventEnvelope(
    String eventId,
    String eventType,
    String occurredAt,
    String traceId,
    UserPayload payload   // typed — chứa email, fullName, actionUrl
) {
  public static UserEventEnvelope createUserRegistered(String traceId, UserPayload payload) {
    return new UserEventEnvelope(
        UUID.randomUUID().toString(), "UserRegistered",
        Instant.now().toString(), traceId, payload);
  }
  public static UserEventEnvelope createPasswordReset(String traceId, UserPayload payload) {
    return new UserEventEnvelope(
        UUID.randomUUID().toString(), "PasswordResetRequested",
        Instant.now().toString(), traceId, payload);
  }

  // D-11: payload mang sẵn dữ liệu — notification-service KHÔNG gọi REST
  public record UserPayload(
      String userId,
      String email,
      String fullName,
      String actionUrl    // link verify/reset đã dựng sẵn (APP_BASE_URL + token)
  ) {}
}
```

---

### `notification-service/src/main/resources/application.yml` (config)

**Action:** Thêm spring.mail.* block vào file hiện có.
**Analog:** self (application.yml hiện tại)

**Pattern thêm:**
```yaml
spring:
  mail:
    host: ${MAIL_SMTP_HOST:}           # blank → không trigger auto-config đúng nghĩa
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

---

### `user-service/.../messaging/config/UserRabbitMQConfig.java` (config, event-driven)

**Action:** File MỚI.
**Analog:** `sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/messaging/config/RabbitMQConfig.java` — copy y cấu trúc, chỉ thay exchange name và bỏ queue inventory.

**Imports pattern** (lines 1–16 của RabbitMQConfig.java):
```java
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
```

**Core pattern** (lines 22–92 của RabbitMQConfig.java — mirror với user.events):
```java
@Configuration
public class UserRabbitMQConfig {
  public static final String USER_EXCHANGE = "user.events";
  public static final String USER_DLX = "user.dlx";
  public static final String USER_DLQ = "user-events.dlq";
  public static final String USER_DLQ_ROUTING = "user-events";
  public static final String USER_NOTIFICATION_QUEUE = "notification.user-events";
  public static final String USER_BINDING_KEY = "user.#";
  public static final String ROUTING_KEY_REGISTERED = "user.registered";
  public static final String ROUTING_KEY_PASSWORD_RESET = "user.password-reset";

  // Khai báo exchange + DLX + DLQ + queue + binding
  // (copy y pattern từ RabbitMQConfig.java lines 33–77)

  // jacksonMessageConverter + rabbitTemplate — copy lines 80–91
  @Bean public Jackson2JsonMessageConverter jacksonMessageConverter(ObjectMapper objectMapper) { ... }
  @Bean public RabbitTemplate rabbitTemplate(ConnectionFactory cf, Jackson2JsonMessageConverter converter) {
      RabbitTemplate t = new RabbitTemplate(cf);
      t.setMessageConverter(converter);
      t.setMandatory(true);   // mandatory=true để detect routing failure
      return t;
  }
}
```

---

### `user-service/.../messaging/publisher/AccountEventPublisher.java` (publisher, event-driven)

**Action:** File MỚI.
**Analog:** `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/messaging/publisher/OrderEventPublisher.java` — copy chính xác.

**Imports pattern** (lines 1–16 của OrderEventPublisher.java):
```java
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
```

**afterCommit pattern — Pitfall 2** (lines 52–69 của OrderEventPublisher.java):
```java
public void publishUserRegistered(UserEventEnvelope.UserPayload payload) {
    // Pitfall 2: capture MDC traceId NGAY — afterCommit có thể MDC empty
    String traceId = MDC.get("traceId");
    final String safeTraceId = traceId != null ? traceId : "no-trace";
    final UserEventEnvelope envelope = UserEventEnvelope.createUserRegistered(safeTraceId, payload);

    if (TransactionSynchronizationManager.isSynchronizationActive()) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                doPublish(envelope, UserRabbitMQConfig.ROUTING_KEY_REGISTERED, safeTraceId);
            }
        });
    } else {
        doPublish(envelope, UserRabbitMQConfig.ROUTING_KEY_REGISTERED, safeTraceId);
    }
}
// publishPasswordReset tương tự với ROUTING_KEY_PASSWORD_RESET
```

**doPublish pattern** (lines 71–106 của OrderEventPublisher.java):
```java
// Copy nguyên doPublish() — chỉ thay EXCHANGE → UserRabbitMQConfig.USER_EXCHANGE
// và routingKey làm tham số dynamic (thay vì hardcode ROUTING_KEY_ORDER_PLACED)
private void doPublish(UserEventEnvelope envelope, String routingKey, String traceId) {
    // ... copy lines 72–106 với TraceIdMessagePostProcessor + CorrelationData + Publisher Confirms
}
```

**Lưu ý:** `TraceIdMessagePostProcessor` hiện có tại order-service — user-service cần class tương đương tại `user-service/.../messaging/tracing/TraceIdMessagePostProcessor.java`. Copy từ order-service.

---

### `user-service/.../service/VerificationTokenService.java` (service, CRUD)

**Action:** File MỚI.
**Analog:** `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/service/AuthService.java`

**Imports pattern** (tương tự AuthService.java lines 1–15):
```java
import com.ptit.htpt.userservice.domain.VerificationTokenEntity;
import com.ptit.htpt.userservice.repository.VerificationTokenRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
```

**Core token generation pattern:**
```java
@Service
@Transactional
public class VerificationTokenService {
    private static final SecureRandom RANDOM = new SecureRandom();

    // D-06: 32 byte → base64url, no padding → 43 char URL-safe
    public String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // D-05: type ∈ {EMAIL_VERIFY, PASSWORD_RESET}
    public VerificationTokenEntity createToken(String userId, String type, java.time.Duration ttl) {
        String token = generateToken();
        VerificationTokenEntity entity = new VerificationTokenEntity(
            token, userId, type, Instant.now().plus(ttl)
        );
        return tokenRepository.save(entity);
    }

    // Pitfall 4: SELECT FOR UPDATE + check used_at IS NULL + expires_at > NOW() trong cùng transaction
    public VerificationTokenEntity verifyAndConsume(String token, String expectedType) {
        VerificationTokenEntity entity = tokenRepository.findByTokenForUpdate(token)
            .orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token không hợp lệ"));
        if (!entity.type().equals(expectedType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token không đúng loại");
        }
        if (entity.usedAt() != null) {
            throw new ResponseStatusException(HttpStatus.GONE, "Token đã được sử dụng");
        }
        if (entity.expiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Token đã hết hạn");
        }
        entity.markUsed();
        return tokenRepository.save(entity);
    }
}
```

**Error strategy** (copy từ AuthService.java line 14): `ResponseStatusException` → `GlobalExceptionHandler` serialize → `ApiErrorResponse`.

---

### `user-service/.../service/AuthService.java` (service, CRUD)

**Action:** Sửa — chèn sinh token + publish sau `userRepo.save()` trong `register()`.
**Analog:** self

**Pattern chèn vào register()** (sau line 51 của AuthService.java — sau `userRepo.save(entity)`):
```java
// Sau userRepo.save(entity):
// 1. Sinh EMAIL_VERIFY token
String verifyToken = verificationTokenService.createToken(
    entity.id(), "EMAIL_VERIFY", Duration.ofHours(24)).token();
// 2. Dựng link đầy đủ (APP_BASE_URL inject qua @Value)
String verifyUrl = appBaseUrl + "/verify-email?token=" + verifyToken;
// 3. Publish event (afterCommit — user-service transaction vẫn active)
UserEventEnvelope.UserPayload payload = new UserEventEnvelope.UserPayload(
    entity.id(), entity.email(),
    entity.fullName() != null ? entity.fullName() : entity.username(),
    verifyUrl
);
accountEventPublisher.publishUserRegistered(payload);
// 4. JWT vẫn phát ngay (D-07 — không hard-gate)
String token = jwtUtils.issueToken(...);
return new AuthResponseDto(token, UserMapper.toDto(entity));
```

**Pattern method mới `forgotPassword()`:**
```java
// D-08 + POST /auth/password/forgot: luôn trả 200 (chống email enumeration)
public void forgotPassword(String email) {
    userRepo.findByEmail(email).ifPresent(entity -> {
        String resetToken = verificationTokenService.createToken(
            entity.id(), "PASSWORD_RESET", Duration.ofHours(1)).token();
        String resetUrl = appBaseUrl + "/reset-password?token=" + resetToken;
        UserEventEnvelope.UserPayload payload = new UserEventEnvelope.UserPayload(
            entity.id(), entity.email(),
            entity.fullName() != null ? entity.fullName() : entity.username(),
            resetUrl
        );
        accountEventPublisher.publishPasswordReset(payload);
    });
    // Không throw dù email không tồn tại — anti-enumeration
}
```

---

### `user-service/.../web/AuthController.java` (controller, request-response)

**Action:** Sửa — thêm 3 endpoint D-08.
**Analog:** self — `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/AuthController.java`

**Imports thêm** (pattern từ line 1–12 của AuthController.java):
```java
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
```

**3 endpoint mới** (thêm sau logout — sau line 63):
```java
// D-08a: GET /auth/verify-email?token=...
@GetMapping("/verify-email")
public ApiResponse<Void> verifyEmail(@RequestParam String token) {
    authService.verifyEmail(token);
    return ApiResponse.of(200, "Email xác minh thành công", null);
}

// D-08b: POST /auth/password/forgot — luôn 200 (anti-enumeration)
@PostMapping("/password/forgot")
public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
    authService.forgotPassword(request.email());
    return ApiResponse.of(200, "Nếu email tồn tại, link đặt lại đã được gửi", null);
}

// D-08c: POST /auth/password/reset
@PostMapping("/password/reset")
public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
    authService.resetPassword(request.token(), request.newPassword());
    return ApiResponse.of(200, "Mật khẩu đã được đặt lại", null);
}
```

**ApiResponse pattern** (line 41–43 của AuthController.java):
```java
// Luôn trả ApiResponse.of(statusCode, message, data) — tránh double-wrap từ ApiResponseAdvice
return ApiResponse.of(200, "message", data);
```

---

### `user-service/.../domain/UserEntity.java` (model, CRUD)

**Action:** Sửa — thêm field `emailVerified` + setter.
**Analog:** self

**Pattern thêm field** (theo pattern `deleted` field — line 51–52 của UserEntity.java):
```java
@Column(name = "email_verified", nullable = false)
private boolean emailVerified = false;
```

**Setter pattern** (theo pattern `setFullName()` — lines 96–99):
```java
public void setEmailVerified(boolean emailVerified) {
    this.emailVerified = emailVerified;
    this.updatedAt = Instant.now();
}
public boolean emailVerified() { return emailVerified; }
```

**Constructor update** (lines 63–76): thêm `emailVerified` vào constructor protected + static factory `create()` — mặc định `false`.

---

### `user-service/.../domain/VerificationTokenEntity.java` (model, CRUD)

**Action:** File MỚI.
**Analog:** `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/domain/UserEntity.java`

**Pattern** (copy cấu trúc @Entity/@Table từ UserEntity.java):
```java
@Entity
@Table(name = "verification_tokens")
public class VerificationTokenEntity {

  @Id
  @Column(length = 64, nullable = false, updatable = false)
  private String token;

  @Column(name = "user_id", length = 36, nullable = false)
  private String userId;

  @Column(length = 20, nullable = false)
  private String type;  // EMAIL_VERIFY | PASSWORD_RESET

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "used_at")
  private Instant usedAt;  // null = chưa dùng

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected VerificationTokenEntity() {}  // JPA proxy — copy từ UserEntity line 61

  public VerificationTokenEntity(String token, String userId, String type, Instant expiresAt) {
    this.token = token; this.userId = userId; this.type = type;
    this.expiresAt = expiresAt; this.createdAt = Instant.now();
  }

  public void markUsed() { this.usedAt = Instant.now(); }

  // Accessors record-style — copy pattern UserEntity lines 120–129
  public String token() { return token; }
  public String userId() { return userId; }
  public String type() { return type; }
  public Instant expiresAt() { return expiresAt; }
  public Instant usedAt() { return usedAt; }
  public Instant createdAt() { return createdAt; }
}
```

---

### `user-service/src/main/resources/db/migration/V102__add_email_verified.sql` (migration)

**Action:** File MỚI.
**Analog:** `sources/backend/user-service/src/main/resources/db/migration/V2__add_fullname_phone.sql`

**V-number:** V101 đã có (`V101__create_addresses.sql`) → dùng **V102**.

**Pattern** (copy format từ V2__add_fullname_phone.sql):
```sql
-- Phase 27: thêm cột email_verified vào users (D-04)
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT false;
```

---

### `user-service/src/main/resources/db/migration/V103__create_verification_tokens.sql` (migration)

**Action:** File MỚI.
**Analog:** `sources/backend/notification-service/src/main/resources/db/migration/V1__init_schema.sql` (CREATE TABLE pattern với INDEX)

**Pattern** (copy format CREATE TABLE + CREATE INDEX từ V1 notification-service):
```sql
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

---

### `order-service/.../messaging/event/OrderEventEnvelope.java` (model, event-driven)

**Action:** Sửa — thêm `customerEmail` vào `OrderPlacedPayload`, thêm `productName` vào `Item`, thêm `OrderStatusChangedPayload`, thêm factory method.
**Analog:** self

**Hiện tại** (lines 32–44): `OrderPlacedPayload` thiếu `customerEmail`; `Item` thiếu `productName`.

**Pattern mở rộng:**
```java
// Thêm customerEmail (Pitfall 5: CRITICAL — notification-service cần để biết gửi đâu)
public record OrderPlacedPayload(
    String orderId,
    String userId,
    String customerEmail,   // MỚI — fetch từ JWT claims hoặc user-service REST
    List<Item> items,
    BigDecimal totalAmount,
    String currency
) {}

public record Item(
    String productId,
    String productName,     // MỚI — fetch từ product-service hoặc order entity
    int quantity,
    BigDecimal priceAtPurchase
) {}

// Factory MỚI cho OrderStatusChanged
public static OrderEventEnvelope createOrderStatusChanged(
    String traceId, OrderStatusChangedPayload payload) {
  return new OrderEventEnvelope(
      UUID.randomUUID().toString(), "OrderStatusChanged",
      Instant.now().toString(), traceId, payload);  // NOTE: payload field là Object nếu union
}

public record OrderStatusChangedPayload(
    String orderId,
    String userId,
    String customerEmail,
    String newStatus,        // shipped | delivered | cancelled
    String customerName
) {}
```

**Lưu ý Pitfall 3:** Nếu giữ typed `OrderPlacedPayload` trong field `payload`, không thể thêm `OrderStatusChangedPayload` vào cùng record mà không đổi type sang `Object`. Planner phải chọn: (A) tách 2 envelope class riêng, hoặc (B) dùng `Object` + Jackson `@JsonTypeInfo`. Khuyến nghị: **tách 2 envelope** — `OrderPlacedEnvelope` (existing refactor) + `OrderStatusChangedEnvelope` (mới).

---

### `order-service/.../messaging/publisher/OrderEventPublisher.java` (publisher, event-driven)

**Action:** Sửa — thêm method `publishOrderStatusChanged(...)`.
**Analog:** self — copy pattern `publishOrderPlaced()` (lines 52–69)

**Pattern method mới:**
```java
public void publishOrderStatusChanged(OrderEventEnvelope.OrderStatusChangedPayload payload) {
    // Copy y hệt publishOrderPlaced() — chỉ thay:
    // 1. Factory: OrderEventEnvelope.createOrderStatusChanged(safeTraceId, payload)
    // 2. Routing key: RabbitMQConfig.ROUTING_KEY_ORDER_STATUS_CHANGED = "order.status-changed"
    // 3. Log: event=OrderStatusChanged
    String traceId = MDC.get("traceId");
    final String safeTraceId = traceId != null ? traceId : "no-trace";
    final OrderEventEnvelope envelope =
        OrderEventEnvelope.createOrderStatusChanged(safeTraceId, payload);

    if (TransactionSynchronizationManager.isSynchronizationActive()) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                doPublish(envelope, RabbitMQConfig.ROUTING_KEY_ORDER_STATUS_CHANGED, safeTraceId);
            }
        });
    } else {
        doPublish(envelope, RabbitMQConfig.ROUTING_KEY_ORDER_STATUS_CHANGED, safeTraceId);
    }
}
```

**RabbitMQConfig thêm constant:** `public static final String ROUTING_KEY_ORDER_STATUS_CHANGED = "order.status-changed";`

---

### `order-service/.../service/OrderCrudService.java` (service, CRUD)

**Action:** Sửa — `updateOrderState()` thêm publish `OrderStatusChanged`.
**Analog:** self — copy pattern `createOrderFromCommand()` lines 179–195

**Hiện tại `updateOrderState()`** (lines 207–212):
```java
public OrderDto updateOrderState(String id, OrderStateRequest request) {
    OrderEntity current = orderRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    current.setStatus(request.state());
    return OrderMapper.toDto(orderRepository.save(current));
}
```

**Pattern mở rộng** (chèn publish sau save — mirror lines 183–195):
```java
public OrderDto updateOrderState(String id, OrderStateRequest request) {
    OrderEntity current = orderRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    current.setStatus(request.state());
    OrderEntity saved = orderRepository.save(current);

    // D-12: publish OrderStatusChanged afterCommit (mirror createOrderFromCommand lines 183-195)
    OrderEventEnvelope.OrderStatusChangedPayload statusPayload =
        new OrderEventEnvelope.OrderStatusChangedPayload(
            saved.id(),
            saved.userId(),
            saved.customerEmail(),  // field cần thêm vào OrderEntity hoặc fetch
            request.state(),
            null  // customerName — optional, notification-service có thể bỏ qua null
        );
    orderEventPublisher.publishOrderStatusChanged(statusPayload);

    return OrderMapper.toDto(saved);
}
```

---

### `frontend/src/app/forgot-password/page.tsx` (component, request-response)

**Action:** File MỚI.
**Analog:** `sources/frontend/src/app/login/page.tsx` — copy structure chính xác.

**Imports pattern** (lines 1–12 của login/page.tsx):
```typescript
'use client';
import React, { Suspense, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import styles from '../login/page.module.css';  // tái dụng — không tạo file CSS mới
import Button from '@/components/ui/Button/Button';
import Input from '@/components/ui/Input/Input';
import Banner from '@/components/ui/Banner/Banner';
import { ApiError } from '@/services/errors';
```

**Page shell pattern** (lines 82–163 của login/page.tsx):
```typescript
// Copy `.page` + `.formContainer` + `.formHeader` + `.form` layout
// h1: "Quên mật khẩu?" / p: "Nhập email đăng ký..."
// 1 Input email + Button submit (loading state)
// Success state: replace form bằng panel text (không navigate)
// Error state: Banner trên form
// switchAuth: "Nhớ mật khẩu rồi?" + Link "/login"
```

**handleSubmit pattern** (lines 49–77 của login/page.tsx):
```typescript
const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    // client validation (email format — copy register/page.tsx lines 37–38)
    // setLoading(true) → call forgotPassword API → setLoading(false)
    // success: setSuccessState(true), không navigate
    // error 5xx: Banner "Có lỗi xảy ra. Vui lòng thử lại sau."
};
```

**Suspense wrapper** (lines 166–172 của login/page.tsx):
```typescript
export default function ForgotPasswordPage() {
  return (
    <Suspense fallback={<div className={styles.page} />}>
      <ForgotPasswordPageContent />
    </Suspense>
  );
}
```

---

### `frontend/src/app/reset-password/page.tsx` (component, request-response)

**Action:** File MỚI.
**Analog:** `sources/frontend/src/app/register/page.tsx` — 2 password field, validation match.

**Imports pattern** (lines 1–12 của register/page.tsx):
```typescript
'use client';
import React, { Suspense, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';  // đọc ?token
import styles from '../login/page.module.css';  // tái dụng
import Button from '@/components/ui/Button/Button';
import Input from '@/components/ui/Input/Input';
import Banner from '@/components/ui/Banner/Banner';
```

**useSearchParams pattern** (login/page.tsx lines 5, 39):
```typescript
// Cần Suspense wrapper vì dùng useSearchParams (Next.js App Router requirement — UI-SPEC A4)
const searchParams = useSearchParams();
const token = searchParams.get('token');
// Nếu !token → hiện error state ngay, KHÔNG gọi API
```

**Password mismatch validation** (copy register/page.tsx lines 40–41):
```typescript
if (form.newPassword !== form.confirmPassword)
  newErrors.confirmPassword = 'Mật khẩu không khớp';
```

**Token invalid/expired state:** hiện status card lỗi (tương tự verify-email) thay form — nếu API trả 400/410.

---

### `frontend/src/app/verify-email/page.tsx` (component, request-response)

**Action:** File MỚI — không phải form, là status card.
**Analog:** `sources/frontend/src/app/login/page.tsx` (Suspense + useSearchParams pattern)

**Imports pattern:**
```typescript
'use client';
import React, { Suspense, useEffect, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import styles from './page.module.css';  // CSS riêng — không phải form layout
import Button from '@/components/ui/Button/Button';
```

**Flow pattern** (UI-SPEC Interaction Contracts — verify-email):
```typescript
type VerifyState = 'loading' | 'success' | 'expired' | 'invalid';

function VerifyEmailContent() {
  const searchParams = useSearchParams();
  const token = searchParams.get('token');
  const [state, setState] = useState<VerifyState>(token ? 'loading' : 'invalid');

  useEffect(() => {
    if (!token) return;
    fetch(`/api/users/auth/verify-email?token=${token}`)
      .then(res => {
        if (res.ok) setState('success');
        else if (res.status === 410) setState('expired');
        else setState('invalid');
      })
      .catch(() => setState('invalid'));
  }, [token]);

  // Render icon circle + heading + body + Button CTA theo state
  // Success: color #16a34a (one-off exception — không có token --success)
  // Error: color var(--error)
}

export default function VerifyEmailPage() {
  return (
    <Suspense fallback={<div className={styles.page}><span>Đang xử lý...</span></div>}>
      <VerifyEmailContent />
    </Suspense>
  );
}
```

**CSS riêng** (`verify-email/page.module.css`) — không tái dụng login/page.module.css vì không có form. Cần các class: `.page`, `.statusCard`, `.iconCircle`, `.iconCircleSuccess`, `.iconCircleError`, `.heading`, `.body`, `.cta`.

---

## Shared Patterns

### Pattern 1: RabbitMQ Idempotency (processedEventRepository)

**Source:** `sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/messaging/consumer/OrderPlacedNotifyListener.java` lines 62–67
**Apply to:** `UserEventListener.java`, `OrderPlacedNotifyListener.java` (sửa)

```java
// INSERT processed_events ĐẦU TIÊN trong cùng @Transactional (Pitfall 8)
boolean inserted = processedEventRepository.insertIfAbsent(eventId, envelope.eventType());
if (!inserted) {
    log.info("[MQ-CONSUME] queue={} eventId={} status=skipped-duplicate", QUEUE, eventId);
    return;
}
```

### Pattern 2: afterCommit Transaction Synchronization

**Source:** `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/messaging/publisher/OrderEventPublisher.java` lines 53–69
**Apply to:** `AccountEventPublisher.java`, `OrderEventPublisher.java` (method mới)

```java
// CRITICAL: capture MDC.get("traceId") NGAY tại caller thread (Pitfall 1/2)
String traceId = MDC.get("traceId");
final String safeTraceId = traceId != null ? traceId : "no-trace";

if (TransactionSynchronizationManager.isSynchronizationActive()) {
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() { doPublish(envelope, routingKey, safeTraceId); }
    });
} else {
    doPublish(envelope, routingKey, safeTraceId);  // fallback: không trong transaction
}
```

### Pattern 3: Error Handling — ResponseStatusException

**Source:** `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/service/AuthService.java` lines 43–47, 62–66
**Apply to:** `VerificationTokenService.java`, `AuthService.java` (methods mới)

```java
// Tất cả service user-service dùng ResponseStatusException → GlobalExceptionHandler tự serialize
throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token không hợp lệ");
throw new ResponseStatusException(HttpStatus.GONE, "Token đã hết hạn");
```

### Pattern 4: ApiResponse.of() — tránh double-wrap

**Source:** `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/AuthController.java` lines 41–43
**Apply to:** `AuthController.java` (3 endpoint mới)

```java
// Luôn return ApiResponse.of() manually — KHÔNG return plain DTO
// ApiResponseAdvice pass-through khi body instanceof ApiResponse<?>
return ApiResponse.of(200, "message", data);
return ApiResponse.of(201, "Created", data);
return ApiResponse.of(200, "message", null);  // Void
```

### Pattern 5: TraceId Propagation (Consumer Side)

**Source:** `sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/messaging/consumer/OrderPlacedNotifyListener.java` lines 57, 87
**Apply to:** `UserEventListener.java`

```java
// Đầu method: enter traceId context
TraceIdConsumerInterceptor.enter(traceIdHeader);
// finally block: exit
TraceIdConsumerInterceptor.exit();
```

### Pattern 6: Flyway Migration Style

**Source:** `sources/backend/user-service/src/main/resources/db/migration/V2__add_fullname_phone.sql`
**Apply to:** `V102__add_email_verified.sql`, `V103__create_verification_tokens.sql`

```sql
-- Phase XX: mô tả ngắn (decision reference D-XX)
ALTER TABLE ... ADD COLUMN IF NOT EXISTS ...;
CREATE TABLE IF NOT EXISTS ... ();
CREATE INDEX IF NOT EXISTS idx_... ON ...(...);
```

### Pattern 7: CSS Module Tái Dụng (Frontend)

**Source:** `sources/frontend/src/app/register/page.tsx` line 6
**Apply to:** `forgot-password/page.tsx`, `reset-password/page.tsx`

```typescript
// Tái dụng login/page.module.css — KHÔNG tạo file CSS mới cho form pages
import styles from '../login/page.module.css';
// Classes sẵn dùng: .page, .formContainer, .formHeader, .formTitle, .formSubtitle,
//                   .form, .switchAuth, .switchLink, .forgotLink
```

### Pattern 8: DispatchLogEntity.create() — Status Values

**Source:** `sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/domain/DispatchLogEntity.java` lines 71–86
**Apply to:** `NotificationDispatchService.java` (mọi send call)

```java
// Factory signature đã có — status phải fit VARCHAR(16) constraint
DispatchLogEntity.create(eventId, recipientUserId, channel, subject, body, status);
// status ∈ {"SENT", "FAILED", "SKIPPED"} — tất cả <= 7 ký tự, OK với VARCHAR(16)
```

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `user-service/.../messaging/tracing/TraceIdMessagePostProcessor.java` | utility | event-driven | Chưa có tại user-service; copy từ `order-service/.../messaging/tracing/TraceIdMessagePostProcessor.java` |
| `user-service/.../repository/VerificationTokenRepository.java` | repository | CRUD | JPA Repository interface — standard pattern, không cần analog phức tạp |

---

## Critical Notes cho Planner

1. **Pitfall 5 — customerEmail trong OrderPlacedPayload:** `OrderCrudService.createOrderFromCommand()` hiện build payload từ `saved.id()` và `saved.userId()` (lines 185–194) nhưng KHÔNG có email. Planner cần quyết định: (a) client truyền `customerEmail` trong order request body, (b) order-service fetch từ JWT header `X-User-Email` nếu gateway inject, hoặc (c) order-service gọi REST user-service. Option (b) là ít xâm phạm nhất nếu gateway propagate header.

2. **Pitfall 3 — OrderEventEnvelope union type:** File hiện tại hardcoded `OrderPlacedPayload payload` (line 20 order-service, line 18 notification-service). Thêm `OrderStatusChanged` bắt buộc phải chọn: tách thành 2 record riêng (khuyến nghị) hoặc đổi sang `Object` + custom deserializer. Tách 2 record → phải update cả `OrderPlacedNotifyListener` imports.

3. **V-number Flyway user-service:** V101 đã có (`V101__create_addresses.sql`). Dùng **V102** và **V103**.

4. **Login page đã có link forgotLink** (login/page.tsx line 132–133): `<Link href="/forgot-password" className={styles.forgotLink}>Quên mật khẩu?</Link>` — executor KHÔNG cần thêm.

5. **Suspense bắt buộc** cho `verify-email/page.tsx` và `reset-password/page.tsx` vì dùng `useSearchParams()` trong Next.js App Router (UI-SPEC A4).

---

## Metadata

**Analog search scope:** `sources/backend/notification-service/`, `sources/backend/user-service/`, `sources/backend/order-service/`, `sources/frontend/src/app/`
**Files scanned:** 18 source files đọc trực tiếp
**Pattern extraction date:** 2026-05-22
