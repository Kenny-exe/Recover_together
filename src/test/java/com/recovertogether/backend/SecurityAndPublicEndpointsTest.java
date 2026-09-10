package com.recovertogether.backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityAndPublicEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /health is public and returns UP without any token")
    void testHealthPublic() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("POST /users/register is public and processes request without token (fails validation cleanly)")
    void testRegisterWithoutToken() throws Exception {
        mockMvc.perform(post("/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("POST /users/register succeeds to reach handler even with stale/invalid Bearer token")
    void testRegisterWithStaleToken() throws Exception {
        mockMvc.perform(post("/users/register")
                        .header("Authorization", "Bearer this.is.an.invalid.or.stale.jwt.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("Protected endpoint GET /checkin/streak without token is rejected with 403")
    void testProtectedEndpointWithoutToken() throws Exception {
        mockMvc.perform(get("/checkin/streak"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Protected endpoint GET /checkin/streak with invalid token is rejected with 401")
    void testProtectedEndpointWithInvalidToken() throws Exception {
        mockMvc.perform(get("/checkin/streak")
                        .header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid token"));
    }

    @Test
    @DisplayName("GET /auth/test unauthenticated is rejected by security (403)")
    void testAuthTestUnauthenticatedForbidden() throws Exception {
        mockMvc.perform(get("/auth/test").param("token", "dummy"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /auth/test debug endpoint no longer exists (404 for authenticated)")
    @org.springframework.security.test.context.support.WithMockUser
    void testAuthTestRemovedAuthenticatedReturns404() throws Exception {
        mockMvc.perform(get("/auth/test").param("token", "dummy"))
                .andExpect(status().isNotFound());
    }
}
