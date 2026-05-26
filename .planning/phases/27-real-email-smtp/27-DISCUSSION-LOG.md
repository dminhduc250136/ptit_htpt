# Phase 27: Gửi Email Thật (SMTP) - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-22
**Phase:** 27-real-email-smtp
**Mode:** `--auto` (Claude chọn phương án khuyến nghị, log inline)
**Areas discussed:** Kiến trúc gửi email, Token & login gating, Template email, Trigger status đơn, Graceful degradation

---

## Kiến trúc gửi email (Gray Area A)

| Option | Description | Selected |
|--------|-------------|----------|
| Centralize SMTP ở notification-service | Mọi service publish event, notification-service gửi. Tái dụng Phase 23, củng cố event-driven | ✓ |
| user-service tự gửi mail tài khoản trực tiếp | Mỗi service có JavaMailSender riêng, ít topology hơn nhưng SMTP code trùng lặp 2 nơi | |

**User's choice:** `[auto]` Centralize ở notification-service — recommended (D-01, D-02, D-03).
**Notes:** Route account email qua RabbitMQ exchange mới `user.events`. order-service thêm event `OrderStatusChanged` trên exchange `order.events` đã có. Củng cố điểm cộng mục 5 đề bài (event-driven workflow).

---

## Token xác minh & login gating (Gray Area B)

| Option | Description | Selected |
|--------|-------------|----------|
| Bảng `verification_tokens` chung 2 luồng + cột `email_verified` | 1 bảng dùng cho cả verify + reset, phân biệt qua `type` | ✓ |
| Token columns trực tiếp trên `users` | Đơn giản hơn nhưng không xử lý được nhiều token / reuse | |
| Hard-gate login khi chưa verified | An toàn hơn nhưng phá E2E/UAT hiện có | |
| Soft — login không bị chặn bởi verified | Giữ luồng JWT hiện tại, defer hardening | ✓ |

**User's choice:** `[auto]` Bảng chung + soft login gate — recommended (D-04..D-08).
**Notes:** Token base64url 32-byte SecureRandom; EMAIL_VERIFY 24h, PASSWORD_RESET 1h. 3 endpoint mới; `/auth/password/forgot` luôn trả 200 chống enumeration.

---

## Nội dung & template email (Gray Area C)

| Option | Description | Selected |
|--------|-------------|----------|
| HTML qua Java text block | Không thêm dependency, giữ minimalism Phase 23 | ✓ |
| Thymeleaf/Freemarker template engine | Đẹp hơn nhưng thêm scope cho mức đồ án | |

**User's choice:** `[auto]` Java text block HTML — recommended (D-09, D-10, D-11).
**Notes:** 6 template qua enum `MailTemplate`. Payload event mang sẵn email + tên sản phẩm + URL hành động → notification-service không gọi REST cross-service.

---

## Trigger cập nhật trạng thái đơn (Gray Area D)

| Option | Description | Selected |
|--------|-------------|----------|
| Publish `OrderStatusChanged` ở `PATCH /{id}/state` afterCommit | Mirror pattern Phase 23, queue đã bind `order.#` | ✓ |
| notification-service poll DB trạng thái đơn | Phản pattern, không event-driven | |

**User's choice:** `[auto]` Publish event afterCommit — recommended (D-12, D-13).
**Notes:** Chỉ shipped/delivered/cancelled gửi email. Listener branch theo `eventType`, idempotent qua `processed_events`.

---

## Graceful degradation & observability (Gray Area E)

| Option | Description | Selected |
|--------|-------------|----------|
| Thiếu env SMTP → start bình thường, log WARN, `dispatch_log` SKIPPED | Đáp ứng MAIL-01, demo được | ✓ |
| Thiếu env SMTP → fail fast khi khởi động | Đơn giản nhưng vi phạm MAIL-01 | |

**User's choice:** `[auto]` Graceful degradation — recommended (D-14..D-17).
**Notes:** Config SMTP hoàn toàn từ env. Mọi email ghi `dispatch_log` (SENT/FAILED/SKIPPED). SMTP lỗi tạm thời → TransientMessageException → retry + DLQ Phase 23.

---

## Claude's Discretion

- Tên class cụ thể (`EmailSender`, `MailTemplate`, `VerificationTokenService`, `AccountEventPublisher`...).
- SQL chi tiết Flyway migration.
- Tách hay gộp controller verify/reset; gộp hay tách listener method.
- Bố cục HTML/CSS inline của 6 template.

## Deferred Ideas

Hard-gate login chưa verified; plain-text multipart; admin UI resend; rate-limit reset; open/click tracking; i18n; template engine. Chi tiết trong CONTEXT.md §Deferred.
