package com.recovertogether.backend.service;

import com.recovertogether.backend.dto.AchievementResponse;
import com.recovertogether.backend.entity.Achievement;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.enums.NotificationType;
import com.recovertogether.backend.repository.AchievementRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class AchievementService
{
    private final AchievementRepository achievementRepository;
    private final NotificationService notificationService;

    public AchievementService(AchievementRepository achievementRepository,
                              NotificationService notificationService)
    {
        this.achievementRepository=achievementRepository;
        this.notificationService=notificationService;
    }

    public List<AchievementResponse> getAchievements()
    {
        User currentUser=(User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        return achievementRepository.findByUserOrderByEarnedAtDesc(currentUser).stream().map(AchievementResponse::new).toList();
    }

    public void awardAchievement(User user, String title)
    {
        if(achievementRepository.existsByUserAndTitle(user,title))
        {
            return;
        }

        Achievement achievement =new Achievement();
        achievement.setUser(user);
        achievement.setTitle(title);
        achievementRepository.save(achievement);

        notificationService.createNotification(user, NotificationType.MILESTONE_REACHED,"Achievment unlocked: "+title);
    }

    public void checkMilestones(User user, int streak)
    {
        if(streak>=1)
        {
            awardAchievement(user,"1-day streak");
        }
        if(streak >= 3)
        {
            awardAchievement(user, "3 Day Streak");
        }

        if(streak >= 7)
        {
            awardAchievement(user, "7 Day Streak");
        }

        if(streak >= 14)
        {
            awardAchievement(user, "14 Day Streak");
        }

        if(streak >= 30)
        {
            awardAchievement(user, "30 Day Streak");
        }

        if(streak >= 60)
        {
            awardAchievement(user, "60 Day Streak");
        }

        if(streak >= 100)
        {
            awardAchievement(user, "100 Day Streak");
        }

        if(streak >= 365)
        {
            awardAchievement(user, "365 Day Streak");
        }
    }
}
