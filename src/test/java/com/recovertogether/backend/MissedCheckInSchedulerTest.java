package com.recovertogether.backend;

import com.recovertogether.backend.entity.DailyCheckIn;
import com.recovertogether.backend.entity.PartnerRequest;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.enums.NotificationType;
import com.recovertogether.backend.enums.PartnerRequestStatus;
import com.recovertogether.backend.repository.DailyCheckInRepository;
import com.recovertogether.backend.repository.NotificationRepository;
import com.recovertogether.backend.repository.PartnerRequestRepository;
import com.recovertogether.backend.service.MissedCheckInScheduler;
import com.recovertogether.backend.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MissedCheckInSchedulerTest {

    private static final LocalDate LOCAL_DAY = LocalDate.of(2026, 10, 28);
    private static final ZonedDateTime NINE_PM_UTC =
            ZonedDateTime.of(2026, 10, 28, 21, 0, 0, 0, ZoneOffset.UTC);

    @Mock
    private PartnerRequestRepository partnerRequestRepository;

    @Mock
    private DailyCheckInRepository dailyCheckInRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private MissedCheckInScheduler scheduler;

    private User userA;
    private User userB;
    private PartnerRequest partnership;

    @BeforeEach
    void setUp() {
        userA = new User();
        userA.setName("Alice");
        userA.setEmail("alice@example.com");
        userA.setTimezone("UTC");

        userB = new User();
        userB.setName("Bob");
        userB.setEmail("bob@example.com");
        userB.setTimezone("UTC");

        partnership = new PartnerRequest();
        partnership.setSender(userA);
        partnership.setReceiver(userB);
        partnership.setStatus(PartnerRequestStatus.ACCEPTED);
    }

    @Test
    @DisplayName("Both checked in today -> No notifications created")
    void testBothCheckedIn() {
        when(partnerRequestRepository.findByStatus(PartnerRequestStatus.ACCEPTED))
                .thenReturn(List.of(partnership));
        when(dailyCheckInRepository.findByUserAndDate(userA, LOCAL_DAY))
                .thenReturn(Optional.of(new DailyCheckIn()));
        when(dailyCheckInRepository.findByUserAndDate(userB, LOCAL_DAY))
                .thenReturn(Optional.of(new DailyCheckIn()));

        scheduler.checkMissedCheckIns(NINE_PM_UTC);

        verifyNoInteractions(notificationService);
        verifyNoInteractions(notificationRepository);
    }

    @Test
    @DisplayName("Sender (Alice) missed check-in -> Receiver (Bob) gets notification")
    void testSenderMissedCheckIn() {
        when(partnerRequestRepository.findByStatus(PartnerRequestStatus.ACCEPTED))
                .thenReturn(List.of(partnership));
        when(dailyCheckInRepository.findByUserAndDate(userA, LOCAL_DAY))
                .thenReturn(Optional.empty());
        when(dailyCheckInRepository.findByUserAndDate(userB, LOCAL_DAY))
                .thenReturn(Optional.of(new DailyCheckIn()));
        when(notificationRepository.existsByReceiverAndTypeAndMessageAndCreatedAtAfter(
                eq(userB), eq(NotificationType.MISSED_CHECKIN),
                eq("Alice missed today's check-in"), any(LocalDateTime.class)))
                .thenReturn(false);

        scheduler.checkMissedCheckIns(NINE_PM_UTC);

        verify(notificationService).createNotification(
                userB, NotificationType.MISSED_CHECKIN, "Alice missed today's check-in");
        verify(notificationService, never()).createNotification(
                eq(userA), any(), any());
    }

    @Test
    @DisplayName("Receiver (Bob) missed check-in -> Sender (Alice) gets notification")
    void testReceiverMissedCheckIn() {
        when(partnerRequestRepository.findByStatus(PartnerRequestStatus.ACCEPTED))
                .thenReturn(List.of(partnership));
        when(dailyCheckInRepository.findByUserAndDate(userA, LOCAL_DAY))
                .thenReturn(Optional.of(new DailyCheckIn()));
        when(dailyCheckInRepository.findByUserAndDate(userB, LOCAL_DAY))
                .thenReturn(Optional.empty());
        when(notificationRepository.existsByReceiverAndTypeAndMessageAndCreatedAtAfter(
                eq(userA), eq(NotificationType.MISSED_CHECKIN),
                eq("Bob missed today's check-in"), any(LocalDateTime.class)))
                .thenReturn(false);

        scheduler.checkMissedCheckIns(NINE_PM_UTC);

        verify(notificationService).createNotification(
                userA, NotificationType.MISSED_CHECKIN, "Bob missed today's check-in");
        verify(notificationService, never()).createNotification(
                eq(userB), any(), any());
    }

    @Test
    @DisplayName("Both missed check-in -> Both get notified about their partner")
    void testBothMissedCheckIn() {
        when(partnerRequestRepository.findByStatus(PartnerRequestStatus.ACCEPTED))
                .thenReturn(List.of(partnership));
        when(dailyCheckInRepository.findByUserAndDate(userA, LOCAL_DAY))
                .thenReturn(Optional.empty());
        when(dailyCheckInRepository.findByUserAndDate(userB, LOCAL_DAY))
                .thenReturn(Optional.empty());
        when(notificationRepository.existsByReceiverAndTypeAndMessageAndCreatedAtAfter(
                eq(userB), eq(NotificationType.MISSED_CHECKIN),
                eq("Alice missed today's check-in"), any(LocalDateTime.class)))
                .thenReturn(false);
        when(notificationRepository.existsByReceiverAndTypeAndMessageAndCreatedAtAfter(
                eq(userA), eq(NotificationType.MISSED_CHECKIN),
                eq("Bob missed today's check-in"), any(LocalDateTime.class)))
                .thenReturn(false);

        scheduler.checkMissedCheckIns(NINE_PM_UTC);

        verify(notificationService).createNotification(
                userB, NotificationType.MISSED_CHECKIN, "Alice missed today's check-in");
        verify(notificationService).createNotification(
                userA, NotificationType.MISSED_CHECKIN, "Bob missed today's check-in");
    }

    @Test
    @DisplayName("Notification already exists for the local day -> Duplicate prevention works")
    void testDuplicatePrevention() {
        when(partnerRequestRepository.findByStatus(PartnerRequestStatus.ACCEPTED))
                .thenReturn(List.of(partnership));
        when(dailyCheckInRepository.findByUserAndDate(userA, LOCAL_DAY))
                .thenReturn(Optional.empty());
        when(dailyCheckInRepository.findByUserAndDate(userB, LOCAL_DAY))
                .thenReturn(Optional.empty());
        when(notificationRepository.existsByReceiverAndTypeAndMessageAndCreatedAtAfter(
                eq(userB), eq(NotificationType.MISSED_CHECKIN),
                eq("Alice missed today's check-in"), any(LocalDateTime.class)))
                .thenReturn(true);
        when(notificationRepository.existsByReceiverAndTypeAndMessageAndCreatedAtAfter(
                eq(userA), eq(NotificationType.MISSED_CHECKIN),
                eq("Bob missed today's check-in"), any(LocalDateTime.class)))
                .thenReturn(true);

        scheduler.checkMissedCheckIns(NINE_PM_UTC);

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("Repeated scheduler runs within the same local day only notify once")
    void testRepeatedRunsOnlyNotifyOnce() {
        when(partnerRequestRepository.findByStatus(PartnerRequestStatus.ACCEPTED))
                .thenReturn(List.of(partnership));
        when(dailyCheckInRepository.findByUserAndDate(userA, LOCAL_DAY))
                .thenReturn(Optional.empty());
        when(dailyCheckInRepository.findByUserAndDate(userB, LOCAL_DAY))
                .thenReturn(Optional.of(new DailyCheckIn()));
        when(notificationRepository.existsByReceiverAndTypeAndMessageAndCreatedAtAfter(
                eq(userB), eq(NotificationType.MISSED_CHECKIN),
                eq("Alice missed today's check-in"), any(LocalDateTime.class)))
                .thenReturn(false)
                .thenReturn(true);

        // 21:00, 21:15, 21:30 and 21:45 local all fall inside the alert hour.
        scheduler.checkMissedCheckIns(NINE_PM_UTC);
        scheduler.checkMissedCheckIns(NINE_PM_UTC.plusMinutes(15));
        scheduler.checkMissedCheckIns(NINE_PM_UTC.plusMinutes(30));
        scheduler.checkMissedCheckIns(NINE_PM_UTC.plusMinutes(45));

        verify(notificationService, times(1)).createNotification(
                userB, NotificationType.MISSED_CHECKIN, "Alice missed today's check-in");
    }

    @Test
    @DisplayName("Outside 21:00 local -> no evaluation and no notifications")
    void testOutsideAlertHourDoesNothing() {
        when(partnerRequestRepository.findByStatus(PartnerRequestStatus.ACCEPTED))
                .thenReturn(List.of(partnership));

        scheduler.checkMissedCheckIns(NINE_PM_UTC.withHour(20));

        verifyNoInteractions(dailyCheckInRepository);
        verifyNoInteractions(notificationRepository);
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("Each user is evaluated at 21:00 in their own timezone")
    void testUsersEvaluatedInTheirOwnTimezone() {
        // Bob is in Tokyo (UTC+9), so 12:00 UTC is 21:00 for him. Alice in UTC is at 12:00.
        userB.setTimezone("Asia/Tokyo");
        ZonedDateTime noonUtc =
                ZonedDateTime.of(2026, 10, 28, 12, 0, 0, 0, ZoneOffset.UTC);

        when(partnerRequestRepository.findByStatus(PartnerRequestStatus.ACCEPTED))
                .thenReturn(List.of(partnership));
        when(dailyCheckInRepository.findByUserAndDate(userB, LOCAL_DAY))
                .thenReturn(Optional.empty());
        when(notificationRepository.existsByReceiverAndTypeAndMessageAndCreatedAtAfter(
                eq(userA), eq(NotificationType.MISSED_CHECKIN),
                eq("Bob missed today's check-in"), any(LocalDateTime.class)))
                .thenReturn(false);

        scheduler.checkMissedCheckIns(noonUtc);

        // Alice is not at 21:00 yet, so her missed check-in is not reported.
        verify(dailyCheckInRepository, never()).findByUserAndDate(eq(userA), any());
        verify(notificationService).createNotification(
                userA, NotificationType.MISSED_CHECKIN, "Bob missed today's check-in");
        verify(notificationService, never()).createNotification(
                eq(userB), any(), any());
    }

    @Test
    @DisplayName("Duplicate cutoff is the start of the user's local day in server time")
    void testDuplicateCutoffUsesUserLocalDayStart() {
        ZoneId kolkata = ZoneId.of("Asia/Kolkata");
        userA.setTimezone(kolkata.getId());
        // 15:30 UTC == 21:00 in Kolkata (UTC+5:30).
        ZonedDateTime kolkataNinePm =
                ZonedDateTime.of(2026, 10, 28, 15, 30, 0, 0, ZoneOffset.UTC);

        LocalDateTime expectedCutoff = LOCAL_DAY
                .atStartOfDay(kolkata)
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime()
                .minusSeconds(1);

        when(partnerRequestRepository.findByStatus(PartnerRequestStatus.ACCEPTED))
                .thenReturn(List.of(partnership));
        when(dailyCheckInRepository.findByUserAndDate(userA, LOCAL_DAY))
                .thenReturn(Optional.empty());
        when(notificationRepository.existsByReceiverAndTypeAndMessageAndCreatedAtAfter(
                eq(userB), eq(NotificationType.MISSED_CHECKIN),
                eq("Alice missed today's check-in"), eq(expectedCutoff)))
                .thenReturn(false);

        scheduler.checkMissedCheckIns(kolkataNinePm);

        verify(notificationService).createNotification(
                userB, NotificationType.MISSED_CHECKIN, "Alice missed today's check-in");
    }
}
