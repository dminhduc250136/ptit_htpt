package com.ptit.htpt.notificationservice.messaging.consumer;

import com.ptit.htpt.notificationservice.messaging.config.RabbitMQConfig;
import com.ptit.htpt.notificationservice.messaging.event.UserEventEnvelope;
import com.ptit.htpt.notificationservice.messaging.exception.PermanentMessageException;
import com.ptit.htpt.notificationservice.messaging.exception.TransientMessageException;
import com.ptit.htpt.notificationservice.messaging.tracing.TraceIdConsumerInterceptor;
import com.ptit.htpt.notificationservice.repository.ProcessedEventRepository;
import com.ptit.htpt.notificationservice.service.NotificationDispatchService;
import com.ptit.htpt.notificationservice.service.email.MailTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 27 / Plan 27-05 (MAIL-02): Consume sự kiện tài khoản người dùng từ queue notification.user-events.
 *
 * <p>Event hỗ trợ:
 * - UserRegistered → gửi email xác minh tài khoản (MailTemplate.ACCOUNT_VERIFICATION)
 * - PasswordResetRequested → gửi email đặt lại mật khẩu (MailTemplate.PASSWORD_RESET)
 *
 * <p>Idempotent qua processed_events table (D-06, Pitfall 8).
 * PermanentException → DLQ ngay (D-17: re-throw PermanentMessageException).
 * TransientException → Spring retry interceptor 3 lần exp backoff.
 *
 * <p>Pattern mirror với OrderPlacedNotifyListener (Plan 23-05/27-03).
 * KHÔNG Lombok — explicit constructor injection.
 */
@Component
public class UserEventListener {

  private static final Logger log = LoggerFactory.getLogger(UserEventListener.class);
  private static final String QUEUE = RabbitMQConfig.USER_NOTIFICATION_QUEUE;

  private final ProcessedEventRepository processedEventRepository;
  private final NotificationDispatchService notificationDispatchService;

  public UserEventListener(ProcessedEventRepository processedEventRepository,
                            NotificationDispatchService notificationDispatchService) {
    this.processedEventRepository = processedEventRepository;
    this.notificationDispatchService = notificationDispatchService;
  }

  @RabbitListener(queues = "notification.user-events")
  @Transactional
  public void onUserEvent(@Payload UserEventEnvelope envelope,
                           @Header(name = "X-Trace-Id", required = false) String traceIdHeader) {
    TraceIdConsumerInterceptor.enter(traceIdHeader);
    String eventId = envelope.eventId();
    try {
      log.info("[MQ-CONSUME] queue={} eventId={} eventType={} status=received",
          QUEUE, eventId, envelope.eventType());

      // D-06 + Pitfall 8: INSERT processed_events ĐẦU TIÊN trong cùng transaction.
      boolean inserted = processedEventRepository.insertIfAbsent(eventId, envelope.eventType());
      if (!inserted) {
        log.info("[MQ-CONSUME] queue={} eventId={} status=skipped-duplicate", QUEUE, eventId);
        return;
      }

      // Branch theo eventType — gửi email tương ứng
      UserEventEnvelope.UserPayload payload = envelope.payload();
      switch (envelope.eventType()) {
        case "UserRegistered" ->
            notificationDispatchService.sendUserEmail(
                eventId, MailTemplate.ACCOUNT_VERIFICATION,
                payload.email(), payload.fullName(), payload.actionUrl());
        case "PasswordResetRequested" ->
            notificationDispatchService.sendUserEmail(
                eventId, MailTemplate.PASSWORD_RESET,
                payload.email(), payload.fullName(), payload.actionUrl());
        default ->
            throw new PermanentMessageException("Unknown user event type: " + envelope.eventType());
      }

      log.info("[MQ-CONSUME] queue={} eventId={} status=done", QUEUE, eventId);
    } catch (PermanentMessageException e) {
      // PermanentMessageException đã extend AmqpRejectAndDontRequeueException → Spring skip retry.
      log.error("[MQ-DLQ] eventId={} reason={} payload={}", eventId, e.getMessage(), envelope);
      throw e;
    } catch (DataAccessResourceFailureException | TransientDataAccessException e) {
      log.warn("[MQ-RETRY] eventId={} error={}", eventId, e.getMessage());
      throw new TransientMessageException("DB transient on consume eventId=" + eventId, e);
    } catch (RuntimeException e) {
      // Mặc định coi như permanent — tránh retry vô tận.
      log.error("[MQ-DLQ] eventId={} reason=unexpected error={} payload={}",
          eventId, e.getMessage(), envelope);
      throw new AmqpRejectAndDontRequeueException("Unexpected: " + e.getMessage(), e);
    } finally {
      TraceIdConsumerInterceptor.exit();
    }
  }
}
