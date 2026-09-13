package com.recovertogether.backend.security;

import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.repository.UserRepository;
import com.recovertogether.backend.service.JwtService;
import com.recovertogether.backend.service.JwtService.TokenValidationResult;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    private boolean isPublicPath(String path) {
        if (path == null) return false;
        return path.equals("/auth/login")
                || path.equals("/auth/refresh")
                || path.equals("/users/register")
                || path.equals("/health")
                || path.startsWith("/swagger-ui")
                || path.equals("/swagger-ui.html")
                || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getServletPath();
        String uri = request.getRequestURI();

        if (isPublicPath(path) || isPublicPath(uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            if (!jwtService.validateToken(token)) {
                TokenValidationResult validationResult = jwtService.validateTokenDetailed(token);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                if (validationResult == TokenValidationResult.EXPIRED) {
                    response.getWriter().write("{\"message\":\"Token has expired\"}");
                } else {
                    response.getWriter().write("{\"message\":\"Invalid token\"}");
                }
                return;
            }

            String email = jwtService.extractEmail(token);
            User user = userRepository.findByEmail(email).orElse(null);

            if (user == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"User not found\"}");
                return;
            }

            // Check if token was issued before a global session invalidation event (e.g. password change)
            if (user.getTokensInvalidatedBefore() != null) {
                Date issuedAt = jwtService.extractIssuedAt(token);
                if (issuedAt != null) {
                    LocalDateTime iat = LocalDateTime.ofInstant(issuedAt.toInstant(), ZoneId.systemDefault());
                    // Allow 1-second clock skew tolerance
                    if (iat.isBefore(user.getTokensInvalidatedBefore().minusSeconds(1))) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"message\":\"Token has been invalidated\"}");
                        return;
                    }
                }
            }

            LocalDateTime now = LocalDateTime.now();
            if (user.getLastSeen() == null || user.getLastSeen().isBefore(now.minusMinutes(5))) {
                user.setLastSeen(now);
                userRepository.save(user);
            }

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList());

            SecurityContextHolder.getContext().setAuthentication(auth);

        } catch (Exception e) {
            logger.debug("Authentication processing failure: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Invalid or expired token\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}