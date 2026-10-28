package com.recovertogether.backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Production deployments should not expose API documentation. With
 * {@code app.docs.enabled=false} the springdoc endpoints disappear entirely
 * (404), so the OpenAPI surface can be turned off with one environment
 * variable instead of code changes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.docs.enabled=false")
class DocsDisabledIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("OpenAPI and Swagger UI return 404 when docs are disabled")
    void docsEndpointsDisappear() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Resource not found"));

        mockMvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().isNotFound());
    }
}
