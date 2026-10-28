package com.recovertogether.backend.service;

import com.recovertogether.backend.entity.PasswordResetToken;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.enums.AuditAction;
import com.recovertogether.backend.repository.PasswordResetTokenRepository;
import com.recovertogether.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

/**
 * Self-service account recovery.
 *
 * Requests always succeed from the caller's point of view, whether or not the
 * email belongs to an account, so the endpoint cannot be used to enumerate
 * users. Reset codes are 256-bit random values, stored only as SHA-256 hashes
 * and consumable exactly once.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private static final int TOKEN_BYTES = 32;
    private static final Duration TOKEN_VALIDITY = Duration.ofMinutes(30);
    private static final Duration REQUEST_COOLDOWN = Duration.ofSeconds(60);
    private static final long MAX_REQUESTS_PER_HOUR = 3;

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AuditLogService auditLogService;
    private final MailService mailService;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(
            PasswordResetTokenRepository passwordResetTokenRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService,
            AuditLogService auditLogService,
            MailService mailService) {
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.auditLogService = auditLogService;
        this.mailService = mailService;
    }

    @Transactional
    public void requestPasswordReset(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            return;
        }

        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);
        if (userOpt.isEmpty()) {
            // Same response as a hit; never reveal whether the account exists.
            log.debug("Password reset requested for an unknown email");
            return;
        }

        User user = userOpt.get();
        LocalDateTime now = LocalDateTime.now();

        Optional<PasswordResetToken> latest =
                passwordResetTokenRepository.findTopByUserOrderByCreatedAtDesc(user);
        if (latest.isPresent() && latest.get().getCreatedAt() != null
                && latest.get().getCreatedAt().isAfter(now.minus(REQUEST_COOLDOWN))) {
            log.debug("Password reset cooldown active for user {}", user.getId());
            return;
        }

        long recentRequests = passwordResetTokenRepository
                .countByUserAndCreatedAtAfter(user, now.minus(Duration.ofHours(1)));
        if (recentRequests >= MAX_REQUESTS_PER_HOUR) {
            log.debug("Password reset hourly limit reached for user {}", user.getId());
            return;
        }

        // Invalidate any earlier, still-usable code before issuing a new one.
        passwordResetTokenRepository.invalidateAllForUser(user);

        String rawToken = generateToken();
        PasswordResetToken token = new PasswordResetToken(
                user,
                refreshTokenService.hashToken(rawToken),
                now.plus(TOKEN_VALIDITY));

        passwordResetTokenRepository.save(token);

        // Best-effort: the mail service swallows delivery failures. The token
        // is already stored, so the user can request another code.
        mailService.sendPasswordResetCode(user, rawToken, TOKEN_VALIDITY);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidToken();
        }

        validateNewPassword(newPassword);

        String tokenHash = refreshTokenService.hashToken(rawToken.trim());
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(this::invalidToken);

        if (resetToken.isUsed() || resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw invalidToken();
        }

        Long userId = resetToken.getUser().getId();

        // Atomic consume: a concurrent reset with the same code loses here.
        if (passwordResetTokenRepository.markUsed(resetToken.getId()) == 0) {
            throw invalidToken();
        }

        User user = userRepository.findById(userId)
                .orElseThrow(this::invalidToken);

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setTokensInvalidatedBefore(LocalDateTime.now());
        user.setFailedLoginAttempts(0);
        user.setLockoutUntil(null);
        userRepository.save(user);

        // A password reset is a security event: end every existing session.
        refreshTokenService.revokeAllForUser(user);
        passwordResetTokenRepository.invalidateAllForUser(user);

        auditLogService.log(AuditAction.PASSWORD_RESET, user.getId(), user.getEmail(), null);
    }

    private void validateNewPassword(String newPassword) {
        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 128) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "New password must be between 8 and 128 characters");
        }
    }

    private ResponseStatusException invalidToken() {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid or expired reset code");
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private String generateToken() {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
