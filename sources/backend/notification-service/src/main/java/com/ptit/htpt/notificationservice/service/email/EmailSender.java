package com.ptit.htpt.notificationservice.service.email;

import com.ptit.htpt.notificationservice.messaging.exception.PermanentMessageException;
import com.ptit.htpt.notificationservice.messaging.exception.TransientMessageException;
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

/**
 * Phase 27 / Plan 27-03: gửi email HTML qua JavaMailSender (SMTP thật) với graceful degradation.
 *
 * <p>MAIL-01: khi thiếu env SMTP (host/from blank hoặc bean null), service khởi động bình thường,
 * log WARN một lần, mọi email ghi SKIPPED vào dispatch_log — KHÔNG crash.
 *
 * <p>Pitfall 1: kiểm tra cả fromAddress.isBlank() vì blank host vẫn có thể tạo JavaMailSender bean.
 * T-27-08: timeout 5000ms connection / 3000ms read / 5000ms write giới hạn thời gian chờ SMTP.
 * KHÔNG Lombok.
 */
@Service
public class EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final boolean configured;

    public EmailSender(
            @Autowired(required = false) JavaMailSender mailSender,
            @Value("${MAIL_FROM_ADDRESS:}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        // Pitfall 1: check cả fromAddress vì blank host vẫn có thể tạo JavaMailSender bean
        this.configured = mailSender != null && !fromAddress.isBlank();
        if (!this.configured) {
            log.warn("[EMAIL-INIT] SMTP chưa cấu hình. Mọi email sẽ ghi SKIPPED vào dispatch_log.");
        }
    }

    /**
     * Gửi email HTML. Trả EmailResult.SKIPPED khi chưa cấu hình SMTP hoặc to blank.
     * Throw TransientMessageException (SMTP lỗi tạm thời → retry → DLQ per D-17).
     * Throw PermanentMessageException khi format email sai (MessagingException).
     *
     * @param to        địa chỉ người nhận
     * @param subject   tiêu đề email
     * @param htmlBody  nội dung HTML
     * @return EmailResult.SENT / SKIPPED
     */
    public EmailResult send(String to, String subject, String htmlBody) {
        if (!configured) {
            log.warn("[EMAIL-SKIP] SMTP chưa cấu hình, bỏ qua email to={} subject={}", to, subject);
            return EmailResult.SKIPPED;
        }
        if (to == null || to.isBlank()) {
            log.warn("[EMAIL-SKIP] Địa chỉ người nhận trống, bỏ qua email subject={}", subject);
            return EmailResult.SKIPPED;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true); // true = HTML
            mailSender.send(message);
            log.info("[EMAIL-SENT] to={} subject={}", to, subject);
            return EmailResult.SENT;
        } catch (MailException e) {
            log.error("[EMAIL-FAIL] Gửi email thất bại to={} err={}", to, e.getMessage());
            throw new TransientMessageException("SMTP send failed: " + e.getMessage(), e);
        } catch (MessagingException e) {
            log.error("[EMAIL-PERM-FAIL] MessagingException to={} err={}", to, e.getMessage());
            throw new PermanentMessageException("Invalid email format: " + e.getMessage());
        }
    }

    public enum EmailResult {
        SENT, FAILED, SKIPPED
    }
}
