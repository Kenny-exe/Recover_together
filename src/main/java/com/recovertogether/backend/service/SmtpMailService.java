package com.recovertogether.backend.service;

import com.recovertogether.backend.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * SMTP-backed mail delivery. Spring Boot only creates a {@link JavaMailSender}
 * when {@code spring.mail.host} is configured, so local development runs in
 * log-only mode instead of failing startup.
 *
 * When {@code app.mail.log-reset-codes=true} the raw reset code is logged for
 * manual testing. Keep that flag off in production: anyone with log access
 * could otherwise reset accounts.
 */
@Service
public class SmtpMailService implements MailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String fromAddress;
    private final boolean logResetCodes;

    public SmtpMailService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${app.mail.from:no-reply@recovertogether.app}") String fromAddress,
            @Value("${app.mail.log-reset-codes:false}") boolean logResetCodes) {
        this.mailSenderProvider = mailSenderProvider;
        this.fromAddress = fromAddress;
        this.logResetCodes = logResetCodes;
    }

    @Override
    public void sendPasswordResetCode(User user, String code, Duration validity) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();

        if (sender == null) {
            if (logResetCodes) {
                log.info("Password reset code for {}: {}", user.getEmail(), code);
            } else {
                log.warn("Password reset requested for {} but spring.mail.host is not configured; "
                        + "no email was sent", user.getEmail());
            }
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(user.getEmail());
            message.setSubject("Reset your RecoverTogether password");
            message.setText(buildBody(user, code, validity));
            sender.send(message);
            log.debug("Password reset email sent to {}", user.getEmail());
        } catch (Exception e) {
            // Never let an email outage break the reset workflow.
            log.warn("Failed to send password reset email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    private String buildBody(User user, String code, Duration validity) {
        return "Hi " + user.getName() + ",\n\n"
                + "Your RecoverTogether password reset code is:\n\n"
                + "    " + code + "\n\n"
                + "It expires in " + validity.toMinutes() + " minutes. "
                + "If you did not request this, you can safely ignore this email.\n\n"
                + "— RecoverTogether";
    }
}
