package com.recovertogether.backend;

import com.recovertogether.backend.entity.Achievement;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.enums.NotificationType;
import com.recovertogether.backend.repository.AchievementRepository;
import com.recovertogether.backend.repository.PartnerRequestRepository;
import com.recovertogether.backend.service.AchievementService;
import com.recovertogether.backend.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AchievementServiceTest {

    @Mock
    private AchievementRepository achievementRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private PartnerRequestRepository partnerRequestRepository;

    @InjectMocks
    private AchievementService achievementService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        setUserId(user, 1L);
        user.setName("Alice");
        user.setEmail("alice@test.com");
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
    @DisplayName("checkMilestones at streak=1 awards '1 Day Streak'")
    void testCheckMilestones_AwardsOneDayStreak() {
        when(achievementRepository.existsByUserAndTitle(user, "1 Day Streak")).thenReturn(false);
        when(achievementRepository.existsByUserAndTitle(user, "1 Day streak")).thenReturn(false);
        when(partnerRequestRepository.findFirstBySenderAndStatus(any(), any())).thenReturn(Optional.empty());
        when(partnerRequestRepository.findFirstByReceiverAndStatus(any(), any())).thenReturn(Optional.empty());

        achievementService.checkMilestones(user, 1);

        ArgumentCaptor<Achievement> captor = ArgumentCaptor.forClass(Achievement.class);
        verify(achievementRepository).save(captor.capture());
        assertEquals("1 Day Streak", captor.getValue().getTitle());
        assertEquals(user, captor.getValue().getUser());

        verify(notificationService).createNotification(
                eq(user),
                eq(NotificationType.ACHIEVEMENT_UNLOCKED),
                eq("Achievement unlocked: 1 Day Streak")
        );
    }

    @Test
    @DisplayName("awardAchievement with '1 Day Streak' does not award if legacy '1 Day streak' already exists")
    void testAwardAchievement_LegacyTitleDeduplication() {
        when(achievementRepository.existsByUserAndTitle(user, "1 Day Streak")).thenReturn(false);
        when(achievementRepository.existsByUserAndTitle(user, "1 Day streak")).thenReturn(true);

        achievementService.awardAchievement(user, "1 Day Streak");

        verify(achievementRepository, never()).save(any());
        verify(notificationService, never()).createNotification(any(), any(), any());
    }

    @Test
    @DisplayName("awardAchievement does not award if exact title already exists")
    void testAwardAchievement_AlreadyExists() {
        when(achievementRepository.existsByUserAndTitle(user, "3 Day Streak")).thenReturn(true);

        achievementService.awardAchievement(user, "3 Day Streak");

        verify(achievementRepository, never()).save(any());
        verify(notificationService, never()).createNotification(any(), any(), any());
    }
}
