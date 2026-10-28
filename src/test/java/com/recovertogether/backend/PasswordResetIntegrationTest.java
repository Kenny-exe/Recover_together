package com.recovertogether.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.repository.UserRepository;
import com.recovertogether.backend.service.MailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real-database end-to-end coverage for the password reset flow, including
 * the mail capture seam, refresh-token revocation, and single-use codes.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetIntegrationTest {

    private static final String OLD_PASSWORD = "OldPassword123!";
    private static final String NEW_PASSWORD = "NewPassword456!";
    private static final String GENERIC_FORGOT_MESSAGE =
            "If an account exists for that email, a reset code has been sent";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RecordingMailService mailService;

    private User user;
    private String email;

    @BeforeEach
    void setUp() {
        email = "reset_" + System.nanoTime() + "@example.com";

        user = new User();
        user.setName("Reset Test");
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(OLD_PASSWORD));
        user = userRepository.save(user);
    }

    @AfterEach
    void tearDown() {
        if (user == null || user.getId() == null) {
            return;
        }

        Long userId = user.getId();
        jdbcTemplate.update("DELETE FROM password_reset_tokens WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM audit_logs WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
    }

    @Test
    @DisplayName("RESET FLOW: generic request, code works once, old sessions and password are invalidated")
    void testPasswordResetFlow() throws Exception {
        String upperEmail = email.toUpperCase(Locale.ROOT);

        // Login with different casing proves the email-normalization fix.
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", upperEmail, "password", OLD_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginJson =
                objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String refreshToken = loginJson.get("refreshToken").asText();
        assertNotNull(refreshToken);

        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", upperEmail))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(GENERIC_FORGOT_MESSAGE));

        String code = mailService.latestCodeFor(email);
        assertNotNull(code);
        assertFalse(code.isBlank());

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("token", code, "newPassword", NEW_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password reset successfully"));

        // The refresh token issued before the reset must be dead.
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("refreshToken", refreshToken))))
                .andExpect(status().isUnauthorized());

        // Old password no longer works; the new one does.
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", OLD_PASSWORD))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", NEW_PASSWORD))))
                .andExpect(status().isOk());

        // The same code cannot be replayed.
        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("token", code, "newPassword", "AnotherPass789!"))))
                .andExpect(status().isBadRequest());

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE user_id = ? AND action = 'PASSWORD_RESET'",
                Integer.class, user.getId()));
    }

    @Test
    @DisplayName("FORGOT: unknown and known emails get the same response; no email for unknown")
    void testForgotPasswordDoesNotRevealAccounts() throws Exception {
        String unknownEmail = "nobody_" + System.nanoTime() + "@example.com";

        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", unknownEmail))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(GENERIC_FORGOT_MESSAGE));

        assertEquals(0, mailService.countFor(unknownEmail));
    }

    @Test
    @DisplayName("RESET: an invalid code is rejected without touching the account")
    void testResetRejectsInvalidCode() throws Exception {
        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("token", "not-a-real-code", "newPassword", NEW_PASSWORD))))
                .andExpect(status().isBadRequest());
    }

    @TestConfiguration
    static class MailTestConfig {

        @Bean
        @Primary
        RecordingMailService recordingMailService() {
            return new RecordingMailService();
        }
    }

    static class RecordingMailService implements MailService {

        private final List<SentMail> sent = new CopyOnWriteArrayList<>();

        @Override
        public void sendPasswordResetCode(User user, String code, Duration validity) {
            sent.add(new SentMail(user.getEmail(), code));
        }

        String latestCodeFor(String email) {
            for (int i = sent.size() - 1; i >= 0; i--) {
                if (sent.get(i).email().equals(email)) {
                    return sent.get(i).code();
                }
            }
            throw new AssertionError("No reset email was sent to " + email);
        }

        int countFor(String email) {
            int count = 0;
            for (SentMail mail : sent) {
                if (mail.email().equals(email)) {
                    count++;
                }
            }
            return count;
        }

        record SentMail(String email, String code) {
        }
    }
}
