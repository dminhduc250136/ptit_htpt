package com.ptit.htpt.notificationservice.service;

import com.ptit.htpt.notificationservice.domain.DispatchLogEntity;
import com.ptit.htpt.notificationservice.messaging.event.OrderEventEnvelope;
import com.ptit.htpt.notificationservice.repository.DispatchLogRepository;
import com.ptit.htpt.notificationservice.service.email.EmailSender;
import com.ptit.htpt.notificationservice.service.email.EmailSender.EmailResult;
import com.ptit.htpt.notificationservice.service.email.MailTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase 27 / Plan 27-03 — unit test cho NotificationDispatchService nâng cấp.
 *
 * <p>4 test cases:
 * 1. sendOrderConfirmation → emailSender mock SENT → dispatch_log status=SENT
 * 2. sendOrderConfirmation → emailSender mock SKIPPED → dispatch_log status=SKIPPED
 * 3. sendOrderStatusChanged(ORDER_SHIPPED) → dispatch_log subject của ORDER_SHIPPED
 * 4. sendUserEmail(ACCOUNT_VERIFICATION) → render với fullName + actionUrl → dispatch_log SENT
 */
class NotificationDispatchServiceTest {

    private DispatchLogRepository dispatchLogRepository;
    private EmailSender emailSender;
    private NotificationDispatchService service;

    @BeforeEach
    void setUp() {
        dispatchLogRepository = mock(DispatchLogRepository.class);
        emailSender = mock(EmailSender.class);
        service = new NotificationDispatchService(dispatchLogRepository, emailSender);

        // Mock save để trả lại entity nhận được
        when(dispatchLogRepository.save(any(DispatchLogEntity.class)))
            .thenAnswer(inv -> inv.getArgument(0));
    }

    /**
     * Test 1: sendOrderConfirmation với customerEmail hợp lệ + EmailSender mock SENT
     * → dispatch_log lưu status=SENT
     */
    @Test
    void sendOrderConfirmation_emailerSent_logStatusSent() {
        when(emailSender.send(anyString(), anyString(), anyString())).thenReturn(EmailResult.SENT);

        OrderEventEnvelope.OrderPlacedPayload payload = new OrderEventEnvelope.OrderPlacedPayload(
            "ORD-001", "USER-001", "customer@example.com",
            List.of(new OrderEventEnvelope.Item("P1", "Product 1", 2, BigDecimal.valueOf(100000))),
            BigDecimal.valueOf(200000), "VND"
        );

        DispatchLogEntity log = service.sendOrderConfirmation(UUID.randomUUID().toString(), payload);

        assertThat(log.status()).isEqualTo("SENT");
        verify(emailSender, times(1)).send(eq("customer@example.com"), anyString(), anyString());
    }

    /**
     * Test 2: sendOrderConfirmation khi EmailSender trả SKIPPED → dispatch_log status=SKIPPED
     */
    @Test
    void sendOrderConfirmation_emailerSkipped_logStatusSkipped() {
        when(emailSender.send(anyString(), anyString(), anyString())).thenReturn(EmailResult.SKIPPED);

        OrderEventEnvelope.OrderPlacedPayload payload = new OrderEventEnvelope.OrderPlacedPayload(
            "ORD-002", "USER-002", "",
            List.of(new OrderEventEnvelope.Item("P1", "Product 1", 1, BigDecimal.valueOf(50000))),
            BigDecimal.valueOf(50000), "VND"
        );

        DispatchLogEntity log = service.sendOrderConfirmation(UUID.randomUUID().toString(), payload);

        assertThat(log.status()).isEqualTo("SKIPPED");
    }

    /**
     * Test 3: sendOrderStatusChanged(ORDER_SHIPPED) → dispatch_log subject của ORDER_SHIPPED
     */
    @Test
    void sendOrderStatusChanged_shipped_logSubjectMatches() {
        when(emailSender.send(anyString(), anyString(), anyString())).thenReturn(EmailResult.SENT);

        OrderEventEnvelope.OrderStatusChangedPayload payload = new OrderEventEnvelope.OrderStatusChangedPayload(
            "ORD-003", "USER-003", "customer@example.com", "shipped", "Nguyễn Văn A"
        );

        DispatchLogEntity log = service.sendOrderStatusChanged(
            UUID.randomUUID().toString(), payload, MailTemplate.ORDER_SHIPPED);

        assertThat(log.subject()).isEqualTo(MailTemplate.ORDER_SHIPPED.subject());
        assertThat(log.status()).isEqualTo("SENT");
    }

    /**
     * Test 4: sendUserEmail(ACCOUNT_VERIFICATION) → render với fullName + actionUrl → dispatch_log SENT
     */
    @Test
    void sendUserEmail_accountVerification_logStatusSent() {
        when(emailSender.send(anyString(), anyString(), anyString())).thenReturn(EmailResult.SENT);

        DispatchLogEntity log = service.sendUserEmail(
            UUID.randomUUID().toString(),
            MailTemplate.ACCOUNT_VERIFICATION,
            "user@example.com",
            "Nguyễn Văn A",
            "https://example.com/verify-email?token=abc123"
        );

        assertThat(log.status()).isEqualTo("SENT");
        assertThat(log.subject()).isEqualTo(MailTemplate.ACCOUNT_VERIFICATION.subject());
        verify(emailSender, times(1)).send(eq("user@example.com"), anyString(), contains("Nguyễn Văn A"));
    }
}
