package com.ptit.htpt.notificationservice.service;

import com.ptit.htpt.notificationservice.domain.DispatchLogEntity;
import com.ptit.htpt.notificationservice.messaging.event.OrderEventEnvelope;
import com.ptit.htpt.notificationservice.repository.DispatchLogRepository;
import com.ptit.htpt.notificationservice.service.email.EmailSender;
import com.ptit.htpt.notificationservice.service.email.MailTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Phase 27 / Plan 27-03: nâng cấp gửi email thật qua EmailSender + ghi dispatch_log
 * với status SENT/FAILED/SKIPPED (thay vì hardcode "SENT" như Phase 23).
 *
 * <p>MAIL-01: EmailSender tự xử lý graceful degradation — nếu SMTP chưa cấu hình,
 * EmailResult=SKIPPED và dispatch_log ghi "SKIPPED" (KHÔNG crash).
 * MAIL-03: sendOrderConfirmation gửi email xác nhận đơn hàng.
 * MAIL-04: sendOrderStatusChanged gửi email cập nhật trạng thái.
 *
 * <p>sendUserEmail signature nhận 3 String (to, fullName, actionUrl) để Plan 27-05
 * (UserEventListener) truyền trực tiếp từ UserEventEnvelope.UserPayload — tránh phụ thuộc type.
 *
 * <p>KHÔNG Lombok — explicit constructor injection.
 */
@Service
public class NotificationDispatchService {

  private static final String CHANNEL_EMAIL = "email";

  private final DispatchLogRepository dispatchLogRepository;
  private final EmailSender emailSender;

  public NotificationDispatchService(DispatchLogRepository dispatchLogRepository,
                                      EmailSender emailSender) {
    this.dispatchLogRepository = dispatchLogRepository;
    this.emailSender = emailSender;
  }

  /**
   * Gửi email xác nhận đơn hàng (MAIL-03) + ghi dispatch_log.
   *
   * @param eventId   ID của event RabbitMQ (dùng để trace và ghi dispatch_log)
   * @param payload   OrderPlacedPayload chứa customerEmail, orderId, items, totalAmount, currency
   * @return DispatchLogEntity đã lưu với status SENT/SKIPPED
   */
  @Transactional
  public DispatchLogEntity sendOrderConfirmation(String eventId,
                                                  OrderEventEnvelope.OrderPlacedPayload payload) {
    Map<String, String> vars = Map.of(
        "orderId", payload.orderId(),
        "totalAmount", payload.totalAmount().toPlainString(),
        "currency", payload.currency(),
        "itemCount", String.valueOf(payload.items().size())
    );
    String subject = MailTemplate.ORDER_CONFIRMATION.subject();
    String body = MailTemplate.ORDER_CONFIRMATION.render(vars);
    EmailSender.EmailResult result = emailSender.send(payload.customerEmail(), subject, body);
    return dispatchLogRepository.save(
        DispatchLogEntity.create(eventId, payload.userId(), CHANNEL_EMAIL, subject, body, result.name())
    );
  }

  /**
   * Gửi email cập nhật trạng thái đơn hàng (MAIL-04) + ghi dispatch_log.
   * Chỉ gọi với template ORDER_SHIPPED/ORDER_DELIVERED/ORDER_CANCELLED
   * (listener quyết định bỏ qua pending/confirmed).
   *
   * @param eventId   ID của event RabbitMQ
   * @param payload   OrderStatusChangedPayload chứa customerEmail, orderId, newStatus
   * @param template  MailTemplate tương ứng với newStatus
   * @return DispatchLogEntity đã lưu với status SENT/SKIPPED
   */
  @Transactional
  public DispatchLogEntity sendOrderStatusChanged(String eventId,
                                                   OrderEventEnvelope.OrderStatusChangedPayload payload,
                                                   MailTemplate template) {
    Map<String, String> vars = Map.of(
        "orderId", payload.orderId()
    );
    String subject = template.subject();
    String body = template.render(vars);
    EmailSender.EmailResult result = emailSender.send(payload.customerEmail(), subject, body);
    return dispatchLogRepository.save(
        DispatchLogEntity.create(eventId, payload.userId(), CHANNEL_EMAIL, subject, body, result.name())
    );
  }

  /**
   * Gửi email liên quan đến tài khoản người dùng (ACCOUNT_VERIFICATION/PASSWORD_RESET).
   *
   * <p>Signature 3 String (to, fullName, actionUrl) để Plan 27-05 UserEventListener truyền trực tiếp
   * từ UserEventEnvelope.UserPayload mà không tạo dependency vào type user-service.
   * Ghi quyết định: KHÔNG nhận UserPayload object để tránh coupling cross-service.
   *
   * @param eventId    ID của event RabbitMQ
   * @param template   MailTemplate (ACCOUNT_VERIFICATION hoặc PASSWORD_RESET)
   * @param to         địa chỉ email người nhận
   * @param fullName   tên đầy đủ (dùng trong template lời chào)
   * @param actionUrl  link đầy đủ cho nút CTA (verifyUrl hoặc resetUrl)
   * @return DispatchLogEntity đã lưu với status SENT/SKIPPED
   */
  @Transactional
  public DispatchLogEntity sendUserEmail(String eventId, MailTemplate template,
                                          String to, String fullName, String actionUrl) {
    Map<String, String> vars;
    if (template == MailTemplate.ACCOUNT_VERIFICATION) {
      vars = Map.of("fullName", fullName, "verifyUrl", actionUrl);
    } else if (template == MailTemplate.PASSWORD_RESET) {
      vars = Map.of("fullName", fullName, "resetUrl", actionUrl);
    } else {
      vars = Map.of("fullName", fullName, "actionUrl", actionUrl);
    }
    String subject = template.subject();
    String body = template.render(vars);
    // recipientUserId không có trong user event payload gọn — dùng "user" fallback,
    // Plan 27-05 có thể truyền userId nếu cần audit per-user
    EmailSender.EmailResult result = emailSender.send(to, subject, body);
    return dispatchLogRepository.save(
        DispatchLogEntity.create(eventId, to, CHANNEL_EMAIL, subject, body, result.name())
    );
  }

  /**
   * @deprecated Dùng sendOrderConfirmation thay thế (Phase 27 Plan 27-03).
   *             Giữ lại để OrderPlacedNotifyListener cũ không vỡ compile — sẽ xóa ở Plan 27-05.
   */
  @Deprecated
  @Transactional
  public DispatchLogEntity recordOrderConfirmation(String eventId,
                                                    OrderEventEnvelope.OrderPlacedPayload payload) {
    return sendOrderConfirmation(eventId, payload);
  }
}
