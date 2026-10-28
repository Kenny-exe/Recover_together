package com.recovertogether.backend;

import com.recovertogether.backend.entity.PasswordResetToken;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.enums.AuditAction;
import com.recovertogether.backend.repository.PasswordResetTokenRepository;
import com.recovertogether.backend.repository.UserRepository;
import com.recovertogether.backend.service.AuditLogService;
import com.recovertogether.backend.service.MailService;
import com.recovertogether.backend.service.PasswordResetService;
import com.recovertogether.backend.service.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private MailService mailService;

    private PasswordResetService passwordResetService;
    private User alice;

    @BeforeEach
    void setUp() {
        passwordResetService = new PasswordResetService(
                passwordResetTokenRepository,
                userRepository,
                passwordEncoder,
                refreshTokenService,
                auditLogService,
                mailService);

        alice = new User();
        ReflectionTestUtils.setField(alice, "id", 1L);
        alice.setName("Alice");
        alice.setEmail("alice@example.com");
        alice.setPassword("old-hash");
    }

    @Test
    @DisplayName("REQUEST: normalizes email, stores only a hash, and emails the raw code")
    void testRequestStoresHashedCode() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
        when(passwordResetTokenRepository.findTopByUserOrderByCreatedAtDesc(alice))
                .thenReturn(Optional.empty());
        when(passwordResetTokenRepository.countByUserAndCreatedAtAfter(eq(alice), any()))
                .thenReturn(0L);
        when(refreshTokenService.hashToken(anyString())).thenReturn("hashed-code");
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        passwordResetService.requestPasswordReset("  Alice@Example.COM  ");

        verify(userRepository).findByEmail("alice@example.com");
        verify(passwordResetTokenRepository).invalidateAllForUser(alice);

        ArgumentCaptor<PasswordResetToken> tokenCaptor =
                ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());

        PasswordResetToken saved = tokenCaptor.getValue();
        assertEquals("hashed-code", saved.getTokenHash());
        assertSame(alice, saved.getUser());
        assertTrue(saved.getExpiresAt().isAfter(LocalDateTime.now().plusMinutes(29)));
        assertTrue(saved.getExpiresAt().isBefore(LocalDateTime.now().plusMinutes(31)));

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendPasswordResetCode(
                eq(alice), codeCaptor.capture(), eq(Duration.ofMinutes(30)));
        assertNotEquals(codeCaptor.getValue(), saved.getTokenHash());
        verify(refreshTokenService).hashToken(codeCaptor.getValue());
    }

    @Test
    @DisplayName("REQUEST: unknown email is silent and stores nothing")
    void testRequestUnknownEmailIsSilent() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> passwordResetService.requestPasswordReset("nobody@example.com"));

        verifyNoInteractions(passwordResetTokenRepository, mailService);
    }

    @Test
    @DisplayName("REQUEST: a per-user cooldown prevents email flooding")
    void testRequestCooldown() {
        PasswordResetToken latest =
                new PasswordResetToken(alice, "hash", LocalDateTime.now().plusMinutes(10));
        latest.setCreatedAt(LocalDateTime.now().minusSeconds(5));

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
        when(passwordResetTokenRepository.findTopByUserOrderByCreatedAtDesc(alice))
                .thenReturn(Optional.of(latest));

        passwordResetService.requestPasswordReset("alice@example.com");

        verify(passwordResetTokenRepository, never()).save(any());
        verifyNoInteractions(mailService);
    }

    @Test
    @DisplayName("REQUEST: at most three codes are issued per user per hour")
    void testRequestHourlyLimit() {
        PasswordResetToken latest =
                new PasswordResetToken(alice, "hash", LocalDateTime.now().plusMinutes(10));
        latest.setCreatedAt(LocalDateTime.now().minusMinutes(5));

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
        when(passwordResetTokenRepository.findTopByUserOrderByCreatedAtDesc(alice))
                .thenReturn(Optional.of(latest));
        when(passwordResetTokenRepository.countByUserAndCreatedAtAfter(eq(alice), any()))
                .thenReturn(3L);

        passwordResetService.requestPasswordReset("alice@example.com");

        verify(passwordResetTokenRepository, never()).save(any());
        verifyNoInteractions(mailService);
    }

    @Test
    @DisplayName("RESET: updates the password, clears lockout, revokes sessions, and audits")
    void testResetSuccess() {
        PasswordResetToken resetToken =
                new PasswordResetToken(alice, "hashed-code", LocalDateTime.now().plusMinutes(10));
        resetToken.setId(7L);

        alice.setFailedLoginAttempts(5);
        alice.setLockoutUntil(LocalDateTime.now().plusMinutes(10));
        alice.setTokensInvalidatedBefore(null);

        when(refreshTokenService.hashToken("raw-code")).thenReturn("hashed-code");
        when(passwordResetTokenRepository.findByTokenHash("hashed-code"))
                .thenReturn(Optional.of(resetToken));
        when(passwordResetTokenRepository.markUsed(7L)).thenReturn(1);
        when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
        when(passwordEncoder.encode("NewPassword123!")).thenReturn("new-hash");
        when(userRepository.save(alice)).thenReturn(alice);

        passwordResetService.resetPassword(" raw-code ", "NewPassword123!");

        assertEquals("new-hash", alice.getPassword());
        assertNotNull(alice.getTokensInvalidatedBefore());
        assertEquals(0, alice.getFailedLoginAttempts());
        assertNull(alice.getLockoutUntil());

        verify(userRepository).save(alice);
        verify(refreshTokenService).revokeAllForUser(alice);
        verify(passwordResetTokenRepository).invalidateAllForUser(alice);
        verify(auditLogService).log(
                eq(AuditAction.PASSWORD_RESET), eq(1L), eq("alice@example.com"), isNull());
    }

    @Test
    @DisplayName("RESET: expired codes are rejected")
    void testResetExpiredCode() {
        PasswordResetToken resetToken =
                new PasswordResetToken(alice, "hashed-code", LocalDateTime.now().minusSeconds(1));
        resetToken.setId(7L);

        when(refreshTokenService.hashToken("raw-code")).thenReturn("hashed-code");
        when(passwordResetTokenRepository.findByTokenHash("hashed-code"))
                .thenReturn(Optional.of(resetToken));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> passwordResetService.resetPassword("raw-code", "NewPassword123!"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Invalid or expired reset code", ex.getReason());
        verify(passwordResetTokenRepository, never()).markUsed(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("RESET: already-used codes are rejected")
    void testResetUsedCode() {
        PasswordResetToken resetToken =
                new PasswordResetToken(alice, "hashed-code", LocalDateTime.now().plusMinutes(10));
        resetToken.setId(7L);
        resetToken.setUsed(true);

        when(refreshTokenService.hashToken("raw-code")).thenReturn("hashed-code");
        when(passwordResetTokenRepository.findByTokenHash("hashed-code"))
                .thenReturn(Optional.of(resetToken));

        assertThrows(ResponseStatusException.class,
                () -> passwordResetService.resetPassword("raw-code", "NewPassword123!"));

        verify(passwordResetTokenRepository, never()).markUsed(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("RESET: a concurrent consume (0 rows updated) is rejected")
    void testResetConcurrentConsume() {
        PasswordResetToken resetToken =
                new PasswordResetToken(alice, "hashed-code", LocalDateTime.now().plusMinutes(10));
        resetToken.setId(7L);

        when(refreshTokenService.hashToken("raw-code")).thenReturn("hashed-code");
        when(passwordResetTokenRepository.findByTokenHash("hashed-code"))
                .thenReturn(Optional.of(resetToken));
        when(passwordResetTokenRepository.markUsed(7L)).thenReturn(0);

        assertThrows(ResponseStatusException.class,
                () -> passwordResetService.resetPassword("raw-code", "NewPassword123!"));

        verify(userRepository, never()).findById(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("RESET: unknown codes and invalid passwords are rejected")
    void testResetRejectsUnknownCodeAndShortPassword() {
        when(refreshTokenService.hashToken("unknown")).thenReturn("missing");
        when(passwordResetTokenRepository.findByTokenHash("missing")).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class,
                () -> passwordResetService.resetPassword("unknown", "NewPassword123!"));
        assertThrows(ResponseStatusException.class,
                () -> passwordResetService.resetPassword("unknown", "short"));

        verify(userRepository, never()).save(any());
    }
}
