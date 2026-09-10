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
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MissedCheckInSchedulerTest {

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

        userB = new User();
        userB.setName("Bob");
        userB.setEmail("bob@example.com");

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
        when(dailyCheckInRepository.findByUserAndDate(userA, LocalDate.now()))
                .thenReturn(Optional.of(new DailyCheckIn()));
        when(dailyCheckInRepository.findByUserAndDate(userB, LocalDate.now()))
                .thenReturn(Optional.of(new DailyCheckIn()));

        scheduler.checkMissedCheckIns();

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("Sender (Alice) missed check-in -> Receiver (Bob) gets notification")
    void testSenderMissedCheckIn() {
        when(partnerRequestRepository.findByStatus(PartnerRequestStatus.ACCEPTED))
                .thenReturn(List.of(partnership));
        when(dailyCheckInRepository.findByUserAndDate(userA, LocalDate.now()))
                .thenReturn(Optional.empty());
        when(dailyCheckInRepository.findByUserAndDate(userB, LocalDate.now()))
                .thenReturn(Optional.of(new DailyCheckIn()));
        when(notificationRepository.existsByReceiverAndTypeAndMessage(
                eq(userB), eq(NotificationType.MISSED_CHECKIN), eq("Alice missed today's check-in")))
                .thenReturn(false);

        scheduler.checkMissedCheckIns();

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
        when(dailyCheckInRepository.findByUserAndDate(userA, LocalDate.now()))
                .thenReturn(Optional.of(new DailyCheckIn()));
        when(dailyCheckInRepository.findByUserAndDate(userB, LocalDate.now()))
                .thenReturn(Optional.empty());
        when(notificationRepository.existsByReceiverAndTypeAndMessage(
                eq(userA), eq(NotificationType.MISSED_CHECKIN), eq("Bob missed today's check-in")))
                .thenReturn(false);

        scheduler.checkMissedCheckIns();

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
        when(dailyCheckInRepository.findByUserAndDate(userA, LocalDate.now()))
                .thenReturn(Optional.empty());
        when(dailyCheckInRepository.findByUserAndDate(userB, LocalDate.now()))
                .thenReturn(Optional.empty());
        when(notificationRepository.existsByReceiverAndTypeAndMessage(
                eq(userB), eq(NotificationType.MISSED_CHECKIN), eq("Alice missed today's check-in")))
                .thenReturn(false);
        when(notificationRepository.existsByReceiverAndTypeAndMessage(
                eq(userA), eq(NotificationType.MISSED_CHECKIN), eq("Bob missed today's check-in")))
                .thenReturn(false);

        scheduler.checkMissedCheckIns();

        verify(notificationService).createNotification(
                userB, NotificationType.MISSED_CHECKIN, "Alice missed today's check-in");
        verify(notificationService).createNotification(
                userA, NotificationType.MISSED_CHECKIN, "Bob missed today's check-in");
    }

    @Test
    @DisplayName("Notification already exists -> Duplicate prevention works")
    void testDuplicatePrevention() {
        when(partnerRequestRepository.findByStatus(PartnerRequestStatus.ACCEPTED))
                .thenReturn(List.of(partnership));
        when(dailyCheckInRepository.findByUserAndDate(userA, LocalDate.now()))
                .thenReturn(Optional.empty());
        when(dailyCheckInRepository.findByUserAndDate(userB, LocalDate.now()))
                .thenReturn(Optional.empty());
        // Already exists for both
        when(notificationRepository.existsByReceiverAndTypeAndMessage(
                eq(userB), eq(NotificationType.MISSED_CHECKIN), eq("Alice missed today's check-in")))
                .thenReturn(true);
        when(notificationRepository.existsByReceiverAndTypeAndMessage(
                eq(userA), eq(NotificationType.MISSED_CHECKIN), eq("Bob missed today's check-in")))
                .thenReturn(true);

        scheduler.checkMissedCheckIns();

        verifyNoInteractions(notificationService);
    }
}
