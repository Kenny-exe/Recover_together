package com.recovertogether.backend;

import com.recovertogether.backend.controller.CheckInReminderController;
import com.recovertogether.backend.dto.ReminderWindowRequest;
import com.recovertogether.backend.dto.ReminderWindowResponse;
import com.recovertogether.backend.entity.CheckInReminderSchedule;
import com.recovertogether.backend.entity.DailyCheckIn;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.enums.NotificationType;
import com.recovertogether.backend.enums.ReminderWindow;
import com.recovertogether.backend.repository.CheckInReminderScheduleRepository;
import com.recovertogether.backend.repository.DailyCheckInRepository;
import com.recovertogether.backend.repository.UserRepository;
import com.recovertogether.backend.service.CheckInReminderService;
import com.recovertogether.backend.service.NotificationService;
import com.recovertogether.backend.service.ReminderTimeGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckInReminderTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CheckInReminderScheduleRepository reminderScheduleRepository;

    @Mock
    private DailyCheckInRepository dailyCheckInRepository;

    @Mock
    private NotificationService notificationService;

    private CheckInReminderService reminderService;
    private CheckInReminderController reminderController;

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        reminderService = new CheckInReminderService(
                userRepository,
                reminderScheduleRepository,
                dailyCheckInRepository,
                notificationService
        );
        reminderController = new CheckInReminderController(reminderService);

        userA = new User();
        userA.setName("Alice");
        userA.setEmail("alice@example.com");
        userA.setReminderWindow(ReminderWindow.MORNING);
        setId(userA, 1L);

        userB = new User();
        userB.setName("Bob");
        userB.setEmail("bob@example.com");
        userB.setReminderWindow(ReminderWindow.AFTERNOON);
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

    @Test
    @DisplayName("1. Reminder is generated when user has not checked in and time is reached")
    void testReminderGeneratedWhenNotCheckedIn() {
        LocalDate today = LocalDate.now();
        LocalTime scheduledTime = LocalTime.of(9, 30);
        LocalTime currentTime = LocalTime.of(9, 30);

        CheckInReminderSchedule schedule = new CheckInReminderSchedule(userA, today, scheduledTime);

        when(userRepository.findAll()).thenReturn(List.of(userA));
        when(reminderScheduleRepository.existsByUserAndDate(userA, today)).thenReturn(true);
        when(reminderScheduleRepository.findByDateAndSentFalseAndScheduledTimeLessThanEqual(today, currentTime))
                .thenReturn(List.of(schedule));
        when(dailyCheckInRepository.findByUserAndDate(userA, today)).thenReturn(Optional.empty());

        reminderService.processReminders(today, currentTime);

        verify(notificationService).createNotification(
                userA,
                NotificationType.DAILY_CHECKIN_REMINDER,
                "How are you doing today? Take a moment to check in."
        );
        assertTrue(schedule.isSent());
        verify(reminderScheduleRepository).save(schedule);
    }

    @Test
    @DisplayName("2. Reminder is NOT generated when user has already checked in before scheduled time")
    void testReminderNotGeneratedWhenAlreadyCheckedIn() {
        LocalDate today = LocalDate.now();
        LocalTime scheduledTime = LocalTime.of(10, 0);
        LocalTime currentTime = LocalTime.of(10, 15);

        CheckInReminderSchedule schedule = new CheckInReminderSchedule(userA, today, scheduledTime);

        when(userRepository.findAll()).thenReturn(List.of(userA));
        when(reminderScheduleRepository.existsByUserAndDate(userA, today)).thenReturn(true);
        when(reminderScheduleRepository.findByDateAndSentFalseAndScheduledTimeLessThanEqual(today, currentTime))
                .thenReturn(List.of(schedule));
        when(dailyCheckInRepository.findByUserAndDate(userA, today)).thenReturn(Optional.of(new DailyCheckIn()));

        reminderService.processReminders(today, currentTime);

        verifyNoInteractions(notificationService);
        assertTrue(schedule.isSent());
        verify(reminderScheduleRepository).save(schedule);
    }

    @Test
    @DisplayName("3. Only one reminder is generated per user per day even if scheduler runs repeatedly")
    void testSingleReminderPerDayOnRepeatedSchedulerRuns() {
        LocalDate today = LocalDate.now();
        LocalTime scheduledTime = LocalTime.of(9, 0);
        LocalTime firstRunTime = LocalTime.of(9, 15);
        LocalTime secondRunTime = LocalTime.of(9, 30);

        CheckInReminderSchedule schedule = new CheckInReminderSchedule(userA, today, scheduledTime);

        when(userRepository.findAll()).thenReturn(List.of(userA));
        when(reminderScheduleRepository.existsByUserAndDate(userA, today)).thenReturn(true);
        when(reminderScheduleRepository.findByDateAndSentFalseAndScheduledTimeLessThanEqual(today, firstRunTime))
                .thenReturn(List.of(schedule));
        when(dailyCheckInRepository.findByUserAndDate(userA, today)).thenReturn(Optional.empty());

        // First run: dispatches reminder and marks schedule sent
        reminderService.processReminders(today, firstRunTime);
        verify(notificationService, times(1)).createNotification(eq(userA), any(), any());
        assertTrue(schedule.isSent());

        // Second run: query for sent=false returns empty list
        when(reminderScheduleRepository.findByDateAndSentFalseAndScheduledTimeLessThanEqual(today, secondRunTime))
                .thenReturn(Collections.emptyList());

        reminderService.processReminders(today, secondRunTime);

        // Still exactly 1 notification sent
        verify(notificationService, times(1)).createNotification(eq(userA), any(), any());
    }

    @Test
    @DisplayName("4. Different users receive reminders independently according to their own schedule")
    void testDifferentUsersReceiveRemindersIndependently() {
        LocalDate today = LocalDate.now();
        LocalTime userATime = LocalTime.of(9, 0);
        LocalTime userBTime = LocalTime.of(14, 0);

        CheckInReminderSchedule scheduleA = new CheckInReminderSchedule(userA, today, userATime);

        when(userRepository.findAll()).thenReturn(List.of(userA, userB));
        when(reminderScheduleRepository.existsByUserAndDate(userA, today)).thenReturn(true);
        when(reminderScheduleRepository.existsByUserAndDate(userB, today)).thenReturn(true);

        // At 09:30: userA is due, userB is not due yet
        LocalTime morningCheck = LocalTime.of(9, 30);
        when(reminderScheduleRepository.findByDateAndSentFalseAndScheduledTimeLessThanEqual(today, morningCheck))
                .thenReturn(List.of(scheduleA));
        when(dailyCheckInRepository.findByUserAndDate(userA, today)).thenReturn(Optional.empty());

        reminderService.processReminders(today, morningCheck);

        verify(notificationService).createNotification(eq(userA), eq(NotificationType.DAILY_CHECKIN_REMINDER), anyString());
        verify(notificationService, never()).createNotification(eq(userB), any(), any());
    }

    @Test
    @DisplayName("5. Configured time windows boundaries are strictly respected")
    void testConfiguredTimeWindowsRespected() {
        ReminderTimeGenerator defaultGen = ReminderTimeGenerator.defaultRandom();

        for (ReminderWindow window : ReminderWindow.values()) {
            LocalTime scheduled = defaultGen.generateScheduledTime(window, LocalTime.of(0, 0));
            assertNotNull(scheduled, "Scheduled time should not be null for window: " + window);
            assertFalse(scheduled.isBefore(window.getStartTime()),
                    "Scheduled time " + scheduled + " must not be before start " + window.getStartTime());
            assertTrue(scheduled.isBefore(window.getEndTime()),
                    "Scheduled time " + scheduled + " must be before end " + window.getEndTime());
        }
    }

    @Test
    @DisplayName("6. No reminder is scheduled if current time is past window end time")
    void testNoReminderWhenWindowHasPassed() {
        ReminderTimeGenerator defaultGen = ReminderTimeGenerator.defaultRandom();

        // Morning ends at 12:00; test with earliest allowed = 12:01
        LocalTime morningScheduled = defaultGen.generateScheduledTime(ReminderWindow.MORNING, LocalTime.of(12, 1));
        assertNull(morningScheduled);

        // Afternoon ends at 17:00; test with earliest allowed = 17:30
        LocalTime afternoonScheduled = defaultGen.generateScheduledTime(ReminderWindow.AFTERNOON, LocalTime.of(17, 30));
        assertNull(afternoonScheduled);
    }

    @Test
    @DisplayName("7. Changing reminder window BEFORE reminder is sent regenerates today's schedule")
    void testChangeWindowBeforeSentUpdatesSchedule() {
        LocalDate today = LocalDate.now();
        LocalTime oldScheduled = LocalTime.of(10, 0);

        CheckInReminderSchedule schedule = new CheckInReminderSchedule(userA, today, oldScheduled);
        schedule.setSent(false);

        // Provide deterministic generator: Evening returns 19:30
        reminderService.setTimeGenerator((window, earliest) -> LocalTime.of(19, 30));

        when(userRepository.save(userA)).thenReturn(userA);
        when(reminderScheduleRepository.findByUserAndDate(userA, today)).thenReturn(Optional.of(schedule));

        ReminderWindowResponse response = reminderService.updateReminderWindow(userA, ReminderWindow.EVENING);

        assertEquals(ReminderWindow.EVENING, response.getWindow());
        assertEquals(LocalTime.of(19, 30), schedule.getScheduledTime());
        assertFalse(schedule.isSent());
        verify(reminderScheduleRepository).save(schedule);
    }

    @Test
    @DisplayName("8. Changing reminder window AFTER reminder was sent does NOT modify today's schedule")
    void testChangeWindowAfterSentDoesNotCreateAnotherToday() {
        LocalDate today = LocalDate.now();
        LocalTime oldScheduled = LocalTime.of(9, 30);

        CheckInReminderSchedule schedule = new CheckInReminderSchedule(userA, today, oldScheduled);
        schedule.setSent(true); // Already sent

        when(userRepository.save(userA)).thenReturn(userA);
        when(reminderScheduleRepository.findByUserAndDate(userA, today)).thenReturn(Optional.of(schedule));

        ReminderWindowResponse response = reminderService.updateReminderWindow(userA, ReminderWindow.EVENING);

        assertEquals(ReminderWindow.EVENING, response.getWindow());
        // Schedule is untouched
        assertEquals(oldScheduled, schedule.getScheduledTime());
        assertTrue(schedule.isSent());
        verify(reminderScheduleRepository, never()).save(schedule);
    }

    @Test
    @DisplayName("9. Concurrency race condition on schedule creation is handled gracefully")
    void testConcurrentScheduleCreationHandledGracefully() {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.of(9, 0);

        when(reminderScheduleRepository.existsByUserAndDate(userA, today)).thenReturn(false);
        when(reminderScheduleRepository.save(any())).thenThrow(new DataIntegrityViolationException("Duplicate key uk_reminder_schedule_user_date"));

        // Must not throw an exception out of the method
        assertDoesNotThrow(() -> reminderService.generateScheduleForUser(userA, today, now));
    }

    @Test
    @DisplayName("10. Controller GET /checkin/reminder-window returns authenticated user preference")
    void testControllerGetReminderWindow() {
        ReminderWindowResponse res = reminderController.getReminderWindow();

        assertNotNull(res);
        assertEquals(ReminderWindow.MORNING, res.getWindow());
        assertEquals("08:00", res.getStartTime());
        assertEquals("12:00", res.getEndTime());
    }

    @Test
    @DisplayName("11. Controller PUT /checkin/reminder-window updates authenticated user preference")
    void testControllerUpdateReminderWindow() {
        when(userRepository.save(userA)).thenReturn(userA);
        when(reminderScheduleRepository.findByUserAndDate(eq(userA), any())).thenReturn(Optional.empty());

        ReminderWindowRequest request = new ReminderWindowRequest(ReminderWindow.AFTERNOON);
        ReminderWindowResponse res = reminderController.updateReminderWindow(request);

        assertNotNull(res);
        assertEquals(ReminderWindow.AFTERNOON, res.getWindow());
        assertEquals("12:00", res.getStartTime());
        assertEquals("17:00", res.getEndTime());
    }
}
