package com.recovertogether.backend;

import com.recovertogether.backend.dto.CheckInStatsResponse;
import com.recovertogether.backend.dto.DashboardResponse;
import com.recovertogether.backend.dto.StreakResponse;
import com.recovertogether.backend.dto.UnreadCountResponse;
import com.recovertogether.backend.entity.DailyCheckIn;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.repository.AchievementRepository;
import com.recovertogether.backend.repository.DailyCheckInRepository;
import com.recovertogether.backend.service.DailyCheckInService;
import com.recovertogether.backend.service.DashboardService;
import com.recovertogether.backend.service.MessageService;
import com.recovertogether.backend.service.PartnerRequestService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private DailyCheckInService dailyCheckInService;

    @Mock
    private PartnerRequestService partnerRequestService;

    @Mock
    private MessageService messageService;

    @Mock
    private DailyCheckInRepository dailyCheckInRepository;

    @Mock
    private AchievementRepository achievementRepository;

    @InjectMocks
    private DashboardService dashboardService;

    private User currentUser;
    private User partnerUser;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        setUserId(currentUser, 1L);
        currentUser.setName("Alice");
        currentUser.setEmail("alice@test.com");

        partnerUser = new User();
        setUserId(partnerUser, 2L);
        partnerUser.setName("Bob");
        partnerUser.setEmail("bob@test.com");
        partnerUser.setLastSeen(LocalDateTime.of(2026, 9, 10, 12, 0));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUser, null, Collections.emptyList())
        );
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
    @DisplayName("Dashboard with partner who checked in today")
    void testGetDashboard_WithPartner_CheckedInToday() {
        when(dailyCheckInService.getStats()).thenReturn(new CheckInStatsResponse(20L, 18L, 2L, 90.0, 5, 10));
        when(messageService.getUnreadCount()).thenReturn(new UnreadCountResponse(3));
        when(achievementRepository.countByUser(currentUser)).thenReturn(4L);

        when(partnerRequestService.findPartnerUser(currentUser)).thenReturn(Optional.of(partnerUser));
        when(dailyCheckInService.calculateStreak(partnerUser)).thenReturn(new StreakResponse(7, 14));

        DailyCheckIn checkIn = new DailyCheckIn();
        checkIn.setDate(LocalDate.now());
        when(dailyCheckInRepository.findTopByUserOrderByDateDesc(partnerUser)).thenReturn(Optional.of(checkIn));

        DashboardResponse response = dashboardService.getDashboard();

        // User stats
        assertEquals(5, response.getCurrentStreak());
        assertEquals(10, response.getBestStreak());
        assertEquals(20, response.getTotalCheckIns());
        assertEquals(18, response.getSuccessCount());
        assertEquals(2, response.getRelapseCount());
        assertEquals(90.0, response.getSuccessRate());
        assertEquals(3, response.getUnreadMessages());
        assertEquals(4L, response.getAchievementCount());

        // Partner stats
        assertEquals("Bob", response.getPartnerName());
        assertEquals(7, response.getPartnerCurrentStreak());
        assertEquals(14, response.getPartnerBestStreak());
        assertEquals(LocalDateTime.of(2026, 9, 10, 12, 0), response.getPartnerLastSeen());
        assertTrue(response.isPartnerCheckedInToday());
        assertEquals(0, response.getDaysSinceLastCheckIn());
    }

    @Test
    @DisplayName("Dashboard with partner who checked in 3 days ago")
    void testGetDashboard_WithPartner_CheckedInPast() {
        when(dailyCheckInService.getStats()).thenReturn(new CheckInStatsResponse(10L, 8L, 2L, 80.0, 2, 5));
        when(messageService.getUnreadCount()).thenReturn(new UnreadCountResponse(0));
        when(achievementRepository.countByUser(currentUser)).thenReturn(1L);

        when(partnerRequestService.findPartnerUser(currentUser)).thenReturn(Optional.of(partnerUser));
        when(dailyCheckInService.calculateStreak(partnerUser)).thenReturn(new StreakResponse(0, 5));

        DailyCheckIn checkIn = new DailyCheckIn();
        checkIn.setDate(LocalDate.now().minusDays(3));
        when(dailyCheckInRepository.findTopByUserOrderByDateDesc(partnerUser)).thenReturn(Optional.of(checkIn));

        DashboardResponse response = dashboardService.getDashboard();

        assertEquals("Bob", response.getPartnerName());
        assertEquals(0, response.getPartnerCurrentStreak());
        assertEquals(5, response.getPartnerBestStreak());
        assertFalse(response.isPartnerCheckedInToday());
        assertEquals(3, response.getDaysSinceLastCheckIn());
    }

    @Test
    @DisplayName("Dashboard with partner who has no check-ins")
    void testGetDashboard_WithPartner_NoCheckIns() {
        when(dailyCheckInService.getStats()).thenReturn(new CheckInStatsResponse(0L, 0L, 0L, 0.0, 0, 0));
        when(messageService.getUnreadCount()).thenReturn(new UnreadCountResponse(0));
        when(achievementRepository.countByUser(currentUser)).thenReturn(0L);

        when(partnerRequestService.findPartnerUser(currentUser)).thenReturn(Optional.of(partnerUser));
        when(dailyCheckInService.calculateStreak(partnerUser)).thenReturn(new StreakResponse(0, 0));
        when(dailyCheckInRepository.findTopByUserOrderByDateDesc(partnerUser)).thenReturn(Optional.empty());

        DashboardResponse response = dashboardService.getDashboard();

        assertEquals("Bob", response.getPartnerName());
        assertFalse(response.isPartnerCheckedInToday());
        assertEquals(-1, response.getDaysSinceLastCheckIn());
    }

    @Test
    @DisplayName("Dashboard without partner returns default partner fields without throwing exceptions")
    void testGetDashboard_WithoutPartner() {
        when(dailyCheckInService.getStats()).thenReturn(new CheckInStatsResponse(15L, 14L, 1L, 93.3, 3, 7));
        when(messageService.getUnreadCount()).thenReturn(new UnreadCountResponse(2));
        when(achievementRepository.countByUser(currentUser)).thenReturn(2L);

        when(partnerRequestService.findPartnerUser(currentUser)).thenReturn(Optional.empty());

        DashboardResponse response = dashboardService.getDashboard();

        // User stats remain intact
        assertEquals(3, response.getCurrentStreak());
        assertEquals(7, response.getBestStreak());
        assertEquals(15, response.getTotalCheckIns());
        assertEquals(14, response.getSuccessCount());
        assertEquals(1, response.getRelapseCount());
        assertEquals(93.3, response.getSuccessRate());
        assertEquals(2, response.getUnreadMessages());
        assertEquals(2L, response.getAchievementCount());

        // Default partner fields
        assertNull(response.getPartnerName());
        assertEquals(0, response.getPartnerCurrentStreak());
        assertEquals(0, response.getPartnerBestStreak());
        assertNull(response.getPartnerLastSeen());
        assertFalse(response.isPartnerCheckedInToday());
        assertEquals(-1, response.getDaysSinceLastCheckIn());

        // Ensure no daily check-in queries were executed for partner
        verify(dailyCheckInRepository, never()).findTopByUserOrderByDateDesc(any());
        verify(dailyCheckInService, never()).calculateStreak(any());
    }
}
