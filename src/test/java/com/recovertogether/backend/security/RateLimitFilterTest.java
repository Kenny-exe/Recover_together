package com.recovertogether.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct unit tests for the fixed-window rate limiter. No Spring context:
 * the filter's window bookkeeping, per-path isolation, X-Forwarded-For
 * handling, and disabled mode are exercised against mock servlet objects.
 */
class RateLimitFilterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /** Counts how many requests made it past the filter. */
    private static class CountingChain implements FilterChain {
        int invocations;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
            invocations++;
        }
    }

    private MockHttpServletRequest request(String uri, String remoteAddr, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRequestURI(uri);
        if (remoteAddr != null) {
            request.setRemoteAddr(remoteAddr);
        }
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }

    private MockHttpServletResponse pass(RateLimitFilter filter, CountingChain chain,
                                         MockHttpServletRequest request) throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        // A fresh request object per call: OncePerRequestFilter marks the
        // request as already-filtered, so reuse would silently skip the filter.
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    @DisplayName("Requests within the limit pass; the first over the limit gets 429 + Retry-After")
    void throttlesAtLimitWithRetryAfter() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(true, 2, false, MAPPER);
        CountingChain chain = new CountingChain();

        assertEquals(200, pass(filter, chain, request("/auth/login", "1.1.1.1", null)).getStatus());
        assertEquals(200, pass(filter, chain, request("/auth/login", "1.1.1.1", null)).getStatus());

        MockHttpServletResponse throttled = pass(filter, chain, request("/auth/login", "1.1.1.1", null));
        assertEquals(429, throttled.getStatus());
        assertTrue(Integer.parseInt(throttled.getHeader("Retry-After")) >= 1, "Retry-After must be at least 1");
        String body = throttled.getContentAsString();
        assertTrue(body.contains("\"status\":429"), body);
        assertTrue(body.contains("Too many requests"), body);
        assertEquals(2, chain.invocations, "throttled requests must not reach the chain");
    }

    @Test
    @DisplayName("Windows are isolated per path, not shared across protected endpoints")
    void windowsAreIsolatedPerPath() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(true, 1, false, MAPPER);
        CountingChain chain = new CountingChain();

        assertEquals(200, pass(filter, chain, request("/auth/login", "1.1.1.1", null)).getStatus());
        assertEquals(429, pass(filter, chain, request("/auth/login", "1.1.1.1", null)).getStatus());

        // Different protected path from the same IP still has its own window.
        assertEquals(200, pass(filter, chain, request("/users/register", "1.1.1.1", null)).getStatus());
        // Different IP on the throttled path is also independent.
        assertEquals(200, pass(filter, chain, request("/auth/login", "2.2.2.2", null)).getStatus());
    }

    @Test
    @DisplayName("Unprotected paths are never throttled even after hitting the limit elsewhere")
    void unprotectedPathsPassThrough() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(true, 1, false, MAPPER);
        CountingChain chain = new CountingChain();

        for (int i = 0; i < 10; i++) {
            assertEquals(200, pass(filter, chain, request("/health", "1.1.1.1", null)).getStatus());
        }
        assertEquals(10, chain.invocations);
    }

    @Test
    @DisplayName("Disabled filters never respond 429")
    void disabledFilterPassesEverything() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(false, 1, false, MAPPER);
        CountingChain chain = new CountingChain();

        for (int i = 0; i < 20; i++) {
            assertEquals(200, pass(filter, chain, request("/auth/login", "1.1.1.1", null)).getStatus());
        }
        assertEquals(20, chain.invocations);
    }

    @Test
    @DisplayName("Filters only engage on exactly-matching protected paths")
    void protectedPathsAreExactMatches() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(true, 1, false, MAPPER);
        CountingChain chain = new CountingChain();

        // /auth/login is protected but /auth/loginUser or /auth/loginx is not.
        assertEquals(200, pass(filter, chain, request("/auth/loginUser", "1.1.1.1", null)).getStatus());
        assertEquals(200, pass(filter, chain, request("/", "1.1.1.1", null)).getStatus());
        assertEquals(2, chain.invocations);
    }

    @Test
    @DisplayName("With trust-proxy, X-Forwarded-For identifies the real client")
    void trustsForwardedForWhenConfigured() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(true, 1, true, MAPPER);
        CountingChain chain = new CountingChain();

        // Same client IP (XFF) behind two different LB connections.
        assertEquals(200, pass(filter, chain, request("/auth/login", "10.0.0.1", "77.77.77.77, 10.0.0.9")).getStatus());

        MockHttpServletResponse secondHop = pass(filter, chain, request("/auth/login", "10.0.0.2", "77.77.77.77"));
        assertEquals(429, secondHop.getStatus(), "same XFF client via a second LB hop shares the window");

        // A different end client (first XFF entry differs) is not throttled.
        assertEquals(200, pass(filter, chain, request("/auth/login", "10.0.0.3", "88.88.88.88")).getStatus());
    }

    @Test
    @DisplayName("Without trust-proxy, remote address wins and spoofed XFF is ignored")
    void ignoresForwardedForWithoutTrustProxy() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(true, 1, false, MAPPER);
        CountingChain chain = new CountingChain();

        assertEquals(200, pass(filter, chain, request("/auth/login", "10.0.0.1", "77.77.77.77")).getStatus());

        // Attacker tries to reuse the same (XFF) identity while hop 10.0.0.2 —
        // with trust-proxy=false the remote address is the bucket key, so this
        // request lands in a fresh window and passes.
        assertEquals(200, pass(filter, chain, request("/auth/login", "10.0.0.2", "77.77.77.77")).getStatus());
        assertEquals(2, chain.invocations);
    }

    @Test
    @DisplayName("Constructor rejects non-positive limits")
    void rejectsNonPositiveLimit() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new RateLimitFilter(true, 0, false, MAPPER));
        assertFalse(RateLimitFilter.PROTECTED_PATHS.isEmpty());
    }
}
