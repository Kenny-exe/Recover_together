package com.recovertogether.backend;

import com.recovertogether.backend.dto.StreakResponse;
import com.recovertogether.backend.entity.DailyCheckIn;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.enums.CheckInStatus;
import com.recovertogether.backend.repository.DailyCheckInRepository;
import com.recovertogether.backend.repository.PartnerRequestRepository;
import com.recovertogether.backend.repository.UserRepository;
import com.recovertogether.backend.service.AchievementService;
import com.recovertogether.backend.service.DailyCheckInService;
import com.recovertogether.backend.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyCheckInStreakTest {

    @Mock
    private DailyCheckInRepository dailyCheckInRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private PartnerRequestRepository partnerRequestRepository;

    @Mock
    private AchievementService achievementService;

    @InjectMocks
    private DailyCheckInService dailyCheckInService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setName("Alice");
        testUser.setEmail("alice@example.com");
    }

    private DailyCheckIn createCheckIn(LocalDate date, CheckInStatus status) {
        DailyCheckIn checkIn = new DailyCheckIn();
        checkIn.setUser(testUser);
        checkIn.setDate(date);
        checkIn.setStatus(status);
        return checkIn;
    }

    @Test
    @DisplayName("Empty check-in history returns 0 current and 0 best streak")
    void testEmptyHistory() {
        when(dailyCheckInRepository.findByUserOrderByDateAsc(testUser)).thenReturn(List.of());

        StreakResponse streak = dailyCheckInService.calculateStreak(testUser);

        assertEquals(0, streak.getCurrentStreak());
        assertEquals(0, streak.getBestStreak());
    }

    @Test
    @DisplayName("Single successful check-in today returns current 1, best 1")
    void testCheckInTodaySuccess() {
        List<DailyCheckIn> checkIns = List.of(
                createCheckIn(LocalDate.now(), CheckInStatus.SUCCESS)
        );
        when(dailyCheckInRepository.findByUserOrderByDateAsc(testUser)).thenReturn(checkIns);

        StreakResponse streak = dailyCheckInService.calculateStreak(testUser);

        assertEquals(1, streak.getCurrentStreak());
        assertEquals(1, streak.getBestStreak());
    }

    @Test
    @DisplayName("Single successful check-in yesterday returns current 1, best 1 (streak still active)")
    void testCheckInYesterdaySuccess() {
        List<DailyCheckIn> checkIns = List.of(
                createCheckIn(LocalDate.now().minusDays(1), CheckInStatus.SUCCESS)
        );
        when(dailyCheckInRepository.findByUserOrderByDateAsc(testUser)).thenReturn(checkIns);

        StreakResponse streak = dailyCheckInService.calculateStreak(testUser);

        assertEquals(1, streak.getCurrentStreak());
        assertEquals(1, streak.getBestStreak());
    }

    @Test
    @DisplayName("Check-in 2 days ago without yesterday or today resets current streak to 0, preserves best streak")
    void testStaleCheckInTwoDaysAgo() {
        List<DailyCheckIn> checkIns = List.of(
                createCheckIn(LocalDate.now().minusDays(2), CheckInStatus.SUCCESS)
        );
        when(dailyCheckInRepository.findByUserOrderByDateAsc(testUser)).thenReturn(checkIns);

        StreakResponse streak = dailyCheckInService.calculateStreak(testUser);

        assertEquals(0, streak.getCurrentStreak(), "Current streak must reset to 0 if last check-in was 2 days ago");
        assertEquals(1, streak.getBestStreak(), "Best streak must be preserved");
    }

    @Test
    @DisplayName("Consecutive 5-day success streak ending 3 days ago resets current streak to 0, preserves best streak of 5")
    void testStaleMultiDayStreak() {
        List<DailyCheckIn> checkIns = new ArrayList<>();
        LocalDate start = LocalDate.now().minusDays(7);
        for (int i = 0; i < 5; i++) {
            checkIns.add(createCheckIn(start.plusDays(i), CheckInStatus.SUCCESS));
        }
        when(dailyCheckInRepository.findByUserOrderByDateAsc(testUser)).thenReturn(checkIns);

        StreakResponse streak = dailyCheckInService.calculateStreak(testUser);

        assertEquals(0, streak.getCurrentStreak(), "Stale streak must be 0");
        assertEquals(5, streak.getBestStreak(), "Best streak must be 5");
    }

    @Test
    @DisplayName("Consecutive 3-day success streak ending today returns current 3, best 3")
    void testActiveThreeDayStreakEndingToday() {
        List<DailyCheckIn> checkIns = List.of(
                createCheckIn(LocalDate.now().minusDays(2), CheckInStatus.SUCCESS),
                createCheckIn(LocalDate.now().minusDays(1), CheckInStatus.SUCCESS),
                createCheckIn(LocalDate.now(), CheckInStatus.SUCCESS)
        );
        when(dailyCheckInRepository.findByUserOrderByDateAsc(testUser)).thenReturn(checkIns);

        StreakResponse streak = dailyCheckInService.calculateStreak(testUser);

        assertEquals(3, streak.getCurrentStreak());
        assertEquals(3, streak.getBestStreak());
    }

    @Test
    @DisplayName("Consecutive 3-day success streak ending yesterday returns current 3, best 3")
    void testActiveThreeDayStreakEndingYesterday() {
        List<DailyCheckIn> checkIns = List.of(
                createCheckIn(LocalDate.now().minusDays(3), CheckInStatus.SUCCESS),
                createCheckIn(LocalDate.now().minusDays(2), CheckInStatus.SUCCESS),
                createCheckIn(LocalDate.now().minusDays(1), CheckInStatus.SUCCESS)
        );
        when(dailyCheckInRepository.findByUserOrderByDateAsc(testUser)).thenReturn(checkIns);

        StreakResponse streak = dailyCheckInService.calculateStreak(testUser);

        assertEquals(3, streak.getCurrentStreak());
        assertEquals(3, streak.getBestStreak());
    }

    @Test
    @DisplayName("Relapse today resets current streak to 0, preserves previous best streak")
    void testRelapseToday() {
        List<DailyCheckIn> checkIns = List.of(
                createCheckIn(LocalDate.now().minusDays(2), CheckInStatus.SUCCESS),
                createCheckIn(LocalDate.now().minusDays(1), CheckInStatus.SUCCESS),
                createCheckIn(LocalDate.now(), CheckInStatus.RELAPSE)
        );
        when(dailyCheckInRepository.findByUserOrderByDateAsc(testUser)).thenReturn(checkIns);

        StreakResponse streak = dailyCheckInService.calculateStreak(testUser);

        assertEquals(0, streak.getCurrentStreak(), "Relapse today must reset current streak to 0");
        assertEquals(2, streak.getBestStreak(), "Best streak before relapse was 2");
    }
}
