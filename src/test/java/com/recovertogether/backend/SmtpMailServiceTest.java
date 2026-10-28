package com.recovertogether.backend;

import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.service.SmtpMailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmtpMailServiceTest {

    @Mock
    private ObjectProvider<JavaMailSender> mailSenderProvider;

    @Mock
    private JavaMailSender mailSender;

    private SmtpMailService mailService;
    private User alice;

    @BeforeEach
    void setUp() {
        mailService = new SmtpMailService(mailSenderProvider, "no-reply@test.local", false);

        alice = new User();
        alice.setName("Alice");
        alice.setEmail("alice@example.com");
    }

    @Test
    @DisplayName("SEND: configured SMTP delivers the reset code to the user")
    void testSendWithSmtpConfigured() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);

        mailService.sendPasswordResetCode(alice, "code-123", Duration.ofMinutes(30));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage message = captor.getValue();
        assertEquals("no-reply@test.local", message.getFrom());
        assertArrayEquals(new String[]{"alice@example.com"}, message.getTo());
        assertEquals("Reset your RecoverTogether password", message.getSubject());
        assertTrue(message.getText().contains("code-123"));
        assertTrue(message.getText().contains("30 minutes"));
    }

    @Test
    @DisplayName("SEND: without SMTP config, the service is a safe no-op")
    void testSendWithoutSmtpConfigured() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(null);

        assertDoesNotThrow(() ->
                mailService.sendPasswordResetCode(alice, "code-123", Duration.ofMinutes(30)));

        verifyNoInteractions(mailSender);
    }

    @Test
    @DisplayName("SEND: SMTP failures never break the caller")
    void testSendFailureIsSwallowed() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        doThrow(new MailSendException("smtp down"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() ->
                mailService.sendPasswordResetCode(alice, "code-123", Duration.ofMinutes(30)));
    }
}
