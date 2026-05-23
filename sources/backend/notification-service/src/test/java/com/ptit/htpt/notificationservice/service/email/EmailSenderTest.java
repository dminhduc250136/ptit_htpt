package com.ptit.htpt.notificationservice.service.email;

import com.ptit.htpt.notificationservice.messaging.exception.TransientMessageException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Phase 27 / Plan 27-03 — unit test cho EmailSender graceful degradation.
 *
 * <p>4 test cases:
 * 1. mailSender=null → SKIPPED, không throw
 * 2. fromAddress blank → SKIPPED, không throw
 * 3. mailSender mock OK → send MimeMessage thật → trả SENT
 * 4. mailSender.send() throw MailSendException → throw TransientMessageException
 */
class EmailSenderTest {

    /**
     * Test 1: EmailSender khởi tạo với mailSender=null → send() trả SKIPPED, KHÔNG throw
     */
    @Test
    void testSkipsWhenNotConfigured() {
        EmailSender sender = new EmailSender(null, "from@example.com");

        EmailSender.EmailResult result = sender.send("to@example.com", "subject", "<p>body</p>");

        assertThat(result).isEqualTo(EmailSender.EmailResult.SKIPPED);
    }

    /**
     * Test 2: mailSender không null nhưng fromAddress blank → configured=false → send() trả SKIPPED
     */
    @Test
    void testSkipsWhenFromBlank() {
        JavaMailSender mockMailSender = mock(JavaMailSender.class);
        EmailSender sender = new EmailSender(mockMailSender, "");

        EmailSender.EmailResult result = sender.send("to@example.com", "subject", "<p>body</p>");

        assertThat(result).isEqualTo(EmailSender.EmailResult.SKIPPED);
    }

    /**
     * Test 3: mailSender mock OK → send() build MimeMessage thật (qua Session empty), gọi mailSender.send(), trả SENT
     * Dùng MimeMessage thật (không mock) để tránh ambiguous setFrom overload.
     */
    @Test
    void testSendSuccess() {
        JavaMailSender mockMailSender = mock(JavaMailSender.class);
        // Trả MimeMessage thật từ Session empty — MimeMessageHelper có thể gọi các method đúng
        MimeMessage realMimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mockMailSender.createMimeMessage()).thenReturn(realMimeMessage);
        doNothing().when(mockMailSender).send(any(MimeMessage.class));

        EmailSender sender = new EmailSender(mockMailSender, "from@example.com");

        EmailSender.EmailResult result = sender.send("to@example.com", "Tiêu đề", "<h1>Nội dung</h1>");

        assertThat(result).isEqualTo(EmailSender.EmailResult.SENT);
        verify(mockMailSender, times(1)).send(any(MimeMessage.class));
    }

    /**
     * Test 4: mailSender.send() throw MailSendException → EmailSender throw TransientMessageException
     */
    @Test
    void testThrowsTransientOnMailException() {
        JavaMailSender mockMailSender = mock(JavaMailSender.class);
        MimeMessage realMimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mockMailSender.createMimeMessage()).thenReturn(realMimeMessage);
        doThrow(new MailSendException("connection refused"))
            .when(mockMailSender).send(any(MimeMessage.class));

        EmailSender sender = new EmailSender(mockMailSender, "from@example.com");

        assertThatThrownBy(() -> sender.send("to@example.com", "subject", "<p>body</p>"))
            .isInstanceOf(TransientMessageException.class)
            .hasMessageContaining("SMTP send failed");
    }
}
