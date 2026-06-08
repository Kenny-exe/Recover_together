package com.recovertogether.backend.service;

import com.recovertogether.backend.dto.*;
import org.springframework.stereotype.Service;

@Service
public class DashboardService
{
    private final DailyCheckInService dailyCheckInService;
    private final PartnerRequestService partnerRequestService;
    private final MessageService messageService;

    public DashboardService(
            DailyCheckInService dailyCheckInService,
            PartnerRequestService partnerRequestService,
            MessageService messageService)
    {
        this.dailyCheckInService = dailyCheckInService;
        this.partnerRequestService = partnerRequestService;
        this.messageService = messageService;
    }

    public DashboardResponse getDashboard()
    {
        CheckInStatsResponse stats =
                dailyCheckInService.getStats();

        StreakResponse streak =
                dailyCheckInService.getStreak();

        PartnerSummaryResponse partnerSummary =
                partnerRequestService.getPartnerSummary();

        LastSeenResponse partnerLastSeen =
                partnerRequestService.getPartnerLastSeen();

        UnreadCountResponse unread =
                messageService.getReadCount();

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

                partnerLastSeen.getLastSeen()
        );
    }
}