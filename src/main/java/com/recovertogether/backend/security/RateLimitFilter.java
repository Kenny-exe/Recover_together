package com.recovertogether.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recovertogether.backend.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window IP rate limiter for the public auth endpoints.
 *
 * <p>Credential-stuffing and registration spam are stopped per client IP
 * (per protected path) in a 60-second window; the per-account login lockout
 * in {@code UserService} is independent protection on top of this. When the
 * limit is exceeded the request is short-circuited with a 429 (the app's
 * standard {@code ErrorResponse} JSON) plus a {@code Retry-After} header,
 * before any controller or token work happens.
 *
 * <p>The filter is registered only in the security chain (its servlet
 * registration is suppressed in {@code SecurityConfig}) so it runs exactly
 * once, before {@link JwtAuthenticationFilter}.
 */
public class RateLimitFilter extends OncePerRequestFilter
{
    private static final Logger logger = LoggerFactory.getLogger(RateLimitFilter.class);

    /** Public, unauthenticated endpoints that brute-force traffic targets. */
    static final Set<String> PROTECTED_PATHS = Set.of(
            "/auth/login",
            "/auth/refresh",
            "/auth/forgot-password",
            "/auth/reset-password",
            "/users/register");

    /** Fixed window length: all counts reset on each minute boundary. */
    static final long WINDOW_MILLIS = 60_000L;

    /** Hard cap on tracked windows so a many-IP attack cannot exhaust memory. */
    private static final int MAX_TRACKED_WINDOWS = 50_000;

    private final boolean enabled;
    private final int limitPerMinute;
    private final boolean trustProxy;
    private final ObjectMapper objectMapper;

    record Window(long start, AtomicInteger count) {}

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(
            boolean enabled,
            int limitPerMinute,
            boolean trustProxy,
            ObjectMapper objectMapper)
    {
        if (limitPerMinute < 1)
        {
            throw new IllegalArgumentException("app.rate-limit.limit-per-minute must be at least 1");
        }
        this.enabled = enabled;
        this.limitPerMinute = limitPerMinute;
        this.trustProxy = trustProxy;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request)
    {
        return !enabled || !PROTECTED_PATHS.contains(path(request));
    }

    String clientIp(HttpServletRequest request)
    {
        if (trustProxy)
        {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank())
            {
                int comma = forwarded.indexOf(',');
                String first = comma > 0 ? forwarded.substring(0, comma) : forwarded;
                return first.trim();
            }
        }
        return request.getRemoteAddr();
    }

    private static String path(HttpServletRequest request)
    {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException
    {
        String key = clientIp(request) + "|" + path(request);
        long now = System.currentTimeMillis();

        if (windows.size() > MAX_TRACKED_WINDOWS)
        {
            windows.values().removeIf(window -> now - window.start() >= WINDOW_MILLIS);
        }

        Window window = windows.compute(key, (k, existing) ->
                existing == null || now - existing.start() >= WINDOW_MILLIS
                        ? new Window(now, new AtomicInteger(0))
                        : existing);

        if (window.count().incrementAndGet() > limitPerMinute)
        {
            long retryAfterSeconds = Math.max(1, (WINDOW_MILLIS - (now - window.start()) + 999) / 1000);
            logger.warn("Rate limit exceeded for {} on {}", key, request.getMethod());

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), new ErrorResponse(
                    LocalDateTime.now(),
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    "Too many requests. Please slow down and try again soon."));
            return;
        }

        filterChain.doFilter(request, response);
    }
}
