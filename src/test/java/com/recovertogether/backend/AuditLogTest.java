package com.recovertogether.backend;

import com.recovertogether.backend.controller.UserController;
import com.recovertogether.backend.dto.SupportRequestResponse;
import com.recovertogether.backend.entity.*;
import com.recovertogether.backend.enums.AuditAction;
import com.recovertogether.backend.enums.NotificationType;
import com.recovertogether.backend.enums.PartnerRequestStatus;
import com.recovertogether.backend.repository.*;
import com.recovertogether.backend.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PartnerRequestRepository partnerRequestRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private PartnerRequestService partnerRequestService;

    @Mock
    private SupportRequestRepository supportRequestRepository;

    @Mock
    private RecoveryResourceService recoveryResourceService;

    @Mock
    private DailyCheckInService dailyCheckInService;

    private AuditLogService auditLogService;
    private UserService userService;
    private UserController userController;
    private PartnerRequestService concretePartnerRequestService;
    private SupportService supportService;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogService(auditLogRepository);
        userService = new UserService(userRepository, passwordEncoder, auditLogService);
        userController = new UserController(userRepository, userService, auditLogService);
        concretePartnerRequestService = new PartnerRequestService(
                partnerRequestRepository,
                userRepository,
                dailyCheckInService,
                auditLogService
        );
        supportService = new SupportService(
                partnerRequestRepository,
                messageRepository,
                notificationService,
                partnerRequestService,
                supportRequestRepository,
                recoveryResourceService,
                auditLogService
        );

        alice = new User();
        alice.setName("Alice");
        alice.setEmail("alice@example.com");
        alice.setPassword("encoded_secret");
        setId(alice, 1L);

        bob = new User();
        bob.setName("Bob");
        bob.setEmail("bob@example.com");
        bob.setPassword("encoded_secret");
        setId(bob, 2L);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(alice, null, Collections.emptyList())
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.50");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
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

    @Test
    @DisplayName("1. Successful login creates LOGIN_SUCCESS audit log with user info")
    void testLoginSuccessAudit() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
        when(passwordEncoder.matches("raw_secret", "encoded_secret")).thenReturn(true);

        String result = userService.login("alice@example.com", "raw_secret");

        assertEquals("Login Successful", result);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog logged = captor.getValue();

        assertEquals(AuditAction.LOGIN_SUCCESS, logged.getAction());
        assertEquals(1L, logged.getUserId());
        assertEquals("alice@example.com", logged.getUserEmail());
        assertEquals("192.168.1.50", logged.getIpAddress());
        assertNull(logged.getDetails());
    }

    @Test
    @DisplayName("2. Failed login with wrong password creates LOGIN_FAILURE audit log with known user")
    void testLoginFailureWrongPasswordAudit() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
        when(passwordEncoder.matches("wrong_secret", "encoded_secret")).thenReturn(false);

        assertThrows(ResponseStatusException.class, () -> userService.login("alice@example.com", "wrong_secret"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog logged = captor.getValue();

        assertEquals(AuditAction.LOGIN_FAILURE, logged.getAction());
        assertEquals(1L, logged.getUserId());
        assertEquals("alice@example.com", logged.getUserEmail());
        assertEquals("Invalid password", logged.getDetails());
    }

    @Test
    @DisplayName("3. Failed login with unknown user creates LOGIN_FAILURE audit log with null userId")
    void testLoginFailureUnknownUserAudit() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> userService.login("unknown@example.com", "any"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog logged = captor.getValue();

        assertEquals(AuditAction.LOGIN_FAILURE, logged.getAction());
        assertNull(logged.getUserId());
        assertEquals("unknown@example.com", logged.getUserEmail());
        assertEquals("User not found", logged.getDetails());
    }

    @Test
    @DisplayName("4. User registration creates USER_REGISTERED audit log")
    void testUserRegistrationAudit() {
        User newUser = new User();
        newUser.setName("Charlie");
        newUser.setEmail("charlie@example.com");
        newUser.setPassword("raw_password");

        when(userRepository.existsByEmail("charlie@example.com")).thenReturn(false);
        when(passwordEncoder.encode("raw_password")).thenReturn("encoded_charlie");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            setId(u, 3L);
            return u;
        });

        User registered = userService.register(newUser);

        assertNotNull(registered);
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog logged = captor.getValue();

        assertEquals(AuditAction.USER_REGISTERED, logged.getAction());
        assertEquals(3L, logged.getUserId());
        assertEquals("charlie@example.com", logged.getUserEmail());
    }

    @Test
    @DisplayName("5. Account deletion creates ACCOUNT_DELETED audit log")
    void testAccountDeletionAudit() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(alice));

        String result = userController.deleteUser(1L);

        assertEquals("User deleted successfully", result);
        verify(userRepository).delete(alice);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog logged = captor.getValue();

        assertEquals(AuditAction.ACCOUNT_DELETED, logged.getAction());
        assertEquals(1L, logged.getUserId());
        assertEquals("alice@example.com", logged.getUserEmail());
    }

    @Test
    @DisplayName("6. Partner request sent creates PARTNER_REQUEST_SENT audit log with sender as actor")
    void testPartnerRequestSentAudit() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(bob));
        when(partnerRequestRepository.findFirstBySenderAndStatus(any(), eq(PartnerRequestStatus.ACCEPTED))).thenReturn(Optional.empty());
        when(partnerRequestRepository.findFirstByReceiverAndStatus(any(), eq(PartnerRequestStatus.ACCEPTED))).thenReturn(Optional.empty());
        when(partnerRequestRepository.findBySenderAndReceiver(alice, bob)).thenReturn(Optional.empty());
        when(partnerRequestRepository.findBySenderAndReceiver(bob, alice)).thenReturn(Optional.empty());

        concretePartnerRequestService.sendRequest(2L);

        verify(partnerRequestRepository).save(any(PartnerRequest.class));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog logged = captor.getValue();

        assertEquals(AuditAction.PARTNER_REQUEST_SENT, logged.getAction());
        assertEquals(1L, logged.getUserId(), "Sender must be actor");
        assertEquals("alice@example.com", logged.getUserEmail());
        assertEquals("Target User ID: 2", logged.getDetails());
    }

    @Test
    @DisplayName("7. Partner request accepted creates PARTNER_REQUEST_ACCEPTED audit log with receiver as actor")
    void testPartnerRequestAcceptedAudit() {
        // Authenticate Bob as the receiver who accepts
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(bob, null, Collections.emptyList())
        );

        PartnerRequest req = new PartnerRequest();
        req.setSender(alice);
        req.setReceiver(bob);
        req.setStatus(PartnerRequestStatus.PENDING);
        try {
            var f = PartnerRequest.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(req, 10L);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(partnerRequestRepository.findById(10L)).thenReturn(Optional.of(req));
        when(partnerRequestRepository.findFirstBySenderAndStatus(any(), eq(PartnerRequestStatus.ACCEPTED))).thenReturn(Optional.empty());
        when(partnerRequestRepository.findFirstByReceiverAndStatus(any(), eq(PartnerRequestStatus.ACCEPTED))).thenReturn(Optional.empty());

        concretePartnerRequestService.acceptRequest(10L);

        assertEquals(PartnerRequestStatus.ACCEPTED, req.getStatus());
        verify(partnerRequestRepository).save(req);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog logged = captor.getValue();

        assertEquals(AuditAction.PARTNER_REQUEST_ACCEPTED, logged.getAction());
        assertEquals(2L, logged.getUserId(), "Bob (receiver) must be actor");
        assertEquals("bob@example.com", logged.getUserEmail());
        assertEquals("Request ID: 10", logged.getDetails());
    }

    @Test
    @DisplayName("8. Partner request rejected creates PARTNER_REQUEST_REJECTED audit log with receiver as actor")
    void testPartnerRequestRejectedAudit() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(bob, null, Collections.emptyList())
        );

        PartnerRequest req = new PartnerRequest();
        req.setSender(alice);
        req.setReceiver(bob);
        req.setStatus(PartnerRequestStatus.PENDING);
        try {
            var f = PartnerRequest.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(req, 11L);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(partnerRequestRepository.findById(11L)).thenReturn(Optional.of(req));

        concretePartnerRequestService.rejectRequest(11L);

        assertEquals(PartnerRequestStatus.REJECTED, req.getStatus());
        verify(partnerRequestRepository).save(req);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog logged = captor.getValue();

        assertEquals(AuditAction.PARTNER_REQUEST_REJECTED, logged.getAction());
        assertEquals(2L, logged.getUserId());
        assertEquals("bob@example.com", logged.getUserEmail());
        assertEquals("Request ID: 11", logged.getDetails());
    }

    @Test
    @DisplayName("9. Unpairing creates PARTNER_UNPAIRED audit log")
    void testPartnerUnpairedAudit() {
        PartnerRequest req = new PartnerRequest();
        req.setSender(alice);
        req.setReceiver(bob);
        req.setStatus(PartnerRequestStatus.ACCEPTED);

        when(partnerRequestRepository.findFirstBySenderAndStatus(alice, PartnerRequestStatus.ACCEPTED))
                .thenReturn(Optional.of(req));

        concretePartnerRequestService.unpair();

        verify(partnerRequestRepository).delete(req);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog logged = captor.getValue();

        assertEquals(AuditAction.PARTNER_UNPAIRED, logged.getAction());
        assertEquals(1L, logged.getUserId());
        assertEquals("alice@example.com", logged.getUserEmail());
    }

    @Test
    @DisplayName("10. Support request creates SUPPORT_REQUEST_CREATED audit log without storing sensitive notes")
    void testSupportRequestCreatedAudit() {
        when(partnerRequestService.findPartnerUser(alice)).thenReturn(Optional.of(bob));

        SupportRequestResponse res = supportService.requestSupport("Very sensitive note about my struggles");

        assertTrue(res.isPartnerNotified());

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog logged = captor.getValue();

        assertEquals(AuditAction.SUPPORT_REQUEST_CREATED, logged.getAction());
        assertEquals(1L, logged.getUserId());
        assertEquals("alice@example.com", logged.getUserEmail());
        assertEquals("Partner paired", logged.getDetails());
        // Verify sensitive note was NEVER recorded in audit log
        assertFalse(logged.getDetails().contains("Very sensitive note"));
    }

    @Test
    @DisplayName("11. SOS alert creates SOS_TRIGGERED audit log")
    void testSOSTriggeredAudit() {
        when(partnerRequestService.getPartner(alice)).thenReturn(bob);
        when(messageRepository.existsBySenderAndSosAlertTrueAndCreatedAtAfter(eq(alice), any(LocalDateTime.class)))
                .thenReturn(false);

        supportService.sendSOS();

        verify(messageRepository).save(any(Message.class));
        verify(notificationService).createNotification(bob, NotificationType.SOS_ALERT, "Alice NEEDS SUPPORT IMMEDIATELY");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog logged = captor.getValue();

        assertEquals(AuditAction.SOS_TRIGGERED, logged.getAction());
        assertEquals(1L, logged.getUserId());
        assertEquals("alice@example.com", logged.getUserEmail());
    }

    // ==========================================
    // RESILIENCE & FAILURE TOLERANCE TESTS
    // ==========================================

    @Test
    @DisplayName("12. Resilience: If AuditLogRepository.save() fails, login STILL succeeds")
    void testAuditFailureDoesNotBreakLogin() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
        when(passwordEncoder.matches("raw_secret", "encoded_secret")).thenReturn(true);
        doThrow(new RuntimeException("Database connection timeout on audit log"))
                .when(auditLogRepository).save(any(AuditLog.class));

        String result = assertDoesNotThrow(() -> userService.login("alice@example.com", "raw_secret"));

        assertEquals("Login Successful", result);
    }

    @Test
    @DisplayName("13. Resilience: If AuditLogRepository.save() fails, sendSOS STILL succeeds")
    void testAuditFailureDoesNotBreakSOS() {
        when(partnerRequestService.getPartner(alice)).thenReturn(bob);
        when(messageRepository.existsBySenderAndSosAlertTrueAndCreatedAtAfter(eq(alice), any(LocalDateTime.class)))
                .thenReturn(false);
        doThrow(new RuntimeException("Database error on audit log"))
                .when(auditLogRepository).save(any(AuditLog.class));

        assertDoesNotThrow(() -> supportService.sendSOS());

        verify(messageRepository).save(any(Message.class));
        verify(notificationService).createNotification(bob, NotificationType.SOS_ALERT, "Alice NEEDS SUPPORT IMMEDIATELY");
    }

    @Test
    @DisplayName("14. Resilience: If AuditLogRepository.save() fails, requestSupport STILL succeeds")
    void testAuditFailureDoesNotBreakSupportRequest() {
        when(partnerRequestService.findPartnerUser(alice)).thenReturn(Optional.of(bob));
        doThrow(new RuntimeException("Disk full on audit table"))
                .when(auditLogRepository).save(any(AuditLog.class));

        SupportRequestResponse response = assertDoesNotThrow(() -> supportService.requestSupport("Help"));

        assertTrue(response.isPartnerNotified());
        verify(notificationService).createNotification(
                bob,
                NotificationType.SUPPORT_REQUEST,
                "Your partner may need some support right now."
        );
    }
}
