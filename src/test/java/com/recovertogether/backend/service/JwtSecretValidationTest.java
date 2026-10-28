package com.recovertogether.backend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Startup-guard tests for the JWT secret strength check. Repo-wide secrets
 * (test properties, smoke scripts) are long enough, so production-safe
 * deployments boot; anything shorter than 32 bytes must fail fast.
 */
class JwtSecretValidationTest {

    @Test
    @DisplayName("A secret shorter than 32 bytes is rejected at startup")
    void rejectsShortSecret() {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secret", "too-short-secret");

        IllegalStateException ex = assertThrows(IllegalStateException.class, service::validateSecret);

        assertTrue(ex.getMessage().contains("jwt.secret"));
    }

    @Test
    @DisplayName("A null secret is treated as zero bytes and rejected")
    void rejectsNullSecret() {
        JwtService service = new JwtService();

        IllegalStateException ex = assertThrows(IllegalStateException.class, service::validateSecret);

        assertTrue(ex.getMessage().contains("256 bits"));
    }

    @Test
    @DisplayName("Exactly 32 bytes (256 bits) is accepted for HMAC-SHA256")
    void acceptsThirtyTwoByteSecret() {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secret", "0123456789abcdef0123456789ABCDEF");

        assertDoesNotThrow(service::validateSecret);
    }

    @Test
    @DisplayName("31 bytes is one byte too short and is rejected")
    void rejectsThirtyOneByteSecret() {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secret", "0123456789abcdef0123456789ABCDE");

        assertThrows(IllegalStateException.class, service::validateSecret);
    }

    @Test
    @DisplayName("The error message guides the operator to JWT_SECRET")
    void errorMessageNamesTheEnvironmentVariable() {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secret", "short");

        IllegalStateException ex = assertThrows(IllegalStateException.class, service::validateSecret);

        assertEquals(
                "jwt.secret must be at least 32 bytes (256 bits) for HMAC-SHA256; "
                        + "set JWT_SECRET to a strong random value",
                ex.getMessage());
    }
}
