package com.recovertogether.backend;

import com.recovertogether.backend.controller.AuthController;
import com.recovertogether.backend.controller.UserController;
import com.recovertogether.backend.dto.ChangePasswordRequest;
import com.recovertogether.backend.dto.LoginRequest;
import com.recovertogether.backend.dto.LoginResponse;
import com.recovertogether.backend.dto.LogoutRequest;
import com.recovertogether.backend.dto.RefreshTokenRequest;
import com.recovertogether.backend.entity.AuditLog;
import com.recovertogether.backend.entity.RefreshToken;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.enums.AuditAction;
import com.recovertogether.backend.repository.AuditLogRepository;
import com.recovertogether.backend.repository.RefreshTokenRepository;
import com.recovertogether.backend.repository.UserRepository;
import com.recovertogether.backend.security.JwtAuthenticationFilter;
import com.recovertogether.backend.service.AuditLogService;
import com.recovertogether.backend.service.JwtService;
import com.recovertogether.backend.service.RefreshTokenService;
import com.recovertogether.backend.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationHardeningTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private FilterChain filterChain;

    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuditLogService auditLogService;
    private RefreshTokenService refreshTokenService;
    private UserService userService;
    private AuthController authController;
    private UserController userController;
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private User alice;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();

        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", "ThisIsOnlyATestSecretForRecoverTogether123456789");
        ReflectionTestUtils.setField(jwtService, "expirationtime", 900000L); // 15 minutes

        auditLogService = new AuditLogService(auditLogRepository);
        refreshTokenService = new RefreshTokenService(refreshTokenRepository);
        userService = new UserService(userRepository, passwordEncoder, auditLogService, refreshTokenService);
        authController = new AuthController(userService, jwtService, refreshTokenService, auditLogService);
        userController = new UserController(userRepository, userService, auditLogService);
        jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtService, userRepository);

        alice = new User();
        setId(alice, 1L);
        alice.setName("Alice");
        alice.setEmail("alice@example.com");
        alice.setPassword(passwordEncoder.encode("Password123!"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setId(User user, Long id) {
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // =========================================================================
    // 1. JWT TESTS
    // =========================================================================

    @Test
    @DisplayName("JWT 1: Valid token authenticates and passes through filter")
    void testJwtValidToken() throws ServletException, IOException {
        String token = jwtService.generateToken(alice.getEmail());
        when(userRepository.findByEmail(alice.getEmail())).thenReturn(Optional.of(alice));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/dashboard");
        request.setServletPath("/dashboard");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(alice.getEmail(), ((User) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getEmail());
    }

    @Test
    @DisplayName("JWT 2: Expired token returns clean 401 with Token has expired message")
    void testJwtExpiredToken() throws ServletException, IOException {
        // Token created with negative expiration (already expired)
        String expiredToken = jwtService.generateToken(alice.getEmail(), -5000L);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/dashboard");
        request.setServletPath("/dashboard");
        request.addHeader("Authorization", "Bearer " + expiredToken);
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Token has expired"));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("JWT 3: Malformed token returns clean 401 with Invalid token message")
    void testJwtMalformedToken() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/dashboard");
        request.setServletPath("/dashboard");
        request.addHeader("Authorization", "Bearer this.is.a.malformed.token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Invalid token"));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("JWT 4: Tampered signature returns clean 401 with Invalid token message")
    void testJwtInvalidSignature() throws ServletException, IOException {
        JwtService otherKeyJwtService = new JwtService();
        ReflectionTestUtils.setField(otherKeyJwtService, "secret", "CompletelyDifferentSecretKeyForRecoverTogether987654321");
        ReflectionTestUtils.setField(otherKeyJwtService, "expirationtime", 900000L);
        String forgedToken = otherKeyJwtService.generateToken(alice.getEmail());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/dashboard");
        request.setServletPath("/dashboard");
        request.addHeader("Authorization", "Bearer " + forgedToken);
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Invalid token"));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("JWT 5: Missing Authorization header allows request down chain without authentication")
    void testJwtMissingToken() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/dashboard");
        request.setServletPath("/dashboard");
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("JWT 6: Stale token issued before global session invalidation returns 401")
    void testJwtStaleTokenAfterGlobalInvalidation() throws ServletException, IOException {
        String token = jwtService.generateToken(alice.getEmail());

        // Simulate global session invalidation occurring after token issuance
        alice.setTokensInvalidatedBefore(LocalDateTime.now().plusSeconds(10));
        when(userRepository.findByEmail(alice.getEmail())).thenReturn(Optional.of(alice));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/dashboard");
        request.setServletPath("/dashboard");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Token has been invalidated"));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("JWT 7: Token for deleted user account returns 401 User not found")
    void testJwtDeletedAccount() throws ServletException, IOException {
        String token = jwtService.generateToken("deleted@example.com");
        when(userRepository.findByEmail("deleted@example.com")).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/dashboard");
        request.setServletPath("/dashboard");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("User not found"));
        verify(filterChain, never()).doFilter(any(), any());
    }

    // =========================================================================
    // 2. REFRESH TOKEN TESTS
    // =========================================================================

    @Test
    @DisplayName("Refresh 1: Valid refresh token rotates and revokes old token")
    void testRefreshTokenRotationSuccess() {
        String rawOldToken = "secure-random-raw-token-1234567890";
        String tokenHash = refreshTokenService.hashToken(rawOldToken);

        RefreshToken oldEntity = new RefreshToken(alice, tokenHash, LocalDateTime.now().plusDays(30));
        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(oldEntity));

        RefreshTokenService.RotationResult result = refreshTokenService.rotateRefreshToken(rawOldToken);

        assertNotNull(result);
        assertEquals(alice.getId(), result.user().getId());
        assertNotNull(result.newRefreshToken());
        assertNotEquals(rawOldToken, result.newRefreshToken());

        // Old entity marked revoked
        assertTrue(oldEntity.isRevoked());
        verify(refreshTokenRepository).save(oldEntity);
        // New token saved
        verify(refreshTokenRepository).save(argThat(r -> !r.isRevoked() && r.getUser().equals(alice)));
    }

    @Test
    @DisplayName("Refresh 2: Expired refresh token returns 401")
    void testRefreshTokenExpired() {
        String rawToken = "expired-raw-token";
        String tokenHash = refreshTokenService.hashToken(rawToken);

        RefreshToken expiredEntity = new RefreshToken(alice, tokenHash, LocalDateTime.now().minusMinutes(5));
        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(expiredEntity));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> refreshTokenService.rotateRefreshToken(rawToken));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals("Invalid or expired refresh token", ex.getReason());
    }

    @Test
    @DisplayName("Refresh 3: Presenting revoked refresh token triggers theft detection and revokes all user tokens")
    void testRefreshTokenReuseTheftDetection() {
        String rawToken = "replayed-raw-token";
        String tokenHash = refreshTokenService.hashToken(rawToken);

        RefreshToken revokedEntity = new RefreshToken(alice, tokenHash, LocalDateTime.now().plusDays(10));
        revokedEntity.setRevoked(true); // already revoked!

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(revokedEntity));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> refreshTokenService.rotateRefreshToken(rawToken));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals("Invalid or revoked refresh token", ex.getReason());

        // Theft mitigation: All refresh tokens for this user must be revoked
        verify(refreshTokenRepository).revokeAllByUser(alice);
    }

    @Test
    @DisplayName("Refresh 4: Public /auth/refresh endpoint returns new access and refresh tokens")
    void testAuthRefreshEndpoint() {
        String rawToken = "client-refresh-token";
        String tokenHash = refreshTokenService.hashToken(rawToken);

        RefreshToken entity = new RefreshToken(alice, tokenHash, LocalDateTime.now().plusDays(30));
        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(entity));

        LoginResponse response = authController.refresh(new RefreshTokenRequest(rawToken));

        assertNotNull(response);
        assertNotNull(response.getToken());
        assertNotNull(response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
    }

    // =========================================================================
    // 3. LOGOUT TESTS
    // =========================================================================

    @Test
    @DisplayName("Logout 1: Authenticated logout revokes current refresh session and logs LOGOUT audit")
    void testLogoutRevokesSessionAndAudits() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(alice, null, Collections.emptyList())
        );

        String rawToken = "session-to-revoke";
        String tokenHash = refreshTokenService.hashToken(rawToken);
        RefreshToken entity = new RefreshToken(alice, tokenHash, LocalDateTime.now().plusDays(30));
        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(entity));

        var result = authController.logout(new LogoutRequest(rawToken));

        assertEquals("Logged out successfully", result.get("message"));
        assertTrue(entity.isRevoked());
        verify(refreshTokenRepository).save(entity);

        // Security context cleared
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        // Audit LOGOUT logged
        verify(auditLogRepository).save(argThat(log ->
                log.getAction() == AuditAction.LOGOUT && log.getUserId().equals(1L)
        ));
    }

    @Test
    @DisplayName("Logout 2: Normal logout does NOT globally invalidate access tokens (they expire in 15 mins)")
    void testLogoutDoesNotGloballyInvalidate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(alice, null, Collections.emptyList())
        );

        authController.logout(new LogoutRequest(null));

        assertNull(alice.getTokensInvalidatedBefore());
    }

    // =========================================================================
    // 4. PASSWORD CHANGE TESTS
    // =========================================================================

    @Test
    @DisplayName("Password Change 1: Successful password change updates hash, invalidates sessions, revokes refresh tokens, and audits")
    void testPasswordChangeSuccess() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.changePassword(alice, "Password123!", "NewSecretPassword456!");

        // Password hash updated
        assertTrue(passwordEncoder.matches("NewSecretPassword456!", alice.getPassword()));
        assertFalse(passwordEncoder.matches("Password123!", alice.getPassword()));

        // Global invalidation timestamp set
        assertNotNull(alice.getTokensInvalidatedBefore());

        // All refresh tokens revoked
        verify(refreshTokenRepository).revokeAllByUser(alice);

        // Audit PASSWORD_CHANGED emitted
        verify(auditLogRepository).save(argThat(log ->
                log.getAction() == AuditAction.PASSWORD_CHANGED && log.getUserId().equals(1L)
        ));
    }

    @Test
    @DisplayName("Password Change 2: Wrong current password throws 400 Bad Request")
    void testPasswordChangeWrongCurrentPassword() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userService.changePassword(alice, "WrongPassword!", "NewSecretPassword456!"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Current password does not match", ex.getReason());
        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeAllByUser(any());
    }

    @Test
    @DisplayName("Password Change 3: Reusing current password throws 400 Bad Request")
    void testPasswordChangeSamePasswordRejected() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userService.changePassword(alice, "Password123!", "Password123!"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("New password cannot be the same as current password", ex.getReason());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Password Change 4: POST /users/change-password endpoint changes password for authenticated user")
    void testUserControllerChangePasswordEndpoint() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(alice, null, Collections.emptyList())
        );
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = userController.changePassword(new ChangePasswordRequest("Password123!", "BrandNewPassword789!"));

        assertEquals("Password changed successfully", response.get("message"));
        assertTrue(passwordEncoder.matches("BrandNewPassword789!", alice.getPassword()));
    }

    // =========================================================================
    // 5. BRUTE FORCE PROTECTION TESTS
    // =========================================================================

    @Test
    @DisplayName("Brute Force 1: 4 consecutive failed password attempts increments counter and account remains unlocked")
    void testBruteForceFourFailuresAllowed() {
        when(userRepository.findByEmail(alice.getEmail())).thenReturn(Optional.of(alice));

        for (int i = 1; i <= 4; i++) {
            assertThrows(ResponseStatusException.class, () -> userService.authenticate(alice.getEmail(), "WrongSecret"));
            assertEquals(i, alice.getFailedLoginAttempts());
            assertNull(alice.getLockoutUntil());
        }

        // Only LOGIN_FAILURE logged, no ACCOUNT_LOCKED
        verify(auditLogRepository, times(4)).save(argThat(log -> log.getAction() == AuditAction.LOGIN_FAILURE));
        verify(auditLogRepository, never()).save(argThat(log -> log.getAction() == AuditAction.ACCOUNT_LOCKED));
    }

    @Test
    @DisplayName("Brute Force 2: 5th consecutive failed attempt locks account for 15 minutes and logs ACCOUNT_LOCKED")
    void testBruteForceFifthFailureLocksAccount() {
        alice.setFailedLoginAttempts(4);
        when(userRepository.findByEmail(alice.getEmail())).thenReturn(Optional.of(alice));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userService.authenticate(alice.getEmail(), "WrongSecret"));

        // Generic error response
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals("Invalid email or password", ex.getReason());

        assertEquals(5, alice.getFailedLoginAttempts());
        assertNotNull(alice.getLockoutUntil());
        assertTrue(alice.getLockoutUntil().isAfter(LocalDateTime.now().plusMinutes(14)));

        // ACCOUNT_LOCKED audit log recorded
        verify(auditLogRepository).save(argThat(log -> log.getAction() == AuditAction.ACCOUNT_LOCKED));
    }

    @Test
    @DisplayName("Brute Force 3: Locked account rejects without invoking BCrypt password matching")
    void testBruteForceLockedAccountSkipsBcrypt() {
        PasswordEncoder spyEncoder = spy(passwordEncoder);
        UserService testUserService = new UserService(userRepository, spyEncoder, auditLogService, refreshTokenService);

        alice.setFailedLoginAttempts(5);
        alice.setLockoutUntil(LocalDateTime.now().plusMinutes(10));
        when(userRepository.findByEmail(alice.getEmail())).thenReturn(Optional.of(alice));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> testUserService.authenticate(alice.getEmail(), "Password123!"));

        // Generic error response (does not reveal account is locked)
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals("Invalid email or password", ex.getReason());

        // BCrypt matching was NEVER executed while account is locked
        verify(spyEncoder, never()).matches(any(), any());
    }

    @Test
    @DisplayName("Brute Force 4: Successful login resets failed attempts and clears lockout timestamp")
    void testBruteForceSuccessResetsCounter() {
        alice.setFailedLoginAttempts(3);
        when(userRepository.findByEmail(alice.getEmail())).thenReturn(Optional.of(alice));

        User authenticated = userService.authenticate(alice.getEmail(), "Password123!");

        assertNotNull(authenticated);
        assertEquals(0, alice.getFailedLoginAttempts());
        assertNull(alice.getLockoutUntil());
        verify(userRepository).save(alice);
    }

    // =========================================================================
    // 6. AUDIT RESILIENCE TESTS
    // =========================================================================

    @Test
    @DisplayName("Audit Resilience 1: Audit failure does NOT break login")
    void testAuditFailureDoesNotBreakLogin() {
        when(userRepository.findByEmail(alice.getEmail())).thenReturn(Optional.of(alice));
        doThrow(new RuntimeException("Audit DB down")).when(auditLogRepository).save(any(AuditLog.class));

        User loggedIn = assertDoesNotThrow(() -> userService.authenticate(alice.getEmail(), "Password123!"));
        assertNotNull(loggedIn);
    }

    @Test
    @DisplayName("Audit Resilience 2: Audit failure does NOT break logout")
    void testAuditFailureDoesNotBreakLogout() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(alice, null, Collections.emptyList())
        );
        doThrow(new RuntimeException("Audit DB down")).when(auditLogRepository).save(any(AuditLog.class));

        var response = assertDoesNotThrow(() -> authController.logout(new LogoutRequest(null)));
        assertEquals("Logged out successfully", response.get("message"));
    }

    @Test
    @DisplayName("Audit Resilience 3: Audit failure does NOT break password change")
    void testAuditFailureDoesNotBreakPasswordChange() {
        doThrow(new RuntimeException("Audit DB down")).when(auditLogRepository).save(any(AuditLog.class));

        assertDoesNotThrow(() -> userService.changePassword(alice, "Password123!", "ResilientPassword999!"));
        assertTrue(passwordEncoder.matches("ResilientPassword999!", alice.getPassword()));
    }
}
