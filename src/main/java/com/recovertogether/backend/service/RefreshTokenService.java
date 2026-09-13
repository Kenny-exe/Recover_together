package com.recovertogether.backend.service;

import com.recovertogether.backend.entity.RefreshToken;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class RefreshTokenService {

    private static final Logger logger = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int REFRESH_TOKEN_VALIDITY_DAYS = 30;
    private static final int TOKEN_BYTE_LENGTH = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public record RotationResult(User user, String newRefreshToken) {}

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional
    public String createRefreshToken(User user) {
        String rawToken = generateSecureRandomToken();
        String tokenHash = hashToken(rawToken);

        RefreshToken refreshToken = new RefreshToken(
                user,
                tokenHash,
                LocalDateTime.now().plusDays(REFRESH_TOKEN_VALIDITY_DAYS)
        );
        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    @Transactional
    public RotationResult rotateRefreshToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is required");
        }

        String tokenHash = hashToken(rawToken);
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHash(tokenHash);

        if (tokenOpt.isEmpty()) {
            logger.warn("Refresh attempt with unknown token hash");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }

        RefreshToken oldToken = tokenOpt.get();

        if (oldToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            logger.warn("Refresh attempt with expired token for user {}", oldToken.getUser().getId());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }

        if (oldToken.isRevoked()) {
            // Replay attack / token reuse detected: revoke all refresh tokens for this user
            logger.warn("Revoked refresh token presented for user {}. Possible token theft! Revoking all sessions.",
                    oldToken.getUser().getId());
            refreshTokenRepository.revokeAllByUser(oldToken.getUser());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or revoked refresh token");
        }

        // Revoke the presented token
        oldToken.setRevoked(true);
        refreshTokenRepository.save(oldToken);

        // Issue new rotated token
        String newRawToken = generateSecureRandomToken();
        String newTokenHash = hashToken(newRawToken);

        RefreshToken newToken = new RefreshToken(
                oldToken.getUser(),
                newTokenHash,
                LocalDateTime.now().plusDays(REFRESH_TOKEN_VALIDITY_DAYS)
        );
        refreshTokenRepository.save(newToken);

        return new RotationResult(oldToken.getUser(), newRawToken);
    }

    @Transactional
    public void revokeRefreshToken(String rawToken, User user) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        String tokenHash = hashToken(rawToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            if (token.getUser().getId().equals(user.getId())) {
                token.setRevoked(true);
                refreshTokenRepository.save(token);
                logger.debug("Revoked refresh token for user {}", user.getId());
            }
        });
    }

    @Transactional
    public void revokeAllForUser(User user) {
        refreshTokenRepository.revokeAllByUser(user);
        logger.debug("Revoked all refresh tokens for user {}", user.getId());
    }

    private String generateSecureRandomToken() {
        byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
