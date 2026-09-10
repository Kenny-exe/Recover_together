package com.recovertogether.backend.service;

import com.recovertogether.backend.dto.*;
import com.recovertogether.backend.entity.DailyCheckIn;
import com.recovertogether.backend.entity.User;
import com.recovertogether.backend.repository.AchievementRepository;
import com.recovertogether.backend.repository.DailyCheckInRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class DashboardService
{
    private final DailyCheckInService dailyCheckInService;
    private final PartnerRequestService partnerRequestService;
    private final MessageService messageService;
    private final DailyCheckInRepository dailyCheckInRepository;
    private final AchievementRepository achievementRepository;

    public DashboardService(
            DailyCheckInService dailyCheckInService,
            PartnerRequestService partnerRequestService,
            MessageService messageService,
            DailyCheckInRepository dailyCheckInRepository,
            AchievementRepository achievementRepository)
    {
        this.dailyCheckInService = dailyCheckInService;
        this.partnerRequestService = partnerRequestService;
        this.messageService = messageService;
        this.dailyCheckInRepository = dailyCheckInRepository;
        this.achievementRepository = achievementRepository;
    }

    public DashboardResponse getDashboard()
    {
        User currentUser =
                (User) SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .getPrincipal();

        CheckInStatsResponse stats =
                dailyCheckInService.getStats();

        UnreadCountResponse unread =
                messageService.getUnreadCount();

        long achievementCount =
                achievementRepository.countByUser(currentUser);

        Optional<User> partnerOpt =
                partnerRequestService.findPartnerUser(currentUser);

        String partnerName = null;
        int partnerCurrentStreak = 0;
        int partnerBestStreak = 0;
        LocalDateTime partnerLastSeen = null;
        boolean partnerCheckedInToday = false;
        long daysSinceLastCheckIn = -1;

        if (partnerOpt.isPresent())
        {
            User partner = partnerOpt.get();
            partnerName = partner.getName();
            partnerLastSeen = partner.getLastSeen();

            StreakResponse partnerStreak =
                    dailyCheckInService.calculateStreak(partner);
            partnerCurrentStreak = partnerStreak.getCurrentStreak();
            partnerBestStreak = partnerStreak.getBestStreak();

            Optional<DailyCheckIn> latestCheckIn =
                    dailyCheckInRepository.findTopByUserOrderByDateDesc(partner);
            if (latestCheckIn.isPresent())
            {
                LocalDate lastDate = latestCheckIn.get().getDate();
                partnerCheckedInToday = lastDate.equals(LocalDate.now());
                daysSinceLastCheckIn = ChronoUnit.DAYS.between(lastDate, LocalDate.now());
            }
        }

        return new DashboardResponse(
                stats.getCurrentStreak(),
                stats.getBestStreak(),

                stats.getTotalCheckIns(),
                stats.getSuccessCount(),
                stats.getRelapseCount(),
                stats.getSuccessRate(),

                unread.getUnreadCount(),

                partnerName,
                partnerCurrentStreak,
                partnerBestStreak,

                partnerLastSeen,
                partnerCheckedInToday,
                daysSinceLastCheckIn,
                achievementCount
        );
    }
}