package com.recovertogether.backend.service;

import com.recovertogether.backend.dto.*;
import com.recovertogether.backend.repository.AchievementRepository;
import org.springframework.stereotype.Service;
import com.recovertogether.backend.dto.PartnerStatusResponse;
import org.springframework.web.server.ResponseStatusException;
import com.recovertogether.backend.entity.User;
import org.springframework.security.core.context.SecurityContextHolder;


@Service
public class DashboardService
{
    private final DailyCheckInService dailyCheckInService;
    private final PartnerRequestService partnerRequestService;
    private final MessageService messageService;
    private final PartnerCheckInService partnerCheckInService;
    private final AchievementRepository achievementRepository;

    public DashboardService(
            DailyCheckInService dailyCheckInService,
            PartnerRequestService partnerRequestService,
            MessageService messageService,
            PartnerCheckInService partnerCheckInService,
            AchievementRepository achievementRepository)
    {
        this.dailyCheckInService = dailyCheckInService;
        this.partnerRequestService = partnerRequestService;
        this.messageService = messageService;
        this.partnerCheckInService = partnerCheckInService;
        this.achievementRepository=achievementRepository;
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

        StreakResponse streak =
                dailyCheckInService.getStreak();

        PartnerSummaryResponse partnerSummary;
        try
        {
            partnerSummary = partnerRequestService.getPartnerSummary();
        }
        catch(ResponseStatusException e)
        {
            partnerSummary =
                    new PartnerSummaryResponse(
                            null,
                            null,
                            0,
                            0);
        }

        LastSeenResponse partnerLastSeen;
        try
        {
            partnerLastSeen = partnerRequestService.getPartnerLastSeen();
        }
        catch(ResponseStatusException e)
        {
            partnerLastSeen = new LastSeenResponse(null,null);
        }

        UnreadCountResponse unread =
                messageService.getReadCount();

        long achievementCount =
                achievementRepository.countByUser(currentUser);

        PartnerStatusResponse partnerStatus;
        try
        {
            partnerStatus=partnerCheckInService.getPartnerStatus();
        }
        catch (ResponseStatusException e)
        {
            partnerStatus=new PartnerStatusResponse(
                    false,
                    -1,
                    null);
        }

        return new DashboardResponse(
                streak.getCurrentStreak(),
                streak.getBestStreak(),

                stats.getTotalCheckIns(),
                stats.getSuccessCount(),
                stats.getRelapseCount(),
                stats.getSuccessRate(),

                unread.getUnreadCount(),

                partnerSummary.getName(),
                partnerSummary.getCurrentStreak(),
                partnerSummary.getBestStreak(),

                partnerLastSeen.getLastSeen(),
                partnerStatus.isPartnerCheckedInToday(),
                partnerStatus.getDaysSinceLastCheckIn(),
                achievementCount
        );
    }
}