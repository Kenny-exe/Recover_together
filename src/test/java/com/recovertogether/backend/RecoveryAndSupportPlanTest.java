package com.recovertogether.backend;

import com.recovertogether.backend.controller.RecoveryPlanController;
import com.recovertogether.backend.controller.SupportController;
import com.recovertogether.backend.controller.SupportPlanController;
import com.recovertogether.backend.dto.*;
import com.recovertogether.backend.entity.*;
import com.recovertogether.backend.enums.CheckInStatus;
import com.recovertogether.backend.enums.NotificationType;
import com.recovertogether.backend.enums.PartnerRequestStatus;
import com.recovertogether.backend.enums.SupportAction;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecoveryAndSupportPlanTest {

    @Mock
    private RecoveryPlanRepository recoveryPlanRepository;

    @Mock
    private SupportPlanRepository supportPlanRepository;

    @Mock
    private SupportRequestRepository supportRequestRepository;

    @Mock
    private PartnerRequestRepository partnerRequestRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private PartnerRequestService partnerRequestService;

    @Mock
    private DailyCheckInRepository dailyCheckInRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AchievementService achievementService;

    @Mock
    private RecoveryResourceService recoveryResourceService;

    @Mock
    private AuditLogService auditLogService;

    private RecoveryPlanService recoveryPlanService;
    private SupportPlanService supportPlanService;
    private SupportService supportService;
    private DailyCheckInService dailyCheckInService;

    private RecoveryPlanController recoveryPlanController;
    private SupportPlanController supportPlanController;
    private SupportController supportController;

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        recoveryPlanService = new RecoveryPlanService(recoveryPlanRepository);
        supportPlanService = new SupportPlanService(supportPlanRepository, recoveryPlanRepository, partnerRequestService);
        supportService = new SupportService(
                partnerRequestRepository,
                messageRepository,
                notificationService,
                partnerRequestService,
                supportRequestRepository,
                recoveryResourceService,
                auditLogService
        );
        dailyCheckInService = new DailyCheckInService(
                dailyCheckInRepository,
                userRepository,
                notificationService,
                partnerRequestRepository,
                achievementService
        );

        recoveryPlanController = new RecoveryPlanController(recoveryPlanService);
        supportPlanController = new SupportPlanController(supportPlanService);
        supportController = new SupportController(supportService);

        userA = new User();
        userA.setName("Alice");
        userA.setEmail("alice@example.com");
        setId(userA, 1L);

        userB = new User();
        userB.setName("Bob");
        userB.setEmail("bob@example.com");
        setId(userB, 2L);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userA, null, Collections.emptyList())
        );
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

    // ==========================================
    // RECOVERY PLAN TESTS
    // ==========================================

    @Test
    @DisplayName("1. User can create their own recovery plan")
    void testCreateOwnRecoveryPlan() {
        RecoveryPlanRequest request = new RecoveryPlanRequest(
                "Stay sober for 90 days",
                List.of("Go for a walk", "Meditation"),
                List.of("Stress", "Certain locations"),
                List.of("Counselor")
        );

        when(recoveryPlanRepository.existsByUser(userA)).thenReturn(false);
        when(recoveryPlanRepository.save(any(RecoveryPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        RecoveryPlanResponse response = recoveryPlanController.createPlan(request);

        assertNotNull(response);
        assertEquals("Stay sober for 90 days", response.getGoal());
        assertEquals(2, response.getCopingStrategies().size());
        assertEquals(2, response.getTriggers().size());
        assertEquals(1, response.getContacts().size());
        verify(recoveryPlanRepository).save(any(RecoveryPlan.class));
    }

    @Test
    @DisplayName("2. Creating duplicate recovery plan throws 400 Bad Request")
    void testCreateDuplicateRecoveryPlanFails() {
        RecoveryPlanRequest request = new RecoveryPlanRequest("Goal", List.of(), List.of(), List.of());
        when(recoveryPlanRepository.existsByUser(userA)).thenReturn(true);

        assertThrows(ResponseStatusException.class, () -> recoveryPlanController.createPlan(request));
        verify(recoveryPlanRepository, never()).save(any());
    }

    @Test
    @DisplayName("3. User can retrieve their own recovery plan")
    void testGetOwnRecoveryPlan() {
        RecoveryPlan plan = new RecoveryPlan(
                userA,
                "Goal 90",
                List.of("Exercise"),
                List.of("Loneliness"),
                List.of("Support group")
        );
        when(recoveryPlanRepository.findByUser(userA)).thenReturn(Optional.of(plan));

        RecoveryPlanResponse response = recoveryPlanController.getPlan();

        assertNotNull(response);
        assertEquals("Goal 90", response.getGoal());
        assertEquals(List.of("Exercise"), response.getCopingStrategies());
    }

    @Test
    @DisplayName("4. User can update their own recovery plan")
    void testUpdateOwnRecoveryPlan() {
        RecoveryPlan existingPlan = new RecoveryPlan(
                userA,
                "Old Goal",
                List.of("Walk"),
                List.of("Stress"),
                List.of("Friend")
        );
        when(recoveryPlanRepository.findByUser(userA)).thenReturn(Optional.of(existingPlan));
        when(recoveryPlanRepository.save(any(RecoveryPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        RecoveryPlanRequest updateReq = new RecoveryPlanRequest(
                "New Goal 180 days",
                List.of("Walk", "Yoga"),
                List.of("Stress", "Fatigue"),
                List.of("Friend", "Therapist")
        );

        RecoveryPlanResponse response = recoveryPlanController.updatePlan(updateReq);

        assertEquals("New Goal 180 days", response.getGoal());
        assertEquals(2, response.getCopingStrategies().size());
        assertEquals(2, response.getTriggers().size());
        verify(recoveryPlanRepository).save(existingPlan);
    }

    @Test
    @DisplayName("5. User can delete their own recovery plan")
    void testDeleteOwnRecoveryPlan() {
        RecoveryPlan plan = new RecoveryPlan(userA, "Goal", List.of(), List.of(), List.of());
        when(recoveryPlanRepository.findByUser(userA)).thenReturn(Optional.of(plan));

        MessageResponse response = recoveryPlanController.deletePlan();

        assertEquals("Recovery plan deleted successfully", response.getMessage());
        verify(recoveryPlanRepository).delete(plan);
    }

    @Test
    @DisplayName("6. User cannot access or modify another user's plan (IDOR prevented)")
    void testIDORProtectionOnRecoveryPlan() {
        // When Alice is authenticated, service only looks up Alice's plan
        when(recoveryPlanRepository.findByUser(userA)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> recoveryPlanController.getPlan());
        verify(recoveryPlanRepository, never()).findByUser(userB);
    }

    // ==========================================
    // SUPPORT PLAN TESTS
    // ==========================================

    @Test
    @DisplayName("7. User can create/update their own support preferences")
    void testCreateOrUpdateSupportPlan() {
        Set<SupportAction> actions = Set.of(
                SupportAction.SEND_MESSAGE,
                SupportAction.REMIND_ME_OF_MY_GOAL,
                SupportAction.REMIND_ME_OF_MY_COPING_PLAN
        );
        SupportPlanRequest request = new SupportPlanRequest(actions);

        when(supportPlanRepository.findByUser(userA)).thenReturn(Optional.empty());
        when(supportPlanRepository.save(any(SupportPlan.class))).thenAnswer(inv -> inv.getArgument(0));

        SupportPlanResponse response = supportPlanController.updatePlan(request);

        assertNotNull(response);
        assertEquals(actions, response.getSupportActions());
        verify(supportPlanRepository).save(any(SupportPlan.class));
    }

    @Test
    @DisplayName("8. Partner view exposes only consented support actions, goal, and coping strategies")
    void testPartnerSupportInfoWithConsent() {
        // User A asks for partner User B's support info
        when(partnerRequestService.findPartnerUser(userA)).thenReturn(Optional.of(userB));

        Set<SupportAction> bActions = Set.of(
                SupportAction.SEND_MESSAGE,
                SupportAction.REMIND_ME_OF_MY_GOAL,
                SupportAction.REMIND_ME_OF_MY_COPING_PLAN
        );
        SupportPlan bPlan = new SupportPlan(userB, bActions);
        when(supportPlanRepository.findByUser(userB)).thenReturn(Optional.of(bPlan));

        RecoveryPlan bRecPlan = new RecoveryPlan(
                userB,
                "Stay sober for 90 days",
                List.of("Deep breathing", "Call sponsor"),
                List.of("Private Trigger 1", "Private Trigger 2"),
                List.of("Private Contact")
        );
        when(recoveryPlanRepository.findByUser(userB)).thenReturn(Optional.of(bRecPlan));

        PartnerSupportResponse partnerInfo = supportPlanController.getPartnerSupportInfo();

        assertNotNull(partnerInfo);
        assertEquals("Bob", partnerInfo.getPartnerName());
        assertEquals(bActions, partnerInfo.getSupportActions());
        assertEquals("Stay sober for 90 days", partnerInfo.getGoal());
        assertEquals(List.of("Deep breathing", "Call sponsor"), partnerInfo.getCopingStrategies());
    }

    @Test
    @DisplayName("9. Partner view NEVER exposes triggers or private contacts")
    void testPartnerViewNeverExposesTriggersOrContacts() {
        when(partnerRequestService.findPartnerUser(userA)).thenReturn(Optional.of(userB));

        SupportPlan bPlan = new SupportPlan(userB, Set.of(SupportAction.SEND_MESSAGE));
        when(supportPlanRepository.findByUser(userB)).thenReturn(Optional.of(bPlan));

        PartnerSupportResponse partnerInfo = supportPlanController.getPartnerSupportInfo();

        assertNotNull(partnerInfo);
        assertNull(partnerInfo.getGoal(), "Goal must be null if REMIND_ME_OF_MY_GOAL not selected");
        assertTrue(partnerInfo.getCopingStrategies().isEmpty(), "Coping strategies must be empty if REMIND_ME_OF_MY_COPING_PLAN not selected");
        // PartnerSupportResponse has no getter/field for triggers or contacts whatsoever
        verify(recoveryPlanRepository, never()).findByUser(any());
    }

    @Test
    @DisplayName("10. Partner support endpoint throws 404 if user has no partner")
    void testPartnerSupportInfoWithoutPartnerFails() {
        when(partnerRequestService.findPartnerUser(userA)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> supportPlanController.getPartnerSupportInfo());
    }

    // ==========================================
    // EXPLICIT SUPPORT REQUEST TESTS
    // ==========================================

    @Test
    @DisplayName("11. User explicitly requests support and accepted partner receives gentle notification")
    void testExplicitSupportRequestWithPartner() {
        when(partnerRequestService.findPartnerUser(userA)).thenReturn(Optional.of(userB));

        SupportRequestResponse res = supportController.requestSupport(new SupportRequestDto("Having a tough day"));

        assertTrue(res.isPartnerNotified());
        assertEquals("Support request sent to your partner", res.getMessage());

        verify(notificationService).createNotification(
                userB,
                NotificationType.SUPPORT_REQUEST,
                "Your partner may need some support right now."
        );
        verify(supportRequestRepository).save(any(SupportRequest.class));
    }

    @Test
    @DisplayName("12. User without a partner requesting support does not throw an exception")
    void testExplicitSupportRequestWithoutPartner() {
        when(partnerRequestService.findPartnerUser(userA)).thenReturn(Optional.empty());

        SupportRequestResponse res = supportController.requestSupport(new SupportRequestDto("Need help"));

        assertFalse(res.isPartnerNotified());
        assertNotNull(res.getMessage());
        verifyNoInteractions(notificationService);
        verify(supportRequestRepository).save(any(SupportRequest.class));
    }

    // ==========================================
    // DAILY CHECK-IN & STREAK REGRESSION TESTS
    // ==========================================

    @Test
    @DisplayName("13. Check-in with STRUGGLING preserves streak and does NOT notify partner")
    void testCheckInStrugglingDoesNotNotifyPartner() {
        when(dailyCheckInRepository.findByUserAndDate(userA, LocalDate.now())).thenReturn(Optional.empty());

        CheckInRequest request = new CheckInRequest();
        request.setStatus(CheckInStatus.STRUGGLING);
        request.setNote("Tough cravings today");

        dailyCheckInService.submitCheckIn(request);

        verify(dailyCheckInRepository).save(any(DailyCheckIn.class));
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("14. Check-in with STRONG_URGE preserves streak and does NOT notify partner")
    void testCheckInStrongUrgeDoesNotNotifyPartner() {
        when(dailyCheckInRepository.findByUserAndDate(userA, LocalDate.now())).thenReturn(Optional.empty());

        CheckInRequest request = new CheckInRequest();
        request.setStatus(CheckInStatus.STRONG_URGE);
        request.setNote("Very strong craving");

        dailyCheckInService.submitCheckIn(request);

        verify(dailyCheckInRepository).save(any(DailyCheckIn.class));
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("15. Check-in with NEED_SUPPORT notifies partner with SUPPORT_REQUEST")
    void testCheckInNeedSupportNotifiesPartner() {
        when(dailyCheckInRepository.findByUserAndDate(userA, LocalDate.now())).thenReturn(Optional.empty());

        PartnerRequest pr = new PartnerRequest();
        pr.setSender(userA);
        pr.setReceiver(userB);
        pr.setStatus(PartnerRequestStatus.ACCEPTED);

        when(partnerRequestRepository.findFirstBySenderAndStatus(userA, PartnerRequestStatus.ACCEPTED))
                .thenReturn(Optional.of(pr));

        CheckInRequest request = new CheckInRequest();
        request.setStatus(CheckInStatus.NEED_SUPPORT);
        request.setNote("Please reach out");

        dailyCheckInService.submitCheckIn(request);

        verify(dailyCheckInRepository).save(any(DailyCheckIn.class));
        verify(notificationService).createNotification(
                userB,
                NotificationType.SUPPORT_REQUEST,
                "Your partner may need some support right now."
        );
    }

    @Test
    @DisplayName("16. Check-in with RELAPSE preserves existing partner relapse notification behavior")
    void testCheckInRelapseNotifiesPartner() {
        when(dailyCheckInRepository.findByUserAndDate(userA, LocalDate.now())).thenReturn(Optional.empty());

        PartnerRequest pr = new PartnerRequest();
        pr.setSender(userA);
        pr.setReceiver(userB);
        pr.setStatus(PartnerRequestStatus.ACCEPTED);

        when(partnerRequestRepository.findFirstBySenderAndStatus(userA, PartnerRequestStatus.ACCEPTED))
                .thenReturn(Optional.of(pr));

        CheckInRequest request = new CheckInRequest();
        request.setStatus(CheckInStatus.RELAPSE);
        request.setNote("Had a slip");

        dailyCheckInService.submitCheckIn(request);

        verify(dailyCheckInRepository).save(any(DailyCheckIn.class));
        verify(notificationService).createNotification(
                userB,
                NotificationType.PARTNER_RELAPSE,
                "Alice reported a relapse today"
        );
    }

    @Test
    @DisplayName("17. Streak calculation: STRUGGLING, STRONG_URGE, and NEED_SUPPORT count as maintaining recovery")
    void testStreakCalculationWithWellbeingStates() {
        LocalDate today = LocalDate.now();

        DailyCheckIn d1 = new DailyCheckIn();
        d1.setDate(today.minusDays(4));
        d1.setStatus(CheckInStatus.SUCCESS);

        DailyCheckIn d2 = new DailyCheckIn();
        d2.setDate(today.minusDays(3));
        d2.setStatus(CheckInStatus.STRUGGLING);

        DailyCheckIn d3 = new DailyCheckIn();
        d3.setDate(today.minusDays(2));
        d3.setStatus(CheckInStatus.STRONG_URGE);

        DailyCheckIn d4 = new DailyCheckIn();
        d4.setDate(today.minusDays(1));
        d4.setStatus(CheckInStatus.NEED_SUPPORT);

        DailyCheckIn d5 = new DailyCheckIn();
        d5.setDate(today);
        d5.setStatus(CheckInStatus.SUCCESS);

        when(dailyCheckInRepository.findByUserOrderByDateAsc(userA)).thenReturn(List.of(d1, d2, d3, d4, d5));

        StreakResponse streak = dailyCheckInService.calculateStreak(userA);

        assertEquals(5, streak.getCurrentStreak(), "Streak must continue through non-relapse states");
        assertEquals(5, streak.getBestStreak());
    }

    @Test
    @DisplayName("18. Streak calculation: ONLY RELAPSE resets current streak to 0")
    void testOnlyRelapseResetsStreak() {
        LocalDate today = LocalDate.now();

        DailyCheckIn d1 = new DailyCheckIn();
        d1.setDate(today.minusDays(3));
        d1.setStatus(CheckInStatus.STRUGGLING);

        DailyCheckIn d2 = new DailyCheckIn();
        d2.setDate(today.minusDays(2));
        d2.setStatus(CheckInStatus.STRONG_URGE);

        DailyCheckIn d3 = new DailyCheckIn();
        d3.setDate(today.minusDays(1));
        d3.setStatus(CheckInStatus.RELAPSE); // Relapse yesterday

        DailyCheckIn d4 = new DailyCheckIn();
        d4.setDate(today);
        d4.setStatus(CheckInStatus.NEED_SUPPORT); // Support requested today

        when(dailyCheckInRepository.findByUserOrderByDateAsc(userA)).thenReturn(List.of(d1, d2, d3, d4));

        StreakResponse streak = dailyCheckInService.calculateStreak(userA);

        assertEquals(1, streak.getCurrentStreak(), "Current streak starts over after relapse");
        assertEquals(2, streak.getBestStreak(), "Best streak was 2 before relapse");
    }

    @Test
    @DisplayName("19. Statistics: non-relapse check-ins count toward successCount")
    void testStatsTreatNonRelapseAsCleanDays() {
        when(dailyCheckInRepository.countByUser(userA)).thenReturn(10L);
        when(dailyCheckInRepository.countByUserAndStatus(userA, CheckInStatus.RELAPSE)).thenReturn(2L);
        when(dailyCheckInRepository.findByUserOrderByDateAsc(userA)).thenReturn(List.of());

        CheckInStatsResponse stats = dailyCheckInService.getStats();

        assertEquals(10L, stats.getTotalCheckIns());
        assertEquals(2L, stats.getRelapseCount());
        assertEquals(8L, stats.getSuccessCount());
        assertEquals(80.0, stats.getSuccessRate(), 0.001);
    }
}
