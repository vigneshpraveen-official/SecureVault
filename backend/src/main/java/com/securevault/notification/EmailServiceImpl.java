package com.securevault.notification;

import com.securevault.common.util.LogMasking;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Dispatched through the S4.6 async executor (P5.6 step 2) — email is never on the request thread.
 * Failures never break the business operation (P5.6 step 4): the operation that triggered this has
 * already committed by the time this runs (it's only ever called from NotificationEventListener's
 * AFTER_COMMIT handlers), so there is nothing left to roll back — catching here and logging WARN is
 * the whole error-handling story, deliberately.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@securevault.local}")
    private String fromAddress;

    @Override
    @Async("taskExecutor")
    public void send(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress.isBlank() ? "noreply@securevault.local" : fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.debug("Email sent: to={}, subject={}", LogMasking.maskEmail(to), subject);
        } catch (Exception ex) {
            log.warn("Failed to send email to {}: {}", LogMasking.maskEmail(to), ex.getMessage());
        }
    }
}
