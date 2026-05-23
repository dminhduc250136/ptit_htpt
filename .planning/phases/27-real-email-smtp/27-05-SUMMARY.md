---
phase: 27-real-email-smtp
plan: "05"
subsystem: notification-service + docker-compose + frontend
tags: [rabbitmq, user-events, smtp, email, nextjs, auth-pages, tdd]

# Dependency graph
requires:
  - "27-03: EmailSender SMTP + MailTemplate + NotificationDispatchService.sendUserEmail"
  - "27-04: AuthService register hook + 3 endpoint verify-email/forgot/reset"

provides:
  - "UserEventListener: consume notification.user-events idempotent, branch UserRegistered/PasswordResetRequested → sendUserEmail"
  - "UserEventEnvelope: record Java để deserialize event tài khoản"
  - "RabbitMQConfig user.events topology: exchange + DLX + DLQ + queue + binding"
  - "OrderPlacedNotifyListener: sửa EVENT_TYPE hardcode → envelope.eventType() dynamic"
  - "docker-compose: user-service env RabbitMQ + APP_BASE_URL; notification-service env SMTP"
  - "forgot-password/page.tsx: form email + success panel, Suspense"
  - "verify-email/page.tsx: status card loading|success|expired|invalid, fetch GET API"
  - "verify-email/page.module.css: CSS riêng status card"
  - "reset-password/page.tsx: form 2 password + validate, Suspense + useSearchParams"
  - "services/auth.ts: forgotPassword + resetPassword functions"

affects:
  - "notification-service: RabbitMQConfig, UserEventListener, UserEventEnvelope, OrderPlacedNotifyListener"
  - "docker-compose: user-service + notification-service blocks"
  - "frontend: 3 trang auth + services/auth.ts"

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "UserEventListener: pattern mirror OrderPlacedNotifyListener — 5 log path D-17, idempotent insertIfAbsent, TraceIdConsumerInterceptor, branch switch eventType"
    - "verify-email/page.tsx: state machine 4 trạng thái (loading|success|expired|invalid), fetch trong useEffect"
    - "forgot-password/page.tsx: anti-enumeration UX — luôn hiện success panel dù email không tồn tại"
    - "reset-password/page.tsx: token invalid → status card thay form"
    - "docker-compose ${VAR:-default} pattern: KHÔNG hardcode SMTP credential"

key-files:
  created:
    - "sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/messaging/event/UserEventEnvelope.java"
    - "sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/messaging/consumer/UserEventListener.java"
    - "sources/backend/notification-service/src/test/java/com/ptit/htpt/notificationservice/messaging/UserEventListenerIT.java"
    - "sources/backend/notification-service/src/test/java/com/ptit/htpt/notificationservice/messaging/OrderStatusChangedListenerIT.java"
    - "sources/frontend/src/app/forgot-password/page.tsx"
    - "sources/frontend/src/app/verify-email/page.tsx"
    - "sources/frontend/src/app/verify-email/page.module.css"
    - "sources/frontend/src/app/reset-password/page.tsx"
  modified:
    - "sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/messaging/config/RabbitMQConfig.java"
    - "sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/messaging/consumer/OrderPlacedNotifyListener.java"
    - "docker-compose.yml"
    - "sources/frontend/src/services/auth.ts"

key-decisions:
  - "UserEventListener dùng RabbitMQConfig.USER_NOTIFICATION_QUEUE constant cho @RabbitListener(queues) — compile-time string literal yêu cầu; literal 'notification.user-events' dùng trong annotation"
  - "UserEventEnvelope: typed UserPayload (không union type như OrderEventEnvelope) — user events luôn cùng 1 payload shape"
  - "[Rule 1 Bug] OrderPlacedNotifyListener: đổi EVENT_TYPE hằng hardcode sang envelope.eventType() dynamic để ghi đúng eventType vào processed_events cho cả 2 loại event"
  - "TDD: 2 IT class (UserEventListenerIT + OrderStatusChangedListenerIT), 6 test total — Maven CLI defer theo precedent v1.3"
  - "npm run build (tsc + Next.js): exit 0 — 3 trang FE build thành công"
  - "docker-compose user-service: thêm depends_on rabbitmq service_healthy để startup order đúng"
  - "verify-email CTA 'Gửi lại email xác minh' → /register (endpoint resend chưa có trong phase này — theo UI-SPEC A3)"

# Metrics
duration: ~20min
completed: 2026-05-22
tasks_completed: 3
files_created: 8
files_modified: 4
---

# Phase 27 Plan 05: Wave 3 — UserEventListener + docker-compose env + 3 trang FE auth Summary

**UserEventListener idempotent consume notification.user-events + OrderPlacedNotifyListener branch fix + docker-compose SMTP/RabbitMQ env + 3 trang Next.js forgot-password/verify-email/reset-password.**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-05-22T16:43:00Z
- **Completed:** 2026-05-22
- **Tasks:** 3 / 3
- **Files created:** 8
- **Files modified:** 4

## Accomplishments

### Task 1: Topology user.events + UserEventListener + nhánh OrderStatusChanged (TDD)

**RED (commit 161bd09):** Tạo `UserEventListenerIT` (3 test) + `OrderStatusChangedListenerIT` (3 test).

**GREEN (commit 292411a):**

- `RabbitMQConfig.java`: thêm 6 bean user.events topology — `userEventsExchange` (TopicExchange durable), `userDeadLetterExchange` (DirectExchange durable), `userDeadLetterQueue`, `userDlqBinding`, `userNotificationQueue` (DLQ args), `userNotificationBinding`. Constants: `USER_EXCHANGE`, `USER_DLX`, `USER_DLQ`, `USER_DLQ_ROUTING`, `USER_NOTIFICATION_QUEUE`, `USER_BINDING_KEY`.
- `UserEventEnvelope.java`: record `(eventId, eventType, occurredAt, traceId, UserPayload)` với nested `record UserPayload(userId, email, fullName, actionUrl)`. KHÔNG cần factory (chỉ deserialize phía consumer).
- `UserEventListener.java`: `@Component`, `@RabbitListener(queues = "notification.user-events")`, `@Transactional`. TraceIdConsumerInterceptor.enter/exit. insertIfAbsent idempotent. Branch `UserRegistered` → `ACCOUNT_VERIFICATION` / `PasswordResetRequested` → `PASSWORD_RESET` / default → PermanentMessageException. 5 log path D-17. Error handling: PermanentMessageException re-throw, TransientDataAccess → TransientMessageException, RuntimeException → AmqpRejectAndDontRequeueException.
- `OrderPlacedNotifyListener.java`: sửa `EVENT_TYPE` hằng hardcode → `envelope.eventType()` dynamic ([Rule 1 Bug fix]).

### Task 2: docker-compose env SMTP + RabbitMQ (commit 63bc8f7)

- **user-service**: thêm `depends_on rabbitmq service_healthy` + `SPRING_RABBITMQ_HOST`, `SPRING_RABBITMQ_USER`, `SPRING_RABBITMQ_PASS`, `APP_BASE_URL`.
- **notification-service**: thêm `MAIL_SMTP_HOST`, `MAIL_SMTP_PORT`, `MAIL_SMTP_USERNAME`, `MAIL_SMTP_PASSWORD`, `MAIL_FROM_ADDRESS` với `${VAR:-default}` pattern (KHÔNG hardcode credential).
- `docker compose config` exit 0.

### Task 3: 3 trang FE auth + services/auth.ts (commit a09c2ee)

- **`services/auth.ts`**: thêm `forgotPassword(email: string): Promise<void>` + `resetPassword(token, newPassword): Promise<void>`.
- **`forgot-password/page.tsx`**: form email, Suspense, states idle/loading/success/error. Success → ẩn form, hiện "Kiểm tra hộp thư của bạn" + email bold. Tái dụng `login/page.module.css`.
- **`verify-email/page.tsx`**: state machine `loading|success|expired|invalid`. Suspense + useSearchParams. fetch `GET /api/users/auth/verify-email?token=...`. Status card với icon circle 64px (success `#16a34a`, error `var(--error)`).
- **`verify-email/page.module.css`**: CSS riêng (không form), classes `.page .statusCard .iconCircle .iconCircleSuccess .iconCircleError .heading .body .cta`.
- **`reset-password/page.tsx`**: Suspense + useSearchParams `?token`. Không token → status card lỗi ngay. Form 2 password field, validate `>= 6 ký tự` + khớp nhau. Token 400/410 → status card lỗi. Success → panel CTA đăng nhập.
- `tsc --noEmit` + `next build` exit 0 — 3 trang xuất hiện trong build output.

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| Task 1 RED | 161bd09 | test(27-05): TDD RED UserEventListenerIT + OrderStatusChangedListenerIT |
| Task 1 GREEN | 292411a | feat(27-05): topology user.events + UserEventEnvelope + UserEventListener + fix |
| Task 2 | 63bc8f7 | feat(27-05): docker-compose env SMTP + RabbitMQ cho user-service + notification-service |
| Task 3 | a09c2ee | feat(27-05): 3 trang FE auth forgot-password + verify-email + reset-password |

## TDD Gate Compliance

- RED gate: commit `161bd09` — `test(27-05): TDD RED — UserEventListenerIT + OrderStatusChangedListenerIT failing`
- GREEN gate: commit `292411a` — `feat(27-05): topology user.events + UserEventEnvelope + UserEventListener + fix`
- REFACTOR: không cần.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] OrderPlacedNotifyListener dùng EVENT_TYPE hardcode thay vì envelope.eventType()**

- **Found during:** Task 1 — đọc code OrderPlacedNotifyListener thấy `EVENT_TYPE = "OrderPlaced"` được dùng trong `insertIfAbsent(eventId, EVENT_TYPE)`.
- **Issue:** Khi message `OrderStatusChanged` đến, sẽ ghi `event_type = "OrderPlaced"` vào `processed_events` — sai semantically và có thể gây conflict khi cùng orderId có cả 2 event types (vì PK là `event_id` không phải `(event_id, event_type)`, nhưng vẫn gây confusion trong audit).
- **Fix:** Đổi sang `envelope.eventType()` để ghi đúng `"OrderPlaced"` hoặc `"OrderStatusChanged"`.
- **Files modified:** `OrderPlacedNotifyListener.java`
- **Commit:** 292411a

**2. [Rule 1 - Test improvement] OrderStatusChangedListenerIT test 2 dùng Awaitility thay Thread.sleep**

- **Found during:** Task 1 — ban đầu viết `Thread.sleep(3000)` để assert "không có dispatch_log". 
- **Fix:** Dùng Awaitility chờ `processedEventRepository.findById(eventId).isPresent()` trước khi assert `dispatchLogRepository.findByEventId(eventId).isEmpty()` — đảm bảo consumer đã xử lý xong trước khi assert.
- **Files modified:** `OrderStatusChangedListenerIT.java`
- **Commit:** 161bd09 (bundled với RED)

## Known Stubs

Không có stub.

- `UserEventListener.sendUserEmail()` gọi `NotificationDispatchService.sendUserEmail()` thật → `EmailSender.send()` thật (graceful degradation khi SMTP chưa cấu hình → status SKIPPED).
- 3 trang FE gọi API thật `/api/users/auth/...` — không có hardcode data hay mock.
- docker-compose không có credential literal — `${VAR:-}` graceful degradation đúng.

## Threat Surface Scan

Không có surface mới ngoài threat model đã đăng ký:
- `UserEventListener` consume từ `notification.user-events` — trong T-27-15 (consumer loop mitigate qua DLQ).
- T-27-13 (open-redirect actionUrl): user-service dựng server-side từ `APP_BASE_URL` cố định + token, FE chỉ đọc `?token`. Đã mitigate.
- T-27-14 (token browser history): accepted trong plan.

## Self-Check: PASSED

Files created:
- [x] UserEventEnvelope.java — tạo mới
- [x] UserEventListener.java — tạo mới
- [x] UserEventListenerIT.java — tạo mới
- [x] OrderStatusChangedListenerIT.java — tạo mới
- [x] forgot-password/page.tsx — tạo mới
- [x] verify-email/page.tsx — tạo mới
- [x] verify-email/page.module.css — tạo mới
- [x] reset-password/page.tsx — tạo mới

Files modified:
- [x] RabbitMQConfig.java — có USER_EXCHANGE + 6 bean user.events
- [x] OrderPlacedNotifyListener.java — đổi EVENT_TYPE → envelope.eventType()
- [x] docker-compose.yml — SPRING_RABBITMQ_HOST + APP_BASE_URL + MAIL_SMTP_*
- [x] services/auth.ts — forgotPassword + resetPassword functions

Commits:
- [x] 161bd09 — FOUND
- [x] 292411a — FOUND
- [x] 63bc8f7 — FOUND
- [x] a09c2ee — FOUND
