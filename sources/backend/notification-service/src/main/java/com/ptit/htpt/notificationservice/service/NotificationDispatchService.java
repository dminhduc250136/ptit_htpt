package com.ptit.htpt.notificationservice.service;

import com.ptit.htpt.notificationservice.domain.DispatchLogEntity;
import com.ptit.htpt.notificationservice.messaging.event.OrderEventEnvelope;
import com.ptit.htpt.notificationservice.repository.DispatchLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * D-14: render template "order-confirmation" đơn giản (subject + body string concat từ payload)
 * + insert dispatch_log với status=SENT, channel=email.
 *
 * <p>KHÔNG gửi SMTP thật. KHÔNG dùng template engine ngoài (Thymeleaf/Freemarker) —
 * scope dev demo, body chỉ chứa orderId + totalAmount + số sản phẩm.
 */
@Service
public class NotificationDispatchService {

  private static final String CHANNEL_EMAIL = "email";
  private static final String STATUS_SENT = "SENT";

  private final DispatchLogRepository dispatchLogRepository;

  public NotificationDispatchService(DispatchLogRepository dispatchLogRepository) {
    this.dispatchLogRepository = dispatchLogRepository;
  }

  @Transactional
  public DispatchLogEntity recordOrderConfirmation(String eventId,
                                                    OrderEventEnvelope.OrderPlacedPayload payload) {
    String subject = "Xác nhận đơn hàng " + payload.orderId();
    StringBuilder body = new StringBuilder();
    body.append("Cảm ơn bạn đã đặt hàng!\n");
    body.append("Mã đơn: ").append(payload.orderId()).append("\n");
    body.append("Tổng tiền: ").append(payload.totalAmount()).append(" ").append(payload.currency()).append("\n");
    body.append("Số sản phẩm: ").append(payload.items().size()).append("\n");

    DispatchLogEntity log = DispatchLogEntity.create(
        eventId,
        payload.userId(),
        CHANNEL_EMAIL,
        subject,
        body.toString(),
        STATUS_SENT
    );
    return dispatchLogRepository.save(log);
  }
}
