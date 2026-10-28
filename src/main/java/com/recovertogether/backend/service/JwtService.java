package com.recovertogether.backend.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private static final Logger logger = LoggerFactory.getLogger(JwtService.class);

    /**
     * HMAC-SHA256 needs at least 256 bits (32 bytes) of key material; shorter
     * secrets are brute-forceable. Validated at startup so a weak or missing
     * secret fails the boot instead of quietly signing tokens with a weak key.
     */
    @PostConstruct
    public void validateSecret() {
        int secretBytes = secret == null ? 0 : secret.getBytes(StandardCharsets.UTF_8).length;
        if (secretBytes < 32) {
            throw new IllegalStateException(
                    "jwt.secret must be at least 32 bytes (256 bits) for HMAC-SHA256; "
                            + "set JWT_SECRET to a strong random value");
        }
    }

    public enum TokenValidationResult {
        VALID,
        EXPIRED,
        INVALID
    }

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expirationtime;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String email) {
        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationtime))
                .signWith(getSigningKey())
                .compact();
    }

    public String generateToken(String email, long customExpirationMillis) {
        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + customExpirationMillis))
                .signWith(getSigningKey())
                .compact();
    }

    public String extractEmail(String token) {
        Claims claims = extractAllClaims(token);
        return claims.getSubject();
    }

    public Date extractIssuedAt(String token) {
        Claims claims = extractAllClaims(token);
        return claims.getIssuedAt();
    }

    public Date extractExpiration(String token) {
        Claims claims = extractAllClaims(token);
        return claims.getExpiration();
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public TokenValidationResult validateTokenDetailed(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return TokenValidationResult.VALID;
        } catch (ExpiredJwtException e) {
            logger.debug("Token expired: {}", e.getMessage());
            return TokenValidationResult.EXPIRED;
        } catch (Exception e) {
            logger.debug("Token invalid: {}", e.getMessage());
            return TokenValidationResult.INVALID;
        }
    }

    public boolean validateToken(String token) {
        return validateTokenDetailed(token) == TokenValidationResult.VALID;
    }
}
