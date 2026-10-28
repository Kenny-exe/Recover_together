package com.recovertogether.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end hardening wiring: the security chain engages the rate limiter in
 * front of the JWT filter, CORS preflights are honoured only for configured
 * origins, and the public OpenAPI docs are reachable while enabled.
 *
 * This context deliberately re-enables rate limiting with a tiny window; the
 * default test configuration (shared with the rest of the suite) has it off.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.limit-per-minute=3",
        "app.cors.allowed-origins=http://localhost:3000"
})
class SecurityHardeningIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("The fourth login attempt from one IP within the window is throttled with 429 + Retry-After")
    void fourthLoginAttemptIsThrottled() throws Exception {
        String attemptJson = objectMapper.writeValueAsString(Map.of(
                "email", "nobody-for-throttle@example.com",
                "password", "WrongPassword123!"));

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(attemptJson))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(attemptJson))
                .andExpect(status().is(429))
                .andExpect(header().string("Retry-After", org.hamcrest.Matchers.matchesPattern("[1-9][0-9]*")))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value("Too many requests. Please slow down and try again soon."));
    }

    @Test
    @DisplayName("A configured origin may preflight the API and gets CORS headers")
    void configuredOriginIsAllowed() throws Exception {
        mockMvc.perform(options("/users/register")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
    }

    @Test
    @DisplayName("Origins outside the list are rejected")
    void unconfiguredOriginIsRejected() throws Exception {
        mockMvc.perform(options("/users/register")
                        .header(HttpHeaders.ORIGIN, "http://evil.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("OpenAPI docs are available while docs are enabled")
    void apiDocsAvailableWhenEnabled() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }
}
