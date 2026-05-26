# Phase 27: Gửi Email Thật (SMTP) - Context

**Gathered:** 2026-05-22
**Status:** Ready for planning

> Được thu thập ở chế độ `--auto`: Claude tự chọn phương án khuyến nghị cho từng gray area. Mỗi quyết định được log inline trong `27-DISCUSSION-LOG.md` để review.

<domain>
## Phase Boundary

Phase này biến hệ thống từ "chỉ ghi `dispatch_log`" (Phase 23) thành **gửi email thật qua SMTP** tới hộp thư người dùng, cho ba luồng:
1. **Xác thực tài khoản** — email xác minh khi đăng ký + email reset mật khẩu (gắn vào user-service auth flow).
2. **Xác nhận đơn hàng** — khi đặt hàng thành công (consume `OrderPlaced` đã có từ Phase 23).
3. **Cập nhật trạng thái đơn** — khi đơn chuyển shipped / delivered / cancelled.

**Trong scope:**
- Thêm SMTP (Gmail App Password) vào notification-service — service gửi email duy nhất.
- user-service: thêm vai trò RabbitMQ **Producer** cho event tài khoản + token xác minh/reset + 3 endpoint auth mới.
- order-service: publish thêm event `OrderStatusChanged` khi đổi trạng thái đơn.
- notification-service: consume thêm event tài khoản + event status; render template HTML tiếng Việt; gửi SMTP thật.
- Graceful degradation: thiếu env SMTP → service vẫn khởi động, log cảnh báo.
- FE nhẹ: trang `/verify-email`, `/reset-password`, `/forgot-password`.

**Ngoài scope (defer):**
- Hard-gate đăng nhập với tài khoản chưa verified (giữ soft — defer hardening).
- Plain-text multipart alternative — chỉ gửi HTML.
- Admin UI xem/resend `dispatch_log`.
- Rate-limit request reset mật khẩu.
- Email open/click tracking, i18n đa ngôn ngữ.
- Template engine ngoài (Thymeleaf/Freemarker) — dùng Java text block.

</domain>

<decisions>
## Implementation Decisions

### Kiến trúc gửi email (Gray Area A)

- **D-01:** **notification-service là service gửi SMTP duy nhất.** Chỉ thêm `spring-boot-starter-mail` (`JavaMailSender`) vào notification-service. Tận dụng consumer + reliability infra Phase 23. Củng cố luồng event-driven (điểm cộng mục 5 đề bài).
- **D-02:** **user-service trở thành RabbitMQ Producer** cho event tài khoản. Topology mới (mirror pattern Phase 23): topic exchange `user.events` (durable), routing key `user.registered` + `user.password-reset`; queue durable `notification.user-events` bind `user.#`; DLX `user.dlx` + queue `user-events.dlq`.
- **D-03:** **order-service publish thêm event `OrderStatusChanged`** (routing key `order.status-changed`) lên exchange `order.events` đã có. Queue `notification.order-events` đã bind `order.#` → KHÔNG cần đổi topology phía order.

### Token xác minh & login gating (Gray Area B)

- **D-04:** Thêm cột `email_verified BOOLEAN NOT NULL DEFAULT false` vào `user_svc.users` qua Flyway migration mới (chọn V-number tiếp theo trong `db/migration`).
- **D-05:** Bảng mới `user_svc.verification_tokens(token VARCHAR(64) PK, user_id VARCHAR(36) NOT NULL, type VARCHAR(20) NOT NULL, expires_at TIMESTAMPTZ NOT NULL, used_at TIMESTAMPTZ NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now())`. Dùng chung cho cả 2 luồng — `type ∈ {EMAIL_VERIFY, PASSWORD_RESET}`.
- **D-06:** Token = chuỗi ngẫu nhiên URL-safe sinh bởi `SecureRandom` (32 byte → base64url). Hạn dùng: `EMAIL_VERIFY` 24h, `PASSWORD_RESET` 1h. Token đã dùng (`used_at` set) hoặc hết hạn → trả lỗi rõ ràng.
- **D-07:** **Login KHÔNG bị hard-gate bởi verification** (soft approach — defer hardening theo ưu tiên dự án). `register()` vẫn phát JWT ngay, `email_verified=false`. Link xác minh chỉ lật cờ. E2E/UAT hiện có không bị ảnh hưởng.
- **D-08:** Endpoint mới trong `AuthController` user-service:
  - `GET /auth/verify-email?token=...` — verify token, lật `email_verified=true`, đánh dấu token used.
  - `POST /auth/password/forgot` body `{email}` — luôn trả 200 (chống email enumeration); nếu email tồn tại thì publish event reset.
  - `POST /auth/password/reset` body `{token, newPassword}` — verify token, đổi mật khẩu, đánh dấu token used.

### Nội dung & template email (Gray Area C)

- **D-09:** Email **HTML render bằng Java text block** trong notification-service — KHÔNG dùng template engine ngoài (giữ minimalism Phase 23). Nội dung tiếng Việt. Chỉ gửi HTML (không multipart plain-text).
- **D-10:** 6 template, định danh qua enum `MailTemplate`: `ACCOUNT_VERIFICATION`, `PASSWORD_RESET`, `ORDER_CONFIRMATION` (nâng cấp từ string-concat Phase 23), `ORDER_SHIPPED`, `ORDER_DELIVERED`, `ORDER_CANCELLED`.
- **D-11:** **Event payload mang sẵn dữ liệu để gửi email** — notification-service KHÔNG gọi REST cross-service:
  - order-service: payload `OrderPlaced` + `OrderStatusChanged` thêm `customerEmail` + tên sản phẩm trong từng item.
  - user-service: event tài khoản mang `email`, `fullName`, và **URL hành động đã dựng sẵn** (link verify/reset đầy đủ).

### Trigger cập nhật trạng thái đơn (Gray Area D)

- **D-12:** order-service `AdminOrderController PATCH /{id}/state` → publish `OrderStatusChanged` trong `afterCommit` callback (mirror D-03 Phase 23). Chỉ trạng thái `shipped` / `delivered` / `cancelled` mới gửi email; `pending` / `confirmed` bỏ qua (notification-service quyết định theo eventType + status).
- **D-13:** notification-service: mỗi queue một `@RabbitListener` method; trong method branch theo `eventType` để chọn `MailTemplate`. Idempotent qua bảng `processed_events` đã có (D-06 Phase 23).

### Graceful degradation & observability (Gray Area E)

- **D-14:** Config SMTP đọc **hoàn toàn từ env**: `MAIL_SMTP_HOST`, `MAIL_SMTP_PORT`, `MAIL_SMTP_USERNAME`, `MAIL_SMTP_PASSWORD`, `MAIL_FROM_ADDRESS`. Gmail App Password do user cấp. KHÔNG hardcode credential.
- **D-15:** Thiếu/blank env SMTP → notification-service **vẫn khởi động bình thường**; `EmailSender` log WARN một lần lúc startup; mỗi lần gửi log WARN + ghi `dispatch_log` status=`SKIPPED`. KHÔNG crash, KHÔNG throw.
- **D-16:** Mọi lần gửi email ghi `dispatch_log` với status: `SENT` (thành công) / `FAILED` (SMTP exception) / `SKIPPED` (chưa cấu hình SMTP). Tận dụng `DispatchLogEntity` đã có.
- **D-17:** SMTP send lỗi tạm thời → throw `TransientMessageException` → retry 3 lần exp backoff Phase 23 → DLQ. Tái dụng nguyên cơ chế reliability Phase 23.

### FE & hạ tầng

- **D-18:** Trang Next.js: `/verify-email` (đọc `?token`, gọi `GET /auth/verify-email`, hiện thành công / hết hạn), `/reset-password` (đọc `?token`, form mật khẩu mới), `/forgot-password` (input email). Thêm link "Quên mật khẩu?" ở trang login.
- **D-19:** docker-compose: notification-service nhận env SMTP; user-service nhận `SPRING_RABBITMQ_HOST=rabbitmq` + `depends_on: rabbitmq` + env `APP_BASE_URL` (dựng link verify/reset).

### Claude's Discretion

Researcher/planner tự quyết trong giới hạn các D-* trên:
- Tên class cụ thể (`EmailSender`, `MailTemplate`, `VerificationTokenService`, `AccountEventPublisher`, `UserEventListener`...).
- SQL chi tiết Flyway migration `verification_tokens` + cột `email_verified`.
- Có tách `AuthController` thành controller riêng cho verify/reset hay giữ chung.
- Bố cục HTML/CSS inline của 6 template (miễn tiếng Việt, hiển thị đủ dữ liệu yêu cầu).
- Có gộp 2 listener method hay tách theo queue.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Yêu cầu & context phase trước

- `.planning/REQUIREMENTS.md` §MAIL (MAIL-01..MAIL-04) — yêu cầu gốc Phase 27
- `.planning/phases/23-message-queue-rabbitmq/23-CONTEXT.md` — topology RabbitMQ, D-06 idempotency, D-07 retry, D-08 exception, D-09 DLQ, D-15 envelope format, D-16/17 logging — Phase 27 mirror toàn bộ pattern này

### Code notification-service (consumer + dispatch — tái dụng & mở rộng)

- `sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/service/NotificationDispatchService.java` — hiện string-concat, KHÔNG gửi SMTP → nâng cấp gửi thật
- `sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/messaging/consumer/OrderPlacedNotifyListener.java` — pattern listener idempotent, thêm listener cho `user.events` + nhánh `OrderStatusChanged`
- `sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/messaging/config/RabbitMQConfig.java` — topology, thêm exchange/queue `user.*`
- `sources/backend/notification-service/src/main/java/com/ptit/htpt/notificationservice/domain/DispatchLogEntity.java` — ghi log mọi email (SENT/FAILED/SKIPPED)
- `sources/backend/notification-service/src/main/resources/db/migration/` — đếm V-number hiện tại

### Code user-service (auth flow — gắn token + producer)

- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/service/AuthService.java` — `register()` chèn sinh token + publish event; KHÔNG đổi luồng phát JWT (D-07)
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/AuthController.java` — thêm 3 endpoint D-08
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/domain/UserEntity.java` — thêm field `emailVerified` + mutator
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/TraceIdFilter.java` — pattern traceId, propagate sang message header (mirror Phase 23)
- `sources/backend/user-service/src/main/resources/db/migration/` — đếm V-number cho migration `email_verified` + `verification_tokens`

### Code order-service (producer — thêm event status)

- `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/messaging/publisher/OrderEventPublisher.java` — thêm `publishOrderStatusChanged(...)`
- `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/messaging/event/OrderEventEnvelope.java` — thêm factory `createOrderStatusChanged` + field `customerEmail` + tên sản phẩm vào payload
- `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/AdminOrderController.java` §52-57 — `PATCH /{id}/state` là chỗ chèn publish (afterCommit)

### Infrastructure

- `docker-compose.yml` — env SMTP cho notification-service; RabbitMQ env + `APP_BASE_URL` cho user-service
- `sources/backend/*/pom.xml` — pattern declare starter dependency

### Đề bài

- Memory `project_microservice-gaps.md` — lý do event-driven; D-01/D-02 chọn route qua MQ để củng cố mục 5 (điểm cộng workflow event-driven)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **notification-service consumer infra (Phase 23)**: `OrderPlacedNotifyListener`, `processed_events` idempotency, `TransientMessageException`/`PermanentMessageException`, retry + DLQ — Phase 27 tái dụng toàn bộ, chỉ thêm SMTP + template + listener mới.
- **`DispatchLogEntity.create(...)`**: đã có sẵn factory ghi log — Phase 27 dùng cho mọi email, mở rộng status `SKIPPED`/`FAILED`.
- **`OrderEventPublisher` + afterCommit pattern (order-service)**: publish sau commit đã chuẩn — `OrderStatusChanged` mirror y hệt.
- **`TraceIdFilter` + `TraceIdMessagePostProcessor`/`TraceIdConsumerInterceptor`**: propagate traceId xuyên service qua header `X-Trace-Id` — user-service producer mới dùng lại pattern.
- **`AuthService` / `JwtUtils` / `PasswordEncoder`**: luồng register/login sẵn có — chèn token + publish, không viết lại auth.

### Established Patterns

- **DB-per-service (Phase 24)**: mỗi service postgres riêng — `verification_tokens` thuộc DB user-service; `processed_events` đã ở DB notification-service.
- **Flyway `db/migration`**: schema chính; Phase 27 thêm migration cho `email_verified` + `verification_tokens`.
- **RabbitMQ topology mirror**: Phase 23 đã có `order.events` + DLX pattern — `user.events` copy y nguyên cấu trúc.
- **Commit prefix EN + body tiếng Việt**: giữ nguyên.

### Integration Points

- **user-service**: `AuthService.register()` → sinh token EMAIL_VERIFY + publish `user.registered`. 3 endpoint mới trong `AuthController`. Thêm `messaging/` package (producer + config).
- **order-service**: `AdminOrderController.PATCH /{id}/state` → afterCommit publish `OrderStatusChanged`. Mở rộng `OrderEventEnvelope` payload.
- **notification-service**: thêm `EmailSender` (JavaMailSender), enum `MailTemplate`, listener cho `notification.user-events`, nhánh `OrderStatusChanged` trong listener order. Nâng cấp `NotificationDispatchService` gửi SMTP thật.
- **docker-compose.yml**: env SMTP + RabbitMQ + `APP_BASE_URL`.
- **FE Next.js**: 3 trang mới + link "Quên mật khẩu?".

</code_context>

<specifics>
## Specific Ideas

- **Demo cho hội đồng**: đăng ký tài khoản bằng email thật → nhận mail xác minh trong hộp thư → bấm link → tài khoản verified. Đặt đơn → nhận mail xác nhận. Admin đổi trạng thái → nhận mail cập nhật. Mở RabbitMQ Management UI cho thấy event đi qua `user.events` / `order.events`.
- **Demo graceful degradation**: bỏ env SMTP → service vẫn chạy, `dispatch_log` ghi `SKIPPED` — chứng minh MAIL-01.
- **SMTP**: Gmail `smtp.gmail.com:587` STARTTLS + App Password (account `sppteam03@gmail.com` hoặc account user cấp qua env).

</specifics>

<deferred>
## Deferred Ideas

1. **Hard-gate login** với tài khoản chưa verified — giờ là soft (D-07). Phase hardening riêng.
2. **Plain-text multipart alternative** — chỉ gửi HTML.
3. **Admin UI** xem/resend `dispatch_log` — phase "Admin notification ops".
4. **Rate-limit** request reset mật khẩu — chống abuse, defer hardening.
5. **Email open/click tracking** + i18n đa ngôn ngữ.
6. **Template engine** (Thymeleaf) nếu template HTML phình to — giờ Java text block đủ.

### Reviewed Todos (not folded)

Không quét được todo system (gsd-sdk không khả dụng trên Windows path). Nếu có todo liên quan, user note thủ công.

</deferred>

---

*Phase: 27-real-email-smtp*
*Context gathered: 2026-05-22*
