---
phase: 27-real-email-smtp
plan: 03
subsystem: notification-service
tags: [smtp, email, java-mail, graceful-degradation, template, rabbitmq]

# Dependency graph
requires:
  - phase: 27-01
    provides: OrderEventEnvelope polymorphic payload contract (customerEmail + OrderStatusChangedPayload)
  - phase: 23-message-queue-rabbitmq
    provides: notification-service RabbitMQ consumer + DispatchLogEntity + ProcessedEventRepository

provides:
  - EmailSender: gửi SMTP thật với graceful degradation (MAIL-01)
  - MailTemplate: 6 template HTML tiếng Việt
  - NotificationDispatchService: sendOrderConfirmation + sendOrderStatusChanged + sendUserEmail
  - OrderEventEnvelope (notification-service): đồng bộ với order-service — polymorphic payload

affects: [27-04, 27-05]

# Tech tracking
tech-stack:
  added: [spring-boot-starter-mail, jakarta.mail.internet.MimeMessage, org.springframework.mail.javamail.MimeMessageHelper]
  patterns:
    - EmailSender graceful degradation: @Autowired(required=false) + @Value MAIL_FROM_ADDRESS, configured = mailSender != null && !fromAddress.isBlank()
    - MailTemplate enum với 6 hằng, 2 abstract method (subject + render), Java text block HTML
    - NotificationDispatchService: inject EmailSender, ghi dispatch_log status từ EmailResult.name()
    - sendUserEmail(eventId, template, to, fullName, actionUrl) — 3 String signature tránh coupling với Plan 27-05

key-files:
  created:
    - sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/service/email/EmailSender.java
    - sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/service/email/MailTemplate.java
    - sources/backend/notification-service/src/test/java/com/ptit/htpt/notificationservice/service/email/EmailSenderTest.java
    - sources/backend/notification-service/src/test/java/com/ptit/htpt/notificationservice/service/NotificationDispatchServiceTest.java
  modified:
    - sources/backend/notification-service/pom.xml (thêm spring-boot-starter-mail)
    - sources/backend/notification-service/src/main/resources/application.yml (thêm spring.mail.* block)
    - sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/messaging/event/OrderEventEnvelope.java (đồng bộ với order-service)
    - sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/service/NotificationDispatchService.java (nâng cấp toàn bộ)
    - sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/messaging/consumer/OrderPlacedNotifyListener.java (branch switch eventType)
    - sources/backend/notification-service/src/test/java/com/ptit/htpt/notificationservice/messaging/OrderPlacedNotifyListenerIT.java (sửa Item constructor + assertion)

key-decisions:
  - "sendUserEmail signature 3 String (to, fullName, actionUrl) thay vì UserPayload — Plan 27-05 UserEventListener truyền trực tiếp, tránh coupling cross-service type"
  - "recordOrderConfirmation @Deprecated delegate sang sendOrderConfirmation — backward compat, xóa ở Plan 27-05 khi listener chuyển sang gọi sendOrderConfirmation"
  - "OrderPlacedNotifyListenerIT assertion status isIn(SENT, SKIPPED) — graceful degradation khi SMTP chưa cấu hình trong test environment"
  - "MailTemplate ORDER_SHIPPED/DELIVERED: vars chỉ cần orderId — subject đã nói lên trạng thái"

patterns-established:
  - "Pattern: EmailSender graceful degradation — configured = mailSender != null && !fromAddress.isBlank() (Pitfall 1: blank host vẫn có thể tạo bean)"
  - "Pattern: MailTemplate enum Java text block — %% escape cho CSS %s placeholder, vars Map.of() từ caller"
  - "Pattern: EmailResult.name() làm status value — SENT/FAILED/SKIPPED fit VARCHAR(16)"

requirements-completed: [MAIL-01, MAIL-03, MAIL-04]

# Metrics
duration: 8min
completed: 2026-05-22
---

# Phase 27 Plan 03: EmailSender SMTP + MailTemplate + NotificationDispatchService Upgrade Summary

**EmailSender SMTP với graceful degradation (MAIL-01) + 6 template HTML tiếng Việt + NotificationDispatchService gọi email thật và ghi dispatch_log SENT/FAILED/SKIPPED.**

## Performance

- **Duration:** ~8 min
- **Started:** 2026-05-22T16:26:46Z
- **Completed:** 2026-05-22T16:34:49Z
- **Tasks:** 3 / 3
- **Files created:** 4
- **Files modified:** 6

## Accomplishments

### Task 1: spring-boot-starter-mail + spring.mail config + EmailSender graceful degradation (TDD)

- Thêm `spring-boot-starter-mail` vào `pom.xml` (BOM 3.3.2 tự quản version)
- Thêm `spring.mail.*` block vào `application.yml`: host/port/username/password từ env `MAIL_SMTP_*`, không credential literal (T-27-07), timeout 5s/3s/5s (T-27-08)
- `EmailSender.java`: `@Autowired(required=false) JavaMailSender`, `@Value("${MAIL_FROM_ADDRESS:}")`, `configured = mailSender != null && !fromAddress.isBlank()` (Pitfall 1)
- Graceful degradation: SKIPPED khi chưa cấu hình, log WARN một lần — KHÔNG crash startup
- `EmailResult` enum: SENT / FAILED / SKIPPED
- Throw `TransientMessageException` (MailException → retry) và `PermanentMessageException` (MessagingException → DLQ)
- `EmailSenderTest` 4 test GREEN: SKIPPED-null / SKIPPED-blank / SENT-success / TransientException

### Task 2: MailTemplate enum 6 template HTML tiếng Việt

- `MailTemplate.java`: enum với 6 hằng, 2 abstract method `subject()` + `render(Map<String,String> vars)`
- 6 template: `ACCOUNT_VERIFICATION`, `PASSWORD_RESET`, `ORDER_CONFIRMATION`, `ORDER_SHIPPED`, `ORDER_DELIVERED`, `ORDER_CANCELLED`
- HTML inline-CSS, `lang="vi"`, font Arial, max-width 600px, màu accent `#2563eb`
- Java text block `"""..."""` + `.formatted()` — KHÔNG template engine ngoài (D-09)
- `mvn -q compile` exit 0

### Task 3: Đồng bộ OrderEventEnvelope + nâng cấp NotificationDispatchService (TDD)

- `OrderEventEnvelope.java` (notification-service): đồng bộ chính xác với order-service (Plan 27-01) — `payload Object` + `@JsonTypeInfo/@JsonSubTypes`, thêm `customerEmail`/`productName`, thêm `OrderStatusChangedPayload`, accessor helper `orderPlacedPayload()`/`orderStatusChangedPayload()`
- `NotificationDispatchService.java`: inject `EmailSender`, thêm `sendOrderConfirmation` + `sendOrderStatusChanged` + `sendUserEmail(eventId, template, to, fullName, actionUrl)`
- `emailSender.send()` thật → ghi `dispatch_log` với `status = result.name()` (SENT/SKIPPED)
- `recordOrderConfirmation` `@Deprecated` delegate sang `sendOrderConfirmation` (backward compat)
- `OrderPlacedNotifyListener.java`: refactor gọi trực tiếp branch `switch(envelope.eventType())` cho cả OrderPlaced và OrderStatusChanged
- `NotificationDispatchServiceTest` 4 test GREEN

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| Task 1 | e939ada | feat(27-03): EmailSender SMTP graceful degradation + spring-boot-starter-mail |
| Task 2 | 4845c22 | feat(27-03): MailTemplate enum 6 template HTML tiếng Việt |
| Task 3 | 5a640c0 | feat(27-03): đồng bộ OrderEventEnvelope + nâng cấp NotificationDispatchService |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Sửa EmailSenderTest mock MimeMessage dùng ambiguous setFrom**
- **Found during:** Task 1 — mock `MimeMessage` trực tiếp gặp compile error "reference to setFrom is ambiguous"
- **Fix:** Dùng `MimeMessage` thật (`new MimeMessage(Session.getInstance(new Properties()))`) thay vì mock — cho phép `MimeMessageHelper` gọi method thực, `mailSender.send()` vẫn mock
- **Files modified:** EmailSenderTest.java
- **Commit:** e939ada (bundled với Task 1)

**2. [Rule 1 - Bug] Cập nhật OrderPlacedNotifyListenerIT.buildEnvelope (Item constructor 3→4 arg)**
- **Found during:** Task 3 — sau khi đồng bộ `OrderEventEnvelope.Item` thêm `productName`, IT cũ vỡ compile
- **Fix:** Cập nhật `new Item("prod-1", 1, BigDecimal)` → `new Item("prod-1", "Product 1", 1, BigDecimal)`; sửa assertion `status` từ `isEqualTo("SENT")` → `isIn("SENT", "SKIPPED")` vì SMTP không cấu hình trong test
- **Files modified:** OrderPlacedNotifyListenerIT.java
- **Commit:** 5a640c0

## Known Stubs

Không có stub. EmailSender gọi JavaMailSender thật; khi SMTP chưa cấu hình, status SKIPPED được ghi đúng semantic.

## Threat Flags

Không phát hiện threat surface mới ngoài threat model đã đăng ký (T-27-07/T-27-08/T-27-09).

## Self-Check: PASSED

Files created/modified:
- `sources/.../service/email/EmailSender.java` — FOUND (e939ada)
- `sources/.../service/email/MailTemplate.java` — FOUND (4845c22)
- `sources/.../service/NotificationDispatchService.java` — FOUND (5a640c0)
- `sources/.../messaging/event/OrderEventEnvelope.java` — FOUND (5a640c0)

Commits:
- e939ada — FOUND
- 4845c22 — FOUND
- 5a640c0 — FOUND
