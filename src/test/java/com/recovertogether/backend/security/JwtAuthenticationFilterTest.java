package com.recovertogether.backend.security;

import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.repository.UserRepository;
import com.recovertogether.backend.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        setUserId(user, 1L);
        user.setEmail("user@example.com");
        user.setName("Test User");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setUserId(User user, Long id) {
        try {
            Field field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("lastSeen is updated and saved when user.lastSeen is null")
    void testLastSeenThrottling_WhenLastSeenIsNull() throws ServletException, IOException {
        user.setLastSeen(null);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/dashboard");
        request.setServletPath("/dashboard");
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.validateToken("valid-token")).thenReturn(true);
        when(jwtService.extractEmail("valid-token")).thenReturn("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        assertNotNull(user.getLastSeen());
        verify(userRepository, times(1)).save(user);
        verify(filterChain, times(1)).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("lastSeen is NOT saved when user.lastSeen was updated 2 minutes ago (< 5 minutes)")
    void testLastSeenThrottling_WhenLastSeenIsRecent() throws ServletException, IOException {
        LocalDateTime recentTime = LocalDateTime.now().minusMinutes(2);
        user.setLastSeen(recentTime);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/dashboard");
        request.setServletPath("/dashboard");
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.validateToken("valid-token")).thenReturn(true);
        when(jwtService.extractEmail("valid-token")).thenReturn("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // lastSeen should NOT be updated, and userRepository.save should NOT be called
        assertEquals(recentTime, user.getLastSeen());
        verify(userRepository, never()).save(any());
        verify(filterChain, times(1)).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("lastSeen is updated and saved when user.lastSeen was updated 10 minutes ago (> 5 minutes)")
    void testLastSeenThrottling_WhenLastSeenIsOld() throws ServletException, IOException {
        LocalDateTime oldTime = LocalDateTime.now().minusMinutes(10);
        user.setLastSeen(oldTime);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/dashboard");
        request.setServletPath("/dashboard");
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.validateToken("valid-token")).thenReturn(true);
        when(jwtService.extractEmail("valid-token")).thenReturn("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // lastSeen should be updated to now, and userRepository.save called
        assertTrue(user.getLastSeen().isAfter(oldTime));
        verify(userRepository, times(1)).save(user);
        verify(filterChain, times(1)).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
