package com.recovertogether.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recovertogether.backend.security.JwtAuthenticationFilter;
import com.recovertogether.backend.security.RateLimitFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

import static org.springframework.security.config.Customizer.withDefaults;


@Configuration
public class SecurityConfig
{
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter)
    {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, RateLimitFilter rateLimitFilter) throws Exception
    {
        http
                .csrf(csrf -> csrf.disable())

                // CORS is driven by the corsConfigurationSource bean below; with
                // no allowed origins configured (the default) cross-origin
                // requests are rejected. Mobile apps do not need CORS.
                .cors(withDefaults())

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth ->
                        auth.dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ASYNC,
                                        jakarta.servlet.DispatcherType.ERROR)
                                .permitAll()
                                .requestMatchers(
                                        "/auth/login",
                                        "/auth/refresh",
                                        "/auth/forgot-password",
                                        "/auth/reset-password",
                                        "/users/register",
                                        "/health",
                                        "/swagger-ui/**",
                                        "/swagger-ui.html",
                                        "/v3/api-docs/**"
                                )
                                .permitAll()
                                .anyRequest()
                                .authenticated())

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                )

                // Rate limiting sits in front of token validation so brute-force
                // traffic is throttled before any authentication work happens.
                // The filter arrives as a bean-method parameter, resolved from
                // the context after this configuration instantiates (no cycle).
                // Registered after JwtAuthenticationFilter so its position is
                // known to the order comparator.
                .addFilterBefore(
                        rateLimitFilter,
                        JwtAuthenticationFilter.class
                );

        return http.build();
    }

    /**
     * Bean declaration so the filter's properties are resolved at startup; the
     * registration bean below suppresses the container-level auto-registration
     * so the filter runs exactly once, from the security chain.
     */
    @Bean
    public RateLimitFilter rateLimitFilter(
            @Value("${app.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.rate-limit.limit-per-minute:30}") int limitPerMinute,
            @Value("${app.rate-limit.trust-proxy:false}") boolean trustProxy,
            ObjectMapper objectMapper)
    {
        return new RateLimitFilter(enabled, limitPerMinute, trustProxy, objectMapper);
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
            RateLimitFilter rateLimitFilter)
    {
        FilterRegistrationBean<RateLimitFilter> registration =
                new FilterRegistrationBean<>(rateLimitFilter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * Opt-in CORS for future web clients, driven by
     * {@code app.cors.allowed-origins} (comma-separated). With nothing
     * configured the source denies every cross-origin request, which is the
     * safe default for a mobile-only API.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:}") String allowedOrigins)
    {
        CorsConfiguration configuration = new CorsConfiguration();

        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();
        if (!origins.isEmpty())
        {
            configuration.setAllowedOrigins(origins);
            configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
            configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
            configuration.setMaxAge(3600L);
        }

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder()
    {
        return new BCryptPasswordEncoder();
    }
}